package qkd.physical;

import java.io.Serializable;

/**
 * Result of one Decoy-BB84 physical-layer evaluation.
 *
 * <p>All gains/error terms are probabilities. SKR is exposed in both bit/s
 * and Mbit/s. E_mu is the QBER of the signal state in the model used by the
 * supplied Python replication.</p>
 */
public final class QKDPhysicalResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final double attenuationDbPerKm;
    private final double transmittanceWithDetector;
    private final double backgroundProbability;
    private final double qMu;
    private final double eMu;
    private final double q1;
    private final double e1;
    private final double entropyEMu;
    private final double entropyE1;
    private final double secretKeyRateBps;

    public QKDPhysicalResult(
            double attenuationDbPerKm,
            double transmittanceWithDetector,
            double backgroundProbability,
            double qMu,
            double eMu,
            double q1,
            double e1,
            double entropyEMu,
            double entropyE1,
            double secretKeyRateBps) {
        this.attenuationDbPerKm = attenuationDbPerKm;
        this.transmittanceWithDetector = transmittanceWithDetector;
        this.backgroundProbability = backgroundProbability;
        this.qMu = qMu;
        this.eMu = eMu;
        this.q1 = q1;
        this.e1 = e1;
        this.entropyEMu = entropyEMu;
        this.entropyE1 = entropyE1;
        this.secretKeyRateBps = secretKeyRateBps;
    }

    public double getAttenuationDbPerKm() { return attenuationDbPerKm; }
    public double getTransmittanceWithDetector() { return transmittanceWithDetector; }
    public double getBackgroundProbability() { return backgroundProbability; }
    public double getQMu() { return qMu; }
    public double getEMu() { return eMu; }
    public double getQber() { return eMu; }
    public double getQ1() { return q1; }
    public double getE1() { return e1; }
    public double getEntropyEMu() { return entropyEMu; }
    public double getEntropyE1() { return entropyE1; }
    public double getSecretKeyRateBps() { return secretKeyRateBps; }
    public double getSecretKeyRateMbps() { return secretKeyRateBps / 1e6; }

    @Override
    public String toString() {
        return "QKDPhysicalResult{" +
                "alphaDbPerKm=" + attenuationDbPerKm +
                ", eta=" + transmittanceWithDetector +
                ", pb=" + backgroundProbability +
                ", Q_mu=" + qMu +
                ", E_mu=" + eMu +
                ", Q_1=" + q1 +
                ", e_1=" + e1 +
                ", SKR_Mbps=" + getSecretKeyRateMbps() +
                '}';
    }
}
