package gprmcsa.modulation;

import java.io.Serializable;

import gprmcsa.coreSpectrumAssignment.CoreAndSpectrumAssignmentAlgorithmInterface;
import gprmcsa.routing.Route;
import network.Circuit;
import network.ControlPlane;

/**
 * This interface should be implemented by classes of modulation selection algorithms.
 *
 * @author Alexandre
 */
public interface ModulationSelectionAlgorithmInterface extends Serializable {

	/**
	 * This method selects the appropriate modulation format for the establishment of the circuit.
	 *
	 * @param circuit Circuit
	 * @param route Route
	 * @param coreAndSpectrumAssignment CoreAndSpectrumAssignmentAlgorithmInterface
	 * @param cp ControlPlane
	 * @return Modulation
	 */
	public Modulation selectModulation(Circuit circuit, Route route, CoreAndSpectrumAssignmentAlgorithmInterface coreAndSpectrumAssignment, ControlPlane cp);

}
