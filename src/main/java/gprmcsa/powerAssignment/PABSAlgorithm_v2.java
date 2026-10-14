package gprmcsa.powerAssignment;

import java.util.HashMap;
import java.util.Map;

import gprmcsa.modulation.Modulation;
import gprmcsa.routing.Route;
import network.Circuit;
import network.ControlPlane;

/**
 * This class represents the Power Assignment by Binary Search (PABS).
 * The PABS algorithm is based on the binary search method for assigning power per circuit in an adaptive way.
 *
 * Article: New algorithm for per-circuit power assignment in elastic optical networks (2020).
 *
 * The value of margin must be entered in the configuration file "others" as shown below.
 * {"variables":{
 *               "margin":"0.25"
 *              }
 * }
 *
 * @author Alexandre
 *
 */
public class PABSAlgorithm_v2 implements PowerAssignmentAlgorithmInterface {

	private Double margin;
    private HashMap<Route, HashMap<Double, HashMap<Modulation, Double>>> powerDatabase; // route, transmission rate e modulation

    final int MAX_ATTEMPTS_COUNTER = 50;

    private int numAttemptsQoTOCounter = 0;
    private int numAttemptsCounter = 0;

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

		if(margin == null){
			Map<String, String> uv = cp.getMesh().getOthersConfig().getVariables();
			if(uv.get("margin") != null) {
				margin = Double.parseDouble((String)uv.get("margin"));
			}

			if(margin == null) {
				System.out.println("The OSNR margin was not found. Using the default value.");
				margin = 1.0;
			}
		}

		//For the PABS algorithm to work correctly the PSD must be variable.
		if (cp.getMesh().getPhysicalLayer().getFixedPowerSpectralDensity()) {
			cp.getMesh().getPhysicalLayer().setFixedPowerSpectralDensity(false);
		}

		double launchPower = PowerAssignmentByBinarySearch(circuit, route, modulation, core, spectrumAssigned, cp);
		circuit.setLaunchPowerLinear(launchPower);

		return launchPower;
	}

	/**
	 * Returns the numAttemptsQoTOCounter
	 *
	 * @return numAttemptsQoTOCounter - int
	 */
	public int getNumAttemptsQoTOCounter() {
		return numAttemptsQoTOCounter;
	}

	/**
	 * Returns the numAttemptsCounter
	 *
	 * @return numAttemptsCounter - int
	 */
	public int getNumAttemptsCounter() {
		return numAttemptsCounter;
	}

	/**
	 * This method applies the PABS power assignment strategy.
	 *
	 * @param circuit Circuit
	 * @param route Route
	 * @param modulation Modulation
	 * @param core int
	 * @param spectrumAssigned int[]
	 * @param cp ControlPlane
	 * @return double
	 */
    public double PowerAssignmentByBinarySearch(Circuit circuit, Route route, Modulation modulation, int core, int spectrumAssigned[], ControlPlane cp){

    	if(this.powerDatabase == null) {
    		this.powerDatabase = new HashMap<Route, HashMap<Double, HashMap<Modulation, Double>>>();
    	}

		double SNRcurrent = 0.0;
		double SNRdif = 0.0;
		double powerDB = 0.0;
		double tolPower = 1e-6; // power tolerance

		boolean QoTO = false;
		boolean XT = false;
		boolean XTO = false;
		int attemptsCounter = 0; // Count attempts to reach SNRth
		int attemptsQoTOCounter = 0; // Count attempts to reach acceptable QoTO

		// To save the amount of attempts to reach SNT and QoTO
		this.numAttemptsQoTOCounter = 0;
		this.numAttemptsCounter = 0;

		double SNRth = modulation.getSNRthresholdLinear(); //Linear
		SNRth = SNRth + this.margin; //margin is linear

		// If the margin value is dB
		//double marginLinear = PhysicalLayer.ratioOfDB(margin);
		//SNRth = SNRth + marginLinear;

		boolean toSaveDB = false;
    	boolean isPowerDB = false;
		HashMap<Double, HashMap<Modulation, Double>> trsModsPower = this.powerDatabase.get(route);
    	if(trsModsPower != null) {
    		HashMap<Modulation, Double> modsPower = trsModsPower.get(circuit.getRequiredBitRate());
    		if(modsPower != null) {
    			Double power = modsPower.get(modulation);
    			if(power != null) {
    				powerDB = power;
    				isPowerDB = true;
    			}
    		}
    	}

		double Pmax = cp.getMesh().getPhysicalLayer().computeMaximumPower(circuit, circuit.getRequiredBitRate(), route, 0, route.getNodeList().size() - 1, modulation, core, spectrumAssigned);
		circuit.setLaunchPowerLinear(Pmax);
		SNRcurrent = cp.getMesh().getPhysicalLayer().computeSNRSegment(circuit, route, 0, route.getNodeList().size() - 1, modulation, core, spectrumAssigned, null, false);

		if (SNRcurrent < SNRth) {
			return Pmax;
		}

		double Pmin = new EnPAAlgorithm().computePowerByLinearInterpolation(circuit, route, modulation, core, spectrumAssigned, 0.0, cp);
		circuit.setLaunchPowerLinear(Pmin);
		SNRcurrent = cp.getMesh().getPhysicalLayer().computeSNRSegment(circuit, route, 0, route.getNodeList().size() - 1, modulation, core, spectrumAssigned, null, false);

		double Pcurrent = Pmin;

		while (true) {
			attemptsCounter++;
			this.numAttemptsCounter = attemptsCounter;

			if (isPowerDB) {
				Pcurrent = powerDB;
				isPowerDB = false;

			} else {
				Pcurrent = (Pmin + Pmax) / 2.0;
			}

			circuit.setLaunchPowerLinear(Pcurrent);
			SNRcurrent = cp.getMesh().getPhysicalLayer().computeSNRSegment(circuit, route, 0, route.getNodeList().size() - 1, modulation, core, spectrumAssigned, null, false);

			SNRdif = SNRcurrent - SNRth;

			if (SNRdif >= 0.0) {
				XT = cp.isAdmissibleCrosstalk(circuit);

				if (XT) { // XT are acceptable
					attemptsQoTOCounter++;
					this.numAttemptsQoTOCounter = attemptsQoTOCounter;

					QoTO = cp.isAdmissibleOSNRInOther(circuit); // Checks the QoT for others circuits
	                XTO = cp.isAdmissibleCrosstalkInOther(circuit); // Checks the XT for others circuits

					if (QoTO && XTO) {
						toSaveDB = true;
						break;
					}
				}
			}

			if(SNRdif >= 0.0) {
				Pmax = Pcurrent;

			} else {
				Pmin = Pcurrent;
			}

			if (Math.abs(Pmax - Pmin) < tolPower) {
                break; // converged in power
            }

			if(attemptsCounter > MAX_ATTEMPTS_COUNTER) {
				break;
			}
		}

		if (toSaveDB && (Pcurrent != powerDB)) {
			// Saves the power in the database

			trsModsPower = this.powerDatabase.get(route);
	    	if(trsModsPower == null) {
	    		trsModsPower = new HashMap<Double, HashMap<Modulation,Double>>();
	    		this.powerDatabase.put(route, trsModsPower);
	    	}

	    	HashMap<Modulation, Double> modsPower = trsModsPower.get(circuit.getRequiredBitRate());
    		if(modsPower == null) {
    			modsPower = new HashMap<Modulation, Double>();
    			trsModsPower.put(circuit.getRequiredBitRate(), modsPower);
    		}

    		modsPower.put(modulation, Pcurrent);
		}

		return Pcurrent;
	}
}
