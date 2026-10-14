package qkd.verification;

import java.util.HashMap;

import com.google.gson.Gson;

import measurement.Measurements;
import network.Mesh;
import qkd.control.QKDControlPlane;
import qkd.metrics.QKDMetricsSnapshot;
import qkd.network.QKDMeshFactory;
import simulationControl.Util;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.OthersConfig;
import simulationControl.parsers.PhysicalLayerConfig;
import simulationControl.parsers.SimulationConfig;
import simulationControl.parsers.TrafficConfig;
import simulator.Simulation;
import simulator.Simulator;

/** Part 15 verification: classical + QKD viability is decided before commit. */
public final class QKDJointAdmissionVerification {
    private QKDJointAdmissionVerification() { }

    public static void main(String[] args) throws Exception {
        Gson gson = new Gson();
        NetworkConfig network = gson.fromJson(QKDDynamicLifecycleVerification.networkJson(), NetworkConfig.class);
        TrafficConfig traffic = gson.fromJson(QKDDynamicLifecycleVerification.trafficJson(), TrafficConfig.class);
        PhysicalLayerConfig physical = gson.fromJson(QKDDynamicLifecycleVerification.physicalLayerJson(), PhysicalLayerConfig.class);
        String base = QKDDynamicLifecycleVerification.simulationJson();
        String highDemand = base.substring(0, base.length()-1)
                + ",\"qkdKeyDemand\":{\"keySizeBits\":100000000,"
                + "\"updateIntervalsMicroseconds\":[1.0],\"randomSeed\":2024}}";
        SimulationConfig config = gson.fromJson(highDemand, SimulationConfig.class);

        Util util = new Util(); util.projectPath = ".";
        Mesh mesh = QKDMeshFactory.create(network, traffic, physical, new OthersConfig(),
                new HashMap<String, HashMap<Double, Double>>(), util, config);
        Simulation sim = new Simulation(config, mesh, 0, 0, util);
        QKDControlPlane cp = (QKDControlPlane) sim.getControlPlane();
        Measurements m = new Simulator(sim).start();
        check(m != null, "simulation returned null measurements");

        check(cp.getQkdProtectedAttempts() > 0, "expected protected attempts");
        check(cp.getQkdPrecommitAdmissionChecks() > 0, "pre-commit check was not executed");
        check(cp.getQkdPrecommitAdmissionRejections() > 0, "expected pre-commit QKD rejection");
        check(cp.getQkdAccepted() == 0, "impossible QKD demand must not be accepted");
        check(cp.getQkdReleased() == 0, "no accepted QKD session should require departure release");
        check(cp.getActiveQkdSessionCount() == 0, "pre-commit rejection leaked active QKD sessions");

        QKDMetricsSnapshot snap = cp.snapshotQkdMetrics(0, 0);
        check(snap.activatedQchs == snap.deactivatedQchs,
                "tentative QCh reservations were not fully rolled back");

        System.out.println("[OK] impossible QKD demand is rejected in the pre-commit admission hook");
        System.out.println("[OK] classical commit is not followed by QKD rollback in the normal no-grooming path");
        System.out.println("[OK] tentative QCh/Q-TRx resources are fully rolled back on joint-admission failure");
        System.out.println("     attempts=" + cp.getQkdProtectedAttempts()
                + ", precommitChecks=" + cp.getQkdPrecommitAdmissionChecks()
                + ", precommitRejects=" + cp.getQkdPrecommitAdmissionRejections()
                + ", accepted=" + cp.getQkdAccepted()
                + ", activatedQChs=" + snap.activatedQchs
                + ", deactivatedQChs=" + snap.deactivatedQchs);
        System.out.println();
        System.out.println("QKD Part 15 joint-admission verification: ALL CHECKS PASSED");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
