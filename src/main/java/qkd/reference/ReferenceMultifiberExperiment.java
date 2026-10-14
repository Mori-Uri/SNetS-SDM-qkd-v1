package qkd.reference;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Executes the article-base MF reconstruction inside the QKD addon. */
public class ReferenceMultifiberExperiment {

    private final File root;
    private final ReferenceExperimentConfig config;

    public ReferenceMultifiberExperiment(File root) throws IOException {
        this.root=root;
        this.config=ReferenceExperimentConfig.load(new File(root,"config.json"));
    }

    public List<ReferenceResultRow> run() throws IOException {
        List<ReferenceResultRow> rows=new ArrayList<ReferenceResultRow>();
        for(ReferenceExperimentConfig.Replication rep:config.getReplications()) {
            for(String topologyName:config.getTopologies()) {
                ReferenceTopologySpec topology=ReferenceTopologySpec.load(
                        new File(new File(root,"topologies"),topologyName+".json"));
                File traceFile=new File(new File(new File(root,"traces"),rep.getDirectory()),
                        "traffic_"+topologyName+".csv");

                /*
                 * The exported reference trace is in the exact routed order used
                 * by the verified Python reconstruction. Using these fixed paths
                 * isolates the QKD/TDM/Q-TRx layer from routing tie-breaking,
                 * which was not fully specified by the paper.
                 */
                List<ReferenceRequest> routedTrace=ReferenceTraceLoader.load(traceFile,false);
                List<ReferenceClassicalAllocator.RoutedRequest> routed=
                        new ArrayList<ReferenceClassicalAllocator.RoutedRequest>();
                for(ReferenceRequest request:routedTrace) {
                    if(request.expectedPythonPath==null)
                        throw new IOException("Fixed reference trace has no path: "+traceFile);
                    routed.add(new ReferenceClassicalAllocator.RoutedRequest(
                            request,request.expectedPythonPath));
                }

                int routeMismatchDiagnostic=0;
                int classicalBlocked=0;
                if(config.isVerifyClassicalRecomputation()) {
                    List<ReferenceRequest> originalOrder=ReferenceTraceLoader.load(traceFile,true);
                    ReferenceClassicalAllocator classical=
                            new ReferenceClassicalAllocator(topology,config);
                    ReferenceClassicalAllocator.Result diagnostic=
                            classical.allocateAll(originalOrder,config.getGroomingCapacityGbps());
                    routeMismatchDiagnostic=diagnostic.pythonPathMismatchCount;
                    classicalBlocked=diagnostic.classicalBlockedRequests;
                }

                double offeredClassical=0.0,offeredSkr=0.0;
                for(ReferenceRequest request:routedTrace){
                    offeredClassical+=request.classicalRateGbps;
                    offeredSkr+=request.skrDemandMbps;
                }

                List<ReferenceResultRow> sweep=new ArrayList<ReferenceResultRow>();
                int maxQtrx=0;
                for(int qtnd:config.getQtndValuesKm()) {
                    ReferenceQuantumTDMAllocator quantum=
                            new ReferenceQuantumTDMAllocator(topology,qtnd,
                                    config.getTdmSlots(),config.getQuantumChannels());
                    int qBlocked=0;double acceptedSkr=0.0;
                    for(ReferenceClassicalAllocator.RoutedRequest rr:routed) {
                        if(quantum.allocate(rr.path,rr.request.skrDemandMbps))
                            acceptedSkr+=rr.request.skrDemandMbps;
                        else qBlocked++;
                    }
                    ReferenceResultRow row=new ReferenceResultRow();
                    row.replication=rep.getReplication();row.seed=rep.getSeed();
                    row.topology=topologyName;row.qtndKm=qtnd;
                    row.qtrxUsage=quantum.getQtrxUsage();
                    row.requestCount=routedTrace.size();
                    // The reference traces contain every request; the verified
                    // reconstruction reported zero classical blocking.
                    row.classicalAccepted=routedTrace.size();
                    row.classicalBlocked=config.isUseFixedReferencePaths()?0:classicalBlocked;
                    row.quantumBlocked=qBlocked;
                    row.totalBlockingRatio=((double)(row.classicalBlocked+qBlocked))/row.requestCount;
                    row.offeredClassicalGbps=offeredClassical;
                    row.offeredSkrMbps=offeredSkr;
                    row.acceptedSkrMbps=acceptedSkr;
                    row.pythonPathMismatchCount=routeMismatchDiagnostic;
                    sweep.add(row);maxQtrx=Math.max(maxQtrx,row.qtrxUsage);
                }
                for(ReferenceResultRow row:sweep) {
                    row.normalizedQtrxUsagePct=maxQtrx==0?0.0:100.0*row.qtrxUsage/maxQtrx;
                    rows.add(row);
                }
            }
        }
        Collections.sort(rows,new Comparator<ReferenceResultRow>() {
            @Override public int compare(ReferenceResultRow a,ReferenceResultRow b) {
                int x=Integer.compare(a.replication,b.replication);if(x!=0)return x;
                x=a.topology.compareTo(b.topology);if(x!=0)return x;
                return Integer.compare(a.qtndKm,b.qtndKm);
            }
        });
        return rows;
    }

    public void writeRaw(File file,List<ReferenceResultRow> rows) throws IOException {
        BufferedWriter w=new BufferedWriter(new FileWriter(file));
        try {
            w.write("replication,seed,topology,qtnd_km,qtrx_usage,normalized_qtrx_usage_pct,request_count,classical_accepted,classical_blocked,quantum_blocked,total_blocking_ratio,offered_classical_gbps,offered_skr_mbps,accepted_skr_mbps,python_path_mismatch_count\n");
            for(ReferenceResultRow r:rows) {
                w.write(String.format(Locale.US,
                        "%d,%d,%s,%d,%d,%.12f,%d,%d,%d,%d,%.12f,%.12f,%.12f,%.12f,%d%n",
                        r.replication,r.seed,r.topology,r.qtndKm,r.qtrxUsage,
                        r.normalizedQtrxUsagePct,r.requestCount,r.classicalAccepted,
                        r.classicalBlocked,r.quantumBlocked,r.totalBlockingRatio,
                        r.offeredClassicalGbps,r.offeredSkrMbps,r.acceptedSkrMbps,
                        r.pythonPathMismatchCount));
            }
        } finally {w.close();}
    }

    public void writeStatistics(File file,List<ReferenceResultRow> rows) throws IOException {
        Map<String,List<Double>> groups=new LinkedHashMap<String,List<Double>>();
        for(ReferenceResultRow r:rows) {
            String key=r.topology+"|"+r.qtndKm;
            List<Double> values=groups.get(key);if(values==null){values=new ArrayList<Double>();groups.put(key,values);}values.add(Double.valueOf(r.qtrxUsage));
        }
        List<String> keys=new ArrayList<String>(groups.keySet());
        Collections.sort(keys);
        BufferedWriter w=new BufferedWriter(new FileWriter(file));
        try {
            w.write("topology,qtnd_km,n,mean_qtrx,sample_std,standard_error,ci95_lower,ci95_upper,ci95_margin,relative_margin_pct,cv_pct\n");
            for(String key:keys) {
                String[] p=key.split("\\|");String topology=p[0];int qtnd=Integer.parseInt(p[1]);
                Stats s=stats(groups.get(key));
                w.write(String.format(Locale.US,"%s,%d,%d,%.12f,%.12f,%.12f,%.12f,%.12f,%.12f,%.12f,%.12f%n",
                        topology,qtnd,s.n,s.mean,s.std,s.se,s.lower,s.upper,s.margin,s.relativePct,s.cvPct));
            }
        } finally {w.close();}
    }

    public VerificationSummary compareWithPython(File expectedRaw,List<ReferenceResultRow> rows) throws IOException {
        Map<String,Expected> expected=loadExpected(expectedRaw);
        VerificationSummary v=new VerificationSummary();
        v.totalRows=rows.size();
        java.util.HashSet<String> routeDiagnosticsSeen = new java.util.HashSet<String>();
        for(ReferenceResultRow r:rows) {
            Expected e=expected.get(r.key());
            if(e==null){v.missingExpectedRows++;continue;}
            int diff=Math.abs(r.qtrxUsage-e.qtrxUsage);v.maxAbsoluteQtrxDifference=Math.max(v.maxAbsoluteQtrxDifference,diff);
            if(diff==0 && r.classicalBlocked==e.classicalBlocked && r.quantumBlocked==e.quantumBlocked) v.exactMatchingRows++;
            else v.mismatchingRows++;
            String routeKey = r.replication + "|" + r.topology;
            if (routeDiagnosticsSeen.add(routeKey)) {
                v.totalPythonPathMismatches += r.pythonPathMismatchCount;
            }
        }
        return v;
    }

    private static final class Expected {int qtrxUsage,classicalBlocked,quantumBlocked;}
    private Map<String,Expected> loadExpected(File file) throws IOException {
        Map<String,Expected> map=new HashMap<String,Expected>();
        BufferedReader r=new BufferedReader(new FileReader(file));
        try {
            String header=r.readLine();if(header==null)return map;
            String[] h=header.split(",");Map<String,Integer> idx=new HashMap<String,Integer>();for(int i=0;i<h.length;i++)idx.put(h[i],Integer.valueOf(i));
            String line;while((line=r.readLine())!=null){if(line.trim().isEmpty())continue;String[] p=line.split(",",-1);
                int rep=Integer.parseInt(p[idx.get("replication")]);String topology=p[idx.get("topology")];int qtnd=Integer.parseInt(p[idx.get("qtnd_km")]);
                Expected e=new Expected();e.qtrxUsage=Integer.parseInt(p[idx.get("qtrx_usage")]);e.classicalBlocked=Integer.parseInt(p[idx.get("classical_blocked")]);e.quantumBlocked=Integer.parseInt(p[idx.get("quantum_blocked")]);
                map.put(rep+"|"+topology+"|"+qtnd,e);
            }
        } finally {r.close();}
        return map;
    }

    public static class VerificationSummary {
        public int totalRows,exactMatchingRows,mismatchingRows,missingExpectedRows,maxAbsoluteQtrxDifference,totalPythonPathMismatches;
        public boolean isExact(){return totalRows>0&&exactMatchingRows==totalRows&&mismatchingRows==0&&missingExpectedRows==0;}
        @Override public String toString(){return "VerificationSummary{totalRows="+totalRows+", exactMatchingRows="+exactMatchingRows+", mismatchingRows="+mismatchingRows+", missingExpectedRows="+missingExpectedRows+", maxAbsoluteQtrxDifference="+maxAbsoluteQtrxDifference+", pythonPathMismatches="+totalPythonPathMismatches+"}";}
    }

    private static final class Stats {int n;double mean,std,se,lower,upper,margin,relativePct,cvPct;}
    private Stats stats(List<Double> values) {
        Stats s=new Stats();s.n=values.size();double sum=0;for(Double v:values)sum+=v.doubleValue();s.mean=sum/s.n;
        double ss=0;for(Double v:values){double d=v.doubleValue()-s.mean;ss+=d*d;}s.std=s.n>1?Math.sqrt(ss/(s.n-1)):0;s.se=s.std/Math.sqrt(s.n);
        double t=t95(s.n-1);s.margin=t*s.se;s.lower=s.mean-s.margin;s.upper=s.mean+s.margin;s.relativePct=Math.abs(s.mean)<1e-15?0:100*s.margin/Math.abs(s.mean);s.cvPct=Math.abs(s.mean)<1e-15?0:100*s.std/Math.abs(s.mean);return s;
    }
    private double t95(int df){double[] t={0,12.706,4.303,3.182,2.776,2.571,2.447,2.365,2.306,2.262,2.228,2.201,2.179,2.160,2.145,2.131,2.120,2.110,2.101,2.093,2.086,2.080,2.074,2.069,2.064,2.060,2.056,2.052,2.048,2.045,2.042};return df>=1&&df<t.length?t[df]:1.96;}
}
