package qkd.verification;

import java.util.HashMap;
import java.util.Vector;

import com.google.gson.Gson;

import gprmcsa.GPRMCSA;
import gprmcsa.integrated.IntegratedRMLSAAlgorithmInterface;
import gprmcsa.modulation.Modulation;
import gprmcsa.routing.KRoutingAlgorithmInterface;
import gprmcsa.routing.Route;
import network.Circuit;
import network.Link;
import network.Mesh;
import network.Node;
import network.Pair;
import qkd.control.QKDActiveConnection;
import qkd.control.QKDControlPlane;
import qkd.control.QKDExistingSessionImpactResult;
import qkd.control.QKDProvisioningResult;
import qkd.control.QKDProvisioningService;
import qkd.control.QKDSessionImpact;
import qkd.key.QKDKeyDemand;
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
 * Part 10 verification: a new classical circuit must not be admitted when its
 * inter-core XT would make an already-active QKD session physically unviable.
 *
 * <p>The fixture deliberately keeps classical QoT/XT checks disabled so the
 * only reason for rejecting the harmful candidate is the new QKD protection
 * rule. A second non-overlapping classical candidate is then admitted to prove
 * that the rule is selective rather than a blanket block while QKD is active.</p>
 */
public final class QKDExistingSessionProtectionVerification {

    private QKDExistingSessionProtectionVerification() {
    }

    public static void main(String[] args) throws Exception {
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
        check(genericMesh instanceof QKDMesh, "fixture must create QKDMesh");
        QKDMesh mesh = (QKDMesh) genericMesh;

        Pair pair = findPair(mesh, "1", "2");
        Route route = directRoute(pair);
        Modulation modulation = mesh.getAvaliableModulations().get(0);

        FixedCandidateIntegratedAlgorithm integrated =
                new FixedCandidateIntegratedAlgorithm(
                        route, modulation, 1, new int[] {1, 1}, 1.0e-3);

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
                integrated,
                algorithms.instantiateRouting(),
                algorithms.instantiateKRouting(),
                algorithms.instantiateSpectrumAssignment(),
                algorithms.instantiateModulationSelection(),
                algorithms.instantiateCoreAndSpectrumAssignment(),
                algorithms.instantiateReallocation(),
                algorithms.instantiatePowerAssignment());

        /*
         * Create one active QKD session without adding a classical interferer.
         * This isolates the Part-10 rule from the lifecycle already verified in
         * Part 9. The QKD allocator reserves slot 1 on core 0.
         */
        RequestForConnection protectedRequest = request(pair, true);
        Circuit referenceCircuit = controlPlane.createNewCircuit(protectedRequest);
        referenceCircuit.setRoute(route);

        QKDProvisioningService provisioning = new QKDProvisioningService(mesh);
        QKDProvisioningResult provisioned = provisioning.provision(protectedRequest);
        check(provisioned.isSuccess(),
                "baseline QKD session could not be provisioned: "
                + provisioned.getFailureReason());

        QKDActiveConnection activeQkd = provisioned.getConnection();
        controlPlane.getQkdSessionRegistry().register(
                protectedRequest, activeQkd);

        int[] qkdBand = activeQkd.getQkdSession()
                .getAllocations().get(0).getSpectrumBand();
        check(qkdBand[0] == 1 && qkdBand[1] == 1,
                "verification expects first QKD slot [1,1]");

        double baselineSkr = activeQkd.getMinimumSecretKeyRateBps();
        check(baselineSkr > 0.0, "baseline QKD session must be viable");
        System.out.println("[OK] baseline active QKD session established on core 0 / slot [1,1]");
        System.out.println("     baselineMinSKR_Mbps=" + (baselineSkr / 1e6));

        Link link = route.getLinkList().get(0);
        check(link.getCore(1).getUsedSlots() == 0,
                "classical test core must initially be empty");

        /* Harmful candidate: same physical link, adjacent core, same slot. */
        integrated.configure(1, new int[] {1, 1}, 1.0e-3);
        RequestForConnection harmful = request(pair, false);
        boolean harmfulAccepted = controlPlane.handleRequisition(harmful);

        check(!harmfulAccepted,
                "overlapping classical candidate should be rejected by QKD protection");
        check(link.getCore(1).getUsedSlots() == 0,
                "rejected candidate leaked classical spectrum");
        check(controlPlane.getActiveQkdSessionCount() == 1,
                "active QKD session disappeared after candidate rejection");
        check(activeQkd.isActive(),
                "QKD session must remain active after protecting it");

        QKDExistingSessionImpactResult harmfulImpact =
                controlPlane.getLastExistingSessionImpactResult();
        check(harmfulImpact != null,
                "missing QKD impact result for harmful candidate");
        check(harmfulImpact.getAffectedSessions() == 1,
                "harmful candidate should affect exactly one QKD session");
        check(harmfulImpact.getViolatedSessions() == 1,
                "harmful candidate should violate the active QKD session");
        check(!harmfulImpact.isAdmissible(),
                "harmful impact result should be inadmissible");

        QKDSessionImpact violation = harmfulImpact.getFirstViolation();
        check(violation != null, "expected one QKD violation");
        check(violation.getProjectedMinimumSkrBps() == 0.0,
                "fixture expected projected SKR to collapse to zero");

        System.out.println("[OK] overlapping classical candidate rejected before resource commitment");
        System.out.println("     projectedMinSKR_Mbps="
                + (violation.getProjectedMinimumSkrBps() / 1e6)
                + ", projectedMaxQBER="
                + violation.getProjectedMaximumQber());
        System.out.println("[OK] rejected candidate leaves classical and QKD resources unchanged");

        /* Harmless candidate: same link/core but no spectral overlap with QCh. */
        integrated.configure(1, new int[] {2, 2}, 1.0e-3);
        RequestForConnection harmless = request(pair, false);
        boolean harmlessAccepted = controlPlane.handleRequisition(harmless);

        check(harmlessAccepted,
                "non-overlapping classical candidate should remain admissible");
        check(link.getCore(1).getUsedSlots() == 1,
                "accepted classical circuit did not reserve its slot");
        check(controlPlane.getActiveQkdSessionCount() == 1,
                "QKD session must remain registered after harmless admission");

        QKDExistingSessionImpactResult harmlessImpact =
                controlPlane.getLastExistingSessionImpactResult();
        check(harmlessImpact != null && harmlessImpact.isAdmissible(),
                "harmless candidate impact should be admissible");
        check(harmlessImpact.getAffectedSessions() == 0,
                "non-overlapping candidate must not be marked as affecting QKD");
        check(Math.abs(activeQkd.getMinimumSecretKeyRateBps() - baselineSkr) < 1e-6,
                "harmless admission unexpectedly changed QKD SKR");

        System.out.println("[OK] non-overlapping classical candidate remains admissible");
        System.out.println("[OK] committed classical changes refresh active QKD physical snapshots");

        controlPlane.finalizeConnection(harmless);
        check(link.getCore(1).getUsedSlots() == 0,
                "classical departure did not release spectrum");
        check(controlPlane.getActiveQkdSessionCount() == 1,
                "unrelated classical departure removed active QKD session");
        check(Math.abs(activeQkd.getMinimumSecretKeyRateBps() - baselineSkr) < 1e-6,
                "QKD state was not refreshed after classical departure");

        check(controlPlane.getQkdExistingSessionImpactChecks() == 2,
                "expected exactly two existing-QKD impact checks");
        check(controlPlane.getQkdClassicalRejectionsByExistingSessionImpact() == 1,
                "expected exactly one classical rejection caused by active QKD");
        check(controlPlane.getQkdActiveSessionRefreshes() >= 2,
                "expected QKD state refresh after classical allocation/release");

        System.out.println("[OK] classical departure refreshes the remaining active QKD state");
        System.out.println("     impactChecks="
                + controlPlane.getQkdExistingSessionImpactChecks()
                + ", qkdImpactRejections="
                + controlPlane.getQkdClassicalRejectionsByExistingSessionImpact()
                + ", qkdStateRefreshes="
                + controlPlane.getQkdActiveSessionRefreshes());

        /* Explicit cleanup of the synthetic reference QKD session. */
        controlPlane.getQkdSessionRegistry().remove(protectedRequest);
        check(provisioning.release(activeQkd),
                "failed to release synthetic baseline QKD session");
        check(link.getCore(0).getUsedSlots() == 0,
                "QKD spectrum leaked after cleanup");

        System.out.println();
        System.out.println("QKD Part 10 verification: ALL CHECKS PASSED");
    }

    private static RequestForConnection request(Pair pair, boolean qkdProtected) {
        RequestForConnection request = new RequestForConnection();
        request.setPair(pair);
        request.setRequiredBitRate(1.0e9);
        request.setProtectionType(qkdProtected
                ? RequestProtectionType.QKD_PROTECTED
                : RequestProtectionType.CLASSICAL);
        if (qkdProtected) {
            request.setQkdKeyDemand(new QKDKeyDemand(256, 64.0));
        }
        return request;
    }

    private static Pair findPair(Mesh mesh, String source, String destination) {
        for (Pair pair : mesh.getPairList()) {
            if (source.equals(pair.getSource().getName())
                    && destination.equals(pair.getDestination().getName())) {
                return pair;
            }
        }
        throw new IllegalStateException("pair not found: " + source + "->" + destination);
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
                + "\"requests\":1,\"qkdWarmupFraction\":0.0,"
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
                + "\"qkdProtectedRequestProbability\":0.0"
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
                + "\"couplingCoefficient\":1.0E-3,\"corePitch\":4.5E-5,"
                + "\"polarizationModes\":2.0"
                + "}";
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /**
     * Small deterministic integrated RMCSA used only by this verifier. It makes
     * route/core/spectrum/power explicit so the Part-10 admission rule can be
     * tested independently from heuristic selection behavior.
     */
    private static final class FixedCandidateIntegratedAlgorithm
            implements IntegratedRMLSAAlgorithmInterface {

        private static final long serialVersionUID = 1L;

        private final Route route;
        private final Modulation modulation;
        private int coreId;
        private int[] spectrum;
        private double launchPowerW;

        private FixedCandidateIntegratedAlgorithm(Route route,
                Modulation modulation,
                int coreId,
                int[] spectrum,
                double launchPowerW) {
            this.route = route;
            this.modulation = modulation;
            configure(coreId, spectrum, launchPowerW);
        }

        private void configure(int coreId, int[] spectrum, double launchPowerW) {
            this.coreId = coreId;
            this.spectrum = new int[] {spectrum[0], spectrum[1]};
            this.launchPowerW = launchPowerW;
        }

        @Override
        public boolean rsa(Circuit circuit, network.ControlPlane cp) {
            circuit.setRoute(route);
            circuit.setModulation(modulation);
            circuit.setIndexCore(coreId);
            circuit.setSpectrumAssigned(new int[] {spectrum[0], spectrum[1]});
            circuit.setLaunchPowerLinear(launchPowerW);
            return true;
        }

        @Override
        public KRoutingAlgorithmInterface getRoutingAlgorithm() {
            return null;
        }
    }
}
