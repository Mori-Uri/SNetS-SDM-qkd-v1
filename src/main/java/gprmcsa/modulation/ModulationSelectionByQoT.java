package gprmcsa.modulation;

import java.util.List;

import gprmcsa.coreSpectrumAssignment.CoreAndSpectrumAssignmentAlgorithmInterface;
import gprmcsa.routing.Route;
import network.Circuit;
import network.ControlPlane;

/**
 * This class implements the modulation selection algorithm by quality of transmission.
 * The spectrum allocation of each modulation is also checked.
 * Information such as modulation and selected spectrum, and quality of transmission, are stored in the circuit.
 *
 * @author Alexandre
 */
public class ModulationSelectionByQoT implements ModulationSelectionAlgorithmInterface {

	private List<Modulation> avaliableModulations;

	/**
	 * Selects the modulation.
	 * @param circuit the circuit.
	 * @param route the route.
	 * @param coreAndSpectrumAssignment the coreAndSpectrumAssignment.
	 * @param cp the cp.
	 * @return the result of the operation.
	 */
	@Override
	public Modulation selectModulation(Circuit circuit, Route route, CoreAndSpectrumAssignmentAlgorithmInterface coreAndSpectrumAssignment, ControlPlane cp) {
		if(avaliableModulations == null) {
			avaliableModulations = cp.getMesh().getAvaliableModulations();
		}

		boolean flagQoT = false; // Assuming that the circuit QoT starts as not acceptable

		// Modulation and spectrum selected
		Modulation chosenMod = null;
		int chosenBand[] = null;

		// Modulation which at least allocates spectrum, used to avoid error in metrics
		Modulation alternativeMod = null;
		int alternativeBand[] = null;

		for (int m = 0; m < avaliableModulations.size(); m++) {
			Modulation mod = avaliableModulations.get(m);
			circuit.setModulation(mod);
			int numberOfSlots = mod.requiredSlots(circuit.getRequiredBitRate());

			if(coreAndSpectrumAssignment.assignCoreAndSpectrum(numberOfSlots, circuit, cp)){
				int band[] = circuit.getSpectrumAssigned();
				int core = circuit.getIndexCore();

				if(alternativeMod == null){
					alternativeMod = mod; // The first modulation that was able to allocate spectrum
					alternativeBand = band;
				}

				if(cp.getMesh().getPhysicalLayer().isAdmissibleModultion(circuit, route, mod, core, band, null, false)){
					chosenMod = mod; // Save the modulation that has admissible QoT
					chosenBand = band;

					flagQoT = true;
				}
			}
		}

		if(chosenMod == null){ // QoT is not enough for all modulations
			chosenMod = avaliableModulations.get(0); // To avoid metric error
			chosenBand = null;

			if(alternativeMod != null){ // Allocated spectrum using some modulation, but the QoT was inadmissible
				chosenMod = alternativeMod;
				chosenBand = alternativeBand;
			}
		}

		// Configures the circuit information. They can be used by the method that requested the modulation selection
		circuit.setModulation(chosenMod);
		circuit.setSpectrumAssigned(chosenBand);
		circuit.setQoT(flagQoT);

		return chosenMod;
	}

}
