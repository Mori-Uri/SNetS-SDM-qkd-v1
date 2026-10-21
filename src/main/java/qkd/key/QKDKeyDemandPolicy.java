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

    // ------------------------------------------------------------------ Part 34: demand by bit rate

    /** Demand of each classical bit rate (bit/s), when the mode is "byBitRate"; null in the uniform mode. */
    private java.util.TreeMap<Double, QKDKeyDemand> byBitRate;

    /**
     * Switches to the "byBitRate" mode. The demands are the configured ones (key size / interval), sorted from the
     * smallest to the largest key rate; the distinct bit rates of the generators are sorted the same way and paired
     * by rank (equal counts: 1st with 1st, ...; more bit rates than demands: consecutive groups share a demand; fewer:
     * spread from the smallest to the largest demand). An explicit table (Gb/s -> Mbit/s) overrides the pairing for
     * the rates it lists.
     */
    public void configureByBitRate(java.util.Collection<Double> bitRatesBps, java.util.Map<String, Double> explicitGbpsToMbps) {
        java.util.TreeSet<Double> rates = new java.util.TreeSet<Double>();
        for (Double r : bitRatesBps) if (r != null && r > 0) rates.add(r);
        double[] keyRates = new double[updateIntervalsMicroseconds.length];
        for (int i = 0; i < keyRates.length; i++) keyRates[i] = keySizeBits / updateIntervalsMicroseconds[i];
        double[] sortedKey = keyRates.clone();
        Arrays.sort(sortedKey);
        // remove repeated key rates
        int nk = 0;
        for (int i = 0; i < sortedKey.length; i++) if (nk == 0 || sortedKey[i] != sortedKey[nk - 1]) sortedKey[nk++] = sortedKey[i];
        java.util.TreeMap<Double, QKDKeyDemand> m = new java.util.TreeMap<Double, QKDKeyDemand>();
        int n = rates.size(), j = 0;
        for (Double r : rates) {
            int k;
            if (nk == 1) k = 0;
            else if (n == 1) k = nk / 2;
            else if (n <= nk) k = (int) Math.round(j * (nk - 1) / (double) (n - 1));
            else k = (int) Math.floor(j * nk / (double) n);
            double mbps = sortedKey[Math.min(nk - 1, k)];
            if (explicitGbpsToMbps != null) {
                for (java.util.Map.Entry<String, Double> e : explicitGbpsToMbps.entrySet()) {
                    double gb = Double.parseDouble(e.getKey().trim().replace(',', '.'));
                    if (Math.abs(gb * 1e9 - r) <= 1e-6 * r) mbps = e.getValue().doubleValue();
                }
            }
            m.put(r, new QKDKeyDemand(keySizeBits, keySizeBits / mbps));
            j++;
        }
        this.byBitRate = m;
    }

    private boolean byBitRateRequested;
    private java.util.Map<String, Double> byBitRateExplicit;

    /** Asks for the "byBitRate" mode; the mesh calls {@link #configureByBitRate} once it knows the generators. */
    public void requestByBitRate(java.util.Map<String, Double> explicitGbpsToMbps) {
        this.byBitRateRequested = true;
        this.byBitRateExplicit = explicitGbpsToMbps;
    }

    public boolean isByBitRateRequested() {
        return byBitRateRequested;
    }

    public java.util.Map<String, Double> getByBitRateExplicit() {
        return byBitRateExplicit;
    }

    public boolean isByBitRate() {
        return byBitRate != null;
    }

    /** Demand of a request of this bit rate (mode "byBitRate"); the nearest configured rate if it is not listed. */
    public QKDKeyDemand forBitRate(double bitRateBps) {
        if (byBitRate == null || byBitRate.isEmpty()) throw new IllegalStateException("key demand by bit rate not configured");
        QKDKeyDemand d = byBitRate.get(bitRateBps);
        if (d != null) return d;
        Double lo = byBitRate.floorKey(bitRateBps), hi = byBitRate.ceilingKey(bitRateBps);
        if (lo == null) return byBitRate.get(hi);
        if (hi == null) return byBitRate.get(lo);
        return bitRateBps - lo <= hi - bitRateBps ? byBitRate.get(lo) : byBitRate.get(hi);
    }

    /** Copy of the bit rate (bit/s) -> demand table, or null in the uniform mode. */
    public java.util.Map<Double, QKDKeyDemand> getByBitRateTable() {
        return byBitRate == null ? null : new java.util.TreeMap<Double, QKDKeyDemand>(byBitRate);
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
