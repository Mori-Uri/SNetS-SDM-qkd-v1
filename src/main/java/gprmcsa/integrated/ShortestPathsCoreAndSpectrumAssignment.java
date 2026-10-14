package gprmcsa.integrated;

import java.util.List;
import java.util.Random;

import gprmcsa.coreSpectrumAssignment.CoreAndSpectrumAssignmentAlgorithmInterface;
import gprmcsa.modulation.Modulation;
import gprmcsa.routing.KRoutingAlgorithmInterface;
import gprmcsa.routing.Route;
import gprmcsa.routing.RoutingAlgorithmInterface;
import network.Circuit;
import network.ControlPlane;

/**
 * Algorithm created to apply sequentially the routing, modulation, core and spectrum allocation algorithms.
 *
 * @author Alexandre
 *
 */
public class ShortestPathsCoreAndSpectrumAssignment implements IntegratedRMLSAAlgorithmInterface {

    private RoutingAlgorithmInterface routing;
    //private ModulationSelectionAlgorithmInterface modulationSelection;
    private CoreAndSpectrumAssignmentAlgorithmInterface coreAndSpectrumAssignment;

    /**
     * Returns the rsa.
     * @param circuit the circuit.
     * @param cp the cp.
     * @return true if the condition is met; false otherwise.
     */
    @Override
    public boolean rsa(Circuit circuit, ControlPlane cp) {
        if (routing == null){
        	routing = cp.getRouting();
        }
        //if (modulationSelection == null){
        //    modulationSelection = cp.getModulationSelection();
        //}
        if(coreAndSpectrumAssignment == null){
        	coreAndSpectrumAssignment = cp.getCoreAndSpectrumAssignment();
		}

        //Select the route
        routing.findRoute(circuit, cp.getMesh());
        Route route = circuit.getRoute();

        List<Modulation> avaliableModulations = cp.getMesh().getAvaliableModulations();

        // Modulation, core and band chosen
        Modulation chosenMod = null;
        int chosenCore = -1;
        int chosenBand[] = null;

        // to avoid metrics error
  		Modulation checkMod = null;
  		int checkCore = -1;
  		int checkBand[] = null;

  		Modulation checkMod2 = null;
  		int checkCore2 = -1;
  		int checkBand2[] = null;

    	// Begins with the most spectrally efficient modulation format
		for (int m = avaliableModulations.size()-1; m >= 0; m--) {
			Modulation mod = avaliableModulations.get(m);
			circuit.setModulation(mod);

        	int slotsNumber = mod.requiredSlots(circuit.getRequiredBitRate());

            coreAndSpectrumAssignment.assignCoreAndSpectrum(slotsNumber, circuit, cp);
            int core = circuit.getIndexCore();
            int band[] = circuit.getSpectrumAssigned();

            if (band != null) {
        		checkMod = mod;
        		checkCore = core;
            	checkBand = band;

            	//Modulation has acceptable QoT
            	if (cp.isAdmissibleOSNR(circuit)){
            		checkMod2 = mod;
            		checkCore2 = core;
                	checkBand2 = band;

                	if (cp.isAdmissibleOSNRInOther(circuit)) { //Checks the OoT for others circuits
                		chosenMod = mod;
    	                chosenCore = core;
                    	chosenBand = band;

                    	break; //Stop when to reach acceptable QoTO
                	}
            	}
            }
        }

        if (chosenMod != null) { // If a modulation with acceptable QoT and QoTO was found
            circuit.setRoute(route);
            circuit.setModulation(chosenMod);
            circuit.setIndexCore(chosenCore);
            circuit.setSpectrumAssigned(chosenBand);

            return true;

        } else if(checkMod2 != null) {
            circuit.setRoute(route);
            circuit.setModulation(checkMod2);
            circuit.setIndexCore(checkCore2);
            circuit.setSpectrumAssigned(checkBand2);

            return false;

        } else {
        	if(checkMod == null){
				checkMod = avaliableModulations.get(0);
				checkCore = new Random().nextInt(cp.getMesh().getLinkList().get(0).getNumberOfCores());
			}
            circuit.setRoute(route);
            circuit.setModulation(checkMod);
            circuit.setIndexCore(checkCore);
            circuit.setSpectrumAssigned(checkBand);

            return false;
        }
    }

    /**
	 * Returns the routing algorithm
	 *
	 * @return KRoutingAlgorithmInterface
	 */
    public KRoutingAlgorithmInterface getRoutingAlgorithm(){
    	return null;
    }
}