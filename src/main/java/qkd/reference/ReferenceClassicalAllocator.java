package qkd.reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Java port of the classical part of the verified multi-fiber reconstruction.
 * It is intentionally isolated in the QKD addon and does not modify SNetS
 * routing/core/spectrum algorithms.
 */
public class ReferenceClassicalAllocator {

    public static final class RoutedRequest {
        public final ReferenceRequest request;
        public final List<Integer> path;
        RoutedRequest(ReferenceRequest request, List<Integer> path) {
            this.request=request; this.path=new ArrayList<Integer>(path);
        }
    }

    public static final class Result {
        public final List<RoutedRequest> routedRequests;
        public final int classicalBlockedRequests;
        public final int pythonPathMismatchCount;
        Result(List<RoutedRequest> routedRequests, int classicalBlockedRequests,
                int pythonPathMismatchCount) {
            this.routedRequests=routedRequests;
            this.classicalBlockedRequests=classicalBlockedRequests;
            this.pythonPathMismatchCount=pythonPathMismatchCount;
        }
    }

    private static final class Modulation {
        final String name; final double reachKm; final double channelCapacityGbps;
        Modulation(String name,double reachKm,double capacity){this.name=name;this.reachKm=reachKm;this.channelCapacityGbps=capacity;}
    }

    private static final Modulation[] MODULATIONS = new Modulation[] {
        new Modulation("64QAM",125.0,150.0),
        new Modulation("32QAM",250.0,125.0),
        new Modulation("16QAM",500.0,100.0),
        new Modulation("8QAM",1000.0,75.0),
        new Modulation("QPSK",2000.0,50.0),
        new Modulation("BPSK",Double.POSITIVE_INFINITY,25.0)
    };

    private final ReferenceTopologySpec topology;
    private final int channelsPerFiber;
    private final int fiberCount;
    private final int kPaths;
    private final int guardChannels;
    private final boolean reservePichChannel;
    private final Map<String, boolean[][]> occupancy =
            new HashMap<String, boolean[][]>();

    public ReferenceClassicalAllocator(ReferenceTopologySpec topology,
            ReferenceExperimentConfig config) {
        this.topology=topology;
        this.channelsPerFiber=config.getClassicalChannelsPerFiber();
        this.fiberCount=config.getClassicalFibersPerLink();
        this.kPaths=config.getKPaths();
        this.guardChannels=config.getClassicalGuardChannels();
        this.reservePichChannel=config.isReservePichChannel();
        for (ReferenceTopologySpec.Edge edge : topology.getEdges()) {
            boolean[][] grid = new boolean[fiberCount][channelsPerFiber];
            if (reservePichChannel) {
                for (int f=0; f<fiberCount; f++) grid[f][0]=true;
            }
            occupancy.put(edge.key(), grid);
        }
    }

    /** Repeats the reference grooming + multi-fiber classical allocation. */
    public Result allocateAll(List<ReferenceRequest> requests,
            double groomingCapacityGbps) {
        List<GroomedDemand> groomed = groom(requests, groomingCapacityGbps);
        List<RoutedRequest> routed = new ArrayList<RoutedRequest>();
        int blocked = 0;
        int mismatch = 0;

        for (GroomedDemand group : groomed) {
            Allocation allocation = allocate(group.source, group.destination,
                    group.classicalRateGbps);
            if (!allocation.accepted) {
                blocked += group.requests.size();
                continue;
            }
            for (ReferenceRequest request : group.requests) {
                routed.add(new RoutedRequest(request, allocation.path));
                if (request.expectedPythonPath != null
                        && !request.expectedPythonPath.equals(allocation.path)) {
                    mismatch++;
                }
            }
        }
        return new Result(routed, blocked, mismatch);
    }

    private static final class GroomedDemand {
        int source; int destination; double classicalRateGbps;
        final List<ReferenceRequest> requests = new ArrayList<ReferenceRequest>();
    }

    private static List<GroomedDemand> groom(List<ReferenceRequest> requests,
            double capacityGbps) {
        if (!(capacityGbps > 0.0)) throw new IllegalArgumentException("capacity must be > 0");
        List<GroomedDemand> groups = new ArrayList<GroomedDemand>();
        Map<String,List<Integer>> indices = new LinkedHashMap<String,List<Integer>>();
        for (ReferenceRequest request : requests) {
            String key=request.source+"->"+request.destination;
            List<Integer> candidates=indices.get(key);
            Integer selected=null;
            if (candidates != null) {
                for (Integer idx : candidates) {
                    GroomedDemand group=groups.get(idx.intValue());
                    if (group.classicalRateGbps + request.classicalRateGbps
                            <= capacityGbps + 1e-9) {
                        selected=idx; break;
                    }
                }
            }
            if (selected == null) {
                GroomedDemand group=new GroomedDemand();
                group.source=request.source; group.destination=request.destination;
                groups.add(group);
                selected=Integer.valueOf(groups.size()-1);
                if (candidates == null) {
                    candidates=new ArrayList<Integer>();
                    indices.put(key,candidates);
                }
                candidates.add(selected);
            }
            GroomedDemand group=groups.get(selected.intValue());
            group.classicalRateGbps += request.classicalRateGbps;
            group.requests.add(request);
        }
        return groups;
    }

    private static final class Allocation {
        final boolean accepted; final List<Integer> path;
        Allocation(boolean accepted,List<Integer> path){this.accepted=accepted;this.path=path;}
    }

    private static final class Candidate {
        int endingIndex; int fiberIndex; double lengthKm;
        List<Integer> path; int start; int required;
    }

    private Allocation allocate(int source,int destination,double rateGbps) {
        List<List<Integer>> paths=kShortestSimplePaths(source,destination,kPaths);
        List<Candidate> candidates=new ArrayList<Candidate>();
        for (List<Integer> path : paths) {
            double length=pathLength(path);
            Modulation modulation=selectModulation(length);
            int payload=(int)Math.ceil(rateGbps/modulation.channelCapacityGbps - 1e-15);
            int required=payload+guardChannels;
            int[] fit=findFirstFit(path,required);
            if (fit == null) continue;
            Candidate c=new Candidate();
            c.start=fit[0]; c.fiberIndex=fit[1]; c.required=required;
            c.endingIndex=c.start+required-1; c.lengthKm=length; c.path=path;
            candidates.add(c);
        }
        if (candidates.isEmpty()) return new Allocation(false,null);
        Collections.sort(candidates,new Comparator<Candidate>() {
            @Override public int compare(Candidate a,Candidate b) {
                int x=Integer.compare(a.endingIndex,b.endingIndex); if(x!=0)return x;
                x=Integer.compare(a.fiberIndex,b.fiberIndex); if(x!=0)return x;
                x=Double.compare(a.lengthKm,b.lengthKm); if(x!=0)return x;
                return comparePath(a.path,b.path);
            }
        });
        Candidate chosen=candidates.get(0);
        for (String edgeKey : pathEdgeKeys(chosen.path)) {
            boolean[][] grid=occupancy.get(edgeKey);
            for(int ch=chosen.start;ch<chosen.start+chosen.required;ch++)
                grid[chosen.fiberIndex][ch]=true;
        }
        return new Allocation(true,new ArrayList<Integer>(chosen.path));
    }

    private int[] findFirstFit(List<Integer> path,int required) {
        if (required > channelsPerFiber) return null;
        List<String> edges=pathEdgeKeys(path);
        int[] best=null;
        for(int fiber=0;fiber<fiberCount;fiber++) {
            int run=0;
            for(int ch=0;ch<channelsPerFiber;ch++) {
                boolean occupied=false;
                for(String edge:edges) {
                    if(occupancy.get(edge)[fiber][ch]) {occupied=true;break;}
                }
                run=occupied?0:run+1;
                if(run>=required) {
                    int start=ch-required+1;
                    if(best==null || start<best[0]
                            || (start==best[0] && fiber<best[1]))
                        best=new int[]{start,fiber};
                    break;
                }
            }
        }
        return best;
    }

    private double pathLength(List<Integer> path) {
        double sum=0.0;
        for(int i=0;i<path.size()-1;i++)
            sum+=topology.getEdgeLengthKm(path.get(i),path.get(i+1));
        return sum;
    }

    private static Modulation selectModulation(double lengthKm) {
        for(Modulation m:MODULATIONS) if(lengthKm<=m.reachKm)return m;
        return MODULATIONS[MODULATIONS.length-1];
    }

    private List<String> pathEdgeKeys(List<Integer> path) {
        List<String> keys=new ArrayList<String>();
        for(int i=0;i<path.size()-1;i++) {
            int a=path.get(i),b=path.get(i+1);
            keys.add(Math.min(a,b)+"-"+Math.max(a,b));
        }
        return keys;
    }

    /**
     * For these very small article topologies, enumerating all simple paths is
     * deterministic and easier to audit than embedding another routing stack.
     */
    public List<List<Integer>> kShortestSimplePaths(int source,int destination,int k) {
        List<List<Integer>> all=new ArrayList<List<Integer>>();
        List<Integer> current=new ArrayList<Integer>();
        Set<Integer> visited=new HashSet<Integer>();
        current.add(Integer.valueOf(source)); visited.add(Integer.valueOf(source));
        dfsPaths(source,destination,current,visited,all);
        Collections.sort(all,new Comparator<List<Integer>>() {
            @Override public int compare(List<Integer> a,List<Integer> b) {
                int x=Double.compare(pathLength(a),pathLength(b));
                if(x!=0)return x;
                return comparePath(a,b);
            }
        });
        if(all.size()>k) return new ArrayList<List<Integer>>(all.subList(0,k));
        return all;
    }

    private void dfsPaths(int node,int destination,List<Integer> current,
            Set<Integer> visited,List<List<Integer>> out) {
        if(node==destination) {out.add(new ArrayList<Integer>(current));return;}
        List<ReferenceTopologySpec.Edge> edges=topology.getAdjacentEdges(node);
        Collections.sort(edges,new Comparator<ReferenceTopologySpec.Edge>() {
            @Override public int compare(ReferenceTopologySpec.Edge a,ReferenceTopologySpec.Edge b) {
                return Integer.compare(a.other(node),b.other(node));
            }
        });
        for(ReferenceTopologySpec.Edge edge:edges) {
            int next=edge.other(node);
            if(visited.contains(Integer.valueOf(next)))continue;
            visited.add(Integer.valueOf(next));current.add(Integer.valueOf(next));
            dfsPaths(next,destination,current,visited,out);
            current.remove(current.size()-1);visited.remove(Integer.valueOf(next));
        }
    }

    private static int comparePath(List<Integer> a,List<Integer> b) {
        int n=Math.min(a.size(),b.size());
        for(int i=0;i<n;i++) {int x=Integer.compare(a.get(i),b.get(i));if(x!=0)return x;}
        return Integer.compare(a.size(),b.size());
    }
}
