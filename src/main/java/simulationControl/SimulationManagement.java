package simulationControl;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import measurement.Measurements;
import network.Mesh;
import network.Pair;
import network.RequestGenerator;
import qkd.network.QKDMeshFactory;
import qkd.control.QKDControlPlane;
import qkd.metrics.QKDMetricsSnapshot;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.OthersConfig;
import simulationControl.parsers.PhysicalLayerConfig;
import simulationControl.parsers.SimulationConfig;
import simulationControl.parsers.SimulationRequest;
import simulationControl.parsers.TrafficConfig;
import simulationControl.resultManagers.ResultManager;
import simulator.Simulation;
import simulator.Simulator;

/**
 * Coordinates all load-point/replication executions.
 *
 * <p>Part 6 changes only mesh creation: when {@code network.qkd.enabled=true},
 * {@link QKDMeshFactory} creates a QKD-aware mesh whose runtime request
 * generators are {@code QKDRequestGenerator}s. Legacy scenarios still receive
 * the original {@link Mesh}.</p>
 */
public class SimulationManagement {

    private SimulationRequest simulationRequest;
    private List<List<Simulation>> simulations;
    private volatile int done;
    private volatile int failed;
    private int numOfSimulations;
    private List<List<Measurements>> mainMeasuremens;

    public interface SimulationProgressListener {
        void onSimulationProgressUpdate(double progress);
        void onSimulationFinished();
    }

    public List<List<Measurements>> getMainMeasuremens() {
        return mainMeasuremens;
    }

    public SimulationManagement(SimulationRequest simulationRequest) {
        this.simulationRequest = simulationRequest;
        this.simulations = createAllSimulations(simulationRequest);
        this.done = 0;
        this.numOfSimulations = 0;
        this.mainMeasuremens = new ArrayList<List<Measurements>>();

        for (List<Simulation> loadPoint : simulations) {
            List<Measurements> measurements = new ArrayList<Measurements>();
            mainMeasuremens.add(measurements);

            for (int i = 0; i < loadPoint.size(); i++) {
                measurements.add(null);
                numOfSimulations++;
            }
        }
    }

    public static List<List<Simulation>> createAllSimulations(
            SimulationRequest simulationRequest) {

        Util util = new Util();
        String separator = System.getProperty("file.separator");
        String projectPath = simulationRequest.getPath()
                + separator + simulationRequest.getName();
        util.projectPath = projectPath;

        NetworkConfig networkConfig = simulationRequest.getNetworkConfig();
        SimulationConfig simulationConfig = simulationRequest.getSimulationConfig();
        TrafficConfig trafficConfig = simulationRequest.getTrafficConfig();
        PhysicalLayerConfig physicalLayerConfig =
                simulationRequest.getPhysicalLayerConfig();
        OthersConfig othersConfig = simulationRequest.getOthersConfig();

        System.out.println("Calculating the modulations transmission ranges");

        Mesh baseMesh = QKDMeshFactory.create(
                networkConfig,
                trafficConfig,
                physicalLayerConfig,
                othersConfig,
                null,
                util,
                simulationConfig);

        List<List<Simulation>> simulations =
                new ArrayList<List<Simulation>>();

        for (int loadPoint = 0;
                loadPoint < simulationConfig.getLoadPoints();
                loadPoint++) {

            List<Simulation> replications = new ArrayList<Simulation>();

            for (int replication = 0;
                    replication < simulationConfig.getReplications();
                    replication++) {

                Mesh mesh = QKDMeshFactory.create(
                        networkConfig,
                        trafficConfig,
                        physicalLayerConfig,
                        othersConfig,
                        baseMesh.getModTrDistance(),
                        util,
                        simulationConfig,
                        loadPoint,
                        replication);

                incArrivedRate(mesh.getPairList(), loadPoint);

                Simulation simulation = new Simulation(
                        simulationConfig,
                        mesh,
                        loadPoint,
                        replication,
                        util);

                replications.add(simulation);
            }

            simulations.add(replications);
        }

        util.pairs.addAll(simulations.get(0).get(0).getMesh().getPairList());
        return simulations;
    }

    private static void incArrivedRate(Vector<Pair> pairs, int mult) {
        for (Pair pair : pairs) {
            for (RequestGenerator generator : pair.getRequestGenerators()) {
                generator.incArrivedRate(mult);
            }
        }
    }

    public void startSimulations(
            final SimulationProgressListener simulationProgressListener) {

        final ExecutorService executor = Executors.newScheduledThreadPool(
                simulationRequest.getSimulationConfig().getThreads());
        done = 0;
        failed = 0;

        for (List<Simulation> loadPoint : simulations) {
            for (final Simulation replication : loadPoint) {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Simulator simulator = new Simulator(replication);

                            mainMeasuremens
                                .get(replication.getLoadPoint())
                                .set(replication.getReplication(), simulator.start());

                            int finished;
                            synchronized (SimulationManagement.this) {
                                finished = ++done;
                            }
                            simulationProgressListener.onSimulationProgressUpdate(
                                    ((double) finished) / ((double) numOfSimulations));
                        } catch (Throwable ex) {
                            ex.printStackTrace();
                            /*
                             * Count the failed replication so that the wait loop
                             * below terminates instead of hanging forever.
                             */
                            synchronized (SimulationManagement.this) {
                                failed++;
                                done++;
                            }
                        }
                    }
                });
            }
        }

        while (done < numOfSimulations) {
            try {
                Thread.sleep(200L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        executor.shutdown();
        if (failed > 0) {
            throw new IllegalStateException(failed + " of " + numOfSimulations
                    + " simulations failed; see the stack traces above. No results were saved.");
        }

        ResultManager resultManager = new ResultManager(mainMeasuremens);
        SimulationRequest.Result finalResult = resultManager.getResults();
        finalResult.qkdStatistics = buildQkdStatisticsCsv();
        finalResult.qkdMetrics = buildQkdMetricsCsv();
        finalResult.blockingDetails = buildBlockingDetailsCsv();
        simulationRequest.setResult(finalResult);
        simulationProgressListener.onSimulationFinished();
    }

    private interface QkdValue { double of(QKDMetricsSnapshot m); }

    /** Part 25: BlockingDetails.csv (observation-only counters of the control plane). */
    private String buildBlockingDetailsCsv() {
        List<List<qkd.metrics.BlockingDetailRecorder.Snapshot>> snaps =
                new ArrayList<List<qkd.metrics.BlockingDetailRecorder.Snapshot>>();
        for (List<Simulation> load : simulations) {
            List<qkd.metrics.BlockingDetailRecorder.Snapshot> row =
                    new ArrayList<qkd.metrics.BlockingDetailRecorder.Snapshot>();
            for (Simulation sim : load) {
                Object cp = sim.getControlPlane();
                if (cp instanceof QKDControlPlane) row.add(((QKDControlPlane) cp).snapshotBlockingDetails());
                else if (cp instanceof qkd.metrics.ObservedClassicalControlPlane)
                    row.add(((qkd.metrics.ObservedClassicalControlPlane) cp).snapshotBlockingDetails());
                else return null;
            }
            snaps.add(row);
        }
        try {
            return qkd.metrics.BlockingDetailRecorder.toCsv(snaps);
        } catch (RuntimeException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static double ratio(double a, double b) { return b > 0 ? a / b : Double.NaN; }

    private static double sumEquip(QKDMetricsSnapshot m, int k) {
        double t = 0;
        for (double[] v : m.qEquipment.values()) t += v[k];
        return t;
    }

    /**
     * Part 23: QKD metrics in the standard SNetS layout
     * (Metrics,LoadPoint,src,dest, ,rep0..repN), one row per metric and load
     * point, so they can be averaged/plotted like the classical CSVs.
     */
    private String buildQkdMetricsCsv() {
        List<List<QKDMetricsSnapshot>> snaps = new ArrayList<List<QKDMetricsSnapshot>>();
        int reps = 0;
        for (List<Simulation> load : simulations) {
            List<QKDMetricsSnapshot> row = new ArrayList<QKDMetricsSnapshot>();
            for (Simulation sim : load) {
                if (!(sim.getControlPlane() instanceof QKDControlPlane)) return null;
                row.add(((QKDControlPlane) sim.getControlPlane()).snapshotQkdMetrics(sim.getLoadPoint(), sim.getReplication()));
            }
            reps = Math.max(reps, row.size());
            snaps.add(row);
        }
        if (snaps.isEmpty() || reps == 0) return null;
        java.util.LinkedHashMap<String, QkdValue> metrics = new java.util.LinkedHashMap<String, QkdValue>();
        metrics.put("QKD blocking probability", m -> ratio(m.rejected, m.protectedAttempts));
        metrics.put("QKD blocking probability by classical resources", m -> ratio(m.blockedClassical, m.protectedAttempts));
        metrics.put("QKD blocking probability by QCh spectrum", m -> ratio(m.blockedSpectrum, m.protectedAttempts));
        metrics.put("QKD blocking probability by SKR", m -> ratio(m.blockedSkr, m.protectedAttempts));
        metrics.put("QKD blocking probability by PICh", m -> ratio(m.blockedPich, m.protectedAttempts));
        metrics.put("QKD blocking probability by lack of QTx/QRx", m -> ratio(m.blockedQtrx, m.protectedAttempts));
        metrics.put("QKD blocking probability by other", m -> ratio(m.blockedOther, m.protectedAttempts));
        metrics.put("QKD acceptance ratio", m -> ratio(m.accepted, m.protectedAttempts));
        metrics.put("Fraction of QKD-protected requests", m -> ratio(m.protectedAttempts, m.totalRequestAttempts));
        metrics.put("Classical candidates rejected to protect QKD (per request)", m -> ratio(m.classicalRejectionsByQkdImpact, m.totalRequestAttempts));
        metrics.put("Alternative QKD route ratio", m -> ratio(m.alternativeRouteAccepted, m.accepted));
        metrics.put("Mean admission SKR (Mbit/s)", m -> m.accepted > 0 ? m.meanAdmissionRawSkrMbps : Double.NaN);
        metrics.put("Minimum admission SKR (Mbit/s)", m -> m.accepted > 0 ? m.minimumAdmissionRawSkrMbps : Double.NaN);
        metrics.put("Mean admission QBER", m -> m.accepted > 0 ? m.meanAdmissionQber : Double.NaN);
        metrics.put("Maximum admission QBER", m -> m.accepted > 0 ? m.maximumAdmissionQber : Double.NaN);
        metrics.put("Mean key demand (Mbit/s)", m -> m.accepted > 0 ? m.totalDemandMbps / m.accepted : Double.NaN);
        metrics.put("Mean active QChs", m -> m.meanActiveQchs);
        metrics.put("Mean active Q-TRx pairs", m -> m.meanActiveQTrxPairs);
        metrics.put("Mean active QKD sessions", m -> m.meanActiveSessions);
        metrics.put("Peak active QChs", m -> m.peakActiveQchs);
        metrics.put("Peak Q-TRx pairs", m -> m.peakQTrxPairs);
        metrics.put("Mean Q-TRx pairs per admitted session", m -> m.meanQTrxPerSession);
        metrics.put("Mean hops per admitted session", m -> m.meanHopsPerSession);
        metrics.put("Mean QKD route length (km)", m -> m.meanRouteKmPerSession);
        metrics.put("Planned trusted nodes", m -> m.plannedTrustedNodes);
        metrics.put("Planned QKD spans (directed links)", m -> m.plannedQkdSpans);
        metrics.put("QTND (km)", m -> m.qtndKm);
        // Part 26: shared PIChs (pichMode = segment) and QTx/QRx totals
        metrics.put("Mean open PIChs", m -> m.meanOpenPichs);
        metrics.put("Peak open PIChs", m -> m.peakOpenPichs < 0 ? Double.NaN : m.peakOpenPichs);
        metrics.put("Mean QTx in use (network)", m -> sumEquip(m, 0));
        metrics.put("Mean QRx in use (network)", m -> sumEquip(m, 1));

        StringBuilder sb = new StringBuilder("Metrics,LoadPoint,src,dest, ");
        for (int r = 0; r < reps; r++) sb.append(",rep").append(r);
        sb.append('\n');
        for (java.util.Map.Entry<String, QkdValue> e : metrics.entrySet()) {
            for (int lp = 0; lp < snaps.size(); lp++) {
                sb.append(e.getKey()).append(',').append(lp).append(",all,all, ");
                for (QKDMetricsSnapshot m : snaps.get(lp)) sb.append(',').append(e.getValue().of(m));
                sb.append('\n');
            }
        }
        sb.append('\n');
        // Part 26: QTx/QRx per QKD node (network nodes and in-line trusted nodes)
        java.util.TreeSet<String> qnodes = new java.util.TreeSet<String>();
        for (List<QKDMetricsSnapshot> row : snaps) for (QKDMetricsSnapshot m : row) qnodes.addAll(m.qEquipment.keySet());
        String[] eqNames = {"Mean QTx in use per node", "Mean QRx in use per node", "Peak QTx in use per node", "Peak QRx in use per node"};
        for (int k = 0; k < eqNames.length; k++) {
            for (String node : qnodes) {
                for (int lp = 0; lp < snaps.size(); lp++) {
                    sb.append(eqNames[k]).append(',').append(lp).append(',').append(node).append(",all, ");
                    for (QKDMetricsSnapshot m : snaps.get(lp)) {
                        double[] v = m.qEquipment.get(node);
                        sb.append(',').append(v == null ? 0.0 : v[k]);
                    }
                    sb.append('\n');
                }
            }
        }
        // per pair (only pairs with QKD-protected attempts)
        java.util.TreeSet<String> pairs = new java.util.TreeSet<String>();
        for (List<QKDMetricsSnapshot> row : snaps) for (QKDMetricsSnapshot m : row) pairs.addAll(m.perPair.keySet());
        for (String pair : pairs) {
            String[] sd = pair.split("\\|", 2);
            for (int lp = 0; lp < snaps.size(); lp++) {
                sb.append("QKD blocking probability per pair,").append(lp).append(',').append(sd[0]).append(',').append(sd.length > 1 ? sd[1] : "").append(", ");
                for (QKDMetricsSnapshot m : snaps.get(lp)) {
                    long[] v = m.perPair.get(pair);
                    sb.append(',').append(v == null ? Double.NaN : ratio(v[1], v[0]));
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private String buildQkdStatisticsCsv() {
        boolean anyQkd = false;
        StringBuilder sb = new StringBuilder();
        sb.append("LoadPoint,Replication,TotalRequestAttempts,ProtectedAttempts,Accepted,Rejected,Released,"
                + "ExistingSessionImpactChecks,ClassicalRejectionsByQKDImpact,ActiveRefreshes,"
                + "PrecommitAdmissionChecks,PrecommitAdmissionRejections,"
                + "ActivatedQChs,DeactivatedQChs,TDMReuses,CapacityFailures,PeakActiveQChs,PeakQTRxPairs,"
                + "AcceptedDemand_Mbps,MeanAdmissionRawSKR_Mbps,MinimumAdmissionRawSKR_Mbps,"
                + "MeanAdmissionQBER,MaximumAdmissionQBER,MaximumAdmissionPXT_W,"
                + "QKDAcceptanceRatio,QKDBlockingProbability,MeanActiveQChs,MeanActiveQTRxPairs,"
                + "MeanActiveQKDSessions,QTND_km,WarmupArrivals\n");
        for (List<Simulation> load : simulations) {
            for (Simulation sim : load) {
                if (!(sim.getControlPlane() instanceof QKDControlPlane)) continue;
                anyQkd = true;
                QKDMetricsSnapshot m = ((QKDControlPlane)sim.getControlPlane())
                        .snapshotQkdMetrics(sim.getLoadPoint(), sim.getReplication());
                sb.append(m.loadPoint).append(',').append(m.replication).append(',')
                  .append(m.totalRequestAttempts).append(',').append(m.protectedAttempts).append(',').append(m.accepted).append(',')
                  .append(m.rejected).append(',').append(m.released).append(',')
                  .append(m.existingSessionImpactChecks).append(',')
                  .append(m.classicalRejectionsByQkdImpact).append(',').append(m.activeRefreshes).append(',')
                  .append(m.precommitAdmissionChecks).append(',').append(m.precommitAdmissionRejections).append(',')
                  .append(m.activatedQchs).append(',').append(m.deactivatedQchs).append(',')
                  .append(m.tdmReuses).append(',').append(m.capacityFailures).append(',')
                  .append(m.peakActiveQchs).append(',').append(m.peakQTrxPairs).append(',')
                  .append(m.totalDemandMbps).append(',').append(m.meanAdmissionRawSkrMbps).append(',')
                  .append(m.minimumAdmissionRawSkrMbps).append(',').append(m.meanAdmissionQber).append(',')
                  .append(m.maximumAdmissionQber).append(',').append(m.maximumAdmissionPxtW).append(',')
                  .append(m.acceptanceRatio).append(',').append(m.blockingProbability).append(',')
                  .append(m.meanActiveQchs).append(',').append(m.meanActiveQTrxPairs).append(',')
                  .append(m.meanActiveSessions).append(',').append(m.qtndKm).append(',')
                  .append(m.warmupArrivals).append('\n');
            }
        }
        return anyQkd ? sb.toString() : null;
    }
}
