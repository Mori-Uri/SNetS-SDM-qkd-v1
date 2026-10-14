package qkd.reference;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Article-base (Dibaj et al. 2024, multi-fiber scenario) reproduced with the
 * native SNetS engine.
 *
 * <p>Unlike {@link QKDReferenceArticleMain}, which replays the routed traces
 * of the Python reconstruction through the QKD layer, this driver builds
 * ordinary SNetS simulation folders (network, physicalLayer, simulation,
 * traffic, others) for Small Core, Butterfly and Metro and every QTND, so the
 * requests, routes, classical allocation, QTND segmentation, QCh/TDM sharing
 * and Q-TRx accounting are all produced by the simulator itself
 * ({@code simulationControl.Main}). The resulting folders can also be run
 * one by one with {@code java -jar SNetS-SDM-QKD.jar <case>} or from the
 * launcher.</p>
 *
 * <p>Model mapping of the multi-fiber scenario onto SNetS:</p>
 * <ul>
 * <li>10 classical fibers per link = 10 uncoupled "cores" (no adjacency,
 * crosstalk off) with 960 slots of 12.5 GHz = 160 channels of 75 GHz;</li>
 * <li>dedicated quantum fiber = 11th core reserved for QKD (960 QChs of
 * 12.5 GHz);</li>
 * <li>classical RMSTA: {@code qrmsta} (K = 2 paths, distance-adaptive
 * modulation, first fit on the 75 GHz grid, PICh channel reserved, path with
 * the lowest ending channel);</li>
 * <li>QKD layer: span-granularity QCh pools shared by both directions,
 * 256 TDM slots per QCh, QChs on the lowest-attenuation wavelengths,
 * p_b = p_dark (no classical noise in MF);</li>
 * <li>semi-static traffic: every request is QKD-protected and holds for the
 * whole run (holdRate ~ 0), source/destination uniform, classical rate
 * uniform in {100, 150, ..., 600} Gb/s, key update 64/128/256 us;</li>
 * <li>number of requests per replication = the reference request count of
 * each topology; Q-TRx usage = peak number of active QChs.</li>
 * </ul>
 *
 * <p>Usage: {@code java -cp SNetS-SDM-QKD.jar qkd.reference.QKDNativeReferenceMain
 * <prepare|run|analyze|all> <Dibaj_MF folder> [native folder] [--replications N] [--threads N]}</p>
 *
 * <p>Refer\u00eancias: [DIBAJ24] M. R. Dibaj et al., "Cost-Optimized Quantum Communication Networks: The Crucial Role of Trusted Node Placement in Multi-Band and Multi-Fiber Realms", IWCIT 2024 (artigo-base).</p>
 */
public final class QKDNativeReferenceMain {

    public static final double[] BIT_RATES_GBPS = {100, 150, 200, 250, 300, 350, 400, 450, 500, 550, 600};

    private QKDNativeReferenceMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java -cp <jar> qkd.reference.QKDNativeReferenceMain "
                    + "<prepare|run|analyze|all> <Dibaj_MF folder> [native folder] "
                    + "[--replications N] [--threads N] [--topologies a,b] [--qtnd 5,10,...]");
            System.exit(2);
        }
        String mode = args[0].toLowerCase(Locale.ROOT);
        File root = new File(args[1]).getAbsoluteFile();
        File nativeDir = null;
        int replications = -1;
        int threads = Math.max(1, Math.min(10, Runtime.getRuntime().availableProcessors()));
        List<String> topologies = null;
        int[] qtnds = null;
        for (int i = 2; i < args.length; i++) {
            if ("--replications".equals(args[i]) && i + 1 < args.length) replications = Integer.parseInt(args[++i]);
            else if ("--threads".equals(args[i]) && i + 1 < args.length) threads = Integer.parseInt(args[++i]);
            else if ("--topologies".equals(args[i]) && i + 1 < args.length) topologies = Arrays.asList(args[++i].split(","));
            else if ("--qtnd".equals(args[i]) && i + 1 < args.length) {
                String[] p = args[++i].split(",");
                qtnds = new int[p.length];
                for (int j = 0; j < p.length; j++) qtnds[j] = Integer.parseInt(p[j].trim());
            } else if (!args[i].startsWith("--")) nativeDir = new File(args[i]).getAbsoluteFile();
        }
        if (nativeDir == null) nativeDir = new File(root.getParentFile(), root.getName() + "_SNetS");
        ReferenceExperimentConfig config = ReferenceExperimentConfig.load(new File(root, "config.json"));
        if (topologies == null) topologies = config.getTopologies();
        if (qtnds == null) qtnds = config.getQtndValuesKm();
        if (replications <= 0) replications = config.getReplications().size();

        long start = System.currentTimeMillis();
        if ("prepare".equals(mode) || "all".equals(mode)) {
            prepare(root, nativeDir, config, topologies, qtnds, replications, threads);
        }
        if ("run".equals(mode) || "all".equals(mode)) {
            run(nativeDir, topologies, qtnds);
        }
        if ("analyze".equals(mode) || "all".equals(mode) || "run".equals(mode)) {
            boolean ok = analyze(root, nativeDir, topologies, qtnds);
            System.out.println("Elapsed seconds: " + (System.currentTimeMillis() - start) / 1000.0);
            if (!ok) System.exit(1);
        }
    }

    public static String caseName(String topology, int qtnd) {
        return String.format(Locale.US, "%s_QTND_%02d", topology, qtnd);
    }

    /** Aggregated results of the native run: {@code <native folder>/results}. */
    public static File resultsDir(File nativeDir) {
        return new File(nativeDir, "results");
    }

    static File caseDir(File nativeDir, String topology, int qtnd) {
        return new File(new File(nativeDir, topology), caseName(topology, qtnd));
    }

    // =================================================================== prepare

    public static void prepare(File root, File nativeDir, ReferenceExperimentConfig config,
            List<String> topologies, int[] qtnds, int replications, int threads) throws IOException {
        Map<String, Integer> counts = referenceRequestCounts(root);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        int created = 0;
        for (String topologyName : topologies) {
            ReferenceTopologySpec topology = ReferenceTopologySpec.load(
                    new File(new File(root, "topologies"), topologyName + ".json"));
            Integer n = counts.get(topologyName);
            int requests = n != null ? n.intValue()
                    : (int) Math.round(topology.getOfferedSkrMbps() / (7.0 / 3.0));
            for (int qtnd : qtnds) {
                File dir = caseDir(nativeDir, topologyName, qtnd);
                if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create " + dir);
                write(new File(dir, "network"), gson.toJson(network(topology, config, qtnd)));
                write(new File(dir, "physicalLayer"), gson.toJson(physicalLayer()));
                write(new File(dir, "simulation"), gson.toJson(simulation(requests, replications, threads)));
                write(new File(dir, "traffic"), traffic(topology));
                write(new File(dir, "others"), gson.toJson(others(config)));
                created++;
            }
        }
        write(new File(nativeDir, "README.txt"),
                "Cenarios SNetS gerados por qkd.reference.QKDNativeReferenceMain a partir de\n"
                + root.getAbsolutePath() + "\n\n"
                + "Cada pasta <topologia>/<topologia>_QTND_XX e um caso SNetS comum (MF: 10 fibras\n"
                + "classicas + 1 fibra quantica, qrmsta, QCh por trecho, TDM 256, trafego semiestatico).\n"
                + "Executar tudo e analisar:  java -cp SNetS-SDM-QKD.jar qkd.reference.QKDNativeReferenceMain run "
                + "<Dibaj_MF> <esta pasta>\n"
                + "Resultados agregados: results/ (snets_native_report.txt, *_statistics.csv, *_summary.csv).\n");
        System.out.println("Prepared " + created + " SNetS cases in " + nativeDir.getAbsolutePath());
    }

    public static Map<String, Integer> referenceRequestCounts(File root) throws IOException {
        Map<String, Integer> out = new HashMap<String, Integer>();
        File raw = new File(new File(root, "reference_python"), "replications_raw.csv");
        if (!raw.isFile()) return out;
        List<Map<String, String>> rows = readCsv(raw);
        for (Map<String, String> r : rows) {
            if (r.get("topology") != null && r.get("request_count") != null)
                out.put(r.get("topology"), Integer.valueOf(r.get("request_count").trim()));
        }
        return out;
    }

    public static Map<String, Object> network(ReferenceTopologySpec topology,
            ReferenceExperimentConfig config, int qtnd) {
        int classicalFibers = config.getClassicalFibersPerLink();
        int slotsPerChannel = 6;
        int slots = config.getClassicalChannelsPerFiber() * slotsPerChannel;
        if (config.getQuantumChannels() > slots) slots = config.getQuantumChannels();
        List<Object> nodes = new ArrayList<Object>();
        for (int node : topology.getNodes()) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("name", String.valueOf(node));
            m.put("transmitters", 1000000);
            m.put("receivers", 1000000);
            m.put("regenerators", 0);
            nodes.add(m);
        }
        List<Object> links = new ArrayList<Object>();
        for (ReferenceTopologySpec.Edge e : topology.getEdges()) {
            for (int dir = 0; dir < 2; dir++) {
                Map<String, Object> m = new LinkedHashMap<String, Object>();
                m.put("source", String.valueOf(dir == 0 ? e.getU() : e.getV()));
                m.put("destination", String.valueOf(dir == 0 ? e.getV() : e.getU()));
                m.put("slots", slots);
                m.put("spectrum", 12.5e9);
                m.put("size", e.getLengthKm());
                links.add(m);
            }
        }
        List<Object> modulations = new ArrayList<Object>();
        modulations.add(modulation("BPSK", 2, 100000.0));
        modulations.add(modulation("QPSK", 4, 2000.0));
        modulations.add(modulation("8QAM", 8, 1000.0));
        modulations.add(modulation("16QAM", 16, 500.0));
        modulations.add(modulation("32QAM", 32, 250.0));
        modulations.add(modulation("64QAM", 64, 125.0));
        List<Object> cores = new ArrayList<Object>();
        for (int c = 0; c <= classicalFibers; c++) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id", c);
            m.put("adjacentCores", new ArrayList<Integer>());
            cores.add(m);
        }
        Map<String, Object> qkd = new LinkedHashMap<String, Object>();
        qkd.put("enabled", true);
        qkd.put("qtndKm", (double) qtnd);
        qkd.put("quantumCoreId", classicalFibers);
        qkd.put("qchGranularity", "span");
        qkd.put("undirectedChannels", true);
        qkd.put("tdmSlots", config.getTdmSlots());
        qkd.put("wavelengthPolicy", "grid");
        Map<String, Object> net = new LinkedHashMap<String, Object>();
        net.put("nodes", nodes);
        net.put("links", links);
        net.put("modulations", modulations);
        net.put("cores", cores);
        net.put("guardBand", 0);
        net.put("bvtSpectralAmplitude", 100000);
        net.put("qkd", qkd);
        return net;
    }

    private static Map<String, Object> modulation(String name, double m, double reachKm) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("name", name);
        out.put("maxRange", reachKm);
        out.put("M", m);
        out.put("SNR", 0.0);
        out.put("XT", 0.0);
        return out;
    }

    public static Map<String, Object> physicalLayer() {
        Map<String, Object> p = new LinkedHashMap<String, Object>();
        p.put("physicalLayerModel", 0);
        p.put("crosstalkModel", 0);
        p.put("activeQoT", false);
        p.put("activeQoTForOther", false);
        p.put("activeASE", false);
        p.put("activeNLI", false);
        p.put("activeXT", false);
        p.put("activeXTForOther", false);
        // 25 Gb/s per bit/symbol in a 75 GHz channel (Table of the reconstruction).
        p.put("rateOfFEC", 5.0);
        p.put("typeOfTestQoT", 0);
        p.put("power", 0.0);
        p.put("spanLength", 80.0);
        p.put("fiberLoss", 0.2);
        p.put("fiberNonlinearity", 0.0013);
        p.put("fiberDispersion", 1.6e-05);
        p.put("centerFrequency", 1.9385e14);
        p.put("constantOfPlanck", 6.626e-34);
        p.put("noiseFigureOfOpticalAmplifier", 5.0);
        p.put("powerSaturationOfOpticalAmplifier", 16.0);
        p.put("noiseFactorModelParameterA1", 100.0);
        p.put("noiseFactorModelParameterA2", 4.0);
        p.put("typeOfAmplifierGain", 0);
        p.put("amplificationFrequency", 1.9385e14);
        p.put("switchInsertionLoss", 5.0);
        p.put("fixedPowerSpectralDensity", false);
        p.put("referenceBandwidthForPowerSpectralDensity", 1.25e10);
        p.put("propagationConstant", 1.0e7);
        p.put("bendingRadius", 0.01);
        p.put("couplingCoefficient", 0.0);
        p.put("corePitch", 4.5e-05);
        p.put("polarizationModes", 2.0);
        Map<String, Object> receiver = new LinkedHashMap<String, Object>();
        receiver.put("detectorGateWindowPs", 100.0);
        receiver.put("receiverOpticalEfficiency", 1.0);
        receiver.put("receiverTransferModel", "receiver_referenced");
        receiver.put("intercoreCrosstalkEnabled", false);
        p.put("qkdReceiver", receiver);
        Map<String, Object> q = new LinkedHashMap<String, Object>();
        q.put("meanPhotonNumber", 0.7);
        q.put("pulseIntervalPs", 250.0);
        q.put("errorCorrectionInefficiency", 1.16);
        q.put("misalignmentError", 0.015);
        q.put("detectorEfficiency", 0.3);
        q.put("darkCountProbability", 1e-8);
        q.put("qchBandwidthGHz", 12.5);
        q.put("qchPowerDbm", -80.0);
        q.put("attenuationFloorDbPerKm", 0.18);
        q.put("attenuationQuadraticDbPerKmNm2", 8e-6);
        q.put("attenuationCenterNm", 1550.0);
        p.put("qkdPhysical", q);
        return p;
    }

    public static Map<String, Object> simulation(int requests, int replications, int threads) {
        Map<String, Object> s = new LinkedHashMap<String, Object>();
        s.put("requests", requests);
        s.put("rmlsaType", 1);
        s.put("routing", "djk");
        s.put("kRouting", "newksp");
        s.put("spectrumAssignment", "firstfit");
        s.put("coreAndSpectrumAssignment", "qfirstfit");
        s.put("integratedRmlsa", "qrmsta");
        s.put("modulationSelection", "modulationbydistance");
        s.put("grooming", "notrafficgrooming");
        s.put("reallocation", "fsalfav1");
        s.put("powerAssignment", "cpsd");
        s.put("loadPoints", 1);
        s.put("replications", replications);
        Map<String, Object> metrics = new LinkedHashMap<String, Object>();
        metrics.put("BlockingProbability", true);
        metrics.put("BitRateBlockingProbability", false);
        metrics.put("SpectrumUtilization", false);
        metrics.put("SpectrumSizeStatistics", false);
        metrics.put("ExternalFragmentation", false);
        metrics.put("RelativeFragmentation", false);
        metrics.put("TransmittersReceiversRegeneratorsUtilization", false);
        metrics.put("ModulationUtilization", false);
        metrics.put("ConsumedEnergy", false);
        metrics.put("GroomingStatistics", false);
        metrics.put("DataSetInformation", false);
        metrics.put("PhysicalLayerStatistics", false);
        s.put("activeMetrics", metrics);
        s.put("regeneratorAssignment", "aar");
        s.put("networkType", 0);
        s.put("threads", Math.max(1, Math.min(threads, replications)));
        s.put("qkdProtectedRequestProbability", 1.0);
        Map<String, Object> key = new LinkedHashMap<String, Object>();
        key.put("keySizeBits", 256);
        key.put("updateIntervalsMicroseconds", Arrays.asList(64.0, 128.0, 256.0));
        key.put("randomSeed", 2024);
        s.put("qkdKeyDemand", key);
        s.put("trafficRandomSeed", 2024);
        s.put("qkdProtectionRandomSeed", 2025);
        s.put("qkdWarmupFraction", 0.0);
        s.put("qkdExactRequestCount", true);
        return s;
    }

    public static String traffic(ReferenceTopologySpec topology) {
        StringBuilder sb = new StringBuilder("{\"requestGenerators\":[\n");
        boolean first = true;
        for (int s : topology.getNodes()) {
            for (int d : topology.getNodes()) {
                if (s == d) continue;
                for (double rate : BIT_RATES_GBPS) {
                    if (!first) sb.append(",\n");
                    first = false;
                    sb.append(String.format(Locale.US,
                            "{\"source\":\"%d\",\"destination\":\"%d\",\"bitRate\":%.1f,"
                            + "\"arrivalRate\":1.0,\"holdRate\":1.0E-9,\"arrivalRateIncrease\":0.0}",
                            s, d, rate * 1e9));
                }
            }
        }
        return sb.append("\n]}").toString();
    }

    public static Map<String, Object> others(ReferenceExperimentConfig config) {
        Map<String, Object> v = new LinkedHashMap<String, Object>();
        v.put("k", String.valueOf(config.getKPaths()));
        v.put("classicalChannelGHz", "75");
        v.put("reservePichChannel", String.valueOf(config.isReservePichChannel()));
        v.put("guardChannels", String.valueOf(config.getClassicalGuardChannels()));
        Map<String, Object> o = new LinkedHashMap<String, Object>();
        o.put("variables", v);
        o.put("kbpWeights", new LinkedHashMap<String, Object>());
        return o;
    }

    // =================================================================== run

    /** Runs every prepared case through the normal SNetS entry point. */
    public static void run(File nativeDir, List<String> topologies, int[] qtnds) throws Exception {
        Class<?> main = Class.forName("simulationControl.Main");
        Method local = main.getMethod("localSimulation", String.class);
        int total = topologies.size() * qtnds.length, done = 0;
        for (String topology : topologies) {
            for (int qtnd : qtnds) {
                File dir = caseDir(nativeDir, topology, qtnd);
                if (!new File(dir, "simulation").isFile()) {
                    throw new IOException("Case not prepared: " + dir + " (run 'prepare' first)");
                }
                System.out.println("=== [" + (++done) + "/" + total + "] " + dir.getAbsolutePath());
                local.invoke(null, dir.getAbsolutePath());
                System.out.println("progress: " + (100.0 * done / total) + "%");
            }
        }
    }

    // =================================================================== analyze

    static final class Obs {
        String topology; int qtnd; int replication; double qtrx; double normalized;
        long protectedAttempts, accepted, rejected; double classicalBlocking = Double.NaN;
    }

    public static boolean analyze(File root, File nativeDir, List<String> topologies, int[] qtnds) throws IOException {
        File out = resultsDir(nativeDir);
        if (!out.isDirectory() && !out.mkdirs()) throw new IOException("Cannot create " + out);
        List<Obs> all = new ArrayList<Obs>();
        List<String> missing = new ArrayList<String>();
        for (String topology : topologies) {
            for (int qtnd : qtnds) {
                File dir = caseDir(nativeDir, topology, qtnd);
                File stats = new File(dir, caseName(topology, qtnd) + "_QKDStatistics.csv");
                if (!stats.isFile()) { missing.add(caseName(topology, qtnd)); continue; }
                Map<Integer, Double> classical = readClassicalBlocking(
                        new File(dir, caseName(topology, qtnd) + "_BlockingProbability.csv"));
                for (Map<String, String> r : readCsv(stats)) {
                    Obs o = new Obs();
                    o.topology = topology; o.qtnd = qtnd;
                    o.replication = Integer.parseInt(r.get("Replication").trim()) + 1;
                    o.qtrx = Double.parseDouble(r.get("PeakQTRxPairs").trim());
                    o.protectedAttempts = Long.parseLong(r.get("ProtectedAttempts").trim());
                    o.accepted = Long.parseLong(r.get("Accepted").trim());
                    o.rejected = Long.parseLong(r.get("Rejected").trim());
                    Double cb = classical.get(Integer.valueOf(o.replication - 1));
                    if (cb != null) o.classicalBlocking = cb.doubleValue();
                    all.add(o);
                }
            }
        }
        if (all.isEmpty()) {
            System.out.println("No QKDStatistics.csv found under " + nativeDir + ". Run the cases first.");
            return false;
        }
        // per-replication normalization (eq. 4.23 of the qualification)
        Map<String, Double> maxByRep = new HashMap<String, Double>();
        for (Obs o : all) {
            String k = o.topology + "|" + o.replication;
            Double m = maxByRep.get(k);
            if (m == null || o.qtrx > m) maxByRep.put(k, o.qtrx);
        }
        for (Obs o : all) {
            double m = maxByRep.get(o.topology + "|" + o.replication);
            o.normalized = m > 0 ? 100.0 * o.qtrx / m : 0.0;
        }
        Collections.sort(all, (a, b) -> a.topology.equals(b.topology)
                ? (a.replication != b.replication ? Integer.compare(a.replication, b.replication) : Integer.compare(a.qtnd, b.qtnd))
                : Integer.compare(topologies.indexOf(a.topology), topologies.indexOf(b.topology)));

        try (BufferedWriter w = new BufferedWriter(new FileWriter(new File(out, "snets_native_raw.csv")))) {
            w.write("topology,qtnd_km,replication,qtrx_usage,normalized_qtrx_usage_pct,protected_requests,qkd_accepted,qkd_rejected,classical_blocking_probability\n");
            for (Obs o : all) {
                w.write(String.format(Locale.US, "%s,%d,%d,%.0f,%.6f,%d,%d,%d,%s%n", o.topology, o.qtnd,
                        o.replication, o.qtrx, o.normalized, o.protectedAttempts, o.accepted, o.rejected,
                        Double.isNaN(o.classicalBlocking) ? "" : String.format(Locale.US, "%.8f", o.classicalBlocking)));
            }
        }

        Map<String, Map<Integer, Double>> article = readQtrx(new File(new File(root, "reference_python"),
                "reference_fig7_multifiber.csv"), "qtrx_usage");
        Map<String, Map<Integer, Double>> python = readQtrx(new File(new File(root, "reference_python"),
                "replication_statistics.csv"), "qtrx_mean");
        Map<String, Map<Integer, Double>> pythonCi = readQtrx(new File(new File(root, "reference_python"),
                "replication_statistics.csv"), "qtrx_margin_error95");

        StringBuilder report = new StringBuilder();
        report.append("SNetS-QKD - artigo-base (Dibaj et al. 2024, MF) executado no motor nativo do SNetS\n");
        report.append("==========================================================================\n\n");
        report.append("Casos: ").append(nativeDir.getAbsolutePath()).append('\n');
        if (!missing.isEmpty()) report.append("Casos sem resultado: ").append(missing).append('\n');
        report.append('\n');

        boolean allOk = missing.isEmpty();
        try (BufferedWriter stats = new BufferedWriter(new FileWriter(new File(out, "snets_native_statistics.csv")));
             BufferedWriter summary = new BufferedWriter(new FileWriter(new File(out, "snets_native_summary.csv")))) {
            stats.write("topology,qtnd_km,n,mean_qtrx,sample_std,ci95_margin,ci95_lower,ci95_upper,"
                    + "normalized_mean_pct,normalized_ci95_margin_pct,article_qtrx,article_normalized_pct,"
                    + "python_mean_qtrx,python_ci95_margin,article_inside_ci95,qkd_blocking_mean,classical_blocking_mean\n");
            summary.write("topology,optimum_qtnd_snets,optimum_qtnd_python,optimum_qtnd_article,"
                    + "replications_optimum_within_5km_of_25,mae_vs_article,rmse_vs_article,mae_vs_python,"
                    + "u_shape,qkd_blocking_max,classical_blocking_max\n");
            for (String topology : topologies) {
                TreeMap<Integer, List<Obs>> byQ = new TreeMap<Integer, List<Obs>>();
                for (Obs o : all) if (o.topology.equals(topology)) {
                    List<Obs> l = byQ.get(o.qtnd);
                    if (l == null) { l = new ArrayList<Obs>(); byQ.put(o.qtnd, l); }
                    l.add(o);
                }
                if (byQ.isEmpty()) continue;
                Map<Integer, Double> art = article.get(topology);
                Map<Integer, Double> py = python.get(topology);
                Map<Integer, Double> pyCi = pythonCi.get(topology);
                double artMax = 0;
                if (art != null) for (double v : art.values()) artMax = Math.max(artMax, v);
                double bestMean = Double.POSITIVE_INFINITY; int bestQ = -1;
                double maeA = 0, rmseA = 0, maeP = 0; int nA = 0, nP = 0;
                double qkdBlockMax = 0, classBlockMax = 0;
                Map<Integer, Double> means = new TreeMap<Integer, Double>();
                for (Map.Entry<Integer, List<Obs>> e : byQ.entrySet()) {
                    List<Double> v = new ArrayList<Double>(), vn = new ArrayList<Double>();
                    double qb = 0, cb = 0; int cbN = 0;
                    for (Obs o : e.getValue()) {
                        v.add(o.qtrx); vn.add(o.normalized);
                        qb += o.protectedAttempts > 0 ? (double) o.rejected / o.protectedAttempts : 0;
                        if (!Double.isNaN(o.classicalBlocking)) { cb += o.classicalBlocking; cbN++; }
                    }
                    qb /= e.getValue().size();
                    cb = cbN > 0 ? cb / cbN : Double.NaN;
                    qkdBlockMax = Math.max(qkdBlockMax, qb);
                    if (!Double.isNaN(cb)) classBlockMax = Math.max(classBlockMax, cb);
                    double[] s = meanCi(v), sn = meanCi(vn);
                    means.put(e.getKey(), s[0]);
                    if (s[0] < bestMean) { bestMean = s[0]; bestQ = e.getKey(); }
                    Double a = art == null ? null : art.get(e.getKey());
                    Double p = py == null ? null : py.get(e.getKey());
                    Double pc = pyCi == null ? null : pyCi.get(e.getKey());
                    if (a != null) { maeA += Math.abs(s[0] - a); rmseA += (s[0] - a) * (s[0] - a); nA++; }
                    if (p != null) { maeP += Math.abs(s[0] - p); nP++; }
                    boolean inside = a != null && a >= s[0] - s[2] && a <= s[0] + s[2];
                    stats.write(String.format(Locale.US, "%s,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%s,%s,%s,%s,%s,%.8f,%s%n",
                            topology, e.getKey(), v.size(), s[0], s[1], s[2], s[0] - s[2], s[0] + s[2], sn[0], sn[2],
                            a == null ? "" : fmt(a), a == null || artMax == 0 ? "" : fmt(100.0 * a / artMax),
                            p == null ? "" : fmt(p), pc == null ? "" : fmt(pc), String.valueOf(inside),
                            qb, Double.isNaN(cb) ? "" : String.format(Locale.US, "%.8f", cb)));
                }
                // optimum per replication
                Map<Integer, Integer> repBest = new TreeMap<Integer, Integer>();
                Map<Integer, Double> repBestVal = new HashMap<Integer, Double>();
                for (Obs o : all) if (o.topology.equals(topology)) {
                    Double cur = repBestVal.get(o.replication);
                    if (cur == null || o.qtrx < cur) { repBestVal.put(o.replication, o.qtrx); repBest.put(o.replication, o.qtnd); }
                }
                int within = 0;
                Map<Integer, Integer> hist = new TreeMap<Integer, Integer>();
                for (int q : repBest.values()) {
                    if (Math.abs(q - 25) <= 5) within++;
                    hist.put(q, hist.containsKey(q) ? hist.get(q) + 1 : 1);
                }
                int pyBest = argMin(py), artBest = argMin(art);
                Integer firstQ = byQ.firstKey(), lastQ = byQ.lastKey();
                boolean uShape = bestQ != firstQ && bestQ != lastQ
                        && means.get(firstQ) > bestMean && means.get(lastQ) > bestMean;
                summary.write(String.format(Locale.US, "%s,%d,%s,%s,%d/%d,%s,%s,%s,%s,%.8f,%.8f%n", topology, bestQ,
                        pyBest < 0 ? "" : String.valueOf(pyBest), artBest < 0 ? "" : String.valueOf(artBest),
                        within, repBest.size(), nA > 0 ? fmt(maeA / nA) : "", nA > 0 ? fmt(Math.sqrt(rmseA / nA)) : "",
                        nP > 0 ? fmt(maeP / nP) : "", String.valueOf(uShape), qkdBlockMax, classBlockMax));
                report.append(String.format(Locale.US, "%-11s QTND otimo SNetS=%d km | Python=%s | artigo=%s | "
                        + "otimo por replicacao=%s | MAE vs artigo=%s Q-TRx | MAE vs Python=%s Q-TRx | "
                        + "curva em U=%s | bloqueio QKD max=%.4f | bloqueio classico max=%.4f%n",
                        topology, bestQ, pyBest < 0 ? "-" : String.valueOf(pyBest),
                        artBest < 0 ? "-" : String.valueOf(artBest), hist,
                        nA > 0 ? fmt(maeA / nA) : "-", nP > 0 ? fmt(maeP / nP) : "-", uShape, qkdBlockMax, classBlockMax));
                boolean check = uShape && within == repBest.size();
                report.append("            criterio da qualificacao (otimo a no maximo 5 km de 25 km em todas as replicacoes; curva em U): ")
                        .append(check ? "OK" : "NAO ATENDIDO").append("\n\n");
                allOk &= check;
            }
        }
        report.append("Arquivos: snets_native_raw.csv, snets_native_statistics.csv, snets_native_summary.csv\n");
        report.append("Status geral: ").append(allOk ? "OK" : "VER DETALHES ACIMA").append('\n');
        write(new File(out, "snets_native_report.txt"), report.toString());
        System.out.print(report);
        return allOk;
    }

    private static Map<Integer, Double> readClassicalBlocking(File f) throws IOException {
        Map<Integer, Double> out = new HashMap<Integer, Double>();
        if (!f.isFile()) return out;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String header = r.readLine();
            if (header == null) return out;
            String[] h = header.split(",", -1);
            int repStart = -1;
            for (int i = 0; i < h.length; i++) if (h[i].trim().startsWith("rep")) { repStart = i; break; }
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split(",", -1);
                if (p.length > repStart && p[0].trim().equals("Blocking probability") && repStart > 0) {
                    for (int i = repStart; i < p.length; i++) {
                        try { out.put(i - repStart, Double.valueOf(p[i].trim())); } catch (NumberFormatException ignored) { }
                    }
                    break;
                }
            }
        }
        return out;
    }

    private static Map<String, Map<Integer, Double>> readQtrx(File f, String column) throws IOException {
        Map<String, Map<Integer, Double>> out = new HashMap<String, Map<Integer, Double>>();
        if (!f.isFile()) return out;
        for (Map<String, String> r : readCsv(f)) {
            String v = r.get(column);
            if (v == null || v.trim().isEmpty()) continue;
            Map<Integer, Double> m = out.get(r.get("topology"));
            if (m == null) { m = new TreeMap<Integer, Double>(); out.put(r.get("topology"), m); }
            m.put(Integer.valueOf(r.get("qtnd_km").trim()), Double.valueOf(v.trim()));
        }
        return out;
    }

    private static int argMin(Map<Integer, Double> m) {
        if (m == null || m.isEmpty()) return -1;
        int best = -1; double v = Double.POSITIVE_INFINITY;
        for (Map.Entry<Integer, Double> e : m.entrySet()) if (e.getValue() < v) { v = e.getValue(); best = e.getKey(); }
        return best;
    }

    /** mean, sample std, t-based 95% margin */
    static double[] meanCi(List<Double> v) {
        int n = v.size();
        double s = 0; for (double x : v) s += x;
        double mean = s / n, ss = 0;
        for (double x : v) ss += (x - mean) * (x - mean);
        double sd = n > 1 ? Math.sqrt(ss / (n - 1)) : 0;
        double[] t = {0, 12.706, 4.303, 3.182, 2.776, 2.571, 2.447, 2.365, 2.306, 2.262, 2.228, 2.201, 2.179, 2.160,
                2.145, 2.131, 2.120, 2.110, 2.101, 2.093, 2.086, 2.080, 2.074, 2.069, 2.064, 2.060, 2.056, 2.052,
                2.048, 2.045, 2.042};
        double tc = n - 1 >= 1 && n - 1 < t.length ? t[n - 1] : 1.96;
        return new double[] {mean, sd, n > 1 ? tc * sd / Math.sqrt(n) : 0};
    }

    public static List<Map<String, String>> readCsv(File f) throws IOException {
        List<Map<String, String>> rows = new ArrayList<Map<String, String>>();
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String header = r.readLine();
            if (header == null) return rows;
            String[] h = header.split(",", -1);
            String line;
            while ((line = r.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] p = line.split(",", -1);
                Map<String, String> m = new LinkedHashMap<String, String>();
                for (int i = 0; i < h.length && i < p.length; i++) m.put(h[i].trim(), p[i]);
                rows.add(m);
            }
        }
        return rows;
    }

    private static String fmt(double v) {
        return String.format(Locale.US, "%.4f", v);
    }

    private static void write(File f, String text) throws IOException {
        try (BufferedWriter w = new BufferedWriter(new FileWriter(f))) {
            w.write(text);
        }
    }
}
