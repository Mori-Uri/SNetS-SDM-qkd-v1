package gprmcsa.coreSpectrumAssignment;

import java.util.List;

import network.Circuit;
import network.ControlPlane;
import util.IntersectionFreeSpectrum;

/**
 * Represents the TestOSNR component.
 */
public class TestOSNR implements CoreAndSpectrumAssignmentAlgorithmInterface{

	@Override
	public boolean assignCoreAndSpectrum(int numberOfSlots, Circuit circuit, ControlPlane cp) {
		int chosenCore = coreAssignment();
    	List<int[]> composition = IntersectionFreeSpectrum.merge(circuit.getRoute(), circuit.getGuardBand(), chosenCore);
        
        int chosen[] = policy(numberOfSlots, composition, circuit, cp);
        circuit.setSpectrumAssigned(chosen);
        circuit.setIndexCore(chosenCore);
        
       // System.out.println("teste1");
        
       // teste(circuit, chosen, chosenCore);
        
       // testes(circuit);
        
        if (chosen == null)
        	return false;

        return true;
	}

	@Override
	public int coreAssignment() {
		return 1;
	}

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
	
	private void testes(Circuit circuit) {
		System.out.println("----------------------------------------\nPhysical Layer Tests:");
		
		System.out.println(" The used core is: "+circuit.getIndexCore());
		System.out.println(" The route is: "+circuit.getRoute().getSource().getName()+"-"+circuit.getRoute().getDestination().getName());
		System.out.println(" The route length is: "+circuit.getRoute().getDistanceAllLinks()+" km");
		System.out.println(" The used modulation is: "+circuit.getModulation().getName());
		System.out.println(" The circuit OSNR is: "+circuit.getSNR());
		
		System.out.println("----------------------------------------\n");
	}

}
