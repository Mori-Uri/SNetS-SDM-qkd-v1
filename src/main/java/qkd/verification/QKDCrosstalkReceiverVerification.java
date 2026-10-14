package qkd.verification;

import com.google.gson.Gson;

import qkd.physical.BB84DecoyModel;
import qkd.physical.QKDPhysicalLayer;
import qkd.physical.QKDCrosstalkNoiseResult;
import qkd.physical.QKDCrosstalkReceiverTransferMode;
import qkd.physical.QKDPhysicalParameters;
import qkd.physical.QKDPhysicalResult;
import simulationControl.parsers.PhysicalLayerConfig;

/**
 * Part 11.5 verification of the receiver-side bridge
 * PXT -> Nbar_XT -> pXT -> pb -> QBER -> SKR.
 *
 * <p>This verifier intentionally separates implementation verification from
 * physical validation. It checks equations, units, monotonic behavior,
 * configuration plumbing and a sensitivity reference for receiver transfer.
 * It does not claim that Tg=100 ps, eta_f=1 or a specific transfer model is a
 * calibrated representation of a real QKD receiver.</p>
 */
public final class QKDCrosstalkReceiverVerification {

    private static final double WAVELENGTH_NM = 1550.0;
    private static final double XT_REFERENCE_POWER_W = 1.0e-13;

    private QKDCrosstalkReceiverVerification() {
    }

    public static void main(String[] args) {
        verifyScenarioReceiverConfiguration();
        verifyZeroXtRecoversReferenceBaseline();
        verifyPhotonAndPoissonChainNumerically();
        verifyExactIndependentBackgroundCombination();
        verifyReceiverParameterMonotonicity();
        verifyDistributedReceiverTransferReference();
        verifyTransferModeParsing();

        System.out.println();
        System.out.println("QKD Part 11.5 receiver-model verification: ALL CHECKS PASSED");
        System.out.println("NOTE: equation/implementation verification passed; receiver calibration remains an experimental-literature assumption to document.");
    }

    private static void verifyScenarioReceiverConfiguration() {
        String json = "{"
                + "\"qkdReceiver\":{"
                + "\"detectorGateWindowPs\":100.0,"
                + "\"receiverOpticalEfficiency\":1.0,"
                + "\"receiverTransferModel\":\"receiver_referenced\"}"
                + "}";

        PhysicalLayerConfig config = new Gson().fromJson(
                json, PhysicalLayerConfig.class);
        PhysicalLayerConfig.QKDReceiverConfig receiver = config.getQkdReceiver();

        checkClose(receiver.getDetectorGateWindowPs(), 100.0, 0.0,
                "detectorGateWindowPs");
        checkClose(receiver.getDetectorGateWindowS(), 100e-12, 1e-24,
                "detectorGateWindowS");
        checkClose(receiver.getReceiverOpticalEfficiency(), 1.0, 0.0,
                "receiverOpticalEfficiency");
        check("receiver_referenced".equals(receiver.getReceiverTransferModel()),
                "receiverTransferModel was not parsed");

        QKDPhysicalParameters parameters = new QKDPhysicalParameters()
                .withReceiverAssumptions(
                        receiver.getDetectorGateWindowS(),
                        receiver.getReceiverOpticalEfficiency(),
                        QKDCrosstalkReceiverTransferMode.fromConfig(
                                receiver.getReceiverTransferModel()));

        check(parameters.getReceiverTransferMode()
                        == QKDCrosstalkReceiverTransferMode.RECEIVER_REFERENCED,
                "scenario receiver transfer mode did not reach QKD parameters");

        System.out.println("[OK] physicalLayer.qkdReceiver is explicit and parsable:"
                + " Tg=100 ps, eta_f=1.0, mode=receiver_referenced");
    }

    private static void verifyZeroXtRecoversReferenceBaseline() {
        QKDPhysicalParameters parameters = new QKDPhysicalParameters();
        QKDPhysicalLayer noiseModel = new QKDPhysicalLayer(parameters);
        BB84DecoyModel bb84 = new BB84DecoyModel(parameters);

        QKDCrosstalkNoiseResult noise = noiseModel.evaluateFromReceivedPower(
                0.0,
                WAVELENGTH_NM,
                parameters.getReceiverOpticalEfficiency(),
                parameters.getDetectorGateWindowS());

        QKDPhysicalResult baseline = bb84.evaluateMultiFiber(WAVELENGTH_NM, 25.0);
        QKDPhysicalResult withXtBridge = bb84.evaluate(
                WAVELENGTH_NM,
                25.0,
                noise.getTotalBackgroundProbability());

        checkClose(noise.getCrosstalkBackgroundProbability(), 0.0, 0.0,
                "pXT at zero PXT");
        checkClose(noise.getTotalBackgroundProbability(),
                parameters.getDarkCountProbability(), 1e-18,
                "pb at zero PXT");
        checkClose(withXtBridge.getSecretKeyRateMbps(),
                baseline.getSecretKeyRateMbps(), 1e-9,
                "zero-XT SKR baseline");

        System.out.println("[OK] PXT=0 -> pXT=0 -> pb=pdark -> reference BB84 SKR is unchanged");
    }

    private static void verifyPhotonAndPoissonChainNumerically() {
        QKDPhysicalParameters parameters = new QKDPhysicalParameters();
        QKDPhysicalLayer model = new QKDPhysicalLayer(parameters);

        QKDCrosstalkNoiseResult result = model.evaluateFromReceivedPower(
                XT_REFERENCE_POWER_W,
                WAVELENGTH_NM,
                1.0,
                100e-12);

        double wavelengthM = WAVELENGTH_NM * 1e-9;
        double photonFrequency = parameters.getSpeedOfLightMPerS() / wavelengthM;
        double photonEnergy = parameters.getPlanckConstantJs() * photonFrequency;
        double expectedMean = parameters.getDetectorEfficiency()
                * XT_REFERENCE_POWER_W * 100e-12 / photonEnergy;
        double expectedPxt = -Math.expm1(-expectedMean);
        double pDark = parameters.getDarkCountProbability();
        double expectedPb = 1.0 - (1.0 - pDark) * (1.0 - expectedPxt);

        checkClose(result.getPhotonEnergyJ(), photonEnergy, 1e-30,
                "photon energy");
        checkClose(result.getMeanDetectedXtEvents(), expectedMean, 1e-16,
                "mean XT events");
        checkClose(result.getCrosstalkBackgroundProbability(), expectedPxt, 1e-16,
                "Poisson pXT");
        checkClose(result.getTotalBackgroundProbability(), expectedPb, 1e-16,
                "exact pb combination");

        System.out.println("[OK] numerical receiver chain: PXT="
                + XT_REFERENCE_POWER_W
                + " W -> NbarXT=" + result.getMeanDetectedXtEvents()
                + " -> pXT=" + result.getCrosstalkBackgroundProbability()
                + " -> pb=" + result.getTotalBackgroundProbability());
    }

    private static void verifyExactIndependentBackgroundCombination() {
        double pDark = 1.0e-3;
        double pXt = 2.0e-3;
        double exact = QKDPhysicalLayer.combineIndependentProbabilities(
                pDark, pXt);
        double expected = pDark + pXt - pDark * pXt;

        checkClose(exact, expected, 1e-18, "independent background combination");
        check(exact < pDark + pXt,
                "exact independent combination must include the overlap correction");

        System.out.println("[OK] pb uses exact independent-event combination, not only pdark+pXT");
    }

    private static void verifyReceiverParameterMonotonicity() {
        QKDPhysicalParameters parameters = new QKDPhysicalParameters();
        QKDPhysicalLayer model = new QKDPhysicalLayer(parameters);

        QKDCrosstalkNoiseResult base = model.evaluateFromReceivedPower(
                XT_REFERENCE_POWER_W, WAVELENGTH_NM, 1.0, 100e-12);
        QKDCrosstalkNoiseResult halfGate = model.evaluateFromReceivedPower(
                XT_REFERENCE_POWER_W, WAVELENGTH_NM, 1.0, 50e-12);
        QKDCrosstalkNoiseResult halfEfficiency = model.evaluateFromReceivedPower(
                XT_REFERENCE_POWER_W, WAVELENGTH_NM, 0.5, 100e-12);
        QKDCrosstalkNoiseResult doublePower = model.evaluateFromReceivedPower(
                2.0 * XT_REFERENCE_POWER_W, WAVELENGTH_NM, 1.0, 100e-12);

        check(halfGate.getCrosstalkBackgroundProbability()
                        < base.getCrosstalkBackgroundProbability(),
                "smaller Tg must reduce pXT");
        check(halfEfficiency.getCrosstalkBackgroundProbability()
                        < base.getCrosstalkBackgroundProbability(),
                "smaller eta_f must reduce pXT");
        check(doublePower.getCrosstalkBackgroundProbability()
                        > base.getCrosstalkBackgroundProbability(),
                "larger PXT must increase pXT");

        checkClose(halfGate.getMeanDetectedXtEvents(),
                0.5 * base.getMeanDetectedXtEvents(), 1e-16,
                "Tg linearity of NbarXT");
        checkClose(halfEfficiency.getMeanDetectedXtEvents(),
                0.5 * base.getMeanDetectedXtEvents(), 1e-16,
                "eta_f linearity of NbarXT");
        checkClose(doublePower.getMeanDetectedXtEvents(),
                2.0 * base.getMeanDetectedXtEvents(), 1e-16,
                "PXT linearity of NbarXT");

        System.out.println("[OK] receiver sensitivities are monotonic: PXT up -> pXT up; Tg/eta_f down -> pXT down");
    }

    private static void verifyDistributedReceiverTransferReference() {
        double lengthKm = 25.0;
        double alphaDbPerKm = 0.18;
        double generatedPowerW = 2.5e-14;

        double attenuationNepersPerKm = alphaDbPerKm * Math.log(10.0) / 10.0;
        double expectedEffectiveLengthKm =
                -Math.expm1(-attenuationNepersPerKm * lengthKm)
                / attenuationNepersPerKm;
        double expectedFactor = expectedEffectiveLengthKm / lengthKm;

        double actualEffectiveLengthKm =
                QKDPhysicalLayer.distributedEffectiveLengthKm(
                        lengthKm, alphaDbPerKm);
        double actualFactor =
                QKDPhysicalLayer.distributedTransferFactor(
                        lengthKm, alphaDbPerKm);

        double receiverReferenced = QKDPhysicalLayer.toReceiverPowerW(
                generatedPowerW,
                lengthKm,
                alphaDbPerKm,
                QKDCrosstalkReceiverTransferMode.RECEIVER_REFERENCED);
        double distributed = QKDPhysicalLayer.toReceiverPowerW(
                generatedPowerW,
                lengthKm,
                alphaDbPerKm,
                QKDCrosstalkReceiverTransferMode.DISTRIBUTED_QUANTUM_ATTENUATION_REFERENCE);

        checkClose(actualEffectiveLengthKm, expectedEffectiveLengthKm, 1e-12,
                "distributed effective length");
        checkClose(actualFactor, expectedFactor, 1e-14,
                "distributed transfer factor");
        checkClose(receiverReferenced, generatedPowerW, 0.0,
                "receiver-referenced transfer");
        checkClose(distributed, generatedPowerW * expectedFactor, 1e-28,
                "distributed received power");
        check(distributed < receiverReferenced,
                "distributed reference should attenuate generated XT for alpha>0");

        System.out.println("[OK] distributed sensitivity reference for 25 km / 0.18 dB/km:"
                + " Leff=" + actualEffectiveLengthKm + " km"
                + ", factor=" + actualFactor
                + ", PXT_Rx=" + distributed + " W"
                + " (vs " + receiverReferenced + " W with T=1)");
    }

    private static void verifyTransferModeParsing() {
        check(QKDCrosstalkReceiverTransferMode.fromConfig("receiver_referenced")
                        == QKDCrosstalkReceiverTransferMode.RECEIVER_REFERENCED,
                "receiver_referenced parse failed");
        check(QKDCrosstalkReceiverTransferMode.fromConfig("distributed")
                        == QKDCrosstalkReceiverTransferMode.DISTRIBUTED_QUANTUM_ATTENUATION_REFERENCE,
                "distributed parse failed");

        boolean rejected = false;
        try {
            QKDCrosstalkReceiverTransferMode.fromConfig("unknown-model");
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "unknown transfer mode must be rejected");

        System.out.println("[OK] receiver transfer modes are explicit; unknown modes are rejected");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static void checkClose(
            double actual,
            double expected,
            double tolerance,
            String name) {
        if (Double.doubleToLongBits(actual) == Double.doubleToLongBits(expected)) {
            return;
        }
        if (Math.abs(actual - expected) > tolerance) {
            throw new IllegalStateException(name + ": expected=" + expected
                    + ", actual=" + actual + ", tolerance=" + tolerance);
        }
    }
}
