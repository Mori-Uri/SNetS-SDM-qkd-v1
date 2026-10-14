package qkd.ladder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import qkd.reference.QKDNativeReferenceMain;
import qkd.reference.ReferenceExperimentConfig;
import qkd.reference.ReferenceTopologySpec;

/**
 * Builds the five SNetS configuration files of every ladder case.
 *
 * <p>Two families of scenarios exist:</p>
 * <ul>
 * <li>{@code ARTICLE}: the native article-base mapping of
 * {@link QKDNativeReferenceMain} (qrmsta, 75 GHz classical channels, QChs per
 * QTND span shared by TDM). Without modifiers the files are byte-identical to
 * the ones written by the native reproduction. Modifiers change one factor at
 * a time: multi-core fibre, crosstalk, QoT, dynamic traffic, QKD route mode,
 * alternative routes, PICh and per-generator key demand.</li>
 * <li>{@code QUALIFICATION}: the standard SNetS classical model of the
 * qualification example (12.5 GHz slots, qabne, QoT and XT thresholds,
 * 7-core MCF) used by the complete scenario.</li>
 * </ul>
 */
final class LadderScenario {

    enum Family { ARTICLE, QUALIFICATION }

    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Thresholds of the qualification (SNR, XT in dB); BPSK derived as QPSK - 3.01 dB. */
    static final Object[][] MODULATION_THRESHOLDS = {
        {"BPSK", 5.94, -16.02}, {"QPSK", 8.95, -19.03}, {"8QAM", 13.15, -23.23},
        {"16QAM", 15.49, -25.57}, {"32QAM", 18.51, -28.59}, {"64QAM", 21.28, -31.36},
    };

    // ------------------------------------------------------------------ specification

    static final class Spec implements Cloneable {
        Family family = Family.ARTICLE;
        String topology = "small_core";
        int qtnd = 25;
        int requests = 1000;
        int replications = 3;
        /** null = keep the article MF layout (10 classical fibres + 1 quantum). Otherwise 1, 2, 7, 12 or 19. */
        Integer cores = null;
        int quantumCore = 1;
        boolean qkdEnabled = true;
        boolean xt = false;
        boolean qot = false;
        double coupling = 0.012;
        double protectedProbability = 1.0;
        Double keyRateMbps = null;
        Double generatorProtectedProbability = null;
        String granularity = "span";
        Integer tdmSlots = null;
        Boolean undirected = null;
        int alternativeRoutes = 0;
        int pichSlots = 0;
        boolean metrics = false;
        boolean dynamic = false;
        double load0 = 0, loadIncrement = 0;
        int loadPoints = 1;
        double warmup = 0.0;
        Integer linkSlots = null;
        Integer seed = null;
        boolean reservePich = true;
        boolean wavelengthLanes = false;
        String qchSlotPolicy = null;
        double[] bitRatesGbps = null;
        double[] bitRateWeights = null;
        String[] onlyPair = null;
        /** Part 26: overrides of the qkdPhysical block (e.g. Moghaddam et al. 2021, Table I). */
        Map<String, Object> qkdPhysical = null;
        /** Part 26: sequential RMSA with Dijkstra by length (classical and QKD route = shortest path). */
        boolean shortestPath = false;

        Spec copy() {
            try {
                return (Spec) clone();
            } catch (CloneNotSupportedException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    // ------------------------------------------------------------------ context

    final File dibajRoot;
    final File topologyDir;
    final ReferenceExperimentConfig config;
    final Map<String, Integer> referenceCounts;

    LadderScenario(File dibajRoot, File ladderRoot) throws IOException {
        this.dibajRoot = dibajRoot;
        this.config = ReferenceExperimentConfig.load(new File(dibajRoot, "config.json"));
        this.referenceCounts = QKDNativeReferenceMain.referenceRequestCounts(dibajRoot);
        this.topologyDir = new File(ladderRoot, "topologies");
        writeExtraTopologies();
    }

    /** Requests of the article for a topology (offered SKR / mean key demand). */
    int referenceRequests(String topology) throws IOException {
        Integer n = referenceCounts.get(topology);
        if (n != null) return n.intValue();
        ReferenceTopologySpec t = topology(topology);
        return (int) Math.round(t.getOfferedSkrMbps() / (7.0 / 3.0));
    }

    ReferenceTopologySpec topology(String name) throws IOException {
        File f = new File(new File(dibajRoot, "topologies"), name + ".json");
        if (!f.isFile()) f = new File(topologyDir, name + ".json");
        return ReferenceTopologySpec.load(f);
    }

    /** USA (24 nodes) and a two-node link, not part of the article. */
    private void writeExtraTopologies() throws IOException {
        String usa = "1-2:400 1-3:500 2-3:475 2-4:550 3-5:500 3-9:600 3-11:950 4-5:500 4-6:125 4-7:500 5-6:425 5-8:550 5-9:500 "
                + "6-7:400 7-8:600 8-10:450 9-10:500 9-11:700 9-12:500 10-13:475 10-14:400 11-12:450 11-15:1300 11-16:650 "
                + "12-13:450 12-18:500 13-14:325 13-21:550 14-23:600 15-17:600 16-17:650 16-18:300 17-19:350 18-19:500 "
                + "18-20:400 18-21:500 19-20:150 20-21:425 20-22:300 21-22:500 21-23:400 22-24:450 23-24:450";
        writeTopology("usa", usa);
        writeTopology("two_nodes", "1-2:50");
        // Moghaddam, Beyranvand, Salehi, IEEE JSAC 39(9), 2021, Fig. 6(a) and 6(b)
        writeTopology("moghaddam_core", "1-2:100 1-4:86 2-5:90 2-3:76 4-5:80 3-4:70");
        writeTopology("moghaddam_metro", "1-2:25 1-8:30 2-3:20 2-8:40 3-4:35 3-5:40 4-5:30 4-6:20 5-6:45 6-7:40 7-8:50");
    }

    private void writeTopology(String name, String links) throws IOException {
        StringBuilder sb = new StringBuilder("{\n  \"name\": \"" + name + "\",\n  \"offeredClassicalGbps\": 0.0,\n  \"offeredSkrMbps\": 0.0,\n  \"edges\": [\n");
        String[] parts = links.trim().split("\\s+");
        for (int i = 0; i < parts.length; i++) {
            String[] ab = parts[i].split("[-:]");
            sb.append(String.format(Locale.US, "    {\"u\":%s,\"v\":%s,\"lengthKm\":%s.0}%s%n", ab[0], ab[1], ab[2], i + 1 < parts.length ? "," : ""));
        }
        sb.append("  ]\n}\n");
        LadderIO.write(new File(topologyDir, name + ".json"), sb.toString());
    }

    // ------------------------------------------------------------------ files

    /** Map file name -> content of the five SNetS configuration files. */
    Map<String, String> files(Spec s) throws IOException {
        return s.family == Family.ARTICLE ? articleFiles(s) : qualificationFiles(s);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> articleFiles(Spec s) throws IOException {
        ReferenceTopologySpec topo = topology(s.topology);
        Map<String, Object> net = QKDNativeReferenceMain.network(topo, config, s.qtnd);
        Map<String, Object> phy = QKDNativeReferenceMain.physicalLayer();
        Map<String, Object> sim = QKDNativeReferenceMain.simulation(s.requests, s.replications, Math.min(10, s.replications));
        Map<String, Object> oth = QKDNativeReferenceMain.others(config);
        String traffic = s.dynamic || s.onlyPair != null || s.bitRatesGbps != null || s.keyRateMbps != null
                || s.generatorProtectedProbability != null ? traffic(topo, s) : QKDNativeReferenceMain.traffic(topo);

        // ---- network
        if (s.cores != null) {
            net.put("cores", coreList(s.cores));
        }
        Map<String, Object> qkd = (Map<String, Object>) net.get("qkd");
        if (s.cores != null) qkd.put("quantumCoreId", s.quantumCore);
        if (!s.qkdEnabled) qkd.put("enabled", false);
        if (!"span".equals(s.granularity)) {
            qkd.put("qchGranularity", s.granularity);
            qkd.put("tdmSlots", 0);
            qkd.put("undirectedChannels", false);
        }
        if (s.tdmSlots != null) qkd.put("tdmSlots", s.tdmSlots);
        if (s.undirected != null) qkd.put("undirectedChannels", s.undirected);
        if (s.alternativeRoutes > 0) qkd.put("alternativeRoutes", s.alternativeRoutes);
        if (s.wavelengthLanes && "span".equals(s.granularity)) qkd.put("spanLanePlacement", "wavelength");
        if (s.wavelengthLanes && "route".equals(s.granularity)) qkd.put("qchSlotPolicy", "lowestloss");
        if (s.qchSlotPolicy != null) qkd.put("qchSlotPolicy", s.qchSlotPolicy);
        if (s.pichSlots > 0) { qkd.put("pichSlots", s.pichSlots); qkd.put("pichMode", "segment"); }
        if (s.linkSlots != null) {
            for (Object l : (List<Object>) net.get("links")) ((Map<String, Object>) l).put("slots", s.linkSlots);
        }
        if (s.qot) {
            for (Object o : (List<Object>) net.get("modulations")) {
                Map<String, Object> m = (Map<String, Object>) o;
                for (Object[] t : MODULATION_THRESHOLDS) {
                    if (t[0].equals(m.get("name"))) {
                        if (s.qot) m.put("SNR", t[1]);
                    }
                }
            }
        }

        // ---- physical layer
        if (s.xt) {
            phy.put("activeXT", true);
            phy.put("activeXTForOther", true);
            phy.put("couplingCoefficient", s.coupling);
            ((Map<String, Object>) phy.get("qkdReceiver")).put("intercoreCrosstalkEnabled", true);
        }
        if (s.qot) {
            phy.put("activeQoT", true);
            phy.put("activeQoTForOther", true);
            phy.put("activeASE", true);
            phy.put("activeNLI", true);
        }

        if (s.qkdPhysical != null) ((Map<String, Object>) phy.get("qkdPhysical")).putAll(s.qkdPhysical);

        // ---- simulation
        Map<String, Object> am = (Map<String, Object>) sim.get("activeMetrics");
        if (s.metrics) {
            am.put("BitRateBlockingProbability", true);
            am.put("SpectrumUtilization", true);
            am.put("QKDMetrics", true);
        }
        if (s.protectedProbability != 1.0) sim.put("qkdProtectedRequestProbability", s.protectedProbability);
        if (s.dynamic) {
            sim.put("loadPoints", s.loadPoints);
            sim.put("qkdWarmupFraction", s.warmup);
        }
        if (s.seed != null) sim.put("trafficRandomSeed", s.seed);
        if (s.shortestPath) {
            sim.put("rmlsaType", 0);
            sim.put("routing", "djk");
        }

        // ---- others
        if (!s.reservePich) ((Map<String, Object>) oth.get("variables")).put("reservePichChannel", "false");

        Map<String, String> out = new LinkedHashMap<String, String>();
        out.put("network", GSON.toJson(net));
        out.put("physicalLayer", GSON.toJson(phy));
        out.put("simulation", GSON.toJson(sim));
        out.put("traffic", traffic);
        out.put("others", GSON.toJson(oth));
        return out;
    }

    /** Dynamic (or restricted) traffic: every pair and bit rate, total load split equally. */
    private String traffic(ReferenceTopologySpec topo, Spec s) {
        double[] rates = s.bitRatesGbps != null ? s.bitRatesGbps : QKDNativeReferenceMain.BIT_RATES_GBPS;
        double[] w = s.bitRateWeights != null ? s.bitRateWeights : null;
        double wsum = 0;
        for (int i = 0; i < rates.length; i++) wsum += w == null ? 1 : w[i];
        List<int[]> pairs = new ArrayList<int[]>();
        for (int a : topo.getNodes()) for (int b : topo.getNodes()) {
            if (a == b) continue;
            if (s.onlyPair != null && !(String.valueOf(a).equals(s.onlyPair[0]) && String.valueOf(b).equals(s.onlyPair[1]))) continue;
            pairs.add(new int[] {a, b});
        }
        StringBuilder sb = new StringBuilder("{\"requestGenerators\":[\n");
        boolean first = true;
        for (int[] p : pairs) {
            for (int i = 0; i < rates.length; i++) {
                double share = (w == null ? 1 : w[i]) / wsum / pairs.size();
                double lambda, inc, hold;
                if (s.dynamic) { lambda = s.load0 * share; inc = s.loadIncrement * share; hold = 1.0; }
                else { lambda = 1.0; inc = 0.0; hold = 1.0E-9; }
                if (!first) sb.append(",\n");
                first = false;
                sb.append(String.format(Locale.US,
                        "{\"source\":\"%d\",\"destination\":\"%d\",\"bitRate\":%.1f,\"arrivalRate\":%s,\"holdRate\":%s,\"arrivalRateIncrease\":%s",
                        p[0], p[1], rates[i] * 1e9, num(lambda), num(hold), num(inc)));
                if (s.generatorProtectedProbability != null) sb.append(",\"qkdProtectedProbability\":").append(num(s.generatorProtectedProbability));
                if (s.keyRateMbps != null) sb.append(",\"keyRateMbps\":").append(num(s.keyRateMbps));
                sb.append('}');
            }
        }
        return sb.append("\n]}").toString();
    }

    private static String num(double v) {
        return Double.toString(v);
    }

    // ------------------------------------------------------------------ multi-core layouts

    /** Core list (id + adjacentCores) of the 1, 2, 7, 12 and 19-core layouts used by the launcher. */
    static List<Object> coreList(int n) {
        int[][] adj;
        switch (n) {
            case 1: adj = new int[][] {{}}; break;
            case 2: adj = new int[][] {{1}, {0}}; break;
            case 3: adj = new int[][] {{1, 2}, {0, 2}, {0, 1}}; break;
            case 7: adj = new int[][] {{1, 2, 3, 4, 5, 6}, {0, 2, 6}, {0, 1, 3}, {0, 2, 4}, {0, 3, 5}, {0, 4, 6}, {0, 1, 5}}; break;
            case 12: {
                adj = new int[12][];
                for (int i = 0; i < 12; i++) adj[i] = i == 0 ? new int[] {1, 11} : i == 11 ? new int[] {0, 10} : new int[] {i - 1, i + 1};
                break;
            }
            case 19: adj = new int[][] {{1, 3, 4}, {0, 2, 4, 5}, {1, 5, 6}, {0, 4, 7, 8}, {0, 1, 3, 5, 8, 18}, {1, 2, 4, 6, 9, 18},
                {2, 5, 9, 10}, {3, 8, 11}, {3, 4, 7, 11, 12, 18}, {5, 6, 10, 13, 14, 18}, {6, 9, 14}, {7, 8, 12, 15},
                {8, 11, 13, 15, 16, 18}, {9, 12, 14, 16, 17, 18}, {9, 10, 13, 17}, {11, 12, 16}, {12, 13, 15, 17}, {13, 14, 16},
                {4, 5, 8, 9, 12, 13}}; break;
            default: throw new IllegalArgumentException("Layout de n\u00facleos n\u00e3o suportado: " + n);
        }
        List<Object> out = new ArrayList<Object>();
        for (int i = 0; i < adj.length; i++) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", i);
            List<Integer> a = new ArrayList<Integer>();
            for (int x : adj[i]) a.add(x);
            m.put("adjacentCores", a);
            out.add(m);
        }
        return out;
    }

    static int neighbours(int cores, int core) {
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) coreList(cores).get(core);
        return ((List<?>) m.get("adjacentCores")).size();
    }

    // ------------------------------------------------------------------ qualification family

    private Map<String, String> qualificationFiles(Spec s) throws IOException {
        ReferenceTopologySpec topo = topology(s.topology);
        List<Object> nodes = new ArrayList<Object>();
        for (int n : topo.getNodes()) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("name", String.valueOf(n));
            m.put("transmitters", 1000);
            m.put("receivers", 1000);
            m.put("regenerators", 0);
            nodes.add(m);
        }
        List<Object> links = new ArrayList<Object>();
        for (ReferenceTopologySpec.Edge e : topo.getEdges()) {
            for (int dir = 0; dir < 2; dir++) {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("source", String.valueOf(dir == 0 ? e.getU() : e.getV()));
                m.put("destination", String.valueOf(dir == 0 ? e.getV() : e.getU()));
                m.put("slots", s.linkSlots != null ? s.linkSlots : 320);
                m.put("spectrum", 1.25E10);
                m.put("size", e.getLengthKm());
                links.add(m);
            }
        }
        List<Object> mods = new ArrayList<Object>();
        Object[][] q = {{"4QAM", 5000.0, 4.0, 8.95, -19.03}, {"8QAM", 2500.0, 8.0, 13.15, -23.23},
            {"16QAM", 1250.0, 16.0, 15.49, -25.57}, {"32QAM", 625.0, 32.0, 18.51, -28.59}, {"64QAM", 312.0, 64.0, 21.28, -31.36}};
        for (Object[] r : q) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("name", r[0]); m.put("maxRange", r[1]); m.put("M", r[2]); m.put("SNR", r[3]); m.put("XT", r[4]);
            mods.add(m);
        }
        Map<String, Object> qkd = new LinkedHashMap<String, Object>();
        qkd.put("enabled", s.qkdEnabled);
        qkd.put("qtndKm", (double) s.qtnd);
        qkd.put("quantumCoreId", s.quantumCore);
        qkd.put("qchGranularity", s.granularity);
        qkd.put("wavelengthPolicy", "grid");
        qkd.put("alternativeRoutes", s.alternativeRoutes);
        qkd.put("pichSlots", s.pichSlots);
        if (s.pichSlots > 0) qkd.put("pichMode", "segment");
        Map<String, Object> net = new LinkedHashMap<String, Object>();
        net.put("nodes", nodes);
        net.put("links", links);
        net.put("modulations", mods);
        net.put("cores", coreList(s.cores != null ? s.cores : 7));
        net.put("guardBand", 1);
        net.put("bvtSpectralAmplitude", 1000);
        net.put("qkd", qkd);

        Map<String, Object> phy = new LinkedHashMap<String, Object>();
        Object[][] p = {{"physicalLayerModel", 0}, {"crosstalkModel", 0}, {"activeQoT", s.qot}, {"activeQoTForOther", s.qot},
            {"activeASE", s.qot}, {"activeNLI", s.qot}, {"activeXT", s.xt}, {"activeXTForOther", s.xt}, {"rateOfFEC", 0.25},
            {"typeOfTestQoT", 0}, {"power", 0.0}, {"spanLength", 80.0}, {"fiberLoss", 0.2}, {"fiberNonlinearity", 0.0013},
            {"fiberDispersion", 1.6E-5}, {"centerFrequency", 1.9385E14}, {"constantOfPlanck", 6.626E-34},
            {"noiseFigureOfOpticalAmplifier", 5.0}, {"powerSaturationOfOpticalAmplifier", 16.0},
            {"noiseFactorModelParameterA1", 100.0}, {"noiseFactorModelParameterA2", 4.0}, {"typeOfAmplifierGain", 0},
            {"amplificationFrequency", 1.9385E14}, {"switchInsertionLoss", 5.0}, {"fixedPowerSpectralDensity", false},
            {"referenceBandwidthForPowerSpectralDensity", 1.25E10}, {"propagationConstant", 1.0E7}, {"bendingRadius", 0.01},
            {"couplingCoefficient", s.xt ? s.coupling : 0.0}, {"corePitch", 4.5E-5}, {"polarizationModes", 2.0}};
        for (Object[] r : p) phy.put((String) r[0], r[1]);
        Map<String, Object> rx = new LinkedHashMap<String, Object>();
        rx.put("detectorGateWindowPs", 100.0);
        rx.put("receiverOpticalEfficiency", 1.0);
        rx.put("receiverTransferModel", "receiver_referenced");
        rx.put("intercoreCrosstalkEnabled", s.xt);
        phy.put("qkdReceiver", rx);

        Map<String, Object> sim = new LinkedHashMap<String, Object>();
        sim.put("requests", s.requests);
        sim.put("rmlsaType", 0);
        sim.put("routing", "djk");
        sim.put("kRouting", "ksp");
        sim.put("spectrumAssignment", "firstfit");
        sim.put("coreAndSpectrumAssignment", "qabne");
        sim.put("integratedRmlsa", "completesharing");
        sim.put("modulationSelection", "modulationbydistance");
        sim.put("grooming", "notrafficgrooming");
        sim.put("reallocation", "fsalfav1");
        sim.put("powerAssignment", "cpsd");
        sim.put("loadPoints", s.loadPoints);
        sim.put("replications", s.replications);
        Map<String, Object> am = new LinkedHashMap<String, Object>();
        for (String k : new String[] {"BlockingProbability", "BitRateBlockingProbability", "SpectrumUtilization", "QKDMetrics"}) am.put(k, true);
        for (String k : new String[] {"SpectrumSizeStatistics", "ExternalFragmentation", "RelativeFragmentation",
            "TransmittersReceiversRegeneratorsUtilization", "ModulationUtilization", "ConsumedEnergy", "GroomingStatistics",
            "DataSetInformation", "PhysicalLayerStatistics"}) am.put(k, false);
        sim.put("activeMetrics", am);
        sim.put("regeneratorAssignment", "aar");
        sim.put("networkType", 0);
        sim.put("threads", Math.min(10, s.replications));
        sim.put("qkdProtectedRequestProbability", 0.0);
        Map<String, Object> key = new LinkedHashMap<String, Object>();
        key.put("keySizeBits", 256);
        key.put("updateIntervalsMicroseconds", Arrays.asList(128.0));
        key.put("randomSeed", 2024);
        sim.put("qkdKeyDemand", key);
        sim.put("qkdWarmupFraction", s.warmup);
        sim.put("qkdExactRequestCount", true);
        sim.put("trafficRandomSeed", s.seed != null ? s.seed : 20241009);
        sim.put("qkdProtectionRandomSeed", 20241010);

        Map<String, Object> vars = new LinkedHashMap<String, Object>();
        vars.put("k", "3");
        Map<String, Object> oth = new LinkedHashMap<String, Object>();
        oth.put("variables", vars);
        oth.put("kbpWeights", new LinkedHashMap<String, Object>());

        Spec t = s.copy();
        if (t.bitRatesGbps == null) { t.bitRatesGbps = new double[] {100, 200, 300, 400, 500}; t.bitRateWeights = new double[] {5, 4, 3, 2, 1}; }
        t.dynamic = true;
        Map<String, String> out = new LinkedHashMap<String, String>();
        out.put("network", GSON.toJson(net));
        out.put("physicalLayer", GSON.toJson(phy));
        out.put("simulation", GSON.toJson(sim));
        out.put("traffic", traffic(topo, t));
        out.put("others", GSON.toJson(oth));
        return out;
    }
}
