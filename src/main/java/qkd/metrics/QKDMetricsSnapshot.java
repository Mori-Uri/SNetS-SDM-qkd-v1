package qkd.metrics;

import java.io.Serializable;

/**
 * End-of-replication QKD statistics used by the general experiment CSV.
 *
 * <p>Refer\u00eancias: [JAIN91] R. Jain, "The Art of Computer Systems Performance Analysis", Wiley, 1991 (lei de Little, m\u00e9dias por chegada / PASTA, intervalos de confian\u00e7a); [DIBAJ24] (Q-TRx, n\u00f3s confi\u00e1veis).</p>
 */
public final class QKDMetricsSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;
    public int loadPoint;
    public int replication;
    public long totalRequestAttempts, protectedAttempts, accepted, rejected, released;
    public long existingSessionImpactChecks, classicalRejectionsByQkdImpact, activeRefreshes;
    public long precommitAdmissionChecks, precommitAdmissionRejections;
    public long activatedQchs, deactivatedQchs, tdmReuses, capacityFailures;
    public long peakActiveQchs, peakQTrxPairs;
    public double totalDemandMbps;
    public double meanAdmissionRawSkrMbps;
    public double minimumAdmissionRawSkrMbps;
    public double meanAdmissionQber;
    public double maximumAdmissionQber;
    public double maximumAdmissionPxtW;
    public double acceptanceRatio = Double.NaN;
    public double blockingProbability = Double.NaN;
    /** Arrival-averaged (PASTA) occupancy after warm-up. */
    public double meanActiveQchs, meanActiveQTrxPairs, meanActiveSessions;
    public double qtndKm;
    public double warmupArrivals;
    /** Part 23: QKD blocking by cause (counts), alternative routes, admitted-session shape. */
    public long blockedClassical, blockedSpectrum, blockedSkr, blockedPich, blockedOther;
    public long alternativeRouteAccepted;
    public double meanQTrxPerSession = Double.NaN, meanHopsPerSession = Double.NaN, meanRouteKmPerSession = Double.NaN;
    public long routeCandidateBandsEvaluated;
    public int plannedTrustedNodes, plannedQkdSpans;
    /** "src|dst" -> {QKD-protected attempts, QKD-protected blocked}. */
    /** Part 26: QTx/QRx and shared PICh statistics. */
    public long blockedQtrx, qEquipmentFailures;
    public double meanOpenPichs = Double.NaN;
    public long peakOpenPichs = -1, pichOpened;
    /** node -> {mean QTx, mean QRx, peak QTx, peak QRx, QTx limit, QRx limit, network node 1/0}. */
    public final java.util.Map<String, double[]> qEquipment = new java.util.TreeMap<String, double[]>();
    public final java.util.Map<String, long[]> perPair = new java.util.TreeMap<String, long[]>();
}
