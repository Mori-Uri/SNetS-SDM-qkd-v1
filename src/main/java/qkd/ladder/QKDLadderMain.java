package qkd.ladder;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import qkd.ladder.LadderScenario.Family;
import qkd.ladder.LadderScenario.Spec;
import qkd.physical.BB84DecoyModel;
import qkd.physical.QKDPhysicalResult;
import qkd.reference.QKDNativeReferenceMain;
import qkd.reference.ReferenceTopologySpec;

/**
 * Verification ladder: a sequence of SNetS scenarios that bridges the
 * article-base reproduction (Dibaj et al. 2024, multi-fiber) to a complete
 * SDM/QKD scenario on a large topology, changing one factor per step, plus
 * extra verifications with known answers (Erlang-B, analytic trusted-node
 * count, independent SKR, limits, monotonicity, reproducibility, conservation
 * and symmetry).
 *
 * <p>Every step writes ordinary SNetS cases under
 * {@code verificacao/QKD_ladder/<perfil>/<passo>/} (they can be opened in the
 * launcher) and a report with checks in the step folder (checks.csv,
 * data.csv, charts.csv, cases.txt, report.txt, status.txt).</p>
 *
 * <p>Usage: {@code java -cp SNetS-SDM-QKD.jar qkd.ladder.QKDLadderMain <passo|list>
 * <pasta simulations> [--profile rapido|completo] [--threads N] [--force] [--out <pasta verificacao>]}</p>
 *
 * <p>Part 27: the outputs go to {@code <pasta verificacao>/QKD_ladder/<perfil>/<passo>/}, by default the folder
 * {@code verificacao} next to the simulations folder; the article base is read from
 * {@code verificacao/QKD_reference/Dibaj_MF} (or the old {@code simulations/QKD_reference/Dibaj_MF}).</p>
 *
 * <p>Refer\u00eancias: [DIBAJ24], [MORI26], [MOGH21], [AHMED26a], [JAIN91] R. Jain, "The Art of Computer Systems Performance Analysis", Wiley, 1991 (Erlang-B, lei de Little, intervalos de confian\u00e7a).</p>
 */
public final class QKDLadderMain {

    static final String[] STEPS = {"A1", "A2", "A3", "B1", "B2", "B2L", "B3", "C1", "C2", "D1", "D2",
        "E1", "E2", "E3", "E4", "F1", "F2", "X1", "X2", "X3", "X4", "X5", "X6", "X7", "X8",
        "M1", "M2", "M3", "Q1", "Q2", "Q3"};
    static final String[] ARTICLE_TOPOLOGIES = {"small_core", "butterfly", "metro"};
    static final String[] TOPO_LABEL = {"Small Core", "Butterfly", "Metro"};
    static final double MEAN_BIT_RATE_GBPS = 350.0;
    /**
     * Load of the SDM steps relative to the article load scaled by the number of
     * classical cores: in A3 the 1 % blocking operating point of the article is
     * reached at about twice the nominal request count, and with this load the
     * classical spectrum overlaps the QChs (placed at the lowest-attenuation
     * wavelengths, mid-band), so inter-core crosstalk actually reaches the QKD core.
     */
    static final double SDM_LOAD_FACTOR = 2.0;

    /** Sizes of a profile: "rapido" (minutes, for checking the chain) or "completo" (article sizes). */
    static final class Profile {
        String name;
        int[] qtnds;
        int reps;
        int dynRequests;
        double[] dynLoadFactors;
        double[] a3Factors;
        int erlangRequests;
        int fRequests;
        int f2Requests;
    }

    static Profile profile(String name) {
        Profile p = new Profile();
        if ("completo".equals(name)) {
            p.name = "completo";
            p.qtnds = new int[] {5, 10, 15, 20, 25, 30, 35, 40, 45, 50};
            p.reps = 10;
            p.dynRequests = 100000;
            p.dynLoadFactors = new double[] {1.0, 1.5, 2.0, 2.5, 3.0};
            p.a3Factors = new double[] {1.0, 1.5, 1.75, 2.0, 2.1, 2.2, 2.3, 2.5, 3.0};
            p.erlangRequests = 200000;
            p.fRequests = 150000;
            p.f2Requests = 150000;
        } else {
            p.name = "rapido";
            p.qtnds = new int[] {10, 20, 25, 30, 40};
            p.reps = 3;
            p.dynRequests = 20000;
            p.dynLoadFactors = new double[] {1.0, 2.0, 3.0};
            p.a3Factors = new double[] {1.0, 2.0, 2.25, 2.5, 3.0};
            p.erlangRequests = 30000;
            p.fRequests = 30000;
            p.f2Requests = 30000;
        }
        return p;
    }

    final File sims;
    final File dibaj;
    final File root;
    final Profile prof;
    final LadderScenario sc;
    final LadderRunner runner;

    /** Part 27: folder of the verification outputs (QKD_ladder, QKD_reference), separate from simulations. */
    final File verif;

    /** Default verification folder: "verificacao" next to the simulations folder. */
    static File defaultVerificationDir(File sims) {
        File parent = sims.getAbsoluteFile().getParentFile();
        return new File(parent == null ? sims : parent, "verificacao");
    }

    /** The article-base reference: verificacao/QKD_reference/Dibaj_MF, or the old place under simulations. */
    static File dibajDir(File sims, File verif) {
        File n = new File(new File(verif, "QKD_reference"), "Dibaj_MF");
        if (new File(n, "config.json").isFile()) return n;
        File o = new File(new File(sims, "QKD_reference"), "Dibaj_MF");
        return new File(o, "config.json").isFile() ? o : n;
    }

    QKDLadderMain(File sims, Profile prof, boolean force) throws Exception {
        this(sims, defaultVerificationDir(sims), prof, force);
    }

    QKDLadderMain(File sims, File verif, Profile prof, boolean force) throws Exception {
        this.sims = sims;
        this.verif = verif.getAbsoluteFile();
        this.prof = prof;
        this.dibaj = dibajDir(sims, this.verif);
        this.root = new File(new File(this.verif, "QKD_ladder"), prof.name);
        if (!new File(dibaj, "config.json").isFile()) throw new IllegalStateException("Pasta do artigo-base n\u00e3o encontrada: " + dibaj);
        this.sc = new LadderScenario(dibaj, root);
        this.runner = new LadderRunner();
        this.runner.force = force;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || "list".equals(args[0])) {
            System.out.println(String.join(",", STEPS));
            return;
        }
        String step = args[0].toUpperCase(Locale.ROOT);
        File sims = new File(args.length > 1 && !args[1].startsWith("--") ? args[1] : "simulations").getAbsoluteFile();
        String profile = "rapido";
        boolean force = false;
        File verif = null;
        for (int i = 1; i < args.length; i++) {
            if ("--profile".equals(args[i]) && i + 1 < args.length) profile = args[++i];
            else if ("--out".equals(args[i]) && i + 1 < args.length) verif = new File(args[++i]).getAbsoluteFile();
            else if ("--threads".equals(args[i]) && i + 1 < args.length) System.setProperty("snets.threads", args[++i]);
            else if ("--force".equals(args[i])) force = true;
        }
        if (!Arrays.asList(STEPS).contains(step)) throw new IllegalArgumentException("Passo desconhecido: " + step);
        QKDLadderMain m = new QKDLadderMain(sims, verif != null ? verif : defaultVerificationDir(sims), profile(profile), force);
        LadderReport rep = m.run(step);
        rep.write(m.prof.name, m.runner.ran, m.runner.reused);
        System.exit(LadderReport.FAIL.equals(rep.status()) ? 1 : 0);
    }

    LadderReport run(String step) throws Exception {
        switch (step) {
            case "A1": return a1();
            case "A2": return a2();
            case "A3": return a3();
            case "B1": return b1();
            case "B2": return b2();
            case "B2L": return b2l();
            case "B3": return b3();
            case "C1": return c(true);
            case "C2": return c(false);
            case "D1": return d1();
            case "D2": return d2();
            case "E1": case "E2": case "E3": case "E4": return e(step);
            case "F1": return f("F1", "small_core", "Cen\u00e1rio completo \u2014 rede de 5 n\u00f3s da qualifica\u00e7\u00e3o", prof.fRequests, new double[] {800, 1200, 1600});
            case "F2": return f("F2", "usa", "Cen\u00e1rio completo \u2014 USA (24 n\u00f3s)", prof.f2Requests, new double[] {600, 900, 1200});
            case "X1": return x1();
            case "X2": return x2();
            case "X3": return x3();
            case "X4": return x4();
            case "X5": return x5();
            case "X6": return x6();
            case "X7": return x7();
            case "X8": return x8();
            case "M1": return new LadderArticles(this).m1();
            case "M2": return new LadderArticles(this).mNet("M2", "moghaddam_metro",
                    "Moghaddam 2021: SKR m\u00e9dia na rede metro (Fig. 6b, n\u00f3s como relays confi\u00e1veis)", 24.8, "Fig. 10a, DC-WC");
            case "M3": return new LadderArticles(this).mNet("M3", "moghaddam_core",
                    "Moghaddam 2021: SKR m\u00e9dia na rede de n\u00facleo pequena (Fig. 6a, n\u00f3s como relays confi\u00e1veis)", 2.2, "Fig. 7a, DC-WC h = 4,6e-11");
            case "Q1": return new LadderArticles(this).q1();
            case "Q2": return new LadderArticles(this).q2();
            case "Q3": return new LadderArticles(this).q3();
            default: throw new IllegalArgumentException(step);
        }
    }

    // =================================================================== infrastructure

    LadderReport report(String step, String title) {
        System.out.println("=== Plano de verifica\u00e7\u00e3o QKD \u2014 " + step + ": " + title + " (perfil " + prof.name + ")");
        return new LadderReport(step, title, new File(root, step), verif);
    }

    static final class Case {
        final File dir;
        final Spec spec;
        Case(File dir, Spec spec) { this.dir = dir; this.spec = spec; }
    }

    Case cs(String step, String name, Spec s) {
        return new Case(new File(new File(root, step), name), s);
    }

    void runAll(LadderReport rep, List<Case> cases) throws Exception {
        int n = cases.size();
        for (int i = 0; i < n; i++) {
            Case c = cases.get(i);
            runner.setProgress((double) i / n, 1.0 / n);
            runner.ensure(c.dir, sc.files(c.spec));
            if (c.dir.getParentFile().getName().equals(rep.step) || c.dir.getParentFile().getParentFile().getName().equals(rep.step)) rep.addCase(c.dir);
        }
        runner.setProgress(1.0, 0);
    }

    Spec art(String topo) throws Exception {
        Spec s = new Spec();
        s.topology = topo;
        s.requests = sc.referenceRequests(topo);
        s.replications = prof.reps;
        return s;
    }

    /** Article spec scaled to a fibre with {@code classicalCores} classical cores (10 fibres in the article). */
    Spec sdm(String topo, int cores, int quantumCore, int classicalCores) throws Exception {
        Spec s = art(topo);
        s.cores = cores;
        s.quantumCore = quantumCore;
        s.requests = (int) Math.round(sc.referenceRequests(topo) * classicalCores / 10.0 * SDM_LOAD_FACTOR);
        s.metrics = true;
        // Span lanes at the slot of their wavelength (mid-band first): the overlap with the classical
        // channels of the neighbouring cores (crosstalk) is consistent with the wavelength used in the SKR.
        s.wavelengthLanes = true;
        return s;
    }

    /** Dynamic version: total offered load = factor x (number of semi-static requests of the step). */
    Spec dyn(Spec base, double[] loads) {
        Spec s = base.copy();
        s.dynamic = true;
        s.requests = prof.dynRequests;
        s.load0 = loads[0];
        s.loadIncrement = loads.length > 1 ? loads[1] - loads[0] : 0;
        s.loadPoints = loads.length;
        s.warmup = 0.1;
        s.metrics = true;
        return s;
    }

    static List<Map<String, String>> stats(File dir) throws Exception {
        return LadderIO.table(LadderRunner.result(dir, "QKDStatistics"));
    }

    static LadderIO.Metrics bp(File dir) throws Exception {
        return LadderIO.metrics(LadderRunner.result(dir, "BlockingProbability"));
    }

    static LadderIO.Metrics qm(File dir) throws Exception {
        return LadderIO.metrics(LadderRunner.result(dir, "QKDMetrics"));
    }

    static LadderIO.Metrics su(File dir) throws Exception {
        return LadderIO.metrics(LadderRunner.result(dir, "SpectrumUtilization"));
    }

    /** Column of QKDStatistics per replication at a load point. */
    static double[] col(List<Map<String, String>> rows, String column, int lp) {
        List<Double> v = new ArrayList<Double>();
        for (Map<String, String> r : rows) if ((int) LadderIO.num(r, "LoadPoint") == lp) v.add(LadderIO.num(r, column));
        double[] a = new double[v.size()];
        for (int i = 0; i < a.length; i++) a[i] = v.get(i);
        return a;
    }

    static double[] classicalBp(File dir, int lp) throws Exception {
        LadderIO.Metrics m = bp(dir);
        if (m == null) return new double[0];
        double[] v = m.get("Blocking probability", lp);
        return v == null ? new double[0] : v;
    }

    static String label(String topo) {
        int i = Arrays.asList(ARTICLE_TOPOLOGIES).indexOf(topo);
        return i >= 0 ? TOPO_LABEL[i] : "usa".equals(topo) ? "USA" : topo;
    }

    static String fmt(double v) {
        return LadderIO.f(v);
    }

    static double max(double[] v) {
        double m = Double.NEGATIVE_INFINITY;
        for (double x : v) if (!Double.isNaN(x)) m = Math.max(m, x);
        return m;
    }

    // ---------------------------------------------------------------- generic conservation checks

    /** Counts conservation violations in the results of the given cases and adds the checks. */
    void conservation(LadderReport rep, List<File> dirs, String prefix) throws Exception {
        int rows = 0, badCount = 0, badCause = 0, causeRows = 0, badClass = 0, classRows = 0, badFrac = 0, fracRows = 0;
        String exCount = "", exCause = "", exClass = "";
        for (File d : dirs) {
            for (Map<String, String> r : stats(d)) {
                rows++;
                double att = LadderIO.num(r, "ProtectedAttempts"), acc = LadderIO.num(r, "Accepted"), rej = LadderIO.num(r, "Rejected");
                if (acc + rej != att) { badCount++; if (exCount.isEmpty()) exCount = d.getName() + " LP" + fmt(LadderIO.num(r, "LoadPoint")) + " rep" + fmt(LadderIO.num(r, "Replication")); }
            }
            LadderIO.Metrics q = qm(d);
            if (q != null && q.has("QKD blocking probability")) {
                for (Object[] row : q.rows("QKD blocking probability")) {
                    String[] k = (String[]) row[0];
                    double[] tot = (double[]) row[1];
                    int lp = Integer.parseInt(k[1]);
                    String[] dims = Arrays.copyOfRange(k, 2, k.length);
                    if (!"all".equals(dims.length > 0 ? dims[0] : "all")) continue;
                    double[] sum = new double[tot.length];
                    for (String cause : new String[] {"classical resources", "QCh spectrum", "SKR", "PICh", "lack of QTx/QRx", "other"}) {
                        double[] c = q.get("QKD blocking probability by " + cause, lp, dims);
                        if (c != null) for (int i = 0; i < Math.min(c.length, sum.length); i++) sum[i] += c[i];
                    }
                    double[] acc = q.get("QKD acceptance ratio", lp, dims);
                    for (int i = 0; i < tot.length; i++) {
                        if (Double.isNaN(tot[i])) continue;
                        causeRows++;
                        boolean ok = Math.abs(sum[i] - tot[i]) <= 1e-9 && (acc == null || i >= acc.length || Math.abs(acc[i] + tot[i] - 1.0) <= 1e-9 || (acc[i] == 0 && tot[i] == 0));
                        if (!ok) { badCause++; if (exCause.isEmpty()) exCause = d.getName() + " LP" + lp + " rep" + i + ": total=" + fmt(tot[i]) + " soma=" + fmt(sum[i]); }
                    }
                }
                List<Map<String, String>> st = stats(d);
                for (Object[] row : q.rows("Fraction of QKD-protected requests")) {
                    String[] k = (String[]) row[0];
                    double[] v = (double[]) row[1];
                    int lp = Integer.parseInt(k[1]);
                    double[] att = col(st, "ProtectedAttempts", lp), tot = col(st, "TotalRequestAttempts", lp);
                    for (int i = 0; i < Math.min(v.length, att.length); i++) {
                        fracRows++;
                        double expect = tot[i] > 0 ? att[i] / tot[i] : 0;
                        if (Math.abs(expect - v[i]) > 1e-9) badFrac++;
                    }
                }
            }
            LadderIO.Metrics b = bp(d);
            if (b != null) {
                for (Object[] row : b.rows("Blocking probability")) {
                    String[] k = (String[]) row[0];
                    if (!"all".equals(k[2])) continue;
                    double[] tot = (double[]) row[1];
                    int lp = Integer.parseInt(k[1]);
                    String[] dims = Arrays.copyOfRange(k, 2, k.length);
                    double[] sum = new double[tot.length];
                    for (String[] kk : b.keys) {
                        if (!kk[0].startsWith("Blocking probability by ") || !kk[1].equals(k[1])) continue;
                        if (!Arrays.equals(Arrays.copyOfRange(kk, 2, kk.length), dims)) continue;
                        double[] c = b.get(kk[0], lp, dims);
                        if (c != null) for (int i = 0; i < Math.min(c.length, sum.length); i++) sum[i] += c[i];
                    }
                    for (int i = 0; i < tot.length; i++) {
                        classRows++;
                        if (Math.abs(sum[i] - tot[i]) > 1e-9 * Math.max(1, tot[i])) { badClass++; if (exClass.isEmpty()) exClass = d.getName() + " LP" + lp + " rep" + i + ": total=" + fmt(tot[i]) + " soma=" + fmt(sum[i]); }
                    }
                }
            }
        }
        if (rows > 0) rep.check(prefix + ".cons.qkd", badCount == 0, LadderReport.FAIL, "Conserva\u00e7\u00e3o QKD: aceitas + bloqueadas = requisi\u00e7\u00f5es protegidas (QKDStatistics)",
                badCount + " viola\u00e7\u00e3o(\u00f5es) em " + rows + " linhas", "0", exCount);
        if (causeRows > 0) rep.check(prefix + ".cons.causes", badCause == 0, LadderReport.FAIL, "Causas do bloqueio QKD somam o bloqueio total e aceita\u00e7\u00e3o + bloqueio = 1 (QKDMetrics)",
                badCause + " viola\u00e7\u00e3o(\u00f5es) em " + causeRows + " valores", "0", exCause);
        if (fracRows > 0) rep.check(prefix + ".cons.frac", badFrac == 0, LadderReport.FAIL, "Fra\u00e7\u00e3o de protegidas do QKDMetrics = ProtectedAttempts \u00f7 TotalRequestAttempts do QKDStatistics",
                badFrac + " diverg\u00eancia(s) em " + fracRows, "0", null);
        if (classRows > 0) rep.check(prefix + ".cons.classical", badClass == 0, LadderReport.WARN, "Causas do bloqueio cl\u00e1ssico somam o bloqueio total (BlockingProbability)",
                badClass + " diverg\u00eancia(s) em " + classRows + " valores", "0", exClass.isEmpty() ? null : exClass + " \u2014 o SNetS pode contar uma requisi\u00e7\u00e3o em mais de uma causa");
    }

    // =================================================================== Phase A

    List<Case> a1Cases() throws Exception {
        List<Case> out = new ArrayList<Case>();
        for (String t : ARTICLE_TOPOLOGIES) for (int q : prof.qtnds) {
            Spec s = art(t);
            s.qtnd = q;
            out.add(new Case(new File(new File(new File(root, "A1"), t), QKDNativeReferenceMain.caseName(t, q)), s));
        }
        return out;
    }

    LadderReport a1() throws Exception {
        LadderReport rep = report("A1", "Artigo-base: reprodu\u00e7\u00e3o nativa (Q-TRx \u00d7 QTND, 3 topologias)");
        List<Case> cases = a1Cases();
        runAll(rep, cases);
        File stepDir = new File(root, "A1");
        boolean ok = QKDNativeReferenceMain.analyze(dibaj, stepDir, Arrays.asList(ARTICLE_TOPOLOGIES), prof.qtnds);
        List<Map<String, String>> sum = LadderIO.table(new File(new File(stepDir, "results"), "snets_native_summary.csv"));
        List<Map<String, String>> st = LadderIO.table(new File(new File(stepDir, "results"), "snets_native_statistics.csv"));
        boolean small = prof.reps < 10;
        for (Map<String, String> r : sum) {
            String t = r.get("topology"), L = label(t);
            int snets = (int) LadderIO.num(r, "optimum_qtnd_snets");
            double py = LadderIO.num(r, "optimum_qtnd_python");
            rep.check("a1.u." + t, "true".equals(r.get("u_shape")), LadderReport.FAIL, L + ": Q-TRx \u00d7 QTND tem forma de U (m\u00ednimo interno)", r.get("u_shape"), "true", null);
            rep.check("a1.opt." + t, Math.abs(snets - py) <= 5, LadderReport.FAIL, L + ": QTND \u00f3timo do SNetS a no m\u00e1ximo 5 km do da reconstru\u00e7\u00e3o em Python",
                    snets + " km", fmt(py) + " km \u00b1 5", "artigo: " + r.get("optimum_qtnd_article") + " km");
            String[] w = r.get("replications_optimum_within_5km_of_25").split("/");
            rep.check("a1.rep." + t, w[0].equals(w[1]), LadderReport.FAIL, L + ": em todas as replica\u00e7\u00f5es o \u00f3timo fica a no m\u00e1ximo 5 km de 25 km (crit\u00e9rio da qualifica\u00e7\u00e3o)",
                    r.get("replications_optimum_within_5km_of_25"), "todas", null);
            double mae = LadderIO.num(r, "mae_vs_python");
            rep.check("a1.mae." + t, mae <= 10, small ? LadderReport.WARN : LadderReport.FAIL, L + ": diferen\u00e7a m\u00e9dia para a reconstru\u00e7\u00e3o em Python \u2264 10 Q-TRx",
                    fmt(mae) + " Q-TRx", "\u2264 10", small ? "perfil r\u00e1pido: a m\u00e9dia do SNetS usa s\u00f3 " + prof.reps + " replica\u00e7\u00f5es" : null);
            rep.check("a1.mae_art." + t, LadderReport.INFO, L + ": diferen\u00e7a m\u00e9dia para a Fig. 7 digitalizada do artigo", fmt(LadderIO.num(r, "mae_vs_article")) + " Q-TRx", null,
                    "o artigo n\u00e3o publica todos os desempates de roteamento; a refer\u00eancia quantitativa \u00e9 a reconstru\u00e7\u00e3o");
            rep.check("a1.block." + t, LadderIO.num(r, "qkd_blocking_max") == 0 && LadderIO.num(r, "classical_blocking_max") == 0, LadderReport.FAIL,
                    L + ": sem bloqueio cl\u00e1ssico nem QKD na carga do artigo (como na reconstru\u00e7\u00e3o)",
                    "QKD " + LadderIO.pct(LadderIO.num(r, "qkd_blocking_max")) + ", cl\u00e1ssico " + LadderIO.pct(LadderIO.num(r, "classical_blocking_max")), "0 %", null);
        }
        for (int i = 0; i < ARTICLE_TOPOLOGIES.length; i++) {
            String t = ARTICLE_TOPOLOGIES[i];
            rep.chart("qtrx_" + t, TOPO_LABEL[i] + " \u2014 pares Q-TRx \u00d7 QTND", "QTND (km)", "Pares Q-TRx", false);
            for (Map<String, String> r : st) {
                if (!t.equals(r.get("topology"))) continue;
                double q = LadderIO.num(r, "qtnd_km");
                rep.point("qtrx_" + t, "SNetS (m\u00e9dia \u00b1 IC95%)", q, LadderIO.num(r, "mean_qtrx"), LadderIO.num(r, "ci95_margin"), LadderIO.num(r, "n"));
                if (!Double.isNaN(LadderIO.num(r, "python_mean_qtrx"))) rep.point("qtrx_" + t, "Reconstru\u00e7\u00e3o Python", q, LadderIO.num(r, "python_mean_qtrx"), Double.NaN, Double.NaN);
                if (!Double.isNaN(LadderIO.num(r, "article_qtrx"))) rep.point("qtrx_" + t, "Artigo (Fig. 7)", q, LadderIO.num(r, "article_qtrx"), Double.NaN, Double.NaN);
            }
        }
        conservation(rep, dirs(cases), "a1");
        rep.note("Relat\u00f3rio completo da reprodu\u00e7\u00e3o: " + new File(new File(stepDir, "results"), "snets_native_report.txt").getAbsolutePath()
                + (ok ? "" : " (crit\u00e9rio da qualifica\u00e7\u00e3o n\u00e3o atendido em alguma topologia)"));
        return rep;
    }

    static List<File> dirs(List<Case> cases) {
        List<File> out = new ArrayList<File>();
        for (Case c : cases) out.add(c.dir);
        return out;
    }

    LadderReport a2() throws Exception {
        LadderReport rep = report("A2", "Artigo-base + m\u00e9tricas de bloqueio e utiliza\u00e7\u00e3o ativadas");
        List<Case> base = a1Cases();
        List<Case> withMetrics = new ArrayList<Case>();
        for (Case c : base) {
            Spec s = c.spec.copy();
            s.metrics = true;
            withMetrics.add(new Case(new File(new File(new File(root, "A2"), s.topology), c.dir.getName()), s));
        }
        List<Case> all = new ArrayList<Case>(base);
        all.addAll(withMetrics);
        runAll(rep, all);
        int same = 0, diff = 0, bpDiff = 0;
        String ex = "";
        for (int i = 0; i < base.size(); i++) {
            File a = base.get(i).dir, b = withMetrics.get(i).dir;
            List<Map<String, String>> sa = stats(a), sb = stats(b);
            boolean eq = sa.equals(sb) && !sa.isEmpty();
            if (eq) same++; else { diff++; if (ex.isEmpty()) ex = b.getName(); }
            double[] ba = classicalBp(a, 0), bb = classicalBp(b, 0);
            if (!Arrays.equals(ba, bb)) bpDiff++;
        }
        rep.check("a2.same", diff == 0, LadderReport.FAIL, "Ativar m\u00e9tricas n\u00e3o altera a simula\u00e7\u00e3o: QKDStatistics id\u00eantico ao do A1 em todos os casos",
                same + " id\u00eanticos, " + diff + " diferentes", "todos id\u00eanticos", ex.isEmpty() ? null : "primeiro caso diferente: " + ex);
        rep.check("a2.samebp", bpDiff == 0, LadderReport.FAIL, "Bloqueio cl\u00e1ssico id\u00eantico ao do A1 (mesmas sementes e decis\u00f5es)", bpDiff + " caso(s) diferente(s)", "0", null);
        double maxQ = 0, maxC = 0;
        for (int ti = 0; ti < ARTICLE_TOPOLOGIES.length; ti++) {
            String t = ARTICLE_TOPOLOGIES[ti];
            rep.chart("bp_" + t, TOPO_LABEL[ti] + " \u2014 bloqueio \u00d7 QTND", "QTND (km)", "Probabilidade de bloqueio", false);
            rep.chart("util_" + t, TOPO_LABEL[ti] + " \u2014 utiliza\u00e7\u00e3o do espectro \u00d7 QTND", "QTND (km)", "Utiliza\u00e7\u00e3o", false);
            for (Case c : withMetrics) {
                if (!c.spec.topology.equals(t)) continue;
                double[] qb = col(stats(c.dir), "QKDBlockingProbability", 0);
                double[] cb = classicalBp(c.dir, 0);
                maxQ = Math.max(maxQ, max(qb));
                maxC = Math.max(maxC, max(cb));
                rep.point("bp_" + t, "Cl\u00e1ssico", c.spec.qtnd, LadderIO.meanCi(cb));
                rep.point("bp_" + t, "QKD", c.spec.qtnd, LadderIO.meanCi(qb));
                LadderIO.Metrics u = su(c.dir);
                if (u != null) {
                    double[] v = u.get("Utilization", 0, "all", "all");
                    if (v != null) rep.point("util_" + t, "Utiliza\u00e7\u00e3o m\u00e9dia", c.spec.qtnd, LadderIO.meanCi(v));
                }
            }
        }
        rep.check("a2.zero", maxQ == 0 && maxC == 0, LadderReport.WARN, "Bloqueio nulo na carga do artigo (a reconstru\u00e7\u00e3o n\u00e3o teve bloqueio)",
                "QKD m\u00e1x. " + LadderIO.pct(maxQ) + ", cl\u00e1ssico m\u00e1x. " + LadderIO.pct(maxC), "0 %",
                "a Fig. 7 do artigo foi obtida com 1 % de bloqueio; veja o passo A3");
        conservation(rep, dirs(withMetrics), "a2");
        rep.note("Os casos do A2 t\u00eam SpectrumUtilization e QKDMetrics: abra-os no launcher para ver o mapa de calor dos enlaces.");
        return rep;
    }

    LadderReport a3() throws Exception {
        LadderReport rep = report("A3", "Ponto de opera\u00e7\u00e3o do artigo: carga at\u00e9 1 % de bloqueio (QTND 25 km)");
        double[] articleTbps = {625, 612, 654};
        List<Case> cases = new ArrayList<Case>();
        for (String t : ARTICLE_TOPOLOGIES) for (double m : prof.a3Factors) {
            Spec s = art(t);
            s.requests = (int) Math.round(sc.referenceRequests(t) * m);
            s.metrics = true;
            cases.add(cs("A3", t + String.format(Locale.US, "_carga_x%.2f", m), s));
        }
        runAll(rep, cases);
        rep.chart("bp", "Bloqueio \u00d7 tr\u00e1fego cl\u00e1ssico oferecido (QTND 25 km)", "Tr\u00e1fego cl\u00e1ssico oferecido (Tbit/s)", "Probabilidade de bloqueio", false);
        rep.chart("qtrx", "Pares Q-TRx \u00d7 tr\u00e1fego oferecido (QTND 25 km)", "Tr\u00e1fego cl\u00e1ssico oferecido (Tbit/s)", "Pares Q-TRx", false);
        for (int ti = 0; ti < ARTICLE_TOPOLOGIES.length; ti++) {
            String t = ARTICLE_TOPOLOGIES[ti], L = TOPO_LABEL[ti];
            List<double[]> pts = new ArrayList<double[]>();
            boolean mono = true;
            double prev = -1, prevCi = 0;
            for (Case c : cases) {
                if (!c.spec.topology.equals(t)) continue;
                double tbps = c.spec.requests * MEAN_BIT_RATE_GBPS / 1000.0;
                double[] cb = LadderIO.meanCi(classicalBp(c.dir, 0));
                double[] qb = LadderIO.meanCi(col(stats(c.dir), "QKDBlockingProbability", 0));
                double total = Math.max(cb[0], qb[0]);
                rep.point("bp", L + " cl\u00e1ssico", tbps, cb);
                rep.point("bp", L + " QKD", tbps, qb);
                rep.point("qtrx", L, tbps, LadderIO.meanCi(col(stats(c.dir), "PeakQTRxPairs", 0)));
                if (prev >= 0 && total < prev - Math.max(2 * (prevCi + cb[2]), 0.002)) mono = false;
                prev = total;
                prevCi = cb[2];
                pts.add(new double[] {tbps, total});
            }
            rep.point("bp", "Artigo: 1 % de bloqueio", articleTbps[ti], 0.01, Double.NaN, Double.NaN);
            rep.check("a3.mono." + t, mono, LadderReport.FAIL, L + ": bloqueio n\u00e3o diminui quando a carga aumenta", mono ? "crescente" : "diminuiu", "n\u00e3o decrescente", null);
            double cross = Double.NaN;
            for (int i = 1; i < pts.size(); i++) {
                double[] a = pts.get(i - 1), b = pts.get(i);
                if (a[1] < 0.01 && b[1] >= 0.01) { cross = a[0] + (0.01 - a[1]) * (b[0] - a[0]) / (b[1] - a[1]); break; }
            }
            if (!pts.isEmpty() && pts.get(0)[1] >= 0.01) cross = pts.get(0)[0];
            rep.check("a3.cross." + t, LadderReport.INFO, L + ": tr\u00e1fego cl\u00e1ssico em que o bloqueio atinge 1 %",
                    Double.isNaN(cross) ? "acima de " + fmt(pts.get(pts.size() - 1)[0]) + " Tbit/s" : fmt(cross) + " Tbit/s",
                    articleTbps[ti] + " Tbit/s (artigo)",
                    Double.isNaN(cross) ? "o bloqueio n\u00e3o chegou a 1 % na faixa simulada" : String.format(Locale.US, "raz\u00e3o SNetS/artigo = %.2f", cross / articleTbps[ti]));
        }
        rep.note("A carga do artigo (fator 1) \u00e9 a mesma do A1: offered SKR \u00f7 demanda m\u00e9dia de 7/3 Mbit/s requisi\u00e7\u00f5es, m\u00e9dia de 350 Gbit/s por requisi\u00e7\u00e3o.");
        rep.note("Se o bloqueio de 1 % s\u00f3 aparece com cargas bem maiores que as do artigo, a capacidade cl\u00e1ssica do modelo (10 fibras \u00d7 159 canais de 75 GHz, K = 2) \u00e9 maior que a usada no artigo \u2014 uma diferen\u00e7a de suposi\u00e7\u00e3o, n\u00e3o de implementa\u00e7\u00e3o.");
        conservation(rep, dirs(cases), "a3");
        return rep;
    }

    // =================================================================== Phase B

    List<Case> bGrid(String step, boolean xt, boolean qot, double coupling) throws Exception {
        List<Case> out = new ArrayList<Case>();
        for (String t : ARTICLE_TOPOLOGIES) for (int q : prof.qtnds) {
            Spec s = sdm(t, 2, 1, 1);
            s.qtnd = q;
            s.xt = xt;
            s.qot = qot;
            s.coupling = coupling;
            out.add(cs(step, String.format(Locale.US, "%s_QTND_%02d", t, q), s));
        }
        return out;
    }

    Case bTwin(String t) throws Exception {
        Spec s = sdm(t, 2, 1, 1);
        s.protectedProbability = 0.0;
        return cs("B1", t + "_classico", s);
    }

    void qtrxCharts(LadderReport rep, String prefix, List<Case> cases, String series) throws Exception {
        for (int ti = 0; ti < ARTICLE_TOPOLOGIES.length; ti++) {
            String t = ARTICLE_TOPOLOGIES[ti];
            rep.chart(prefix + "qtrx_" + t, TOPO_LABEL[ti] + " \u2014 pares Q-TRx \u00d7 QTND", "QTND (km)", "Pares Q-TRx", false);
            rep.chart(prefix + "skr_" + t, TOPO_LABEL[ti] + " \u2014 SKR m\u00e9dia na admiss\u00e3o \u00d7 QTND", "QTND (km)", "SKR (Mbit/s)", false);
            rep.chart(prefix + "bp_" + t, TOPO_LABEL[ti] + " \u2014 bloqueio \u00d7 QTND", "QTND (km)", "Probabilidade de bloqueio", false);
            for (Case c : cases) {
                if (!c.spec.topology.equals(t)) continue;
                List<Map<String, String>> st = stats(c.dir);
                rep.point(prefix + "qtrx_" + t, series, c.spec.qtnd, LadderIO.meanCi(col(st, "PeakQTRxPairs", 0)));
                rep.point(prefix + "skr_" + t, series, c.spec.qtnd, LadderIO.meanCi(col(st, "MeanAdmissionRawSKR_Mbps", 0)));
                rep.point(prefix + "bp_" + t, series + " \u2014 QKD", c.spec.qtnd, LadderIO.meanCi(col(st, "QKDBlockingProbability", 0)));
                rep.point(prefix + "bp_" + t, series + " \u2014 cl\u00e1ssico", c.spec.qtnd, LadderIO.meanCi(classicalBp(c.dir, 0)));
            }
        }
    }

    void uShape(LadderReport rep, String prefix, List<Case> cases) throws Exception {
        for (int ti = 0; ti < ARTICLE_TOPOLOGIES.length; ti++) {
            String t = ARTICLE_TOPOLOGIES[ti];
            TreeMap<Integer, Double> m = new TreeMap<Integer, Double>();
            for (Case c : cases) if (c.spec.topology.equals(t)) m.put(c.spec.qtnd, LadderIO.mean(col(stats(c.dir), "PeakQTRxPairs", 0)));
            if (m.isEmpty()) continue;
            int best = m.firstKey();
            for (Map.Entry<Integer, Double> e : m.entrySet()) if (e.getValue() < m.get(best)) best = e.getKey();
            boolean u = best != m.firstKey() && best != m.lastKey();
            rep.check(prefix + ".opt." + t, LadderReport.INFO, TOPO_LABEL[ti] + ": QTND com menos pares Q-TRx", best + " km" + (u ? " (curva em U)" : " (extremo da faixa)"),
                    null, "artigo (MF, sem ru\u00eddo): 25 km; com ru\u00eddo cl\u00e1ssico (MB): 20 km");
        }
    }

    LadderReport b1() throws Exception {
        LadderReport rep = report("B1", "MCF de 2 n\u00facleos (1 cl\u00e1ssico + 1 qu\u00e2ntico), sem XT/OSNR");
        List<Case> grid = bGrid("B1", false, false, 0.0);
        List<Case> all = new ArrayList<Case>(grid);
        for (String t : ARTICLE_TOPOLOGIES) all.add(bTwin(t));
        runAll(rep, all);
        int compared = 0, mismatches = 0, skipped = 0, pxtBad = 0;
        String ex = "";
        for (Case c : grid) {
            File twin = bTwin(c.spec.topology).dir;
            List<Map<String, String>> st = stats(c.dir);
            double rejected = 0;
            for (double v : col(st, "Rejected", 0)) rejected += v;
            for (double v : col(st, "MaximumAdmissionPXT_W", 0)) if (v != 0) pxtBad++;
            if (rejected > 0) { skipped++; continue; }
            compared++;
            boolean eq = Arrays.equals(classicalBp(c.dir, 0), classicalBp(twin, 0));
            LadderIO.Metrics ua = su(c.dir), ub = su(twin);
            if (eq && ua != null && ub != null) {
                for (Object[] row : ub.rows("Utilization per Link and Core")) {
                    String[] k = (String[]) row[0];
                    if (!"0".equals(k[3])) continue;
                    if (!Arrays.equals((double[]) row[1], ua.get(k[0], Integer.parseInt(k[1]), k[2], k[3]))) { eq = false; break; }
                }
            }
            if (!eq) { mismatches++; if (ex.isEmpty()) ex = c.dir.getName(); }
        }
        rep.check("b1.twin", mismatches == 0, LadderReport.FAIL,
                "Sem XT, o QKD n\u00e3o interfere no cl\u00e1ssico: bloqueio e utiliza\u00e7\u00e3o do n\u00facleo cl\u00e1ssico (por enlace) id\u00eanticos aos do g\u00eameo sem requisi\u00e7\u00f5es protegidas",
                compared + " casos comparados, " + mismatches + " diferente(s)", "0 diferen\u00e7as",
                (skipped > 0 ? skipped + " caso(s) com bloqueio QKD n\u00e3o comparados (uma recusa QKD libera o circuito cl\u00e1ssico). " : "") + (ex.isEmpty() ? "" : "primeiro: " + ex));
        rep.check("b1.pxt", pxtBad == 0, LadderReport.FAIL, "Sem XT, nenhuma pot\u00eancia de crosstalk chega ao n\u00facleo qu\u00e2ntico (MaximumAdmissionPXT_W = 0)",
                pxtBad + " valor(es) \u2260 0", "0", null);
        uShape(rep, "b1", grid);
        qtrxCharts(rep, "", grid, "B1 (2 n\u00facleos, sem XT)");
        rep.note("Carga: requisi\u00e7\u00f5es do artigo \u00d7 1/10 (um n\u00facleo cl\u00e1ssico contra 10 fibras cl\u00e1ssicas no artigo) \u00d7 2 (ponto de opera\u00e7\u00e3o de \u2248 1 % de bloqueio encontrado no A3). Demais par\u00e2metros iguais ao A1.");
        conservation(rep, dirs(all), "b1");
        return rep;
    }

    /** Sum of a QKDStatistics column over the replications of load point 0. */
    static double sum(File dir, String column) throws Exception {
        double t = 0;
        for (double v : col(stats(dir), column, 0)) if (!Double.isNaN(v)) t += v;
        return t;
    }

    LadderReport b2() throws Exception {
        LadderReport rep = report("B2", "MCF de 2 n\u00facleos com crosstalk entre n\u00facleos (XT)");
        List<Case> b1 = bGrid("B1", false, false, 0.0);
        List<Case> b2 = bGrid("B2", true, false, 0.012);
        List<Case> all = new ArrayList<Case>(b1);
        all.addAll(b2);
        runAll(rep, all);
        int skrBad = 0, skrN = 0, bpLower = 0;
        double pxtMax = 0, protect = 0, extra = 0;
        String ex = "";
        rep.chart("protect", "Candidatos cl\u00e1ssicos recusados para proteger sess\u00f5es QKD (por requisi\u00e7\u00e3o)", "QTND (km)", "Recusas por requisi\u00e7\u00e3o", false);
        for (int i = 0; i < b2.size(); i++) {
            Case c = b2.get(i);
            List<Map<String, String>> s1 = stats(b1.get(i).dir), s2 = stats(c.dir);
            pxtMax = Math.max(pxtMax, max(col(s2, "MaximumAdmissionPXT_W", 0)));
            protect += sum(c.dir, "ClassicalRejectionsByQKDImpact");
            double k1 = LadderIO.mean(col(s1, "MeanAdmissionRawSKR_Mbps", 0)), k2 = LadderIO.mean(col(s2, "MeanAdmissionRawSKR_Mbps", 0));
            if (!Double.isNaN(k1) && !Double.isNaN(k2)) {
                skrN++;
                if (k2 > k1 * (1 + 1e-6)) { skrBad++; if (ex.isEmpty()) ex = c.dir.getName() + ": " + fmt(k2) + " > " + fmt(k1); }
            }
            double[] q1 = LadderIO.meanCi(col(s1, "QKDBlockingProbability", 0)), q2 = LadderIO.meanCi(col(s2, "QKDBlockingProbability", 0));
            if (q2[0] < q1[0] - Math.max(q1[2] + q2[2], 0.002)) bpLower++;
            extra = Math.max(extra, q2[0] - q1[0]);
            double[] rej = col(s2, "ClassicalRejectionsByQKDImpact", 0), att = col(s2, "TotalRequestAttempts", 0);
            double[] per = new double[rej.length];
            for (int k = 0; k < per.length; k++) per[k] = att[k] > 0 ? rej[k] / att[k] : 0;
            rep.point("protect", label(c.spec.topology), c.spec.qtnd, LadderIO.meanCi(per));
        }
        rep.check("b2.effect", protect > 0 || extra > 0, LadderReport.FAIL, "Com XT ativo, o crosstalk afeta a admiss\u00e3o (recusas para proteger QChs ativos ou mais bloqueio QKD que no B1)",
                String.format(Locale.US, "%.0f recusas; bloqueio QKD at\u00e9 %s acima do B1", protect, LadderIO.pct(extra)), "> 0", null);
        rep.check("b2.bp", bpLower == 0, LadderReport.FAIL, "Bloqueio QKD com XT \u2265 sem XT (B1), para cada topologia e QTND", bpLower + " caso(s) menor(es)", "0", null);
        rep.check("b2.skr", LadderReport.INFO, "SKR m\u00e9dia das sess\u00f5es admitidas: casos em que fica acima do B1", skrBad + " de " + skrN, null, "o XT bloqueia sobretudo as sess\u00f5es com trechos longos (SKR menor), ent\u00e3o a m\u00e9dia das admitidas pode subir; " + (ex.isEmpty() ? "" : ex));
        rep.check("b2.pxt", LadderReport.INFO, "Maior pot\u00eancia de crosstalk numa sess\u00e3o admitida", String.format(Locale.US, "%.3e W", pxtMax), null,
                "um canal cl\u00e1ssico de 0 dBm no n\u00facleo vizinho, na mesma frequ\u00eancia, leva p_b \u2192 1 e SKR \u2192 0: s\u00f3 s\u00e3o admitidos QChs sem sobreposi\u00e7\u00e3o espectral (PXT = 0), e os candidatos cl\u00e1ssicos que cairiam sobre um QCh ativo s\u00e3o recusados");
        uShape(rep, "b2", b2);
        qtrxCharts(rep, "", b1, "B1 sem XT");
        qtrxCharts(rep, "", b2, "B2 com XT");
        rep.note("QChs posicionados no slot do seu comprimento de onda (spanLanePlacement = wavelength, meio da banda primeiro); o espectro cl\u00e1ssico \u00e9 ocupado a partir do slot 1 (first fit).");
        rep.note("O bloqueio adicional em rela\u00e7\u00e3o ao B1 mostra o custo da coexist\u00eancia sem coordena\u00e7\u00e3o espectral entre n\u00facleos. O modo route (fase E) escolhe slots de QCh com SKR suficiente.");
        conservation(rep, dirs(b2), "b2");
        return rep;
    }

    LadderReport b2l() throws Exception {
        LadderReport rep = report("B2L", "Limite: XT \u2192 0 deve reproduzir o B1 exatamente");
        List<Case> b1 = bGrid("B1", false, false, 0.0);
        List<Case> lim = bGrid("B2L", true, false, 1e-12);
        List<Case> all = new ArrayList<Case>(b1);
        all.addAll(lim);
        runAll(rep, all);
        int diff = 0;
        String ex = "";
        for (int i = 0; i < lim.size(); i++) {
            List<Map<String, String>> s1 = stats(b1.get(i).dir), s2 = stats(lim.get(i).dir);
            boolean eq = s1.size() == s2.size();
            for (String c : new String[] {"PeakQTRxPairs", "Accepted", "Rejected", "ActivatedQChs", "TDMReuses"}) eq &= Arrays.equals(col(s1, c, 0), col(s2, c, 0));
            eq &= Arrays.equals(classicalBp(b1.get(i).dir, 0), classicalBp(lim.get(i).dir, 0));
            if (!eq) { diff++; if (ex.isEmpty()) ex = lim.get(i).dir.getName(); }
        }
        rep.check("b2l.same", diff == 0, LadderReport.FAIL, "Com coeficiente de acoplamento 1e-12 (XT ligado, mas desprez\u00edvel) os Q-TRx, aceita\u00e7\u00f5es, QChs e bloqueio cl\u00e1ssico s\u00e3o id\u00eanticos aos do B1",
                diff + " caso(s) diferente(s) de " + lim.size(), "0", ex.isEmpty() ? null : "primeiro: " + ex);
        qtrxCharts(rep, "", b1, "B1 sem XT");
        qtrxCharts(rep, "", lim, "B2L XT \u2192 0");
        return rep;
    }

    LadderReport b3() throws Exception {
        LadderReport rep = report("B3", "MCF de 2 n\u00facleos com XT e OSNR/QoT (ASE + NLI)");
        List<Case> b2 = bGrid("B2", true, false, 0.012);
        List<Case> b3 = bGrid("B3", true, true, 0.012);
        List<Case> all = new ArrayList<Case>(b2);
        all.addAll(b3);
        runAll(rep, all);
        double qotMax = 0;
        rep.chart("cls", "Bloqueio cl\u00e1ssico: B2 (XT) \u00d7 B3 (XT + QoT)", "QTND (km)", "Probabilidade de bloqueio", false);
        for (int i = 0; i < b3.size(); i++) {
            LadderIO.Metrics m = bp(b3.get(i).dir);
            if (m != null) qotMax = Math.max(qotMax, Math.max(m.mean("Blocking probability by QoTN", 0), m.mean("Blocking probability by QoTO", 0)));
            rep.point("cls", "B2 " + label(b3.get(i).spec.topology), b3.get(i).spec.qtnd, LadderIO.meanCi(classicalBp(b2.get(i).dir, 0)));
            rep.point("cls", "B3 " + label(b3.get(i).spec.topology), b3.get(i).spec.qtnd, LadderIO.meanCi(classicalBp(b3.get(i).dir, 0)));
        }
        rep.check("b3.qot", qotMax > 0, LadderReport.FAIL, "Com QoT ativo, h\u00e1 bloqueio por OSNR (QoTN/QoTO) nos circuitos longos", LadderIO.pct(qotMax), "> 0",
                "limiares de SNR da qualifica\u00e7\u00e3o (BPSK derivado = QPSK \u2212 3,01 dB); a sele\u00e7\u00e3o de modula\u00e7\u00e3o do qrmsta \u00e9 s\u00f3 por dist\u00e2ncia");
        rep.note("Com QoT, menos circuitos cl\u00e1ssicos s\u00e3o aceitos e h\u00e1 menos recusas para proteger QChs; por isso o bloqueio total do B3 n\u00e3o precisa ser maior que o do B2 em todos os casos.");
        qtrxCharts(rep, "", b2, "B2 XT");
        qtrxCharts(rep, "", b3, "B3 XT + QoT");
        conservation(rep, dirs(b3), "b3");
        return rep;
    }

    // =================================================================== Phase C

    List<Case> cGrid(String step, int quantumCore) throws Exception {
        List<Case> out = new ArrayList<Case>();
        for (String t : ARTICLE_TOPOLOGIES) for (int q : prof.qtnds) {
            Spec s = sdm(t, 7, quantumCore, 6);
            s.qtnd = q;
            s.xt = true;
            out.add(cs(step, String.format(Locale.US, "%s_QTND_%02d", t, q), s));
        }
        return out;
    }

    LadderReport c(boolean edge) throws Exception {
        String step = edge ? "C1" : "C2";
        LadderReport rep = report(step, edge ? "MCF de 7 n\u00facleos, QKD no n\u00facleo da borda (1), com XT" : "MCF de 7 n\u00facleos, QKD no n\u00facleo central (0), com XT");
        List<Case> c1 = cGrid("C1", 1);
        List<Case> mine = edge ? c1 : cGrid("C2", 0);
        List<Case> all = new ArrayList<Case>(mine);
        if (!edge) all.addAll(0, c1);
        runAll(rep, all);
        int nb = LadderScenario.neighbours(7, edge ? 1 : 0);
        rep.check(step + ".adj", nb == (edge ? 3 : 6), LadderReport.FAIL, "Vizinhos do n\u00facleo qu\u00e2ntico no arquivo network", String.valueOf(nb), edge ? "3" : "6", null);
        double protect = 0;
        for (Case c : mine) protect += sum(c.dir, "ClassicalRejectionsByQKDImpact");
        rep.check(step + ".xt", protect > 0, LadderReport.FAIL, "O crosstalk dos n\u00facleos cl\u00e1ssicos vizinhos age sobre o n\u00facleo qu\u00e2ntico (recusas para proteger QChs ativos)",
                String.format(Locale.US, "%.0f recusas", protect), "> 0", null);
        if (!edge) {
            int worse = 0, n = 0;
            for (int i = 0; i < mine.size(); i++) {
                double[] be = LadderIO.meanCi(col(stats(c1.get(i).dir), "QKDBlockingProbability", 0));
                double[] bc = LadderIO.meanCi(col(stats(mine.get(i).dir), "QKDBlockingProbability", 0));
                n++;
                if (bc[0] < be[0] - Math.max(be[2] + bc[2], 0.002)) worse++;
            }
            rep.check("C2.bp", worse == 0, LadderReport.WARN, "Bloqueio QKD com o QKD no n\u00facleo central (6 vizinhos cl\u00e1ssicos) \u2265 na borda (3 vizinhos)",
                    worse + " de " + n + " casos menores", "0", null);
            qtrxCharts(rep, "", c1, "C1 borda (n\u00facleo 1)");
        }
        uShape(rep, step, mine);
        qtrxCharts(rep, "", mine, edge ? "C1 borda (n\u00facleo 1)" : "C2 centro (n\u00facleo 0)");
        rep.note("Carga: requisi\u00e7\u00f5es do artigo \u00d7 6/10 (6 n\u00facleos cl\u00e1ssicos contra 10 fibras cl\u00e1ssicas no artigo) \u00d7 2 (ponto de opera\u00e7\u00e3o do A3).");
        conservation(rep, dirs(mine), step);
        return rep;
    }

    // =================================================================== Phase D (dynamic)

    /**
     * Slots per link of the dynamic steps: 240 slots = 40 channels of 75 GHz per core.
     * Dynamic traffic needs many more arrivals than simultaneous connections (several
     * holding times per replication); with the full 960 slots the steady state would
     * need thousands of active circuits and QChs and hours of simulation.
     */
    static final int DYN_SLOTS = 240;

    double[] dynLoads(String t) throws Exception {
        double base = sc.referenceRequests(t) * 6 / 10.0 * DYN_SLOTS / 960.0;
        double[] l = new double[prof.dynLoadFactors.length];
        for (int i = 0; i < l.length; i++) l[i] = Math.round(base * prof.dynLoadFactors[i]);
        return l;
    }

    Spec dSpec(String t) throws Exception {
        Spec s = sdm(t, 7, 1, 6);
        s.qtnd = 25;
        s.xt = true;
        s.linkSlots = DYN_SLOTS;
        return dyn(s, dynLoads(t));
    }

    List<Case> d1Cases() throws Exception {
        List<Case> out = new ArrayList<Case>();
        for (String t : ARTICLE_TOPOLOGIES) out.add(cs("D1", t + "_dinamico", dSpec(t)));
        return out;
    }

    /** Monotonicity, Little's law and charts of a dynamic multi-load case. */
    void dynamicChecks(LadderReport rep, String prefix, Case c, String L, double protectedShare, boolean little) throws Exception {
        List<Map<String, String>> st = stats(c.dir);
        int lps = c.spec.loadPoints;
        double[] loads = new double[lps];
        for (int i = 0; i < lps; i++) loads[i] = c.spec.load0 + i * c.spec.loadIncrement;
        String ch = prefix + "bp_" + c.spec.topology, chs = prefix + "little_" + c.spec.topology;
        rep.chart(ch, L + " \u2014 bloqueio \u00d7 carga", "Carga (Erlang)", "Probabilidade de bloqueio", false);
        if (little) rep.chart(chs, L + " \u2014 sess\u00f5es QKD ativas: simulado \u00d7 lei de Little", "Carga (Erlang)", "Sess\u00f5es QKD ativas (m\u00e9dia)", false);
        boolean mono = true;
        double[] prevC = null, prevQ = null;
        int littleBad = 0, littleWarn = 0;
        String worst = "";
        double worstErr = 0;
        for (int lp = 0; lp < lps; lp++) {
            double[] cb = LadderIO.meanCi(classicalBp(c.dir, lp));
            double[] qb = LadderIO.meanCi(col(st, "QKDBlockingProbability", lp));
            rep.point(ch, "Cl\u00e1ssico", loads[lp], cb);
            rep.point(ch, "QKD", loads[lp], qb);
            if (prevC != null) {
                if (cb[0] < prevC[0] - Math.max(cb[2] + prevC[2], 0.002)) mono = false;
                if (qb[0] < prevQ[0] - Math.max(qb[2] + prevQ[2], 0.002)) mono = false;
            }
            prevC = cb;
            prevQ = qb;
            if (little) {
                double[] sess = LadderIO.meanCi(col(st, "MeanActiveQKDSessions", lp));
                double expected = loads[lp] * protectedShare * (1 - qb[0]);
                rep.point(chs, "Simulado (PASTA)", loads[lp], sess);
                rep.point(chs, "Lei de Little: A\u00b7p\u00b7(1\u2212B)", loads[lp], expected, Double.NaN, Double.NaN);
                double err = expected > 0 ? Math.abs(sess[0] - expected) / expected : 0;
                if (err > worstErr) { worstErr = err; worst = "LP" + lp + ": " + fmt(sess[0]) + " contra " + fmt(expected); }
                if (err > 0.10) littleBad++;
                else if (err > 0.05) littleWarn++;
            }
        }
        if (protectedShare >= 1.0 && c.spec.protectedProbability >= 1.0 && c.spec.generatorProtectedProbability == null) {
            int diff = 0, n = 0;
            for (int lp = 0; lp < lps; lp++) {
                double[] cb = classicalBp(c.dir, lp), qb = col(st, "QKDBlockingProbability", lp);
                for (int i = 0; i < Math.min(cb.length, qb.length); i++) { n++; if (Math.abs(cb[i] - qb[i]) > 1e-12) diff++; }
            }
            rep.check(prefix + ".same." + c.spec.topology, diff == 0 && n > 0, LadderReport.FAIL,
                    L + ": com 100 % das requisi\u00e7\u00f5es protegidas e o mesmo aquecimento, bloqueio QKD = bloqueio cl\u00e1ssico (BlockingProbability e QKDMetrics contam as mesmas requisi\u00e7\u00f5es)",
                    diff + " diferen\u00e7a(s) em " + n, "0", null);
        }
        rep.check(prefix + ".mono." + c.spec.topology, mono, LadderReport.FAIL, L + ": bloqueio cl\u00e1ssico e QKD n\u00e3o diminuem com a carga (toler\u00e2ncia: IC95%)",
                mono ? "n\u00e3o decrescente" : "diminuiu", "n\u00e3o decrescente", null);
        if (little) rep.check(prefix + ".little." + c.spec.topology, littleBad == 0 && littleWarn == 0, littleBad > 0 ? LadderReport.FAIL : LadderReport.WARN,
                L + ": sess\u00f5es QKD ativas = carga protegida \u00d7 (1 \u2212 bloqueio QKD) (lei de Little, \u00b15 %)",
                String.format(Locale.US, "maior desvio %.1f %%", 100 * worstErr), "\u2264 5 %", worst);
    }

    LadderReport d1() throws Exception {
        LadderReport rep = report("D1", "Tr\u00e1fego din\u00e2mico (chegadas de Poisson, dura\u00e7\u00e3o exponencial) \u2014 7 n\u00facleos, XT, QTND 25 km");
        List<Case> cases = d1Cases();
        runAll(rep, cases);
        for (int i = 0; i < cases.size(); i++) dynamicChecks(rep, "d1", cases.get(i), TOPO_LABEL[i], 1.0, true);
        conservation(rep, dirs(cases), "d1");
        rep.note("Enlaces com " + DYN_SLOTS + " slots (40 canais de 75 GHz por n\u00facleo) para que cada replica\u00e7\u00e3o cubra v\u00e1rias dura\u00e7\u00f5es m\u00e9dias. Cargas = (requisi\u00e7\u00f5es do artigo \u00d7 6/10 \u00d7 " + DYN_SLOTS + "/960) \u00d7 " + Arrays.toString(prof.dynLoadFactors) + " Erlangs; "
                + prof.dynRequests + " chegadas por replica\u00e7\u00e3o, 10 % de aquecimento descartado.");
        return rep;
    }

    LadderReport d2() throws Exception {
        LadderReport rep = report("D2", "Converg\u00eancia: dobro de chegadas e 20 % de aquecimento contra o D1");
        List<Case> d1 = d1Cases();
        List<Case> d2 = new ArrayList<Case>();
        for (Case c : d1) {
            Spec s = c.spec.copy();
            s.requests = prof.dynRequests * 2;
            s.warmup = 0.2;
            d2.add(cs("D2", c.spec.topology + "_dinamico_2x", s));
        }
        List<Case> all = new ArrayList<Case>(d1);
        all.addAll(d2);
        runAll(rep, all);
        for (int i = 0; i < d1.size(); i++) {
            String t = d1.get(i).spec.topology, L = TOPO_LABEL[i];
            rep.chart("conv_" + t, L + " \u2014 bloqueio: D1 \u00d7 D2", "Carga (Erlang)", "Probabilidade de bloqueio", false);
            int bad = 0;
            String ex = "";
            for (int lp = 0; lp < d1.get(i).spec.loadPoints; lp++) {
                double load = d1.get(i).spec.load0 + lp * d1.get(i).spec.loadIncrement;
                for (String kind : new String[] {"Cl\u00e1ssico", "QKD"}) {
                    double[] a = "QKD".equals(kind) ? LadderIO.meanCi(col(stats(d1.get(i).dir), "QKDBlockingProbability", lp)) : LadderIO.meanCi(classicalBp(d1.get(i).dir, lp));
                    double[] b = "QKD".equals(kind) ? LadderIO.meanCi(col(stats(d2.get(i).dir), "QKDBlockingProbability", lp)) : LadderIO.meanCi(classicalBp(d2.get(i).dir, lp));
                    rep.point("conv_" + t, "D1 " + kind, load, a);
                    rep.point("conv_" + t, "D2 " + kind, load, b);
                    double tol = Math.max(a[2] + b[2], 0.003 + 0.10 * Math.max(a[0], b[0]));
                    if (Math.abs(a[0] - b[0]) > tol) { bad++; if (ex.isEmpty()) ex = kind + " LP" + lp + ": " + fmt(a[0]) + " \u00d7 " + fmt(b[0]); }
                }
            }
            rep.check("d2.conv." + t, bad == 0, LadderReport.WARN, L + ": bloqueios do D1 e do D2 compat\u00edveis (IC95% somados ou 10 % + 0,003)",
                    bad + " ponto(s) fora", "0", ex.isEmpty() ? null : ex + " \u2014 aumente as chegadas por replica\u00e7\u00e3o");
        }
        conservation(rep, dirs(d2), "d2");
        return rep;
    }

    // =================================================================== Phase E (qualification features)

    Spec eSpec(String t, int level) throws Exception {
        Spec s = dSpec(t);
        double[] loads = dynLoads(t);
        double mid = loads[Math.min(1, loads.length - 1)];
        s.load0 = mid;
        s.loadIncrement = 0;
        s.loadPoints = 1;
        s.granularity = "route";
        if (level >= 2) s.alternativeRoutes = 2;
        if (level >= 3) s.pichSlots = 1;
        if (level >= 4) { s.generatorProtectedProbability = 0.2; s.keyRateMbps = 2.0; }
        return s;
    }

    LadderReport e(String step) throws Exception {
        int level = step.charAt(1) - '0';
        String[] titles = {"", "Modo route: QCh dedicado com o mesmo slot em todos os trechos (mesma carga do D1)",
            "Route + rotas QKD alternativas (k = 2)", "Route + alternativas + PICh (1 slot, por enlace, compartilhado)",
            "Route + alternativas + PICh + demanda por gerador (20 % protegidas, 2 Mbit/s)"};
        LadderReport rep = report(step, titles[level]);
        List<Case> mine = new ArrayList<Case>(), prev = new ArrayList<Case>();
        for (String t : ARTICLE_TOPOLOGIES) {
            mine.add(cs(step, t, eSpec(t, level)));
            if (level >= 2) prev.add(cs("E" + (level - 1), t, eSpec(t, level - 1)));
        }
        List<Case> all = new ArrayList<Case>(prev);
        all.addAll(mine);
        runAll(rep, all);
        rep.chart("bp", "Bloqueio por topologia", "Topologia (1 = Small Core, 2 = Butterfly, 3 = Metro)", "Probabilidade de bloqueio", false);
        rep.chart("causes", "Causas do bloqueio QKD", "Topologia (1 = Small Core, 2 = Butterfly, 3 = Metro)", "Probabilidade de bloqueio", false);
        double minDemand = level >= 4 ? 2.0 : 1.0;
        for (int i = 0; i < mine.size(); i++) {
            Case c = mine.get(i);
            String L = TOPO_LABEL[i];
            List<Map<String, String>> st = stats(c.dir);
            double[] qb = LadderIO.meanCi(col(st, "QKDBlockingProbability", 0)), cb = LadderIO.meanCi(classicalBp(c.dir, 0));
            rep.point("bp", step + " QKD", i + 1, qb);
            rep.point("bp", step + " cl\u00e1ssico", i + 1, cb);
            LadderIO.Metrics q = qm(c.dir);
            if (q != null) for (String cause : new String[] {"classical resources", "QCh spectrum", "SKR", "PICh", "lack of QTx/QRx", "other"}) {
                rep.point("causes", cause, i + 1, LadderIO.meanCi(q.get("QKD blocking probability by " + cause, 0)));
            }
            double minSkr = Double.POSITIVE_INFINITY;
            for (double v : col(st, "MinimumAdmissionRawSKR_Mbps", 0)) if (!Double.isNaN(v) && !Double.isInfinite(v)) minSkr = Math.min(minSkr, v);
            rep.check(step + ".skr." + c.spec.topology, Double.isInfinite(minSkr) || minSkr >= minDemand - 1e-9, LadderReport.FAIL,
                    L + ": toda sess\u00e3o admitida tem SKR \u2265 demanda de chave", Double.isInfinite(minSkr) ? "sem admiss\u00f5es" : fmt(minSkr) + " Mbit/s", "\u2265 " + fmt(minDemand) + " Mbit/s", null);
            if (level >= 2) {
                List<Map<String, String>> sp = stats(prev.get(i).dir);
                double[] qp = LadderIO.meanCi(col(sp, "QKDBlockingProbability", 0)), cp = LadderIO.meanCi(classicalBp(prev.get(i).dir, 0));
                rep.point("bp", "E" + (level - 1) + " QKD", i + 1, qp);
                rep.point("bp", "E" + (level - 1) + " cl\u00e1ssico", i + 1, cp);
                if (level == 2) {
                    rep.check("E2.alt." + c.spec.topology, qb[0] <= qp[0] + Math.max(qb[2] + qp[2], 0.002), LadderReport.WARN,
                            L + ": rotas alternativas n\u00e3o aumentam o bloqueio QKD", LadderIO.pct(qb[0]) + " contra " + LadderIO.pct(qp[0]) + " no E1", "\u2264 E1", null);
                    rep.check("E2.ratio." + c.spec.topology, LadderReport.INFO, L + ": fra\u00e7\u00e3o de sess\u00f5es em rota alternativa",
                            q == null ? "\u2014" : LadderIO.pct(q.mean("Alternative QKD route ratio", 0)), null, null);
                } else if (level == 3) {
                    rep.check("E3.cls." + c.spec.topology, cb[0] >= cp[0] - Math.max(cb[2] + cp[2], 0.002), LadderReport.WARN,
                            L + ": o PICh ocupa espectro cl\u00e1ssico, ent\u00e3o o bloqueio cl\u00e1ssico n\u00e3o diminui", LadderIO.pct(cb[0]) + " contra " + LadderIO.pct(cp[0]) + " no E2", "\u2265 E2", null);
                    rep.check("E3.pich." + c.spec.topology, LadderReport.INFO, L + ": bloqueio QKD por falta de PICh",
                            q == null ? "\u2014" : LadderIO.pct(q.mean("QKD blocking probability by PICh", 0)), null,
                            "o qrmsta j\u00e1 reserva o 1\u00ba canal de cada n\u00facleo cl\u00e1ssico para o PICh, como no artigo");
                }
            }
            if (level >= 4) {
                double att = 0, tot = 0;
                for (double v : col(st, "ProtectedAttempts", 0)) att += v;
                for (double v : col(st, "TotalRequestAttempts", 0)) tot += v;
                double frac = tot > 0 ? att / tot : Double.NaN;
                double sigma = Math.sqrt(0.2 * 0.8 / Math.max(1, tot));
                rep.check("E4.frac." + c.spec.topology, Math.abs(frac - 0.2) <= 4 * sigma, LadderReport.FAIL, L + ": fra\u00e7\u00e3o de requisi\u00e7\u00f5es protegidas \u2248 0,2 (\u00b14\u03c3 binomial)",
                        String.format(Locale.US, "%.4f", frac), String.format(Locale.US, "0,2 \u00b1 %.4f", 4 * sigma), null);
                rep.check("E4.key." + c.spec.topology, q == null || Math.abs(q.mean("Mean key demand (Mbit/s)", 0) - 2.0) < 1e-9, LadderReport.FAIL,
                        L + ": demanda m\u00e9dia de chave = keyRateMbps do gerador", q == null ? "\u2014" : fmt(q.mean("Mean key demand (Mbit/s)", 0)) + " Mbit/s", "2 Mbit/s", null);
            }
        }
        if (level == 1) {
            List<Case> d1 = d1Cases();
            rep.note("Compara\u00e7\u00e3o com o D1 (modo span + TDM, mesma topologia, ponto de carga do meio):");
            for (int i = 0; i < d1.size(); i++) {
                if (!LadderRunner.hasResults(d1.get(i).dir)) continue;
                double[] qd = LadderIO.meanCi(col(stats(d1.get(i).dir), "QKDBlockingProbability", Math.min(1, d1.get(i).spec.loadPoints - 1)));
                rep.point("bp", "D1 (span) QKD", i + 1, qd);
            }
        }
        conservation(rep, dirs(mine), step);
        return rep;
    }

    // =================================================================== Phase F (complete)

    LadderReport f(String step, String topo, String title, int requests, double[] loads) throws Exception {
        LadderReport rep = report(step, title + " (7 n\u00facleos, XT + QoT, din\u00e2mico, route, alternativas, PICh, 20 % protegidas, 2 Mbit/s)");
        Spec s = new Spec();
        s.family = Family.QUALIFICATION;
        s.topology = topo;
        s.cores = 7;
        s.quantumCore = 1;
        s.qtnd = 30;
        s.requests = requests;
        s.replications = prof.reps;
        s.xt = true;
        s.qot = true;
        s.granularity = "route";
        s.alternativeRoutes = 2;
        s.pichSlots = 1;
        s.generatorProtectedProbability = 0.2;
        s.keyRateMbps = 2.0;
        s.dynamic = true;
        s.load0 = loads[0];
        s.loadIncrement = loads[1] - loads[0];
        s.loadPoints = loads.length;
        s.warmup = 0.1;
        s.metrics = true;
        Case c = cs(step, topo + "_completo", s);
        List<Case> all = new ArrayList<Case>();
        all.add(c);
        runAll(rep, all);
        dynamicChecks(rep, step.toLowerCase(Locale.ROOT), c, label(topo), 0.2, true);
        List<Map<String, String>> st = stats(c.dir);
        double minSkr = Double.POSITIVE_INFINITY;
        for (Map<String, String> r : st) { double v = LadderIO.num(r, "MinimumAdmissionRawSKR_Mbps"); if (!Double.isNaN(v)) minSkr = Math.min(minSkr, v); }
        rep.check(step + ".skr", Double.isInfinite(minSkr) || minSkr >= 2.0 - 1e-9, LadderReport.FAIL, "Toda sess\u00e3o admitida tem SKR \u2265 2 Mbit/s",
                Double.isInfinite(minSkr) ? "sem admiss\u00f5es" : fmt(minSkr) + " Mbit/s", "\u2265 2", null);
        double att = 0, tot = 0;
        for (Map<String, String> r : st) { att += LadderIO.num(r, "ProtectedAttempts"); tot += LadderIO.num(r, "TotalRequestAttempts"); }
        double frac = att / Math.max(1, tot), sigma = Math.sqrt(0.16 / Math.max(1, tot));
        rep.check(step + ".frac", Math.abs(frac - 0.2) <= 4 * sigma, LadderReport.FAIL, "Fra\u00e7\u00e3o de requisi\u00e7\u00f5es protegidas \u2248 0,2", String.format(Locale.US, "%.4f", frac),
                String.format(Locale.US, "0,2 \u00b1 %.4f", 4 * sigma), null);
        trustedNodes(rep, step, c.dir, sc.topology(topo), 30);
        LadderIO.Metrics q = qm(c.dir);
        if (q != null) {
            rep.chart("causes", "Causas do bloqueio QKD \u00d7 carga", "Carga (Erlang)", "Probabilidade de bloqueio", false);
            for (int lp = 0; lp < loads.length; lp++) for (String cause : new String[] {"classical resources", "QCh spectrum", "SKR", "PICh", "other"})
                rep.point("causes", cause, loads[lp], LadderIO.meanCi(q.get("QKD blocking probability by " + cause, lp)));
        }
        symmetry(rep, step, c, label(topo));
        conservation(rep, Arrays.asList(c.dir), step);
        rep.note("Abra o caso no launcher (Resultados) para o mapa de calor dos enlaces por ponto de carga e o diagrama dos n\u00facleos.");
        return rep;
    }

    /** Planned trusted nodes and spans of a case against the analytic count. */
    void trustedNodes(LadderReport rep, String id, File dir, ReferenceTopologySpec topo, double qtnd) throws Exception {
        int trusted = 0, spans = 0;
        for (ReferenceTopologySpec.Edge e : topo.getEdges()) {
            int k = Math.max(1, (int) Math.ceil(e.getLengthKm() / qtnd - 1e-12));
            trusted += k - 1;
            spans += 2 * k;
        }
        LadderIO.Metrics q = qm(dir);
        double t = q == null ? Double.NaN : q.mean("Planned trusted nodes", 0);
        double sp = q == null ? Double.NaN : q.mean("Planned QKD spans (directed links)", 0);
        rep.check(id + ".tn", t == trusted && sp == spans, LadderReport.FAIL,
                "N\u00f3s confi\u00e1veis e trechos planejados = \u03a3 \u2308L/QTND\u2309 \u2212 1 e 2\u00b7\u03a3 \u2308L/QTND\u2309 (QTND " + fmt(qtnd) + " km)",
                fmt(t) + " n\u00f3s, " + fmt(sp) + " trechos", trusted + " n\u00f3s, " + spans + " trechos", null);
    }

    /** Relative difference of the utilization of both directions of each link. */
    void symmetry(LadderReport rep, String id, Case c, String L) throws Exception {
        LadderIO.Metrics u = su(c.dir);
        if (u == null) return;
        for (int lp = 0; lp < c.spec.loadPoints; lp++) {
            Map<String, Double> per = new LinkedHashMap<String, Double>();
            for (Object[] row : u.rows("Utilization Per Link")) {
                String[] k = (String[]) row[0];
                if (Integer.parseInt(k[1]) != lp) continue;
                per.put(k[2], LadderIO.mean((double[]) row[1]));
            }
            double sum = 0, worst = 0;
            int n = 0;
            String worstLink = "";
            for (Map.Entry<String, Double> e : per.entrySet()) {
                String l = e.getKey();
                if (!l.startsWith("<") || !l.contains("-")) continue;
                String[] ab = l.substring(1, l.length() - 1).split("-");
                Double back = per.get("<" + ab[1] + "-" + ab[0] + ">");
                if (back == null || ab[0].compareTo(ab[1]) > 0) continue;
                double m = (e.getValue() + back) / 2;
                if (m <= 0) continue;
                double d = Math.abs(e.getValue() - back) / m;
                sum += d;
                n++;
                if (d > worst) { worst = d; worstLink = ab[0] + "\u2194" + ab[1]; }
            }
            if (n == 0) continue;
            double mean = sum / n;
            rep.check(id + ".sym.lp" + lp, mean <= 0.25, LadderReport.WARN, L + ", ponto " + (lp + 1) + ": utiliza\u00e7\u00e3o dos dois sentidos de cada enlace parecida (tr\u00e1fego uniforme)",
                    String.format(Locale.US, "diferen\u00e7a m\u00e9dia %.1f %%, maior %.1f %% (%s)", 100 * mean, 100 * worst, worstLink), "m\u00e9dia \u2264 25 %",
                    "o desempate de rotas de mesmo comprimento pode ser diferente em cada sentido");
        }
    }

    // =================================================================== X1 Erlang-B

    LadderReport x1() throws Exception {
        LadderReport rep = report("X1", "Erlang-B: enlace \u00fanico com C canais e chegadas de Poisson");
        List<Case> cases = new ArrayList<Case>();
        int[][] cfg = {{2, 10}, {3, 20}};
        double[][] loads = {{3, 5, 7, 9}, {10, 13, 16, 19}};
        for (int k = 0; k < cfg.length; k++) {
            Spec s = new Spec();
            s.topology = "two_nodes";
            s.cores = cfg[k][0];
            s.quantumCore = cfg[k][0] - 1;
            s.protectedProbability = 0.0;
            s.linkSlots = 60;
            s.reservePich = false;
            s.onlyPair = new String[] {"1", "2"};
            s.bitRatesGbps = new double[] {100};
            s.replications = prof.reps;
            s.dynamic = true;
            s.requests = prof.erlangRequests;
            s.load0 = loads[k][0];
            s.loadIncrement = loads[k][1] - loads[k][0];
            s.loadPoints = loads[k].length;
            s.warmup = 0.1;
            cases.add(cs("X1", "erlang_C" + cfg[k][1], s));
        }
        runAll(rep, cases);
        rep.chart("eb", "Bloqueio simulado \u00d7 Erlang-B", "Carga (Erlang)", "Probabilidade de bloqueio", true);
        for (int k = 0; k < cases.size(); k++) {
            int c = cfg[k][1];
            for (int lp = 0; lp < loads[k].length; lp++) {
                double[] sim = LadderIO.meanCi(classicalBp(cases.get(k).dir, lp));
                double eb = LadderIO.erlangB(c, loads[k][lp]);
                rep.point("eb", "Simulado C = " + c, loads[k][lp], sim);
                rep.point("eb", "Erlang-B C = " + c, loads[k][lp], eb, Double.NaN, Double.NaN);
                double tol = Math.max(3 * sim[2], 0.10 * eb + 0.0005);
                rep.check("x1.c" + c + ".lp" + lp, Math.abs(sim[0] - eb) <= tol, LadderReport.FAIL,
                        "C = " + c + " canais, A = " + fmt(loads[k][lp]) + " E: bloqueio simulado = Erlang-B",
                        String.format(Locale.US, "%.5f \u00b1 %.5f", sim[0], sim[2]), String.format(Locale.US, "%.5f (toler\u00e2ncia %.5f)", eb, tol), null);
            }
        }
        rep.note("Enlace de 50 km, 60 slots = 10 canais de 75 GHz por n\u00facleo cl\u00e1ssico (1 ou 2 n\u00facleos cl\u00e1ssicos), PICh n\u00e3o reservado, "
                + "100 Gbit/s = 1 canal em 64QAM; sem QKD (fra\u00e7\u00e3o protegida 0). Cada canal \u00e9 um servidor: o bloqueio deve seguir Erlang-B.");
        return rep;
    }

    // =================================================================== X2 trusted nodes

    LadderReport x2() throws Exception {
        LadderReport rep = report("X2", "N\u00f3s confi\u00e1veis e trechos planejados \u00d7 f\u00f3rmula anal\u00edtica");
        String[] topos = {"small_core", "butterfly", "metro", "usa"};
        int[] qs = {5, 10, 25, 50, 100, 3000};
        List<Case> cases = new ArrayList<Case>();
        for (String t : topos) for (int q : qs) {
            Spec s = new Spec();
            s.topology = t;
            s.qtnd = q;
            s.requests = 30;
            s.replications = 1;
            s.metrics = true;
            cases.add(cs("X2", String.format(Locale.US, "%s_QTND_%04d", t, q), s));
        }
        runAll(rep, cases);
        rep.chart("tn", "N\u00f3s confi\u00e1veis planejados \u00d7 QTND", "QTND (km)", "N\u00f3s confi\u00e1veis", true);
        for (String t : topos) {
            ReferenceTopologySpec topo = sc.topology(t);
            double prev = Double.POSITIVE_INFINITY;
            boolean mono = true;
            double longest = 0;
            for (ReferenceTopologySpec.Edge e : topo.getEdges()) longest = Math.max(longest, e.getLengthKm());
            for (Case c : cases) {
                if (!c.spec.topology.equals(t)) continue;
                trustedNodes(rep, "x2." + t + "." + c.spec.qtnd, c.dir, topo, c.spec.qtnd);
                LadderIO.Metrics q = qm(c.dir);
                double tn = q == null ? Double.NaN : q.mean("Planned trusted nodes", 0);
                rep.point("tn", label(t), c.spec.qtnd, Math.max(tn, 0.5), Double.NaN, Double.NaN);
                if (tn > prev) mono = false;
                prev = tn;
                if (c.spec.qtnd >= longest) rep.check("x2.zero." + t, tn == 0, LadderReport.FAIL, label(t) + ": QTND \u2265 maior enlace (" + fmt(longest) + " km) \u21d2 nenhum n\u00f3 confi\u00e1vel",
                        fmt(tn), "0", null);
            }
            rep.check("x2.mono." + t, mono, LadderReport.FAIL, label(t) + ": QTND menor \u21d2 mais (ou iguais) n\u00f3s confi\u00e1veis", mono ? "monot\u00f4nico" : "violado", "n\u00e3o crescente em QTND", null);
        }
        rep.note("No gr\u00e1fico, zero n\u00f3s aparece como 0,5 por causa da escala logar\u00edtmica.");
        return rep;
    }

    // =================================================================== X3 SKR independent

    LadderReport x3() throws Exception {
        LadderReport rep = report("X3", "SKR: modelo do simulador \u00d7 implementa\u00e7\u00e3o independente da eq. (1) do artigo");
        BB84DecoyModel model = new BB84DecoyModel();
        double worst = 0;
        String where = "";
        boolean mono = true;
        for (int lambda = 1520; lambda <= 1620; lambda += 5) {
            double prev = Double.POSITIVE_INFINITY;
            for (int L = 0; L <= 80; L++) {
                double a = model.evaluateMultiFiber(lambda, L).getSecretKeyRateBps();
                double b = IndependentBB84.evaluate(lambda, L, IndependentBB84.PDARK)[5];
                double rel = Math.abs(a - b) / Math.max(1.0, Math.abs(b));
                if (rel > worst) { worst = rel; where = lambda + " nm, " + L + " km"; }
                if (a > prev + 1e-6) mono = false;
                prev = a;
            }
        }
        rep.check("x3.indep", worst <= 1e-9, LadderReport.FAIL, "SKR do simulador = implementa\u00e7\u00e3o independente (1520\u20131620 nm, 0\u201380 km)",
                String.format(Locale.US, "maior diferen\u00e7a relativa %.2e", worst), "\u2264 1e-9", where);
        rep.check("x3.mono", mono, LadderReport.FAIL, "SKR diminui com a dist\u00e2ncia em todos os comprimentos de onda", mono ? "sim" : "n\u00e3o", "sim", null);
        QKDPhysicalResult r = model.evaluateMultiFiber(1550, 25);
        Object[][] t5 = {{"\u03b7", r.getTransmittanceWithDetector(), 0.106444, 5e-7}, {"Q_\u03bc", r.getQMu(), 0.071803, 5e-7}, {"Q_1", r.getQ1(), 0.037001, 5e-7},
            {"E_\u03bc", r.getEMu(), 0.015000, 5e-7}, {"H(E_\u03bc)", r.getEntropyEMu(), 0.112361, 5e-7}, {"SKR (Mbit/s)", r.getSecretKeyRateMbps(), 93.939373, 5e-6}};
        for (Object[] row : t5) {
            double v = (Double) row[1], e = (Double) row[2], tol = (Double) row[3];
            rep.check("x3.t5." + row[0], Math.abs(v - e) <= tol, LadderReport.FAIL, "Tabela 5 da qualifica\u00e7\u00e3o (1550 nm, 25 km): " + row[0],
                    String.format(Locale.US, "%.6f", v), String.format(Locale.US, "%.6f", e), null);
        }
        double best = -1;
        int bestL = 0, bestQ = 0;
        int[] qs = {1, 5, 10, 15, 20, 25, 30, 35, 40};
        rep.chart("fig5", "SKR/Q-TRx \u00d7 QTND num enlace de 40 km (Fig. 5(b))", "QTND (km)", "SKR por Q-TRx (Mbit/s)", false);
        for (int lambda = 1520; lambda <= 1620; lambda += 10) {
            for (int q : qs) {
                double v = model.evaluateMultiFiber(lambda, q).getSecretKeyRateMbps() * q / 40.0;
                if (lambda == 1520 || lambda == 1550 || lambda == 1580 || lambda == 1620) rep.point("fig5", lambda + " nm", q, v, Double.NaN, Double.NaN);
                if (v > best) { best = v; bestL = lambda; bestQ = q; }
            }
        }
        rep.check("x3.fig5", bestL == 1550 && bestQ == 25, LadderReport.FAIL, "\u00d3timo da Fig. 5(b) (MF): 1550 nm e QTND 25 km",
                bestL + " nm, " + bestQ + " km", "1550 nm, 25 km", String.format(Locale.US, "SKR/Q-TRx = %.4f Mbit/s", best));
        rep.chart("skr", "SKR \u00d7 dist\u00e2ncia", "Comprimento do trecho (km)", "SKR (Mbit/s)", true);
        for (int lambda : new int[] {1520, 1550, 1620}) for (int L = 0; L <= 80; L += 5) {
            rep.point("skr", "Simulador " + lambda + " nm", L, model.evaluateMultiFiber(lambda, L).getSecretKeyRateMbps(), Double.NaN, Double.NaN);
            if (lambda == 1550) rep.point("skr", "Independente 1550 nm", L, IndependentBB84.evaluate(lambda, L, IndependentBB84.PDARK)[5] / 1e6, Double.NaN, Double.NaN);
        }
        double prev = Double.POSITIVE_INFINITY;
        boolean noise = true;
        for (double pb = 1e-8; pb <= 1e-4; pb *= 10) {
            double v = model.evaluate(1550, 25, pb).getSecretKeyRateBps();
            if (v > prev) noise = false;
            prev = v;
        }
        rep.check("x3.noise", noise, LadderReport.FAIL, "Mais ru\u00eddo de fundo (p_b, ex.: crosstalk) \u21d2 SKR menor", noise ? "sim" : "n\u00e3o", "sim", null);
        return rep;
    }

    // =================================================================== X4 limits

    LadderReport x4() throws Exception {
        LadderReport rep = report("X4", "Testes de limite (devem dar resultado id\u00eantico ou nulo)");
        String t = "small_core";
        double load = Math.round(sc.referenceRequests(t) / 10.0 * 2.0);
        Spec q = sdm(t, 2, 1, 1);
        q = dyn(q, new double[] {load, load * 1.5});
        q.protectedProbability = 0.0;
        Spec k = dSpec(t);
        k.granularity = "route";
        k.loadPoints = 1;
        k.keyRateMbps = 0.001;
        Spec k2 = k.copy();
        k2.keyRateMbps = 2.0;
        List<Case> cases = Arrays.asList(cs("X4", "a_fracao0_2nucleos", q),
                cs("X4", "b_demanda_0001", k), cs("X4", "b_demanda_2", k2));
        runAll(rep, cases);
        List<Map<String, String>> st0 = stats(cases.get(0).dir);
        double att = 0, qtrx = 0;
        for (Map<String, String> r : st0) { att += LadderIO.num(r, "ProtectedAttempts"); qtrx += LadderIO.num(r, "PeakQTRxPairs"); }
        LadderIO.Metrics u0 = su(cases.get(0).dir);
        double core1 = 0;
        if (u0 != null) for (int lp = 0; lp < 2; lp++) { double[] v = u0.get("Utilization per Core", lp, "all", "1"); if (v != null) core1 = Math.max(core1, max(v)); }
        double bpv = LadderIO.mean(classicalBp(cases.get(0).dir, 1));
        rep.check("x4.frac0", att == 0 && qtrx == 0, LadderReport.FAIL, "Fra\u00e7\u00e3o protegida 0 \u21d2 nenhuma requisi\u00e7\u00e3o protegida, nenhum QCh nem par Q-TRx",
                fmt(att) + " protegidas, " + fmt(qtrx) + " Q-TRx", "0 e 0", null);
        rep.check("x4.core", core1 == 0, LadderReport.FAIL, "O n\u00facleo reservado para QKD nunca \u00e9 usado pelo tr\u00e1fego cl\u00e1ssico, mesmo com o n\u00facleo cl\u00e1ssico saturado",
                "utiliza\u00e7\u00e3o do n\u00facleo 1 = " + fmt(core1), "0", "bloqueio cl\u00e1ssico no 2\u00ba ponto: " + LadderIO.pct(bpv));
        LadderIO.Metrics m1 = qm(cases.get(1).dir), m2 = qm(cases.get(2).dir);
        double skr1 = m1 == null ? Double.NaN : max(m1.get("QKD blocking probability by SKR", 0));
        double skr2 = m2 == null ? Double.NaN : LadderIO.mean(m2.get("QKD blocking probability by SKR", 0));
        rep.check("x4.key", skr1 == 0, LadderReport.FAIL, "Demanda de chave \u2192 0 (0,001 Mbit/s) \u21d2 nenhum bloqueio por SKR", LadderIO.pct(skr1), "0 %",
                "com 2 Mbit/s: " + LadderIO.pct(skr2));
        rep.check("x4.qtnd", LadderReport.INFO, "QTND \u2265 maior enlace \u21d2 nenhum n\u00f3 confi\u00e1vel", "verificado no X2", null, null);
        rep.check("x4.xt", LadderReport.INFO, "XT \u2192 0 reproduz o caso sem XT", "verificado no B2L", null, null);
        conservation(rep, dirs(cases), "x4");
        return rep;
    }

    // =================================================================== X5 monotonicity

    LadderReport x5() throws Exception {
        LadderReport rep = report("X5", "Monotonicidade: demanda de chave e n\u00famero de n\u00facleos");
        String t = "small_core";
        double[] keys = {1, 8, 32, 64, 128};
        List<Case> kc = new ArrayList<Case>();
        for (double k : keys) {
            Spec s = dSpec(t);
            s.granularity = "route";
            s.qtnd = 40;
            s.loadPoints = 1;
            s.keyRateMbps = k;
            kc.add(cs("X5", String.format(Locale.US, "chave_%03.0f_Mbps", k), s));
        }
        int[] cores = {2, 7, 12, 19};
        double load = Math.round(sc.referenceRequests(t) / 10.0 * 3.0);
        List<Case> cc = new ArrayList<Case>();
        for (int n : cores) {
            Spec s = sdm(t, n, 1, n - 1);
            s.xt = true;
            s = dyn(s, new double[] {load});
            cc.add(cs("X5", "nucleos_" + n, s));
        }
        List<Case> all = new ArrayList<Case>(kc);
        all.addAll(cc);
        runAll(rep, all);
        rep.chart("key", "Bloqueio QKD \u00d7 demanda de chave (QTND 40 km, modo route)", "Demanda de chave (Mbit/s)", "Probabilidade de bloqueio", false);
        double prevS = -1, prevT = -1;
        boolean monoS = true, monoT = true;
        for (int i = 0; i < kc.size(); i++) {
            LadderIO.Metrics m = qm(kc.get(i).dir);
            double[] skr = LadderIO.meanCi(m == null ? null : m.get("QKD blocking probability by SKR", 0));
            double[] tot = LadderIO.meanCi(col(stats(kc.get(i).dir), "QKDBlockingProbability", 0));
            rep.point("key", "por SKR", keys[i], skr);
            rep.point("key", "total", keys[i], tot);
            if (prevS >= 0 && skr[0] < prevS - Math.max(skr[2], 0.002)) monoS = false;
            if (prevT >= 0 && tot[0] < prevT - Math.max(tot[2], 0.002)) monoT = false;
            prevS = skr[0];
            prevT = tot[0];
        }
        rep.check("x5.key.skr", monoS, LadderReport.FAIL, "Demanda de chave maior \u21d2 bloqueio por SKR maior (ou igual)", monoS ? "n\u00e3o decrescente" : "diminuiu", "n\u00e3o decrescente", null);
        rep.check("x5.key.tot", monoT, LadderReport.WARN, "Demanda de chave maior \u21d2 bloqueio QKD total maior (ou igual)", monoT ? "n\u00e3o decrescente" : "diminuiu", "n\u00e3o decrescente", null);
        rep.chart("cores", "Bloqueio cl\u00e1ssico \u00d7 n\u00facleos da MCF (mesma carga " + fmt(load) + " E)", "N\u00facleos da fibra", "Probabilidade de bloqueio", false);
        double prev = 2;
        boolean mono = true;
        for (int i = 0; i < cc.size(); i++) {
            double[] b = LadderIO.meanCi(classicalBp(cc.get(i).dir, 0));
            rep.point("cores", "Cl\u00e1ssico", cores[i], b);
            rep.point("cores", "QKD", cores[i], LadderIO.meanCi(col(stats(cc.get(i).dir), "QKDBlockingProbability", 0)));
            if (b[0] > prev + Math.max(b[2], 0.002)) mono = false;
            prev = b[0];
        }
        rep.check("x5.cores", mono, LadderReport.FAIL, "Mais n\u00facleos cl\u00e1ssicos \u21d2 bloqueio cl\u00e1ssico menor (ou igual), 2/7/12/19 n\u00facleos", mono ? "n\u00e3o crescente" : "aumentou", "n\u00e3o crescente", null);
        File x2 = new File(new File(root, "X2"), "status.txt"), d1 = new File(new File(root, "D1"), "status.txt");
        rep.check("x5.qtnd", LadderReport.INFO, "QTND menor \u21d2 mais n\u00f3s confi\u00e1veis", x2.isFile() ? "X2: " + LadderIO.read(x2).split("\n")[0] : "execute o X2", null, null);
        rep.check("x5.load", LadderReport.INFO, "Carga maior \u21d2 bloqueio maior", d1.isFile() ? "D1: " + LadderIO.read(d1).split("\n")[0] : "execute o D1", null, null);
        conservation(rep, dirs(all), "x5");
        return rep;
    }

    // =================================================================== X6 reproducibility

    LadderReport x6() throws Exception {
        LadderReport rep = report("X6", "Reprodutibilidade: mesma semente \u21d2 resultado id\u00eantico, tamb\u00e9m com outro n\u00famero de threads");
        Spec s = dSpec("small_core");
        s.granularity = "route";
        s.alternativeRoutes = 2;
        s.pichSlots = 1;
        s.generatorProtectedProbability = 0.5;
        s.keyRateMbps = 4.0;
        s.replications = 2;
        s.requests = Math.min(prof.dynRequests, 6000);
        s.loadPoints = 2;
        Spec seed = s.copy();
        seed.seed = 777;
        String userThreads = System.getProperty("snets.threads");
        boolean oldForce = runner.force;
        runner.force = true;
        String[] names = {"execucao_a_1thread", "execucao_b_1thread", "execucao_c_2threads", "semente_diferente"};
        Spec[] specs = {s, s, s, seed};
        String[] threads = {"1", "1", "2", "2"};
        try {
            for (int i = 0; i < names.length; i++) {
                System.setProperty("snets.threads", threads[i]);
                runner.setProgress(i / 4.0, 0.25);
                Case c = cs("X6", names[i], specs[i]);
                runner.ensure(c.dir, sc.files(c.spec));
                rep.addCase(c.dir);
            }
        } finally {
            runner.force = oldForce;
            if (userThreads == null) System.clearProperty("snets.threads"); else System.setProperty("snets.threads", userThreads);
        }
        Map<String, String> a = csvs(new File(new File(root, "X6"), names[0]));
        String[] cmp = {"mesma semente, de novo com 1 thread", "mesma semente com 2 threads"};
        for (int i = 1; i <= 2; i++) {
            Map<String, String> b = csvs(new File(new File(root, "X6"), names[i]));
            List<String> diff = new ArrayList<String>();
            for (String k : a.keySet()) if (!a.get(k).equals(b.get(k))) diff.add(k);
            rep.check("x6.same" + i, diff.isEmpty() && a.size() == b.size(), LadderReport.FAIL, "Resultados id\u00eanticos: " + cmp[i - 1],
                    diff.isEmpty() ? a.size() + " arquivos CSV id\u00eanticos" : diff.size() + " arquivo(s) diferente(s)", "todos id\u00eanticos", diff.isEmpty() ? null : diff.toString());
        }
        Map<String, String> d = csvs(new File(new File(root, "X6"), names[3]));
        int changed = 0;
        for (String k : a.keySet()) if (!a.get(k).equals(d.get(k))) changed++;
        rep.check("x6.seed", changed > 0, LadderReport.FAIL, "Outra semente de tr\u00e1fego \u21d2 resultados diferentes (a semente \u00e9 usada)", changed + " arquivo(s) diferente(s)", "> 0", null);
        return rep;
    }

    static Map<String, String> csvs(File dir) throws Exception {
        Map<String, String> out = new TreeMap<String, String>();
        File[] fs = dir.listFiles();
        if (fs != null) for (File f : fs) {
            if (!f.getName().endsWith(".csv")) continue;
            // rows are compared as a sorted set: SNetS writes the per-pair rows in hash order
            List<String> lines = LadderIO.lines(f);
            java.util.Collections.sort(lines);
            out.put(f.getName().substring(dir.getName().length() + 1), String.join("\n", lines));
        }
        return out;
    }

    // =================================================================== X7 conservation

    LadderReport x7() throws Exception {
        LadderReport rep = report("X7", "Conserva\u00e7\u00e3o em todos os casos j\u00e1 simulados do plano + caso com todos os recursos");
        Spec s = dSpec("small_core");
        s.granularity = "route";
        s.alternativeRoutes = 2;
        s.pichSlots = 1;
        s.generatorProtectedProbability = 0.5;
        s.keyRateMbps = 4.0;
        List<Case> mine = Arrays.asList(cs("X7", "todos_os_recursos", s));
        runAll(rep, mine);
        conservation(rep, dirs(mine), "x7.caso");
        List<File> every = new ArrayList<File>();
        collectCases(root, every);
        rep.check("x7.n", LadderReport.INFO, "Casos do plano com resultados", String.valueOf(every.size()), null, null);
        conservation(rep, every, "x7.todos");
        return rep;
    }

    static void collectCases(File dir, List<File> out) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (!f.isDirectory()) continue;
            if (new File(f, "simulation").isFile() && LadderRunner.hasResults(f)) out.add(f);
            else collectCases(f, out);
        }
    }

    // =================================================================== X8 symmetry

    LadderReport x8() throws Exception {
        LadderReport rep = report("X8", "Simetria: utiliza\u00e7\u00e3o dos dois sentidos dos enlaces com tr\u00e1fego uniforme (casos do D1)");
        List<Case> cases = d1Cases();
        runAll(rep, cases);
        for (int i = 0; i < cases.size(); i++) symmetry(rep, "x8." + cases.get(i).spec.topology, cases.get(i), TOPO_LABEL[i]);
        rep.note("Use o mapa de calor (Resultados \u203a Est\u00fadio \u203a Mapa de calor) nos casos do D1 para ver os dois sentidos lado a lado.");
        return rep;
    }
}
