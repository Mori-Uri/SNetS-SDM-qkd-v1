package qkd.verification;

import java.io.File;
import java.util.List;
import java.util.Map;

import qkd.reference.QKDNativeReferenceMain;
import qkd.reference.ReferenceExperimentConfig;

/**
 * Part 22: the article-base multi-fiber experiment (Dibaj et al. 2024, Fig. 7)
 * executed by the native SNetS engine (request generation, routing, classical
 * allocation, QTND planning, span QCh pools, TDM and Q-TRx accounting), for the
 * three topologies, 10 QTND values and 10 replications. The result is compared
 * with the verified Python reconstruction of the qualification: same optimum
 * QTND region, U-shaped curve, no blocking and small mean difference.
 *
 * <p>Usage: {@code java -cp <jar> qkd.verification.QKDNativeArticleBaseVerification
 * [Dibaj_MF folder] [work folder]}</p>
 */
public final class QKDNativeArticleBaseVerification {

    private static final double MAX_MAE_VS_PYTHON = 10.0;

    public static void main(String[] args) throws Exception {
        File root = new File(args.length > 0 ? args[0]
                : (new File("verificacao/QKD_reference/Dibaj_MF").isDirectory() ? "verificacao/QKD_reference/Dibaj_MF"
                        : "simulations/QKD_reference/Dibaj_MF")).getAbsoluteFile();
        File work = args.length > 1 ? new File(args[1]).getAbsoluteFile()
                : new File(System.getProperty("java.io.tmpdir"), "snets_native_article_verification");
        check(new File(root, "config.json").isFile(), "Dibaj_MF folder not found: " + root);
        ReferenceExperimentConfig config = ReferenceExperimentConfig.load(new File(root, "config.json"));
        List<String> topologies = config.getTopologies();
        int[] qtnds = config.getQtndValuesKm();
        QKDNativeReferenceMain.prepare(root, work, config, topologies, qtnds,
                config.getReplications().size(), Math.max(1, Math.min(10, Runtime.getRuntime().availableProcessors())));
        QKDNativeReferenceMain.run(work, topologies, qtnds);
        QKDNativeReferenceMain.analyze(root, work, topologies, qtnds);

        File summary = new File(QKDNativeReferenceMain.resultsDir(work), "snets_native_summary.csv");
        List<Map<String, String>> rows = QKDNativeReferenceMain.readCsv(summary);
        check(rows.size() == topologies.size(), "summary must have one row per topology");
        System.out.println();
        for (Map<String, String> r : rows) {
            String t = r.get("topology");
            int snets = Integer.parseInt(r.get("optimum_qtnd_snets"));
            int python = Integer.parseInt(r.get("optimum_qtnd_python"));
            double mae = Double.parseDouble(r.get("mae_vs_python"));
            check("true".equals(r.get("u_shape")), t + ": Q-TRx x QTND must be U-shaped");
            check(Math.abs(snets - python) <= 5, t + ": optimum QTND differs from the Python reconstruction by more than 5 km");
            check(mae <= MAX_MAE_VS_PYTHON, t + ": mean absolute difference to the Python reconstruction = " + mae);
            check(Double.parseDouble(r.get("qkd_blocking_max")) == 0.0, t + ": QKD blocking must be zero (reference had none)");
            check(Double.parseDouble(r.get("classical_blocking_max")) == 0.0, t + ": classical blocking must be zero");
            String[] within = r.get("replications_optimum_within_5km_of_25").split("/");
            check(within[0].equals(within[1]), t + ": every replication must have its optimum within 5 km of 25 km");
            System.out.println("[OK] " + t + ": native SNetS optimum=" + snets + " km (Python " + python
                    + " km, article " + r.get("optimum_qtnd_article") + " km), MAE vs Python=" + mae
                    + " Q-TRx, MAE vs article=" + r.get("mae_vs_article") + " Q-TRx, no blocking");
        }
        System.out.println("Results: " + QKDNativeReferenceMain.resultsDir(work).getAbsolutePath());
        System.out.println("\nQKD Part 22 native article-base verification: ALL CHECKS PASSED");
    }

    private static void check(boolean c, String m) {
        if (!c) throw new AssertionError(m);
    }
}
