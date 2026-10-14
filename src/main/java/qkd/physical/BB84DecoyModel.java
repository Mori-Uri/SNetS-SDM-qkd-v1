package qkd.physical;

/**
 * Decoy-state BB84 lower-bound model used in the supplied multi-fiber
 * replication.
 *
 * <p>This class is deliberately independent from SNetS routing and resource
 * allocation. It receives the physical conditions of one QKD span and returns
 * the corresponding QBER/SKR quantities. This separation lets us verify the
 * equations before coupling them to the dynamic SDM-EON state.</p>
 *
 * <p>Refer\u00eancias: Eq. (1) de [DIBAJ24] M. R. Dibaj et al., "Cost-Optimized Quantum Communication Networks: The Crucial Role of Trusted Node Placement in Multi-Band and Multi-Fiber Realms", IWCIT 2024 (artigo-base) (limite inferior do BB84 com estados decoy); Eqs. (9)-(18) de [MOGH21] E. E. Moghaddam, H. Beyranvand, J. A. Salehi, "Resource Allocation in SDM-EONs Secured With QKD", IEEE JSAC 39(9), 2021; verifica\u00e7\u00e3o num\u00e9rica na Tabela 5 de [MORI26] U. P. Mori, qualifica\u00e7\u00e3o "Planejamento de N\u00f3s Confi\u00e1veis para QKD em SDM-EONs: An\u00e1lise da Coexist\u00eancia Qu\u00e2ntico-Cl\u00e1ssica", UFPI, 2026.</p>
 */
public final class BB84DecoyModel {

    private final QKDPhysicalParameters parameters;

    public BB84DecoyModel() {
        this(new QKDPhysicalParameters());
    }

    public BB84DecoyModel(QKDPhysicalParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("parameters cannot be null");
        }
        this.parameters = parameters;
    }

    public QKDPhysicalParameters getParameters() {
        return parameters;
    }

    /**
     * Evaluates the reference multi-fiber case, where the background is only
     * the detector dark-count probability.
     */
    public QKDPhysicalResult evaluateMultiFiber(double wavelengthNm, double lengthKm) {
        return evaluate(wavelengthNm, lengthKm, parameters.getDarkCountProbability());
    }

    /**
     * Evaluates one QKD span using an already-combined background probability.
     *
     * @param wavelengthNm quantum-channel wavelength in nm
     * @param lengthKm QKD span length in km
     * @param backgroundProbability total probability of at least one background
     *        event in a detection window (pb)
     *
     * <p>Refer\u00eancias: Q_mu = 1-(1-p_b)e^{-eta mu}, E_mu, Q_1 e e_1 das Eqs. (12)-(18) de [MOGH21] e eq. (1) de [DIBAJ24]; eta = eta_d 10^{-alpha L/10}; SKR = [Q_1(1-H(e_1)) - f Q_mu H(E_mu)]/T_s (sem o fator 1/2 de peneiramento, como em [DIBAJ24]).</p>
     */
    public QKDPhysicalResult evaluate(
            double wavelengthNm,
            double lengthKm,
            double backgroundProbability) {

        if (!Double.isFinite(wavelengthNm) || wavelengthNm <= 0.0) {
            throw new IllegalArgumentException("wavelengthNm must be finite and > 0");
        }
        if (!Double.isFinite(lengthKm) || lengthKm < 0.0) {
            throw new IllegalArgumentException("lengthKm must be finite and >= 0");
        }
        requireProbability(backgroundProbability, "backgroundProbability");

        final double alpha = attenuationDbPerKm(wavelengthNm);
        final double eta = parameters.getDetectorEfficiency()
                * Math.pow(10.0, -alpha * lengthKm / 10.0);
        final double mu = parameters.getMu();
        final double pb = backgroundProbability;
        final double ed = parameters.getPhaseErrorProbability();

        final double expSignal = Math.exp(-eta * mu);
        final double qMu = 1.0 - (1.0 - pb) * expSignal;
        if (qMu <= 0.0) {
            return new QKDPhysicalResult(alpha, eta, pb, 0.0, 0.0, 0.0, 0.0,
                    0.0, 0.0, 0.0);
        }

        final double eMu = (pb / 2.0 + ed * (1.0 - expSignal)) / qMu;
        final double q1 = mu * Math.exp(-mu) * (pb + eta);
        final double e1 = (2.0 * eta * ed + pb) / (2.0 * (pb + eta));

        // Probabilities can reach exactly 0 or 1 only in limiting cases.
        final double hEMu = binaryEntropy(eMu);
        final double hE1 = binaryEntropy(e1);

        final double rawSkr = (
                q1 * (1.0 - hE1)
                - parameters.getErrorCorrectionInefficiency() * qMu * hEMu
        ) / parameters.getPulseIntervalS();

        final double skrBps = Math.max(0.0, rawSkr);

        return new QKDPhysicalResult(
                alpha,
                eta,
                pb,
                qMu,
                eMu,
                q1,
                e1,
                hEMu,
                hE1,
                skrBps
        );
    }

    /**
     * Wavelength-dependent attenuation fit used by the Python replication.
     */
    public double attenuationDbPerKm(double wavelengthNm) {
        return parameters.attenuationDbPerKm(wavelengthNm);
    }

    /**
     * Shannon binary entropy H2(x), with robust endpoint handling.
     */
    public static double binaryEntropy(double x) {
        final double eps = 1e-15;
        if (!Double.isFinite(x) || x < -eps || x > 1.0 + eps) {
            throw new IllegalArgumentException("binary entropy input must be in [0, 1]: " + x);
        }
        if (x <= eps || x >= 1.0 - eps) {
            return 0.0;
        }
        return -x * log2(x) - (1.0 - x) * log2(1.0 - x);
    }

    private static double log2(double x) {
        return Math.log(x) / Math.log(2.0);
    }

    private static void requireProbability(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be in [0, 1]");
        }
    }
}
