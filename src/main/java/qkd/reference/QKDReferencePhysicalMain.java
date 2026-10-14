package qkd.reference;

import qkd.physical.QKDPhysicalResult;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Locale;

import qkd.physical.BB84DecoyModel;

/**
 * Exports deterministic Java/SNetS-QKD physical sweeps used for reference plots.
 *
 * <p>Refer\u00eancias: [DIBAJ24].</p>
 */
public final class QKDReferencePhysicalMain {
    private QKDReferencePhysicalMain() {}

    public static void main(String[] args) throws Exception {
        File out=args.length>0?new File(args[0]):new File("reference_physical_results");
        if(!out.exists()&&!out.mkdirs())throw new IllegalStateException("Cannot create "+out);
        BB84DecoyModel model=new BB84DecoyModel();

        File fig1=new File(out,"snets_fig1b_skr.csv");
        BufferedWriter w1=new BufferedWriter(new FileWriter(fig1));
        try {
            w1.write("wavelength_nm,length_km,skr_mbps\n");
            int[] lengths={1,5,10,15,20,25,30,35,40};
            for(int length:lengths)for(int wavelength=1520;wavelength<=1620;wavelength+=2)
                w1.write(String.format(Locale.US,"%d,%d,%.12f%n",wavelength,length,
                        model.evaluateMultiFiber(wavelength,length).getSecretKeyRateMbps()));
        } finally {w1.close();}

        File fig5=new File(out,"snets_fig5b_skr_per_qtrx.csv");
        BufferedWriter w5=new BufferedWriter(new FileWriter(fig5));
        double best=-1.0;int bestW=0,bestQ=0;
        try {
            w5.write("wavelength_nm,qtnd_km,total_link_km,skr_mbps,skr_per_qtrx_mbps\n");
            int[] qtnds={1,5,10,15,20,25,30,35,40};
            for(int wavelength=1520;wavelength<=1620;wavelength+=10) {
                for(int qtnd:qtnds) {
                    double skr=model.evaluateMultiFiber(wavelength,qtnd).getSecretKeyRateMbps();
                    double ratio=skr*qtnd/40.0;
                    w5.write(String.format(Locale.US,"%d,%d,40,%.12f,%.12f%n",wavelength,qtnd,skr,ratio));
                    if(ratio>best){best=ratio;bestW=wavelength;bestQ=qtnd;}
                }
            }
        } finally {w5.close();}

        File report=new File(out,"physical_reference_report.txt");
        BufferedWriter wr=new BufferedWriter(new FileWriter(report));
        try {
            double skr25=model.evaluateMultiFiber(1550.0,25.0).getSecretKeyRateMbps();
            wr.write(String.format(Locale.US,"SKR(1550 nm,25 km)=%.15f Mbit/s%n",skr25));
            wr.write(String.format(Locale.US,"best Fig5 grid: wavelength=%d nm, QTND=%d km, SKR/Q-TRx=%.12f Mbit/s%n",bestW,bestQ,best));
        } finally {wr.close();}

        // Table 5 of the qualification: intermediate values for 1550 nm / 25 km.
        QKDPhysicalResult r25 = model.evaluateMultiFiber(1550.0, 25.0);
        File table5 = new File(out, "table5_intermediate_values.csv");
        BufferedWriter t5 = new BufferedWriter(new FileWriter(table5));
        int failures = 0;
        try {
            t5.write("quantity,snets_value,qualification_value,abs_difference,status\n");
            failures += row(t5, "eta", r25.getTransmittanceWithDetector(), 0.106444, 5e-7);
            failures += row(t5, "pb", r25.getBackgroundProbability(), 1e-8, 1e-15);
            failures += row(t5, "Q_mu", r25.getQMu(), 0.071803, 5e-7);
            failures += row(t5, "Q_1", r25.getQ1(), 0.037001, 5e-7);
            failures += row(t5, "E_mu", r25.getEMu(), 0.015000, 5e-7);
            failures += row(t5, "e_1", r25.getE1(), 0.015000, 5e-7);
            failures += row(t5, "H(E_mu)", r25.getEntropyEMu(), 0.112361, 5e-7);
            failures += row(t5, "H(e_1)", r25.getEntropyE1(), 0.112361, 5e-7);
            failures += row(t5, "SKR_Mbps", r25.getSecretKeyRateMbps(), 93.939373, 5e-6);
            failures += row(t5, "SKR_per_QTRx_max_Mbps", best, 58.712108, 5e-6);
            failures += row(t5, "optimum_wavelength_nm", bestW, 1550, 0);
            failures += row(t5, "optimum_qtnd_km", bestQ, 25, 0);
        } finally {t5.close();}

        // Mehdizadeh et al. (2024) scenarios 1 and 4 (20 and 70 km, separate fibers): SKR x mu.
        File muFile = new File(out, "mu_sweep_skr.csv");
        BufferedWriter wm = new BufferedWriter(new FileWriter(muFile));
        double skr20 = 0, skr70 = 0, muBest20 = 0, best20 = -1;
        try {
            wm.write("mu,skr_20km_bps,skr_70km_bps\n");
            for (int i = 1; i <= 100; i++) {
                double mu = i / 100.0;
                BB84DecoyModel m = new BB84DecoyModel(withMu(model.getParameters(), mu));
                double a = m.evaluateMultiFiber(1550.0, 20.0).getSecretKeyRateBps();
                double b = m.evaluateMultiFiber(1550.0, 70.0).getSecretKeyRateBps();
                wm.write(String.format(Locale.US, "%.2f,%.6f,%.6f%n", mu, a, b));
                if (i == 70) { skr20 = a / 1e6; skr70 = b / 1e6; }
                if (a > best20) { best20 = a; muBest20 = mu; }
            }
        } finally {wm.close();}
        BufferedWriter t5b = new BufferedWriter(new FileWriter(table5, true));
        try {
            failures += row(t5b, "SKR_20km_mu0.7_Mbps", skr20, 115.96, 0.005);
            failures += row(t5b, "SKR_70km_mu0.7_Mbps", skr70, 14.37, 0.005);
        } finally {t5b.close();}

        System.out.println("[OK] physical reference CSVs exported");
        System.out.println("Table 5 / reference points: " + (failures == 0 ? "ALL MATCH" : failures + " MISMATCH(ES)")
                + " -> " + table5.getAbsolutePath());
        System.out.println("mu sweep: SKR(20 km, mu=0.7)=" + skr20 + " Mbit/s, SKR(70 km, mu=0.7)=" + skr70
                + " Mbit/s, best mu at 20 km=" + muBest20);
        System.out.println("SKR(1550 nm,25 km)="+model.evaluateMultiFiber(1550.0,25.0).getSecretKeyRateMbps()+" Mbit/s");
        System.out.println("Fig5 grid maximum: wavelength="+bestW+" nm, QTND="+bestQ+" km, value="+best+" Mbit/s");
    }

    private static int row(BufferedWriter w, String name, double value, double expected, double tol) throws java.io.IOException {
        double diff = Math.abs(value - expected);
        boolean ok = diff <= tol;
        w.write(String.format(Locale.US, "%s,%.12g,%.12g,%.3e,%s%n", name, value, expected, diff, ok ? "OK" : "MISMATCH"));
        return ok ? 0 : 1;
    }

    private static qkd.physical.QKDPhysicalParameters withMu(qkd.physical.QKDPhysicalParameters p, double mu) {
        return new qkd.physical.QKDPhysicalParameters(mu, p.getPulseIntervalS(), p.getErrorCorrectionInefficiency(),
                p.getPhaseErrorProbability(), p.getDetectorEfficiency(), p.getDarkCountProbability(),
                p.getQchBandwidthHz(), p.getQchPowerDbm(), p.getAttenuationFloorDbPerKm(),
                p.getAttenuationQuadratic(), p.getAttenuationCenterNm(), p.getPlanckConstantJs(),
                p.getSpeedOfLightMPerS(), p.getDetectorGateWindowS(), p.getReceiverOpticalEfficiency());
    }
}
