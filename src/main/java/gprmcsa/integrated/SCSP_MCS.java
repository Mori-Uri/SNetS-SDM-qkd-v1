package gprmcsa.integrated;

import java.util.List;
import java.util.Random;

import gprmcsa.coreSpectrumAssignment.CoreAndSpectrumAssignmentAlgorithmInterface;
import gprmcsa.modulation.Modulation;
import gprmcsa.routing.KRoutingAlgorithmInterface;
import gprmcsa.routing.Route;
import gprmcsa.routing.RoutingAlgorithmInterface;
import gprmcsa.routing.SCSP;
import network.Circuit;
import network.ControlPlane;
import util.IntersectionFreeSpectrum;

/**
 * Algorithm created to apply sequentially the SCSP, modulation, core and spectrum allocation algorithms.
 * 
 */
public class SCSP_MCS implements IntegratedRMLSAAlgorithmInterface {

	private RoutingAlgorithmInterface routing;
	private CoreAndSpectrumAssignmentAlgorithmInterface coreAndSpectrumAssignment;

	@Override
	public boolean rsa(Circuit circuit, ControlPlane cp) {
		if (routing == null) {
			routing = new SCSP();
		}
		if (coreAndSpectrumAssignment == null) {
			coreAndSpectrumAssignment = cp.getCoreAndSpectrumAssignment();
		}

		// All available modulations
		List<Modulation> avaliableModulations = cp.getMesh().getAvaliableModulations();
		int numberOfCores = cp.getMesh().getLinkList().get(0).getNumberOfCores();

		// Route, modulation, core and band chosen
		Route chosenRoute = null;
		Modulation chosenMod = null;
		int chosenCore = -1;
		int chosenBand[] = null;

		// to avoid metrics errorr
		Route checkRoute = null;
		Modulation checkMod = null;
		int checkCore = -1;
		int checkBand[] = null;

		Route checkRoute2 = null;
		Modulation checkMod2 = null;
		int checkCore2 = -1;
		int checkBand2[] = null;

		Route checkRoute3 = null;
		
		boolean QoTO = false;
		boolean XTO = false;

		// Begins with the most spectrally efficient modulation format
		for (int m = avaliableModulations.size()-1; m >= 0; m--) {
			Modulation mod = avaliableModulations.get(m);
			circuit.setModulation(mod);

			// Number of slots required using the selected modulation
			int requiredSlotsNumber = mod.requiredSlots(circuit.getRequiredBitRate());
			
			// It goes through the cores starting from the highest index to the lowest index
			for (int core = numberOfCores-1; core >= 0; core--) {
				circuit.setIndexCore(core);

				// Apply the routing algorithm
				routing.findRoute(circuit, cp.getMesh());
				Route route = circuit.getRoute();

				if (route != null) {
					checkRoute3 = route;

					// Apply the spectrum selection algorithm
					int band[] = assignSpectrum(requiredSlotsNumber, circuit, cp, core);
					circuit.setSpectrumAssigned(band);

					if (band != null) { // Available FSs ?
						checkRoute = route;
						checkMod = mod;
						checkCore = core;
						checkBand = band;

						// Check the physical layer
						boolean QoT = cp.isAdmissibleOSNR(circuit);
						boolean XT = cp.isAdmissibleCrosstalk(circuit);

						if (QoT && XT) { // Acceptable QoT and XT ?
							checkRoute2 = route;
							checkMod2 = mod;
							checkCore2 = core;
							checkBand2 = band;

							// Checks the QoT and XT for others circuits
							QoTO = cp.isAdmissibleOSNRInOther(circuit);
							XTO = cp.isAdmissibleCrosstalkInOther(circuit);

							if (QoTO && XTO) { //Acceptable QoTO and XTO ?
								chosenRoute = route;
								chosenMod = mod;
								chosenCore = core;
								chosenBand = band;

								break; //Stop when to reach acceptable QoTO and XTO
							}
						}
					} //band
				} //route
			} //core
			
			if (QoTO && XTO) { // to exit the search for modulations
				break;
			}
		} //modulation

		if(chosenRoute != null){ //If there is no route chosen is why no available resource was found on any of the candidate routes
			circuit.setRoute(chosenRoute);
			circuit.setModulation(chosenMod);
			circuit.setIndexCore(chosenCore);
			circuit.setSpectrumAssigned(chosenBand);

			return true;

		}else if(checkRoute2 != null){
			circuit.setRoute(checkRoute2);
			circuit.setModulation(checkMod2);
			circuit.setIndexCore(checkCore2);
			circuit.setSpectrumAssigned(checkBand2);

			return false;

		}else{
			if(checkRoute == null){
				if(checkRoute3 != null){
					checkRoute = checkRoute3;
				}else{
					checkRoute = routing.getRoutesForAllPairs().get(circuit.getSource().getName() + SCSP.DIV + circuit.getDestination().getName());
				}
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
	 * @param numberOfslots int
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
	 * policy for even core (pares)
	 * 
	 * @param numberOfslots int
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
	 * @param numberOfslots int
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
	public KRoutingAlgorithmInterface getRoutingAlgorithm(){
		return null;
	}
}
