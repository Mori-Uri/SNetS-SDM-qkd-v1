package gprmcsa.coreSpectrumAssignment;


import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import network.Circuit;
import network.ControlPlane;
import util.IntersectionFreeSpectrum;

/**
 * Represents the MuriTest component.
 */
public class MuriTest implements CoreAndSpectrumAssignmentAlgorithmInterface{
	
	private int numberOfCores;
	
	/**
	 * Creates a new MuriTest instance.
	 */
	public MuriTest() {
		this.numberOfCores = -1;
	}

	/**
	 * Executes the assignCoreAndSpectrum operation.
	 * @param numberOfslots the numberOfslots.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @return the result of the operation.
	 */
	public boolean assignCoreAndSpectrum(int numberOfSlots, Circuit circuit, ControlPlane cp) {
		
		int selectedCore = coreAssignment();
		int selectedLink[] = null;
		int selectedGuard = 0;
		
		List<int[]> composition = IntersectionFreeSpectrum.merge(circuit.getRoute(), circuit.getGuardBand(), selectedCore);
		
		if (selectedCore == 0) {
			selectedLink = policy(numberOfSlots, composition, circuit, cp);
			selectedGuard = 0;
		}else if(selectedCore%2 == 0) {
			selectedLink = policy1(numberOfSlots, composition, circuit, cp);
			selectedGuard = 1;
    	}else if(selectedCore%2 == 1) {
    		selectedLink = policy2(numberOfSlots, composition, circuit, cp);
    		selectedGuard = 8;
    	}
		//Teste de Banda de Guarda
		circuit.setSpectrumAssigned(selectedLink);
        circuit.setIndexCore(selectedCore);
        circuit.setGuardBand(selectedGuard);
		
        if (selectedLink == null) {
        	return false;
        }

        return true;
		
		
	}
	
	/**
	 * Executes the coreAssignment operation.
	 * @return the result of the operation.
	 */
	public int coreAssignment() {
		
		int selectedC = ThreadLocalRandom.current().nextInt(0, numberOfCores);
		return selectedC;
		
	}

	/**
	 * Executes the policy operation.
	 * @param numberOfslots the numberOfslots.
	 * @param freeSpectrumBands the freeSpectrumBands.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @return the result of the operation.
	 */
	public int[] policy(int numberOfSlots, List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp) {
		int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
		if(numberOfSlots > maxAmplitude) return null;
    	
		int selectedLink[] = null;
		ArrayList<int[]> bandList = new ArrayList<int[]>();
		
		for (int[] band : freeSpectrumBands) { //checks and guard the free bands that can establish the requisition
			if(band[1] - band[0] + 1 >= numberOfSlots){
				int faixaTemp[] = band.clone();
				bandList.add(faixaTemp);
			}
		}
		
		if(bandList.size() > 0){ //if you have free bands, choose one randomly
			Random rand = new Random();
			int indexBand = rand.nextInt(bandList.size());
			selectedLink = bandList.get(indexBand);
			selectedLink[1] = selectedLink[0] + numberOfSlots - 1; //it is not necessary to allocate the entire band, only the number of slots necessary
		}
		
		return selectedLink;
		
	}

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
        
        int selectedLink[] = null;
        for (int[] band: freeSpectrumBands) {
        	if (band[1] - band[0] + 1 >= numberOfSlots) {
        		selectedLink = band.clone();
        		selectedLink[1] = selectedLink[0] + numberOfSlots - 1;
                break;
            }
        }
        
        return selectedLink;
        
	}

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
        if(numberOfSlots>maxAmplitude) return null;
        
    	int selectedLink[] = null;
        int band[] = null;
        
        for (int i = freeSpectrumBands.size() - 1; i >= 0; i--) {
            band = freeSpectrumBands.get(i);
            
            if (band[1] - band[0] + 1 >= numberOfSlots) {
            	selectedLink = band.clone();
            	selectedLink[0] = selectedLink[1] - numberOfSlots + 1;//It is not necessary to allocate the entire band, just the amount of slots required
                break;
            }
        }

        return selectedLink;
	}
}
