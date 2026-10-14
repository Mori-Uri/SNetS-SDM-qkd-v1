package qkd.algorithms.coreSpectrumAssignment;

import java.util.List;

import gprmcsa.coreSpectrumAssignment.CoreAndSpectrumAssignmentAlgorithmInterface;
import network.Circuit;
import network.ControlPlane;
import network.Core;
import network.Link;
import util.IntersectionFreeSpectrum;

/**
 * QKD-addon first-fit core and spectrum assignment.
 *
 * <p>Among the classical cores of the route (the core reserved for QKD is
 * never a candidate), chooses the lowest starting slot that fits the
 * request; ties go to the lowest core index. In a multi-fiber model where
 * each "core" is an independent fiber this is the first-fit rule of the
 * Dibaj et al. (2024) reconstruction. Name in the simulation file:
 * {@code "qfirstfit"}.</p>
 *
 * <p>Refer\u00eancias: [CHAT15] B. C. Chatterjee, N. Sarma, E. Oki, "Routing and Spectrum Allocation in Elastic Optical Networks: A Tutorial", IEEE COMST 17(3), 2015 (first fit).</p>
 */
public class QKDFirstFit implements CoreAndSpectrumAssignmentAlgorithmInterface {

    private static final long serialVersionUID = 1L;

    @Override
    public boolean assignCoreAndSpectrum(int numberOfSlots, Circuit circuit, ControlPlane cp) {
        if (circuit.getRoute() == null || circuit.getRoute().getLinkList().isEmpty()) {
            return false;
        }
        Link first = circuit.getRoute().getLinkList().get(0);
        int bestCore = -1;
        int[] best = null;
        for (Core core : first.getCores()) {
            if (core.isQkdReserved()) {
                continue;
            }
            List<int[]> free = IntersectionFreeSpectrum.merge(circuit.getRoute(),
                    circuit.getGuardBand(), core.getId());
            int[] band = policy(numberOfSlots, free, circuit, cp);
            if (band != null && (best == null || band[0] < best[0])) {
                best = band;
                bestCore = core.getId();
            }
        }
        circuit.setIndexCore(bestCore < 0 ? firstClassicalCore(first) : bestCore);
        circuit.setSpectrumAssigned(best);
        return best != null;
    }

    private static int firstClassicalCore(Link link) {
        for (Core core : link.getCores()) {
            if (!core.isQkdReserved()) {
                return core.getId();
            }
        }
        return 0;
    }

    @Override
    public int coreAssignment() {
        return 0;
    }

    @Override
    public int[] policy(int numberOfSlots, List<int[]> freeSpectrumBands, Circuit circuit,
            ControlPlane cp) {
        if (numberOfSlots <= 0) {
            return null;
        }
        for (int[] band : freeSpectrumBands) {
            if (band[1] - band[0] + 1 >= numberOfSlots) {
                return new int[] {band[0], band[0] + numberOfSlots - 1};
            }
        }
        return null;
    }
}
