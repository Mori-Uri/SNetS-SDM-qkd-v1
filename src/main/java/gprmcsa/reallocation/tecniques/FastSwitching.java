package gprmcsa.reallocation.tecniques;

import java.util.List;

import network.Circuit;
import network.ControlPlane;
import util.IntersectionFreeSpectrum;

/**
 * Traffic migration technique between cores for the same spectrum band.
 *
 * @author gustavo
 *
 *
 *         PENDING TESTING
 */
public class FastSwitching {

	/**
	 * Creates a new instance of FastSwitching.
	 */
	public FastSwitching() {
		//
	}

	/**
	 * Troca de core
	 *
	 * @param circuit
	 * @param cp
	 * @return
	 * @throws Exception
	 */
	public boolean execute(Circuit circuit, int newIndexCore, ControlPlane cp) throws Exception {

		// the core must be different from the core of the circuit to be reallocated
		if (circuit.getIndexCore() == newIndexCore) {
			return false;
		}

		// check whether the spectral range of the new core is free
		boolean isPossible = canBeSwitchingCore(circuit, newIndexCore);

		if (isPossible == false) {
			return false;
		}

		// Saves the allocated spectrum band without the reallocation
		int oldIndexCore = circuit.getIndexCore();

//		System.out.println("Spectrum ");

		// Releasing the spectrum and guard bands already allocated from the original core
		cp.releaseSpectrum(circuit, circuit.getSpectrumAssigned(), circuit.getRoute().getLinkList(), circuit.getGuardBand());

		// Try to realloc circuit
		circuit.setIndexCore(newIndexCore);
		if (!cp.allocateSpectrum(circuit, circuit.getSpectrumAssigned(), circuit.getRoute().getLinkList(), circuit.getGuardBand())) {
			throw new Exception("Bad RMLSA. Spectrum cant be allocated.");
		}

		// Verifies if the expansion did not affect the QoT of the circuit or other
		// already active circuits
		boolean QoT = cp.isAdmissibleQualityOfTransmission(circuit);

//		System.out.println("Entered here");
		// QoT ou QoTO no aceitvel ou XT inaceitavel
		// restore the original circuit
		if (!QoT) {

			// QoT was not acceptable after expansion, releasing the spectrum
			cp.releaseSpectrum(circuit, circuit.getSpectrumAssigned(), circuit.getRoute().getLinkList(), circuit.getGuardBand());

			// Reallocating the spectrum and guard bands without the expansion
			circuit.setIndexCore(oldIndexCore);
			if (!cp.allocateSpectrum(circuit, circuit.getSpectrumAssigned(), circuit.getRoute().getLinkList(), circuit.getGuardBand())) {
				throw new Exception("Bad RMLSA. Spectrum cant be allocated.");
			}

			// Recalculates the QoT and XT of the circuit
			cp.computeQualityOfTransmission(circuit, null, false);

			// reallocation failed because QoT was unacceptable

		} else {// QoT Aceitavel
			// check se Xt  aceitavel
			boolean xt = cp.isAdmissibleCrosstalk(circuit);// verifica crosstalk

			if (!xt) {
				cp.releaseSpectrum(circuit, circuit.getSpectrumAssigned(), circuit.getRoute().getLinkList(), circuit.getGuardBand());

				// Reallocating the spectrum and guard bands without the expansion
				circuit.setIndexCore(oldIndexCore);
				if (!cp.allocateSpectrum(circuit, circuit.getSpectrumAssigned(), circuit.getRoute().getLinkList(), circuit.getGuardBand())) {
					throw new Exception("Bad RMLSA. Spectrum cant be allocated.");
				}

				// recalculate crosstalk
				cp.computeCrosstalk(circuit);

			} else { // reallocation completed
				// update network power consumption
//				System.out.println("successful reallocation");

				// update the circuit lists of the cores
				removeCircuitOldCore(circuit, oldIndexCore);
				addCircuitNewCore(circuit, newIndexCore);

				cp.updateNetworkPowerConsumption();
				return true;
			}

		}

		return false;
	}

	/**
	 * Remove the circuit from the old-core circuit list
	 * @param circuit
	 * @param oldIndexCore
	 */
	private void removeCircuitOldCore(Circuit circuit, int oldIndexCore) {
		for (int i = 0; i < circuit.getRoute().getLinkList().size(); i++) {
			circuit.getRoute().getLinkList().get(i).getCore(oldIndexCore).removeCircuit(circuit);
		}
	}

	/**
	 * Add the circuit to the new-core circuit list
	 */
	private void addCircuitNewCore(Circuit circuit, int newIndexCore) {
		for (int i = 0; i < circuit.getRoute().getLinkList().size(); i++) {
			circuit.getRoute().getLinkList().get(i).getCore(newIndexCore).addCircuit(circuit);
		}
	}

	/**
	 * checks se a band de espetro do novo core est free ou not
	 */
	public boolean canBeSwitchingCore(Circuit circuit, int newIndexCore) {

		int guardBand = circuit.getGuardBand();
		int band[] = circuit.getSpectrumAssigned();

		// composition of the route for the core to be reallocated
		List<int[]> composition = IntersectionFreeSpectrum.merge(circuit.getRoute(), guardBand, newIndexCore);

		for (int[] fl : composition) {
			// checks whether the spectral interval is contained in a free block in the new core
			// core
			if (band[0] >= fl[0] && band[1] <= fl[1]) {
				return true;
			}

		}

		return false;
	}

}
