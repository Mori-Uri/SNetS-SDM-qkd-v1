package gprmcsa.powerAssignment;

import java.io.Serializable;

import gprmcsa.modulation.Modulation;
import gprmcsa.routing.Route;
import network.Circuit;
import network.ControlPlane;

/**
 * This interface should be implemented by classes of power assignment algorithms.
 *
 * @author Alexandre
 *
 */
public interface PowerAssignmentAlgorithmInterface  extends Serializable {

	/**
	 * This method computes a launch power for a circuit.
	 *
	 * @param circuit Circuit
	 * @param route Route
	 * @param modulation Modulation
	 * @param core int
	 * @param spectrumAssigned int[]
	 * @param cp ControlPlane
	 * @return double - lauch power value
	 */
	public double assignLaunchPower(Circuit circuit, Route route, Modulation modulation, int core, int spectrumAssigned[], ControlPlane cp);
}
