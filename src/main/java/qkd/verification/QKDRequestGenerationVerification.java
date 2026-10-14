package qkd.verification;

import com.google.gson.Gson;

import network.Node;
import network.Pair;
import qkd.network.QKDMeshFactory;
import qkd.traffic.QKDRequestGenerator;
import qkd.traffic.RequestProtectionType;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.SimulationConfig;
import simulationControl.parsers.TrafficConfig;
import util.RandGenerator;

/**
 * Verifies the revised Part 5/6 traffic architecture:
 * the legacy traffic JSON/parser remains unchanged and a dedicated
 * QKDRequestGenerator is used only when the QKD scenario is enabled.
 */
public final class QKDRequestGenerationVerification {

    private QKDRequestGenerationVerification() {
    }

    public static void main(String[] args) {
        verifyLegacyTrafficJsonIsStillValid();
        verifySimulationProbabilityParsing();
        verifyQkdGeneratorRatio();
        verifyQkdScenarioSelection();
        verifyBoundaries();
        System.out.println();
        System.out.println("QKD Part 5/6 request-generation verification: ALL CHECKS PASSED");
    }

    private static void verifyLegacyTrafficJsonIsStillValid() {
        String json = "{\"requestGenerators\":[{"
                + "\"source\":\"1\",\"destination\":\"2\"," 
                + "\"bitRate\":1.0E11,\"arrivalRate\":1.0,"
                + "\"holdRate\":1.0,\"arrivalRateIncrease\":0.5}]}";

        TrafficConfig traffic = new Gson().fromJson(json, TrafficConfig.class);
        check(traffic != null, "traffic parse returned null");
        check(traffic.getRequestGenerators().size() == 1,
                "legacy traffic requestGenerators were not parsed");

        System.out.println("[OK] original traffic JSON remains unchanged and valid");
    }

    private static void verifySimulationProbabilityParsing() {
        String json = "{\"qkdProtectedRequestProbability\":0.05}";
        SimulationConfig config = new Gson().fromJson(json, SimulationConfig.class);
        checkClose(config.getQkdProtectedRequestProbability(), 0.05, 1e-12,
                "simulation QKD request probability");
        System.out.println("[OK] simulation.qkdProtectedRequestProbability = 0.05 parsed");
    }

    private static void verifyQkdGeneratorRatio() {
        Node a = new Node("A");
        Node b = new Node("B");
        Pair pair = new Pair(a, b);

        QKDRequestGenerator generator = new QKDRequestGenerator(
                pair,
                100e9,
                1.0,
                1.0,
                0.0,
                new RandGenerator(20261001L),
                0.05);

        int qkd = 0;
        int classical = 0;
        final int total = 100000;

        for (int i = 0; i < total; i++) {
            RequestProtectionType type = generator.drawProtectionType();
            if (type == RequestProtectionType.QKD_PROTECTED) {
                qkd++;
            } else {
                classical++;
            }
        }

        double ratio = ((double) qkd) / total;
        check(Math.abs(ratio - 0.05) < 0.003,
                "QKD ratio too far from 5%: " + ratio);
        check(qkd + classical == total, "classification count mismatch");

        System.out.println("[OK] dedicated QKDRequestGenerator 100000-sample:"
                + " classical=" + classical
                + ", qkdProtected=" + qkd
                + ", qkdRatio=" + ratio);
    }

    private static void verifyQkdScenarioSelection() {
        NetworkConfig network = new NetworkConfig();
        check(!QKDMeshFactory.isQkdEnabled(network),
                "legacy network should not select QKDMesh");

        NetworkConfig.QKDConfig qkd = new NetworkConfig.QKDConfig(true, 25.0, 6);
        network.setQkd(qkd);
        check(QKDMeshFactory.isQkdEnabled(network),
                "QKD-enabled network should select QKDMesh");

        System.out.println("[OK] legacy scenario -> Mesh; QKD scenario -> QKDMesh");
    }

    private static void verifyBoundaries() {
        Node a = new Node("A");
        Node b = new Node("B");
        Pair pair = new Pair(a, b);

        QKDRequestGenerator zero = new QKDRequestGenerator(
                pair, 100e9, 1.0, 1.0, 0.0,
                new RandGenerator(1L), 0.0);
        for (int i = 0; i < 1000; i++) {
            check(zero.drawProtectionType() == RequestProtectionType.CLASSICAL,
                    "0% policy generated protected request");
        }

        QKDRequestGenerator one = new QKDRequestGenerator(
                pair, 100e9, 1.0, 1.0, 0.0,
                new RandGenerator(1L), 1.0);
        for (int i = 0; i < 1000; i++) {
            check(one.drawProtectionType() == RequestProtectionType.QKD_PROTECTED,
                    "100% policy generated classical request");
        }

        System.out.println("[OK] 0% and 100% QKD generator boundaries");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void checkClose(double actual, double expected,
            double tolerance, String label) {
        if (Math.abs(actual - expected) > tolerance) {
            throw new AssertionError(label + ": expected=" + expected
                    + ", actual=" + actual);
        }
    }
}
