package qkd.analytical;

import java.util.Locale;

/** Quick command-line check: java -cp SNetS-SDM-QKD.jar qkd.analytical.QKDAnalyticalMain [W] [f_dc...] */
public final class QKDAnalyticalMain {
    private QKDAnalyticalMain() {
    }

    public static void main(String[] args) {
        int w = args.length > 0 ? Integer.parseInt(args[0]) : 4;
        int[] f = new int[Math.max(1, args.length - 1)];
        if (args.length > 1) for (int i = 1; i < args.length; i++) f[i - 1] = Integer.parseInt(args[i]);
        else f[0] = 2;
        QKDAnalyticalModel m = new QKDAnalyticalModel(w, f);
        long t0 = System.currentTimeMillis();
        System.out.println("successors of the empty state: " + m.arrivals(m.empty(), 0).size());
        for (QKDAnalyticalCTMC.Policy p : QKDAnalyticalCTMC.Policy.values()) {
            QKDAnalyticalCTMC ctmc = new QKDAnalyticalCTMC(m, p, 20_000_000);
            System.out.println(p + ": states = " + ctmc.stateCount() + " (" + (System.currentTimeMillis() - t0) + " ms)");
            for (double load : new double[] {0.1, 0.5, 1.0}) {
                double[] lam = new double[f.length], mu = new double[f.length];
                for (int i = 0; i < f.length; i++) { lam[i] = load / f.length; mu[i] = 1.0; }
                ctmc.departures = QKDAnalyticalCTMC.Departures.LITERAL;
                QKDAnalyticalCTMC.Result lit = ctmc.solve(lam, mu, 1e-13, 20000);
                ctmc.departures = QKDAnalyticalCTMC.Departures.PER_REQUEST;
                QKDAnalyticalCTMC.Result r = ctmc.solve(lam, mu, 1e-13, 20000);
                double[] mc = new QKDAnalyticalSimulation(m, p).replicate(lam, mu, 200000, 20000, 5, 1);
                System.out.println(String.format(Locale.US, "  load %.1f  CTMC per-request %.4e (it %d)  literal %.4e   MC %.4e +- %.1e", load,
                        r.blockingProbability, r.iterations, lit.blockingProbability, mc[0], mc[1]));
            }
        }
    }
}
