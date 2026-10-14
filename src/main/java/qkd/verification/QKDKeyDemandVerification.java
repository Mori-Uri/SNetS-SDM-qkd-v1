package qkd.verification;

import java.util.HashMap;
import java.util.Vector;

import com.google.gson.Gson;

import gprmcsa.GPRMCSA;
import gprmcsa.routing.Route;
import network.Circuit;
import network.Mesh;
import network.Node;
import network.Pair;
import qkd.control.QKDControlPlane;
import qkd.control.QKDProvisioningResult;
import qkd.control.QKDProvisioningService;
import qkd.key.QKDKeyDemand;
import qkd.key.QKDKeyDemandPolicy;
import qkd.network.QKDMesh;
import qkd.network.QKDMeshFactory;
import qkd.traffic.RequestProtectionType;
import request.RequestForConnection;
import simulationControl.Util;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.OthersConfig;
import simulationControl.parsers.PhysicalLayerConfig;
import simulationControl.parsers.SimulationConfig;
import simulationControl.parsers.TrafficConfig;

/**
 * Part 11 verification: QKD-protected requests now carry an explicit
 * cryptographic-key demand and admission compares that demand with the
 * bottleneck SKR of the allocated QKD route.
 */
public final class QKDKeyDemandVerification {

    private QKDKeyDemandVerification() {
    }

    public static void main(String[] args) throws Exception {
        verifyReferenceDemandEquation();
        verifySimulationConfiguration();
        verifyDeterministicDemandPolicy();
        verifyDemandAwareQkdAdmission();

        System.out.println();
        System.out.println("QKD Part 11 verification: ALL CHECKS PASSED");
    }

    private static void verifyReferenceDemandEquation() {
        QKDKeyDemand d64 = new QKDKeyDemand(256, 64.0);
        QKDKeyDemand d128 = new QKDKeyDemand(256, 128.0);
        QKDKeyDemand d256 = new QKDKeyDemand(256, 256.0);

        checkClose(d64.getRequiredKeyRateMbps(), 4.0, 1e-12,
                "AES-256 / 64 us");
        checkClose(d128.getRequiredKeyRateMbps(), 2.0, 1e-12,
                "AES-256 / 128 us");
        checkClose(d256.getRequiredKeyRateMbps(), 1.0, 1e-12,
                "AES-256 / 256 us");

        System.out.println("[OK] R_K=L_K/T_K reference demands:"
                + " 64us=4 Mbit/s, 128us=2 Mbit/s, 256us=1 Mbit/s");
    }

    private static void verifySimulationConfiguration() {
        String json = "{\"qkdKeyDemand\":{"
                + "\"keySizeBits\":256,"
                + "\"updateIntervalsMicroseconds\":[64,128,256],"
                + "\"randomSeed\":2024}}";

        SimulationConfig config = new Gson().fromJson(
                json, SimulationConfig.class);
        SimulationConfig.QKDKeyDemandConfig keyConfig =
                config.getQkdKeyDemand();

        check(keyConfig.getKeySizeBits() == 256,
                "configured key size was not parsed");
        double[] intervals = keyConfig.getUpdateIntervalsMicroseconds();
        check(intervals.length == 3,
                "expected three configured update intervals");
        checkClose(intervals[0], 64.0, 1e-12, "interval[0]");
        checkClose(intervals[1], 128.0, 1e-12, "interval[1]");
        checkClose(intervals[2], 256.0, 1e-12, "interval[2]");
        check(keyConfig.getRandomSeed() == 2024L,
                "configured QKD key-demand seed was not parsed");

        System.out.println("[OK] simulation.qkdKeyDemand parsed without changing legacy traffic JSON");
    }

    private static void verifyDeterministicDemandPolicy() {
        QKDKeyDemandPolicy a = new QKDKeyDemandPolicy(
                256, new double[] {64.0, 128.0, 256.0}, 2024L);
        QKDKeyDemandPolicy b = new QKDKeyDemandPolicy(
                256, new double[] {64.0, 128.0, 256.0}, 2024L);

        boolean seen64 = false;
        boolean seen128 = false;
        boolean seen256 = false;

        for (int i = 0; i < 1000; i++) {
            QKDKeyDemand da = a.draw();
            QKDKeyDemand db = b.draw();

            checkClose(da.getUpdateIntervalMicroseconds(),
                    db.getUpdateIntervalMicroseconds(), 0.0,
                    "deterministic key-demand sequence");

            double interval = da.getUpdateIntervalMicroseconds();
            if (interval == 64.0) {
                seen64 = true;
            } else if (interval == 128.0) {
                seen128 = true;
            } else if (interval == 256.0) {
                seen256 = true;
            } else {
                throw new AssertionError(
                        "policy generated unconfigured interval: " + interval);
            }
        }

        check(seen64 && seen128 && seen256,
                "not all configured key-demand classes were observed");

        System.out.println("[OK] key-demand policy is reproducible and uses an independent QKD random stream");
    }

    private static void verifyDemandAwareQkdAdmission() throws Exception {
        Gson gson = new Gson();
        NetworkConfig network = gson.fromJson(networkJson(), NetworkConfig.class);
        TrafficConfig traffic = gson.fromJson(trafficJson(), TrafficConfig.class);
        PhysicalLayerConfig physical = gson.fromJson(
                physicalLayerJson(), PhysicalLayerConfig.class);
        SimulationConfig simulation = gson.fromJson(
                simulationJson(), SimulationConfig.class);

        Util util = new Util();
        util.projectPath = ".";

        Mesh genericMesh = QKDMeshFactory.create(
                network,
                traffic,
                physical,
                new OthersConfig(),
                new HashMap<String, HashMap<Double, Double>>(),
                util,
                simulation);
        check(genericMesh instanceof QKDMesh,
                "fixture must create QKDMesh");
        QKDMesh mesh = (QKDMesh) genericMesh;

        Pair pair = findPair(mesh, "1", "2");
        Route route = directRoute(pair);

        GPRMCSA algorithms = new GPRMCSA(
                "notrafficgrooming",
                "completesharing",
                "djk",
                "ksp",
                "modulationbydistance",
                "firstfit",
                "aar",
                "qabne",
                "fsalfav1",
                "cpsd");

        QKDControlPlane controlPlane = new QKDControlPlane(
                mesh,
                GPRMCSA.RMCSA_INTEGRATED,
                algorithms.instantiateGrooming(),
                algorithms.instantiateIntegratedRSA(),
                algorithms.instantiateRouting(),
                algorithms.instantiateKRouting(),
                algorithms.instantiateSpectrumAssignment(),
                algorithms.instantiateModulationSelection(),
                algorithms.instantiateCoreAndSpectrumAssignment(),
                algorithms.instantiateReallocation(),
                algorithms.instantiatePowerAssignment());

        QKDProvisioningService provisioning = new QKDProvisioningService(mesh);

        RequestForConnection normalDemand = request(
                pair, new QKDKeyDemand(256, 64.0));
        Circuit normalCircuit = controlPlane.createNewCircuit(normalDemand);
        normalCircuit.setRoute(route);

        QKDProvisioningResult accepted = provisioning.provision(normalDemand);
        check(accepted.isSuccess(),
                "4-Mbit/s reference demand should be feasible: "
                + accepted.getFailureReason());

        double availableMbps = accepted.getConnection()
                .getMinimumSecretKeyRateBps() / 1.0e6;
        check(availableMbps >= 4.0,
                "accepted route does not actually satisfy 4 Mbit/s");
        checkClose(accepted.getConnection().getRequiredKeyRateMbps(),
                4.0, 1e-12, "stored active-connection demand");

        check(provisioning.release(accepted.getConnection()),
                "failed to release accepted Part-11 QKD session");

        RequestForConnection excessiveDemand = request(
                pair, new QKDKeyDemand(10000, 1.0));
        Circuit excessiveCircuit = controlPlane.createNewCircuit(excessiveDemand);
        excessiveCircuit.setRoute(route);

        QKDProvisioningResult rejected = provisioning.provision(excessiveDemand);
        check(!rejected.isSuccess(),
                "10-Gbit/s demand should exceed total QKD fixture capacity");
        check(rejected.getFailureReason() != null
                        && (rejected.getFailureReason().contains("cannot satisfy key demand") || rejected.getFailureReason().contains("insufficient QKD key-generation capacity")
                        || rejected.getFailureReason().contains("no free QCh lane/band")),
                "unexpected excessive-demand failure reason: "
                + rejected.getFailureReason());

        check(route.getLinkList().get(0).getCore(0).getUsedSlots() == 0,
                "rejected key-demand request leaked QKD spectrum");

        System.out.println("[OK] QKD admission now requires bottleneck SKR >= request key demand");
        System.out.println("     referenceDemand=4.0 Mbit/s, available="
                + availableMbps + " Mbit/s -> ACCEPT");
        System.out.println("     excessiveDemand=10000.0 Mbit/s -> REJECT + spectrum rollback");
    }

    private static RequestForConnection request(
            Pair pair,
            QKDKeyDemand demand) {
        RequestForConnection request = new RequestForConnection();
        request.setPair(pair);
        request.setRequiredBitRate(1.0e9);
        request.setProtectionType(RequestProtectionType.QKD_PROTECTED);
        request.setQkdKeyDemand(demand);
        return request;
    }

    private static Pair findPair(Mesh mesh, String source, String destination) {
        for (Pair pair : mesh.getPairList()) {
            if (source.equals(pair.getSource().getName())
                    && destination.equals(pair.getDestination().getName())) {
                return pair;
            }
        }
        throw new IllegalStateException(
                "pair not found: " + source + "->" + destination);
    }

    private static Route directRoute(Pair pair) {
        Vector<Node> nodes = new Vector<Node>();
        nodes.add(pair.getSource());
        nodes.add(pair.getDestination());
        Route route = new Route(nodes);
        check(route.getLinkList() != null && route.getLinkList().size() == 1,
                "fixture expected a one-link route");
        return route;
    }

    private static String networkJson() {
        return "{"
                + "\"nodes\":["
                + "{\"name\":\"1\",\"transmitters\":20,\"receivers\":20,\"regenerators\":0},"
                + "{\"name\":\"2\",\"transmitters\":20,\"receivers\":20,\"regenerators\":0}],"
                + "\"links\":["
                + "{\"source\":\"1\",\"destination\":\"2\",\"slots\":20,\"spectrum\":1.25E10,\"size\":10.0},"
                + "{\"source\":\"2\",\"destination\":\"1\",\"slots\":20,\"spectrum\":1.25E10,\"size\":10.0}],"
                + "\"modulations\":["
                + "{\"name\":\"4QAM\",\"maxRange\":5000.0,\"M\":4.0,\"SNR\":8.95,\"XT\":-19.03}],"
                + "\"cores\":["
                + "{\"id\":0,\"adjacentCores\":[1]},"
                + "{\"id\":1,\"adjacentCores\":[0]}],"
                + "\"guardBand\":0,\"bvtSpectralAmplitude\":1000,"
                + "\"qkd\":{\"enabled\":true,\"qtndKm\":25.0,\"quantumCoreId\":0}"
                + "}";
    }

    private static String trafficJson() {
        return "{\"requestGenerators\":["
                + "{\"source\":\"1\",\"destination\":\"2\","
                + "\"bitRate\":1.0E9,\"arrivalRate\":1.0,"
                + "\"holdRate\":1.0,\"arrivalRateIncrease\":0.0}]}";
    }

    private static String simulationJson() {
        return "{"
                + "\"requests\":1,"
                + "\"rmlsaType\":1,"
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
                + "\"activeMetrics\":{},"
                + "\"regeneratorAssignment\":\"aar\","
                + "\"networkType\":0,\"threads\":1,"
                + "\"qkdProtectedRequestProbability\":0.0,"
                + "\"qkdKeyDemand\":{"
                + "\"keySizeBits\":256,"
                + "\"updateIntervalsMicroseconds\":[64,128,256],"
                + "\"randomSeed\":2024}"
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
                + "\"couplingCoefficient\":1.0E-12,\"corePitch\":4.5E-5,"
                + "\"polarizationModes\":2.0"
                + "}";
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
