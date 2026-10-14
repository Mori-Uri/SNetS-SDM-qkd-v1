package gprmcsa.integrated;

import java.util.List;
import java.util.Map;
import java.util.Random;

import gprmcsa.modulation.Modulation;
import gprmcsa.routing.KRoutingAlgorithmInterface;
import gprmcsa.routing.Route;
import network.Circuit;
import network.ControlPlane;
import network.Crosstalk;
import network.Link;
import network.PhysicalLayer;
import util.IntersectionFreeSpectrum;

/**
 * Represents the KSPXT component.
 */
public class KSPXT implements IntegratedRMLSAAlgorithmInterface {

	private int k = 3; //This algorithm uses 3 alternative paths
	private KRoutingAlgorithmInterface kShortestsPaths; //For routing algorithms with more than one route
    //private ModulationSelectionAlgorithmInterface modulationSelection;
    //private CoreAndSpectrumAssignmentAlgorithmInterface coreAndSpectrumAssignment;

    private double alpha1 = 0.5; //Applied to XT
    private double alpha2 = 0.5; //Applied to Utilization

    private double maxXT = Crosstalk.lowestXT;

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

        	if(uv.get("alpha1") != null) {
        		alpha1 = Double.parseDouble((String)uv.get("alpha1"));
        	}

        	if(uv.get("alpha2") != null) {
        		alpha2 = Double.parseDouble((String)uv.get("alpha2"));
        	}
        }
        //if(modulationSelection == null){
        //	modulationSelection = cp.getModulationSelection();
        //}
        //if(coreAndSpectrumAssignment == null){
        //	coreAndSpectrumAssignment = cp.getCoreAndSpectrumAssignment();
		//}

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

  		int numberOfCores = cp.getMesh().getLinkList().get(0).getNumberOfCores();
  		double minRouteCost = Double.POSITIVE_INFINITY;

  		// Go through all candidate routes
        for (Route route : candidateRoutes) {
            circuit.setRoute(route);

            // Begins with the most spectrally efficient modulation format
    		for (int m = avaliableModulations.size()-1; m >= 0; m--) {
    			Modulation mod = avaliableModulations.get(m);
    			circuit.setModulation(mod);

    			int slotsNumber = mod.requiredSlots(circuit.getRequiredBitRate());

    			// It goes through the cores starting from the highest index to the lowest index
    			for (int core = numberOfCores-1; core >= 0; core--) {
    				circuit.setIndexCore(core);

    	            int band[] = assignSpectrum(slotsNumber, circuit, cp, core);
    	            circuit.setSpectrumAssigned(band);

    	            if (band != null) {
    	    			checkRoute = route;
                		checkMod = mod;
                		checkCore = core;
    	            	checkBand = band;

    	            	// Check the physical layer
                		boolean QoT = cp.isAdmissibleOSNR(circuit);
                		boolean XT = true;
                		if (QoT) { // Only tests XT if it passes through OSNR
                			XT = cp.isAdmissibleCrosstalk(circuit);
                		}

    	    			if (QoT && XT) { // QoT and XT are acceptable
    	    				checkRoute2 = route;
    		    			checkMod2 = mod;
    		    			checkCore2 = core;
    	            		checkBand2 = band;

    	            		// Checks the QoT and XT for others circuits
    	            		boolean QoTO = cp.isAdmissibleOSNRInOther(circuit);
    	            		boolean XTO = true;
    	            		if (QoTO) { // Only tests XT if it passes through OSNR
    	            			XTO = cp.isAdmissibleCrosstalkInOther(circuit);
    	            		}

    	            		if (circuit.getXt() > maxXT) {
    							maxXT = circuit.getXt();
    						}

    		                if (QoTO && XTO) { // QoTO and XTO are acceptable

    		                	double cost = calculateCost(circuit, route, mod, core, band);
    		                	if (cost < minRouteCost) { // Select the route with the lowest cost
    		                		minRouteCost = cost;

    		                		chosenRoute = route;
        			                chosenMod = mod;
        			                chosenCore = core;
        			                chosenBand = band;
    		                	}
    		                }
    	            	}
    	            }//spectrum
    			} //core
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
				checkCore = new Random().nextInt(numberOfCores);
			}
            circuit.setRoute(checkRoute);
            circuit.setModulation(checkMod);
            circuit.setIndexCore(checkCore);
            circuit.setSpectrumAssigned(checkBand);

            return false;
        }
	}

	/**
	 * Returns the cost of the solution
	 *
	 * @param circuit Circuit
	 * @param route Route
	 * @param mod Modulation
	 * @param core int
	 * @param sa int[]
	 * @return double
	 */
	private double calculateCost(Circuit circuit, Route route, Modulation mod, int core, int sa[]) {
		double cost = 0.0;

		double xt = circuit.getXt();
		xt = PhysicalLayer.ratioOfDB(xt) / PhysicalLayer.ratioOfDB(maxXT); //Normalized

		double ut = 0.0;
		for (Link link : route.getLinkList()) {
			ut += link.getCore(core).getUtilization();
		}
		ut = ut / route.getLinkList().size();

		cost = (alpha1 * xt) + (alpha2 * ut);

		return cost;
	}

	/**
	 * Performs spectrum allocation strategy selection
	 *
	 * @param slotsNumber int
	 * @param circuit Circuit
	 * @param cp ControlPlane
	 * @param core int
	 * @return int[]
	 */
	private int[] assignSpectrum(int slotsNumber, Circuit circuit, ControlPlane cp, int core) {

		int chosen[] = null;
    	List<int[]> composition = IntersectionFreeSpectrum.merge(circuit.getRoute(), circuit.getGuardBand(), core);

    	if(core == 0) { // central core
    		chosen = mediumFit(slotsNumber, composition, circuit, cp);

    	}else if(core % 2 == 0) { //even core
    		chosen = lastFit(slotsNumber, composition, circuit, cp);

    	}else if(core % 2 == 1) { //odd core
    		chosen = firstFit(slotsNumber, composition, circuit, cp);
    	}

    	return chosen;
	}

	/**
	 * Apply the mediumFit strategy
	 * policy for central core
	 *
	 * @param numberOfSlots int
	 * @param freeSpectrumBands List<int[]>
	 * @param circuit Circuit
	 * @param cp ControlPlane
	 * @return int[]
	 */
	private int[] mediumFit(int numberOfSlots, List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp) {
		int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
        if(numberOfSlots> maxAmplitude) return null;
    	int chosen[] = null;

    	int reference = circuit.getRoute().getLink(0).getCore(0).getNumOfSlots() / 2; //Center slot

    	for (int[] band : freeSpectrumBands) {
        	if(chosen == null) {
        		if (band[1] - band[0] + 1 >= numberOfSlots) {
                    chosen = band.clone();
                    chosen[1] = chosen[0] + numberOfSlots - 1;//It is not necessary to allocate the entire band, just the amount of slots required
                    break;
                }
        	}
    	}

    	if(chosen != null) {
	    	for (int[] band : freeSpectrumBands) {

	        	for(int i = band[0]; i <= band[1]; i++) {
	        		if(Math.abs(reference-i) < Math.abs(reference-chosen[0])) {
	        			if((band[1]-i+1) >= numberOfSlots) {
	        				chosen[0] = i;
	        				chosen[1] = i + numberOfSlots - 1;
	        			}
	        		}
	        	}
	    	}
    	}

        return chosen;
	}

	/**
	 * Apply the lastFit strategy
	 * policy for even core (even-numbered cores)
	 *
	 * @param numberOfSlots int
	 * @param freeSpectrumBands List<int[]>
	 * @param circuit Circuit
	 * @param cp ControlPlane
	 * @return int[]
	 */
	private int[] lastFit(int numberOfSlots, List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp) {
		int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
        if(numberOfSlots>maxAmplitude) return null;
    	int chosen[] = null;
        int band[] = null;

        for (int i = freeSpectrumBands.size() - 1; i >= 0; i--) {
            band = freeSpectrumBands.get(i);

            if (band[1] - band[0] + 1 >= numberOfSlots) {
                chosen = band.clone();
                chosen[0] = chosen[1] - numberOfSlots + 1;//It is not necessary to allocate the entire band, just the amount of slots required
                break;
            }
        }

        return chosen;
	}

	/**
	 * Apply the firstFit strategy
	 * policy for odd core (impares)
	 *
	 * @param numberOfSlots int
	 * @param freeSpectrumBands List<int[]>
	 * @param circuit Circuit
	 * @param cp ControlPlane
	 * @return int[]
	 */
	private int[] firstFit(int numberOfSlots, List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp) {
		int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
        if(numberOfSlots> maxAmplitude) return null;
    	int chosen[] = null;

        for (int[] band : freeSpectrumBands) {

            if (band[1] - band[0] + 1 >= numberOfSlots) {
                chosen = band.clone();
                chosen[1] = chosen[0] + numberOfSlots - 1;//It is not necessary to allocate the entire band, just the amount of slots required
                break;
            }
        }

        return chosen;
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
