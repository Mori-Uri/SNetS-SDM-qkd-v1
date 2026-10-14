package qkd.verification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

import gprmcsa.routing.Route;
import network.Link;
import network.Node;
import qkd.network.QKDRoutePlan;
import qkd.network.QKDRoutePlanner;
import qkd.network.QKDTopologyPlan;
import qkd.network.QKDTopologyPlanner;
import qkd.traffic.RequestProtectionType;
import request.RequestForConnection;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.NetworkConfig.CoreConfig;
import simulationControl.parsers.NetworkConfig.LinkConfig;

/**
 * Part 6 verification: an already-selected classical route is converted into
 * the expected chain of logical QKD spans without changing the classical Route.
 */
public final class QKDRoutePlanningVerification {

    private QKDRoutePlanningVerification() {
    }

    public static void main(String[] args) {
        NetworkConfig config = createNetworkConfig();
        QKDTopologyPlan topologyPlan = new QKDTopologyPlanner().plan(config);
        Route route = createClassicalRoute(config.getCores());

        int originalLinks = route.getLinkList().size();
        int originalNodes = route.getNodeList().size();

        QKDRoutePlan qkdPlan = new QKDRoutePlanner(topologyPlan).plan(route);

        check(qkdPlan.getPhysicalLinkCount() == 2,
                "expected two physical links");
        check(qkdPlan.getTotalSpanCount() == 6,
                "80/25 + 40/25 should produce 4 + 2 = 6 spans");
        check(qkdPlan.getVirtualTrustedNodeCount() == 4,
                "expected 3 + 1 virtual trusted nodes");
        check(qkdPlan.getPhysicalIntermediateTrustedNodeCount() == 1,
                "B should be the physical intermediate trusted node");
        check(qkdPlan.getTotalTrustedRelayCount() == 5,
                "expected five total trusted relay points");
        checkClose(qkdPlan.getTotalDistanceKm(), 120.0, 1e-9,
                "total route distance");

        check(route.getLinkList().size() == originalLinks,
                "classical route links were modified");
        check(route.getNodeList().size() == originalNodes,
                "classical route nodes were modified");

        RequestForConnection request = new RequestForConnection();
        request.setProtectionType(RequestProtectionType.QKD_PROTECTED);
        QKDRoutePlan throughRequest =
                new QKDRoutePlanner(topologyPlan).plan(request, route);
        check(throughRequest.getTotalSpanCount() == 6,
                "protected-request overload changed route plan");

        request.setProtectionType(RequestProtectionType.CLASSICAL);
        boolean rejectedClassical = false;
        try {
            new QKDRoutePlanner(topologyPlan).plan(request, route);
        } catch (IllegalArgumentException expected) {
            rejectedClassical = true;
        }
        check(rejectedClassical,
                "classical request should not receive a QKD route plan");

        System.out.println("[OK] classical route A->B->C retained: 2 links, 3 nodes");
        System.out.println("[OK] QKD route: 120 km -> 6 spans with QTND=25 km");
        System.out.println("[OK] trusted relays: 4 virtual + 1 physical intermediate = 5");
        System.out.println("[OK] QKD route planning accepts only QKD_PROTECTED requests");
        System.out.println();
        System.out.println(qkdPlan);
        System.out.println();
        System.out.println("QKD Part 6 verification: ALL CHECKS PASSED");
    }

    private static NetworkConfig createNetworkConfig() {
        NetworkConfig config = new NetworkConfig();
        config.setCores(createCoreConfigs());
        config.setLinks(Arrays.asList(
                new LinkConfig("A", "B", 320, 12.5e9, 80.0),
                new LinkConfig("B", "C", 320, 12.5e9, 40.0)));
        config.setQkd(new NetworkConfig.QKDConfig(true, 25.0, 0));
        return config;
    }

    private static List<CoreConfig> createCoreConfigs() {
        List<CoreConfig> cores = new ArrayList<CoreConfig>();
        cores.add(new CoreConfig(0, Collections.<Integer>emptyList()));
        return cores;
    }

    private static Route createClassicalRoute(List<CoreConfig> cores) {
        Node a = new Node("A");
        Node b = new Node("B");
        Node c = new Node("C");

        Link ab = new Link(a.getOxc(), b.getOxc(), 320, 12.5e9, 80.0, cores);
        Link bc = new Link(b.getOxc(), c.getOxc(), 320, 12.5e9, 40.0, cores);

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
