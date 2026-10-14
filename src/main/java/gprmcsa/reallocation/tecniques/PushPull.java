package gprmcsa.reallocation.tecniques;

import java.util.List;

import network.Circuit;
import network.ControlPlane;
import util.IntersectionFreeSpectrum;

/**
 * PUSH and PULL technique finalized on 31.05.23.
 * Data-traffic migration technique.
 * The technique allows SPECTRUM REALLOCATION in the spectrum.
 * @author gustavo
 *
 *
 *TESTADO e FINALIZADO
 */
public class PushPull {

	/**
	 * Creates a new instance of PushPull.
	 */
	public PushPull() {

	}

	/**
	 * executes push-pull to the right (upward) in the spectrum
	 *
	 * @param circuit
	 * @param cp
	 * @return
	 * @throws Exception
	 */
	public boolean executeUpper(Circuit circuit, ControlPlane cp) throws Exception {
		int[] band = canBeShiftUpper(circuit);

		if (band == null) {
			return false;
		}

		int numberSlots = circuit.getSpectrumAssigned()[1] - circuit.getSpectrumAssigned()[0] + 1;

		// Saves the allocated spectrum band without the reallocation
		int oldBand[] = circuit.getSpectrumAssigned();

		int newBand[] = new int[2];
		newBand[1] = band[1];
		newBand[0] = band[1] - numberSlots + 1;// verficar se est correto?

		// Releasing the spectrum and guard bands already allocated
		cp.releaseSpectrum(circuit, oldBand, circuit.getRoute().getLinkList(), circuit.getGuardBand());

		// Try to realloc circuit
		circuit.setSpectrumAssigned(newBand);
		if (!cp.allocateSpectrum(circuit, newBand, circuit.getRoute().getLinkList(), circuit.getGuardBand())) {
			throw new Exception("Bad RMLSA. Spectrum cant be allocated.");
		}

		// Verifies if the expansion did not affect the QoT of the circuit or other
		// already active circuits
		boolean QoT = cp.isAdmissibleQualityOfTransmission(circuit);

//				System.out.println("Entered here");
		// QoT ou QoTO no aceitvel ou XT inaceitavel
		// restore the original circuit
		if (!QoT) {

			// QoT was not acceptable after expansion, releasing the spectrum
			cp.releaseSpectrum(circuit, newBand, circuit.getRoute().getLinkList(), circuit.getGuardBand());

			// Reallocating the spectrum and guard bands without the expansion
			circuit.setSpectrumAssigned(oldBand);
			if (!cp.allocateSpectrum(circuit, oldBand, circuit.getRoute().getLinkList(), circuit.getGuardBand())) {
				throw new Exception("Bad RMLSA. Spectrum cant be allocated.");
			}

			// Recalculates the QoT and XT of the circuit
			cp.computeQualityOfTransmission(circuit, null, false);

			// reallocation failed because QoT was unacceptable

		} else {// QoT Aceitavel
			// check se Xt  aceitavel
			boolean xt = cp.isAdmissibleCrosstalk(circuit);// verifica crosstalk

			if (!xt) {
				cp.releaseSpectrum(circuit, newBand, circuit.getRoute().getLinkList(), circuit.getGuardBand());

				// Reallocating the spectrum and guard bands without the expansion
				circuit.setSpectrumAssigned(oldBand);
				if (!cp.allocateSpectrum(circuit, oldBand, circuit.getRoute().getLinkList(), circuit.getGuardBand())) {
					throw new Exception("Bad RMLSA. Spectrum cant be allocated.");
				}

				// recalculate crosstalk
				cp.computeCrosstalk(circuit);

			} else { // reallocation completed
				// update network power consumption
//						System.out.println(" successful reallocation");
				cp.updateNetworkPowerConsumption();
				return true;
			}

		}

		return false;

	}

	/**
	 * executes push-pull to the left (downward) in the spectrum
	 *
	 * @param circuit
	 * @param cp
	 * @return
	 */
	public boolean executeDown(Circuit circuit, ControlPlane cp) throws Exception {

		int[] band = canBeShiftDown(circuit);

		// it is not possible to slide in the spectrum
		if (band == null) {
			return false;
		}

//		System.out.println(band);

		int numberSlots = circuit.getSpectrumAssigned()[1] - circuit.getSpectrumAssigned()[0] + 1;

		// Saves the allocated spectrum band without the reallocation
		int oldBand[] = circuit.getSpectrumAssigned();

		int newBand[] = oldBand.clone();
		newBand[0] = band[0];
		newBand[1] = band[0] + numberSlots - 1;

		// Releasing the spectrum and guard bands already allocated
		cp.releaseSpectrum(circuit, oldBand, circuit.getRoute().getLinkList(), circuit.getGuardBand());

		// Try to realloc circuit
		circuit.setSpectrumAssigned(newBand);
		if (!cp.allocateSpectrum(circuit, newBand, circuit.getRoute().getLinkList(), circuit.getGuardBand())) {
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
			cp.releaseSpectrum(circuit, newBand, circuit.getRoute().getLinkList(), circuit.getGuardBand());

			// Reallocating the spectrum and guard bands without the expansion
			circuit.setSpectrumAssigned(oldBand);
			if (!cp.allocateSpectrum(circuit, oldBand, circuit.getRoute().getLinkList(), circuit.getGuardBand())) {
				throw new Exception("Bad RMLSA. Spectrum cant be allocated.");
			}

			// Recalculates the QoT and XT of the circuit
			cp.computeQualityOfTransmission(circuit, null, false);

			// reallocation failed because QoT was unacceptable

		} else {// QoT Aceitavel
			// check se Xt  aceitavel
			boolean xt = cp.isAdmissibleCrosstalk(circuit);// verifica crosstalk

			if (!xt) {
				cp.releaseSpectrum(circuit, newBand, circuit.getRoute().getLinkList(), circuit.getGuardBand());

				// Reallocating the spectrum and guard bands without the expansion
				circuit.setSpectrumAssigned(oldBand);
				if (!cp.allocateSpectrum(circuit, oldBand, circuit.getRoute().getLinkList(), circuit.getGuardBand())) {
					throw new Exception("Bad RMLSA. Spectrum cant be allocated.");
				}

				// recalculate crosstalk
				cp.computeCrosstalk(circuit);

			} else { // reallocation completed
				// update network power consumption
//				System.out.println(" successful reallocation");
				cp.updateNetworkPowerConsumption();
				return true;
			}

		}

		return false;

	}

	/**
	 * checks the possibility of sliding to the right (up) in the spectrum
	 */
	public int[] canBeShiftUpper(Circuit circuit) {
		List<int[]> composition = IntersectionFreeSpectrum.merge(circuit.getRoute(), circuit.getGuardBand(),
				circuit.getIndexCore());

		// number of free slots below the spectrum - checks the sliding toward
		// down
		int numSlotsUpper = IntersectionFreeSpectrum.freeSlotsUpper(circuit.getSpectrumAssigned(), composition,
				circuit.getGuardBand());
		if (numSlotsUpper <= 0) {
			return null;
		}

		int[] bandToShift = bandAdjacentUpper(circuit.getSpectrumAssigned(), composition, circuit.getGuardBand());

		if (bandToShift != null) {
//			System.out.println("Entered here");
			return bandToShift;
		}

		return null;

	}

	/**
	 * checks the possibility of sliding to the left (down) in the spectrum
	 */
	public int[] canBeShiftDown(Circuit circuit) {

		List<int[]> composition = IntersectionFreeSpectrum.merge(circuit.getRoute(), circuit.getGuardBand(),
				circuit.getIndexCore());

		// number of free slots below the spectrum - checks the sliding toward
		// down
		int numSlotsDown = IntersectionFreeSpectrum.freeSlotsDown(circuit.getSpectrumAssigned(), composition,
				circuit.getGuardBand());
		if (numSlotsDown <= 0) {
			return null;
		}

		int[] bandToShift = bandAdjacentDown(circuit.getSpectrumAssigned(), composition, circuit.getGuardBand());

		if (bandToShift != null) {
//			System.out.println("Entered here2");
			return bandToShift;
		}

		return null;

	}

	/**
	 * Returns the adjacent range less than the range passed by parameter. Used in
	 * optical aggregation algorithms. Methods extracted from the class
	 * IntersectionFreeSpectrum
	 *
	 * @param band      int[]
	 * @param bandsFree List<int[]>
	 * @return int[]
	 */
	public static int[] bandAdjacentDown(int band[], List<int[]> bandsFree, int guardBand) {

//    	System.out.println("Circuit spectrum: "+band[0]+"-"+band[1]);
//    	System.out.println("Banda de guarda: "+guardBand);
//    	System.out.println("Free spectrum band: ");
//    	for (int[] is : bandsFree) {
//			System.out.println(is[0]+"-"+is[1]);
//		}

		for (int[] fl : bandsFree) {

			if (fl[1] == (band[0] - 1 - guardBand)) {
//            	System.out.println("Here - parent");
				return fl;
			}
		}
		return null;
	}

	/**
	 * Returns the adjacent range higher than the range passed by parameter. Used in
	 * optical aggregation algorithms. Methods extracted from the class
	 * IntersectionFreeSpectrum
	 *
	 * @param band      int[]
	 * @param bandsFree List<int[]>
	 * @return int[]
	 */
	public static int[] bandAdjacentUpper(int band[], List<int[]> bandsFree, int guardBand) {
		for (int[] fl : bandsFree) {
			if (fl[0] == (band[1] + 1 + guardBand)) {
				// System.out.println("Test ok");
				return fl;
			}
		}
		return null;
	}

}
