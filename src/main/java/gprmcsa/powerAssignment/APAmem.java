package gprmcsa.powerAssignment;

import java.util.HashMap;
import java.util.Map;

import gprmcsa.modulation.Modulation;
import gprmcsa.routing.Route;
import network.Circuit;
import network.ControlPlane;

/**
 * This class represents the Adaptive Power Assignment with memory (APAmem) algorithm.
 * The APA algorithm assigns a power value per circuit in an adaptive way.
 *
 * Article: Network-state-dependent routing and route-dependent spectrum assignment for PRMLSA problem in all-optical elastic networks (2022).
 *
 * The values ​​of factorMult and limCount must be entered in the "others" configuration file as shown below.
 * {"variables":{
 *               "factorMult":"0.25",
 *               "limCount":"6"
 *              }
 * }
 *
 * @author Alexandre
 */
public class APAmem implements PowerAssignmentAlgorithmInterface {

	private Double factorMult;
	private Integer limCount;
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

		if(limCount == null){
			Map<String, String> uv = cp.getMesh().getOthersConfig().getVariables();
			if(uv.get("limCount") != null) {
				limCount = Integer.parseInt((String)uv.get("limCount"));
			}

			if(limCount == null) {
				limCount = 6;
			}
		}

		//For the APAmen algorithm to work correctly the PSD must be variable.
		if (cp.getMesh().getPhysicalLayer().getFixedPowerSpectralDensity()) {
			cp.getMesh().getPhysicalLayer().setFixedPowerSpectralDensity(false);
		}

		double launchPower = AdaptivePowerAssignmentWithMemory(circuit, route, modulation, core, spectrumAssigned, cp);
		circuit.setLaunchPowerLinear(launchPower);

		return launchPower;
	}

	/**
	 * This method applies the APAmem power assignment strategy.
	 *
	 * @param circuit Circuit
	 * @param route Route
	 * @param mod Modulation
	 * @param core int
	 * @param sa int[]
	 * @param cp ControlPlane
	 * @return double
	 */
    public double AdaptivePowerAssignmentWithMemory(Circuit circuit, Route route, Modulation mod, int core, int sa[], ControlPlane cp){

    	if (this.powerDatabase == null) {
    		this.powerDatabase = new HashMap<Route, HashMap<Double, HashMap<Modulation, Double>>>();
    	}

    	boolean EndMarker = false;
    	int NewCallDB = 0;
    	int CountSub = 0;
    	int CountAdd = 0;

    	boolean QoT = false;
    	boolean QoTO = false;
    	boolean XT = false;
    	boolean XTO = false;

    	double Pcurrent = 0.0;
		double OSNRcurrent = 0.0;

    	double OSNRth = mod.getSNRthreshold(); //dB

    	double Pmin = new EnPAAlgorithm().computePowerByLinearInterpolation(circuit, route, mod, core, sa, 0.0, cp);

    	double Pmax = cp.getMesh().getPhysicalLayer().computeMaximumPower(circuit, circuit.getRequiredBitRate(), route, 0, route.getNodeList().size() - 1, mod, core, sa);
		circuit.setLaunchPowerLinear(Pmax);

		cp.getMesh().getPhysicalLayer().isAdmissibleModultion(circuit, route, mod, core, sa, null, false);
		double OSNRmax = circuit.getSNR(); //dB

		// Is there a DB line with equal characteristics?
		boolean isPowerDB = false;
		HashMap<Double, HashMap<Modulation, Double>> trsModsPower = this.powerDatabase.get(route);
    	if (trsModsPower != null) {
    		HashMap<Modulation, Double> modsPower = trsModsPower.get(circuit.getRequiredBitRate());
    		if (modsPower != null) {
    			Double power = modsPower.get(mod);
    			if (power != null) {
    				Pcurrent = power;
    				isPowerDB = true;
    			}
    		}
    	}

    	if (!isPowerDB) { // If this does not exist in the database
    		NewCallDB++;

    		if (OSNRmax >= OSNRth) {
    			Pcurrent = Pmin + (this.factorMult * (Pmax - Pmin));
    		}else { // SNRmax < SNRth
    			Pcurrent = Pmax;
    		}

			while (EndMarker == false) {
				circuit.setLaunchPowerLinear(Pcurrent);
				QoT = cp.getMesh().getPhysicalLayer().isAdmissibleModultion(circuit, route, mod, core, sa, null, false);
				OSNRcurrent = circuit.getSNR(); //dB

				if (OSNRcurrent >= OSNRth) {

					QoTO = cp.isAdmissibleOSNRInOther(circuit); // Checks the QoT for others circuits

					XT = cp.isAdmissibleCrosstalk(circuit); // XT are acceptable
					XTO = cp.isAdmissibleCrosstalkInOther(circuit); // Checks the XT for others circuits

					if (QoT && QoTO && XT && XTO) {
						EndMarker = true;

						if (NewCallDB > 0) {
							NewCallDB++;
						}

					} else {
						CountSub++;

						if (CountSub >= limCount) {
							EndMarker = true;

						} else { // ContSub < 6
							Pcurrent = Pcurrent - 0.01 * Pcurrent;
						}
					}

				} else { // SNRcurrent < SNRth
					if(Pcurrent + 0.01 * Pcurrent > Pmax) {
						EndMarker = true;

					} else { // Pcurrent + 0.1 * Pcurrent <= Pmax
						CountAdd++;

						if(CountAdd >= limCount) {
							EndMarker = true;

						} else { // CountAdd < 6
							Pcurrent = Pcurrent + 0.1 * Pcurrent;
						}
					}
				}
			}
    	}

		if (NewCallDB > 1) {
			// Saves the power in the database

			trsModsPower = this.powerDatabase.get(route);
	    	if (trsModsPower == null) {
	    		trsModsPower = new HashMap<Double, HashMap<Modulation,Double>>();
	    		this.powerDatabase.put(route, trsModsPower);
	    	}

	    	HashMap<Modulation, Double> modsPower = trsModsPower.get(circuit.getRequiredBitRate());
    		if (modsPower == null) {
    			modsPower = new HashMap<Modulation, Double>();
    			trsModsPower.put(circuit.getRequiredBitRate(), modsPower);
    		}

    		modsPower.put(mod, Pcurrent);
		}

    	return Pcurrent;
    }
}
