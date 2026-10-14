package gprmcsa.coreSpectrumAssignment;


import java.util.List;


import network.Circuit;
import network.ControlPlane;
import util.IntersectionFreeSpectrum;

/**
 * Represents the CSBASDM2 component.
 */
public class CSBASDM2 implements CoreAndSpectrumAssignmentAlgorithmInterface {
	public static final int QUANTCENTRALCORE = 5;

	private int coreOfTheTime;
	private int contCentralCore;
	private int numberOfCores;

	/**
	 * Creates a new instance of CSBASDM2.
	 */
	public CSBASDM2() {
		this.coreOfTheTime = 0;
		this.contCentralCore = QUANTCENTRALCORE;
		this.numberOfCores = -1;
	}

	/**
	 * Returns the assign core and spectrum.
	 * @param numberOfSlots the numberOfSlots.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @return true if the condition is met; false otherwise.
	 */
	@Override
	public boolean assignCoreAndSpectrum(int numberOfSlots, Circuit circuit, ControlPlane cp) {
		if (numberOfCores == -1) {
			numberOfCores = cp.getMesh().getLinkList().get(0).getNumberOfCores();
		}

		int chosenCore = coreAssignment();
		int chosen[] = null;
    	List<int[]> composition = IntersectionFreeSpectrum.merge(circuit.getRoute(), circuit.getGuardBand(), chosenCore);

    	//System.out.println("\n\n Testes");
    	//System.out.println("core "+chosenCore);

    	if(chosenCore == 0) {
    		//System.out.println("Medium Fit selected \n");
    		chosen = policy(numberOfSlots, composition, circuit, cp);
    	}else if(chosenCore%2 == 0) {
    		//System.out.println("Last Fit selected \n");
    		chosen = policy1(numberOfSlots, composition, circuit, cp);
    	}else if(chosenCore%2 == 1) {
    		//System.out.println("First Fit selected \n");
    		chosen = policy2(numberOfSlots, composition, circuit, cp);
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
	 * Returns the core assignment.
	 * @return the result of the operation.
	 */
	@Override
	public int coreAssignment() {
		if ((coreOfTheTime == 0)) {
			if (contCentralCore == 0) {
				contCentralCore = QUANTCENTRALCORE;
				coreOfTheTime++;
				return 0;
			}else {
				contCentralCore--;
				coreOfTheTime++;
			}
		}

		if (coreOfTheTime == numberOfCores -1) {
    		coreOfTheTime = 0;
    		return numberOfCores -1;
    	}else {
    		int temp = coreOfTheTime;
    		coreOfTheTime++;
    		return temp;
    	}
	}

	//"medium fit
	//policy for central core
	/**
	 * Returns the policy.
	 * @param numberOfSlots the numberOfSlots.
	 * @param freeSpectrumBands the freeSpectrumBands.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @return the result of the operation.
	 */
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
		//            			if((band[1]-i+1) >= numberOfSlots) {
		//            				chosen[0] = i;
		//            				chosen[1] = i + numberOfSlots - 1;
		//            			}
		//            		}
		//        		}else {
		//        			if((i+reference)<(chosen[0]+reference)) {
		//            			if((band[1]-i+1) >= numberOfSlots) {
		//            				chosen[0] = i;
		//            				chosen[1] = i + numberOfSlots - 1;
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
	 * Returns the policy1.
	 * @param numberOfSlots the numberOfSlots.
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
	 * Returns the policy2.
	 * @param numberOfSlots the numberOfSlots.
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
