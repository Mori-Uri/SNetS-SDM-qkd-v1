package gprmcsa.coreSpectrumAssignment;

import java.util.List;
import java.util.Random;

import network.Circuit;
import network.ControlPlane;
import util.IntersectionFreeSpectrum;

/**
 * Represents the RandomCoreFirstFit component.
 */
public class RandomCoreFirstFit implements CoreAndSpectrumAssignmentAlgorithmInterface{
	private Random generator;
	private int numberOfCores;

	/**
	 * Creates a new instance of RandomCoreFirstFit.
	 */
	public RandomCoreFirstFit() {
		this.generator = new Random();
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
    	List<int[]> composition = IntersectionFreeSpectrum.merge(circuit.getRoute(), circuit.getGuardBand(), chosenCore);

        int chosen[] = policy(numberOfSlots, composition, circuit, cp);
        circuit.setSpectrumAssigned(chosen);
        circuit.setIndexCore(chosenCore);

       // System.out.println("test1");

       // test(circuit, chosen, chosenCore);

        if (chosen == null)
        	return false;

        return true;
	}

	//This method sets the core choised
	/**
	 * Returns the core assignment.
	 * @return the result of the operation.
	 */
	public int coreAssignment() {
		//int a = generator.nextInt()%this.numberOfCores;
		int a = generator.nextInt(this.numberOfCores);
	//	System.out.println("randomly selected core: "+a);
		return a;
	}

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

       // System.out.println("test2");

        return chosen;
	}

}
