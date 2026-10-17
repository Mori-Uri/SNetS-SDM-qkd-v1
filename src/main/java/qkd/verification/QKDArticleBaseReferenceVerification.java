package qkd.verification;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import qkd.reference.ReferenceMultifiberExperiment;
import qkd.reference.ReferenceResultRow;

/**
 * Part 19: verifies the QKD addon against the already-verified Dibaj MF
 * reconstruction using identical routed traces and resource assumptions.
 */
public final class QKDArticleBaseReferenceVerification {
    private QKDArticleBaseReferenceVerification() { }

    public static void main(String[] args) throws Exception {
        File root = new File(args.length > 0 ? args[0]
                : (new File("verificacao/QKD_reference/Dibaj_MF").isDirectory() ? "verificacao/QKD_reference/Dibaj_MF"
                        : "simulations/QKD_reference/Dibaj_MF"));
        check(root.isDirectory(), "Reference scenario directory not found: " + root.getAbsolutePath());

        ReferenceMultifiberExperiment experiment =
                new ReferenceMultifiberExperiment(root);
        List<ReferenceResultRow> rows = experiment.run();
        ReferenceMultifiberExperiment.VerificationSummary verification =
                experiment.compareWithPython(
                        new File(new File(root, "reference_python"),
                                "replications_raw.csv"), rows);

        check(rows.size() == 300,
                "Expected 10 replications x 3 topologies x 10 QTND = 300 rows");
        check(verification.exactMatchingRows == 300,
                "SNetS-QKD MF/Q-TRx results differ from Python reconstruction: "
                        + verification);
        check(verification.maxAbsoluteQtrxDifference == 0,
                "Q-TRx must match the verified reconstruction exactly");

        Map<String, Integer> optimum = meanCurveOptimum(rows);
        check(Integer.valueOf(25).equals(optimum.get("small_core")),
                "Small Core mean optimum must be 25 km");
        check(Integer.valueOf(30).equals(optimum.get("butterfly")),
                "Butterfly mean optimum must be 30 km in the reconstructed trace");
        check(Integer.valueOf(25).equals(optimum.get("metro")),
                "Metro mean optimum must be 25 km");

        System.out.println("[OK] 300/300 topology/replication/QTND observations match the verified Python reconstruction exactly");
        System.out.println("[OK] maximum absolute Q-TRx difference = 0");
        System.out.println("[OK] mean-curve QTND optima: Small Core=25 km, Butterfly=30 km, Metro=25 km");
        System.out.println("[INFO] classical route recomputation is diagnostic only; fixed routed traces are used because the article does not fully specify routing tie-breaking/calibration details");
        System.out.println("       diagnostic path mismatches reported by Java recomputation are not used in the QKD/Q-TRx equivalence test");
        System.out.println();
        System.out.println("QKD Part 19 article-base reference verification: ALL CHECKS PASSED");
    }

    private static Map<String, Integer> meanCurveOptimum(
            List<ReferenceResultRow> rows) {
        Map<String, Map<Integer, double[]>> acc =
                new HashMap<String, Map<Integer, double[]>>();
        for (ReferenceResultRow row : rows) {
            Map<Integer, double[]> byQtnd = acc.get(row.topology);
            if (byQtnd == null) {
                byQtnd = new HashMap<Integer, double[]>();
                acc.put(row.topology, byQtnd);
            }
            double[] a = byQtnd.get(Integer.valueOf(row.qtndKm));
            if (a == null) {
                a = new double[2];
                byQtnd.put(Integer.valueOf(row.qtndKm), a);
            }
            a[0] += row.qtrxUsage;
            a[1] += 1.0;
        }
        Map<String, Integer> result = new HashMap<String, Integer>();
        for (Map.Entry<String, Map<Integer, double[]>> e : acc.entrySet()) {
            int bestQtnd = -1;
            double best = Double.POSITIVE_INFINITY;
            for (Map.Entry<Integer, double[]> q : e.getValue().entrySet()) {
                double mean = q.getValue()[0] / q.getValue()[1];
                if (mean < best - 1e-12
                        || (Math.abs(mean - best) <= 1e-12
                        && q.getKey().intValue() < bestQtnd)) {
                    best = mean;
                    bestQtnd = q.getKey().intValue();
                }
            }
            result.put(e.getKey(), Integer.valueOf(bestQtnd));
        }
        return result;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
