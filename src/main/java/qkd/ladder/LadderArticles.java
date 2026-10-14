package qkd.ladder;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;

import qkd.analytical.QKDAnalyticalCTMC;
import qkd.analytical.QKDAnalyticalModel;
import qkd.analytical.QKDAnalyticalSimulation;
import qkd.ladder.LadderScenario.Spec;
import qkd.physical.QKDCrosstalkReceiverTransferMode;
import qkd.physical.QKDPhysicalLayer;
import qkd.physical.QKDPhysicalParameters;
import qkd.reference.ReferenceTopologySpec;

/**
 * Part 26: verification steps built on two more articles of docs/artigos.
 *
 * <ul>
 *   <li>Phase M: E. E. Moghaddam, H. Beyranvand, J. A. Salehi, "Resource Allocation in Space
 *       Division Multiplexed Elastic Optical Networks Secured With Quantum Key Distribution",
 *       IEEE JSAC 39(9):2688-2700, 2021.</li>
 *   <li>Phase Q: I. Ahmed, B. C. Chatterjee, E. Oki, "QKD-Analytical: Analytical Model for Blocking
 *       Probabilities in Priority-Aware QKD over SDM-EONs", IEEE INFOCOM 2026.</li>
 * </ul>
 *
 * <p>Refer\u00eancias: [MOGH21], [AHMED26a].</p>
 */
final class LadderArticles {

    private final QKDLadderMain m;

    LadderArticles(QKDLadderMain m) {
        this.m = m;
    }

    // =================================================================== Phase M (Moghaddam 2021)

    /** Table I of Moghaddam et al. (2021), with the attenuation alpha = 0.21 dB/km flat. */
    static QKDPhysicalParameters moghaddam(double pDark) {
        return new QKDPhysicalParameters(0.5, 250e-12, 1.16, 0.015, 0.3, pDark,
                12.5e9, -80.0, 0.21, 0.0, 1550.0, 6.62607015e-34, 299792458.0,
                100e-12, 1.0, QKDCrosstalkReceiverTransferMode.ATTENUATED_SPAN, true);
    }

    static Map<String, Object> moghaddamBlock() {
        Map<String, Object> q = new LinkedHashMap<String, Object>();
        q.put("meanPhotonNumber", 0.5);
        q.put("attenuationFloorDbPerKm", 0.21);
        q.put("attenuationQuadraticDbPerKmNm2", 0.0);
        q.put("darkCountProbability", 1e-8);
        return q;
    }

    /** SKR (bit/s) of one span with a classical channel of launch power p0W in an adjacent core at the same slot. */
    static double skr(QKDPhysicalLayer layer, double lengthKm, double hPerKm, double p0W) {
        double pxt = hPerKm > 0 && p0W > 0 ? QKDPhysicalLayer.calculateContributionPowerW(p0W, 1.0, hPerKm / 1000.0, lengthKm) : 0.0;
        double prx = QKDPhysicalLayer.toReceiverPowerW(pxt, lengthKm, layer.attenuationDbPerKm(1550.0),
                layer.getParameters().getReceiverTransferMode());
        double pb = layer.evaluateFromReceivedPower(prx, 1550.0).getTotalBackgroundProbability();
        return layer.getBb84Model().evaluate(1550.0, lengthKm, pb).getSecretKeyRateBps();
    }

    static double reach(QKDPhysicalLayer layer, double hPerKm, double p0W) {
        double last = 0;
        for (double L = 0.5; L <= 400; L += 0.5) {
            if (skr(layer, L, hPerKm, p0W) > 0) last = L;
            else if (last > 0 || L > 20) break;
        }
        return last;
    }

    LadderReport m1() {
        LadderReport rep = m.report("M1", "Moghaddam 2021, Fig. 5: SKR \u00d7 comprimento (contagem escura e crosstalk intercore)");
        QKDPhysicalLayer d8 = new QKDPhysicalLayer(moghaddam(1e-8)), d6 = new QKDPhysicalLayer(moghaddam(1e-6));
        double p0 = 1e-3; // 0 dBm
        Object[][] cases = {
            {"m1.dark8", "Contagem escura p_dark = 1e-8 (Fig. 5a)", d8, 0.0, 300.0, 0.05},
            {"m1.dark6", "Contagem escura p_dark = 1e-6 (Fig. 5d)", d6, 0.0, 205.0, 0.05},
            {"m1.xt630", "XT h = 6,3e-10 /km, P0 = 0 dBm, p_dark = 1e-8 (Fig. 5b)", d8, 6.3e-10, 115.0, 0.20},
            {"m1.xt630d6", "XT h = 6,3e-10 /km, P0 = 0 dBm, p_dark = 1e-6 (Fig. 5e)", d6, 6.3e-10, 115.0, 0.20},
            {"m1.xt46d6", "XT h = 4,6e-11 /km, P0 = 0 dBm, p_dark = 1e-6 (Fig. 5f)", d6, 4.6e-11, 190.0, 0.10},
        };
        for (Object[] c : cases) {
            double r = reach((QKDPhysicalLayer) c[2], (Double) c[3], p0);
            double paper = (Double) c[4], tol = (Double) c[5];
            rep.check((String) c[0], Math.abs(r - paper) <= tol * paper, LadderReport.WARN, "Alcance (SKR > 0): " + c[1],
                    QKDLadderMain.fmt(r) + " km", "\u2248 " + QKDLadderMain.fmt(paper) + " km \u00b1 " + Math.round(tol * 100) + " % (lido do gr\u00e1fico)", null);
        }
        double r7 = reach(d8, 1e-7, p0);
        rep.check("m1.xt1e7", r7 < 10, LadderReport.WARN, "XT h = 1e-7 /km, P0 = 0 dBm: a SKR zera em poucos km (curva ausente na Fig. 5a)",
                QKDLadderMain.fmt(r7) + " km", "< 10 km", null);
        double s0 = skr(d8, 0.5, 0, 0);
        rep.check("m1.s0", "INFO", "SKR perto de 0 km (o artigo mostra \u2248 1e8 bit/s)", String.format(Locale.US, "%.3g bit/s (\u00f7 2 = %.3g)", s0, s0 / 2),
                "\u2248 1e8 bit/s", "O BB84 do artigo-base (Dibaj) n\u00e3o tem o fator 1/2 de peneiramento; o gr\u00e1fico de Moghaddam parece t\u00ea-lo.");
        rep.chart("skr", "SKR \u00d7 comprimento do enlace (Moghaddam, Fig. 5)", "Comprimento (km)", "SKR (bit/s)", true);
        for (double L = 5; L <= 300; L += 5) {
            double a = skr(d8, L, 0, 0), b = skr(d6, L, 0, 0), c = skr(d8, L, 6.3e-10, p0), d = skr(d6, L, 4.6e-11, p0);
            if (a > 0) rep.point("skr", "escura 1e-8", L, a, Double.NaN, Double.NaN);
            if (b > 0) rep.point("skr", "escura 1e-6", L, b, Double.NaN, Double.NaN);
            if (c > 0) rep.point("skr", "XT h=6,3e-10 (1e-8)", L, c, Double.NaN, Double.NaN);
            if (d > 0) rep.point("skr", "XT h=4,6e-11 (1e-6)", L, d, Double.NaN, Double.NaN);
        }
        rep.note("Ru\u00eddo de XT pela eq. (1) de Moghaddam: I_XT = P0 e^{-\u03b1L} h L (modo de transfer\u00eancia \"attenuated\" da camada f\u00edsica QKD),"
                + " convertido em p_b pela janela do detector (T_d = 100 ps, \u03b7_d = 0,3). Par\u00e2metros da Tabela I: \u03bc = 0,5, T_s = 250 ps,"
                + " e_d = 0,015, f = 1,16, \u03b1 = 0,21 dB/km. Raman e FWM n\u00e3o s\u00e3o modelados pelo simulador (o QCh fica num n\u00facleo dedicado).");
        return rep;
    }

    /** Average over ordered node pairs of the minimum link SKR on the shortest path (trusted relays at nodes). */
    static double expectedAverageSkrMbps(ReferenceTopologySpec t, QKDPhysicalLayer layer) {
        List<Integer> nodes = t.getNodes();
        double sum = 0;
        int n = 0;
        for (int s : nodes) {
            Map<Integer, Double> dist = new HashMap<Integer, Double>();
            Map<Integer, double[]> prev = new HashMap<Integer, double[]>();
            PriorityQueue<double[]> pq = new PriorityQueue<double[]>((a, b) -> Double.compare(a[0], b[0]));
            dist.put(s, 0.0);
            pq.add(new double[] {0, s});
            while (!pq.isEmpty()) {
                double[] e = pq.poll();
                int u = (int) e[1];
                if (e[0] > dist.get(u)) continue;
                for (ReferenceTopologySpec.Edge ed : t.getAdjacentEdges(u)) {
                    int v = ed.other(u);
                    double nd = e[0] + ed.getLengthKm();
                    if (!dist.containsKey(v) || nd < dist.get(v) - 1e-9) {
                        dist.put(v, nd);
                        prev.put(v, new double[] {u, ed.getLengthKm()});
                        pq.add(new double[] {nd, v});
                    }
                }
            }
            for (int d : nodes) {
                if (d == s) continue;
                double min = Double.POSITIVE_INFINITY;
                int x = d;
                while (x != s) {
                    double[] p = prev.get(x);
                    min = Math.min(min, skr(layer, p[1], 0, 0));
                    x = (int) p[0];
                }
                sum += min;
                n++;
            }
        }
        return sum / n / 1e6;
    }

    LadderReport mNet(String step, String topology, String title, double paperMbps, String paperFig) throws Exception {
        LadderReport rep = m.report(step, title);
        Spec s = m.art(topology);
        s.qtnd = 150;
        s.dynamic = true;
        s.requests = m.prof.dynRequests;
        s.load0 = 40;
        s.loadIncrement = 0;
        s.loadPoints = 1;
        s.warmup = 0.1;
        s.metrics = true;
        s.keyRateMbps = 0.002;
        s.qkdPhysical = moghaddamBlock();
        s.shortestPath = true;
        QKDLadderMain.Case c = m.cs(step, topology + "_moghaddam", s);
        List<QKDLadderMain.Case> cases = new ArrayList<QKDLadderMain.Case>();
        cases.add(c);
        m.runAll(rep, cases);
        LadderIO.Metrics q = QKDLadderMain.qm(c.dir);
        double[] mean = LadderIO.meanCi(q == null ? null : q.get("Mean admission SKR (Mbit/s)", 0));
        double[] bp = LadderIO.meanCi(q == null ? null : q.get("QKD blocking probability", 0));
        double expected = expectedAverageSkrMbps(m.sc.topology(topology), new QKDPhysicalLayer(moghaddam(1e-8)));
        rep.check(step.toLowerCase(Locale.ROOT) + ".sim", Math.abs(mean[0] - expected) <= 0.05 * expected + mean[2], LadderReport.WARN,
                "SKR m\u00e9dia na admiss\u00e3o do SNetS = m\u00e9dia independente (menor SKR dos enlaces da rota mais curta, n\u00f3s como relays confi\u00e1veis)",
                String.format(Locale.US, "%.3f \u00b1 %.3f Mbit/s", mean[0], mean[2]), String.format(Locale.US, "%.3f Mbit/s \u00b1 5 %%", expected),
                "roteamento sequencial com Dijkstra pelo comprimento (djk), como na m\u00e9dia independente");
        rep.check(step.toLowerCase(Locale.ROOT) + ".bp", !(bp[0] > 0.01), LadderReport.WARN, "Carga baixa: bloqueio QKD desprez\u00edvel (a m\u00e9dia representa todos os pares)",
                QKDLadderMain.fmt(bp[0]), "\u2264 0,01", null);
        double half = mean[0] / 2;
        rep.check(step.toLowerCase(Locale.ROOT) + ".paper", "INFO", "Comparado ao artigo (" + paperFig + ", com o fator 1/2 de peneiramento)",
                String.format(Locale.US, "%.2f Mbit/s (SNetS \u00f7 2)", half), String.format(Locale.US, "\u2248 %.1f Mbit/s", paperMbps),
                String.format(Locale.US, "diferen\u00e7a %+.0f %%; o artigo escolhe a rota de maior SKR entre K caminhos e inclui XT/FWM do m\u00e9todo DC-WC", 100 * (half - paperMbps) / paperMbps));
        rep.note("Topologia da Fig. 6 de Moghaddam et al. (2021). QTND = 150 km: cada enlace \u00e9 um trecho QKD e os n\u00f3s s\u00e3o relays confi\u00e1veis."
                + " Demanda de chave 2 kbit/s (256 bits a cada 128 ms), par\u00e2metros da Tabela I, sem XT (h = 4,6e-11 /km \u00e9 desprez\u00edvel com pot\u00eancia adaptativa).");
        return rep;
    }

    // =================================================================== Phase Q (QKD-Analytical)

    LadderReport q1() {
        LadderReport rep = m.report("Q1", "QKD-Analytical: reconstru\u00e7\u00e3o exata do espa\u00e7o de estados da Fig. 2 (|C| = 7, |W| = 4, f = 2/1/1)");
        QKDAnalyticalModel model = new QKDAnalyticalModel(4, new int[] {2});
        int succ = model.arrivals(model.empty(), 0).size();
        rep.check("q1.empty", succ == 1200, LadderReport.FAIL, "Sucessores do estado vazio (taxa \u03bb1/1200 na Fig. 2)", String.valueOf(succ), "1200", null);
        QKDAnalyticalCTMC ctmc = new QKDAnalyticalCTMC(model, QKDAnalyticalCTMC.Policy.RANDOM_FIT, 1_000_000);
        rep.check("q1.states", ctmc.stateCount() == 34193, LadderReport.FAIL, "N\u00famero de estados |T| com CS-RF", String.valueOf(ctmc.stateCount()), "34193", null);
        int t2 = model.arrivals(model.state(new String[] {"1102", "0000", "0000", "0000", "0000", "0000"}, 1), 0).size();
        rep.check("q1.t2", t2 == 162, LadderReport.FAIL, "Sucessores do estado t = 2 (taxa \u03bb1/162)", String.valueOf(t2), "162", "c1 = 1,1,\u03ba,2; QCh no slot 1");
        int t10 = model.arrivals(model.state(new String[] {"1100", "0020", "0000", "0000", "0000", "0000"}, 1), 0).size();
        rep.check("q1.t10", t10 == 192, LadderReport.FAIL, "Sucessores do estado t = 10 (taxa \u03bb1/192)", String.valueOf(t10), "192", "c1 = 1,1,\u03c3,0; c2 = \u03c3,\u03c3,2,0");
        rep.note("Regras reconstru\u00eddas (Se\u00e7. III-A e Fig. 2 do artigo): o n\u00facleo central \u00e9 dedicado aos QChs; um slot de dado ou de canal cl\u00e1ssico (CCh)"
                + " bloqueia o mesmo slot nos n\u00facleos vizinhos (\u03c3); o slot livre entre um CCh e um dado do mesmo n\u00facleo \u00e9 banda de guarda (\u03ba);"
                + " um CCh n\u00e3o encosta em dado, e um CCh n\u00e3o coincide com CCh ou \u03ba de n\u00facleo vizinho. Com elas os quatro n\u00fameros do artigo s\u00e3o reproduzidos exatamente.");
        return rep;
    }

    LadderReport q2() {
        LadderReport rep = m.report("Q2", "QKD-Analytical: CTMC exata \u00d7 Monte Carlo com as mesmas regras (|W| = 4)");
        QKDAnalyticalModel model = new QKDAnalyticalModel(4, new int[] {2});
        boolean full = "completo".equals(m.prof.name);
        long arrivals = full ? 400000 : 100000;
        int reps = full ? 10 : 4;
        double[] loads = {0.1, 0.3, 0.5, 0.7, 1.0};
        rep.chart("bp", "Bloqueio \u00d7 carga (|C| = 7, |W| = 4, |D| = 1)", "Carga \u03bb/(|D|\u03bc)", "Probabilidade de bloqueio", true);
        for (QKDAnalyticalCTMC.Policy p : QKDAnalyticalCTMC.Policy.values()) {
            String name = p == QKDAnalyticalCTMC.Policy.RANDOM_FIT ? "CS-RF" : "CS-FF";
            QKDAnalyticalCTMC ctmc = new QKDAnalyticalCTMC(model, p, 1_000_000);
            QKDAnalyticalSimulation sim = new QKDAnalyticalSimulation(model, p);
            int bad = 0, erlang = 0;
            String ex = "";
            for (double L : loads) {
                double[] lam = {L}, mu = {1.0};
                ctmc.departures = QKDAnalyticalCTMC.Departures.PER_REQUEST;
                double exact = ctmc.solve(lam, mu, 1e-13, 50000).blockingProbability;
                ctmc.departures = QKDAnalyticalCTMC.Departures.LITERAL;
                double literal = ctmc.solve(lam, mu, 1e-13, 50000).blockingProbability;
                double[] mc = sim.replicate(lam, mu, arrivals, arrivals / 10, reps, 20260 + Math.round(L * 100));
                rep.point("bp", "CTMC " + name, L, exact, Double.NaN, Double.NaN);
                rep.point("bp", "CTMC " + name + " (eq. 3 literal)", L, literal, Double.NaN, Double.NaN);
                rep.point("bp", "Monte Carlo " + name, L, mc[0], mc[1], reps);
                if (Math.abs(exact - mc[0]) > mc[1] + 0.05 * exact) { bad++; if (ex.isEmpty()) ex = String.format(Locale.US, "carga %.1f: %.3e \u00d7 %.3e", L, exact, mc[0]); }
                if (literal < LadderIO.erlangB(4, L)) erlang++;
            }
            rep.check("q2." + name, bad == 0, LadderReport.WARN, name + ": CTMC (sa\u00eddas com taxa n\u00b7\u03bc) = Monte Carlo (IC95% + 5 %)",
                    bad + " ponto(s) fora", "0", ex.isEmpty() ? null : ex);
            rep.check("q2.lit." + name, "INFO", name + ": a eq. (3) lida ao p\u00e9 da letra (taxa \u03bc para cada estado sucessor de sa\u00edda) fica abaixo do Erlang-B com 4 servidores",
                    erlang + " de " + loads.length + " cargas", "\u2014", "com r\u00f3tulos, o n\u00famero de sucessores de sa\u00edda cresce com as combina\u00e7\u00f5es, n\u00e3o com as requisi\u00e7\u00f5es; por isso o modelo usa n_d\u00b7\u03bc_d repartido entre eles");
        }
        return rep;
    }

    /** Sim-QKD points of Fig. 3(a) of the paper, read from the plot ({load, CS-RF, CS-FF}). */
    static final double[][] FIG3A = {
        {0.1, 7.3e-5, 4.8e-5}, {0.2, 3.2e-4, 2.2e-4}, {0.3, 1.3e-3, 8.1e-4}, {0.4, 3.2e-3, 1.9e-3}, {0.5, 4.8e-3, 2.7e-3},
        {0.6, 6.6e-3, 4.3e-3}, {0.7, 8.9e-3, 5.8e-3}, {0.8, 1.2e-2, 7.9e-3}, {0.9, 1.4e-2, 9.1e-3}, {1.0, 1.7e-2, 1.2e-2},
    };

    LadderReport q3() {
        LadderReport rep = m.report("Q3", "QKD-Analytical, Fig. 3(a): bloqueio \u00d7 carga com |W| = 6 (Monte Carlo com as regras reconstru\u00eddas)");
        QKDAnalyticalModel model = new QKDAnalyticalModel(6, new int[] {2});
        boolean full = "completo".equals(m.prof.name);
        long arrivals = full ? 400000 : 60000;
        int reps = full ? 10 : 3;
        rep.chart("bp", "Fig. 3(a): |C| = 7, |D| = 1, |W| = 6", "Carga \u03bb/(|D|\u03bc)", "Probabilidade de bloqueio", true);
        double[][] ours = new double[FIG3A.length][];
        int[] idx = full ? new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9} : new int[] {0, 2, 4, 6, 9};
        int far = 0;
        boolean ffBetter = true;
        String ex = "";
        for (int i : idx) {
            double L = FIG3A[i][0];
            double[] rf = new QKDAnalyticalSimulation(model, QKDAnalyticalCTMC.Policy.RANDOM_FIT).replicate(new double[] {L}, new double[] {1.0}, arrivals, arrivals / 10, reps, 7 + i);
            double[] ff = new QKDAnalyticalSimulation(model, QKDAnalyticalCTMC.Policy.FIRST_FIT).replicate(new double[] {L}, new double[] {1.0}, arrivals, arrivals / 10, reps, 7 + i);
            ours[i] = new double[] {rf[0], ff[0]};
            rep.point("bp", "SNetS-QA CS-RF", L, rf[0], rf[1], reps);
            rep.point("bp", "SNetS-QA CS-FF", L, ff[0], ff[1], reps);
            rep.point("bp", "Artigo Sim CS-RF", L, FIG3A[i][1], Double.NaN, Double.NaN);
            rep.point("bp", "Artigo Sim CS-FF", L, FIG3A[i][2], Double.NaN, Double.NaN);
            for (int k = 0; k < 2; k++) {
                double a = ours[i][k], b = FIG3A[i][k + 1];
                if (a > 0 && (a / b > 3 || b / a > 3)) { far++; if (ex.isEmpty()) ex = String.format(Locale.US, "carga %.1f %s: %.2e \u00d7 %.2e", L, k == 0 ? "RF" : "FF", a, b); }
            }
            if (L >= 0.3 && ff[0] > rf[0] + rf[1] + ff[1]) ffBetter = false;
        }
        rep.check("q3.paper", far == 0, LadderReport.WARN, "Bloqueio na mesma ordem de grandeza do artigo (fator \u2264 3, valores lidos do gr\u00e1fico)",
                far + " ponto(s) fora", "0", ex.isEmpty() ? null : ex);
        rep.check("q3.ff", ffBetter, LadderReport.WARN, "CS-FF bloqueia menos que CS-RF (conclus\u00e3o do artigo)", ffBetter ? "sim" : "n\u00e3o", "sim", null);
        rep.note("A CTMC exata com |W| = 6 tem estados demais para este perfil; o Q2 mostra que a CTMC (com sa\u00eddas n\u00b7\u03bc) e o Monte Carlo coincidem em |W| = 4."
                + " Diferen\u00e7as em carga baixa podem vir de detalhes n\u00e3o descritos no artigo (ordem do first-fit, sorteio do random-fit, guarda do CCh).");
        return rep;
    }

}
