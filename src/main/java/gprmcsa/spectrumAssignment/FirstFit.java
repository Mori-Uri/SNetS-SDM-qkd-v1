package gprmcsa.spectrumAssignment;

import java.util.List;
import network.Circuit;
import network.ControlPlane;
import util.IntersectionFreeSpectrum;

/**
 * This class represents the spectrum allocation technique called First Fit.
 * This technique chooses the first free spectrum band that accommodates the request.
 *
 * @author Iallen
 */
public class FirstFit implements SpectrumAssignmentAlgorithmInterface {

    /**
     * Returns the assign spectrum.
     * @param numberOfSlots the numberOfSlots.
     * @param circuit the circuit.
     * @param cp the cp.
     * @param indexCore the indexCore.
     * @return true if the condition is met; false otherwise.
     */
    @Override
    public boolean assignSpectrum(int numberOfSlots, Circuit circuit, ControlPlane cp, int indexCore) {
    	List<int[]> composition = IntersectionFreeSpectrum.merge(circuit.getRoute(), circuit.getGuardBand(), indexCore);

        int chosen[] = policy(numberOfSlots, composition, circuit, cp);
        circuit.setSpectrumAssigned(chosen);

        if (chosen == null)
        	return false;

        return true;
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
    public int[] policy(int numberOfSlots, List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp){
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