package qkd.verification;

import java.util.HashMap;

import com.google.gson.Gson;

import measurement.Measurements;
import network.ControlPlane;
import network.Mesh;
import qkd.control.QKDControlPlane;
import qkd.network.QKDMesh;
import qkd.network.QKDMeshFactory;
import qkd.metrics.QKDMetricsSnapshot;
import simulationControl.Util;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.OthersConfig;
import simulationControl.parsers.PhysicalLayerConfig;
import simulationControl.parsers.SimulationConfig;
import simulationControl.parsers.TrafficConfig;
import simulator.Simulation;
import simulator.Simulator;

/**
 * Part 9 verification: QKD resources now follow the real SNetS event cycle.
 *
 * <p>The fixture is deliberately small and uses a very low coupling
 * coefficient so the purpose of this test is lifecycle behavior rather than
 * stressing the coexistence model already verified in Parts 2 and 7.</p>
 */
public final class QKDMetricsVerification {

    private QKDMetricsVerification() {
    }

    public static void main(String[] args) throws Exception {
        Gson gson = new Gson();

        NetworkConfig network = gson.fromJson(networkJson(), NetworkConfig.class);
        TrafficConfig traffic = gson.fromJson(trafficJson(), TrafficConfig.class);
        PhysicalLayerConfig physical = gson.fromJson(
                physicalLayerJson(), PhysicalLayerConfig.class);
        OthersConfig others = new OthersConfig();
        SimulationConfig simulationConfig = gson.fromJson(
                simulationJson(), SimulationConfig.class);

        Util util = new Util();
        util.projectPath = ".";

        Mesh mesh = QKDMeshFactory.create(
                network,
                traffic,
                physical,
                others,
                new HashMap<String, HashMap<Double, Double>>(),
                util,
                simulationConfig);

        check(mesh instanceof QKDMesh,
                "QKD-enabled network must create QKDMesh");
        System.out.println("[OK] QKD-enabled scenario creates QKDMesh");

        Simulation simulation = new Simulation(
                simulationConfig, mesh, 0, 0, util);
        ControlPlane genericControlPlane = simulation.getControlPlane();

        check(genericControlPlane instanceof QKDControlPlane,
                "QKDMesh must select QKDControlPlane");
        QKDControlPlane controlPlane = (QKDControlPlane) genericControlPlane;
        System.out.println("[OK] QKDMesh selects QKDControlPlane without changing the legacy ControlPlane class");

        Measurements measurements = new Simulator(simulation).start();
        check(measurements != null, "simulator did not return measurements");

        check(controlPlane.getQkdProtectedAttempts() > 0,
                "expected QKD-protected requests in the dynamic event cycle");
        check(controlPlane.getQkdAccepted() > 0,
                "expected at least one admitted QKD session");
        check(controlPlane.getQkdReleased() == controlPlane.getQkdAccepted(),
                "every admitted QKD session must be released by its hold event");
        check(controlPlane.getActiveQkdSessionCount() == 0,
                "event machine ended with leaked active QKD sessions");
        check(controlPlane.getQkdSessionRegistry().isEmpty(),
                "QKD session registry must be empty after all departures");

        QKDMetricsSnapshot stats = controlPlane.snapshotQkdMetrics(0, 0);
        check(stats.protectedAttempts == controlPlane.getQkdProtectedAttempts(),
                "snapshot protected-attempt counter mismatch");
        check(stats.accepted > 0 && stats.released == stats.accepted,
                "snapshot admission/release counters invalid");
        check(stats.peakActiveQchs > 0, "QKD metrics did not capture active QCh peak");
        check(stats.peakQTrxPairs > 0, "QKD metrics did not capture Q-TRx peak");
        check(stats.activatedQchs == stats.deactivatedQchs,
                "dynamic run ended with unmatched QCh activation/deactivation");
        check(stats.meanAdmissionRawSkrMbps > 0.0,
                "QKD metrics did not capture admission SKR");
        System.out.println("[OK] QKD metrics snapshot captures attempts/acceptance/release");
        System.out.println("[OK] peak discrete resources: QCh=" + stats.peakActiveQchs
                + ", QTRxPairs=" + stats.peakQTrxPairs);
        System.out.println("[OK] admission physics: meanSKR=" + stats.meanAdmissionRawSkrMbps
                + " Mbit/s, maxQBER=" + stats.maximumAdmissionQber
                + ", maxPXT=" + stats.maximumAdmissionPxtW + " W");
        System.out.println("\nQKD Part 14 metrics verification: ALL CHECKS PASSED");
    }

    private static String networkJson() {
        return "{"
                + "\"nodes\":["
                + "{\"name\":\"1\",\"transmitters\":20,\"receivers\":20,\"regenerators\":0},"
                + "{\"name\":\"2\",\"transmitters\":20,\"receivers\":20,\"regenerators\":0}],"
                + "\"links\":["
                + "{\"source\":\"1\",\"destination\":\"2\",\"slots\":40,\"spectrum\":1.25E10,\"size\":10.0},"
                + "{\"source\":\"2\",\"destination\":\"1\",\"slots\":40,\"spectrum\":1.25E10,\"size\":10.0}],"
                + "\"modulations\":["
                + "{\"name\":\"4QAM\",\"maxRange\":5000.0,\"M\":4.0,\"SNR\":8.95,\"XT\":-19.03}],"
                + "\"cores\":["
                + "{\"id\":0,\"adjacentCores\":[1]},"
                + "{\"id\":1,\"adjacentCores\":[0]}],"
                + "\"guardBand\":1,\"bvtSpectralAmplitude\":1000,"
                + "\"qkd\":{\"enabled\":true,\"qtndKm\":25.0,\"quantumCoreId\":0}"
                + "}";
    }

    private static String trafficJson() {
        return "{\"requestGenerators\":["
                + "{\"source\":\"1\",\"destination\":\"2\","
                + "\"bitRate\":1.0E10,\"arrivalRate\":1.0,"
                + "\"holdRate\":100.0,\"arrivalRateIncrease\":0.0}]}";
    }

    private static String simulationJson() {
        return "{"
                + "\"requests\":8,"
                + "\"rmlsaType\":0,"
                + "\"routing\":\"djk\","
                + "\"kRouting\":\"ksp\","
                + "\"spectrumAssignment\":\"firstfit\","
                + "\"coreAndSpectrumAssignment\":\"qabne\","
                + "\"integratedRmlsa\":\"completesharing\","
                + "\"modulationSelection\":\"modulationbydistance\","
                + "\"grooming\":\"notrafficgrooming\","
                + "\"reallocation\":\"fsalfav1\","
                + "\"powerAssignment\":\"cpsd\","
                + "\"loadPoints\":1,\"replications\":1,"
                + "\"activeMetrics\":{"
                + "\"BlockingProbability\":false,"
                + "\"BitRateBlockingProbability\":false,"
                + "\"SpectrumUtilization\":false,"
                + "\"SpectrumSizeStatistics\":false,"
                + "\"ExternalFragmentation\":false,"
                + "\"RelativeFragmentation\":false,"
                + "\"TransmittersReceiversRegeneratorsUtilization\":false,"
                + "\"ModulationUtilization\":false,"
                + "\"ConsumedEnergy\":false,"
                + "\"GroomingStatistics\":false,"
                + "\"DataSetInformation\":false,"
                + "\"PhysicalLayerStatistics\":false},"
                + "\"regeneratorAssignment\":\"aar\","
                + "\"networkType\":0,\"threads\":1,"
                + "\"qkdProtectedRequestProbability\":1.0"
                + "}";
    }

    private static String physicalLayerJson() {
        return "{"
                + "\"physicalLayerModel\":0,\"crosstalkModel\":0,"
                + "\"activeQoT\":false,\"activeQoTForOther\":false,"
                + "\"activeASE\":false,\"activeNLI\":false,"
                + "\"activeXT\":false,\"activeXTForOther\":false,"
                + "\"rateOfFEC\":0.25,\"typeOfTestQoT\":0,"
                + "\"power\":0.0,\"spanLength\":80.0,\"fiberLoss\":0.2,"
                + "\"fiberNonlinearity\":0.0013,\"fiberDispersion\":1.6E-5,"
                + "\"centerFrequency\":1.9385E14,\"constantOfPlanck\":6.626E-34,"
                + "\"noiseFigureOfOpticalAmplifier\":5.0,"
                + "\"powerSaturationOfOpticalAmplifier\":16.0,"
                + "\"noiseFactorModelParameterA1\":100.0,"
                + "\"noiseFactorModelParameterA2\":4.0,"
                + "\"typeOfAmplifierGain\":0,\"amplificationFrequency\":1.9385E14,"
                + "\"switchInsertionLoss\":5.0,\"fixedPowerSpectralDensity\":false,"
                + "\"referenceBandwidthForPowerSpectralDensity\":1.25E10,"
                + "\"propagationConstant\":1.0E7,\"bendingRadius\":0.01,"
                + "\"couplingCoefficient\":1.0E-10,\"corePitch\":4.5E-5,"
                + "\"polarizationModes\":2.0"
                + "}";
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
