package gprmcsa.routing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import network.Circuit;
import network.Link;
import network.Mesh;
import network.Node;

/**
 * This class represents the SCSP (Spectrum Continuity based Shortest Path) Routing Algorithm.
 * The implementation of the algorithm was based on:
 * "2015 - Spectrum continuity based routing algorithm for flexible grid optical networks"
 *
 */
public class SCSP implements RoutingAlgorithmInterface {

    public static final String DIV = "-";

    private HashMap<String, Route> routesForAllPairs;

    /**
     * Finds the route.
     * @param circuit the circuit.
     * @param mesh the mesh.
     * @return true if the condition is met; false otherwise.
     */
    @Override
    public boolean findRoute(Circuit circuit, Mesh mesh) {
        if (routesForAllPairs == null) {
        	routesForAllPairs = new HashMap<String, Route>();
        }

        Vector<Node> nodeList = mesh.getNodeList();
        Vector<Link> linkList = mesh.getLinkList();
        Node s = getNode(nodeList, circuit.getSource().getName());
        Node d = getNode(nodeList, circuit.getDestination().getName());

        Route route = shortestPaths(circuit, nodeList, linkList, s, d);
        Route routeAux = routesForAllPairs.get(s.getName() + DIV + d.getName());

        if (route != null) {
            circuit.setRoute(route);

            if (routeAux == null || (routeAux != null && route.getDistanceAllLinks() < routeAux.getDistanceAllLinks())) {
        		routesForAllPairs.put(s.getName() + DIV + d.getName(), route);
            }

            return true;
        }

        return false;
    }

    /**
     * Computes the least cost route according to the SCSP algorithm for a pair of source and destination nodes
     *
     * @param circuit Circuit
     * @param nodeList Vector<Node>
     * @param linkList Vector<Link>
     * @param s Node
     * @param d Node
     * @return Route
     */
    private Route shortestPaths(Circuit circuit, Vector<Node> nodeList, Vector<Link> linkList, Node s, Node d) {
    	HashMap<Node, Double> cost = new HashMap<Node, Double>();
		HashMap<Node, Node> father = new HashMap<Node, Node>();
		HashMap<Node, List<Integer>> continuity = new HashMap<Node, List<Integer>>();

		List<Node> q = new ArrayList<Node>();

		int totalNumberOfSlots = linkList.get(0).getCores().get(0).getNumOfSlots();
		int indexCore = circuit.getIndexCore();
		int r = circuit.getModulation().requiredSlots(circuit.getRequiredBitRate());

		// Initialize the vectors
		for(int n = 0; n < nodeList.size(); n++){
			Node node = nodeList.get(n);

			cost.put(node, Double.POSITIVE_INFINITY);
			father.put(node, null);

			q.add(node);
		}

		List<Integer> continuityS = new ArrayList<Integer>(totalNumberOfSlots);
		for(int i = 0; i < totalNumberOfSlots; i++){
			continuityS.add(1);
		}
		continuity.put(s, continuityS);

		cost.put(s, 0.0);
		Node u = s;
		q.remove(s);

		// Q is not empty
		while(!q.isEmpty()){

			// To visit neighbors from u
			Vector<Link> listOfLinks = u.getOxc().getLinksList();
			for(int l = 0; l < listOfLinks.size(); l++){
				Link link = listOfLinks.get(l);

				if(linkList.contains(link)){
					Node v = getNode(nodeList, link.getDestination().getName());

					// Neighbor v from u
					if((v != null) && (q.contains(v))){

						List<Integer> auxVec = createAuxVec(totalNumberOfSlots, link, indexCore, circuit);
						List<Integer> resVec = createResVec(totalNumberOfSlots, continuity.get(u), auxVec);

						Double costV = calculateCost(resVec, r);

						if (costV != Double.POSITIVE_INFINITY) {
							Double lowestCost = cost.get(u) + costV;

							if(cost.get(v) > lowestCost){
								cost.put(v, lowestCost);
								father.put(v, u);
								continuity.put(v, resVec);
							}
						}
					}
				}
			}

			u = getNodeWithLowestCost(q, cost);
			q.remove(u);
		}

		// Building the route from the father
		Vector<Node> listOfNodes = new Vector<Node>();
		Node aux = d;
		if(father.get(aux) != null){
			listOfNodes.add(d);
		}

		while(father.get(aux) != null){
			aux = father.get(aux);
			listOfNodes.add(aux);

			if(aux == s){
				break;
			}
		}

		Vector<Node> listOfNodesAux = new Vector<Node>();
		for(int i = listOfNodes.size() - 1; i >= 0; i--){
			listOfNodesAux.add(listOfNodes.get(i));
		}

		Route route = null;
		if(listOfNodesAux.size() > 0){
			route = new Route(listOfNodesAux);
		}

		return route;
    }

    /**
     * This method creates the auxiliary vector
     *
     * @param totalNumberOfSlots int
     * @param link Link
     * @param core int
     * @param circuit Circuit
     * @return List<Integer
     */
    private List<Integer> createAuxVec(int totalNumberOfSlots, Link link, int indexCore, Circuit circuit){
    	List<Integer> auxVec = new ArrayList<Integer>(totalNumberOfSlots);

    	// Assume all slots are occupied
    	for (int i = 0; i < totalNumberOfSlots; i++){
    		auxVec.add(0);
		}

    	// Returns free slots taking into account the guard band
    	List<int[]> freeSlotsBands = link.getCore(indexCore).getFreeSpectrumBands(circuit.getGuardBand());

    	// Configure the slots that are free
    	for (int f = 0; f < freeSlotsBands.size(); f++) {
    		int[] slotsBands = freeSlotsBands.get(f);

    		for (int s = slotsBands[0] -1; s < slotsBands[1]; s++) {
    			auxVec.set(s, 1);
    		}
    	}

    	return auxVec;
    }

    /**
     * This method creates the res vector
     *
     * @param totalNumberOfSlots int
     * @param continuityU List<Integer>
     * @param auxVec List<Integer>
     * @return List<Integer>
     */
    private List<Integer> createResVec(int totalNumberOfSlots, List<Integer> continuityU, List<Integer> auxVec){
    	List<Integer> resVec = new ArrayList<Integer>(totalNumberOfSlots);

    	for (int i = 0; i < totalNumberOfSlots; i++){
    		if (continuityU.get(i) == 1 && auxVec.get(i) == 1) {
    			resVec.add(1);
    		} else {
    			resVec.add(0);
    		}
    	}

    	return resVec;
    }

    /**
     * This method computes the cost based on resVect
     *
     * @param resVec List<Integer>
     * @param r int
     * @return Double
     */
    private Double calculateCost(List<Integer>resVec, int r) {
    	Double Cij = 0.0;
    	double fij = 0.0;
    	int contNumSlots = 0;

    	for (int i = 0; i < resVec.size(); i++){
    		contNumSlots = 0;

    		for (int j = 0; j < r; j++) {
	    		if (i + j < resVec.size() && resVec.get(i + j) == 1) {
	    			contNumSlots++;
	    		}
    		}

    		if (contNumSlots == r) {
    			fij++;
    		}
    	}

    	Cij = 1.0 / (fij + 1.0);

    	return Cij;
    }

    /**
	 * Method that returns a node of the network by the name entered
	 *
	 * @param nodeList Vector<Node>
	 * @param name String
	 * @return Node
	 */
	private Node getNode(Vector<Node> nodeList, String name) {
		for (int i = 0; i < nodeList.size(); i++) {
			Node tmp = nodeList.get(i);
			if (tmp.getName().equals(name)) {
				return tmp;
			}
		}
		return null;
	}

	/**
	 * Method that returns the node with the lowest cost in the list of costs
	 *
	 * @param q List<Node>
	 * @param costList HashMap<Node, Double>
	 * @return Node
	 */
	private Node getNodeWithLowestCost(List<Node> q, HashMap<Node, Double> costList){
		Node minNode = q.get(0);
		Double minCost = costList.get(minNode);

		for(int i = 1; i < q.size(); i++){
			Node node = q.get(i);
			Double cost = costList.get(node);

			if(cost < minCost){
				minCost = cost;
				minNode = node;
			}
		}

		return minNode;
	}

    /**
	 * Returns the route list for all pairs
	 *
	 * @return Vector<Route>
	 */
	public HashMap<String, Route> getRoutesForAllPairs() {
		return routesForAllPairs;
	}

}
