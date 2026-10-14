package qkd.verification;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import gprmcsa.routing.Route;
import network.Circuit;
import network.Link;
import network.Mesh;
import network.Node;
import network.Pair;
import network.RequestGenerator;
import qkd.capacity.QKDSharedChannel;
import qkd.capacity.QKDSharedResourceManager;
import qkd.control.QKDActiveConnection;
import qkd.control.QKDControlPlane;
import qkd.control.QKDProvisioningResult;
import qkd.control.QKDProvisioningService;
import qkd.key.QKDKeyDemand;
import qkd.network.QKDLinkPlan;
import qkd.network.QKDMesh;
import qkd.network.QKDMeshFactory;
import qkd.traffic.QKDRequestGenerator;
import qkd.traffic.RequestProtectionType;
import gprmcsa.GPRMCSA;
import request.RequestForConnection;
import simulationControl.Util;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.OthersConfig;
import simulationControl.parsers.PhysicalLayerConfig;
import simulationControl.parsers.SimulationConfig;
import simulationControl.parsers.TrafficConfig;

/**
 * Part 23: the study scenario of the qualification (slides 26-37).
 *
 * <p>5-node topology (1-2 76 km, 1-3 86, 2-5 90, 5-3 80, 2-4 100, 3-4 70),
 * 7-core MCF with core 1 reserved to the QChs, QTND = 30 km, a QKD-protected
 * request 2 -&gt; 3 of 100 Gbit/s with 2 Mbit/s of key material. Checks:</p>
 * <ol>
 * <li>trusted-node planning: spans of at most QTND per link, 13 trusted-node sites;</li>
 * <li>route-continuous QCh: the same slot of core 1 on every link/span of the QKD
 *     route, first-fit, SKR of every span at the slot wavelength &ge; demand;</li>
 * <li>PICh: one slot of a classical core on every link of the QKD route;</li>
 * <li>alternative QKD route when the classical route has no free QCh slot;</li>
 * <li>blocking causes [SPECTRUM] / [SKR] and complete release;</li>
 * <li>per-generator QKD traffic fields (qkdProtectedProbability, keyRateMbps);</li>
 * <li>a complete SNetS simulation of the scenario writing QKDMetrics.csv.</li>
 * </ol>
 * Usage: [work dir] (default: temporary directory).
 */
public final class QKDQualificationScenarioVerification {

    public static void main(String[] args) throws Exception {
        if (args.length >= 2 && "--write-example".equals(args[0])) {
            // writes the example case used by the launcher (heavier load than the verification run)
            writeCase(new File(args[1]), 5000, 3, 3, 20.0);
            System.out.println("Example case written to " + new File(args[1]).getAbsolutePath());
            return;
        }
        Gson gson = new Gson();
        NetworkConfig network = gson.fromJson(gson.toJson(network(true)), NetworkConfig.class);
        TrafficConfig traffic = gson.fromJson(gson.toJson(traffic(0.2, 2.0)), TrafficConfig.class);
        PhysicalLayerConfig physical = gson.fromJson(gson.toJson(physicalLayer(false)), PhysicalLayerConfig.class);
        SimulationConfig simulation = gson.fromJson(gson.toJson(simulation(1, 1, 100)), SimulationConfig.class);
        Util util = new Util(); util.projectPath = ".";
        Mesh generic = QKDMeshFactory.create(network, traffic, physical, new OthersConfig(),
                new HashMap<String, HashMap<Double, Double>>(), util, simulation);
        check(generic instanceof QKDMesh, "fixture must create QKDMesh");
        QKDMesh mesh = (QKDMesh) generic;
        check(mesh.getQkdNetworkParameters().isRouteGranularity(), "qchGranularity=route not parsed");
        check(mesh.getQkdNetworkParameters().getAlternativeRoutes() == 2, "alternativeRoutes not parsed");
        check(mesh.getQkdNetworkParameters().getPichSlots() == 1, "pichSlots not parsed");

        // 1. trusted-node planning (slide 28)
        Map<String, double[]> expected = new LinkedHashMap<String, double[]>();
        expected.put("1-2", new double[] {30, 30, 16});
        expected.put("1-3", new double[] {30, 30, 26});
        expected.put("2-5", new double[] {30, 30, 30});
        expected.put("5-3", new double[] {30, 30, 20});
        expected.put("2-4", new double[] {30, 30, 30, 10});
        expected.put("3-4", new double[] {30, 30, 10});
        for (Map.Entry<String, double[]> e : expected.entrySet()) {
            String[] ab = e.getKey().split("-");
            QKDLinkPlan lp = mesh.getQkdTopologyPlan().findLink(ab[0], ab[1]);
            check(lp != null && lp.getSpanCount() == e.getValue().length, "span count of " + e.getKey());
            for (int i = 0; i < e.getValue().length; i++)
                check(Math.abs(lp.getSpans().get(i).getLengthKm() - e.getValue()[i]) < 1e-9, "span lengths of " + e.getKey());
        }
        check(mesh.getQkdTopologyPlan().getTrustedNodeCount() == 13, "13 trusted-node sites expected");
        System.out.println("[OK] QTND = 30 km: 76 -> 30+30+16, 86 -> 30+30+26, 90 -> 30x3, 80 -> 30+30+20, "
                + "100 -> 30x3+10, 70 -> 30+30+10; 13 trusted-node sites (slide 28)");

        GPRMCSA a = new GPRMCSA("notrafficgrooming", "completesharing", "djk", "ksp",
                "modulationbydistance", "firstfit", "aar", "qfirstfit", "fsalfav1", "cpsd");
        QKDControlPlane cp = new QKDControlPlane(mesh, GPRMCSA.RMCSA_SEQUENCIAL,
                a.instantiateGrooming(), a.instantiateIntegratedRSA(), a.instantiateRouting(),
                a.instantiateKRouting(), a.instantiateSpectrumAssignment(), a.instantiateModulationSelection(),
                a.instantiateCoreAndSpectrumAssignment(), a.instantiateReallocation(), a.instantiatePowerAssignment());
        QKDProvisioningService service = new QKDProvisioningService(mesh);
        QKDSharedResourceManager rm = service.getResourceManager();
        Pair p23 = pair(mesh, "2", "3");
        Route r213 = route(mesh, "2", "1", "3");
        QKDKeyDemand twoMbps = new QKDKeyDemand(256, 128.0);
        check(Math.abs(twoMbps.getRequiredKeyRateBps() - 2e6) < 1e-6, "256 bits / 128 us must be 2 Mbit/s");

        // 2. route-continuous QCh + 3. PICh (slides 30-34)
        QKDActiveConnection c1 = provision(cp, service, p23, r213, twoMbps);
        check(!c1.isAlternativeRoute() && c1.getQkdRoute() == r213, "QKD must follow the classical route when possible");
        for (Link l : r213.getLinkList()) {
            check(l.getCore(1).getUsedSlots() == 1, "one QKD-core slot per link of the route");
            check(!l.getCore(1).getSpectrum().getFreeSpectrumBands(0).isEmpty()
                    && l.getCore(1).getSpectrum().getFreeSpectrumBands(0).get(0)[0] == 2,
                    "first-fit: slot 1 of core 1 occupied on every link (continuity)");
        }
        check(rm.getActiveQTrxPairs() == 6, "Q-TRx = 3 + 3 spans on 2->1->3");
        double wl1 = Double.NaN;
        for (QKDSharedChannel ch : rm.snapshotChannels()) {
            check(ch.getAllocation().getSpectrumBand()[0] == 1, "same band on every link");
            if (Double.isNaN(wl1)) wl1 = ch.getWavelengthNm();
            check(Math.abs(ch.getWavelengthNm() - wl1) < 1e-12, "same wavelength on every link");
            check(ch.getCapacityBps() >= 2e6, "SKR of every span at the slot wavelength must reach 2 Mbit/s");
        }
        // Part 26: pichMode = segment (default) -> one PICh per link of the QKD route, shared by sessions
        check(c1.getPich() == null && c1.getPichShares() != null && c1.getPichShares().size() == r213.getLinkList().size(),
                "PICh: one shared PICh per link of the QKD route (pichMode = segment)");
        for (qkd.capacity.QKDPichPool.Channel pc : c1.getPichShares())
            check(pc.coreId == 0 && pc.band[0] == 1, "PICh: slot 1 of the lowest classical core on each link");
        for (Link l : r213.getLinkList()) check(l.getCore(0).getUsedSlots() == 1, "PICh slot on every link");
        System.out.println("[OK] 2->3 over 2-1-3: QCh in slot 1 of core 1 on every span (lambda="
                + String.format(java.util.Locale.US, "%.4f", wl1) + " nm), min SKR="
                + String.format(java.util.Locale.US, "%.2f", c1.getRawPhysicalMinimumSecretKeyRateBps() / 1e6)
                + " Mbit/s >= 2 Mbit/s, Q-TRx=6, PICh in slot 1 of core 0");

        QKDActiveConnection c2 = provision(cp, service, p23, r213, twoMbps);
        for (Link l : r213.getLinkList()) check(l.getCore(1).getUsedSlots() == 2, "second session takes the next slot");
        check(rm.getActiveQuantumChannelCount() == 4, "QChs are dedicated to their session");
        for (Link l : r213.getLinkList()) check(l.getCore(0).getUsedSlots() == 1, "second session shares the PICh of each link (no new slot)");
        for (int i = 0; i < c1.getPichShares().size(); i++)
            check(c1.getPichShares().get(i) == c2.getPichShares().get(i) && c2.getPichShares().get(i).getSessions() == 2,
                    "both sessions use the same PICh on each link");
        System.out.println("[OK] second session: next continuous slot (2) on every link; QChs dedicated; PIChs shared (Part 26)");

        // 4. alternative route: no free QKD slot on 1->3
        Link l13 = link(mesh, "1", "3");
        int n = l13.getCore(1).getNumOfSlots();
        int[] rest = new int[] {3, n};
        check(l13.getCore(1).useSpectrumForQKD(rest, 0), "fixture: fill core 1 of 1->3");
        QKDActiveConnection c3 = provision(cp, service, p23, r213, twoMbps);
        check(c3.isAlternativeRoute(), "QCh must move to an alternative route");
        check(!c3.getQkdRoute().containThisLink(l13), "alternative route must avoid 1->3");
        System.out.println("[OK] 1->3 has no free QCh slot -> QKD session on alternative route "
                + c3.getQkdRoute().getRouteInString() + " (slide 29)");

        // 5. blocking causes
        Link l24 = link(mesh, "2", "4"), l25 = link(mesh, "2", "5");
        List<int[]> f24 = fillQkdCore(l24), f25 = fillQkdCore(l25);
        QKDProvisioningResult blocked = service.provision(protectedRequest(cp, p23, r213, twoMbps));
        check(!blocked.isSuccess() && blocked.getFailureReason().startsWith("[SPECTRUM]"),
                "no continuous slot on any route must give [SPECTRUM]: " + blocked.getFailureReason());
        for (int[] b : f24) l24.getCore(1).liberateSpectrumForQKD(b, 0);
        for (int[] b : f25) l25.getCore(1).liberateSpectrumForQKD(b, 0);
        QKDProvisioningResult tooMuch = service.provision(protectedRequest(cp, p23, r213, new QKDKeyDemand(256, 0.05)));
        check(!tooMuch.isSuccess() && tooMuch.getFailureReason().startsWith("[SKR]"),
                "5120 Mbit/s demand must give [SKR]: " + tooMuch.getFailureReason());
        System.out.println("[OK] blocking causes: " + blocked.getFailureReason().substring(0, 10) + " / "
                + tooMuch.getFailureReason().substring(0, 5));

        service.release(c3); service.release(c2); service.release(c1);
        l13.getCore(1).liberateSpectrumForQKD(rest, 0);
        for (Link l : mesh.getLinkList()) {
            check(l.getCore(1).getUsedSlots() == 0, "QKD core slots leaked on " + l.getName());
            check(l.getCore(0).getUsedSlots() == 0, "PICh slots leaked on " + l.getName());
        }
        check(rm.getActiveQuantumChannelCount() == 0, "QChs leaked");
        System.out.println("[OK] QChs and PICh released on every link");

        // 6. per-generator QKD traffic fields
        int protectedCount = 0, total = 0;
        for (Pair pr : mesh.getPairList()) for (RequestGenerator g : pr.getRequestGenerators()) {
            check(g instanceof QKDRequestGenerator, "QKD generators expected");
            QKDRequestGenerator q = (QKDRequestGenerator) g;
            check(Math.abs(q.getProtectedRequestProbability() - 0.2) < 1e-12, "per-generator qkdProtectedProbability");
            check(q.getFixedKeyDemand() != null && Math.abs(q.getFixedKeyDemand().getRequiredKeyRateBps() - 2e6) < 1e-3,
                    "per-generator keyRateMbps = 2");
            for (int i = 0; i < 500; i++) { total++; if (q.drawProtectionType() == RequestProtectionType.QKD_PROTECTED) protectedCount++; }
        }
        double frac = (double) protectedCount / total;
        check(frac > 0.17 && frac < 0.23, "protected fraction ~0.2, got " + frac);
        System.out.println("[OK] traffic file: qkdProtectedProbability=0.2 (drawn " + String.format(java.util.Locale.US, "%.3f", frac)
                + ") and keyRateMbps=2 per generator");

        // 7. complete simulation of the scenario
        File work = args.length > 0 ? new File(args[0]) : Files.createTempDirectory("snets_qkd_quali").toFile();
        File dir = new File(work, "Exemplo_Qualificacao_QTND30");
        writeCase(dir, 2000, 2, 3, 1.0);
        Class<?> main = Class.forName("simulationControl.Main");
        Method local = main.getMethod("localSimulation", String.class);
        local.invoke(null, dir.getAbsolutePath());
        File metrics = new File(dir, dir.getName() + "_QKDMetrics.csv");
        check(metrics.isFile(), "QKDMetrics.csv not written: " + metrics);
        List<String> lines = Files.readAllLines(metrics.toPath(), StandardCharsets.UTF_8);
        check(lines.get(0).startsWith("Metrics,LoadPoint,src,dest, ,rep0"), "QKDMetrics header");
        double acc = meanOf(lines, "QKD acceptance ratio", 0), bp = meanOf(lines, "QKD blocking probability", 0);
        double prot = meanOf(lines, "Fraction of QKD-protected requests", 0);
        double tn = meanOf(lines, "Planned trusted nodes", 0);
        check(Math.abs(acc + bp - 1.0) < 1e-9, "acceptance + blocking must be 1");
        check(acc > 0.5, "most protected requests must be served at the first load point, got " + acc);
        check(prot > 0.15 && prot < 0.25, "protected fraction in the simulation ~0.2, got " + prot);
        check(tn == 13.0, "planned trusted nodes = 13");
        boolean perPair = false;
        for (String l : lines) if (l.startsWith("QKD blocking probability per pair,0,2,3,")) perPair = true;
        check(perPair, "per-pair QKD blocking row for 2->3");
        System.out.println("[OK] SNetS simulation of the scenario (" + dir.getName() + "): QKD acceptance="
                + String.format(java.util.Locale.US, "%.4f", acc) + ", protected fraction="
                + String.format(java.util.Locale.US, "%.3f", prot) + ", mean Q-TRx/session="
                + String.format(java.util.Locale.US, "%.2f", meanOf(lines, "Mean Q-TRx pairs per admitted session", 0))
                + ", QKDMetrics.csv in SNetS layout");
        System.out.println("\nQKD Part 23 qualification-scenario verification: ALL CHECKS PASSED");
    }

    // ------------------------------------------------------------------ helpers

    private static double meanOf(List<String> lines, String metric, int lp) {
        for (String l : lines) {
            String[] c = l.split(",");
            if (c.length > 6 && c[0].equals(metric) && c[1].equals(String.valueOf(lp))) {
                double s = 0; int k = 0;
                for (int i = 5; i < c.length; i++) { double v = Double.parseDouble(c[i]); if (!Double.isNaN(v)) { s += v; k++; } }
                return k == 0 ? Double.NaN : s / k;
            }
        }
        throw new AssertionError("metric not found: " + metric);
    }

    private static RequestForConnection protectedRequest(QKDControlPlane cp, Pair pair, Route route, QKDKeyDemand demand) {
        RequestForConnection r = new RequestForConnection();
        r.setPair(pair); r.setRequiredBitRate(100e9);
        r.setProtectionType(RequestProtectionType.QKD_PROTECTED); r.setQkdKeyDemand(demand);
        Circuit c = cp.createNewCircuit(r); c.setRoute(route);
        return r;
    }

    private static QKDActiveConnection provision(QKDControlPlane cp, QKDProvisioningService s, Pair pair,
            Route route, QKDKeyDemand demand) {
        QKDProvisioningResult result = s.provision(protectedRequest(cp, pair, route, demand));
        check(result.isSuccess(), "provisioning failed: " + result.getFailureReason());
        return result.getConnection();
    }

    private static Pair pair(Mesh mesh, String s, String d) {
        for (Pair p : mesh.getPairList()) if (s.equals(p.getSource().getName()) && d.equals(p.getDestination().getName())) return p;
        throw new IllegalStateException("pair " + s + "->" + d);
    }

    private static Route route(Mesh mesh, String... names) {
        Vector<Node> n = new Vector<Node>();
        for (String x : names) n.add(mesh.searchNode(x));
        return new Route(n);
    }

    private static List<int[]> fillQkdCore(Link l) throws Exception {
        List<int[]> out = new ArrayList<int[]>();
        for (int[] b : l.getCore(1).getFreeSpectrumBandsForQKD(0)) {
            int[] c = new int[] {b[0], b[1]};
            check(l.getCore(1).useSpectrumForQKD(c, 0), "fixture: fill QKD core of " + l.getName());
            out.add(c);
        }
        return out;
    }

    private static Link link(Mesh mesh, String a, String b) {
        return mesh.searchNode(a).getOxc().linkTo(mesh.searchNode(b).getOxc());
    }

    private static void check(boolean c, String m) { if (!c) throw new AssertionError(m); }

    /** Writes a complete SNetS case of the qualification scenario. */
    public static void writeCase(File dir, int requests, int loadPoints, int replications, double loadScale) throws Exception {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("cannot create " + dir);
        Gson g = new GsonBuilder().setPrettyPrinting().create();
        write(new File(dir, "network"), g.toJson(network(true)));
        write(new File(dir, "traffic"), g.toJson(traffic(0.2, 2.0, loadScale)));
        write(new File(dir, "physicalLayer"), g.toJson(physicalLayer(true)));
        write(new File(dir, "simulation"), g.toJson(simulation(requests, loadPoints, replications)));
        Map<String, Object> others = new LinkedHashMap<String, Object>();
        Map<String, Object> vars = new LinkedHashMap<String, Object>();
        vars.put("k", "3");
        others.put("variables", vars);
        others.put("kbpWeights", new LinkedHashMap<String, Object>());
        write(new File(dir, "others"), g.toJson(others));
    }

    private static void write(File f, String s) throws Exception {
        Files.write(f.toPath(), s.getBytes(StandardCharsets.UTF_8));
    }

    static Map<String, Object> network(boolean qkd) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        List<Object> nodes = new ArrayList<Object>();
        for (String n : new String[] {"1", "2", "3", "4", "5"}) {
            Map<String, Object> o = new LinkedHashMap<String, Object>();
            o.put("name", n); o.put("transmitters", 1000); o.put("receivers", 1000); o.put("regenerators", 0);
            nodes.add(o);
        }
        m.put("nodes", nodes);
        List<Object> links = new ArrayList<Object>();
        String[][] l = {{"1", "2", "76"}, {"1", "3", "86"}, {"2", "5", "90"}, {"5", "3", "80"}, {"2", "4", "100"}, {"3", "4", "70"}};
        for (String[] x : l) for (int d = 0; d < 2; d++) {
            Map<String, Object> o = new LinkedHashMap<String, Object>();
            o.put("source", x[d]); o.put("destination", x[1 - d]); o.put("slots", 320);
            o.put("spectrum", 1.25e10); o.put("size", Double.parseDouble(x[2]));
            links.add(o);
        }
        m.put("links", links);
        List<Object> mods = new ArrayList<Object>();
        Object[][] md = {{"4QAM", 5000.0, 4.0, 8.95, -19.03}, {"8QAM", 2500.0, 8.0, 13.15, -23.23},
            {"16QAM", 1250.0, 16.0, 15.49, -25.57}, {"32QAM", 625.0, 32.0, 18.51, -28.59}, {"64QAM", 312.0, 64.0, 21.28, -31.36}};
        for (Object[] x : md) {
            Map<String, Object> o = new LinkedHashMap<String, Object>();
            o.put("name", x[0]); o.put("maxRange", x[1]); o.put("M", x[2]); o.put("SNR", x[3]); o.put("XT", x[4]);
            mods.add(o);
        }
        m.put("modulations", mods);
        int[][] adj = {{1, 2, 3, 4, 5, 6}, {0, 2, 6}, {0, 1, 3}, {0, 2, 4}, {0, 3, 5}, {0, 4, 6}, {0, 1, 5}};
        List<Object> cores = new ArrayList<Object>();
        for (int i = 0; i < 7; i++) {
            Map<String, Object> o = new LinkedHashMap<String, Object>();
            o.put("id", i);
            List<Integer> a = new ArrayList<Integer>();
            for (int v : adj[i]) a.add(v);
            o.put("adjacentCores", a);
            cores.add(o);
        }
        m.put("cores", cores);
        m.put("guardBand", 1);
        m.put("bvtSpectralAmplitude", 1000);
        Map<String, Object> q = new LinkedHashMap<String, Object>();
        q.put("enabled", qkd); q.put("qtndKm", 30.0); q.put("quantumCoreId", 1);
        q.put("qchGranularity", "route"); q.put("wavelengthPolicy", "grid");
        q.put("alternativeRoutes", 2); q.put("pichSlots", 1);
        m.put("qkd", q);
        return m;
    }

    static Map<String, Object> traffic(double protectedProbability, double keyRateMbps) {
        return traffic(protectedProbability, keyRateMbps, 1.0);
    }

    static Map<String, Object> traffic(double protectedProbability, double keyRateMbps, double scale) {
        List<Object> gens = new ArrayList<Object>();
        String[] nodes = {"1", "2", "3", "4", "5"};
        double[] rates = {100e9, 200e9, 300e9, 400e9, 500e9};
        double[] share = {5, 4, 3, 2, 1};
        for (String s : nodes) for (String d : nodes) {
            if (s.equals(d)) continue;
            for (int i = 0; i < rates.length; i++) {
                Map<String, Object> o = new LinkedHashMap<String, Object>();
                o.put("source", s); o.put("destination", d); o.put("bitRate", rates[i]);
                o.put("arrivalRate", scale * 2.0 * share[i] / 15.0); o.put("holdRate", 1.0);
                o.put("arrivalRateIncrease", scale * 1.0 * share[i] / 15.0);
                o.put("qkdProtectedProbability", protectedProbability);
                o.put("keyRateMbps", keyRateMbps);
                gens.add(o);
            }
        }
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("requestGenerators", gens);
        return m;
    }

    static Map<String, Object> physicalLayer(boolean qot) {
        Map<String, Object> p = new LinkedHashMap<String, Object>();
        p.put("physicalLayerModel", 0); p.put("crosstalkModel", 0);
        p.put("activeQoT", qot); p.put("activeQoTForOther", qot); p.put("activeASE", qot); p.put("activeNLI", qot);
        p.put("activeXT", qot); p.put("activeXTForOther", qot);
        p.put("rateOfFEC", 0.25); p.put("typeOfTestQoT", 0); p.put("power", 0.0); p.put("spanLength", 80.0);
        p.put("fiberLoss", 0.2); p.put("fiberNonlinearity", 0.0013); p.put("fiberDispersion", 1.6e-5);
        p.put("centerFrequency", 1.9385e14); p.put("constantOfPlanck", 6.626e-34);
        p.put("noiseFigureOfOpticalAmplifier", 5.0); p.put("powerSaturationOfOpticalAmplifier", 16.0);
        p.put("noiseFactorModelParameterA1", 100.0); p.put("noiseFactorModelParameterA2", 4.0);
        p.put("typeOfAmplifierGain", 0); p.put("amplificationFrequency", 1.9385e14); p.put("switchInsertionLoss", 5.0);
        p.put("fixedPowerSpectralDensity", false); p.put("referenceBandwidthForPowerSpectralDensity", 1.25e10);
        p.put("propagationConstant", 1.0e7); p.put("bendingRadius", 0.01); p.put("couplingCoefficient", 0.012);
        p.put("corePitch", 4.5e-5); p.put("polarizationModes", 2.0);
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("detectorGateWindowPs", 100.0); r.put("receiverOpticalEfficiency", 1.0);
        r.put("receiverTransferModel", "receiver_referenced"); r.put("intercoreCrosstalkEnabled", true);
        p.put("qkdReceiver", r);
        return p;
    }

    static Map<String, Object> simulation(int requests, int loadPoints, int replications) {
        Map<String, Object> s = new LinkedHashMap<String, Object>();
        s.put("requests", requests); s.put("rmlsaType", 0); s.put("routing", "djk"); s.put("kRouting", "ksp");
        s.put("spectrumAssignment", "firstfit"); s.put("coreAndSpectrumAssignment", "qabne");
        s.put("integratedRmlsa", "completesharing"); s.put("modulationSelection", "modulationbydistance");
        s.put("grooming", "notrafficgrooming"); s.put("reallocation", "fsalfav1"); s.put("powerAssignment", "cpsd");
        s.put("loadPoints", loadPoints); s.put("replications", replications);
        Map<String, Object> met = new LinkedHashMap<String, Object>();
        for (String k : Arrays.asList("BlockingProbability", "BitRateBlockingProbability", "SpectrumUtilization", "QKDMetrics"))
            met.put(k, true);
        for (String k : Arrays.asList("SpectrumSizeStatistics", "ExternalFragmentation", "RelativeFragmentation",
                "TransmittersReceiversRegeneratorsUtilization", "ModulationUtilization", "ConsumedEnergy",
                "GroomingStatistics", "DataSetInformation", "PhysicalLayerStatistics"))
            met.put(k, false);
        s.put("activeMetrics", met);
        s.put("regeneratorAssignment", "aar"); s.put("networkType", 0); s.put("threads", requests > 2000 ? 8 : 2);
        s.put("qkdProtectedRequestProbability", 0.0);
        Map<String, Object> kd = new LinkedHashMap<String, Object>();
        kd.put("keySizeBits", 256); kd.put("updateIntervalsMicroseconds", Arrays.asList(128.0)); kd.put("randomSeed", 2024);
        s.put("qkdKeyDemand", kd);
        s.put("qkdWarmupFraction", 0.1);
        s.put("qkdExactRequestCount", true);
        s.put("trafficRandomSeed", 20241009);
        s.put("qkdProtectionRandomSeed", 20241010);
        return s;
    }
}
