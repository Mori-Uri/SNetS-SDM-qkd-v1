package gprmcsa.integrated;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import gprmcsa.modulation.Modulation;
import gprmcsa.powerAssignment.PowerAssignmentAlgorithmInterface;
import gprmcsa.routing.KRoutingAlgorithmInterface;
import gprmcsa.routing.Route;
import network.Circuit;
import network.ControlPlane;
import network.Core;
import network.Crosstalk;
import network.Link;
import network.PhysicalLayer;
import util.IntersectionFreeSpectrum;

/**
 * Represents the IntegratedPRMCSAAlgorithm_v3 component.
 */
public class IntegratedPRMCSAAlgorithm_v3 implements IntegratedRMLSAAlgorithmInterface {

	private int k = 3; // Number of alternative routes
    private KRoutingAlgorithmInterface kShortestsPaths;
    //private ModulationSelectionAlgorithmInterface modulationSelection;
    //private SpectrumAssignmentAlgorithmInterface spectrumAssignment;
    //private CoreAndSpectrumAssignmentAlgorithmInterface coreAndSpectrumAssignment;
    private PowerAssignmentAlgorithmInterface powerAssignmet;

    private double alpha1 = 0.5; //Aplicado ao XT
    private double alpha2 = 0.5; //Aplicado a Utilizao

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
        //if (modulationSelection == null){
        //    modulationSelection = cp.getModulationSelection();
        //}
        //if(spectrumAssignment == null){
        //    spectrumAssignment = cp.getSpectrumAssignment();
        //}
        //if(coreAndSpectrumAssignment == null){
        //	coreAndSpectrumAssignment = cp.getCoreAndSpectrumAssignment();
		//}
        if(powerAssignmet == null) {
        	powerAssignmet = cp.getPowerAssignment();
        }

        List<Modulation> avaliableModulations = cp.getMesh().getAvaliableModulations();
        List<Route> candidateRoutes = kShortestsPaths.getRoutes(circuit.getSource(), circuit.getDestination());
        List<Integer> coreList = getCoreList(cp);

        // chosen route, modulation, band and power
        Route chosenRoute = null;
        Modulation chosenMod = null;
        int chosenCore = -1;
        int chosenBand[] = null;
        Double chosenPower = Double.POSITIVE_INFINITY;

        // to avoid metrics error
  		Route checkRoute = null;
  		Modulation checkMod = null;
  		int checkCore = -1;
  		int checkBand[] = null;
  		Double checkPower = Double.POSITIVE_INFINITY;

  		Route checkRoute2 = null;
  		Modulation checkMod2 = null;
  		int checkCore2 = -1;
  		int checkBand2[] = null;
  		Double checkPower2 = Double.POSITIVE_INFINITY;

  		double minRouteCost = Double.POSITIVE_INFINITY;

        for (Route route : candidateRoutes) {
            circuit.setRoute(route);

        	// Begins with the most spectrally efficient modulation format
    		for (int m = avaliableModulations.size()-1; m >= 0; m--) {
    			Modulation mod = avaliableModulations.get(m);
    			circuit.setModulation(mod);

	            int slotsNumber = mod.requiredSlots(circuit.getRequiredBitRate());

	            // List of cores in ascending order of adjacent cores.
		  		for (int c = 0; c < coreList.size(); c++) {
    				int core = coreList.get(c);
    				circuit.setIndexCore(core);

    				int band[] = assignSpectrum(slotsNumber, circuit, cp, core);
    	            circuit.setSpectrumAssigned(band);

		    		if (band != null) {
		    			double launchPower = Double.POSITIVE_INFINITY;

		    			// Calculate the launch power for the new circuit
		    			launchPower = powerAssignmet.assignLaunchPower(circuit, route, mod, core, band, cp);
		    			circuit.setLaunchPowerLinear(launchPower);

		    			checkRoute = route;
		    			checkMod = mod;
		    			checkCore = core;
	            		checkBand = band;
	            		checkPower = launchPower;

	            		// Check the physical layer
	            		boolean QoT = cp.isAdmissibleOSNR(circuit);
	            		boolean XT = cp.isAdmissibleCrosstalk(circuit);

		    			if (QoT && XT) { // QoT and XT are acceptable
		    				checkRoute2 = route;
			    			checkMod2 = mod;
			    			checkCore2 = core;
		            		checkBand2 = band;
		            		checkPower2 = launchPower;

			                // Checks the QoT and XT for others circuits
		            		boolean QoTO = cp.isAdmissibleOSNRInOther(circuit);
			                boolean XTO = cp.isAdmissibleCrosstalkInOther(circuit);

			                if(circuit.getXt() > maxXT) {
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
					                chosenPower = launchPower;
    		                	}
			                }
		            	}
		            } //spectrum
    			} //core
            } //modulation
        } //route

        if (chosenRoute != null) { //If there is no route chosen is why no available resource was found on any of the candidate routes
            circuit.setRoute(chosenRoute);
            circuit.setModulation(chosenMod);
            circuit.setIndexCore(chosenCore);
            circuit.setSpectrumAssigned(chosenBand);
            circuit.setLaunchPowerLinear(chosenPower);

            return true;

        } else if(checkRoute2 != null) {
            circuit.setRoute(checkRoute2);
            circuit.setModulation(checkMod2);
            circuit.setIndexCore(checkCore2);
            circuit.setSpectrumAssigned(checkBand2);
            circuit.setLaunchPowerLinear(checkPower2);

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
            circuit.setLaunchPowerLinear(checkPower);

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
	 * Apply the mediumFit strategy
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
	 * Apply the mediumFit strategy
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
	 * This method creates the list of ordered cores
	 *
	 * @param cp ControlPlane
	 * @return List<Integer>
	 */
	private List<Integer> getCoreList(ControlPlane cp){
		List<Integer> coreList = new ArrayList<Integer>();
		List<CoreComp> coreCompList = new ArrayList<CoreComp>();

		int numberOfCores = cp.getMesh().getLinkList().get(0).getNumberOfCores();

		for (int c = numberOfCores-1; c >= 0; c--) {
			Core core = cp.getMesh().getLinkList().get(0).getCore(c);
			CoreComp coreTemp = new CoreComp(c, core.getAdjacentCores().size());
			coreCompList.add(coreTemp);
		}

		coreCompList.sort(Comparator.comparing(CoreComp::getNumAdjacents));

		for (int c = 0; c < coreCompList.size(); c++) {
			CoreComp coreTemp = coreCompList.get(c);
			coreList.add(coreTemp.getId());
		}

		return coreList;
	}

	/**
	 * Class created for the ordering of cores.
	 */
	private class CoreComp {
		private Integer id;
		private Integer numAdjacents;

		public CoreComp(Integer id, Integer numAdjacents) {
			this.id = id;
			this.numAdjacents = numAdjacents;
		}

		/**
		 * Returns the id.
		 * @return the id.
		 */
		public Integer getId() {
			return id;
		}

		/**
		 * Returns the num adjacents.
		 * @return the num adjacents.
		 */
		public Integer getNumAdjacents() {
			return numAdjacents;
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
