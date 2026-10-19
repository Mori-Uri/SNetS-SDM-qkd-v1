package gprmcsa.coreSpectrumAssignment;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import network.Circuit;
import network.ControlPlane;
import network.Core;
import network.Link;
import network.Spectrum;
import util.IntersectionFreeSpectrum;

//CBA-SBA
/**
 * Represents the ABNEwML component.
 */
public class ABNEwML implements CoreAndSpectrumAssignmentAlgorithmInterface {
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
	private int numberOfCores;
	private int spectrumUtilizationAbsolut[];
	private int ultimoCore;
	
	/**
	 * Creates a new ABNEwML instance.
	 */
	public ABNEwML() {
		this.coreOfTheTime = 0;
		this.numberOfCores = -1; //indices de cores
		//this.spectrumUtilizationAbsolut = new int[numberOfCores];
	}
	@Override
	public boolean assignCoreAndSpectrum(int numberOfCores, Circuit circuit, ControlPlane cp) {
		if (numberOfCores == -1) {
			numberOfCores = cp.getMesh().getLinkList().get(0).getNumberOfCores();
			this.spectrumUtilizationAbsolut = new int[numberOfCores];
		}
		
		spectrumUtilizationAbsolute(circuit, cp);
		spectrumUtilizationWeighted(circuit, cp);
		spectrumUtilizationInRoute(circuit, cp);
		
		int chosenCore = coreAssignment(circuit, cp);
		int chosen[] = null;
    	List<int[]> composition = IntersectionFreeSpectrum.merge(circuit.getRoute(), circuit.getGuardBand(), chosenCore);
        
    	//System.out.println("\n\n Testes");
    	
    	if(chosenCore == 0) {
    		//System.out.println("Medium Fit chosen");
    		chosen = policy(numberOfCores, composition, circuit, cp);
    	}else if(chosenCore%2 == 0) {
    		//System.out.println("Last Fit chosen");
    		chosen = policy1(numberOfCores, composition, circuit, cp);
    	}else if(chosenCore%2 == 1) {
    		//System.out.println("First Fit chosen");
    		chosen = policy2(numberOfCores, composition, circuit, cp);
    		
    	}
    	
 
    	//System.out.println("Core: "+chosenCore);
    	//System.out.println("spectrum: "+chosen[0]+"-"+chosen[1]);
        circuit.setSpectrumAssigned(chosen);
        circuit.setIndexCore(chosenCore);
        
        
        if (chosen == null) {
        	return false;
        }

        	
        return true;
	}
	
	/**
	 * Executes the coreAssignment operation.
	 * @return the result of the operation.
	 */
	public int coreAssignment() {
		if (coreOfTheTime == numberOfCores -1) {
    		coreOfTheTime = 0;
    		return numberOfCores -1;
    	}else {
    		int temp = coreOfTheTime;
    		coreOfTheTime++;
    		return temp;
    	}
	}
	
	/**
	 * Executes the coreAssignment operation.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @return the result of the operation.
	 */
	public int coreAssignment(Circuit circuit, ControlPlane cp) {
		int numCore = numberOfCores -1;
		int core = numCore;
		int coreVector[] = new int[numberOfCores];
		
		for (int i = numCore; i>=0; i--) {
			coreVector[i] = (circuit.getSpectrumUtilizationAbsolut()[i] * WEIGHTABS) + (circuit.getSpectrumUtilizationWeighted()[i] * WEIGHTWEI) + (circuit.getSpectrumUtilizationInRoute()[i] * WEIGHTROUT);
		}
		
		for (int i = numCore; i>=0; i--) {
			if ((coreVector[i] < coreVector[core])) {
				core = i;
			}
		}
		
		return core;
	}

	//
	// Machine Learning
	// Machine Learning
	// Machine Learning
	// Machine Learning
	// Machine Learning
	//
	/*	@Override
	/**
	 * Executes the coreAssignment operation.
	 * @return the result of the operation.
	 */
//	public int coreAssignment() {
//		String message = "";
//		link = linkMostUsed(route);
//		//for MLP
//		//message = link.getUsedslots() + "/" + link.getUtilization() + "/" + link.getCircuitList().size() + "/" + route.getHops() + "/" + numberOfFreeslots(route) + "/" + UtilizationGeral(cp.getMesh()) + "/" + mod.getM() + "/" + circuit.getSNR();
//		//for CONV
//		message = link.getUsedSlots() + "/" + link.getCircuitList().size() + "/" + route.getHops() + "/" + mod.getM();
//		//System.out.println(message);
//		//message = link.getUtilization() +"/"+ mod.getM();
//		// Conectando com o servidor
//		Socket cliente = null;
//		try {
//			cliente = new Socket("127.0.0.1", 7000);
//		} catch (UnknownHostException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		} catch (IOException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		}
//		
//		// Enviando dados for the servidor
//		PrintStream saida = null;
//		try {
//			saida = new PrintStream(cliente.getOutputStream());
//		} catch (IOException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		}
//		saida.println(message);
//
//		//Pegando return do servidor
//		Scanner s = null;
//		try {
//			s = new Scanner(cliente.getInputStream());
//		} catch (IOException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		}
//		guardBand = Integer.parseInt(s.nextLine());
//		//System.out.println(message);
//		//System.out.println(guardBand);
//		
//		// Closing the connection
//		s.close();
//		saida.close();
//		try {
//			cliente.close();
//		} catch (IOException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		}
//	}

	//"medium fit
	//policy for central core
	@Override
	public int[] policy(int numberOfCores, List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp) {
		int reference = circuit.getRoute().getLink(0).getCore(0).getNumOfSlots()/2;
		int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
        if(numberOfCores> maxAmplitude) return null;
    	int chosen[] = null;
    	
    	for (int[] band : freeSpectrumBands) {
        	if(chosen == null) {
        		if (band[1] - band[0] + 1 >= numberOfCores) {
                    chosen = band.clone();
                    chosen[1] = chosen[0] + numberOfCores - 1;//It is not necessary to allocate the entire band, just the amount of slots required
                    break;
                }       		
        	}
    	}
    	
    	if(chosen != null) {
	    	for (int[] band : freeSpectrumBands) {	
	        	//if(chosen != null) {
	        	
		        	for(int i = band[0]; i<=band[1]; i++) {
		        		if(Math.abs(reference-i) < Math.abs(reference-chosen[0])) {
		        			if((band[1]-i+1) >= numberOfCores) {
		        				chosen[0] = i;
		        				chosen[1] = i + numberOfCores - 1;
		        			}
		        		}
		        		
		//        		if(i<reference) {
		//        			if((i+reference)>(chosen[0]+reference)) {
		//            			if((band[1]-i+1) >= numberOfCores) {
		//            				chosen[0] = i;
		//            				chosen[1] = i + numberOfCores - 1;
		//            			}
		//            		}
		//        		}else {
		//        			if((i+reference)<(chosen[0]+reference)) {
		//            			if((band[1]-i+1) >= numberOfCores) {
		//            				chosen[0] = i;
		//            				chosen[1] = i + numberOfCores - 1;
		//            			}
		//            		}
		//        		}
		        	}		
		      }
    	}
        
        return chosen;
	}
	
	//Last Fit
	//policy for 2,4,6 core
	/**
	 * Executes the policy1 operation.
	 * @param numberOfCores the numberOfCores.
	 * @param freeSpectrumBands the freeSpectrumBands.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @return the result of the operation.
	 */
	public int[] policy1(int numberOfCores, List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp) {
		int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
		if(numberOfCores>maxAmplitude) return null;
		int chosen[] = null;
		int band[] = null;
		
		for (int i = freeSpectrumBands.size() - 1; i >= 0; i--) {
			band = freeSpectrumBands.get(i);
			
			if (band[1] - band[0] + 1 >= numberOfCores) {
				chosen = band.clone();
				chosen[0] = chosen[1] - numberOfCores + 1;//It is not necessary to allocate the entire band, just the amount of slots required
				break;
			}
		}

		return chosen;
	}
	
	//First Fit
	//policy for 1,3,5 core
	/**
	 * Executes the policy2 operation.
	 * @param numberOfCores the numberOfCores.
	 * @param freeSpectrumBands the freeSpectrumBands.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @return the result of the operation.
	 */
	public int[] policy2(int numberOfCores, List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp) {
		int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
        if(numberOfCores> maxAmplitude) return null;
    	int chosen[] = null;
    	
        for (int[] band : freeSpectrumBands) {
        	
            if (band[1] - band[0] + 1 >= numberOfCores) {
                chosen = band.clone();
                chosen[1] = chosen[0] + numberOfCores - 1;//It is not necessary to allocate the entire band, just the amount of slots required
                break;
            }
        }
        
        return chosen;
	}
	
	// Spectrum Ultilization in Route
	private void spectrumUtilizationInRoute (Circuit circuit, ControlPlane cp) {
		int contador = 0;
		Vector<Link> listaDeLinks;
		listaDeLinks = circuit.getRoute().getLinkList();
		
		for (Link link : listaDeLinks) {
			for (int i = 0; i<7; i++) {
				circuit.refreshSpectrumUtilizationInRouteUnit(i, circuit.getSpectrumUtilizationInRoute()[i] + link.getCore(i).getSpectrum().getUsedSlots());
			}
		}
	}
	
	// Spectrum Utilization Absolute
	private void spectrumUtilizationAbsolute (Circuit circuit, ControlPlane cp) {
		Vector<Link> listaDeLinks;
		ArrayList<Core> coresAdjacentes;
		
		listaDeLinks = circuit.getRoute().getLinkList();
		
		//for (int a = 0; a < this.numberOfCores; a++) {
		//	circuit.refreshSpectrumUtilizationAbsolutUnit(a, 0);;
		//}
		
		for (int i = 0; i<this.numberOfCores; i++) {
			//listaDeLinks = null;
			//coresAdjacentes = null;
			
			coresAdjacentes = circuit.getRoute().getLink(0).getAdjacentCores(i);
			
			for (Link link : listaDeLinks) {
				for (Core coreadj : link.getCores()) {
					if (coresAdjacentes.contains(coreadj)) {
						//circuit.refreshSpectrumUtilizationAbsolutUnit(i, (circuit.getSpectrumUtilizationAbsolut()[i] + 1));
						circuit.refreshSpectrumUtilizationAbsolutUnit(i, circuit.getSpectrumUtilizationAbsolut()[i] + coreadj.getUsedSlots());
						//this.spectrumUtilizationAbsolut[i] = this.spectrumUtilizationAbsolut[i] + coreadj.getUsedslots();
					}
				}				
			}
		}
	}
	
	// Spectrum Ultilization Weighted
	private void spectrumUtilizationWeighted (Circuit circuit, ControlPlane cp) {
		Vector<Link> listaDeLinks;
		ArrayList<Core> coresAdjacentes;
		
		listaDeLinks = circuit.getRoute().getLinkList();
		
		for (int i = 0; i<this.numberOfCores; i++) {
			
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
		
		if(chosenCore == 0) {
			totalWeight = (vetor[0]*WEIGHT4) + (vetor[1]*WEIGHT1) + (vetor[2]*WEIGHT4);
    	}else if(chosenCore%2 == 0) {
    		totalWeight = (vetor[0]*WEIGHT3) + (vetor[1]*WEIGHT2) + (vetor[2]*WEIGHT1);
    	}else if(chosenCore%2 == 1) {
    		totalWeight = (vetor[0]*WEIGHT1) + (vetor[1]*WEIGHT2) + (vetor[2]*WEIGHT3);
    	}
		
		return totalWeight;
	}
	
}
