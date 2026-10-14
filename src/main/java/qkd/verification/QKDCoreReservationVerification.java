package qkd.verification;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Vector;

import gprmcsa.routing.Route;
import network.Core;
import network.Link;
import network.Oxc;
import qkd.network.QKDNetworkParameters;
import simulationControl.SimulationFileManager;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.NetworkConfig.CoreConfig;
import simulationControl.parsers.SimulationRequest;
import util.IntersectionFreeSpectrum;

/**
 * Deterministic verification for Part 4: reservation of a physical MCF core
 * exclusively for QKD.
 *
 * <p>The test uses the normal SNetS network parser, constructs a real
 * {@link Link}, and verifies that classical free-spectrum discovery cannot see
 * the reserved core while the dedicated QKD API can still allocate it.</p>
 */
public final class QKDCoreReservationVerification {

    private QKDCoreReservationVerification() {
        // Utility class.
    }

    public static void main(String[] args) throws Exception {
        verifyReservationPropagationAndClassicalIsolation();
        verifyDisabledQkdLeavesAllCoresClassical();
        System.out.println("QKD Part 4 verification: ALL CHECKS PASSED");
    }

    private static void verifyReservationPropagationAndClassicalIsolation()
            throws Exception {

        final NetworkConfig config = readNetworkThroughSNetS(networkJson(true));
        final QKDNetworkParameters parameters = QKDNetworkParameters.from(config);

        assertTrue("QKD enabled", parameters.isEnabled());
        assertEquals("configured quantum core", 6,
                parameters.getQuantumCoreId());

        int reservedConfigs = 0;
        for (CoreConfig core : config.getCores()) {
            if (core.isQkdReserved()) {
                reservedConfigs++;
                assertEquals("reserved CoreConfig id", 6, core.getId());
            }
        }
        assertEquals("exactly one reserved CoreConfig", 1, reservedConfigs);

        final Link link = new Link(
                new Oxc("A"),
                new Oxc("B"),
                320,
                1.25E10,
                80.0,
                config.getCores());

        assertEquals("physical core count", 7, link.getNumberOfCores());
        assertEquals("classical core count", 6,
                link.getNumberOfClassicalCores());
        assertEquals("reserved core list size", 1,
                link.getQkdReservedCores().size());

        final Core qkdCore = link.getCore(6);
        final Core classicalCore = link.getCore(5);

        assertTrue("core 6 reserved", qkdCore.isQkdReserved());
        assertTrue("core 5 remains classical", !classicalCore.isQkdReserved());
        assertTrue("reserved core hidden from classical free-band API",
                qkdCore.getFreeSpectrumBands(1).isEmpty());
        assertTrue("classical core still exposes free bands",
                !classicalCore.getFreeSpectrumBands(1).isEmpty());

        /*
         * This is the same helper used by integrated RMSCA algorithms in the
         * SNetS code. An empty result proves that a reserved core is not a
         * feasible classical spectrum candidate along a route.
         */
        final Route route = new Route();
        final Vector<Link> links = new Vector<Link>();
        links.add(link);
        route.setLinkList(links);

        final List<int[]> reservedIntersection =
                IntersectionFreeSpectrum.merge(route, 1, 6);
        final List<int[]> classicalIntersection =
                IntersectionFreeSpectrum.merge(route, 1, 5);

        assertTrue("reserved core intersection is empty",
                reservedIntersection.isEmpty());
        assertTrue("classical core intersection remains available",
                !classicalIntersection.isEmpty());

        /* A direct classical allocation is rejected as a second safeguard. */
        final int[] band = new int[] {10, 19};
        assertTrue("direct classical allocation rejected",
                !qkdCore.useSpectrum(band, 1));
        assertEquals("classical rejection leaves QKD core unused", 0,
                qkdCore.getUsedSlots());

        /* The dedicated QKD path can still use exactly the same physical core. */
        assertTrue("QKD free-spectrum API sees the reserved core",
                !qkdCore.getFreeSpectrumBandsForQKD(1).isEmpty());
        assertTrue("QKD allocation succeeds",
                qkdCore.useSpectrumForQKD(band, 1));
        assertTrue("QKD allocation consumes slots", qkdCore.getUsedSlots() > 0);
        qkdCore.liberateSpectrumForQKD(band, 1);
        assertEquals("QKD release restores zero used slots", 0,
                qkdCore.getUsedSlots());

        assertEquals("physical total slots", 7 * 320,
                link.totalNumberOfSlots());
        assertEquals("classical total slots", 6 * 320,
                link.totalNumberOfClassicalSlots());

        System.out.println("[OK] network.qkd.quantumCoreId=6 propagated to runtime Core 6");
        System.out.println("[OK] physical cores=7, classical cores=6, QKD-reserved cores=1");
        System.out.println("[OK] classical free-spectrum discovery cannot use Core 6");
        System.out.println("[OK] direct classical allocation on Core 6 is rejected");
        System.out.println("[OK] dedicated QKD API can allocate/release Core 6");
    }

    private static void verifyDisabledQkdLeavesAllCoresClassical()
            throws Exception {
        final NetworkConfig config = readNetworkThroughSNetS(networkJson(false));
        final Link link = new Link(
                new Oxc("A"),
                new Oxc("B"),
                320,
                1.25E10,
                80.0,
                config.getCores());

        assertEquals("disabled-QKD classical cores", 7,
                link.getNumberOfClassicalCores());
        assertEquals("disabled-QKD reserved cores", 0,
                link.getQkdReservedCores().size());
        assertTrue("core 6 is classical when QKD disabled",
                !link.getCore(6).isQkdReserved());
        assertTrue("core 6 exposes spectrum when QKD disabled",
                !link.getCore(6).getFreeSpectrumBands(1).isEmpty());

        System.out.println("[OK] qkd.enabled=false preserves the original 7-core classical behavior");
    }

    private static String networkJson(boolean enabled) {
        return "{\n"
                + "  \"nodes\": [\n"
                + "    {\"name\":\"A\",\"transmitters\":10,\"receivers\":10,\"regenerators\":0},\n"
                + "    {\"name\":\"B\",\"transmitters\":10,\"receivers\":10,\"regenerators\":0}\n"
                + "  ],\n"
                + "  \"links\": [\n"
                + "    {\"source\":\"A\",\"destination\":\"B\",\"slots\":320,\"spectrum\":1.25E10,\"size\":80.0}\n"
                + "  ],\n"
                + "  \"modulations\": [],\n"
                + "  \"cores\": [\n"
                + "    {\"id\":0,\"adjacentCores\":[1,2,3,4,5,6]},\n"
                + "    {\"id\":1,\"adjacentCores\":[0,2,6]},\n"
                + "    {\"id\":2,\"adjacentCores\":[0,1,3]},\n"
                + "    {\"id\":3,\"adjacentCores\":[0,2,4]},\n"
                + "    {\"id\":4,\"adjacentCores\":[0,3,5]},\n"
                + "    {\"id\":5,\"adjacentCores\":[0,4,6]},\n"
                + "    {\"id\":6,\"adjacentCores\":[0,1,5]}\n"
                + "  ],\n"
                + "  \"guardBand\":1,\n"
                + "  \"bvtSpectralAmplitude\":1000,\n"
                + "  \"qkd\":{\"enabled\":" + enabled
                + ",\"qtndKm\":25.0,\"quantumCoreId\":6}\n"
                + "}";
    }

    private static NetworkConfig readNetworkThroughSNetS(String networkJson)
            throws IOException {
        final File root = Files.createTempDirectory("snets-qkd-part4-").toFile();
        final File scenario = new File(root, "scenario");
        if (!scenario.mkdirs()) {
            throw new IOException("Could not create temporary scenario folder");
        }

        final File networkFile = new File(scenario, "network");
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(
                    new FileOutputStream(networkFile),
                    StandardCharsets.UTF_8);
            writer.write(networkJson);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }

        try {
            final SimulationRequest request =
                    new SimulationFileManager().readSimulation(
                            root.getAbsolutePath(), scenario.getName());
            return request.getNetworkConfig();
        } finally {
            deleteRecursively(root);
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            final File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    private static void assertTrue(String name, boolean value) {
        if (!value) {
            throw new IllegalStateException(name + ": expected true");
        }
    }

    private static void assertEquals(String name, int expected, int actual) {
        if (expected != actual) {
            throw new IllegalStateException(name + ": expected=" + expected
                    + ", actual=" + actual);
        }
    }
}
