package gprmcsa.modulation;

import java.util.List;

import gprmcsa.coreSpectrumAssignment.CoreAndSpectrumAssignmentAlgorithmInterface;
import gprmcsa.routing.Route;
import network.Circuit;
import network.ControlPlane;

/**
 * this class implement the modulation selection algorithm that returns modulation robust enough to satisfy
 * the request with the highest bit rate per possible symbol
 *
 * @author Iallen
 */
public class ModulationSelectionByDistance implements ModulationSelectionAlgorithmInterface {

	private List<Modulation> avaliableModulations;

	/**
	 * Selects the modulation.
	 * @param circuit the circuit.
	 * @param route the route.
	 * @param coreandspectrumAssignment the coreandspectrumAssignment.
	 * @param cp the cp.
	 * @return the result of the operation.
	 */
	@Override
	public Modulation selectModulation(Circuit circuit, Route route, CoreAndSpectrumAssignmentAlgorithmInterface coreandspectrumAssignment, ControlPlane cp) {
		if(avaliableModulations == null) {
			avaliableModulations = cp.getMesh().getAvaliableModulations();
		}

		double maxBPS = 0.0;
		Modulation resMod = null;

		for (Modulation mod : avaliableModulations) {
			if(mod.getMaxRange() >= route.getDistanceAllLinks()){//Modulation robust enough for this requirement
				if(mod.getBitsPerSymbol() > maxBPS){ //Choose the modulation with the largest number of bits per possible symbol
					resMod = mod;
					maxBPS = mod.getBitsPerSymbol();
				}
			}
		}

		if(resMod == null) {
			resMod = avaliableModulations.get(0); // To avoid metric error
		}

		return resMod;
	}

}
