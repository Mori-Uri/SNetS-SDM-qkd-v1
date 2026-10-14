package qkd.key;

import java.io.Serializable;

/**
 * Immutable key-consumption requirement associated with one QKD-protected
 * connection.
 *
 * <p>The model follows the demand expression used in the dissertation:</p>
 *
 * <pre>
 * R_K = L_K / T_K
 * </pre>
 *
 * <p>where {@code L_K} is the cryptographic-key size in bits and
 * {@code T_K} is the key-update interval in seconds. The resulting
 * {@code R_K} is expressed in bit/s and represents the minimum secret-key
 * generation rate that must be available on every QKD span of the route.</p>
 *
 * <p>Refer\u00eancias: [MOGH21] E. E. Moghaddam, H. Beyranvand, J. A. Salehi, "Resource Allocation in SDM-EONs Secured With QKD", IEEE JSAC 39(9), 2021 (Sec. VI-B: chave de 256 bits renovada a cada tau); [HERON09] S. Heron, "Advanced Encryption Standard (AES)", Network Security, 2009.</p>
 */
public final class QKDKeyDemand implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int keySizeBits;
    private final double updateIntervalMicroseconds;
    private final double requiredKeyRateBps;

    public QKDKeyDemand(int keySizeBits,
            double updateIntervalMicroseconds) {
        if (keySizeBits <= 0) {
            throw new IllegalArgumentException("keySizeBits must be > 0");
        }
        if (!Double.isFinite(updateIntervalMicroseconds)
                || updateIntervalMicroseconds <= 0.0) {
            throw new IllegalArgumentException(
                    "updateIntervalMicroseconds must be finite and > 0");
        }

        this.keySizeBits = keySizeBits;
        this.updateIntervalMicroseconds = updateIntervalMicroseconds;
        this.requiredKeyRateBps = calculateRequiredKeyRateBps(
                keySizeBits, updateIntervalMicroseconds);
    }

    public static double calculateRequiredKeyRateBps(
            int keySizeBits,
            double updateIntervalMicroseconds) {
        if (keySizeBits <= 0) {
            throw new IllegalArgumentException("keySizeBits must be > 0");
        }
        if (!Double.isFinite(updateIntervalMicroseconds)
                || updateIntervalMicroseconds <= 0.0) {
            throw new IllegalArgumentException(
                    "updateIntervalMicroseconds must be finite and > 0");
        }

        final double intervalSeconds = updateIntervalMicroseconds * 1.0e-6;
        return keySizeBits / intervalSeconds;
    }

    public int getKeySizeBits() {
        return keySizeBits;
    }

    public double getUpdateIntervalMicroseconds() {
        return updateIntervalMicroseconds;
    }

    public double getRequiredKeyRateBps() {
        return requiredKeyRateBps;
    }

    public double getRequiredKeyRateMbps() {
        return requiredKeyRateBps / 1.0e6;
    }

    @Override
    public String toString() {
        return "QKDKeyDemand{" +
                "keySizeBits=" + keySizeBits +
                ", updateIntervalUs=" + updateIntervalMicroseconds +
                ", requiredKeyRateMbps=" + getRequiredKeyRateMbps() +
                '}';
    }
}
