package gprmcsa.integrated;

import java.util.List;
import java.util.Map;
import java.util.Random;

import gprmcsa.coreSpectrumAssignment.CoreAndSpectrumAssignmentAlgorithmInterface;
import gprmcsa.modulation.Modulation;
import gprmcsa.routing.KRoutingAlgorithmInterface;
import gprmcsa.routing.Route;
import network.Circuit;
import network.ControlPlane;
import network.Link;

/**
 * Represents the KShortestPathsCoreAndSpectrumAssignment_v3 component.
 */
public class KShortestPathsCoreAndSpectrumAssignment_v3 implements IntegratedRMLSAAlgorithmInterface {

	private int k = 3; //This algorithm uses 3 alternative paths
	private KRoutingAlgorithmInterface kShortestsPaths; //For routing algorithms with more than one route
    //private ModulationSelectionAlgorithmInterface modulationSelection;
    private CoreAndSpectrumAssignmentAlgorithmInterface coreAndSpectrumAssignment;

    private int costType = 0;

	/**
	 * Returns the rsa.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @return true if the condition is met; false otherwise.
	 */
	@Override
	public boolean rsa(Circuit circuit, ControlPlane cp) {
		if (kShortestsPaths == null){
			// To read the value of k from the "others" file
        	Map<String, String> uv = cp.getMesh().getOthersConfig().getVariables();
			if(uv.get("k") != null) {
				k = Integer.parseInt((String)uv.get("k"));
			}

        	kShortestsPaths = cp.getKRouting();
        	kShortestsPaths.computeAllRoutes(cp.getMesh(), k);

        	if(uv.get("costType") != null) {
				costType = Integer.parseInt((String)uv.get("costType"));
        	}
        }
        //if (modulationSelection == null){
        //	modulationSelection = cp.getModulationSelection();
        //}
        if(coreAndSpectrumAssignment == null){
        	coreAndSpectrumAssignment = cp.getCoreAndSpectrumAssignment();
		}

        List<Modulation> avaliableModulations = cp.getMesh().getAvaliableModulations();
        List<Route> candidateRoutes = kShortestsPaths.getRoutes(circuit.getSource(), circuit.getDestination());

        // Route, modulation, core and band chosen
        Route chosenRoute = null;
        Modulation chosenMod = null;
        int chosenCore = -1;
        int chosenBand[] = null;

        // To avoid metrics error
  		Route checkRoute = null;
  		Modulation checkMod = null;
  		int checkCore = -1;
  		int checkBand[] = null;

  		Route checkRoute2 = null;
  		Modulation checkMod2 = null;
  		int checkCore2 = -1;
  		int checkBand2[] = null;

  		double minRouteCost = Double.MAX_VALUE;

        for (Route route : candidateRoutes) {
            circuit.setRoute(route);

            // Begins with the most spectrally efficient modulation format
    		for (int m = avaliableModulations.size()-1; m >= 0; m--) {
    			Modulation mod = avaliableModulations.get(m);
    			circuit.setModulation(mod);

    			int slotsNumber = mod.requiredSlots(circuit.getRequiredBitRate());

	            coreAndSpectrumAssignment.assignCoreAndSpectrum(slotsNumber, circuit, cp);
	            int core = circuit.getIndexCore();
	            int band[] = circuit.getSpectrumAssigned();

	            if (band != null) {
	    			checkRoute = route;
            		checkMod = mod;
            		checkCore = core;
	            	checkBand = band;

	            	// Check the physical layer
            		boolean QoT = cp.isAdmissibleOSNR(circuit);
            		boolean XT = cp.isAdmissibleCrosstalk(circuit);

	    			if (QoT && XT) { // QoT and XT are acceptable
	    				checkRoute2 = route;
		    			checkMod2 = mod;
		    			checkCore2 = core;
	            		checkBand2 = band;

	            		// Checks the QoT and XT for others circuits
	            		boolean QoTO = cp.isAdmissibleOSNRInOther(circuit);
	            		boolean XTO = cp.isAdmissibleCrosstalkInOther(circuit);

		                if (QoTO && XTO) {  // QoTO and XTO are acceptable

		                	double cost = calculateRouteCost(circuit, route, mod, core, band);
		                	if (cost < minRouteCost) { // Select the route with the lowest cost
		                		minRouteCost = cost;

			                	chosenRoute = route;
				                chosenMod = mod;
				                chosenCore = core;
				                chosenBand = band;
		                	}
		                }
	            	}
	            } //core and spectrum
            } //modulations
        } //routes

        if (chosenRoute != null) { //If there is no route chosen is why no available resource was found on any of the candidate routes
            circuit.setRoute(chosenRoute);
            circuit.setModulation(chosenMod);
            circuit.setIndexCore(chosenCore);
            circuit.setSpectrumAssigned(chosenBand);

            return true;

        } else if(checkRoute2 != null) {
            circuit.setRoute(checkRoute2);
            circuit.setModulation(checkMod2);
            circuit.setIndexCore(checkCore2);
            circuit.setSpectrumAssigned(checkBand2);

            return false;

        } else {
        	if(checkRoute == null){
				checkRoute = candidateRoutes.get(0);
				checkMod = avaliableModulations.get(0);
				checkCore = new Random().nextInt(cp.getMesh().getLinkList().get(0).getNumberOfCores());
			}
            circuit.setRoute(checkRoute);
            circuit.setModulation(checkMod);
            circuit.setIndexCore(checkCore);
            circuit.setSpectrumAssigned(checkBand);

            return false;
        }
	}

	/**
	 * Returns the cost of the route
	 *
	 * @param circuit Circuit
	 * @param route Route
	 * @param mod Modulation
	 * @param core int
	 * @param sa int[]
	 * @return double
	 */
	private double calculateRouteCost(Circuit circuit, Route route, Modulation mod, int core, int sa[]) {
		double cost = 0.0;

		if (costType == 0) {
			cost = circuit.getXt();

		} else {
			double x = 0.0;
			for (Link link : route.getLinkList()) {
				x += link.getCore(core).getUsedSlots();
			}

			cost = x / route.getLinkList().size();
		}

		return cost;
	}

	/**
	 * Returns the routing algorithm
	 *
	 * @return KRoutingAlgorithmInterface
	 */
	@Override
	public KRoutingAlgorithmInterface getRoutingAlgorithm() {
		return kShortestsPaths;
	}

}
