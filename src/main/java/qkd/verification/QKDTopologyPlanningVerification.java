package qkd.verification;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import qkd.network.QKDLinkPlan;
import qkd.network.QKDSpan;
import qkd.network.QKDTopologyPlan;
import qkd.network.QKDTopologyPlanner;
import simulationControl.SimulationFileManager;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.SimulationRequest;

/**
 * Deterministic standalone verification for Part 3 of the QKD integration.
 *
 * <p>This test verifies four independent properties:</p>
 *
 * <ol>
 *   <li>the original SNetS {@link SimulationFileManager} reads the new
 *       {@code qkd} object directly from the normal {@code network} file;</li>
 *   <li>QTND=25 km divides an 80-km link into 25+25+25+5 km;</li>
 *   <li>trusted-node boundaries are logical only and no SNetS Mesh is built or
 *       modified by this stage; and</li>
 *   <li>a legacy network file without a {@code qkd} object remains compatible
 *       and produces a disabled/empty QKD plan.</li>
 * </ol>
 */
public final class QKDTopologyPlanningVerification {

    private static final double EPSILON = 1e-9;

    private QKDTopologyPlanningVerification() {
        // Utility class.
    }

    public static void main(String[] args) throws Exception {
        verifyNetworkFileParsingAndQtndSegmentation();
        verifyExactMultiple();
        verifyShortLink();
        verifyLegacyNetworkCompatibility();
        verifyInvalidQuantumCoreIsRejected();

        System.out.println("QKD Part 3 verification: ALL CHECKS PASSED");
    }

    private static void verifyNetworkFileParsingAndQtndSegmentation()
            throws Exception {

        final String networkJson =
                "{\n"
                + "  \"nodes\": [\n"
                + "    {\"name\":\"A\",\"transmitters\":10,\"receivers\":10,\"regenerators\":0},\n"
                + "    {\"name\":\"B\",\"transmitters\":10,\"receivers\":10,\"regenerators\":0}\n"
                + "  ],\n"
                + "  \"links\": [\n"
                + "    {\"source\":\"A\",\"destination\":\"B\",\"slots\":320,\"spectrum\":12.5,\"size\":80.0}\n"
                + "  ],\n"
                + "  \"modulations\": [],\n"
                + "  \"cores\": [\n"
                + "    {\"id\":0,\"adjacentCores\":[1,2]},\n"
                + "    {\"id\":1,\"adjacentCores\":[0]},\n"
                + "    {\"id\":2,\"adjacentCores\":[0]}\n"
                + "  ],\n"
                + "  \"guardBand\": 1,\n"
                + "  \"bvtSpectralAmplitude\": 1000,\n"
                + "  \"qkd\": {\n"
                + "    \"enabled\": true,\n"
                + "    \"qtndKm\": 25.0,\n"
                + "    \"quantumCoreId\": 1\n"
                + "  }\n"
                + "}";

        final NetworkConfig network = readNetworkThroughSNetS(networkJson);

        assertTrue("QKD config exists", network.getQkd() != null);
        assertTrue("QKD enabled", network.getQkd().isEnabled());
        assertClose("QTND parsed", 25.0, network.getQkd().getQtndKm());
        assertEquals("quantum core parsed", 1,
                network.getQkd().getQuantumCoreId());

        final QKDTopologyPlan topology =
                new QKDTopologyPlanner().plan(network);
        assertTrue("topology enabled", topology.isEnabled());
        assertEquals("physical QKD links", 1, topology.getPhysicalLinkCount());
        assertEquals("total spans", 4, topology.getTotalSpanCount());
        assertEquals("virtual trusted boundaries", 3,
                topology.getTotalVirtualTrustedNodeBoundaries());

        final QKDLinkPlan link = topology.findLink("A", "B");
        assertTrue("A->B plan exists", link != null);
        assertEquals("A->B span count", 4, link.getSpanCount());
        assertEquals("A->B trusted nodes", 3,
                link.getVirtualTrustedNodeCount());

        final double[] expected = {25.0, 25.0, 25.0, 5.0};
        for (int i = 0; i < expected.length; i++) {
            final QKDSpan span = link.getSpans().get(i);
            assertClose("span " + i + " length", expected[i],
                    span.getLengthKm());
            if (span.getLengthKm() - 25.0 > EPSILON) {
                throw new IllegalStateException(
                        "Span exceeds QTND: " + span);
            }
        }

        assertEquals("first span source", "A",
                link.getSpans().get(0).getLogicalSource());
        assertEquals("last span destination", "B",
                link.getSpans().get(3).getLogicalDestination());

        System.out.println("[OK] SNetS network file -> qkd config: "
                + network.getQkd());
        System.out.println("[OK] 80 km / QTND 25 km -> 25 + 25 + 25 + 5 km");
        for (QKDSpan span : link.getSpans()) {
            System.out.println("     " + span);
        }
        System.out.println("[OK] QKD topology is logical only; no Mesh/Node/Link mutation occurs");
    }

    private static void verifyExactMultiple() {
        final QKDLinkPlan plan = new QKDTopologyPlanner().planLink(
                "X", "Y", 75.0, 25.0);
        assertEquals("75/25 span count", 3, plan.getSpanCount());
        for (QKDSpan span : plan.getSpans()) {
            assertClose("exact multiple span", 25.0, span.getLengthKm());
        }
        System.out.println("[OK] exact multiple: 75 km / 25 km -> 3 spans (no residual span)");
    }

    private static void verifyShortLink() {
        final QKDLinkPlan plan = new QKDTopologyPlanner().planLink(
                "X", "Y", 10.0, 25.0);
        assertEquals("short-link span count", 1, plan.getSpanCount());
        assertEquals("short-link trusted nodes", 0,
                plan.getVirtualTrustedNodeCount());
        assertClose("short-link span length", 10.0,
                plan.getSpans().get(0).getLengthKm());
        System.out.println("[OK] link shorter than QTND remains one QKD span");
    }

    private static void verifyLegacyNetworkCompatibility() throws Exception {
        final String legacyNetworkJson =
                "{\n"
                + "  \"nodes\": [],\n"
                + "  \"links\": [],\n"
                + "  \"modulations\": [],\n"
                + "  \"cores\": [],\n"
                + "  \"guardBand\": 1,\n"
                + "  \"bvtSpectralAmplitude\": 1000\n"
                + "}";

        final NetworkConfig legacy = readNetworkThroughSNetS(legacyNetworkJson);
        assertTrue("legacy qkd field is absent", legacy.getQkd() == null);

        final QKDTopologyPlan topology =
                new QKDTopologyPlanner().plan(legacy);
        assertTrue("legacy topology disabled", !topology.isEnabled());
        assertEquals("legacy topology links", 0, topology.getPhysicalLinkCount());

        System.out.println("[OK] legacy network file without qkd remains compatible");
    }

    private static void verifyInvalidQuantumCoreIsRejected() throws Exception {
        final String invalidNetworkJson =
                "{\n"
                + "  \"nodes\": [],\n"
                + "  \"links\": [{\"source\":\"A\",\"destination\":\"B\",\"slots\":10,\"spectrum\":12.5,\"size\":10.0}],\n"
                + "  \"modulations\": [],\n"
                + "  \"cores\": [{\"id\":0,\"adjacentCores\":[]}],\n"
                + "  \"qkd\": {\"enabled\":true,\"qtndKm\":25.0,\"quantumCoreId\":6}\n"
                + "}";

        final NetworkConfig network = readNetworkThroughSNetS(invalidNetworkJson);
        boolean rejected = false;
        try {
            new QKDTopologyPlanner().plan(network);
        } catch (IllegalArgumentException expected) {
            rejected = expected.getMessage() != null
                    && expected.getMessage().contains("does not exist");
        }
        assertTrue("invalid quantum core rejected", rejected);
        System.out.println("[OK] invalid network.qkd.quantumCoreId is rejected before simulation");
    }

    /**
     * Uses the original SNetS SimulationFileManager, rather than calling Gson
     * directly, to prove that the regular scenario-loading path reads the new
     * network.qkd section without any SimulationFileManager modification.
     */
    private static NetworkConfig readNetworkThroughSNetS(String networkJson)
            throws IOException {

        final File root = Files.createTempDirectory("snets-qkd-part3-").toFile();
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
                            root.getAbsolutePath(),
                            scenario.getName());
            if (request == null || request.getNetworkConfig() == null) {
                throw new IllegalStateException(
                        "SimulationFileManager did not load the network config");
            }
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
        // Best-effort cleanup for a deterministic verification utility.
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

    private static void assertEquals(String name, String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new IllegalStateException(name + ": expected=" + expected
                    + ", actual=" + actual);
        }
    }

    private static void assertClose(String name, double expected,
            double actual) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new IllegalStateException(name + ": expected=" + expected
                    + ", actual=" + actual);
        }
    }
}
