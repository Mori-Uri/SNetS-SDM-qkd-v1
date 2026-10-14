package qkd.verification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

import gprmcsa.routing.Route;
import network.Circuit;
import network.Link;
import network.Node;
import qkd.network.QKDRoutePlan;
import qkd.network.QKDRoutePlanner;
import qkd.network.QKDTopologyPlan;
import qkd.network.QKDTopologyPlanner;
import qkd.physical.QKDPhysicalLayer;
import qkd.physical.QKDRoutePhysicalResult;
import qkd.physical.QKDSpanPhysicalResult;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.NetworkConfig.CoreConfig;
import simulationControl.parsers.NetworkConfig.LinkConfig;

/**
 * Part 7 verification: every QTND span of an already-planned QKD route is
 * evaluated through the complete PXT -> pXT -> QBER -> SKR chain.
 *
 * <p>The test deliberately uses stored classical launch powers and a fixed
 * coupling coefficient so all results can be checked analytically without
 * constructing the complete SNetS PhysicalLayer.</p>
 */
public final class QKDRoutePhysicalEvaluationVerification {

    private static final int QKD_CORE_ID = 0;
    private static final int[] QKD_BAND = new int[] {10, 19};
    private static final double H_PER_M = 1e-15;
    private static final double WAVELENGTH_NM = 1550.0;

    private QKDRoutePhysicalEvaluationVerification() {
    }

    public static void main(String[] args) {
        NetworkConfig config = createNetworkConfig();
        QKDTopologyPlan topology = new QKDTopologyPlanner().plan(config);
        Route classicalRoute = createClassicalRoute(config.getCores());
        populateClassicalInterferers(classicalRoute);

        final int originalLinkCount = classicalRoute.getLinkList().size();
        final int originalNodeCount = classicalRoute.getNodeList().size();

        QKDRoutePlan qkdRoute = new QKDRoutePlanner(topology).plan(classicalRoute);
        QKDPhysicalLayer evaluator = new QKDPhysicalLayer();

        /*
         * This permissive-yet-meaningful test criterion is NOT a final scientific
         * threshold. It is used only to verify the route-level decision logic.
         */
        QKDRoutePhysicalResult result = evaluator.evaluateUsingStoredLaunchPowers(
                classicalRoute,
                qkdRoute,
                QKD_CORE_ID,
                QKD_BAND,
                H_PER_M,
                WAVELENGTH_NM,
                90e6,
                0.02);

        verifyRouteStructure(result);
        verifySpanPowers(result);
        verifyPhysicalChain(result);
        verifyRouteDecision(evaluator, classicalRoute, qkdRoute, result);

        check(classicalRoute.getLinkList().size() == originalLinkCount,
                "physical evaluation modified the classical route link list");
        check(classicalRoute.getNodeList().size() == originalNodeCount,
                "physical evaluation modified the classical route node list");

        System.out.println();
        for (int i = 0; i < result.getSpanResults().size(); i++) {
            System.out.println("span[" + i + "] = " + result.getSpanResults().get(i));
        }
        System.out.println();
        System.out.println(result);
        System.out.println();
        System.out.println("QKD Part 7 verification: ALL CHECKS PASSED");
    }

    private static void verifyRouteStructure(QKDRoutePhysicalResult result) {
        check(result.getSpanResults().size() == 6,
                "expected six QKD span physical results");
        check(result.getRoutePlan().getTotalDistanceKm() == 120.0,
                "expected 120 km route");

        System.out.println("[OK] QKD route physical evaluation covers all 6 QTND spans");
    }

    private static void verifySpanPowers(QKDRoutePhysicalResult result) {
        List<QKDSpanPhysicalResult> spans = result.getSpanResults();

        // A->B has one 1 mW fully overlapping classical interferer.
        assertClose("A-B first 25-km PXT", 2.5e-14,
                spans.get(0).getCrosstalkPowerW(), 1e-25);
        assertClose("A-B second 25-km PXT", 2.5e-14,
                spans.get(1).getCrosstalkPowerW(), 1e-25);
        assertClose("A-B third 25-km PXT", 2.5e-14,
                spans.get(2).getCrosstalkPowerW(), 1e-25);
        assertClose("A-B residual 5-km PXT", 5.0e-15,
                spans.get(3).getCrosstalkPowerW(), 1e-26);

        // B->C has one 4 mW fully overlapping classical interferer.
        assertClose("B-C 25-km PXT", 1.0e-13,
                spans.get(4).getCrosstalkPowerW(), 1e-25);
        assertClose("B-C residual 15-km PXT", 6.0e-14,
                spans.get(5).getCrosstalkPowerW(), 1e-25);

        System.out.println("[OK] per-span PXT uses QKD-span length, not full physical-link length");
    }

    private static void verifyPhysicalChain(QKDRoutePhysicalResult result) {
        List<QKDSpanPhysicalResult> spans = result.getSpanResults();

        QKDSpanPhysicalResult ab25 = spans.get(0);
        QKDSpanPhysicalResult ab5 = spans.get(3);
        QKDSpanPhysicalResult bc25 = spans.get(4);
        QKDSpanPhysicalResult bc15 = spans.get(5);

        assertClose("A-B 25-km SKR Mbps", 93.8402867444917,
                ab25.getSecretKeyRateMbps(), 1e-9);
        assertClose("A-B 5-km SKR Mbps", 219.1293796430962,
                ab5.getSecretKeyRateMbps(), 1e-9);
        assertClose("B-C 25-km QBER", 0.015158485793074584,
                bc25.getQber(), 1e-15);
        assertClose("B-C 25-km SKR Mbps", 93.54339669078442,
                bc25.getSecretKeyRateMbps(), 1e-9);
        assertClose("B-C 15-km SKR Mbps", 143.00252796704393,
                bc15.getSecretKeyRateMbps(), 1e-9);

        check(ab5.getSecretKeyRateBps() > ab25.getSecretKeyRateBps(),
                "shorter residual span should have higher SKR in this controlled case");
        check(bc25.getQber() > ab25.getQber(),
                "higher classical interferer power should increase QBER");

        System.out.println("[OK] each span completes PXT -> pXT -> pb -> QBER -> SKR");
    }

    private static void verifyRouteDecision(
            QKDPhysicalLayer evaluator,
            Route classicalRoute,
            QKDRoutePlan qkdRoute,
            QKDRoutePhysicalResult result) {

        check(result.isViable(),
                "route should be viable for SKR>=90 Mbit/s and QBER<=0.02");
        assertClose("route bottleneck SKR Mbps", 93.54339669078442,
                result.getMinimumSecretKeyRateMbps(), 1e-9);
        assertClose("route maximum QBER", 0.015158485793074584,
                result.getMaximumQber(), 1e-15);
        check(result.getBottleneckSpanIndex() == 4,
                "expected B-C 25-km span to be the route bottleneck");

        QKDRoutePhysicalResult stricter = evaluator.evaluateUsingStoredLaunchPowers(
                classicalRoute,
                qkdRoute,
                QKD_CORE_ID,
                QKD_BAND,
                H_PER_M,
                WAVELENGTH_NM,
                94e6,
                0.02);

        check(!stricter.isViable(),
                "route should fail when minimum required SKR is raised to 94 Mbit/s");

        System.out.println("[OK] route viability requires every span to satisfy SKR/QBER criteria");
        System.out.println("[OK] route bottleneck = span 4, minSKR="
                + result.getMinimumSecretKeyRateMbps() + " Mbit/s, maxQBER="
                + result.getMaximumQber());
    }

    private static NetworkConfig createNetworkConfig() {
        NetworkConfig config = new NetworkConfig();
        config.setCores(createCoreConfigs());
        config.setLinks(Arrays.asList(
                new LinkConfig("A", "B", 100, 12.5e9, 80.0),
                new LinkConfig("B", "C", 100, 12.5e9, 40.0)));
        config.setQkd(new NetworkConfig.QKDConfig(true, 25.0, QKD_CORE_ID));
        return config;
    }

    private static List<CoreConfig> createCoreConfigs() {
        List<CoreConfig> cores = new ArrayList<CoreConfig>();
        cores.add(new CoreConfig(0, Collections.singletonList(1)));
        cores.add(new CoreConfig(1, Collections.singletonList(0)));
        // Deliberately non-adjacent core used to prove it contributes no XT.
        cores.add(new CoreConfig(2, Collections.<Integer>emptyList()));
        return cores;
    }

    private static Route createClassicalRoute(List<CoreConfig> cores) {
        Node a = new Node("A");
        Node b = new Node("B");
        Node c = new Node("C");

        Link ab = new Link(a.getOxc(), b.getOxc(), 100, 12.5e9, 80.0, cores);
        Link bc = new Link(b.getOxc(), c.getOxc(), 100, 12.5e9, 40.0, cores);

        Vector<Node> nodes = new Vector<Node>();
        nodes.add(a);
        nodes.add(b);
        nodes.add(c);

        Vector<Link> links = new Vector<Link>();
        links.add(ab);
        links.add(bc);

        Route route = new Route();
        route.setNodeList(nodes);
        route.setLinkList(links);
        return route;
    }

    private static void populateClassicalInterferers(Route route) {
        Link ab = route.getLinkList().get(0);
        Link bc = route.getLinkList().get(1);

        // A->B: 1 mW, full spectral overlap with the candidate QKD band.
        ab.getCore(1).addCircuit(createInterferer(1, QKD_BAND, 1e-3));

        // Same band and huge power, but non-adjacent core: must be ignored.
        ab.getCore(2).addCircuit(createInterferer(2, QKD_BAND, 1.0));

        // B->C: 4 mW, full overlap. This creates the route bottleneck.
        bc.getCore(1).addCircuit(createInterferer(1, QKD_BAND, 4e-3));

        // Adjacent but spectrally disjoint: must contribute zero.
        bc.getCore(1).addCircuit(createInterferer(1, new int[] {30, 39}, 1.0));
    }

    private static Circuit createInterferer(
            int coreId,
            int[] spectrum,
            double launchPowerW) {
        Circuit circuit = new Circuit();
        circuit.setIndexCore(coreId);
        circuit.setSpectrumAssigned(new int[] {spectrum[0], spectrum[1]});
        circuit.setLaunchPowerLinear(launchPowerW);
        return circuit;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertClose(
            String label,
            double expected,
            double actual,
            double tolerance) {
        if (Math.abs(expected - actual) > tolerance) {
            throw new AssertionError(label + ": expected=" + expected
                    + ", actual=" + actual + ", tolerance=" + tolerance);
        }
    }
}
