package gprmcsa.coreSpectrumAssignment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Vector;

import gprmcsa.routing.Route;
import network.Circuit;
import network.ControlPlane;
import network.Core;
import network.Crosstalk;
import network.Link;
import network.Mesh;
import network.PhysicalLayer;
import network.Spectrum;
import util.IntersectionFreeSpectrum;

// Algoritmo proposto por Jurandir
// SBRC 2021 evolution
// Esta versao diminui o teste de faixas de espectro free.

/**
 * Represents the NewAcine_v2 component.
 */
public class NewAcine_v2 implements CoreAndSpectrumAssignmentAlgorithmInterface {
	public static final int BREAKPOINT1 = 107;
	public static final int BREAKPOINT2 = 214;
	
	public static final int WEIGHT1 = 6;
	public static final int WEIGHT2 = 3;
	public static final int WEIGHT3 = 1;
	public static final int WEIGHT4 = 2;
	
	public static final int WEIGHTABS = 1;
	public static final int WEIGHTWEI = 1;
	public static final int WEIGHTROUT = 8;
	
	private int coreOfTheTime;
	private double xtCandidate;
	private double xtBestCandidate;
	private Circuit circuitCandidate;
	private Circuit circuitBestCandidate;
		
	/**
	 * Creates a new NewAcine_v2 instance.
	 */
	public NewAcine_v2() {
		//
	}
	
	@Override
	public boolean assignCoreAndSpectrum(int numberOfSlots, Circuit circuit, ControlPlane cp) {
		//spectrumUtilizationAbsolute2 (circuit, cp); //Para machine learning
		//spectrumUtilizationWeighted(circuit, cp); //Para machine learning
		//calculeAverageXt(circuit, cp); //for machine learning
		
		this.circuitCandidate = new Circuit();
		this.circuitBestCandidate = new Circuit();
		this.xtCandidate = 0.0;
		this.xtBestCandidate = 0.0;
		this.coreOfTheTime = cp.getMesh().getLinkList().get(0).getNumberOfCores() -1;
		
		circuit.setSpectrumAssigned(policy(numberOfSlots, null, circuit, cp));
        
        if (circuit.getSpectrumAssigned() == null) {
 	       return false;
 		}
        
        circuit.setIndexCore(circuitBestCandidate.getIndexCore());

        return true;
	}
	
	@Override
	public int coreAssignment() {
		int temp = this.coreOfTheTime;
		this.coreOfTheTime--;
		return temp;
	}

	@Override
	public int[] policy(int numberOfSlots, List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp) {
		Random rand = new Random();
		this.circuitCandidate = new Circuit();
		this.circuitBestCandidate = new Circuit();
		
		while (coreOfTheTime >= 0) {
			
			//Core impar
			if(coreOfTheTime%2 == 1) {
				List<int[]> composition = IntersectionFreeSpectrum.merge(circuit.getRoute(), circuit.getGuardBand(), coreOfTheTime);
				
				int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
		        if(numberOfSlots> maxAmplitude) return null;
				
				for (int[] band : composition) {
					if (band[1] - band[0] + 1 >= numberOfSlots) {
						
						for (int f = 0; f < 2; f++) { //Chooses whether to allocate at the beginning or at the end of the band
			                int chosen[] = band.clone();
			                
			                if(f == 0){ // lowest slot index of the band
			                	chosen[1] = band[0] + numberOfSlots - 1;
								
							}else{ // highest slot index of the band
								chosen[0] = band[1] - numberOfSlots + 1;
							}
			                
			                //circuitCandidate = circuit;
			                circuitCandidate.setPair(circuit.getPair());
			                circuitCandidate.setRequests(circuit.getRequests());
			                circuitCandidate.setRoute(circuit.getRoute());
			        		circuitCandidate.setModulation(circuit.getModulation());
			        		circuitCandidate.setGuardBand(circuit.getGuardBand());

			                circuitCandidate.setSpectrumAssigned(chosen);
			                circuitCandidate.setIndexCore(coreOfTheTime);
			                circuitCandidate.setXt(cp.getMesh().getPhysicalLayer().getCrosstalk().calculateCrosstalk(circuitCandidate, null, false));
			                
			                circuitCandidate.setQoT(true);
			                circuitCandidate.setQoTForOther(true);
			                circuitCandidate.setSNR(PhysicalLayer.maxOSNR);
			                
			                if(isAdmissibleQualityOfTransmission(circuitCandidate, cp.getMesh())){ //Check QOTN QOTO
			                
				                if (circuitCandidate.getXt() != Crosstalk.lowestXT) {
				                	//if(circuitCandidate.getXtAdmissible()) {
				                	//if(circuitCandidate.getXt() <= crosstalk.xtThreshold(circuitCandidate.getModulation())) {//XTN
				                		if(cp.getMesh().getPhysicalLayer().getCrosstalk().isAdmissibleInOthers(circuitCandidate)) { //XTO
				                			
				                			//deltaXtCandidate = cp.getMesh().getCrosstalk().xtThreshold(circuitCandidate.getModulation()) - circuitCandidate.getXt();
			                				xtCandidate = circuitCandidate.getXt(); 	
			                				if(xtCandidate < xtBestCandidate) {
		                						circuitBestCandidate.setSpectrumAssigned(circuitCandidate.getSpectrumAssigned());
		                						circuitBestCandidate.setIndexCore(circuitCandidate.getIndexCore());
		                						xtBestCandidate = xtCandidate;		                				
			                				}
				                		}
				                	//}
				                }else {
				                	circuitBestCandidate.setSpectrumAssigned(circuitCandidate.getSpectrumAssigned());
				                	circuitBestCandidate.setIndexCore(coreOfTheTime);
				                	//return chosen;		
				                	return circuitBestCandidate.getSpectrumAssigned();
				                }	
			                }
			            }
					}	            
		        }
			}
			
			//Core par
			if(coreOfTheTime>0 && coreOfTheTime%2 == 0) {
				List<int[]> composition = IntersectionFreeSpectrum.merge(circuit.getRoute(), circuit.getGuardBand(), coreOfTheTime);
				
				int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
		        if(numberOfSlots> maxAmplitude) return null;
				
				for (int[] band : composition) {
					if (band[1] - band[0] + 1 >= numberOfSlots) {
						
						for (int f = 0; f < 2; f++) { //Chooses whether to allocate at the beginning or at the end of the band
			                int chosen[] = band.clone();
			                
			                if(f == 0){ // lowest slot index of the band
			                	chosen[1] = band[0] + numberOfSlots - 1;
								
							}else{ // highest slot index of the band
								chosen[0] = band[1] - numberOfSlots + 1;
							}
			                
			                //circuitCandidate = circuit;		
			                circuitCandidate.setPair(circuit.getPair());
			                circuitCandidate.setRequests(circuit.getRequests());
			                circuitCandidate.setRoute(circuit.getRoute());
			        		circuitCandidate.setModulation(circuit.getModulation());
			        		circuitCandidate.setGuardBand(circuit.getGuardBand());
			                
			                circuitCandidate.setSpectrumAssigned(chosen);
			                circuitCandidate.setIndexCore(coreOfTheTime);
			                circuitCandidate.setXt(cp.getMesh().getPhysicalLayer().getCrosstalk().calculateCrosstalk(circuitCandidate, null, false));
			                
			                circuitCandidate.setQoT(true);
			                circuitCandidate.setQoTForOther(true);
			                circuitCandidate.setSNR(PhysicalLayer.maxOSNR);
			                
			                if(isAdmissibleQualityOfTransmission(circuitCandidate, cp.getMesh())){ //Check QOTN QOTO
			                
				                if (circuitCandidate.getXt() != Crosstalk.lowestXT) {
				                	//if(circuitCandidate.getXtAdmissible()) {
				                	//if(circuitCandidate.getXt() <= crosstalk.xtThreshold(circuitCandidate.getModulation())) {//XTN
				                		if(cp.getMesh().getPhysicalLayer().getCrosstalk().isAdmissibleInOthers(circuitCandidate)) { //XTO
				                			
				                			//deltaXtCandidate = cp.getMesh().getCrosstalk().xtThreshold(circuitCandidate.getModulation()) - circuitCandidate.getXt();
				                			xtCandidate = circuitCandidate.getXt();	
			                				if(xtCandidate <= xtBestCandidate) {
			                					circuitBestCandidate.setSpectrumAssigned(circuitCandidate.getSpectrumAssigned());
			                					circuitBestCandidate.setIndexCore(circuitCandidate.getIndexCore());
			                					xtBestCandidate = xtCandidate;		                				
			                				}
				                				
				                		}
				                	//}
				                }else {
				                	//System.out.println("\noi");
				                	circuitBestCandidate.setSpectrumAssigned(circuitCandidate.getSpectrumAssigned());
				                	circuitBestCandidate.setIndexCore(coreOfTheTime);
				                	//return chosen;
				                	return circuitBestCandidate.getSpectrumAssigned();	                	 
				                }
			                
			                }
			            }
					}	            
		        }
			}
			
			//Central Core
			if (coreOfTheTime == 0 && circuitBestCandidate.getSpectrumAssigned()==null) {
				List<int[]> composition = IntersectionFreeSpectrum.merge(circuit.getRoute(), circuit.getGuardBand(), coreOfTheTime);
				
				int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
		        if(numberOfSlots> maxAmplitude) return null;
				
				for (int[] band : composition) {
					if (band[1] - band[0] + 1 >= numberOfSlots) {
						
						for (int f = 0; f < 2; f++) { //Chooses whether to allocate at the beginning or at the end of the band
			                int chosen[] = band.clone();
			                
			                if(f == 0){ // lowest slot index of the band
			                	chosen[1] = band[0] + numberOfSlots - 1;
								
							}else{ // highest slot index of the band
								chosen[0] = band[1] - numberOfSlots + 1;
							}
			                
			                //circuitCandidate = circuit;	
			                circuitCandidate.setPair(circuit.getPair());
			                circuitCandidate.setRequests(circuit.getRequests());
			                circuitCandidate.setRoute(circuit.getRoute());
			        		circuitCandidate.setModulation(circuit.getModulation());
			        		circuitCandidate.setGuardBand(circuit.getGuardBand());
			                
			                circuitCandidate.setSpectrumAssigned(chosen);
			                circuitCandidate.setIndexCore(coreOfTheTime);
			                circuitCandidate.setXt(cp.getMesh().getPhysicalLayer().getCrosstalk().calculateCrosstalk(circuitCandidate, null, false));
			                
			                circuitCandidate.setQoT(true);
			                circuitCandidate.setQoTForOther(true);
			                circuitCandidate.setSNR(PhysicalLayer.maxOSNR);
			                
			                if(isAdmissibleQualityOfTransmission(circuitCandidate, cp.getMesh())){ //Check QOTN QOTO
			                
				                if (circuitCandidate.getXt() != Crosstalk.lowestXT) {
				                	//if(circuitCandidate.getXtAdmissible()) {
				                	//if(circuitCandidate.getXt() <= crosstalk.xtThreshold(circuitCandidate.getModulation())) { //XTN
				                		if(cp.getMesh().getPhysicalLayer().getCrosstalk().isAdmissibleInOthers(circuitCandidate)) { //XTO
				                			
			                				xtCandidate = circuitCandidate.getXt();	
			                				if((xtCandidate <= xtBestCandidate) && (rand.nextBoolean())) {
			                					circuitBestCandidate.setSpectrumAssigned(circuitCandidate.getSpectrumAssigned());
			                					circuitBestCandidate.setIndexCore(circuitCandidate.getIndexCore());
			                					xtBestCandidate = xtCandidate;		                				
			                				}
				                		}
				                	//}
				                }else {
				                	circuitBestCandidate.setSpectrumAssigned(circuitCandidate.getSpectrumAssigned());
				                	circuitBestCandidate.setIndexCore(coreOfTheTime);
				                	return circuitBestCandidate.getSpectrumAssigned();
				                	//return chosen;		                	 
				                }
				                
			                }
			            }
					}	            
		        }	
			}
			
			coreAssignment();
		}	
		
		return circuitBestCandidate.getSpectrumAssigned();
	}
	
	//
	// Para Machine Learning
	//
/*	private void spectrumUtilizationAbsolute2 (Circuit circuit, ControlPlane cp) {
		Vector<Link> listaDeLinks;
		//ArrayList<Integer> coresAdjacentes;
		circuit.refreshSpectrumUtilizationAbsolutUnit(0, 0);
		circuit.refreshSpectrumUtilizationAbsolutUnit(1, 0);
		circuit.refreshSpectrumUtilizationAbsolutUnit(2, 0);
		circuit.refreshSpectrumUtilizationAbsolutUnit(3, 0);
		circuit.refreshSpectrumUtilizationAbsolutUnit(4, 0);
		circuit.refreshSpectrumUtilizationAbsolutUnit(5, 0);
		circuit.refreshSpectrumUtilizationAbsolutUnit(6, 0);
		
		
		listaDeLinks = circuit.getRoute().getLinkList();
		
		//for (int a = 0; a < this.numberOfslots; a++) {
		//	circuit.refreshSpectrumUtilizationAbsolutUnit(a, 0);;
		//}
		for(Link link : listaDeLinks) {
			for (int i = 0; i< 7 ; i++) {
				int quantslots = 0;
				quantslots = cp.getMesh().getLink(link.getSource().getName(), link.getDestination().getName()).getCore(i).getSpectrum().getUsedslots();
				circuit.refreshSpectrumUtilizationAbsolutUnit(i, circuit.getSpectrumUtilizationAbsolut()[i] + quantslots);
			}
		}
		
	} */
	
	//
	// Para Machine Learning
	//
/*	private void calculeAverageXt (Circuit circuit, ControlPlane cp) {
		Vector<Link> listaDeLinks;
		//ArrayList<Integer> coresAdjacentes;
		circuit.refreshAverageXt(0, 0);
		circuit.refreshAverageXt(1, 0);
		circuit.refreshAverageXt(2, 0);
		circuit.refreshAverageXt(3, 0);
		circuit.refreshAverageXt(4, 0);
		circuit.refreshAverageXt(5, 0);
		circuit.refreshAverageXt(6, 0);
		
		
		listaDeLinks = circuit.getRoute().getLinkList();
		
		
		for (int i = 0; i<7; i++) {
			double sumXT = 0.0;
			int cont = 0;
			double averageXT = 0.0;
			
			for(Link link : listaDeLinks) {	
				for(Circuit circuitAlocated : link.getCore(i).getCircuitList()) {
					//System.out.println(circuitAlocated.getXt());
					sumXT = sumXT + circuitAlocated.getXt();
					cont++;
				}
			}
			
			averageXT =  sumXT/cont;
			if(cont==0) {
				averageXT = sumXT;
			}
			
			circuit.refreshAverageXt(i,averageXT);
		}
		
	} */
	
	private void spectrumUtilizationWeighted (Circuit circuit, ControlPlane cp) {
		Vector<Link> listaDeLinks;
		ArrayList<Core> coresAdjacentes;
		
		listaDeLinks = circuit.getRoute().getLinkList();
		
		for (int i = 0; i<7; i++) {
			
			coresAdjacentes = circuit.getRoute().getLink(0).getAdjacentCores(i);
			
			for (Link link : listaDeLinks) {
				for (Core coreadj : link.getCores()) {
					if (coresAdjacentes.contains(coreadj.getId())) {
						//circuit.refreshSpectrumUtilizationAbsolutUnit(i, (circuit.getSpectrumUtilizationAbsolut()[i] + 1));
						//circuit.refreshSpectrumUtilizationAbsolutUnit(i, circuit.getSpectrumUtilizationAbsolut()[i] + coreadj.getUsedslots());
						//this.spectrumUtilizationAbsolut[i] = this.spectrumUtilizationAbsolut[i] + coreadj.getUsedslots();
						//toApplyWeight(i, sectionSpectrum(spectrum, cp))
						
						circuit.refreshSpectrumUtilizationWeightedUnit(i, circuit.getSpectrumUtilizationWeighted()[i] + toApplyWeight(i, sectionSpectrum(link.getCore(coreadj.getId()).getSpectrum(), cp)));
					}
				}				
			}
		}
	}
	
	private int[] sectionSpectrum (Spectrum spectrum, ControlPlane cp) {
		//int contSpectrum[] = new int[cp.getMesh().getLinkList().get(0).getCore(0).getNumOfslots()];
		int contSpectrum[] = new int[3];
		List<int[]> freeSpectrum = spectrum.getFreeSpectrumBands();
		boolean usado;
		
		for (int i=1; i<BREAKPOINT1; i++) {
			usado = true;
			
			for (int[] band : freeSpectrum) {
				if (i>=band[0] && i<=band[1]) {			
					usado = false;
					break;
				}				
			}
			
			if(usado) {
				contSpectrum[0]++;
			}
		}
		
		for (int i=BREAKPOINT1; i<BREAKPOINT2; i++) {
			usado = true;
			
			for (int[] band : freeSpectrum) {
				if (i>=band[0] && i<=band[1]) {			
					usado = false;
					break;
				}			
			}
			
			if(usado) {
				contSpectrum[1]++;
			}
		}
		
		for (int i=BREAKPOINT2; i<cp.getMesh().getLinkList().get(0).getCore(0).getNumOfSlots(); i++) {
			usado = true;
			
			for (int[] band : freeSpectrum) {
				if (i>=band[0] && i<=band[1]) {			
					usado = false;
					break;
				}				
			}
			
			if(usado) {
				contSpectrum[2]++;
			}
		}
		
		return contSpectrum;
	}
	
	private int toApplyWeight(int chosenCore, int[] vetor) {
		int totalWeight = 0;
		
		if(chosenCore == 0) {
			totalWeight = (vetor[0]*WEIGHT4) + (vetor[1]*WEIGHT1) + (vetor[2]*WEIGHT4);
    	}else if(chosenCore%2 == 0) {
    		totalWeight = (vetor[0]*WEIGHT3) + (vetor[1]*WEIGHT2) + (vetor[2]*WEIGHT1);
    	}else if(chosenCore%2 == 1) {
    		totalWeight = (vetor[0]*WEIGHT1) + (vetor[1]*WEIGHT2) + (vetor[2]*WEIGHT3);
    	}
		
		return totalWeight;
	}
	
	/**
	 * Returns whether admissiblequalityoftransmission is enabled.
	 * @param circuit the circuit.
	 * @param mesh the mesh.
	 * @return the result of the operation.
	 */
	protected boolean isAdmissibleQualityOfTransmission(Circuit circuit, Mesh mesh) {
    	
    	// Check if it is to test the QoT
    	//if(mesh.getPhysicalLayer().isActiveQoT()){
    		
    		// Verifies the QoT of the current circuit
    		if(computeQualityOfTransmissionsigma(circuit, null, false, mesh)){
    			boolean QoTForOther = true;
    			
    			// Check if it is to test the QoT of other already active circuits
    			//if(mesh.getPhysicalLayer().isActiveQoTForOther()){
    				
    				// Calculates the QoT of the other circuits
    			QoTForOther = computeQoTForOther(circuit, mesh);
    			circuit.setQoTForOther(QoTForOther);
    			//}
    			
    			return QoTForOther;
    		}
    		
    		return false; // Circuit can not be established
    	//}
    	
		// If it does not check the QoT then it returns acceptable
		//return true;
    }
		
	
    /**
     * Executes the computeQualityOfTransmissionsigma operation.
     * @param circuit the circuit.
     * @param testCircuit the testCircuit.
     * @param addTestCircuit the addTestCircuit.
     * @param mesh the mesh.
     * @return the result of the operation.
     */
    public boolean computeQualityOfTransmissionsigma(Circuit circuit, Circuit testCircuit, boolean addTestCircuit, Mesh mesh){
    	double sigma = 1.0;
    	double SNR = mesh.getPhysicalLayer().computeSNRSegment(circuit, circuit.getRoute(), 0, circuit.getRoute().getNodeList().size() - 1, circuit.getModulation(), circuit.getIndexCore(), circuit.getSpectrumAssigned(), testCircuit, addTestCircuit);
		double SNRdB = PhysicalLayer.ratioForDB(SNR)-sigma;
		
		circuit.setSNR(SNRdB);

		boolean QoT = mesh.getPhysicalLayer().isAdmissible(circuit.getModulation(), SNR);
		circuit.setQoT(QoT);
		
		//System.out.println("--------\n The OSNR is: "+SNRdB+"\n\n----------");
		
		return QoT;
    }
    
    /**
     * Executes the computeQualityOfTransmission operation.
     * @param circuit the circuit.
     * @param testCircuit the testCircuit.
     * @param addTestCircuit the addTestCircuit.
     * @param mesh the mesh.
     * @return the result of the operation.
     */
    public boolean computeQualityOfTransmission(Circuit circuit, Circuit testCircuit, boolean addTestCircuit, Mesh mesh){
    	//double sigma = 1.0;
    	double SNR = mesh.getPhysicalLayer().computeSNRSegment(circuit, circuit.getRoute(), 0, circuit.getRoute().getNodeList().size() - 1, circuit.getModulation(), circuit.getIndexCore(), circuit.getSpectrumAssigned(), testCircuit, addTestCircuit);
    	boolean QoT = mesh.getPhysicalLayer().isAdmissible(circuit.getModulation(), SNR);
		
		circuit.setSNRlinear(SNR);
		circuit.setQoT(QoT);
		
		//System.out.println("--------\n The OSNR is: "+SNRdB+"\n\n----------");
		
		return QoT;
    }
    
    /**
     * Executes the computeQoTForOther operation.
     * @param circuit the circuit.
     * @param mesh the mesh.
     * @return the result of the operation.
     */
    public boolean computeQoTForOther(Circuit circuit, Mesh mesh){
    	HashSet<Circuit> circuits = new HashSet<Circuit>(); // Circuit list for test
    	HashMap<Circuit, Double> circuitsSNR = new HashMap<Circuit, Double>(); // To guard the SNR of the test list circuits
    	HashMap<Circuit, Boolean> circuitsQoT = new HashMap<Circuit, Boolean>(); // To guard the QoT of the test list circuits
		
    	// Search for all circuits that have links in common with the circuit under evaluation
		Route route = circuit.getRoute();
		for (Link link : route.getLinkList()) {
			
			// Picks up the active circuits that use the link
			HashSet<Circuit> circuitsTemp = link.getCore(circuit.getIndexCore()).getCircuitList();
            for (Circuit circuitTemp : circuitsTemp) {
            	
            	// If the circuit is different from the circuit under evaluation and is not in the circuit list for test
                if (!circuit.equals(circuitTemp) && !circuits.contains(circuitTemp)) {
                    circuits.add(circuitTemp);
                }
            }
		}
		
		// Tests the QoT of circuits
        for (Circuit circuitTemp : circuits) {
        	
        	// Stores the SNR and QoT values
        	circuitsSNR.put(circuitTemp, circuitTemp.getSNR());
            circuitsQoT.put(circuitTemp, circuitTemp.isQoT());
            
        	// Recalculates the QoT and SNR of the circuit
            //boolean QoT = computeQualityOfTransmission(circuitTemp, null, true, mesh);
            boolean QoT = computeQualityOfTransmission(circuitTemp, circuit, true, mesh);
            
            // Returns the SNR and QoT values of circuits before the establishment of the circuit in evaluation
        	//for (Circuit circuitAux : circuitsSNR.keySet()) {
        		//circuitAux.setSNR(circuitsSNR.get(circuitAux));
        		//circuitAux.setQoT(circuitsQoT.get(circuitAux));
        	//}
            
            if (!QoT) {
            	
            	// Returns the SNR and QoT values of circuits before the establishment of the circuit in evaluation
            	for (Circuit circuitAux : circuitsSNR.keySet()) {
            		circuitAux.setSNR(circuitsSNR.get(circuitAux));
            		circuitAux.setQoT(circuitsQoT.get(circuitAux));
            	}
            	
                return false;
            }
        }
        
        // Returns the SNR and QoT values of circuits before the establishment of the circuit in evaluation
    	for (Circuit circuitAux : circuitsSNR.keySet()) {
    		circuitAux.setSNR(circuitsSNR.get(circuitAux));
    		circuitAux.setQoT(circuitsQoT.get(circuitAux));
    	}
        
		return true;
    }
	
//	private boolean xtNeighborIsAdmissible(Circuit circuitCandidate) {
//		ArrayList<Core> adjacentsCores = circuitCandidate.getRoute().getLink(0).coresAdjacents(circuitCandidate.getIndexCore());
//		
//		for(Core neighbor : adjacentsCores) {
//			Circuit circuitJ = new Circuit();
//			circuitJ.setIndexCore(neighbor.getId());
//			circuitJ.setRoute(circuitCandidate.getRoute());
//			circuitJ.sets
//					
//		}
//		
//		return true;
//	}

}
