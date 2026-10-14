package gprmcsa.integrated;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Vector;

import gprmcsa.coreSpectrumAssignment.CoreAndSpectrumAssignmentAlgorithmInterface;
import gprmcsa.modulation.Modulation;
import gprmcsa.routing.KRoutingAlgorithmInterface;
import gprmcsa.routing.Route;
import network.Circuit;
import network.ControlPlane;
import network.Link;
import network.Node;

/**
 * Implementation of a modified version of the Dijkstra's shortest paths algorithm
 *
 * @author Alexandre
 */
public class ModifiedDijkstraPathsComputation implements IntegratedRMLSAAlgorithmInterface {

	//private ModulationSelectionAlgorithmInterface modulationSelection;
    private CoreAndSpectrumAssignmentAlgorithmInterface coreAndSpectrumAssignment;

	/**
	 * Returns the rsa.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @return true if the condition is met; false otherwise.
	 */
	@Override
	public boolean rsa(Circuit circuit, ControlPlane cp) {
        //if (modulationSelection == null){
        //	modulationSelection = cp.getModulationSelection();
        //}
        if(coreAndSpectrumAssignment == null){
        	coreAndSpectrumAssignment = cp.getCoreAndSpectrumAssignment();
		}

        Vector<Node> nodeList = cp.getMesh().getNodeList();
        Vector<Link> linkList = cp.getMesh().getLinkList();
        Node s = getNode(nodeList, circuit.getSource().getName());
        Node d = getNode(nodeList, circuit.getDestination().getName());

        Route route = PathComputation(circuit, nodeList, linkList, s, d, cp);
        circuit.setRoute(route);

        if(route != null){
        	return searchForMCSASolution(circuit, route, cp);
        }

        return false;
	}

	/**
	 * Modified version of Dijkstra's shortest path algorithm
	 *
	 * @param circuit Circuit
	 * @param nodeList Vector<Node>
	 * @param linkList Vector<Link>
	 * @param s Node
	 * @param d Node
	 * @param cp ControlPlane
	 * @return Route
	 */
	private Route PathComputation(Circuit circuit, Vector<Node> nodeList, Vector<Link> linkList, Node s, Node d, ControlPlane cp){
		HashMap<Node, Double> dist = new HashMap<Node, Double>(); //distance from the source node to node i
		HashMap<Node, Node> previous = new HashMap<Node, Node>(); //node prior to node i

		Node lastNode = null; //for error correction

		List<Node> q = new ArrayList<Node>(); //list of unvisited nodes

		for(int n = 0; n < nodeList.size(); n++){
			Node node = nodeList.get(n);

			dist.put(node, Double.MAX_VALUE);
			previous.put(node, null);

			q.add(node);
		}

		dist.put(s, 0.0);

		while(!q.isEmpty()){
			Node u = getNodeMinDist(q, dist);
			q.remove(u);

			if(dist.get(u) == Double.MAX_VALUE){
				break;
			}

			Vector<Link> listOfLinks = u.getOxc().getLinksList();
			for(int l = 0; l < listOfLinks.size(); l++){
				Link link = listOfLinks.get(l);

				Node v = getNode(nodeList, link.getDestination().getName());
				if((v != null) && (q.contains(v))){
					Vector<Node> listOfNodesTemp = new Vector<Node>();
					listOfNodesTemp.add(v);
					listOfNodesTemp.add(u);

					Node aux = u;
					while(previous.get(aux) != null){
						aux = previous.get(aux);
						listOfNodesTemp.add(aux);
					}

					Vector<Node> listOfNodesAuxTemp = new Vector<Node>();
					for(int i = listOfNodesTemp.size() - 1; i >= 0; i--){
						listOfNodesAuxTemp.add(listOfNodesTemp.get(i));
					}
					Route routeTemp = new Route(listOfNodesAuxTemp);

					if(searchForMCSASolution(circuit, routeTemp, cp)) {
						Double cust = dist.get(u) + link.getDistance();

						if(cust < dist.get(v)){
							dist.put(v, cust);
							previous.put(v, u);

							lastNode = u;
						}
					}
				}
			}
		}

		Vector<Node> listOfNodes = new Vector<Node>();
		Node aux = d;
		if(previous.get(aux) != null){
			listOfNodes.add(d);
		}

		while(previous.get(aux) != null){
			aux = previous.get(aux);
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

		if(route == null){
			//complete the part of the missing route to the destination
			route = completeRoute(nodeList, linkList, s, d, lastNode, previous);
		}

		return route;
	}

	/**
	 * Method that seeks to select modulation, core and spectrum.
	 * This method also checks QoT.
	 *
	 * @param circuit Circuit
	 * @param route Route
	 * @param cp ControlPlane
	 * @return boolean
	 */
	private boolean searchForMCSASolution(Circuit circuit, Route route, ControlPlane cp) {
		List<Modulation> avaliableModulations = cp.getMesh().getAvaliableModulations();

		circuit.setRoute(route);

		// Modulation, core and band chosen
    	Modulation chosenMod = null;
        int chosenCore = -1;
        int chosenBand[] = null;

        // to avoid metrics error
  		Modulation checkMod = null;
  		int checkCore = -1;
  		int checkBand[] = null;

  		Modulation checkMod2 = null;
  		int checkCore2 = -1;
  		int checkBand2[] = null;

  		double minRouteCost = Double.MAX_VALUE; //Cost of the MCSA solution

    	// Begins with the most spectrally efficient modulation format
		for (int m = avaliableModulations.size()-1; m >= 0; m--) {
			Modulation mod = avaliableModulations.get(m);
			circuit.setModulation(mod);

        	int slotsNumber = mod.requiredSlots(circuit.getRequiredBitRate());

            coreAndSpectrumAssignment.assignCoreAndSpectrum(slotsNumber, circuit, cp);
            int core = circuit.getIndexCore();
            int band[] = circuit.getSpectrumAssigned();

            if (band != null) {
        		checkMod = mod;
        		checkCore = core;
            	checkBand = band;

            	// Check the physical layer
        		boolean QoT = cp.isAdmissibleOSNR(circuit);
        		boolean XT = cp.isAdmissibleCrosstalk(circuit);

        		if (QoT && XT) { // QoT and XT are acceptable
        			checkMod2 = mod;
            		checkCore2 = core;
                	checkBand2 = band;

                	// Checks the QoT and XT for others circuits
            		boolean QoTO = cp.isAdmissibleOSNRInOther(circuit);
	                boolean XTO = cp.isAdmissibleCrosstalkInOther(circuit);

	                if (QoTO && XTO) { // QoTO and XTO are acceptable

                    	double cost = circuit.getXt(); // Using crosstalk as solution cost
	                	if (cost < minRouteCost) { // Select the solution with the lowest cost
	                		minRouteCost = cost;

		                	chosenMod = mod;
	    	                chosenCore = core;
	                    	chosenBand = band;
	                	}
                	}
            	}
            } //core and spectrum
		} //modulations

        if (chosenMod != null) { // If a modulation with acceptable QoT and QoTO was found
            circuit.setRoute(route);
            circuit.setModulation(chosenMod);
            circuit.setIndexCore(chosenCore);
            circuit.setSpectrumAssigned(chosenBand);

            return true;

        } else if(checkMod2 != null) {
            circuit.setRoute(route);
            circuit.setModulation(checkMod2);
            circuit.setIndexCore(checkCore2);
            circuit.setSpectrumAssigned(checkBand2);

            return false;

        } else {
        	if(checkMod == null){
				checkMod = avaliableModulations.get(0);
				checkCore = new Random().nextInt(cp.getMesh().getLinkList().get(0).getNumberOfCores());
			}
            circuit.setRoute(route);
            circuit.setModulation(checkMod);
            circuit.setIndexCore(checkCore);
            circuit.setSpectrumAssigned(checkBand);

            return false;
        }
	}

	/**
	 * Method that completes a route to the destination
	 *
	 * @param nodeList Vector<Node>
	 * @param linkList Vector<Link>
	 * @param s Node
	 * @param d Node
	 * @param lastNode Node
	 * @param previous HashMap<Node, Node>
	 * @return Route
	 */
	private Route completeRoute(Vector<Node> nodeList, Vector<Link> linkList, Node s, Node d, Node lastNode, HashMap<Node, Node> previous){
		Vector<Node> nodeListP1 = new Vector<Node>();
		Node aux = lastNode;
		if(previous.get(aux) != null){
			nodeListP1.add(lastNode);
		}

		while(previous.get(aux) != null){
			aux = previous.get(aux);
			nodeListP1.add(aux);

			if(aux == s){
				break;
			}
		}

		if(nodeListP1.size() > 0){
			Vector<Link> linkListRem = new Vector<Link>();
			for(int i = 0; i < nodeListP1.size() - 1; i++){
				Link link = nodeListP1.get(i).getOxc().linkTo(nodeListP1.get(i + 1).getOxc());
				linkListRem.add(link);
			}

			Vector<Link> newLinkList = new Vector<Link>();
			for(int i = 0; i < linkList.size(); i++){
				Link link = linkList.get(i);
				if(!linkListRem.contains(link)){
					newLinkList.add(link);
				}
			}

			Route routeP2 = Dijkstra(nodeList, newLinkList, lastNode, d, 1);
			Vector<Node> nodeListP2 = routeP2.getNodeList();

			Vector<Node> listOfNodesAux = new Vector<Node>();
			for(int i = nodeListP1.size() - 1; i >= 0 ; i--){
				listOfNodesAux.add(nodeListP1.get(i));
			}

			for(int i = 1; i < nodeListP2.size(); i++){
				listOfNodesAux.add(nodeListP2.get(i));
			}

			boolean flagRepetition = false;
			for(int i = 0; i < nodeListP1.size(); i++){
				Node nodeI = nodeListP1.get(i);
				int cont = 0;
				for(int j = 0; j < listOfNodesAux.size(); j++){
					Node nodeJ = listOfNodesAux.get(j);
					if(nodeI == nodeJ){
						cont++;
					}
				}
				if(cont > 1){
					flagRepetition = true;
				}
			}

			for(int i = 1; i < nodeListP2.size(); i++){
				Node nodeI = nodeListP2.get(i);
				int cont = 0;
				for(int j = 0; j < listOfNodesAux.size(); j++){
					Node nodeJ = listOfNodesAux.get(j);
					if(nodeI == nodeJ){
						cont++;
					}
				}
				if(cont > 1){
					flagRepetition = true;
				}
			}

			Route routeTemp = null;
			if(flagRepetition){
				routeTemp = Dijkstra(nodeList, linkList, lastNode, d, 1);

			}else{
				routeTemp = new Route(listOfNodesAux);
			}

			return routeTemp;
		}

		return null;
	}

	/**
	 * Method that implements Dijkstra's shortest path algorithm
	 *
	 * @param nodeList Vector<Node>
	 * @param linkList Vector<Link>
	 * @param s Node
	 * @param d Node
	 * @param typeCost int
	 * @return Route
	 */
	private Route Dijkstra(Vector<Node> nodeList, Vector<Link> linkList, Node s, Node d, int typeCost){
		HashMap<Node, Double> dist = new HashMap<Node, Double>();
		HashMap<Node, Node> previous = new HashMap<Node, Node>();

		List<Node> q = new ArrayList<Node>();

		for(int n = 0; n < nodeList.size(); n++){
			Node node = nodeList.get(n);

			dist.put(node, Double.MAX_VALUE);
			previous.put(node, null);

			q.add(node);
		}

		dist.put(s, 0.0);

		while(!q.isEmpty()){
			Node u = getNodeMinDist(q, dist);
			q.remove(u);

			if(dist.get(u) == Double.MAX_VALUE){
				break;
			}

			Vector<Link> listOfLinks = u.getOxc().getLinksList();
			for(int l = 0; l < listOfLinks.size(); l++){
				Link link = listOfLinks.get(l);

				if(linkList.contains(link)){
					Node v = getNode(nodeList, link.getDestination().getName());

					if((v != null) && (q.contains(v))){
						Double linkCost = link.getCost();
						if(typeCost == 1){
							linkCost = link.getDistance();
						}
						Double cust = dist.get(u) + linkCost;
						if(cust < dist.get(v)){
							dist.put(v, cust);
							previous.put(v, u);
						}
					}
				}
			}
		}

		Vector<Node> listOfNodes = new Vector<Node>();
		Node aux = d;
		if(previous.get(aux) != null){
			listOfNodes.add(d);
		}

		while(previous.get(aux) != null){
			aux = previous.get(aux);
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
	 * Method that rotates the node with the lowest cost in a list of costs
	 *
	 * @param q List<Node>
	 * @param dist HashMap<Node, Double>
	 * @return Node
	 */
	private Node getNodeMinDist(List<Node> q, HashMap<Node, Double> dist){
		Node minNode = q.get(0);
		Double minCust = dist.get(minNode);

		for(int i = 1; i < q.size(); i++){
			Node node = q.get(i);
			Double cust = dist.get(node);

			if(cust < minCust){
				minCust = cust;
				minNode = node;
			}
		}

		return minNode;
	}

    /**
	 * Returns the routing algorithm
	 *
	 * @return KRoutingAlgorithmInterface
	 */
	@Override
	public KRoutingAlgorithmInterface getRoutingAlgorithm() {
		return null;
	}
}
