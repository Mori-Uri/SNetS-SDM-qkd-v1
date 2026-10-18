package simulationControl.parsers;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Simulation execution/algorithm configuration.
 *
 * <p>QKD-specific execution parameters are kept in the simulation
 * configuration so the legacy traffic matrix remains unchanged. Part 6 added
 * {@code qkdProtectedRequestProbability}; Part 11 adds the key-demand policy
 * ({@code qkdKeyDemand}) used only by QKD-protected requests.</p>
 */
public class SimulationConfig {

    private int requests;
    private int rmlsaType;
    private String routing;
    private String kRouting;
    private String spectrumAssignment;
    private String coreAndSpectrumAssignment;
    private String integratedRmlsa;
    private String modulationSelection;
    private String grooming;
    private String reallocation;
    private String powerAssignment;
    private int loadPoints;
    private int replications;
    private Metrics activeMetrics;
    private String regeneratorAssignment;
    private int networkType;
    private int threads;

    /** Fraction of generated requests that require QKD protection. */
    private double qkdProtectedRequestProbability;

    /** Optional master seed for arrival/holding traffic. Null preserves legacy behavior. */
    private Long trafficRandomSeed;

    /** Independent stream for CLASSICAL/QKD_PROTECTED classification. */
    private long qkdProtectionRandomSeed;

    /**
     * Key-consumption policy used only by QKD-protected requests.
     * The reference values reproduce the dissertation/article-base setup:
     * AES-256 updated every 64, 128 or 256 microseconds.
     */
    private QKDKeyDemandConfig qkdKeyDemand;
    /**
     * Fraction of the arrivals of each replication whose QKD statistics are
     * discarded as transient (warm-up). null = same rule used by the SNetS
     * classical metrics (10%). Use 0 for semi-static (no-departure) studies.
     */
    private Double qkdWarmupFraction;
    /**
     * QKD scenarios only: process exactly {@code requests} arrivals per
     * replication (see qkd.traffic.QKDArrivalBudget). Default false keeps the
     * original SNetS behaviour.
     */
    private Boolean qkdExactRequestCount;

    public SimulationConfig() {
        activeMetrics = new Metrics();
        threads = 1;
        qkdProtectedRequestProbability = 0.0;
        trafficRandomSeed = null;
        qkdProtectionRandomSeed = 20241010L;
        qkdKeyDemand = new QKDKeyDemandConfig();
    }

    public int getRequests() {
        return requests;
    }

    public void setRequests(int requests) {
        this.requests = requests;
    }

    public int getRmlsaType() {
        return rmlsaType;
    }

    public void setRmlsaType(int rmlsaType) {
        this.rmlsaType = rmlsaType;
    }

    public String getRouting() {
        return routing;
    }

    public void setRouting(String routing) {
        this.routing = routing;
    }

    public String getkRouting() {
        return kRouting;
    }

    public void setkRouting(String kRouting) {
        this.kRouting = kRouting;
    }

    public String getSpectrumAssignment() {
        return spectrumAssignment;
    }

    public String getCoreAndSpectrumAssignment() {
        return coreAndSpectrumAssignment;
    }

    public void setSpectrumAssignment(String spectrumAssignment) {
        this.spectrumAssignment = spectrumAssignment;
    }

    public void setCoreAndSpectrumAssignment(String coreAndSpectrumAssignment) {
        this.coreAndSpectrumAssignment = coreAndSpectrumAssignment;
    }

    public String getIntegratedRmlsa() {
        return integratedRmlsa;
    }

    public void setIntegratedRmlsa(String integratedRmlsa) {
        this.integratedRmlsa = integratedRmlsa;
    }

    public String getModulationSelection() {
        return modulationSelection;
    }

    public void setModulationSelection(String modulationSelection) {
        this.modulationSelection = modulationSelection;
    }

    public String getGrooming() {
        return grooming;
    }

    public void setGrooming(String grooming) {
        this.grooming = grooming;
    }

    public int getLoadPoints() {
        return loadPoints;
    }

    public void setLoadPoints(int loadPoints) {
        this.loadPoints = loadPoints;
    }

    public int getReplications() {
        return replications;
    }

    public void setReplications(int replications) {
        this.replications = replications;
    }

    public Metrics getActiveMetrics() {
        return activeMetrics;
    }

    public void setActiveMetrics(Metrics activeMetrics) {
        this.activeMetrics = activeMetrics;
    }

    public String getRegeneratorAssignment() {
        return regeneratorAssignment;
    }

    public void setRegeneratorAssignment(String regeneratorAssignment) {
        this.regeneratorAssignment = regeneratorAssignment;
    }

    public int getNetworkType() {
        return networkType;
    }

    public void setNetworkType(int networkType) {
        this.networkType = networkType;
    }

    /**
     * Number of worker threads. The launcher can override the value of the
     * simulation file for one execution with -Dsnets.threads=N (used to split
     * the processors among simulations running in parallel); the file itself
     * is not changed.
     */
    public int getThreads() {
        Integer override = Integer.getInteger("snets.threads");
        if (override != null && override.intValue() > 0) {
            return override.intValue();
        }
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public String getReallocation() {
        return reallocation;
    }

    public void setReallocation(String reallocation) {
        this.reallocation = reallocation;
    }

    public String getPowerAssignment() {
        return powerAssignment;
    }

    public void setPowerAssignment(String powerAssignment) {
        this.powerAssignment = powerAssignment;
    }

    public double getQkdProtectedRequestProbability() {
        validateQkdProbability(qkdProtectedRequestProbability);
        return qkdProtectedRequestProbability;
    }

    public void setQkdProtectedRequestProbability(double probability) {
        validateQkdProbability(probability);
        this.qkdProtectedRequestProbability = probability;
    }

    private static void validateQkdProbability(double probability) {
        if (!Double.isFinite(probability)
                || probability < 0.0
                || probability > 1.0) {
            throw new IllegalArgumentException(
                    "simulation.qkdProtectedRequestProbability must be in [0,1]");
        }
    }


    public Long getTrafficRandomSeed() {
        return trafficRandomSeed;
    }

    public void setTrafficRandomSeed(Long trafficRandomSeed) {
        this.trafficRandomSeed = trafficRandomSeed;
    }

    public long getQkdProtectionRandomSeed() {
        return qkdProtectionRandomSeed;
    }

    public void setQkdProtectionRandomSeed(long qkdProtectionRandomSeed) {
        this.qkdProtectionRandomSeed = qkdProtectionRandomSeed;
    }

    public QKDKeyDemandConfig getQkdKeyDemand() {
        if (qkdKeyDemand == null) {
            qkdKeyDemand = new QKDKeyDemandConfig();
        }
        qkdKeyDemand.validate();
        return qkdKeyDemand;
    }

    public void setQkdKeyDemand(QKDKeyDemandConfig qkdKeyDemand) {
        if (qkdKeyDemand == null) {
            throw new IllegalArgumentException("qkdKeyDemand cannot be null");
        }
        qkdKeyDemand.validate();
        this.qkdKeyDemand = qkdKeyDemand;
    }

    /**
     * QKD key-demand parameters kept in the simulation configuration rather
     * than in the legacy traffic matrix.
     */
    public static class QKDKeyDemandConfig implements Serializable {

        private static final long serialVersionUID = 1L;

        private int keySizeBits;
        private double[] updateIntervalsMicroseconds;
        private long randomSeed;

        public QKDKeyDemandConfig() {
            keySizeBits = 256;
            updateIntervalsMicroseconds = new double[] {64.0, 128.0, 256.0};
            randomSeed = 2024L;
        }

        public int getKeySizeBits() {
            validate();
            return keySizeBits;
        }

        public void setKeySizeBits(int keySizeBits) {
            this.keySizeBits = keySizeBits;
            validate();
        }

        public double[] getUpdateIntervalsMicroseconds() {
            validate();
            return Arrays.copyOf(
                    updateIntervalsMicroseconds,
                    updateIntervalsMicroseconds.length);
        }

        public void setUpdateIntervalsMicroseconds(
                double[] updateIntervalsMicroseconds) {
            this.updateIntervalsMicroseconds = updateIntervalsMicroseconds == null
                    ? null
                    : Arrays.copyOf(updateIntervalsMicroseconds,
                            updateIntervalsMicroseconds.length);
            validate();
        }

        public long getRandomSeed() {
            return randomSeed;
        }

        public void setRandomSeed(long randomSeed) {
            this.randomSeed = randomSeed;
        }

        public void validate() {
            if (keySizeBits <= 0) {
                throw new IllegalArgumentException(
                        "simulation.qkdKeyDemand.keySizeBits must be > 0");
            }
            if (updateIntervalsMicroseconds == null
                    || updateIntervalsMicroseconds.length == 0) {
                throw new IllegalArgumentException(
                        "simulation.qkdKeyDemand.updateIntervalsMicroseconds cannot be null/empty");
            }
            for (double interval : updateIntervalsMicroseconds) {
                if (!Double.isFinite(interval) || interval <= 0.0) {
                    throw new IllegalArgumentException(
                            "simulation.qkdKeyDemand update intervals must be finite and > 0");
                }
            }
        }
    }

    public static class Metrics implements Serializable {
        private static final long serialVersionUID = 1L;

        public boolean BlockingProbability;
        public boolean BitRateBlockingProbability;
        public boolean SpectrumUtilization;
        public boolean SpectrumSizeStatistics;
        public boolean ExternalFragmentation;
        public boolean RelativeFragmentation;
        public boolean TransmittersReceiversRegeneratorsUtilization;
        public boolean ModulationUtilization;
        public boolean ConsumedEnergy;
        public boolean GroomingStatistics;
        public boolean DataSetInformation;
        public boolean PhysicalLayerStatistics;
        /** QKD addon (Part 23): QKDMetrics.csv, written only when network.qkd.enabled = true. */
        public boolean QKDMetrics;
        /**
         * Part 27: SlotUtilization.csv, the occupancy of every slot of every core of every link
         * (needs SpectrumUtilization). Turn it off to save disk space in large topologies.
         */
        public boolean SlotUtilization;
        /**
         * Part 29: SlotBlocking.csv, attempts and blocks (by cause) of each slot of each core of each link
         * (needs BlockingProbability).
         */
        public boolean SlotBlocking;

        public Metrics() {
            QKDMetrics = true;
            SlotUtilization = true;
            SlotBlocking = true;
            BlockingProbability = true;
            BitRateBlockingProbability = true;
            SpectrumUtilization = true;
            SpectrumSizeStatistics = true;
            ExternalFragmentation = true;
            RelativeFragmentation = true;
            TransmittersReceiversRegeneratorsUtilization = true;
            ModulationUtilization = true;
            ConsumedEnergy = true;
            GroomingStatistics = true;
            DataSetInformation = true;
            PhysicalLayerStatistics = true;
        }
    }

    public double getQkdWarmupFraction() {
        double v = qkdWarmupFraction == null ? 0.1 : qkdWarmupFraction.doubleValue();
        if (!Double.isFinite(v) || v < 0.0 || v >= 1.0) {
            throw new IllegalArgumentException("simulation.qkdWarmupFraction must be in [0,1)");
        }
        return v;
    }

    public void setQkdWarmupFraction(Double qkdWarmupFraction) {
        this.qkdWarmupFraction = qkdWarmupFraction;
    }


    public boolean isQkdExactRequestCount() {
        return qkdExactRequestCount != null && qkdExactRequestCount.booleanValue();
    }

    public void setQkdExactRequestCount(Boolean qkdExactRequestCount) {
        this.qkdExactRequestCount = qkdExactRequestCount;
    }

}
