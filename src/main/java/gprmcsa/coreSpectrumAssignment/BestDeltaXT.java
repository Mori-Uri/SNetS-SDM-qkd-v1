package gprmcsa.coreSpectrumAssignment;

import java.util.List;

import network.Circuit;
import network.ControlPlane;
import network.Link;
import util.IntersectionFreeSpectrum;

/**
 * Represents the BestDeltaXT component.
 */
public class BestDeltaXT implements CoreAndSpectrumAssignmentAlgorithmInterface{
	public static final int QUANTCENTRALCORE = 100;
	
	private int coreOfTheTime;
	private int contCentralCore;
	
	/**
	 * Creates a new BestDeltaXT instance.
	 */
	public BestDeltaXT() {
		this.coreOfTheTime = 0;
		this.contCentralCore = QUANTCENTRALCORE;
		
	}
	
	@Override
	public boolean assignCoreAndSpectrum(int numberOfSlots, Circuit circuit, ControlPlane cp) {
		
		//int chosenCore = coreAssignment();
		double deltaXTEscolhido = 0;
		int espectroEscolhido[] = null;
		int indexCoreEscolhido = 0; //primeiro core
		double crosstalkEscolhido = 99999999;
		double deltaSNREscolhido = 0;

		int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
		
		//percorrendo cores
		/*for (int indexCore = 1; indexCore < 7; indexCore++) {
			// lista de blocos de slots frees
			List<int[]> composition = IntersectionFreeSpectrum.merge(circuit.getRoute(), circuit.getGuardBand(), indexCore);
			//int chosen[] = policy(numberOfslots, composition, circuit, cp);
			
			for (int[] band : composition) {
				
		        if(numberOfslots> maxAmplitude) return false;
	            if (band[1] - band[0] + 1 >= numberOfslots) {
	            	int espectro[] = band.clone();
	            	espectro[1] = espectro[0] + numberOfslots - 1; //It is not necessary to allocate the entire band, just the amount of slots required
	            	
	            	circuit.setSpectrumAssigned(espectro);
	    	        circuit.setIndexCore(indexCore);
	    	        
	            	boolean atendeCrosstalk = cp.computeCrosstalk(circuit);
	            	
	            	if (atendeCrosstalk) {
	    				double deltaXT = cp.getMesh().getCrosstalk().xtThreshold(circuit.getModulation()) - circuit.getXt();
	    				if (deltaXT > deltaXTChosen) {
	    					//System.out.println(deltaXT);
	    					deltaXTChosen = deltaXT;
	    					espectroChosen = espectro;
	    					indexCoreChosen = indexCore;
	    					crosstalkChosen = circuit.getXt();
	    				}
	    			}else {
	    				System.out.println(" problem de crosstalk");
	    			}
	            }else {
	            	System.out.println(" problem de Espectro");
	            }
	        }
			
			System.out.println(" Trocou de core");
					
		}*/
		//********************************************************************************************
		
		int indexCore = 1;
		//percorrendo cores
		while (true) {
			// lista de blocos de slots frees
			List<int[]> composition = IntersectionFreeSpectrum.merge(circuit.getRoute(), circuit.getGuardBand(), indexCore);
			//int chosen[] = policy(numberOfslots, composition, circuit, cp);
			
			for (int[] band : composition) {
				
		        if(numberOfSlots> maxAmplitude) return false;
	            if (band[1] - band[0] + 1 >= numberOfSlots) {
	            	int espectro[] = band.clone();
	            	espectro[1] = espectro[0] + numberOfSlots - 1; //It is not necessary to allocate the entire band, just the amount of slots required
	            	
	            	circuit.setSpectrumAssigned(espectro);
	    	        circuit.setIndexCore(indexCore);
	    	        
	    	        try {
						//if (cp.isAdmissibleQualityOfTransmission(circuit)) {
							if (cp.isAdmissibleCrosstalk(circuit)) {
								double deltaXT = circuit.getModulation().getXTthreshold() - circuit.getXt();
								if (deltaXT > deltaXTEscolhido) {
									//System.out.println(deltaXT);
									deltaXTEscolhido = deltaXT;
									espectroEscolhido = espectro;
									indexCoreEscolhido = indexCore;
									crosstalkEscolhido = circuit.getXt();
								}
							}else {
								System.out.println(" problem de Crosstalk");
							}
						//}else {
							//break;
							//System.out.println(" problem de SNR others");
							
						//}
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
	            }else {
	            	//System.out.println(" problem de Espectro");
	            }
	        }
			
			if (espectroEscolhido == null) { 
				if (indexCore == 0) break;
				
				if (indexCore == 1) {
					indexCore = 3;
				}else if (indexCore == 3) {
					indexCore = 5;
				}else if (indexCore == 5) {
					indexCore = 2;
				}else if (indexCore == 2) {
					indexCore = 4;
				}else if (indexCore == 4) {
					indexCore = 6;
				}else if (indexCore == 6) {
					indexCore = 0;
				}
			}else if (espectroEscolhido!=null){
				try {
					if (!cp.isAdmissibleQualityOfTransmission(circuit)) {
						if (indexCore == 0) break;
						
						if (indexCore == 1) {
							indexCore = 3;
						}else if (indexCore == 3) {
							indexCore = 5;
						}else if (indexCore == 5) {
							indexCore = 2;
						}else if (indexCore == 2) {
							indexCore = 4;
						}else if (indexCore == 4) {
							indexCore = 6;
						}else if (indexCore == 6) {
							indexCore = 0;
						}
					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}			
		}
		
       // System.out.println("teste1");
        
       // teste(circuit, chosen, chosenCore);
        
        if (espectroEscolhido == null) {
        	
        	return false;
        }
        
        circuit.setSpectrumAssigned(espectroEscolhido);
        circuit.setIndexCore(indexCoreEscolhido);
        circuit.setXt(crosstalkEscolhido);
        
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
	 * Executes the nucleoMenorNumeroslotsUsados operation.
	 * @param circuit the circuit.
	 * @return the result of the operation.
	 */
	public int nucleoMenorNumeroSlotsUsados(Circuit circuit) {
		
		int menorNumeroSlotsUsados = 9999999;
		int indexCoreEscolhido = 0;
		
		for (int indexCore = 0; indexCore < 7; indexCore++) {
			int numeroSlotsUsados = computeSpectrumUtilizedCores(circuit, indexCore);
			if (menorNumeroSlotsUsados > numeroSlotsUsados) {
				menorNumeroSlotsUsados = numeroSlotsUsados;
				indexCoreEscolhido = indexCore;
			}
		}
		
		return indexCoreEscolhido;
	}

	/**
	 * Executes the computeSpectrumUtilizedCores operation.
	 * @param circuit the circuit.
	 * @param indexCore the indexCore.
	 * @return the result of the operation.
	 */
	public int computeSpectrumUtilizedCores(Circuit circuit, int indexCore){
    	int total = 0;
    	
    	for(Link link: circuit.getRoute().getLinkList()) {
    		total = total + link.getCore(indexCore).getUsedSlots();
		}
    	
    	return total;
    }
	
	/**
	 * FirstFit
	 */
	@Override
	public int[] policy(int numberOfSlots, List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp) {
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
        
       // System.out.println("teste2");
        
        return chosen;
	}

}
