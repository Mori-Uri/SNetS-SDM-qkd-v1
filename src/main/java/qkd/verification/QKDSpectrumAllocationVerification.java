package qkd.verification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

import gprmcsa.routing.Route;
import network.Core;
import network.Link;
import network.Node;
import qkd.network.QKDRoutePlan;
import qkd.network.QKDRoutePlanner;
import qkd.network.QKDTopologyPlan;
import qkd.network.QKDTopologyPlanner;
import qkd.resource.QKDChannelAllocation;
import qkd.resource.QKDSession;
import qkd.resource.QKDSpectrumAllocationResult;
import qkd.resource.QKDSpectrumAllocator;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.NetworkConfig.CoreConfig;
import simulationControl.parsers.NetworkConfig.LinkConfig;

/**
 * Part 8 verification: real reservation and release of QKD spectrum on the
 * dedicated quantum core, still without ControlPlane integration.
 */
public final class QKDSpectrumAllocationVerification {

    private static final int QKD_CORE_ID = 0;

    private QKDSpectrumAllocationVerification() {
    }

    public static void main(String[] args) throws Exception {
        verifySuccessfulIndependentPerLinkAllocationAndRelease();
        verifyTransactionalRollbackWhenLaterLinkHasNoSpectrum();

        System.out.println();
        System.out.println("QKD Part 8 verification: ALL CHECKS PASSED");
    }

    private static void verifySuccessfulIndependentPerLinkAllocationAndRelease()
            throws Exception {

        NetworkConfig config = createNetworkConfig(
                20,
                Arrays.asList(
                        new LinkConfig("A", "B", 20, 12.5e9, 80.0),
                        new LinkConfig("B", "C", 20, 12.5e9, 40.0)));

        Route route = createRoute(config.getCores(), 20,
                new String[] {"A", "B", "C"},
                new double[] {80.0, 40.0});

        QKDTopologyPlan topology = new QKDTopologyPlanner().plan(config);
        QKDRoutePlan qkdRoute = new QKDRoutePlanner(topology).plan(route);

        Core abQkd = route.getLinkList().get(0).getCore(QKD_CORE_ID);
        Core bcQkd = route.getLinkList().get(1).getCore(QKD_CORE_ID);

        check(abQkd.isQkdReserved(), "A-B QKD core must be reserved");
        check(bcQkd.isQkdReserved(), "B-C QKD core must be reserved");

        /*
         * Pre-existing QKD occupancy deliberately differs by link. This proves
         * that Part 8 does not impose end-to-end spectrum continuity.
         */
        check(abQkd.useSpectrumForQKD(new int[] {1, 2}, 0),
                "could not prepare A-B QKD occupancy");
        check(bcQkd.useSpectrumForQKD(new int[] {1, 5}, 0),
                "could not prepare B-C QKD occupancy");

        int abBefore = abQkd.getUsedSlots();
        int bcBefore = bcQkd.getUsedSlots();

        QKDSpectrumAllocator allocator = new QKDSpectrumAllocator();
        QKDSpectrumAllocationResult allocation = allocator.allocate(
                route,
                qkdRoute,
                QKD_CORE_ID,
                3,
                0);

        check(allocation.isSuccess(),
                "expected successful QKD allocation: " + allocation);
        QKDSession session = allocation.getSession();
        check(session != null && session.isActive(),
                "successful allocation must create active QKD session");
        check(session.getPhysicalLinkAllocationCount() == 2,
                "expected one QKD reservation per physical link");
        check(session.getCoveredQkdSpanCount() == 6,
                "two physical reservations must cover all six QTND spans");

        QKDChannelAllocation abAllocation = session.getAllocations().get(0);
        QKDChannelAllocation bcAllocation = session.getAllocations().get(1);

        assertBand("A-B first-fit band", new int[] {3, 5},
                abAllocation.getSpectrumBand());
        assertBand("B-C first-fit band", new int[] {6, 8},
                bcAllocation.getSpectrumBand());

        check(!sameBand(abAllocation.getSpectrumBand(),
                        bcAllocation.getSpectrumBand()),
                "Part 8 should allow independent spectrum per physical link");

        check(abAllocation.getCoveredQkdSpans() == 4,
                "A-B reservation should cover 4 QTND spans");
        check(bcAllocation.getCoveredQkdSpans() == 2,
                "B-C reservation should cover 2 QTND spans");

        check(abQkd.getUsedSlots() == abBefore + 3,
                "A-B used-slot count did not increase by three");
        check(bcQkd.getUsedSlots() == bcBefore + 3,
                "B-C used-slot count did not increase by three");

        check(abQkd.getFreeSpectrumBands(0).isEmpty(),
                "classical API must still hide the QKD-reserved core");
        check(bcQkd.getFreeSpectrumBands(0).isEmpty(),
                "classical API must still hide the QKD-reserved core");

        System.out.println("[OK] QKD session reserves real Spectrum state on the dedicated core");
        System.out.println("[OK] one physical-link reservation covers all QTND spans of that link");
        System.out.println("[OK] spectrum continuity is NOT imposed yet: A-B uses [3,5], B-C uses [6,8]");
        System.out.println("     " + abAllocation);
        System.out.println("     " + bcAllocation);

        check(allocator.release(session), "first release should succeed");
        check(!session.isActive(), "released QKD session must be inactive");
        check(abQkd.getUsedSlots() == abBefore,
                "A-B QKD spectrum was not fully released");
        check(bcQkd.getUsedSlots() == bcBefore,
                "B-C QKD spectrum was not fully released");
        check(!allocator.release(session),
                "second release should be safely ignored");

        System.out.println("[OK] session release restores the previous spectrum state");

        // Remove test-only pre-existing occupancy.
        abQkd.liberateSpectrumForQKD(new int[] {1, 2}, 0);
        bcQkd.liberateSpectrumForQKD(new int[] {1, 5}, 0);
    }

    private static void verifyTransactionalRollbackWhenLaterLinkHasNoSpectrum()
            throws Exception {

        NetworkConfig config = createNetworkConfig(
                10,
                Arrays.asList(
                        new LinkConfig("D", "E", 10, 12.5e9, 10.0),
                        new LinkConfig("E", "F", 10, 12.5e9, 10.0)));

        Route route = createRoute(config.getCores(), 10,
                new String[] {"D", "E", "F"},
                new double[] {10.0, 10.0});

        QKDRoutePlan qkdRoute = new QKDRoutePlanner(
                new QKDTopologyPlanner().plan(config)).plan(route);

        Core deQkd = route.getLinkList().get(0).getCore(QKD_CORE_ID);
        Core efQkd = route.getLinkList().get(1).getCore(QKD_CORE_ID);

        // Exhaust only the second physical link.
        check(efQkd.useSpectrumForQKD(new int[] {1, 10}, 0),
                "could not exhaust E-F QKD spectrum");

        int deBefore = deQkd.getUsedSlots();
        int efBefore = efQkd.getUsedSlots();

        QKDSpectrumAllocationResult failed = new QKDSpectrumAllocator().allocate(
                route,
                qkdRoute,
                QKD_CORE_ID,
                2,
                0);

        check(!failed.isSuccess(),
                "allocation should fail when E-F has no QKD spectrum");
        check(failed.getSession() == null,
                "failed allocation must not expose a session");
        check(failed.getFailedPhysicalLinkIndex() == 1,
                "failure should be reported on the second physical link");
        check(deQkd.getUsedSlots() == deBefore,
                "D-E tentative allocation was not rolled back");
        check(efQkd.getUsedSlots() == efBefore,
                "E-F pre-existing occupancy should remain untouched");

        System.out.println("[OK] allocation failure on a later link rolls back earlier QKD reservations");
        System.out.println("     failure=" + failed.getFailureReason());

        efQkd.liberateSpectrumForQKD(new int[] {1, 10}, 0);
    }

    private static NetworkConfig createNetworkConfig(int slots,
            List<LinkConfig> links) {
        NetworkConfig config = new NetworkConfig();
        config.setCores(createCoreConfigs());
        config.setLinks(links);
        config.setQkd(new NetworkConfig.QKDConfig(true, 25.0, QKD_CORE_ID));
        return config;
    }

    private static List<CoreConfig> createCoreConfigs() {
        List<CoreConfig> cores = new ArrayList<CoreConfig>();
        cores.add(new CoreConfig(0, Collections.singletonList(1)));
        cores.add(new CoreConfig(1, Collections.singletonList(0)));
        return cores;
    }

    private static Route createRoute(List<CoreConfig> cores, int slots,
            String[] nodeNames, double[] distances) {
        if (nodeNames.length != distances.length + 1) {
            throw new IllegalArgumentException("invalid route fixture");
        }

        Vector<Node> nodes = new Vector<Node>();
        for (String name : nodeNames) {
            nodes.add(new Node(name));
        }

        Vector<Link> links = new Vector<Link>();
        for (int i = 0; i < distances.length; i++) {
            links.add(new Link(
                    nodes.get(i).getOxc(),
                    nodes.get(i + 1).getOxc(),
                    slots,
                    12.5e9,
                    distances[i],
                    cores));
        }

        Route route = new Route();
        route.setNodeList(nodes);
        route.setLinkList(links);
        return route;
    }

    private static boolean sameBand(int[] a, int[] b) {
        return a[0] == b[0] && a[1] == b[1];
    }

    private static void assertBand(String label, int[] expected, int[] actual) {
        if (!sameBand(expected, actual)) {
            throw new AssertionError(label + ": expected=[" + expected[0]
                    + ',' + expected[1] + "], actual=[" + actual[0] + ','
                    + actual[1] + ']');
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
