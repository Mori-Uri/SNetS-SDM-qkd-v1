package qkd.ladder;

/**
 * Independent re-implementation of the decoy-state BB84 secret key rate used
 * by the article (Dibaj et al. 2024, eq. 1) with the GLLP single-photon
 * estimates, written from the formulas and not from the simulator code:
 *
 * <pre>
 *   eta  = eta_d * 10^(-alpha(lambda) L / 10),  alpha = 0.18 + 8e-6 (lambda - 1550)^2 dB/km
 *   Y0   = p_b,  e0 = 1/2
 *   Q_mu = 1 - (1 - Y0) e^(-eta mu)                     (overall gain)
 *   E_mu = (e0 Y0 + e_d (1 - e^(-eta mu))) / Q_mu       (QBER)
 *   Y1   = Y0 + eta,  Q1 = Y1 mu e^(-mu)                (single-photon yield/gain)
 *   e1   = (e0 Y0 + e_d eta) / Y1                       (single-photon error rate)
 *   SKR  = max(0, Q1 [1 - H(e1)] - f Q_mu H(E_mu)) / T_s
 * </pre>
 *
 * Parameters: mu = 0.7, T_s = 250 ps, f = 1.16, e_d = 0.015, eta_d = 0.3, p_dark = 1e-8.
 *
 * <p>Refer\u00eancias: Eq. (1) de [DIBAJ24].</p>
 */
final class IndependentBB84 {

    static final double MU = 0.7, TS = 250e-12, F = 1.16, ED = 0.015, ETA_D = 0.3, PDARK = 1e-8;

    private IndependentBB84() {
    }

    static double alpha(double lambdaNm) {
        double d = lambdaNm - 1550.0;
        return 0.18 + 8e-6 * d * d;
    }

    /** {eta, Q_mu, E_mu, Q1, e1, SKR in bit/s}. */
    static double[] evaluate(double lambdaNm, double lengthKm, double pb) {
        double eta = ETA_D * Math.pow(10.0, -alpha(lambdaNm) * lengthKm / 10.0);
        double y0 = pb, e0 = 0.5;
        double qmu = 1.0 - (1.0 - y0) * Math.exp(-eta * MU);
        double emu = (e0 * y0 + ED * (1.0 - Math.exp(-eta * MU))) / qmu;
        double y1 = y0 + eta;
        double q1 = y1 * MU * Math.exp(-MU);
        double e1 = (e0 * y0 + ED * eta) / y1;
        double skr = Math.max(0.0, q1 * (1.0 - h(e1)) - F * qmu * h(emu)) / TS;
        return new double[] {eta, qmu, emu, q1, e1, skr};
    }

    static double h(double x) {
        if (x <= 0 || x >= 1) return 0;
        return -x * Math.log(x) / Math.log(2) - (1 - x) * Math.log(1 - x) / Math.log(2);
    }
}
