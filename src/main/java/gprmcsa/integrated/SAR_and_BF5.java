package gprmcsa.integrated;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Vector;

import gprmcsa.modulation.Modulation;
import gprmcsa.routing.KRoutingAlgorithmInterface;
import gprmcsa.routing.Route;
import network.Circuit;
import network.ControlPlane;
import network.Link;
import network.Node;
import network.PhysicalLayer;
import util.ComputesFragmentation;
import util.IntersectionFreeSpectrum;
import util.tools.machineLearning.collectingData.LineData;

/**
 * Represents the SAR_and_BF5 component.
 */
public class SAR_and_BF5 implements IntegratedRMLSAAlgorithmInterface {
	
	private int k = 3; //This algorithm uses 3 alternative paths
	private KRoutingAlgorithmInterface kShortestsPaths; //For routing algorithms with more than one route
	
    private List<LineData> lineDataList = new ArrayList<LineData>();
    private int requestCounter = 0;
    private int differentQuantity = 0;
    
    private boolean saveLineData = false;
    private static final int SAVE_LINE_DATA_INTERVAL = 500;
	
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
  		
  		//Escolha do BF5
  		Route chosenRouteBF5 = null;
        Modulation chosenModBF5 = null;
        int chosenCoreBF5 = -1;
        int chosenBandBF5[] = null;
        LineData chosenLineBF5 = null;
  		
  		// Initializing QoT verification variables
  		boolean QoT = false;
  		boolean XT = false;
  		boolean QoTO = false;
  		boolean XTO = false;
  		
  		int numberOfCores = cp.getMesh().getLinkList().get(0).getNumberOfCores();
  		int numberOfSlots = cp.getMesh().getLinkList().get(0).getCore(0).getNumOfSlots();
  		
  		double minXT = Double.POSITIVE_INFINITY;
  		
  		requestCounter++;
  		saveLineData = requestCounter == 1 || requestCounter % SAVE_LINE_DATA_INTERVAL == 0;
  		
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
	    	            
	    	            LineData lineData = null;
			            if (saveLineData) {
			            	lineData = createLineData(requestCounter, "NONE", circuit, route, mod, core, band, cp);
				            lineDataList.add(lineData);
			            }
			            
			            if (band == null && lineData != null) {
			            	lineData.setWasBlocked(1);
			            	lineData.setBlockCause(Circuit.BY_OTHER);
			            }
	    	            
	    	            if (band != null) {
	    	    			checkRoute = route;
	                		checkMod = mod;
	                		checkCore = core;
	    	            	checkBand = band;
	    	                
	    	            	// Check the ONSR and XT
	                		QoT = cp.isAdmissibleOSNR(circuit);
	                		XT = cp.isAdmissibleCrosstalk(circuit);
	                		
	                		if (QoT == false && lineData != null) {
				            	lineData.setWasBlocked(1);
				            	lineData.setBlockCause(Circuit.BY_QOTN);
				            }
	                		
	                		if (XT == false && lineData != null) {
				            	lineData.setWasBlocked(1);
				            	lineData.setBlockCause(Circuit.BY_XTN);
				            }
	                		
	    	    			if (QoT && XT) { // QoT and XT are acceptable
	    	    				checkRoute2 = route;
	    		    			checkMod2 = mod;
	    		    			checkCore2 = core;
	    	            		checkBand2 = band;
	    		                
	    	            		// Checks the ONSR and XT for others circuits
	    	            		QoTO = cp.isAdmissibleOSNRInOther(circuit);
	    	            		XTO = cp.isAdmissibleCrosstalkInOther(circuit);
	    	            		
	    	            		if (QoTO == false&& lineData != null ) {
					            	lineData.setWasBlocked(1);
					            	lineData.setBlockCause(Circuit.BY_QOTO);
					            }
	    	            		
	    	            		if (XTO == false && lineData != null) {
					            	lineData.setWasBlocked(1);
					            	lineData.setBlockCause(Circuit.BY_XTO);
					            }
	    	            		
	    		                if (QoTO && XTO) { // QoTO and XTO are acceptable
	    		                	
	    		                	if (lineData != null) {
		    		                	lineData.setWasBlocked(0);
						            	lineData.setBlockCause(Circuit.NO_BLOCKING);
	    		                	}
	    		                	
	    		                	if (chosenRoute == null) {
	    		                		//SAR choice
		    		                	chosenRoute = route;
	        			                chosenMod = mod;
	        			                chosenCore = core;
	        			                chosenBand = band;
	        			                
	        			                if (lineData != null) {
	        			                	lineData.setAlgorithm("SAR"); //Mark the line as a solution for SALT.
	        			                }
	    		                	}
        			                
        			                //To BF5
	    		                	double xt = circuit.getXt();
	    		            		if (xt < minXT) {
	    		            			minXT = xt;
	    		            			
	    		            			//BF5 choice
	    		            			chosenRouteBF5 = route;
	        			                chosenModBF5 = mod;
	        			                chosenCoreBF5 = core;
	        			                chosenBandBF5 = band;
	        			                
	        			                if (saveLineData) {
	        			                	//Mark the line as a BF5 solution.
	        			                	LineData lineBF5 = createLineData(requestCounter, "BF5", circuit, chosenRouteBF5, chosenModBF5, chosenCoreBF5, chosenBandBF5, cp);
		        			                lineBF5.setWasBlocked(0);
		        			                lineBF5.setBlockCause(Circuit.NO_BLOCKING);
		        			                
		        			                //If a line already existsd marked as a BF5 solution
		        			                if (chosenLineBF5 != null) {
		        			                	lineDataList.remove(chosenLineBF5);
		        			                }
		        			                
		        			                chosenLineBF5 = lineBF5; //Updates to the new BF5 line.
		        				            lineDataList.add(lineBF5);
		    		            		}
	    		            		}
	    		                }
	    	            	}
	    	            } //spectrum
	    			} //core
	            } //modulations
	        } //routes
		} //slots
  		
  		//Check if the SAR solution differed from the BF5 solution.
  		if (chosenRoute != null && chosenRouteBF5 != null) {
  		    boolean different = chosenRoute != chosenRouteBF5 || 
  		    					chosenMod != chosenModBF5 || 
  		    					chosenCore != chosenCoreBF5 ||
  		    					!Arrays.equals(chosenBand, chosenBandBF5);

  		    if (different) {
  		        differentQuantity++;
  		    }
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
	
	/**
	 * Returns a line from the file.
	 * 
	 * @param algo String
	 * @param circuit Circuit
	 * @param route Route
	 * @param mod Modulation
	 * @param core int
	 * @param band int[]
	 * @param cp ControlPlane
	 * @return String
	 */
	private LineData createLineData(int request, String algo, Circuit circuit, Route route, Modulation mod, int core, int band[], ControlPlane cp) {
		
		//To avoid mistakes
		circuit.setRoute(route);
		circuit.setModulation(mod);
		circuit.setIndexCore(core);
		circuit.setSpectrumAssigned(band);
		
		int req_v = request;
		double bitRate_v = circuit.getRequiredBitRate();
		String route_v = "NONE";
		int hops_v = 0;
		double distance_v = 0.0;
		String mod_v = "NONE";
		String band_v = "NONE";
		int core_v = -1;
		double osnr_v = 0.0;
		double deltaOsnr_v = 0.0;
		double deltaOsnrNeigh_v = 0.0;
		double xt_v = 0.0;
		double deltaXT_v = 0.0;
		double deltaXTAdj_v = 0.0;
		int overlaps_v = 0;
		double launchPower_v = 0.0;
		double ut_v = 0.0;
		double frag_v = 0.0;
		
		
		if (route != null) {
			route_v = getRouteString(route);
			hops_v = route.getHops();
			distance_v = route.getDistanceAllLinks();
		}
		
		if (mod != null) {
			mod_v = mod.getName();
		}
		
		if (band != null) {
			band_v = band[0] + "-" + band[1];
		}
		
		if (core != -1) {
			core_v = core;
		}
		
		if (route != null && mod != null && band != null && core != -1) {
		
			double OSNRlin = cp.getMesh().getPhysicalLayer().computeSNRSegment(circuit, route, 0, route.getNodeList().size() - 1, mod, core, band, null, false);
			double OSNRdB = PhysicalLayer.ratioForDB(OSNRlin);
			double osnr_threshold = mod.getSNRthresholdLinear();
			double deltaOSNR = OSNRlin - osnr_threshold;
			//deltaOSNR = PhysicalLayer.ratioForDB(deltaOSNR);
			osnr_v = OSNRdB;
			deltaOsnr_v = deltaOSNR;
			
			double minDeltaOSNRofNeighbors = cp.getMinDeltaOSNRofNeighbors(circuit, route, core, cp);
			deltaOsnrNeigh_v = minDeltaOSNRofNeighbors;
			
			double XTdB = cp.getMesh().getPhysicalLayer().getCrosstalk().calculateCrosstalk(circuit, null, false);
			double XTlin = PhysicalLayer.ratioOfDB(XTdB);
			double xt_threshold = PhysicalLayer.ratioOfDB(mod.getXTthreshold());
			double deltaXT = xt_threshold - XTlin;
			//deltaXT = PhysicalLayer.ratioForDB(deltaXT);
			xt_v = XTdB;
			deltaXT_v = deltaXT;
			
			int numOverlaps = cp.getMesh().getPhysicalLayer().getCrosstalk().numberSlotsOverlapping(circuit, route, mod, core, band);
			overlaps_v = numOverlaps;
			
			double minDeltaXTofAdjacents = cp.getMesh().getPhysicalLayer().getCrosstalk().getMinDeltaXTofAdjacents(circuit);
			deltaXTAdj_v = minDeltaXTofAdjacents;
			
			double launchPower = cp.getMesh().getPhysicalLayer().getCircuitLaunchPower(circuit, mod);
			launchPower_v = launchPower;
			
			double utilizacao = 0.0;
			for (Link link : route.getLinkList()) {
				utilizacao += link.getCore(core).getUtilization();
			}
			utilizacao = utilizacao / route.getLinkList().size();
			ut_v = utilizacao;
	
	
			List<int[]> composition = IntersectionFreeSpectrum.merge(route, circuit.getGuardBand(), core);
			ComputesFragmentation cfrag = new ComputesFragmentation();
			double fragmentacao = cfrag.relativeFragmentation(composition, band[1] - band[0] + 1);
			frag_v = fragmentacao;
		}
		
		// Creating a new LineData
		LineData newLineData = new LineData();
		
		// Using setters to populate the attributes
		newLineData.setRequest(req_v);
		newLineData.setAlgorithm(algo);
		newLineData.setBiteRate(bitRate_v);
		newLineData.setRoute(route_v);
		newLineData.setHops(hops_v);
		newLineData.setDistance(distance_v);
		newLineData.setMod(mod_v);
		newLineData.setBand(band_v);
		newLineData.setCore(core_v);
		newLineData.setOsnr(osnr_v);
		newLineData.setDeltaOsnr(deltaOsnr_v);
		newLineData.setDeltaOsnrNeigh(deltaOsnrNeigh_v);
		newLineData.setXt(xt_v);
		newLineData.setDeltaXT(deltaXT_v);
		newLineData.setDeltaXTAdj(deltaXTAdj_v);
		newLineData.setOverlaps(overlaps_v);
		newLineData.setLaunchPower(launchPower_v);
		newLineData.setUt(ut_v);
		newLineData.setFrag(frag_v);
		
		return newLineData;
	}
	
	private String getRouteString(Route route) {
		Vector<Node> nodeList = route.getNodeList();
    	StringBuilder res = new StringBuilder();
        for (int i = 0; i < nodeList.size(); i++) {
            res.append(nodeList.get(i).getName());
            if (i < nodeList.size() -1) {
            	res.append("-");
            }
        }
        return res.toString();
    }
    
    /**
     * Returns the linedatalist value.
     * @return the result of the operation.
     */
    public List<LineData> getLineDataList() {
    	return lineDataList;
    }
    
    /**
     * Returns the differentquantity value.
     * @return the result of the operation.
     */
    public int getDifferentQuantity() {
    	return differentQuantity;
    }
    
    /**
     * Sets the differentquantity value.
     * @param differentQuantity the differentQuantity.
     */
    public void setDifferentQuantity(int differentQuantity) {
    	this.differentQuantity = differentQuantity;
    }    
    
}
