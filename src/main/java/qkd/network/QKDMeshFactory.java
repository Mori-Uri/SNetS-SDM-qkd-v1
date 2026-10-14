package qkd.network;

import java.util.HashMap;

import network.Mesh;
import qkd.key.QKDKeyDemandPolicy;
import simulationControl.Util;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.OthersConfig;
import simulationControl.parsers.PhysicalLayerConfig;
import simulationControl.parsers.SimulationConfig;
import simulationControl.parsers.TrafficConfig;

/**
 * Centralized choice between the legacy SNetS Mesh and the QKD-aware Mesh.
 *
 * <p>Refer\u00eancias: [SNETS17], [MORI26].</p>
 */
public final class QKDMeshFactory {

    private QKDMeshFactory() {
    }

    public static Mesh create(NetworkConfig networkConfig,
            TrafficConfig trafficConfig,
            PhysicalLayerConfig physicalLayerConfig,
            OthersConfig othersConfig,
            HashMap<String, HashMap<Double, Double>> modulationDistances,
            Util util,
            SimulationConfig simulationConfig) {
        return create(networkConfig, trafficConfig, physicalLayerConfig,
                othersConfig, modulationDistances, util, simulationConfig, 0, 0);
    }

    /** Creates reproducible, independent streams per load point/replication. */
    public static Mesh create(NetworkConfig networkConfig,
            TrafficConfig trafficConfig,
            PhysicalLayerConfig physicalLayerConfig,
            OthersConfig othersConfig,
            HashMap<String, HashMap<Double, Double>> modulationDistances,
            Util util,
            SimulationConfig simulationConfig,
            int loadPoint, int replication) {

        long offset = ((long) loadPoint) * 100000L + (long) replication;
        Long trafficSeed = null;
        if (simulationConfig != null && simulationConfig.getTrafficRandomSeed() != null) {
            trafficSeed = Long.valueOf(simulationConfig.getTrafficRandomSeed().longValue() + offset);
        }

        if (isQkdEnabled(networkConfig)) {
            if (simulationConfig == null) {
                throw new IllegalArgumentException(
                        "simulationConfig cannot be null for a QKD simulation");
            }

            SimulationConfig.QKDKeyDemandConfig keyConfig =
                    simulationConfig.getQkdKeyDemand();

            QKDKeyDemandPolicy keyDemandPolicy = new QKDKeyDemandPolicy(
                    keyConfig.getKeySizeBits(),
                    keyConfig.getUpdateIntervalsMicroseconds(),
                    keyConfig.getRandomSeed() + offset);

            QKDMesh qkdMesh = new QKDMesh(
                    networkConfig,
                    trafficConfig,
                    physicalLayerConfig,
                    othersConfig,
                    modulationDistances,
                    util,
                    simulationConfig.getQkdProtectedRequestProbability(),
                    keyDemandPolicy,
                    trafficSeed,
                    simulationConfig.getQkdProtectionRandomSeed() + offset);
            qkdMesh.setQkdWarmupArrivals(simulationConfig.getQkdWarmupFraction()
                    * simulationConfig.getRequests());
            if (simulationConfig.isQkdExactRequestCount()) {
                qkdMesh.setExactArrivalLimit(simulationConfig.getRequests());
            }
            return qkdMesh;
        }

        return new Mesh(
                networkConfig,
                trafficConfig,
                physicalLayerConfig,
                othersConfig,
                modulationDistances,
                util,
                trafficSeed);
    }

    public static boolean isQkdEnabled(NetworkConfig networkConfig) {
        return networkConfig != null
                && networkConfig.getQkd() != null
                && networkConfig.getQkd().isEnabled();
    }
}
