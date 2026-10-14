package gprmcsa.integrated;

import java.util.List;
import java.util.Map;
import java.util.Random;

import gprmcsa.coreSpectrumAssignment.CoreAndSpectrumAssignmentAlgorithmInterface;
import gprmcsa.modulation.Modulation;
import gprmcsa.powerAssignment.PABSAlgorithm;
import gprmcsa.powerAssignment.PABSAlgorithm_v2;
import gprmcsa.powerAssignment.PowerAssignmentAlgorithmInterface;
import gprmcsa.routing.KRoutingAlgorithmInterface;
import gprmcsa.routing.Route;
import network.Circuit;
import network.ControlPlane;

/**
 * Algorithm created to apply integrally the PRMCS Assignment algorithms.
 * PRMCS - Power, Routing, Modulation, Core and Spectrum.
 *
 * @author Alexandre
 *
 */
public class IntegratedPRMCSAAlgorithm_v2 implements IntegratedRMLSAAlgorithmInterface {

	private int k = 3; // Number of alternative routes
    private KRoutingAlgorithmInterface kShortestsPaths;
    //private ModulationSelectionAlgorithmInterface modulationSelection;
    //private SpectrumAssignmentAlgorithmInterface spectrumAssignment;
    private CoreAndSpectrumAssignmentAlgorithmInterface coreAndSpectrumAssignment;
    private PowerAssignmentAlgorithmInterface powerAssignmet;

    /**
     * Returns the rsa.
     * @param circuit the circuit.
     * @param cp the cp.
     * @return true if the condition is met; false otherwise.
     */
    @Override
    public boolean rsa(Circuit circuit, ControlPlane cp) {
        if (kShortestsPaths == null){
            // To read the value of k from the "others" file
        	Map<String, String> uv = cp.getMesh().getOthersConfig().getVariables();
			if(uv.get("k") != null) {
				k = Integer.parseInt((String)uv.get("k"));
			}

        	kShortestsPaths = cp.getKRouting();
        	kShortestsPaths.computeAllRoutes(cp.getMesh(), k);
        }
        //if (modulationSelection == null){
        //    modulationSelection = cp.getModulationSelection();
        //}
        //if(spectrumAssignment == null){
        //    spectrumAssignment = cp.getSpectrumAssignment();
        //}
        if(coreAndSpectrumAssignment == null){
        	coreAndSpectrumAssignment = cp.getCoreAndSpectrumAssignment();
		}
        if(powerAssignmet == null) {
        	powerAssignmet = cp.getPowerAssignment();
        }

        List<Modulation> avaliableModulations = cp.getMesh().getAvaliableModulations();
        List<Route> candidateRoutes = kShortestsPaths.getRoutes(circuit.getSource(), circuit.getDestination());

        // chosen route, modulation, band and power
        Route chosenRoute = null;
        Modulation chosenMod = null;
        int chosenCore = -1;
        int chosenBand[] = null;
        Double chosenPower = Double.POSITIVE_INFINITY;

        // to avoid metrics error
  		Route checkRoute = null;
  		Modulation checkMod = null;
  		int checkCore = -1;
  		int checkBand[] = null;
  		Double checkPower = Double.POSITIVE_INFINITY;

  		Route checkRoute2 = null;
  		Modulation checkMod2 = null;
  		int checkCore2 = -1;
  		int checkBand2[] = null;
  		Double checkPower2 = Double.POSITIVE_INFINITY;

  		boolean QoTO = false;
  		boolean XTO = false;

        for (Route route : candidateRoutes) {
            circuit.setRoute(route);

        	// Begins with the most spectrally efficient modulation format
    		for (int m = avaliableModulations.size()-1; m >= 0; m--) {
    			Modulation mod = avaliableModulations.get(m);
    			circuit.setModulation(mod);

	            int slotsNumber = mod.requiredSlots(circuit.getRequiredBitRate());

	            coreAndSpectrumAssignment.assignCoreAndSpectrum(slotsNumber, circuit, cp);
	            int core = circuit.getIndexCore();
	            int band[] = circuit.getSpectrumAssigned();

	    		if (band != null) {
	    			double launchPower = Double.POSITIVE_INFINITY;

	    			launchPower = powerAssignmet.assignLaunchPower(circuit, route, mod, core, band, cp);
	    			circuit.setLaunchPowerLinear(launchPower);

	    			checkRoute = route;
	    			checkMod = mod;
	    			checkCore = core;
            		checkBand = band;
            		checkPower = launchPower;

            		// Check the physical layer
            		boolean QoT = cp.isAdmissibleOSNR(circuit);
            		boolean XT = cp.isAdmissibleCrosstalk(circuit);

	    			if (QoT && XT) { // QoT and XT are acceptable
	    				checkRoute2 = route;
		    			checkMod2 = mod;
		    			checkCore2 = core;
	            		checkBand2 = band;
	            		checkPower2 = launchPower;

		                // Checks the QoT and XT for others circuits
	            		QoTO = cp.isAdmissibleOSNRInOther(circuit);
		                XTO = cp.isAdmissibleCrosstalkInOther(circuit);

		                if (QoTO && XTO) {
		                	chosenRoute = route;
			                chosenMod = mod;
			                chosenCore = core;
			                chosenBand = band;
			                chosenPower = launchPower;
		                }
	            	}
	            } //core and spectrum

	    		if (QoTO && XTO) { // to exit the search for modulations
	            	break;
	            }
            } //modulations

    		if (QoTO && XTO) { // to exit search for route
            	break;
            }
        } //routes

        if (chosenRoute != null) { //If there is no route chosen is why no available resource was found on any of the candidate routes
            circuit.setRoute(chosenRoute);
            circuit.setModulation(chosenMod);
            circuit.setIndexCore(chosenCore);
            circuit.setSpectrumAssigned(chosenBand);
            circuit.setLaunchPowerLinear(chosenPower);

            return true;

        } else if(checkRoute2 != null) {
            circuit.setRoute(checkRoute2);
            circuit.setModulation(checkMod2);
            circuit.setIndexCore(checkCore2);
            circuit.setSpectrumAssigned(checkBand2);
            circuit.setLaunchPowerLinear(checkPower2);

            return false;

        } else {
        	if(checkRoute == null){
				checkRoute = candidateRoutes.get(0);
				checkMod = avaliableModulations.get(0);
				checkCore = new Random().nextInt(cp.getMesh().getLinkList().get(0).getNumberOfCores());
			}
            circuit.setRoute(checkRoute);
            circuit.setModulation(checkMod);
            circuit.setIndexCore(checkCore);
            circuit.setSpectrumAssigned(checkBand);
            circuit.setLaunchPowerLinear(checkPower);

            return false;
        }
    }

    /**
	 * Returns the routing algorithm
	 *
	 * @return KRoutingAlgorithmInterface
	 */
    public KRoutingAlgorithmInterface getRoutingAlgorithm(){
    	return kShortestsPaths;
    }

}
