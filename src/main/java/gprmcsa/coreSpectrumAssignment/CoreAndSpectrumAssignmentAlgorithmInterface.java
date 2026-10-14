package gprmcsa.coreSpectrumAssignment;

import java.io.Serializable;
import java.util.List;

import network.Circuit;
import network.ControlPlane;

/**
 * Represents the CoreAndSpectrumAssignmentAlgorithmInterface component.
 */
public interface CoreAndSpectrumAssignmentAlgorithmInterface extends Serializable{

	/**
	 * This method allocates a spectrum band and a core.
	 * Returns TRUE if successful and FALSE otherwise.
	 *
	 * @param numberOfSlots int
	 * @param circuit Circuit
	 * @param cp ControlPlane
	 * @return boolean
	 */
	public boolean assignCoreAndSpectrum(int numberOfSlots, Circuit circuit, ControlPlane cp);

	/**
	 * This method assigns a core and returns it.
	 *
	 * @return int
	 */
	public int coreAssignment();

	/**
	 * This method applies the specific policy of the core and spectrum allocation algorithm
	 *
	 * @param numberOfSlots int
	 * @param freeSpectrumBands List<int[]>
	 * @param Circuit circuit
	 * @param cp ControlPlane
	 * @return int[]
	 */
	public int[] policy(int numberOfSlots, List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp);
}
