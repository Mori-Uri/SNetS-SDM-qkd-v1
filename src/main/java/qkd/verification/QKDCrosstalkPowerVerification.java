package qkd.verification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import network.Circuit;
import network.Link;
import network.Oxc;
import qkd.physical.BB84DecoyModel;
import qkd.physical.QKDCrosstalkNoiseResult;
import qkd.physical.QKDPhysicalLayer;
import qkd.physical.QKDCrosstalkPowerResult;
import qkd.physical.QKDPhysicalParameters;
import qkd.physical.QKDPhysicalResult;
import simulationControl.parsers.NetworkConfig.CoreConfig;

/**
 * Standalone deterministic verification for Part 2 of the QKD integration.
 *
 * <p>This test uses real SNetS {@link Link}, {@code Core}, and {@link Circuit}
 * objects. It creates a small four-core link, places classical circuits in
 * adjacent and non-adjacent cores, and verifies that the QKD bridge:</p>
 *
 * <ol>
 *   <li>reads only circuits in cores adjacent to the reserved QKD core;</li>
 *   <li>uses the same inclusive spectral-overlap/ISO definition as SNetS;</li>
 *   <li>reproduces P_XT = sum(P_n * ISO * h * L); and</li>
 *   <li>feeds that absolute power into the Part-1 XT -> QBER -> SKR chain.</li>
 * </ol>
 *
 * <p>No SNetS request generator, ControlPlane, routing algorithm, or scenario
 * file is modified in Part 2.</p>
 */
public final class QKDCrosstalkPowerVerification {

    private static final int QKD_CORE_ID = 0;
    private static final int[] QKD_BAND = new int[] {10, 19};
    private static final double LINK_DISTANCE_KM = 50.0;
    private static final double H_PER_M = 1e-15;
    private static final double EXPECTED_PXT_W = 1e-13;

    private QKDCrosstalkPowerVerification() {
        // Utility class.
    }

    public static void main(String[] args) {
        verifyOverlapDefinition();

        final Link link = buildControlledLink();
        final QKDPhysicalLayer calculator =
                new QKDPhysicalLayer();

        final QKDCrosstalkPowerResult power =
                calculator.evaluateLinkUsingStoredLaunchPowers(
                        link,
                        QKD_CORE_ID,
                        QKD_BAND,
                        H_PER_M);

        verifySNetSLinkTraversal(power);
        verifyExpectedAbsolutePower(power);
        verifyPart1PhysicalChain(power);

        System.out.println("QKD Part 2 verification: ALL CHECKS PASSED");
    }

    private static void verifyOverlapDefinition() {
        final int[] fullOverlap = new int[] {10, 19};
        final int[] partialOverlap = new int[] {15, 24};
        final int[] noOverlap = new int[] {30, 39};

        assertEquals("full-overlap slots", 10,
                QKDPhysicalLayer.numberOfOverlappingSlots(
                        QKD_BAND, fullOverlap));
        assertClose("full-overlap ISO", 1.0,
                QKDPhysicalLayer.calculateOverlapIndex(
                        QKD_BAND, fullOverlap), 0.0);

        assertEquals("partial-overlap slots", 5,
                QKDPhysicalLayer.numberOfOverlappingSlots(
                        QKD_BAND, partialOverlap));
        assertClose("partial-overlap ISO", 0.5,
                QKDPhysicalLayer.calculateOverlapIndex(
                        QKD_BAND, partialOverlap), 0.0);

        assertEquals("no-overlap slots", 0,
                QKDPhysicalLayer.numberOfOverlappingSlots(
                        QKD_BAND, noOverlap));
        assertClose("no-overlap ISO", 0.0,
                QKDPhysicalLayer.calculateOverlapIndex(
                        QKD_BAND, noOverlap), 0.0);

        System.out.println("[OK] SNetS spectral-overlap / ISO definition");
    }

    private static Link buildControlledLink() {
        /*
         * Core topology used only by this deterministic test:
         *
         *     core 1
         *       |
         *     core 0 (QKD) -- core 2
         *
         *     core 3 is deliberately NOT adjacent to core 0.
         */
        final List<CoreConfig> cores = new ArrayList<CoreConfig>();
        cores.add(new CoreConfig(0, Arrays.asList(1, 2)));
        cores.add(new CoreConfig(1, Collections.singletonList(0)));
        cores.add(new CoreConfig(2, Collections.singletonList(0)));
        cores.add(new CoreConfig(3, Collections.<Integer>emptyList()));

        final Oxc source = new Oxc("A");
        final Oxc destination = new Oxc("B");
        final Link link = new Link(
                source,
                destination,
                100,        // slots per core
                12.5,       // slot spectral width; not used by this XT test
                LINK_DISTANCE_KM,
                cores);

        // Adjacent core 1: 100% overlap, P = 1 mW.
        link.getCore(1).addCircuit(createInterferer(
                1,
                new int[] {10, 19},
                1e-3));

        // Adjacent core 2: 5/10 slots overlap, P = 2 mW.
        link.getCore(2).addCircuit(createInterferer(
                2,
                new int[] {15, 24},
                2e-3));

        // Adjacent core 1 but no spectrum overlap: must contribute zero.
        link.getCore(1).addCircuit(createInterferer(
                1,
                new int[] {30, 39},
                5e-4));

        // Same spectrum, high power, but NON-adjacent core 3: must be ignored.
        link.getCore(3).addCircuit(createInterferer(
                3,
                new int[] {10, 19},
                1.0));

        return link;
    }

    private static Circuit createInterferer(
            int coreId,
            int[] spectrum,
            double launchPowerW) {
        final Circuit circuit = new Circuit();
        circuit.setIndexCore(coreId);
        circuit.setSpectrumAssigned(spectrum);
        circuit.setLaunchPowerLinear(launchPowerW);
        return circuit;
    }

    private static void verifySNetSLinkTraversal(QKDCrosstalkPowerResult power) {
        assertEquals("adjacent cores examined", 2, power.getAdjacentCoresExamined());
        assertEquals("circuits examined", 3, power.getCircuitsExamined());
        assertEquals("overlapping circuits", 2, power.getOverlappingCircuits());
        assertEquals("stored contributions", 2, power.getContributions().size());

        System.out.println("[OK] live SNetS Link/Core/Circuit traversal: "
                + power.getAdjacentCoresExamined() + " adjacent cores, "
                + power.getCircuitsExamined() + " classical circuits examined, "
                + power.getOverlappingCircuits() + " overlapping interferers");
    }

    private static void verifyExpectedAbsolutePower(QKDCrosstalkPowerResult power) {
        /*
         * Manual calculation:
         *
         * interferer 1: 1e-3 W * 1.0 = 1e-3 W weighted
         * interferer 2: 2e-3 W * 0.5 = 1e-3 W weighted
         * total weighted classical power = 2e-3 W
         *
         * PXT = 2e-3 * 1e-15 [1/m] * 50,000 [m]
         *     = 1e-13 W.
         */
        assertClose("absolute PXT", EXPECTED_PXT_W, power.getTotalPowerW(), 1e-25);

        System.out.println("[OK] absolute inter-core XT power = "
                + power.getTotalPowerW() + " W");
        for (int i = 0; i < power.getContributions().size(); i++) {
            System.out.println("     contribution[" + i + "] = "
                    + power.getContributions().get(i));
        }
    }

    private static void verifyPart1PhysicalChain(QKDCrosstalkPowerResult power) {
        final QKDPhysicalParameters parameters = new QKDPhysicalParameters();
        final QKDPhysicalLayer noiseModel =
                new QKDPhysicalLayer(parameters);
        final BB84DecoyModel bb84 = new BB84DecoyModel(parameters);

        /*
         * In Part 2 we intentionally use transferToReceiver = 1.0. This means
         * the generated link PXT is treated as already referred to the QKD
         * receiver for this controlled verification. Explicit propagation of
         * each link contribution to Bob will be introduced only when QKD spans
         * and QTND are represented in later stages.
         */
        final QKDCrosstalkNoiseResult noise =
                noiseModel.evaluateFromLinkContribution(
                        power.getTotalPowerW(),
                        1.0,
                        1550.0,
                        parameters.getReceiverOpticalEfficiency(),
                        parameters.getDetectorGateWindowS());

        final QKDPhysicalResult baseline = bb84.evaluateMultiFiber(1550.0, 25.0);
        final QKDPhysicalResult degraded = bb84.evaluate(
                1550.0,
                25.0,
                noise.getTotalBackgroundProbability());

        assertClose("pXT reference",
                2.340836805895038e-5,
                noise.getCrosstalkBackgroundProbability(),
                1e-16);
        assertClose("degraded SKR reference",
                93.54339669078391,
                degraded.getSecretKeyRateMbps(),
                1e-9);

        if (!(degraded.getQber() > baseline.getQber())) {
            throw new IllegalStateException("Expected PXT to increase QBER");
        }
        if (!(degraded.getSecretKeyRateMbps() < baseline.getSecretKeyRateMbps())) {
            throw new IllegalStateException("Expected PXT to reduce SKR");
        }

        System.out.println("[OK] SNetS PXT -> detector noise -> BB84 chain:"
                + " pXT=" + noise.getCrosstalkBackgroundProbability()
                + ", QBER=" + degraded.getQber()
                + ", SKR=" + degraded.getSecretKeyRateMbps() + " Mbit/s");
    }

    private static void assertEquals(String name, int expected, int actual) {
        if (expected != actual) {
            throw new IllegalStateException(
                    name + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertClose(
            String name,
            double expected,
            double actual,
            double tolerance) {
        if (Double.doubleToLongBits(expected) == Double.doubleToLongBits(actual)) {
            return;
        }
        if (Math.abs(expected - actual) > tolerance) {
            throw new IllegalStateException(name + ": expected=" + expected
                    + ", actual=" + actual + ", tolerance=" + tolerance);
        }
    }
}
