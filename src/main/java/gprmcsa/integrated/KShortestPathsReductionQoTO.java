package gprmcsa.integrated;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import gprmcsa.coreSpectrumAssignment.CoreAndSpectrumAssignmentAlgorithmInterface;
import gprmcsa.modulation.Modulation;
import gprmcsa.modulation.ModulationSelectionAlgorithmInterface;
import gprmcsa.routing.KRoutingAlgorithmInterface;
import gprmcsa.routing.Route;
import gprmcsa.spectrumAssignment.SpectrumAssignmentAlgorithmInterface;
import network.Circuit;
import network.ControlPlane;
import network.Link;

/**
 * Implementation based on the K Shortest Paths Reduction QoTO (KSP-RQoTO) algorithm presented in:
 *  - An Efficient IA-RMLSA Algorithm for Transparent Elastic Optical Networks (2017)
 *
 * This version pre-establish the circuit under analysis to verify the impact on other
 * circuits already active in the network
 *
 * KSP-RQoTO algorithm uses the sigma parameter to choose modulation format.
 * The value of sigma must be entered in the configuration file "others" as shown below.
 * {"variables":{
 *               "sigma":"0.5"
 *               }
 * }
 *
 * @author Alexandre
 */
public class KShortestPathsReductionQoTO  implements IntegratedRMLSAAlgorithmInterface {

    private static int k = 4; // Number of candidate routes
	private KRoutingAlgorithmInterface kShortestsPaths;
    private ModulationSelectionAlgorithmInterface modulationSelection;
    private SpectrumAssignmentAlgorithmInterface spectrumAssignment;
    private CoreAndSpectrumAssignmentAlgorithmInterface coreAndSpectrumAssignment;

    // A single sigma value for all pairs
    private double sigma;

    // A sigma value for each pair
	private HashMap<String, HashMap<Route, Double>> sigmaForAllPairs;

	// Used as a separator between the names of nodes
    private static final String DIV = "-";

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
        }
		if (modulationSelection == null){
        	modulationSelection = cp.getModulationSelection(); // Uses the modulation selection algorithm defined in the simulation file

        	//read the sigma value
			Map<String, String> uv = cp.getMesh().getOthersConfig().getVariables();
			sigma = Double.parseDouble((String)uv.get("sigma"));
        }
        if(spectrumAssignment == null){
			spectrumAssignment = cp.getSpectrumAssignment(); // Uses the spectrum assignment algorithm defined in the simulation file
		}
        if(coreAndSpectrumAssignment == null){
        	coreAndSpectrumAssignment = cp.getCoreAndSpectrumAssignment();
		}

        List<Modulation> avaliableModulations = cp.getMesh().getAvaliableModulations();
        List<Route> candidateRoutes = kShortestsPaths.getRoutes(circuit.getSource(), circuit.getDestination());

        Route chosenRoute = null;
        Modulation chosenMod = null;
        int chosenCore = -1;
        int chosenBand[] = {999999, 999999}; // Value never reached

        double chosenWorstDeltaSNR = 0.0;
        String pair = circuit.getPair().getSource().getName() + DIV + circuit.getPair().getDestination().getName();
		double sigmaPair = sigma;

		HashMap<Modulation, Double> routeModWorstDeltaSNR = new HashMap<Modulation, Double>();

		// To avoid metrics error
		Route checkRoute = null;
		Modulation checkMod = null;
		int checkCore = -1;
		int checkBand[] = null;

		for (int r = 0; r < candidateRoutes.size(); r++) {
			Route routeTemp = candidateRoutes.get(r);
			circuit.setRoute(routeTemp);

			if(sigmaForAllPairs != null){
				sigmaPair = sigmaForAllPairs.get(pair).get(routeTemp);
			}

	    	double highestLevel = 0.0;

	    	// First modulation format option
	    	Modulation firstModulation = null;
	    	int firstCore = -1;
			int firstBand[] = null;

	    	// Second modulation format option
	    	Modulation secondModulation = null;
	    	int secondCore = -1;
			int secondBand[] = null;

			for(int m = 0; m < avaliableModulations.size(); m++){
				Modulation mod = avaliableModulations.get(m);
				circuit.setModulation(mod);

				int numberOfSlots = mod.requiredSlots(circuit.getRequiredBitRate());

				coreAndSpectrumAssignment.assignCoreAndSpectrum(numberOfSlots, circuit, cp);
	            int core = circuit.getIndexCore();
	            int band[] = circuit.getSpectrumAssigned();

	            circuit.setIndexCore(core);
	            circuit.setSpectrumAssigned(band);

				if(band != null){
					if(checkRoute == null){
						checkRoute = routeTemp;
						checkMod = mod;
						checkCore = core;
						checkBand = band;
					}

					boolean circuitQoT = cp.isAdmissibleOSNR(circuit);

					if(circuitQoT){
						double circuitDeltaSNR = circuit.getSNR() - mod.getSNRthreshold();

						HashSet<Circuit> circuitList = new HashSet<Circuit>();
						for (Link link : routeTemp.getLinkList()) {
							HashSet<Circuit> circuitsAux = link.getCore(core).getCircuitList();

							for(Circuit circuitTemp : circuitsAux){
								if(!circuit.equals(circuitTemp) && !circuitList.contains(circuitTemp)){
									circuitList.add(circuitTemp);
								}
							}
						}

						boolean othersQoT = true;
						double worstDeltaSNR = Double.MAX_VALUE;

						for(Circuit circuitTemp : circuitList){

							boolean QoT = cp.computeQualityOfTransmission(circuitTemp, circuit, true);
							double deltaSNR = circuitTemp.getSNR() - circuitTemp.getModulation().getSNRthreshold();

							if(deltaSNR < worstDeltaSNR){
								worstDeltaSNR = deltaSNR;
							}

							if(!QoT){ //request with unacceptable QoT, has the worst deltaSNR
								othersQoT = false;
								break;
							}
						}

						routeModWorstDeltaSNR.put(mod, worstDeltaSNR);

						if(othersQoT){ // if you have not made QoT inadmissible from any of the other already active circuits
							if(circuitDeltaSNR >= sigmaPair){ // Tries to choose the modulation format that respects the sigma value
								firstModulation = mod;
								firstCore = core;
								firstBand = band;
							}
							if(mod.getBitsPerSymbol() > highestLevel){ // Save the modulation format with the highest level as the second choice option
								secondModulation = mod;
								secondCore = core;
								secondBand = band;

								highestLevel = mod.getBitsPerSymbol();
							}
						} //QoTO
					} //QoTN
				} //band
			} //modulations

			if((firstModulation == null) && (secondModulation != null)){ // Check the modulation format options
				firstModulation = secondModulation;
				firstCore = secondCore;
				firstBand = secondBand;
			}

			if(firstModulation != null){
				circuit.setModulation(firstModulation);
				circuit.setIndexCore(firstCore);
				circuit.setSpectrumAssigned(firstBand);

				if(firstBand != null){
					double worstDeltaSNR = routeModWorstDeltaSNR.get(firstModulation);

					if((firstBand[0] < chosenBand[0]) && (worstDeltaSNR >= chosenWorstDeltaSNR)){
						chosenRoute = routeTemp;
						chosenMod = firstModulation;
						chosenCore = firstCore;
						chosenBand = firstBand;
						chosenWorstDeltaSNR = worstDeltaSNR;
					}
				}
			}
		} //routes

		if(chosenRoute != null){ // If there is no route chosen is because no available resource was found in any of the candidate routes
			circuit.setRoute(chosenRoute);
			circuit.setModulation(chosenMod);
			circuit.setIndexCore(chosenCore);
			circuit.setSpectrumAssigned(chosenBand);

			return true;

		}else{
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
	 * Returns the routing algorithm
	 *
	 * @return KRoutingAlgorithmInterface
	 */
    public KRoutingAlgorithmInterface getRoutingAlgorithm(){
    	return kShortestsPaths;
    }
}
