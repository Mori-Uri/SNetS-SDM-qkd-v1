package qkd.verification;

import qkd.physical.BB84DecoyModel;
import qkd.physical.QKDPhysicalLayer;
import qkd.physical.QKDCrosstalkNoiseResult;
import qkd.physical.QKDPhysicalParameters;
import qkd.physical.QKDPhysicalResult;

/**
 * Standalone deterministic verification for Part 1 of the QKD integration.
 *
 * <p>The reference checks are intentionally independent of the SNetS event
 * engine. The goal is to verify the mathematical implementation before any
 * change is made to routing, ControlPlane, request generation, or topology.</p>
 */
public final class QKDPhysicalVerification {

    private static final double PYTHON_SKR_1550NM_25KM_MBPS = 93.9393733752688;

    private QKDPhysicalVerification() {
        // Utility class.
    }

    public static void main(String[] args) {
        final QKDPhysicalParameters parameters = new QKDPhysicalParameters();
        final BB84DecoyModel bb84 = new BB84DecoyModel(parameters);
        final QKDPhysicalLayer xtNoise = new QKDPhysicalLayer(parameters);

        verifyAttenuationReference(bb84);
        verifyPythonSkrReference(bb84);
        verifyAdditionalPythonReferencePoints(bb84);
        verifyQtndGridReference(bb84);
        verifyZeroXtReturnsMultiFiberBaseline(bb84, xtNoise);
        verifyXtDegradesSkr(bb84, xtNoise);

        System.out.println("QKD Part 1 verification: ALL CHECKS PASSED");
    }

    private static void verifyAttenuationReference(BB84DecoyModel bb84) {
        final double alpha = bb84.attenuationDbPerKm(1550.0);
        assertClose("alpha(1550 nm)", 0.18, alpha, 1e-12);
        System.out.println("[OK] alpha(1550 nm) = " + alpha + " dB/km");
    }

    private static void verifyPythonSkrReference(BB84DecoyModel bb84) {
        final QKDPhysicalResult result = bb84.evaluateMultiFiber(1550.0, 25.0);
        assertClose(
                "SKR(1550 nm, 25 km)",
                PYTHON_SKR_1550NM_25KM_MBPS,
                result.getSecretKeyRateMbps(),
                1e-6
        );
        System.out.println("[OK] Python reference SKR = "
                + result.getSecretKeyRateMbps() + " Mbit/s");
    }


    private static void verifyAdditionalPythonReferencePoints(BB84DecoyModel bb84) {
        assertClose("SKR(1550 nm, 1 km)",
                260.09569635955154,
                bb84.evaluateMultiFiber(1550.0, 1.0).getSecretKeyRateMbps(),
                1e-6);
        assertClose("SKR(1550 nm, 40 km)",
                50.10196362457788,
                bb84.evaluateMultiFiber(1550.0, 40.0).getSecretKeyRateMbps(),
                1e-6);
        assertClose("SKR(1530 nm, 20 km)",
                114.23266639105485,
                bb84.evaluateMultiFiber(1530.0, 20.0).getSecretKeyRateMbps(),
                1e-6);
        assertClose("SKR(1600 nm, 30 km)",
                66.21709704618883,
                bb84.evaluateMultiFiber(1600.0, 30.0).getSecretKeyRateMbps(),
                1e-6);
        System.out.println("[OK] additional Python physical-layer reference points");
    }

    private static void verifyQtndGridReference(BB84DecoyModel bb84) {
        final double totalLinkKm = 40.0;
        double bestQtndKm = -1.0;
        double bestMetric = Double.NEGATIVE_INFINITY;

        for (double qtndKm = 5.0; qtndKm <= 40.0; qtndKm += 5.0) {
            final double skrMbps = bb84.evaluateMultiFiber(1550.0, qtndKm)
                    .getSecretKeyRateMbps();
            final double skrPerQtrx = skrMbps * qtndKm / totalLinkKm;
            if (skrPerQtrx > bestMetric) {
                bestMetric = skrPerQtrx;
                bestQtndKm = qtndKm;
            }
        }

        if (bestQtndKm != 25.0) {
            throw new IllegalStateException(
                    "Expected QTND optimum of 25 km on the 5-km grid, got " + bestQtndKm);
        }
        System.out.println("[OK] QTND reference optimum on 5-km grid = 25 km");
    }

    private static void verifyZeroXtReturnsMultiFiberBaseline(
            BB84DecoyModel bb84,
            QKDPhysicalLayer xtNoise) {

        final QKDCrosstalkNoiseResult noise = xtNoise.evaluateFromReceivedPower(
                0.0,
                1550.0,
                1.0,
                100e-12
        );
        final QKDPhysicalResult baseline = bb84.evaluateMultiFiber(1550.0, 25.0);
        final QKDPhysicalResult withZeroXt = bb84.evaluate(
                1550.0,
                25.0,
                noise.getTotalBackgroundProbability()
        );

        assertClose("pXT with zero power", 0.0,
                noise.getCrosstalkBackgroundProbability(), 0.0);
        assertClose("pb with zero XT", new QKDPhysicalParameters().getDarkCountProbability(),
                noise.getTotalBackgroundProbability(), 1e-18);
        assertClose("SKR baseline vs zero XT",
                baseline.getSecretKeyRateMbps(),
                withZeroXt.getSecretKeyRateMbps(),
                1e-9);
        System.out.println("[OK] zero XT reproduces the multi-fiber baseline");
    }

    private static void verifyXtDegradesSkr(
            BB84DecoyModel bb84,
            QKDPhysicalLayer xtNoise) {

        final QKDPhysicalResult baseline = bb84.evaluateMultiFiber(1550.0, 25.0);

        // Small deterministic XT power chosen only to exercise the conversion
        // chain. It is not a calibrated network operating point.
        final double receivedXtPowerW = 1e-13;
        final QKDCrosstalkNoiseResult noise = xtNoise.evaluateFromReceivedPower(
                receivedXtPowerW,
                1550.0,
                1.0,
                100e-12
        );
        final QKDPhysicalResult degraded = bb84.evaluate(
                1550.0,
                25.0,
                noise.getTotalBackgroundProbability()
        );

        if (!(noise.getCrosstalkBackgroundProbability() > 0.0)) {
            throw new IllegalStateException("Expected positive pXT for positive XT power");
        }
        if (!(degraded.getQber() > baseline.getQber())) {
            throw new IllegalStateException("Expected XT to increase QBER");
        }
        if (!(degraded.getSecretKeyRateMbps() < baseline.getSecretKeyRateMbps())) {
            throw new IllegalStateException("Expected XT to reduce SKR");
        }

        System.out.println("[OK] XT chain: PxtRx=" + receivedXtPowerW
                + " W -> pXT=" + noise.getCrosstalkBackgroundProbability()
                + " -> QBER=" + degraded.getQber()
                + " -> SKR=" + degraded.getSecretKeyRateMbps() + " Mbit/s");
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
