package gprmcsa.integrated;

import java.util.List;
import java.util.Map;
import java.util.Random;

import gprmcsa.coreSpectrumAssignment.CoreAndSpectrumAssignmentAlgorithmInterface;
import gprmcsa.modulation.Modulation;
import gprmcsa.routing.KRoutingAlgorithmInterface;
import gprmcsa.routing.Route;
import network.Circuit;
import network.ControlPlane;
import network.Crosstalk;
import network.Link;
import network.PhysicalLayer;
import util.ComputesFragmentation;
import util.IntersectionFreeSpectrum;

/**
 * Represents the FXTDM_v2 component.
 */
public class FXTDM_v2 implements IntegratedRMLSAAlgorithmInterface {
	
	private int k = 3; //This algorithm uses 3 alternative paths
	private KRoutingAlgorithmInterface kShortestsPaths; //For routing algorithms with more than one route
    //private ModulationSelectionAlgorithmInterface modulationSelection;
    private CoreAndSpectrumAssignmentAlgorithmInterface coreAndSpectrumAssignment;
    
    //Inserir os valuees dos alfas pelo file de configuracao 'others'
    private double alfa1 = 0.2; //Utilization
    private double alfa2 = 0.7; //XT
    private double alfa3 = 0.1; //Framentacao
    
    private Double sigma = 0.0; //Carrega o value pelo file de configuracao 'others'
    
    private double maxXT = Crosstalk.lowestXT;
    
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
        	
        	if(uv.get("alfa1") != null) {
				alfa1 = Double.parseDouble((String)uv.get("alfa1"));
        	}
        	
        	if(uv.get("alfa2") != null) {
        		alfa2 = Double.parseDouble((String)uv.get("alfa2"));
        	}
        	
        	if(uv.get("alfa3") != null) {
        		alfa3 = Double.parseDouble((String)uv.get("alfa3"));
        	}
        	
        	if(uv.get("sigma") != null){ // read the sigma value
    			sigma = Double.parseDouble((String)uv.get("sigma"));
    		}
        }
        //if (modulationSelection == null){
        //	modulationSelection = cp.getModulationSelection();
        //}
        if(coreAndSpectrumAssignment == null){
        	coreAndSpectrumAssignment = cp.getCoreAndSpectrumAssignment();
		}
		
		List<Route> candidateRoutes = kShortestsPaths.getRoutes(circuit.getSource(), circuit.getDestination());
        List<Modulation> avaliableModulations = cp.getMesh().getAvaliableModulations();
        
        // Route, modulation, core and band chosen
        Route chosenRoute = null;
        Modulation chosenMod = null;
        int chosenCore = -1;
        int chosenBand[] = null;
        
        Route chosenRoute2 = null;
        Modulation chosenMod2 = null;
        int chosenCore2 = -1;
        int chosenBand2[] = null;
        
        // To avoid metrics errorr
  		Route checkRoute = null;
  		Modulation checkMod = null;
  		int checkCore = -1;
  		int checkBand[] = null;
  		
  		Route checkRoute2 = null;
  		Modulation checkMod2 = null;
  		int checkCore2 = -1;
  		int checkBand2[] = null;
  		
		boolean QoT = false;
		boolean XT = false;
		boolean QoTO = false;
		boolean XTO = false;
  		
  		double minRouteCost = Double.MAX_VALUE;
        
		// Percorrer routes candidate
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
					checkRoute = route;
					checkMod = mod;
					checkCore = core;
					checkBand = band;
					
					// Check the physical layer
					QoT = cp.isAdmissibleOSNR(circuit);
					XT = cp.isAdmissibleCrosstalk(circuit);
					
					if (QoT && XT) { // QoT and XT are acceptable
						checkRoute2 = route;
						checkMod2 = mod;
						checkCore2 = core;
						checkBand2 = band;
						
						// Checks the QoT and XT for others circuits
						QoTO = cp.isAdmissibleOSNRInOther(circuit);
						XTO = cp.isAdmissibleCrosstalkInOther(circuit);
						
						if(circuit.getXt() > maxXT) {
							maxXT = circuit.getXt();
						}
						
						if (QoTO && XTO) { // QoTO and XTO are acceptable
							
							double cost = calculateCost(circuit, route, mod, core, band, cp);
							if (cost < minRouteCost) { // Select the route with the lowest cost
								minRouteCost = cost;
								
								chosenRoute2 = route;
								chosenMod2 = mod;
								chosenCore2 = core;
								chosenBand2 = band;
								
								double deltaSNR = circuit.getSNR() - mod.getSNRthreshold();
								if(deltaSNR >= sigma){ //Respeita o value de sigma
									chosenRoute = route;
									chosenMod = mod;
									chosenCore = core;
									chosenBand = band;
								}
							}
						}
					}
				} //spectrum
			} //modulation
		} //route
  		
  		if(chosenRoute == null && chosenRoute2 != null) {
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
				checkCore = new Random().nextInt(cp.getMesh().getLinkList().get(0).getNumberOfCores());
			}
            circuit.setRoute(checkRoute);
            circuit.setModulation(checkMod);
            circuit.setIndexCore(checkCore);
            circuit.setSpectrumAssigned(checkBand);
            
            return false;
        }
	}
	
	/**
	 * Returns the cost of the solution
	 * 
	 * @param circuit Circuit
	 * @param route Route
	 * @param mod Modulation
	 * @param core int
	 * @param sa int[]
	 * @return double
	 */
	private double calculateCost(Circuit circuit, Route route, Modulation mod, int core, int sa[], ControlPlane cp) {
		double cost = 0.0;
		
		double crosstalk = 0.0;
		double utilizacao = 0.0;
		double fragmentacao = 0.0;
		
		//XT
		crosstalk = cp.getMesh().getPhysicalLayer().getCrosstalk().calculateCrosstalk(circuit, null, false);
		double xtNormalizado = PhysicalLayer.ratioOfDB(crosstalk) / PhysicalLayer.ratioOfDB(maxXT);
		
		//Utilization
		for (Link link : route.getLinkList()) {
			utilizacao += link.getCore(core).getUtilization();
		}
		utilizacao = utilizacao / route.getLinkList().size();
		
		//Fragmentacao
		List<int[]> composition = IntersectionFreeSpectrum.merge(circuit.getRoute(), circuit.getGuardBand(), core);
		ComputesFragmentation cfrag = new ComputesFragmentation();
		fragmentacao = cfrag.relativeFragmentation(composition, circuit.getSpectrumAssigned()[1] - circuit.getSpectrumAssigned()[0] + 1);

		//Calculando o custo
		cost = (alfa1 * utilizacao) + (alfa2 * xtNormalizado) + (alfa3 * fragmentacao);
		
		return cost;
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
