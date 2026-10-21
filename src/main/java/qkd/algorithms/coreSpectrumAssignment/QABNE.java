package qkd.algorithms.coreSpectrumAssignment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import gprmcsa.coreSpectrumAssignment.CoreAndSpectrumAssignmentAlgorithmInterface;
import network.Circuit;
import network.ControlPlane;
import network.Core;
import network.Link;
import util.IntersectionFreeSpectrum;

/**
 * QKD-addon adaptation of the original SNetS CSBASDM/ABNE core-spectrum
 * assignment strategy.
 *
 * <p>This class intentionally lives under the {@code qkd} namespace and does
 * not modify, extend or replace {@code gprmcsa.coreSpectrumAssignment.CSBASDM}.
 * Classical simulations keep using the original ABNE implementation. QKD
 * simulations may explicitly select this addon with
 * {@code coreAndSpectrumAssignment = "qabne"}.</p>
 *
 * <p>The spectral policies are the same three policies used by ABNE: medium
 * fit for the central core, last fit for even outer-core identifiers and
 * first fit for odd outer-core identifiers. The QKD adaptation changes the
 * core rotation only: cores marked {@link Core#isQkdReserved()} are removed
 * from the classical candidate set before the round-robin rotation.</p>
 *
 * <p>Refer\u00eancias: [ABNE20] J. Lacerda Jr. et al., "ABNE - Algoritmo de Balanceamento Inter-N\u00facleos para Redes \u00d3pticas El\u00e1sticas com SDM", 2020 (rota\u00e7\u00e3o de n\u00facleos e pol\u00edticas medium/first/last fit); [FUJII14] S. Fujii et al., "On-Demand Spectrum and Core Allocation for Reducing Crosstalk in Multicore Fibers in EONs", JOCN 6(12), 2014.</p>
 */
public class QABNE implements CoreAndSpectrumAssignmentAlgorithmInterface {

    private static final long serialVersionUID = 1L;

    private int rotationIndex;
    private List<Integer> classicalCoreIds;
    private int centralCoreId;

    public QABNE() {
        this.rotationIndex = 0;
        this.classicalCoreIds = Collections.emptyList();
        this.centralCoreId = 0;
    }

    @Override
    public boolean assignCoreAndSpectrum(int numberOfSlots, Circuit circuit,
            ControlPlane cp) {
        ensureCoreLayout(circuit);

        if (classicalCoreIds.isEmpty()) {
            circuit.setSpectrumAssigned(null);
            return false;
        }

        final int chosenCore = coreAssignment();
        final List<int[]> freeSpectrumBands = IntersectionFreeSpectrum.merge(
                circuit.getRoute(), circuit.getGuardBand(), chosenCore);

        final int[] chosen;
        if (chosenCore == centralCoreId) {
            chosen = policy(numberOfSlots, freeSpectrumBands, circuit, cp);
        } else if (chosenCore % 2 == 0) {
            chosen = policyLastFit(numberOfSlots, freeSpectrumBands, circuit);
        } else {
            chosen = policyFirstFit(numberOfSlots, freeSpectrumBands, circuit);
        }

        // Part 36: same bookkeeping as the classical ABNE (CSBASDM2): the band and the core are written also when
        // the assignment fails. Before, a failure left the band of the previous (other modulation) attempt in the
        // circuit, and spcsav2 could test the physical layer with that stale band; it also printed "Core not selected.".
        circuit.setSpectrumAssigned(chosen);
        circuit.setIndexCore(chosenCore);
        return chosen != null;
    }

    /**
     * Round-robin over classical cores only. The physical core identifiers are
     * preserved, so ABNE parity-based outer-core policies keep their original
     * meaning.
     */
    @Override
    public int coreAssignment() {
        if (classicalCoreIds == null || classicalCoreIds.isEmpty()) {
            return -1;
        }
        if (rotationIndex >= classicalCoreIds.size()) {
            rotationIndex = 0;
        }
        int selected = classicalCoreIds.get(rotationIndex).intValue();
        rotationIndex = (rotationIndex + 1) % classicalCoreIds.size();
        return selected;
    }

    /** Medium-fit policy used by ABNE on the central core. */
    @Override
    public int[] policy(int numberOfSlots, List<int[]> freeSpectrumBands,
            Circuit circuit, ControlPlane cp) {
        int reference = circuit.getRoute().getLink(0).getCore(centralCoreId)
                .getNumOfSlots() / 2;
        int maxAmplitude = circuit.getPair().getSource().getTxs()
                .getMaxSpectralAmplitude();
        if (numberOfSlots > maxAmplitude) {
            return null;
        }

        int[] chosen = null;
        for (int[] band : freeSpectrumBands) {
            if (band[1] - band[0] + 1 >= numberOfSlots) {
                chosen = band.clone();
                chosen[1] = chosen[0] + numberOfSlots - 1;
                break;
            }
        }

        if (chosen == null) {
            return null;
        }

        for (int[] band : freeSpectrumBands) {
            for (int i = band[0]; i <= band[1]; i++) {
                if (Math.abs(reference - i) < Math.abs(reference - chosen[0])
                        && band[1] - i + 1 >= numberOfSlots) {
                    chosen[0] = i;
                    chosen[1] = i + numberOfSlots - 1;
                }
            }
        }
        return chosen;
    }

    /** Last-fit policy used by ABNE on even outer cores. */
    public int[] policyLastFit(int numberOfSlots,
            List<int[]> freeSpectrumBands, Circuit circuit) {
        int maxAmplitude = circuit.getPair().getSource().getTxs()
                .getMaxSpectralAmplitude();
        if (numberOfSlots > maxAmplitude) {
            return null;
        }

        for (int i = freeSpectrumBands.size() - 1; i >= 0; i--) {
            int[] band = freeSpectrumBands.get(i);
            if (band[1] - band[0] + 1 >= numberOfSlots) {
                int[] chosen = band.clone();
                chosen[0] = chosen[1] - numberOfSlots + 1;
                return chosen;
            }
        }
        return null;
    }

    /** First-fit policy used by ABNE on odd outer cores. */
    public int[] policyFirstFit(int numberOfSlots,
            List<int[]> freeSpectrumBands, Circuit circuit) {
        int maxAmplitude = circuit.getPair().getSource().getTxs()
                .getMaxSpectralAmplitude();
        if (numberOfSlots > maxAmplitude) {
            return null;
        }

        for (int[] band : freeSpectrumBands) {
            if (band[1] - band[0] + 1 >= numberOfSlots) {
                int[] chosen = band.clone();
                chosen[1] = chosen[0] + numberOfSlots - 1;
                return chosen;
            }
        }
        return null;
    }

    public List<Integer> getClassicalCoreIds() {
        return new ArrayList<Integer>(classicalCoreIds);
    }

    public int getCentralCoreId() {
        return centralCoreId;
    }

    private void ensureCoreLayout(Circuit circuit) {
        if (classicalCoreIds != null && !classicalCoreIds.isEmpty()) {
            return;
        }
        if (circuit == null || circuit.getRoute() == null
                || circuit.getRoute().getLinkList().isEmpty()) {
            throw new IllegalArgumentException(
                    "qABNE requires a circuit with a non-empty route");
        }

        Link firstLink = circuit.getRoute().getLink(0);
        List<Integer> available = new ArrayList<Integer>();
        for (Core core : firstLink.getCores()) {
            if (!core.isQkdReserved()) {
                available.add(Integer.valueOf(core.getId()));
            }
        }
        Collections.sort(available);
        this.classicalCoreIds = available;

        // Same physical convention used by the original CSBASDM/ABNE.
        this.centralCoreId = firstLink.getNumberOfCores() == 19 ? 18 : 0;
        this.rotationIndex = 0;
    }
}
