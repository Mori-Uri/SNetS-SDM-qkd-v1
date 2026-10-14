package gprmcsa.powerAssignment;

import gprmcsa.modulation.Modulation;
import gprmcsa.routing.Route;
import network.Circuit;
import network.ControlPlane;

/**
 * This class represents the Constant Power Spectral Density (CPSD) algorithm.
 * In the CPSD strategy, the same power spectral density is used for all circuits.
 * The power of each circuit is proportional to the bandwidth allocated to the circuit.
 *
 * Article: Nyquist-WDM-Based Flexible Optical Networks: Exploring Physical Layer Design Parameters (2013).
 *
 * @author Alexandre
 *
 */
public class CPSDAlgorithm implements PowerAssignmentAlgorithmInterface {

	/**
	 * Returns the assign launch power.
	 * @param circuit the circuit.
	 * @param route the route.
	 * @param modulation the modulation.
	 * @param core the core.
	 * @param spectrumAssigned the spectrumAssigned.
	 * @param cp the cp.
	 * @return the result of the operation.
	 */
	@Override
	public double assignLaunchPower(Circuit circuit, Route route, Modulation modulation, int core, int[] spectrumAssigned, ControlPlane cp) {

		//For the CPSD algorithm to work correctly the PSD must be fixed.
		if (cp.getMesh().getPhysicalLayer().getFixedPowerSpectralDensity() == false) {
			cp.getMesh().getPhysicalLayer().setFixedPowerSpectralDensity(true);
		}

		double launchPower = Double.POSITIVE_INFINITY;
		circuit.setLaunchPowerLinear(launchPower);

		launchPower = cp.getMesh().getPhysicalLayer().getCircuitLaunchPower(circuit, modulation);

		return launchPower;
	}

}
