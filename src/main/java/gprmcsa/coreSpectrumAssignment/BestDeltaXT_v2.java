package gprmcsa.coreSpectrumAssignment;

import java.util.List;

import network.Circuit;
import network.ControlPlane;
import util.IntersectionFreeSpectrum;

/**
 * Represents the BestDeltaXT_v2 component.
 */
public class BestDeltaXT_v2 implements CoreAndSpectrumAssignmentAlgorithmInterface{
	private int coreOfTheTime;
	
	/**
	 * Creates a new BestDeltaXT_v2 instance.
	 */
	public BestDeltaXT_v2() {
		this.coreOfTheTime = 0;
	}
	
	@Override
	public boolean assignCoreAndSpectrum(int numberOfSlots, Circuit circuit, ControlPlane cp) {
		int chosenCore = coreAssignment();
		int chosen[] = null;
    	List<int[]> composition = IntersectionFreeSpectrum.merge(circuit.getRoute(), circuit.getGuardBand(), chosenCore);
        
        circuit.setIndexCore(chosenCore);

    	//System.out.println("\n\n Testes");
    	
    	if(chosenCore == 0) {
    		//System.out.println("Medium Fit chosen");
    		chosen = policy0(numberOfSlots, composition, circuit, cp);
    	}else if(chosenCore%2 == 0) {
    		//System.out.println("Last Fit chosen");
    		chosen = policy0(numberOfSlots, composition, circuit, cp);
    	}else if(chosenCore%2 == 1) {
    		//System.out.println("First Fit chosen");
    		chosen = policy0(numberOfSlots, composition, circuit, cp);
    		
    	}
    	
    	//System.out.println("Core: "+chosenCore);
    	//System.out.println("spectrum: "+chosen[0]+"-"+chosen[1]);
        circuit.setSpectrumAssigned(chosen);
        
        
        if (chosen == null) {
        	return false;
        }

        	
        return true;
	}

	@Override
	public int coreAssignment() {
		if (coreOfTheTime == 6) {
    		coreOfTheTime = 0;
    		return 6;
    	}else {
    		int temp = coreOfTheTime;
    		coreOfTheTime++;
    		return temp;
    	}
	}

	/**
	 * Executes the policy0 operation.
	 * @param numberOfslots the numberOfslots.
	 * @param freeSpectrumBands the freeSpectrumBands.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @return the result of the operation.
	 */
	public int[] policy0(int numberOfSlots, List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp) {
		//int reference = circuit.getRoute().getLink(0).getCore(0).getNumOfslots()/2;
		int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
        if(numberOfSlots> maxAmplitude) return null;
    	
    	double deltaXTEscolhido = 0;
		int espectroEscolhido[] = null;
    	
    	for (int[] band : freeSpectrumBands) {
            
	        if (band[1] - band[0] + 1 >= numberOfSlots) {
            	int espectro[] = band.clone();
            	espectro[1] = espectro[0] + numberOfSlots - 1; //It is not necessary to allocate the entire band, just the amount of slots required
            	
            	circuit.setSpectrumAssigned(espectro);
    	        
            	boolean atendeCrosstalk = cp.computeCrosstalk(circuit);
            	
            	if (atendeCrosstalk) {
    				double deltaXT = circuit.getModulation().getXTthreshold() - circuit.getXt();
    				if (deltaXT > deltaXTEscolhido) {
    					//System.out.println(deltaXT);
    					deltaXTEscolhido = deltaXT;
    					espectroEscolhido = espectro;
    					
    				}
    			}else {
    				System.out.println(" problem de crosstalk");
    			}
            }else {
            	System.out.println(" problem de Espectro");
            }
        }
    	
        return espectroEscolhido;
	}
	//"medium fit
	//policy for central core
	@Override
	public int[] policy(int numberOfSlots, List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp) {
		int reference = circuit.getRoute().getLink(0).getCore(0).getNumOfSlots()/2;
		int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
        if(numberOfSlots> maxAmplitude) return null;
    	int chosen[] = null;
    	
    	for (int[] band : freeSpectrumBands) {
        	if(chosen == null) {
        		if (band[1] - band[0] + 1 >= numberOfSlots) {
                    chosen = band.clone();
                    chosen[1] = chosen[0] + numberOfSlots - 1;//It is not necessary to allocate the entire band, just the amount of slots required
                    break;
                }       		
        	}
    	}
    	
    	if(chosen != null) {
	    	for (int[] band : freeSpectrumBands) {	
	        	//if(chosen != null) {
	        	
		        	for(int i = band[0]; i<=band[1]; i++) {
		        		if(Math.abs(reference-i) < Math.abs(reference-chosen[0])) {
		        			if((band[1]-i+1) >= numberOfSlots) {
		        				chosen[0] = i;
		        				chosen[1] = i + numberOfSlots - 1;
		        			}
		        		}
		        		
		//        		if(i<reference) {
		//        			if((i+reference)>(chosen[0]+reference)) {
		//            			if((band[1]-i+1) >= numberOfslots) {
		//            				chosen[0] = i;
		//            				chosen[1] = i + numberOfslots - 1;
		//            			}
		//            		}
		//        		}else {
		//        			if((i+reference)<(chosen[0]+reference)) {
		//            			if((band[1]-i+1) >= numberOfslots) {
		//            				chosen[0] = i;
		//            				chosen[1] = i + numberOfslots - 1;
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
	 * @param numberOfslots the numberOfslots.
	 * @param freeSpectrumBands the freeSpectrumBands.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @return the result of the operation.
	 */
	public int[] policy1(int numberOfSlots, List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp) {
		int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
        if(numberOfSlots>maxAmplitude) return null;
    	int chosen[] = null;
        int band[] = null;
        
        for (int i = freeSpectrumBands.size() - 1; i >= 0; i--) {
            band = freeSpectrumBands.get(i);
            
            if (band[1] - band[0] + 1 >= numberOfSlots) {
                chosen = band.clone();
                chosen[0] = chosen[1] - numberOfSlots + 1;//It is not necessary to allocate the entire band, just the amount of slots required
                break;
            }
        }

        return chosen;
	}
	
	//First Fit
	//policy for 1,3,5 core
	/**
	 * Executes the policy2 operation.
	 * @param numberOfslots the numberOfslots.
	 * @param freeSpectrumBands the freeSpectrumBands.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @return the result of the operation.
	 */
	public int[] policy2(int numberOfSlots, List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp) {
		int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
        if(numberOfSlots> maxAmplitude) return null;
    	int chosen[] = null;
    	
        for (int[] band : freeSpectrumBands) {
        	
            if (band[1] - band[0] + 1 >= numberOfSlots) {
                chosen = band.clone();
                chosen[1] = chosen[0] + numberOfSlots - 1;//It is not necessary to allocate the entire band, just the amount of slots required
                break;
            }
        }
        
        return chosen;
	}

}
