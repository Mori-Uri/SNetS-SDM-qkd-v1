package gprmcsa.integrated;

import java.util.List;
import java.util.Map;
import java.util.Random;

import gprmcsa.modulation.Modulation;
import gprmcsa.routing.KRoutingAlgorithmInterface;
import gprmcsa.routing.Route;
import network.Circuit;
import network.ControlPlane;
import network.Crosstalk;
import network.PhysicalLayer;
import util.IntersectionFreeSpectrum;

/**
 * Represents the SRMC_minXTlim component.
 */
public class SRMC_minXTlim implements IntegratedRMLSAAlgorithmInterface {
	
	private int k = 3; //This algorithm uses 3 alternative paths
	private KRoutingAlgorithmInterface kShortestsPaths; //For routing algorithms with more than one route
	
	private double minXTthreshold = PhysicalLayer.ratioForDB(Crosstalk.lowestXT); //dB
	
	@Override
	public boolean rsa(Circuit circuit, ControlPlane cp) {
		if (kShortestsPaths == null){
			// To read the value of k from the "others" file
        	Map<String, String> otherConfig = cp.getMesh().getOthersConfig().getVariables();
			if(otherConfig.get("k") != null) {
				k = Integer.parseInt((String)otherConfig.get("k"));
			}
			
			if(otherConfig.get("minXTthreshold") != null) {
				minXTthreshold = Double.parseDouble((String)otherConfig.get("minXTthreshold"));
			}
			
        	kShortestsPaths = cp.getKRouting();
        	kShortestsPaths.computeAllRoutes(cp.getMesh(), k);
        }
		
        List<Modulation> avaliableModulations = cp.getMesh().getAvaliableModulations();
        List<Route> candidateRoutes = kShortestsPaths.getRoutes(circuit.getSource(), circuit.getDestination());
        
        // Route, modulation, core and band chosen
        Route chosenRoute = null;
        Modulation chosenMod = null;
        int chosenCore = -1;
        int chosenBand[] = null;
        
        // To avoid metrics errorr
  		Route checkRoute = null;
  		Modulation checkMod = null;
  		int checkCore = -1;
  		int checkBand[] = null;
  		
  		Route checkRoute2 = null;
  		Modulation checkMod2 = null;
  		int checkCore2 = -1;
  		int checkBand2[] = null;
  		
  		// Para selecionar alow do threshold de XT
  		Route chosenRoute2 = null;
        Modulation chosenMod2 = null;
        int chosenCore2 = -1;
        int chosenBand2[] = null;
  		
  		// Initializing QoT verification variables
  		boolean QoT = false;
  		boolean XT = false;
  		boolean QoTO = false;
  		boolean XTO = false;
  		
  		int numberOfCores = cp.getMesh().getLinkList().get(0).getNumberOfCores();
  		int numberOfSlots = cp.getMesh().getLinkList().get(0).getCore(0).getNumOfSlots();
  		
  		double minXT = Double.POSITIVE_INFINITY;
  		
  		// Goes through the slot band
  		for (int slot = 1; slot <= numberOfSlots; slot++) {
  			
	  		// Go through all candidate routes
	        for (Route route : candidateRoutes) {
	            circuit.setRoute(route);
	            
	            // Begins with the most spectrally efficient modulation format
	    		for (int m = avaliableModulations.size()-1; m >= 0; m--) {
	    			Modulation mod = avaliableModulations.get(m);
	    			circuit.setModulation(mod);
	            
	    			int frequencySlotsNecessary = mod.requiredSlots(circuit.getRequiredBitRate());
	    			
	    			// It goes through the cores starting from the highest index to the lowest index
	    			for (int core = numberOfCores-1; core >= 0; core--) {
	    				circuit.setIndexCore(core);
	    				
	    	            int band[] = assignSpectrum(frequencySlotsNecessary, circuit, cp, route, core, slot);
	    	            circuit.setSpectrumAssigned(band);
	    	            
	    	            if (band != null) {
	    	    			checkRoute = route;
	                		checkMod = mod;
	                		checkCore = core;
	    	            	checkBand = band;
	    	                
	    	            	// Check the physical layer
	                		QoT = cp.isAdmissibleOSNR(circuit);
	                		XT = true;
	                		if (QoT) { // Only tests XT if it passes through OSNR
	                			XT = cp.isAdmissibleCrosstalk(circuit);
	                		}
	                		
	    	    			if (QoT && XT) { // QoT and XT are acceptable
	    	    				checkRoute2 = route;
	    		    			checkMod2 = mod;
	    		    			checkCore2 = core;
	    	            		checkBand2 = band;
	    		                
	    	            		// Checks the QoT and XT for others circuits
	    	            		QoTO = cp.isAdmissibleOSNRInOther(circuit);
	    	            		XTO = true;
	    	            		if (QoTO) { // Only tests XT if it passes through OSNR
	    	            			XTO = cp.isAdmissibleCrosstalkInOther(circuit);
	    	            		}
	    	            		
	    		                if (QoTO && XTO) { // QoTO and XTO are acceptable
	    		                	
	    		                	double xt = circuit.getXt();
	    		            		
	    		            		if (xt <= minXTthreshold) {
	        			                if (chosenRoute2 == null) {
		    		            			chosenRoute2 = route;
		        			                chosenMod2 = mod;
		        			                chosenCore2 = core;
		        			                chosenBand2 = band;
		    		            		}
	    		            		} else {
	    		            			if (xt < minXT) {
		    		            			minXT = xt;
		    		            			
		    		            			chosenRoute = route;
		        			                chosenMod = mod;
		        			                chosenCore = core;
		        			                chosenBand = band;
		    		            		}
	    		            		}
	    		                }
	    	            	}
	    	            } //spectrum
	    			} //core
	            } //modulations
	        } //routes
		} //slots
  		
  		// Prioritizes the XT threshold choice
  		if (chosenRoute2 != null) {
  			chosenRoute = chosenRoute2;
            chosenMod = chosenMod2;
            chosenCore = chosenCore2;
            chosenBand = chosenBand2;
  		}
  		
        if (chosenRoute != null) { //If there is no route chosen is why no available resource was found on any of the candidate routes
            circuit.setRoute(chosenRoute);
            circuit.setModulation(chosenMod);
            circuit.setIndexCore(chosenCore);
            circuit.setSpectrumAssigned(chosenBand);

            return true;
            
        } else if(checkRoute2 != null) {
            circuit.setRoute(checkRoute2);
            circuit.setModulation(checkMod2);
            circuit.setIndexCore(checkCore2);
            circuit.setSpectrumAssigned(checkBand2);
            
            return false;
            
        } else {
        	if(checkRoute == null){
				checkRoute = candidateRoutes.get(0);
				checkMod = avaliableModulations.get(0);
				checkCore = new Random().nextInt(numberOfCores);
			}
            circuit.setRoute(checkRoute);
            circuit.setModulation(checkMod);
            circuit.setIndexCore(checkCore);
            circuit.setSpectrumAssigned(checkBand);
            
            return false;
        }
	}
	
	/**
	 * Performs spectrum allocation strategy selection
	 * 
	 * @param frequencyslotsNecessary int
	 * @param circuit Circuit
	 * @param cp ControlPlane
	 * @param route Route
	 * @param core int
	 * @param slotIndex int
	 * @return int[]
	 */
	private int[] assignSpectrum(int frequencySlotsNecessary, Circuit circuit, ControlPlane cp, Route route, int core, int slotIndex) {
		
		int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
        if(frequencySlotsNecessary > maxAmplitude) return null;
        
        int chosen[] = null;
        List<int[]> composition = IntersectionFreeSpectrum.merge(route, circuit.getGuardBand(), core);
    	
    	if (composition != null) {
    		
	    	//First Fit strategy
	        for (int[] band : composition) {
	        	if ((slotIndex >= band[0]) && (slotIndex + frequencySlotsNecessary -1 <= band[1])) { //If the slot index is within the range
	                chosen = band.clone();
	                chosen[0] = slotIndex;
	                chosen[1] = chosen[0] + frequencySlotsNecessary - 1; //It is not necessary to allocate the entire band, just the amount of slots required
	                break;
	        	}
	        }
    	}
        
    	return chosen;
	}
	
	/**
	 * Returns the routing algorithm
	 * 
	 * @return KRoutingAlgorithmInterface
	 */
	@Override
	public KRoutingAlgorithmInterface getRoutingAlgorithm() {
		return kShortestsPaths;
	}
}
