package gprmcsa.powerAssignment;

import java.util.HashMap;
import java.util.Map;

import gprmcsa.modulation.Modulation;
import gprmcsa.routing.Route;
import network.Circuit;
import network.ControlPlane;

/**
 * This class represents the Adaptive Power Assignment (APA) algorithm.
 * The APA algorithm assigns a power value per circuit in an adaptive way.
 *
 * Article: Power, routing, Modulation Level and Spectrum Assignment in all-optical and elastic networks (2019).
 *
 * The value of factorMult must be entered in the configuration file "others" as shown below.
 * {"variables":{
 *               "factorMult":"0.25"
 *              }
 * }
 *
 * @author Alexandre
 *
 */
public class APAAlgorithm implements PowerAssignmentAlgorithmInterface {

	private Double factorMult;
    private HashMap<Route, HashMap<Double, HashMap<Modulation, Double>>> powerDatabase; // route, transmission rate e modulation

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

		if(factorMult == null){
			Map<String, String> uv = cp.getMesh().getOthersConfig().getVariables();
			if(uv.get("factorMult") != null) {
				factorMult = Double.parseDouble((String)uv.get("factorMult"));
			}

			if(factorMult == null) {
				System.out.println("The factorMult was not found. Using the default value.");
				factorMult = 1.0;
			}
		}

		//For the APA algorithm to work correctly the PSD must be variable.
		if (cp.getMesh().getPhysicalLayer().getFixedPowerSpectralDensity()) {
			cp.getMesh().getPhysicalLayer().setFixedPowerSpectralDensity(false);
		}

		double launchPower = AdaptivePowerAssignment(circuit, route, modulation, core, spectrumAssigned, cp);
		circuit.setLaunchPowerLinear(launchPower);

		return launchPower;
	}

	/**
	 * This method applies the APA power assignment strategy.
	 *
	 * @param circuit Circuit
	 * @param route Route
	 * @param mod Modulation
	 * @param core int
	 * @param sa int[]
	 * @param cp ControlPlane
	 * @return double
	 */
    public double AdaptivePowerAssignment(Circuit circuit, Route route, Modulation mod, int core, int sa[], ControlPlane cp){

    	if(this.powerDatabase == null) {
    		this.powerDatabase = new HashMap<Route, HashMap<Double, HashMap<Modulation, Double>>>();
    	}

    	int NewCall = 0;
    	int CountSub = 0;
    	int CountAdd = 0;

    	boolean QoT = false;
    	boolean QoTO = false;

    	double Pcurrent = 0.0;
		double SNRcurrent = 0.0;

    	double SNRth = mod.getSNRthreshold(); //dB

    	double Pmin = 1.0E-11; //W, -80 dBm

    	double Pmax = cp.getMesh().getPhysicalLayer().computeMaximumPower(circuit, circuit.getRequiredBitRate(), route, 0, route.getNodeList().size() - 1, mod, core, sa);
		circuit.setLaunchPowerLinear(Pmax);

		cp.getMesh().getPhysicalLayer().isAdmissibleModultion(circuit, route, mod, core, sa, null, false);
		double SNRmax = circuit.getSNR(); //dB

		boolean isPowerDB = false;
		HashMap<Double, HashMap<Modulation, Double>> trsModsPower = this.powerDatabase.get(route);
    	if(trsModsPower != null) {
    		HashMap<Modulation, Double> modsPower = trsModsPower.get(circuit.getRequiredBitRate());
    		if(modsPower != null) {
    			Double power = modsPower.get(mod);
    			if(power != null) {
    				Pcurrent = power;
    				isPowerDB = true;
    			}
    		}
    	}

    	boolean search = true;
    	if(!isPowerDB) { // If this does not exist in the database
    		NewCall++;

    		if(SNRmax >= SNRth) {
    			Pcurrent = Pmin + (this.factorMult * (Pmax - Pmin));
    			// Proceed to line 3
    		}else { // SNRmax < SNRth
    			Pcurrent = Pmax;
    			search = false;
    			// Proceed to line 5
    		}
    	}

		while (search) {
			circuit.setLaunchPowerLinear(Pcurrent);
			QoT = cp.getMesh().getPhysicalLayer().isAdmissibleModultion(circuit, route, mod, core, sa, null, false);
			SNRcurrent = circuit.getSNR(); //dB

			if(SNRcurrent >= SNRth) { // Line 3

				QoTO = cp.isAdmissibleOSNRInOther(circuit);

				if(QoT && QoTO) {
					if(NewCall > 0) {
						NewCall++;
					}
					break; // Proceed to line 5

				} else {
					CountSub++;

					if(CountSub >= 6) {
						break; // Proceed to line 5

					} else { // ContSub < 6
						Pcurrent = Pcurrent - 0.1 * Pcurrent;
						// Proceed to line 3
					}
				}

			} else { // SNRcurrent < SNRth
				if(Pcurrent + 0.1 * Pcurrent > Pmax) {
					break; // Proceed to line 5

				} else { // Pcurrent + 0.1 * Pcurrent <= Pmax
					CountAdd++;

					if(CountAdd >= 6) {
						break; // Proceed to line 5

					} else { // CountAdd < 6
						Pcurrent = Pcurrent + 0.1 * Pcurrent;
						// Proceed to line 3
					}
				}
			}
		}

		if (NewCall > 1) { // Line 5
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

    		modsPower.put(mod, Pcurrent);
		}

    	return Pcurrent;
    }

}
