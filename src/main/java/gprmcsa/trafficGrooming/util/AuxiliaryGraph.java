package gprmcsa.trafficGrooming.util;

import java.util.*;


/**
 * Represents the AuxiliaryGraph component.
 */
public class AuxiliaryGraph {

    //Graph
    HashMap<String,HashMap<String, List<Edge>>> graph;

    /*
    Standard constructor. No nodes or edges are build.
     */
    /**
     * Creates a new instance of AuxiliaryGraph.
     */
    public AuxiliaryGraph(){
        graph = new HashMap<>();
    }

    /*
    Create nodes between 1 (inclusive) and $numberOfNodes (inclusive)
     */
    /**
     * Creates a new instance of AuxiliaryGraph.
     * @param numberOfNodes the numberOfNodes.
     */
    public AuxiliaryGraph(int numberOfNodes){
        graph = new HashMap<>();
        int i,j;
        for(i=1;i<=numberOfNodes;i++){
            HashMap<String,List<Edge>> h = new HashMap<>();
            for(j=1;j<=numberOfNodes;j++){
                if(i!=j){
                    List<Edge> l = new ArrayList<>();
                    h.put(""+j,l);
                }
            }
            graph.put(""+i,h);
        }
    }

    /*
    Setup nodes by the given list $nodes;
     */
    /**
     * Creates a new instance of AuxiliaryGraph.
     * @param nodes the nodes.
     */
    public AuxiliaryGraph(List<Object> nodes){
        graph = new HashMap<>();
        for (Object n1:nodes) {
        	//System.out.println("----");
        	//System.out.println(n1.toString());
        	//System.out.println("***");
        	HashMap<String,List<Edge>> h = new HashMap<>();
            for (Object n2:nodes) {
                if(!n1.equals(n2)){
                	//System.out.println(n2.toString());
                    List<Edge> l = new ArrayList<>();
                    h.put(n2.toString(),l);
                }
            }
            //System.out.println("----");
            graph.put(n1.toString(),h);
        }
        //System.out.println("----");
    }

    /*
    Temporary method, only for debug.
     */
    /**
     * Returns the solutions to string.
     * @param sol the sol.
     * @return the result of the operation.
     */
    public String solutionsToString(List<List<Edge>> sol){
        String res = "";

        for (List<Edge> le:sol) {
            res = res + '\n';
            for (Edge e : le) {
                res = res + e.getSource() + "-" + e.getDestination() + "    ";
            }
        }

        return res;
    }

    /**
     * Returns the djk.
     * @param S the S.
     * @param D the D.
     * @return the result of the operation.
     */
    public List<List<Edge>> djk(String S, String D){
        List<List<Edge>> solutions = new ArrayList<>();
        HashMap<String,List<Edge>> distanceVectors = new HashMap<>();
        HashMap<String,Double> distanceVectorsCost = new HashMap<>();

        HashSet<String> toVisit = new HashSet<>();
        HashSet<String> visited = new HashSet<>();

        distanceVectors.put(S,new ArrayList<>());
        distanceVectorsCost.put(S,0d);

        toVisit.add(S);

        while(!toVisit.isEmpty()){
            String n = minCostSet(toVisit,distanceVectorsCost);
            toVisit.remove(n);
            visited.add(n);

            double costUntilN = distanceVectorsCost.get(n);
            List<Edge>dvUntilN = distanceVectors.get(n);
            List<String> nb = new ArrayList<>(graph.get(n).keySet());
            for (String v: nb) {
                if(visited.contains(v)) continue; //don't analises
                Double costUntilV = distanceVectorsCost.get(v);
                if(costUntilV==null)costUntilV=Double.MAX_VALUE;

                List<Edge> edges = graph.get(n).get(v);
                for (Edge e:edges) {
                    double newCost = costUntilN + e.getCost();
                    if(newCost<costUntilV){
                        ArrayList<Edge> dvUntilV = new ArrayList<>();
                        dvUntilV.addAll(dvUntilN);
                        dvUntilV.add(e);
                        distanceVectors.put(v,dvUntilV);
                        distanceVectorsCost.put(v,newCost);
                        if(!v.equals(D)){
                            toVisit.add(v);
                        }
                    }
                    if(v.equals(D)){//possvel soluo
                        ArrayList<Edge> dvUntilV = new ArrayList<>();
                        dvUntilV.addAll(dvUntilN);
                        dvUntilV.add(e);
                        solutions.add(dvUntilV);
                    }
                }
            }
        }

        return solutions;
    }

    /**
     * Returns the min cost set.
     * @param set the set.
     * @param distanceVectorsCost the distanceVectorsCost.
     * @return the result of the operation.
     */
    private String minCostSet(Set<String> set, HashMap<String,Double> distanceVectorsCost){
        Iterator<String>it = set.iterator();
        String min = it.next();
        Double minCost = distanceVectorsCost.get(min);
        while(it.hasNext()){
            String n = it.next();
            Double cost = distanceVectorsCost.get(n);
            if(cost<minCost){
                min = n;
                minCost = cost;
            }
        }

        return min;
    }

    /**
     * Adds the edge.
     * @param e the e.
     */
    public void addEdge(Edge e){
        graph.get(e.getSource()).get(e.getDestination()).add(e);
    }

    public interface Edge{

        /**
         * Returns the cost.
         * @return the cost.
         */
        public double getCost();

        /**
         * Returns the source.
         * @return the source.
         */
        String getSource();

        /**
         * Returns the destination.
         * @return the destination.
         */
        String getDestination();
    }



}


