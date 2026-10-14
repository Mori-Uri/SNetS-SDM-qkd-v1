package gprmcsa.trafficGrooming.util;

import java.util.List;

import network.Circuit;
import request.RequestForConnection;
import util.IntersectionFreeSpectrum;

/**
 * This class provides some useful methods for traffic grooming algorithms
 * Created by Iallen on 11/08/2017.
 */
public class Grooming {

    /**
     * This method analyzes the available spectrum frequencies and calculates the potential for expansion of a circuit.
     * @param circuit
     * @return A vector of integers with two positions. The first position is the lower expansion potential of the circuit. The second position is the upper expansion potential of the circuit.
     */
    public static int[] circuitExpansiveness(Circuit circuit){
        int res[] = new int[2];
        List<int[]> composition = IntersectionFreeSpectrum.merge(circuit.getRoute(), circuit.getGuardBand(), circuit.getIndexCore());
        res[0] = IntersectionFreeSpectrum.freeSlotsDown(circuit.getSpectrumAssigned(), composition, circuit.getGuardBand());
        res[1] = IntersectionFreeSpectrum.freeSlotsUpper(circuit.getSpectrumAssigned(), composition, circuit.getGuardBand());
        return res;
    }

    /**
     * Checks whether be expanded.
     * @param circuit the circuit.
     * @param rfc the rfc.
     * @return true if the condition is met; false otherwise.
     */
    public static boolean canBeExpanded(Circuit circuit, RequestForConnection rfc) {
        int[] exp = Grooming.circuitExpansiveness(circuit);
        int circExCap = exp[0] + exp[1];
        int slotsNeeded = circuit.getModulation().requiredSlots(circuit.getRequiredBitRate() + rfc.getRequiredBitRate()) - (circuit.getSpectrumAssigned()[1] - circuit.getSpectrumAssigned()[0] + 1);
        return circExCap >= slotsNeeded;
    }
}
