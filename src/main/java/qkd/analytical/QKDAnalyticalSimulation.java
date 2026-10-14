package qkd.analytical;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * Monte Carlo counterpart of QKD-Analytical ("Sim-QKD-CS-RF/FF" of the paper, Sec. IV):
 * Poisson arrivals of rate lambda_d per class, exponential holding times of mean
 * 1/mu_d, the same allocation rules as {@link QKDAnalyticalModel}, and, unlike the
 * label-only CTMC, each request releases exactly its own DCh, CCh and QCh.
 *
 * <p>Refer\u00eancias: [AHMED26a] (Sec. IV, simula\u00e7\u00e3o de Monte Carlo); [JAIN91].</p>
 */
public final class QKDAnalyticalSimulation {

    private final QKDAnalyticalModel model;
    private final QKDAnalyticalCTMC.Policy policy;

    public QKDAnalyticalSimulation(QKDAnalyticalModel model, QKDAnalyticalCTMC.Policy policy) {
        this.model = model;
        this.policy = policy;
    }

    private static final class Departure implements Comparable<Departure> {
        final double time; final int cls; final int[] p;
        Departure(double time, int cls, int[] p) { this.time = time; this.cls = cls; this.p = p; }
        @Override public int compareTo(Departure o) { return Double.compare(time, o.time); }
    }

    /** Blocking probability of one replication (the first warmup arrivals are discarded). */
    public double run(double[] lambda, double[] mu, long arrivals, long warmup, long seed) {
        Random rnd = new Random(seed);
        double lsum = 0;
        for (double l : lambda) lsum += l;
        QKDAnalyticalModel.State st = model.empty();
        PriorityQueue<Departure> pq = new PriorityQueue<Departure>();
        double t = 0;
        long seen = 0, blocked = 0, counted = 0;
        while (seen < arrivals + warmup) {
            t += -Math.log(1.0 - rnd.nextDouble()) / lsum;
            while (!pq.isEmpty() && pq.peek().time <= t) {
                Departure d = pq.poll();
                st = model.remove(st, d.cls, d.p);
            }
            double u = rnd.nextDouble() * lsum;
            int c = 0;
            while (c < lambda.length - 1 && u >= lambda[c]) { u -= lambda[c]; c++; }
            seen++;
            List<int[]> ps = model.placements(st, c);
            boolean ok = !ps.isEmpty();
            if (seen > warmup) { counted++; if (!ok) blocked++; }
            if (!ok) continue;
            int[] p = policy == QKDAnalyticalCTMC.Policy.FIRST_FIT ? ps.get(0) : ps.get(rnd.nextInt(ps.size()));
            st = model.apply(st, c, p);
            pq.add(new Departure(t - Math.log(1.0 - rnd.nextDouble()) / mu[c], c, p));
        }
        return counted == 0 ? Double.NaN : (double) blocked / counted;
    }

    /** {mean, 95 % half-width} over independent replications. */
    public double[] replicate(double[] lambda, double[] mu, long arrivals, long warmup, int reps, long seed) {
        List<Double> v = new ArrayList<Double>();
        for (int r = 0; r < reps; r++) v.add(run(lambda, mu, arrivals, warmup, seed + 7919L * r));
        double m = 0;
        for (double x : v) m += x;
        m /= reps;
        double ss = 0;
        for (double x : v) ss += (x - m) * (x - m);
        double sd = reps > 1 ? Math.sqrt(ss / (reps - 1)) : 0;
        double[] tq = {0, 12.706, 4.303, 3.182, 2.776, 2.571, 2.447, 2.365, 2.306, 2.262, 2.228};
        double tt = reps - 1 < tq.length ? tq[Math.max(1, reps - 1)] : 1.96;
        return new double[] {m, reps > 1 ? tt * sd / Math.sqrt(reps) : 0};
    }
}
