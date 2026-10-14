package gprmcsa.powerAssignment;

import gprmcsa.modulation.Modulation;
import gprmcsa.routing.Route;
import network.Circuit;
import network.ControlPlane;

/**
 * This class represents the Constant Power Assignment (CPA) algorithm.
 * The CPA strategy assigns the same power to all circuits.
 *
 * Article: Power, routing, Modulation Level and Spectrum Assignment in all-optical and elastic networks (2019).
 *
 * @author Alexandre
 *
 */
public class CPAAlgorithm implements PowerAssignmentAlgorithmInterface {

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

		//For the CPA algorithm to work correctly the PSD must be variable.
		if (cp.getMesh().getPhysicalLayer().getFixedPowerSpectralDensity()) {
			cp.getMesh().getPhysicalLayer().setFixedPowerSpectralDensity(false);
		}

		double launchPower = cp.getMesh().getPhysicalLayer().getPowerLinear();
		circuit.setLaunchPowerLinear(launchPower);

		return launchPower;
	}

}
