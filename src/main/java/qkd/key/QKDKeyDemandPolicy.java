package qkd.key;

import java.io.Serializable;
import java.util.Arrays;

import util.RandGenerator;

/**
 * Selects the key-consumption requirement for QKD-protected requests.
 *
 * <p>Part 11 uses a uniform choice among the configured key-update intervals.
 * The reference scenario uses a 256-bit key and intervals of 64, 128 and
 * 256 microseconds, corresponding respectively to 4, 2 and 1 Mbit/s.</p>
 *
 * <p>The policy owns a random stream that is independent from the SNetS
 * arrival/holding-time generator. Therefore drawing a key-demand class does
 * not perturb the classical traffic timing sequence.</p>
 *
 * <p>Refer\u00eancias: [MOGH21] (Sec. VI-B), [DIBAJ24] (demanda = tamanho da chave / intervalo de renova\u00e7\u00e3o).</p>
 */
public final class QKDKeyDemandPolicy implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int REFERENCE_KEY_SIZE_BITS = 256;
    public static final double[] REFERENCE_UPDATE_INTERVALS_US =
            new double[] {64.0, 128.0, 256.0};
    public static final long REFERENCE_RANDOM_SEED = 2024L;

    private final int keySizeBits;
    private final double[] updateIntervalsMicroseconds;
    private final RandGenerator random;

    public QKDKeyDemandPolicy(int keySizeBits,
            double[] updateIntervalsMicroseconds,
            long randomSeed) {
        if (keySizeBits <= 0) {
            throw new IllegalArgumentException("keySizeBits must be > 0");
        }
        validateIntervals(updateIntervalsMicroseconds);

        this.keySizeBits = keySizeBits;
        this.updateIntervalsMicroseconds = Arrays.copyOf(
                updateIntervalsMicroseconds,
                updateIntervalsMicroseconds.length);
        this.random = new RandGenerator(randomSeed);
    }

    public static QKDKeyDemandPolicy referenceDefault() {
        return new QKDKeyDemandPolicy(
                REFERENCE_KEY_SIZE_BITS,
                REFERENCE_UPDATE_INTERVALS_US,
                REFERENCE_RANDOM_SEED);
    }

    public QKDKeyDemand draw() {
        final int index = random.randInt(
                0, updateIntervalsMicroseconds.length - 1);
        return new QKDKeyDemand(
                keySizeBits,
                updateIntervalsMicroseconds[index]);
    }

    public int getKeySizeBits() {
        return keySizeBits;
    }

    public double[] getUpdateIntervalsMicroseconds() {
        return Arrays.copyOf(
                updateIntervalsMicroseconds,
                updateIntervalsMicroseconds.length);
    }

    private static void validateIntervals(double[] intervals) {
        if (intervals == null || intervals.length == 0) {
            throw new IllegalArgumentException(
                    "updateIntervalsMicroseconds cannot be null/empty");
        }
        for (double interval : intervals) {
            if (!Double.isFinite(interval) || interval <= 0.0) {
                throw new IllegalArgumentException(
                        "every key-update interval must be finite and > 0");
            }
        }
    }
}
