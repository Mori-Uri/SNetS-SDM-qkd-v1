package gprmcsa.routing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.Vector;

import network.Circuit;
import network.Link;
import network.Mesh;
import network.Node;
import util.IntersectionFreeSpectrum;

/**
 * 
 * The values ​​of k1 and k2 can be entered in the "others" configuration file as shown below.
 * {"variables":{
 *               "k1":"1.0",
 *               "k2":"1.0"
 *              }
 * }
 * 
 * @author Alexandre
 */
public class KBP_v3 implements KRoutingAlgorithmInterface {

	// Used as a separator between the names of nodes
    private static final String DIV = "-";

    // Number of shortest routes to be computed for each pair(s, d)
    private int k;

    // List with the k shortest routes for all pairs (s, d)
    private HashMap<String, List<Route>> routesForAllPairs;
    
    // Used to choose which variables will be applied in calculating the route cost
    private double k1;
    private double k2;
    
    // Maximum link length
    private double linkMaxDistance;
    
    // Used to access information used in calculating route cost
    private Circuit circuit;
    
    // Stores the cost of the routes being computed
    private HashMap<String, Double> routeCost;
    
    /**
     * Constructor
     */
    public KBP_v3() {
        this.k = 3;
        this.routesForAllPairs = new HashMap<>();
        
        this.k1 = 1.0;
    	this.k2 = 1.0;
    }

    /**
     * Compute the k shortest routes for each pair(s, d)
     * 
     * @param mesh Mesha - network topology
     * @param k    int - number of routes to be computed for each pair(s, d)
     */
    public void computeAllRoutes(Mesh mesh, int k) {
    	
    	this.k = k;
    	this.routesForAllPairs = new HashMap<>();
    	
    	Map<String, String> uv = mesh.getOthersConfig().getVariables();
		if(uv.get("k1") != null){
			this.k1 = Integer.parseInt((String)uv.get("k1"));
		}
		if(uv.get("k2") != null){
			this.k2 = Integer.parseInt((String)uv.get("k2"));
		}
		
    	this.linkMaxDistance = 0.0;
    	Vector<Link> linkList = mesh.getLinkList();
    	for(Link link : linkList) {
    		if(link.getDistance() > this.linkMaxDistance) {
    			this.linkMaxDistance = link.getDistance();
    		}
    	}
    }

    /**
     * Compute the k shortest paths between two nodes
     *
     * @param circuit Circuit
     * @param mesh Mesh
     * @return List<Route>
     */
    public List<Route> computeRoutes(Circuit circuit, Mesh mesh) {
    	this.circuit = circuit;
    	Node s = circuit.getSource();
    	Node d = circuit.getDestination();
    	
        Comparator<Route> kbpComparator = new Comparator<Route>() {
            @Override
            public int compare(Route o1, Route o2) {
                int res = getRouteCost(o1).compareTo(getRouteCost(o2));
                if(res==0) res = 1;
                return res;
            }
        };
        
        TreeSet<Route> chosenRoutes = new TreeSet<>(kbpComparator);
        TreeSet<Route> routesUnderConstruction = new TreeSet<>(kbpComparator);
        this.routeCost = new HashMap<String, Double>(); // Reset route costs

        Route r = new Route();
        r.addNode(s);
        routesUnderConstruction.add(r);

        double highestAmongShortest = Double.POSITIVE_INFINITY;

        while (!routesUnderConstruction.isEmpty()) {

            Route expand = routesUnderConstruction.pollFirst();

            if (hasLoop(expand)) continue;

            if (expand.getDestination().equals(d)) { // route completed

                chosenRoutes.add(expand);
                if (chosenRoutes.size() > this.k) { // Already has k chosen routes, should remain only the smallest k
                    Route rl = chosenRoutes.pollLast();
                    highestAmongShortest = getRouteCost(chosenRoutes.last());
                }
                continue;
                
            } else {
                if (getRouteCost(expand) <= highestAmongShortest) { //  Search more routes from this

                    for (Node no : mesh.getAdjacentsNodes(expand.getDestination())) {

                        Route rAux = expand.clone();
                        rAux.addNode(no);

                        routesUnderConstruction.add(rAux);
                    }
                }
            }
        }

        return new ArrayList<Route>(chosenRoutes);
    }
    
    /**
     * Calculate the cost of a route
     * 
     * @param route Route
     * @return Double
     */
    private Double getRouteCost(Route route){
    	// Checks if there are no links in the route
    	if(route.getLinkList().size() == 0){
    		return Double.POSITIVE_INFINITY;
    	}
    	
    	// Checks if a cost has already been computed for this route
    	Double rCost = this.routeCost.get(route.getRouteInString());
    	if(rCost != null){
    		return rCost;
    	}
    	
    	// Initialize the variables
        double cost = Double.POSITIVE_INFINITY;
        double sumDist = 0.0;
        double sumUtil = 0.0;
        
        int indexCore = this.circuit.getIndexCore();
        
		for(Link link: route.getLinkList()){
        	sumDist += link.getDistance() / this.linkMaxDistance;
        	sumUtil += link.getCore(indexCore).getUtilization();
        }
		
		// Compute the cost of the route
        cost = (k1 * sumDist) + (k2 * sumUtil); //Adicionar crosstalk aqui?
    	
    	// Save the cost for this route
    	this.routeCost.put(route.getRouteInString(), cost);
        
        return cost;
    }

    /**
     * Check if a given route has a loop
     * 
     * @param r Route
     * @return boolean
     */
    private boolean hasLoop(Route r) {
        HashSet<String> nos = new HashSet<>();
        for (Node n : r.getNodeList()) {
            if (nos.contains(n.getName()))
            	return true;
            nos.add(n.getName());
        }
        return false;
    }

    /**
     * Returns the k shortest paths between two nodes
     * 
     * @param n1 Node
     * @param n2 Node
     * @return List<Route>
     */
    public List<Route> getRoutes(Node n1, Node n2) {
        return this.routesForAllPairs.get(n1.getName() + DIV + n2.getName());
    }

    /**
	 * Returns the route list for all pairs
	 * 
	 * @return Vector<Route>
	 */
    public HashMap<String, List<Route>> getRoutesForAllPairs() {
		return routesForAllPairs;
	}
}
