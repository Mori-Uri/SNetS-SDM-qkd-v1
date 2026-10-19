package gprmcsa.coreSpectrumAssignment;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Vector;

import network.Circuit;
import network.ControlPlane;
import network.Core;
import network.Link;
import network.Spectrum;
import util.IntersectionFreeSpectrum;

// Algoritmo proposto por Jurandir
// SBRC 2021

/**
 * Represents the Acine component.
 */
public class Acine implements CoreAndSpectrumAssignmentAlgorithmInterface {
	public static final int BREAKPOINT1 = 107;
	public static final int BREAKPOINT2 = 214;
	
	public static final int WEIGHT1 = 6;
	public static final int WEIGHT2 = 3;
	public static final int WEIGHT3 = 1;
	public static final int WEIGHT4 = 2;
	
	public static final int WEIGHTABS = 1;
	public static final int WEIGHTWEI = 1;
	public static final int WEIGHTROUT = 8;
	
	private int numberOfCores;
	private int coreOfTheTime;
	private int centralCoreId; // ID do core Central
	private double xtCandidate;
	private double xtBestCandidate;
	private Circuit circuitCandidate;
	private Circuit circuitBestCandidate;
		
	/**
	 * Creates a new Acine instance.
	 */
	public Acine() {
		this.numberOfCores = -1;
	}
	
	@Override
	public boolean assignCoreAndSpectrum(int numberOfSlots, Circuit circuit, ControlPlane cp) {
		if (numberOfCores == -1) {
			numberOfCores = cp.getMesh().getLinkList().get(0).getNumberOfCores();
			
			if (numberOfCores == 19) { // for 19 cores
				centralCoreId = 18;
			} else { // for 7 e 12 cores
				centralCoreId = 0;
			}
		}
		
		spectrumUtilizationAbsolute2(circuit, cp); //Para machine learning
		//spectrumUtilizationWeighted(circuit, cp); //Para machine learning
		//calculeAverageXt(circuit, cp); //for machine learning
		
		this.circuitCandidate = new Circuit();
		this.circuitBestCandidate = new Circuit();
		this.xtCandidate = 0.0;
		this.xtBestCandidate = 0.0; 
		this.coreOfTheTime = numberOfCores -1;
					
		circuit.setSpectrumAssigned(policy(numberOfSlots, null, circuit, cp));
        circuit.setIndexCore(circuitBestCandidate.getIndexCore());
        
        if (circuit.getSpectrumAssigned() == null) {
 	       return false;
 		}
        
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
		
		while (coreOfTheTime >= 0) {
			
			//Core impar
			if(coreOfTheTime != centralCoreId && coreOfTheTime%2 == 1) {
				List<int[]> composition = IntersectionFreeSpectrum.merge(circuit.getRoute(), circuit.getGuardBand(), coreOfTheTime);
				
				int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
		        if(numberOfSlots> maxAmplitude) return null;
				
				for (int[] band : composition) {
			    	
					for (int i = band[0]; i <= band[1]; i++) {
						int chosen[] = null;
			            if (band[1] - i + 1 >= numberOfSlots) {
			                chosen = band.clone();
			                chosen[0] = i;
			                chosen[1] = chosen[0] + numberOfSlots - 1;//It is not necessary to allocate the entire band, just the amount of slots required
			                
			                //circuitCandidate = circuit;
			                
			                circuitCandidate.setPair(circuit.getPair());
			                circuitCandidate.setRequests(circuit.getRequests());
			                circuitCandidate.setRoute(circuit.getRoute());
			        		circuitCandidate.setModulation(circuit.getModulation());
			        		circuitCandidate.setGuardBand(circuit.getGuardBand());
			        		
			                circuitCandidate.setSpectrumAssigned(chosen);
			                circuitCandidate.setIndexCore(coreOfTheTime);
			                circuitCandidate.setXt(cp.getMesh().getPhysicalLayer().getCrosstalk().calculateCrosstalk(circuitCandidate, null, false));
			                
			                if (circuitCandidate.getXt() != 0) {
			                	if(circuitCandidate.getXtAdmissible()) {
			                		//if(crosstalk.isAdmissibleInOthers(circuitCandidate)) {
			                			//deltaXtCandidate = cp.getMesh().getCrosstalk().xtThreshold(circuitCandidate.getModulation()) - circuitCandidate.getXt();
			                		xtCandidate = circuitCandidate.getXt(); 	
			                		if(xtCandidate < xtBestCandidate) {
		                				circuitBestCandidate.setSpectrumAssigned(circuitCandidate.getSpectrumAssigned());
		                				circuitBestCandidate.setIndexCore(coreOfTheTime);
		                				xtBestCandidate = xtCandidate;		                				
			                		}
			                		//}
			                	}
			                }else {
			                	circuitBestCandidate.setSpectrumAssigned(circuitCandidate.getSpectrumAssigned());
			                	circuitBestCandidate.setIndexCore(coreOfTheTime);
			                	return chosen;		                	 
			                }	           		                
			            }
					}	            
		        }
			}
			
			//Core par
			if(coreOfTheTime != centralCoreId && coreOfTheTime%2 == 0) {
				List<int[]> composition = IntersectionFreeSpectrum.merge(circuit.getRoute(), circuit.getGuardBand(), coreOfTheTime);
				
				int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
		        if(numberOfSlots> maxAmplitude) return null;
				
				for (int[] band : composition) {
			    	
					for (int i = band[0]; i <= band[1]; i++) {
						int chosen[] = null;
			            if (band[1] - i + 1 >= numberOfSlots) {
			                chosen = band.clone();
			                chosen[0] = i;
			                chosen[1] = chosen[0] + numberOfSlots - 1;//It is not necessary to allocate the entire band, just the amount of slots required
			                
			                //circuitCandidate = circuit;
			                
			                circuitCandidate.setPair(circuit.getPair());
			                circuitCandidate.setRequests(circuit.getRequests());
			                circuitCandidate.setRoute(circuit.getRoute());
			        		circuitCandidate.setModulation(circuit.getModulation());
			        		circuitCandidate.setGuardBand(circuit.getGuardBand());
			        		
			                circuitCandidate.setSpectrumAssigned(chosen);
			                circuitCandidate.setIndexCore(coreOfTheTime);
			                circuitCandidate.setXt(cp.getMesh().getPhysicalLayer().getCrosstalk().calculateCrosstalk(circuitCandidate, null, false));
			                
			                if (circuitCandidate.getXt() != 0) {
			                	if(circuitCandidate.getXtAdmissible()) {
			                		//if(crosstalk.isAdmissibleInOthers(circuitCandidate)) {
			                			//deltaXtCandidate = cp.getMesh().getCrosstalk().xtThreshold(circuitCandidate.getModulation()) - circuitCandidate.getXt();
			                		xtCandidate = circuitCandidate.getXt();	
			                		if(xtCandidate <= xtBestCandidate) {
		                				circuitBestCandidate.setSpectrumAssigned(circuitCandidate.getSpectrumAssigned());
		                				circuitBestCandidate.setIndexCore(coreOfTheTime);
		                				xtBestCandidate = xtCandidate;		                				
			                		}
			                		//}
			                	}
			                }else {
			                	circuitBestCandidate.setSpectrumAssigned(circuitCandidate.getSpectrumAssigned());
			                	circuitBestCandidate.setIndexCore(coreOfTheTime);
			                	return chosen;		                	 
			                }	           		                
			            }
					}	            
		        }
			}
			
			//Central Core
			if (coreOfTheTime == centralCoreId && circuitBestCandidate.getSpectrumAssigned()==null) {
				List<int[]> composition = IntersectionFreeSpectrum.merge(circuit.getRoute(), circuit.getGuardBand(), coreOfTheTime);
				
				int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
		        if(numberOfSlots> maxAmplitude) return null;
				
				for (int[] band : composition) {
			    	
					for (int i = band[0]; i <= band[1]; i++) {
						int chosen[] = null;
			            if (band[1] - i + 1 >= numberOfSlots) {
			                chosen = band.clone();
			                chosen[0] = i;
			                chosen[1] = chosen[0] + numberOfSlots - 1;//It is not necessary to allocate the entire band, just the amount of slots required
			                
			                //circuitCandidate = circuit;
			                
			                circuitCandidate.setPair(circuit.getPair());
			                circuitCandidate.setRequests(circuit.getRequests());
			                circuitCandidate.setRoute(circuit.getRoute());
			        		circuitCandidate.setModulation(circuit.getModulation());
			        		circuitCandidate.setGuardBand(circuit.getGuardBand());
			        		
			                circuitCandidate.setSpectrumAssigned(chosen);
			                circuitCandidate.setIndexCore(coreOfTheTime);
			                circuitCandidate.setXt(cp.getMesh().getPhysicalLayer().getCrosstalk().calculateCrosstalk(circuitCandidate, null, false));
			                
			                if (circuitCandidate.getXt() != 0) {
			                	if(circuitCandidate.getXtAdmissible()) {
			                		if(cp.getMesh().getPhysicalLayer().getCrosstalk().isAdmissibleInOthers(circuitCandidate)) {
			                			xtCandidate = circuitCandidate.getXt();	
				                		if((xtCandidate <= xtBestCandidate) && (rand.nextBoolean())) {
			                				circuitBestCandidate.setSpectrumAssigned(circuitCandidate.getSpectrumAssigned());
			                				circuitBestCandidate.setIndexCore(coreOfTheTime);
			                				xtBestCandidate = xtCandidate;		                				
			                			}
			                		}
			                	}
			                }else {
			                	circuitBestCandidate.setSpectrumAssigned(circuitCandidate.getSpectrumAssigned());
			                	circuitBestCandidate.setIndexCore(coreOfTheTime);
			                	return chosen;		                	 
			                }	           		                
			            }
					}	            
		        }
			}
			
//			List<int[]> composition = IntersectionFreeSpectrum.merge(circuit.getRoute(), circuit.getGuardBand(), coreOfTheTime);
//			
//			int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
//	        if(numberOfslots> maxAmplitude) return null;
//			
//			for (int[] band : composition) {
//		    	
//				for (int i = band[0]; i <= band[1]; i++) {
//					int chosen[] = null;
//		            if (band[1] - i + 1 >= numberOfslots) {
//		                chosen = band.clone();
//		                chosen[0] = i;
//		                chosen[1] = chosen[0] + numberOfslots - 1;//It is not necessary to allocate the entire band, just the amount of slots required
//		                
//		                circuitCandidate = circuit;		                
//		                circuitCandidate.setSpectrumAssigned(chosen);
//		                circuitCandidate.setIndexCore(coreOfTheTime);
//		                circuitCandidate.setXt(crosstalk.calculaCrosstalk(circuitCandidate));
//		                
//		                if (circuitCandidate.getXt() != 0) {
//		                	if(circuitCandidate.getXtAdmissible()) {
//		                		if(crosstalk.isAdmissibleInOthers(circuitCandidate)) {
//		                			deltaXtCandidate = cp.getMesh().getCrosstalk().xtThreshold(circuitCandidate.getModulation()) - circuitCandidate.getXt();
//		                			if(deltaXtCandidate > deltaXtBestCandidate) {
//		                				circuitBestCandidate.setSpectrumAssigned(circuitCandidate.getSpectrumAssigned());
//		                				circuitBestCandidate.setIndexCore(coreOfTheTime);
//		                				deltaXtBestCandidate = deltaXtCandidate;		                				
//		                			}
//		                		}
//		                	}
//		                }else {
//		                	circuitBestCandidate.setSpectrumAssigned(circuitCandidate.getSpectrumAssigned());
//		                	circuitBestCandidate.setIndexCore(coreOfTheTime);
//		                	return chosen;		                	 
//		                }	           		                
//		            }
//				}	            
//	        }
			
			coreAssignment();
		}	
		
		return circuitBestCandidate.getSpectrumAssigned();
	}
	
	//
	// Para Machine Learning
	//
	private void spectrumUtilizationAbsolute2 (Circuit circuit, ControlPlane cp) {
		Vector<Link> listaDeLinks;
		
		circuit.initializesInfoForMachineLearning(numberOfCores);
		
		for(int i = 0; i < numberOfCores; i++) {
			circuit.refreshSpectrumUtilizationAbsolutUnit(i, 0);
		}
		
		listaDeLinks = circuit.getRoute().getLinkList();
		
		//for (int a = 0; a < this.numberOfslots; a++) {
		//	circuit.refreshSpectrumUtilizationAbsolutUnit(a, 0);;
		//}
		for(Link link : listaDeLinks) {
			for (int i = 0; i < numberOfCores; i++) {
				int quantSlots = 0;
				quantSlots = cp.getMesh().getLink(link.getSource().getName(), link.getDestination().getName()).getCore(i).getSpectrum().getUsedSlots();
				circuit.refreshSpectrumUtilizationAbsolutUnit(i, circuit.getSpectrumUtilizationAbsolut()[i] + quantSlots);
			}
		}
	}
	
	//
	// Para Machine Learning
	//
	private void calculeAverageXt (Circuit circuit, ControlPlane cp) {
		Vector<Link> listaDeLinks;
		
		circuit.initializesInfoForMachineLearning(numberOfCores);
		
		for(int i = 0; i < numberOfCores; i++) {
			circuit.refreshSpectrumUtilizationAbsolutUnit(i, 0);
		}
		
		listaDeLinks = circuit.getRoute().getLinkList();
		
		for (int i = 0;  i< numberOfCores; i++) {
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
	}
	
	private void spectrumUtilizationWeighted (Circuit circuit, ControlPlane cp) {
		Vector<Link> listaDeLinks;
		ArrayList<Core> coresAdjacentes;
		
		listaDeLinks = circuit.getRoute().getLinkList();
		
		for (int i = 0; i < numberOfCores; i++) {
			
			coresAdjacentes = circuit.getRoute().getLink(0).getAdjacentCores(i);
			
			for (Link link : listaDeLinks) {
				for (Core coreadj : link.getCores()) {
					if (coresAdjacentes.contains(coreadj)) {
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
		
		if(chosenCore == centralCoreId) {
			totalWeight = (vetor[0]*WEIGHT4) + (vetor[1]*WEIGHT1) + (vetor[2]*WEIGHT4);
    	}else if(chosenCore%2 == 0) {
    		totalWeight = (vetor[0]*WEIGHT3) + (vetor[1]*WEIGHT2) + (vetor[2]*WEIGHT1);
    	}else if(chosenCore%2 == 1) {
    		totalWeight = (vetor[0]*WEIGHT1) + (vetor[1]*WEIGHT2) + (vetor[2]*WEIGHT3);
    	}
		
		return totalWeight;
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
