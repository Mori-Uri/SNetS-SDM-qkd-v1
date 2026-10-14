package qkd.network;

import java.util.HashMap;
import java.util.List;

import network.Mesh;
import network.Pair;
import network.RequestGenerator;
import qkd.key.QKDKeyDemandPolicy;
import qkd.physical.QKDCrosstalkReceiverTransferMode;
import qkd.physical.QKDPhysicalParameters;
import qkd.traffic.QKDRequestGenerator;
import simulationControl.Util;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.OthersConfig;
import simulationControl.parsers.PhysicalLayerConfig;
import simulationControl.parsers.TrafficConfig;
import util.RandGenerator;

/**
 * Mesh variant used only by QKD-enabled simulations.
 *
 * <p>The classical topology construction is delegated entirely to
 * {@link Mesh}. After that construction finishes, this subclass replaces only
 * the runtime traffic-generator objects by {@link QKDRequestGenerator}
 * instances. The original traffic JSON and the original SNetS
 * {@code RequestGenerator} remain unchanged.</p>
 *
 * <p>Part 9 also keeps the precomputed logical QKD topology in the runtime
 * mesh. This allows the QKD control plane to reuse the same QTND segmentation
 * during every request without rebuilding or mutating the classical graph.</p>
 *
 * <p>Refer\u00eancias: [SNETS17] A. Fontinele et al., "An Efficient IA-RMLSA Algorithm for Transparent Elastic Optical Networks", Comput. Netw. 118, 2017 (SNetS) (malha do SNetS); [MORI26] (extens\u00e3o QKD).</p>
 */
public class QKDMesh extends Mesh {

    private static final long serialVersionUID = 1L;

    private final double protectedRequestProbability;
    private final QKDKeyDemandPolicy keyDemandPolicy;
    private transient simulationControl.parsers.TrafficConfig trafficConfigForQkd;
    private final QKDNetworkParameters qkdNetworkParameters;
    private final QKDTopologyPlan qkdTopologyPlan;
    private final QKDPhysicalParameters qkdPhysicalParameters;
    private final Long trafficRandomSeed;
    private final long protectionRandomSeed;
    /** Arrivals of a replication discarded as warm-up in the QKD statistics. */
    private double qkdWarmupArrivals = 0.0;
    private final qkd.traffic.QKDArrivalBudget arrivalBudget = new qkd.traffic.QKDArrivalBudget();

    public QKDMesh(NetworkConfig networkConfig, TrafficConfig trafficConfig,
            PhysicalLayerConfig physicalLayerConfig, OthersConfig othersConfig,
            HashMap<String, HashMap<Double, Double>> modulationDistances,
            Util util, double protectedRequestProbability) {
        this(networkConfig, trafficConfig, physicalLayerConfig, othersConfig,
                modulationDistances, util, protectedRequestProbability,
                QKDKeyDemandPolicy.referenceDefault(), null, 20241010L);
    }

    public QKDMesh(NetworkConfig networkConfig, TrafficConfig trafficConfig,
            PhysicalLayerConfig physicalLayerConfig, OthersConfig othersConfig,
            HashMap<String, HashMap<Double, Double>> modulationDistances,
            Util util, double protectedRequestProbability,
            QKDKeyDemandPolicy keyDemandPolicy) {
        this(networkConfig, trafficConfig, physicalLayerConfig, othersConfig,
                modulationDistances, util, protectedRequestProbability,
                keyDemandPolicy, null, 20241010L);
    }

    public QKDMesh(NetworkConfig networkConfig, TrafficConfig trafficConfig,
            PhysicalLayerConfig physicalLayerConfig, OthersConfig othersConfig,
            HashMap<String, HashMap<Double, Double>> modulationDistances,
            Util util, double protectedRequestProbability,
            QKDKeyDemandPolicy keyDemandPolicy, Long trafficRandomSeed,
            long protectionRandomSeed) {

        super(networkConfig, trafficConfig, physicalLayerConfig, othersConfig,
                modulationDistances, util, trafficRandomSeed);

        validateProbability(protectedRequestProbability);
        if (keyDemandPolicy == null) {
            throw new IllegalArgumentException("keyDemandPolicy cannot be null");
        }

        this.protectedRequestProbability = protectedRequestProbability;
        this.keyDemandPolicy = keyDemandPolicy;
        this.trafficConfigForQkd = trafficConfig;
        this.trafficRandomSeed = trafficRandomSeed;
        this.protectionRandomSeed = protectionRandomSeed;
        this.qkdNetworkParameters = QKDNetworkParameters.from(networkConfig);
        this.qkdTopologyPlan = new QKDTopologyPlanner().plan(networkConfig);
        this.qkdPhysicalParameters = buildQkdPhysicalParameters(physicalLayerConfig);

        if (!qkdNetworkParameters.isEnabled() || !qkdTopologyPlan.isEnabled()) {
            throw new IllegalArgumentException(
                    "QKDMesh requires network.qkd.enabled=true");
        }

        installQkdRequestGenerators();
    }

    private void installQkdRequestGenerators() {
        /*
         * The original Mesh uses one shared RandGenerator for all traffic
         * generators. We keep the same pattern here, but use a QKD-aware
         * generator class only when QKD is enabled.
         */
        RandGenerator timingRandom = trafficRandomSeed == null
                ? new RandGenerator() : new RandGenerator(trafficRandomSeed.longValue());
        RandGenerator protectionRandom = new RandGenerator(protectionRandomSeed);

        /* Part 23: per-generator QKD fields of the traffic file, in file order per pair. */
        java.util.Map<String, List<simulationControl.parsers.TrafficConfig.RequestGeneratorConfig>> byPair =
                new java.util.HashMap<String, List<simulationControl.parsers.TrafficConfig.RequestGeneratorConfig>>();
        if (trafficConfigForQkd != null && trafficConfigForQkd.getRequestGenerators() != null) {
            for (simulationControl.parsers.TrafficConfig.RequestGeneratorConfig rgc : trafficConfigForQkd.getRequestGenerators()) {
                String k = rgc.getSource() + "|" + rgc.getDestination();
                if (!byPair.containsKey(k)) byPair.put(k, new java.util.ArrayList<simulationControl.parsers.TrafficConfig.RequestGeneratorConfig>());
                byPair.get(k).add(rgc);
            }
        }

        for (Pair pair : getPairList()) {
            List<RequestGenerator> generators = pair.getRequestGenerators();
            List<simulationControl.parsers.TrafficConfig.RequestGeneratorConfig> cfgs =
                    byPair.get(pair.getSource().getName() + "|" + pair.getDestination().getName());

            for (int i = 0; i < generators.size(); i++) {
                RequestGenerator original = generators.get(i);
                simulationControl.parsers.TrafficConfig.RequestGeneratorConfig cfg =
                        cfgs != null && i < cfgs.size() ? cfgs.get(i) : null;
                double probability = cfg != null && cfg.getQkdProtectedProbability() != null
                        ? cfg.getQkdProtectedProbability().doubleValue() : protectedRequestProbability;
                validateProbability(probability);

                QKDRequestGenerator qkdGenerator = new QKDRequestGenerator(
                        pair,
                        original.getBandwidth(),
                        original.getHoldRate(),
                        original.getArrivedRate(),
                        original.getIncLoad(),
                        timingRandom,
                        protectionRandom,
                        probability,
                        keyDemandPolicy);

                if (cfg != null) qkdGenerator.setFixedKeyRateMbps(cfg.getKeyRateMbps());
                qkdGenerator.setArrivalBudget(arrivalBudget);
                generators.set(i, qkdGenerator);
            }
        }
    }

    public double getProtectedRequestProbability() {
        return protectedRequestProbability;
    }

    public QKDKeyDemandPolicy getQkdKeyDemandPolicy() {
        return keyDemandPolicy;
    }

    public QKDNetworkParameters getQkdNetworkParameters() {
        return qkdNetworkParameters;
    }

    public QKDTopologyPlan getQkdTopologyPlan() {
        return qkdTopologyPlan;
    }

    /**
     * QKD physical parameters after applying the explicit receiver assumptions
     * from the physical-layer scenario file.
     */
    public QKDPhysicalParameters getQkdPhysicalParameters() {
        return qkdPhysicalParameters;
    }

    private static QKDPhysicalParameters buildQkdPhysicalParameters(
            PhysicalLayerConfig physicalLayerConfig) {
        if (physicalLayerConfig == null) {
            throw new IllegalArgumentException(
                    "physicalLayerConfig cannot be null for QKDMesh");
        }

        // Part 26: the qkdPhysicalLayer case file wins; older cases keep the
        // qkdPhysical/qkdReceiver blocks inside physicalLayer.
        qkd.physical.QKDPhysicalLayerConfig cfg = physicalLayerConfig.getQkdPhysicalLayer();
        if (cfg == null) {
            cfg = qkd.physical.QKDPhysicalLayerConfig.fromLegacy(
                    physicalLayerConfig.getQkdPhysical(), physicalLayerConfig.getQkdReceiver());
        }
        return cfg.toParameters();
    }

    /** Limits the arrivals of this replication to exactly {@code limit} (negative = off). */
    public void setExactArrivalLimit(int limit) {
        arrivalBudget.setLimit(limit);
    }

    public qkd.traffic.QKDArrivalBudget getArrivalBudget() {
        return arrivalBudget;
    }

    public double getQkdWarmupArrivals() {
        return qkdWarmupArrivals;
    }

    public void setQkdWarmupArrivals(double qkdWarmupArrivals) {
        if (!Double.isFinite(qkdWarmupArrivals) || qkdWarmupArrivals < 0.0) {
            throw new IllegalArgumentException("qkdWarmupArrivals must be >= 0");
        }
        this.qkdWarmupArrivals = qkdWarmupArrivals;
    }

    public int getQkdRequestGeneratorCount() {
        int count = 0;
        for (Pair pair : getPairList()) {
            for (RequestGenerator generator : pair.getRequestGenerators()) {
                if (generator instanceof QKDRequestGenerator) {
                    count++;
                }
            }
        }
        return count;
    }

    private static void validateProbability(double probability) {
        if (!Double.isFinite(probability)
                || probability < 0.0
                || probability > 1.0) {
            throw new IllegalArgumentException(
                    "protectedRequestProbability must be in [0,1]");
        }
    }
}
