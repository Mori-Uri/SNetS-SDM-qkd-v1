package gprmcsa.guardBandSelection;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Vector;

import gprmcsa.integrated.IntegratedRMLSAAlgorithmInterface;
import gprmcsa.modulation.Modulation;
import gprmcsa.routing.KRoutingAlgorithmInterface;
import gprmcsa.routing.Route;
import gprmcsa.routing.RoutingAlgorithmInterface;
import network.Circuit;
import network.ControlPlane;
import network.Link;
import util.IntersectionFreeSpectrum;

/**
 * Represents the DJKGBSAlgorithm component.
 */
public class DJKGBSAlgorithm implements IntegratedRMLSAAlgorithmInterface {

	private RoutingAlgorithmInterface routing;
	private int maximumGuardBand = 8;

	/**
	 * Returns the rsa.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @return true if the condition is met; false otherwise.
	 */
	@Override
	public boolean rsa(Circuit circuit, ControlPlane cp) {
		if (routing == null){
			routing = cp.getRouting();

			// To read the value of k from the "others" file
        	Map<String, String> uv = cp.getMesh().getOthersConfig().getVariables();
        	if(uv.get("maximumGuardBand") != null) {
        		maximumGuardBand = Integer.parseInt((String)uv.get("maximumGuardBand"));
        	}
        }

		//Select the route
        routing.findRoute(circuit, cp.getMesh());
        Route route = circuit.getRoute();

        List<Modulation> avaliableModulations = cp.getMesh().getAvaliableModulations();

        // Modulation, core and band chosen
        Modulation chosenMod = null;
        int chosenCore = -1;
        int chosenBand[] = null;
        int chosenGB = -1;

        // To avoid metrics error
  		Modulation checkMod = null;
  		int checkCore = -1;
  		int checkBand[] = null;
  		int checkGB = -1;

  		Modulation checkMod2 = null;
  		int checkCore2 = -1;
  		int checkBand2[] = null;
  		int checkGB2 = -1;

  		int numberOfCores = cp.getMesh().getLinkList().get(0).getNumberOfCores();
  		double minRouteCost = Double.POSITIVE_INFINITY;

        // Begins with the most spectrally efficient modulation format
		for (int m = avaliableModulations.size()-1; m >= 0; m--) {
			Modulation mod = avaliableModulations.get(m);
			circuit.setModulation(mod);

			int slotsNumber = mod.requiredSlots(circuit.getRequiredBitRate()); // Slots required for establishment on the new circuit

			// Possible values ​​for the guard band
            for (int gb = 0; gb < maximumGuardBand; gb++) {
            	circuit.setGuardBand(gb); // You have to set the guard band after you set the modulation.

    			// It goes through the cores starting from the highest index to the lowest index
    			for (int core = numberOfCores-1; core >= 0; core--) {
    				circuit.setIndexCore(core);

    	            int band[] = assignSpectrum(slotsNumber, circuit, cp, core);
    	            circuit.setSpectrumAssigned(band);

    	            if (band != null) {
                		checkMod = mod;
                		checkCore = core;
    	            	checkBand = band;
    	            	checkGB = gb;

    	            	// Check the physical layer
    	            	boolean QoT = cp.isAdmissibleOSNR(circuit);
                		boolean XT = true;
                		if (QoT) { // Only tests XT if it passes through OSNR
                			XT = cp.isAdmissibleCrosstalk(circuit);
                		}

    	    			if (QoT && XT) { // QoT and XT are acceptable
    		    			checkMod2 = mod;
    		    			checkCore2 = core;
    	            		checkBand2 = band;
    	            		checkGB2 = gb;

    	            		// Checks the QoT and XT for others circuits
    	            		boolean QoTO = cp.isAdmissibleOSNRInOther(circuit);
    	            		boolean XTO = true;
    	            		if (QoTO) { // Only tests XT if it passes through OSNR
    	            			XTO = cp.isAdmissibleCrosstalkInOther(circuit);
    	            		}

    		                if (QoTO && XTO) { // QoTO and XTO are acceptable

    		                	double cost = circuit.getXt();
    		                	if (cost < minRouteCost) { // Select the route with the lowest cost
    		                		minRouteCost = cost;

        			                chosenMod = mod;
        			                chosenCore = core;
        			                chosenBand = band;
        			                chosenGB = gb;
    		                	}
    		                }
    	            	}
    	            }//spectrum
    			} //core
            } //guard band
        } //modulations

        if (chosenMod != null) { // If a modulation with acceptable QoT and QoTO was found
            circuit.setRoute(route);
            circuit.setModulation(chosenMod);
            circuit.setGuardBand(chosenGB);
            circuit.setIndexCore(chosenCore);
            circuit.setSpectrumAssigned(chosenBand);

            return true;

        } else if(checkMod2 != null) {
            circuit.setRoute(route);
            circuit.setModulation(checkMod2);
            circuit.setGuardBand(checkGB2);
            circuit.setIndexCore(checkCore2);
            circuit.setSpectrumAssigned(checkBand2);

            return false;

        } else {
        	if(checkMod == null){
				checkMod = avaliableModulations.get(0);
				checkCore = new Random().nextInt(numberOfCores);
			}
            circuit.setRoute(route);
            circuit.setModulation(checkMod);
            circuit.setGuardBand(checkGB);
            circuit.setIndexCore(checkCore);
            circuit.setSpectrumAssigned(checkBand);

            return false;
        }
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
	 * Apply the mediumFit strategy policy for central core
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
	 * Apply the mediumFit strategy policy for even core (even-numbered cores)
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
	 * Apply the mediumFit strategy policy for odd core (impares)
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
		return null;
	}

}
