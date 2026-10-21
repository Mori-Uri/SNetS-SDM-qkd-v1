package qkd.control;

import java.util.IdentityHashMap;
import java.util.Map;

import gprmcsa.coreSpectrumAssignment.CoreAndSpectrumAssignmentAlgorithmInterface;
import gprmcsa.integrated.IntegratedRMLSAAlgorithmInterface;
import gprmcsa.modulation.ModulationSelectionAlgorithmInterface;
import gprmcsa.powerAssignment.PowerAssignmentAlgorithmInterface;
import gprmcsa.reallocation.ReallocationAlgorithmInterface;
import gprmcsa.routing.KRoutingAlgorithmInterface;
import gprmcsa.routing.RoutingAlgorithmInterface;
import gprmcsa.spectrumAssignment.SpectrumAssignmentAlgorithmInterface;
import gprmcsa.trafficGrooming.TrafficGroomingAlgorithmInterface;
import network.Circuit;
import network.ControlPlane;
import qkd.network.QKDMesh;
import qkd.metrics.QKDMetricsSnapshot;
import qkd.physical.QKDSpanPhysicalResult;
import request.RequestForConnection;

/**
 * SNetS control plane extended with QKD lifecycle and coexistence checks.
 * Classical-only requests keep the legacy path. For QKD-protected requests,
 * the selected classical candidate is checked together with tentative QKD
 * resources before the classical circuit is committed. Active QKD sessions
 * are also protected from harmful new classical candidates.
 *
 * <p>Refer\u00eancias: [SNETS17] A. Fontinele et al., "An Efficient IA-RMLSA Algorithm for Transparent Elastic Optical Networks", Comput. Netw. 118, 2017 (SNetS) (plano de controle original); [MORI26] U. P. Mori, qualifica\u00e7\u00e3o "Planejamento de N\u00f3s Confi\u00e1veis para QKD em SDM-EONs: An\u00e1lise da Coexist\u00eancia Qu\u00e2ntico-Cl\u00e1ssica", UFPI, 2026 (admiss\u00e3o QKD antes do compromisso cl\u00e1ssico e prote\u00e7\u00e3o das sess\u00f5es ativas).</p>
 */
public class QKDControlPlane extends ControlPlane {

    private static final long serialVersionUID = 1L;
    private static final boolean TRACE = Boolean.getBoolean("snets.qkd.trace");

    private final QKDProvisioningService provisioningService;
    private final QKDSessionRegistry sessionRegistry;
    private final QKDExistingSessionImpactEvaluator existingSessionImpactEvaluator;
    private final Map<Circuit, QKDActiveConnection> pendingQkdAdmissions =
            new IdentityHashMap<Circuit, QKDActiveConnection>();

    private long totalRequestAttempts;
    private long qkdProtectedAttempts;
    private long qkdAccepted;
    private long qkdRejected;
    private long qkdReleased;
    private long qkdExistingSessionImpactChecks;
    private long qkdClassicalRejectionsByExistingSessionImpact;
    private long qkdActiveSessionRefreshes;
    private long qkdPrecommitAdmissionChecks;
    private long qkdPrecommitAdmissionRejections;
    private String lastQkdPrecommitFailureReason;

    private QKDExistingSessionImpactResult lastExistingSessionImpactResult;

    private double qkdAcceptedDemandBpsSum;
    private double qkdAdmissionRawSkrBpsSum;
    private double qkdAdmissionRawSkrBpsMin = Double.POSITIVE_INFINITY;
    private double qkdAdmissionQberSum;
    private double qkdAdmissionQberMax;
    private double qkdAdmissionPxtWMax;
    private final double warmupArrivals;
    /**
     * False when the classical channels cannot add noise to the QChs
     * (qkdReceiver.intercoreCrosstalkEnabled = false, e.g. multi-fiber or the
     * C2 "no XT" scenario). Then p_b = p_dark is independent of the classical
     * state, so the protection of active sessions and their refresh after
     * each classical change are skipped (they could never change anything).
     */
    private final boolean classicalNoiseCoupled;
    private long arrivalsSeen;
    private boolean warmupDone;
    private long occupancySamples;
    private double activeQchSampleSum;
    private double activeQtrxSampleSum;
    private double activeSessionSampleSum;
    /** Part 23: QKD blocking by cause and per pair, admission details. */
    private long qkdBlockedClassical;
    /** Part 25: QKD cause of the last candidate rejected by the addon (null = none). */
    private String lastQkdRejectCause;
    private final qkd.metrics.BlockingDetailRecorder blockingDetails;
    private long qkdBlockedSpectrum;
    private long qkdBlockedSkr;
    private long qkdBlockedPich;
    private long qkdBlockedQtrx;
    private long qkdBlockedOther;
    private long qkdAlternativeRouteAccepted;
    private double qkdAdmittedQTrxSum;
    private double qkdAdmittedHopsSum;
    private double qkdAdmittedRouteKmSum;
    private final java.util.Map<String, long[]> qkdPerPair = new java.util.TreeMap<String, long[]>();

    public QKDControlPlane(QKDMesh mesh, int rmlsaType,
            TrafficGroomingAlgorithmInterface trafficGroomingAlgorithm,
            IntegratedRMLSAAlgorithmInterface integratedRMLSAAlgorithm,
            RoutingAlgorithmInterface routingAlgorithm,
            KRoutingAlgorithmInterface kRoutingAlgorithm,
            SpectrumAssignmentAlgorithmInterface spectrumAssignmentAlgorithm,
            ModulationSelectionAlgorithmInterface modulationSelection,
            CoreAndSpectrumAssignmentAlgorithmInterface coreAndSpectrumAssignment,
            ReallocationAlgorithmInterface reallocation,
            PowerAssignmentAlgorithmInterface powerAssignment) {

        super(mesh, rmlsaType,
                trafficGroomingAlgorithm,
                integratedRMLSAAlgorithm,
                routingAlgorithm,
                kRoutingAlgorithm,
                spectrumAssignmentAlgorithm,
                modulationSelection,
                coreAndSpectrumAssignment,
                reallocation,
                powerAssignment);

        this.provisioningService = new QKDProvisioningService(mesh);
        this.sessionRegistry = new QKDSessionRegistry();
        this.existingSessionImpactEvaluator =
                new QKDExistingSessionImpactEvaluator(mesh);
        this.warmupArrivals = mesh.getQkdWarmupArrivals();
        this.warmupDone = warmupArrivals <= 0.0;
        this.classicalNoiseCoupled = mesh.getQkdPhysicalParameters().isIntercoreCrosstalkEnabled();
        this.blockingDetails = new qkd.metrics.BlockingDetailRecorder(warmupArrivals, this);
    }

    /**
     * Applies the same transient rule as the SNetS classical metrics: once the
     * configured number of arrivals has been generated, every cumulative QKD
     * counter restarts. Then samples the QKD occupancy seen by this arrival;
     * by PASTA (Poisson arrivals) the arrival average estimates the
     * time-average occupancy.
     */
    private void observeArrival() {
        if (!warmupDone && arrivalsSeen >= warmupArrivals) {
            warmupDone = true;
            resetQkdStatistics();
        }
        arrivalsSeen++;
        if (warmupDone) {
            qkd.capacity.QKDSharedResourceManager rm = provisioningService.getResourceManager();
            occupancySamples++;
            activeQchSampleSum += rm.getActiveQuantumChannelCount();
            activeQtrxSampleSum += rm.getActiveQTrxPairs();
            activeSessionSampleSum += sessionRegistry.size();
            rm.sampleEquipment();
            if (provisioningService.getPichPool() != null) provisioningService.getPichPool().sample();
        }
    }

    private void resetQkdStatistics() {
        totalRequestAttempts = 0; qkdProtectedAttempts = 0; qkdAccepted = 0;
        qkdRejected = 0; qkdReleased = 0; qkdExistingSessionImpactChecks = 0;
        qkdClassicalRejectionsByExistingSessionImpact = 0; qkdActiveSessionRefreshes = 0;
        qkdPrecommitAdmissionChecks = 0; qkdPrecommitAdmissionRejections = 0;
        qkdAcceptedDemandBpsSum = 0; qkdAdmissionRawSkrBpsSum = 0;
        qkdAdmissionRawSkrBpsMin = Double.POSITIVE_INFINITY; qkdAdmissionQberSum = 0;
        qkdAdmissionQberMax = 0; qkdAdmissionPxtWMax = 0;
        occupancySamples = 0; activeQchSampleSum = 0; activeQtrxSampleSum = 0;
        activeSessionSampleSum = 0;
        qkdBlockedClassical = 0; qkdBlockedSpectrum = 0; qkdBlockedSkr = 0; qkdBlockedPich = 0; qkdBlockedQtrx = 0; qkdBlockedOther = 0;
        qkdAlternativeRouteAccepted = 0; qkdAdmittedQTrxSum = 0; qkdAdmittedHopsSum = 0; qkdAdmittedRouteKmSum = 0;
        qkdPerPair.clear();
        provisioningService.resetStatistics();
    }

    /**
     * Part 10 extends the legacy classical XT admission point with one extra
     * condition: all already-active QKD sessions must remain viable if the
     * classical candidate were established. The candidate has already received
     * route/core/spectrum/modulation from RMCSA at this point, but has not yet
     * been allocated, so the QKD check is non-destructive.
     */
    @Override
    public boolean isAdmissibleGeneralCrosstalk(Circuit circuit) throws Exception {
        if (!super.isAdmissibleGeneralCrosstalk(circuit)) {
            return false;
        }

        /* First protect QKD sessions that are already active. */
        if (sessionRegistry.isEmpty() || !classicalNoiseCoupled) {
            lastExistingSessionImpactResult = null;
        } else {
            qkdExistingSessionImpactChecks++;
            QKDExistingSessionImpactResult result =
                    existingSessionImpactEvaluator.evaluateCandidate(
                            circuit, sessionRegistry);
            lastExistingSessionImpactResult = result;

            if (!result.isAdmissible()) {
                qkdClassicalRejectionsByExistingSessionImpact++;
                lastQkdRejectCause = qkd.metrics.BlockingDetailRecorder.C_QKD_PROTECT;
                QKDSessionImpact violation = result.getFirstViolation();
                trace("QKD_EXISTING_SESSION_PROTECT",
                        primaryRequest(circuit),
                        violation == null ? result.toString() : violation.toString());
                return false;
            }
        }

        /*
         * Part 15: if this candidate belongs to a QKD-protected request,
         * reserve/evaluate its QKD resources BEFORE the classical circuit is
         * committed. The candidate is included hypothetically in the PXT
         * calculation, so the decision already reflects its own interference.
         */
        RequestForConnection request = primaryRequest(circuit);
        if (request != null && request.isQkdProtected()) {
            qkdPrecommitAdmissionChecks++;

            QKDActiveConnection stale = pendingQkdAdmissions.remove(circuit);
            if (stale != null) {
                provisioningService.release(stale);
            }

            QKDProvisioningResult qkd = provisioningService.provision(request, circuit);
            if (!qkd.isSuccess()) {
                qkdPrecommitAdmissionRejections++;
                lastQkdPrecommitFailureReason = qkd.getFailureReason();
                lastQkdRejectCause = qkdCauseOf(qkd.getFailureReason());
                trace("QKD_PRECOMMIT_REJECT", request, qkd.getFailureReason());
                return false;
            }

            lastQkdPrecommitFailureReason = null;
            pendingQkdAdmissions.put(circuit, qkd.getConnection());
            trace("QKD_PRECOMMIT_OK", request,
                    "reservedRateMbps="
                    + (qkd.getConnection().getRequiredKeyRateBps() / 1.0e6));
        }

        return true;
    }

    /**
     * After a classical circuit is actually committed, refresh the stored
     * PXT/QBER/SKR snapshots of all active QKD sessions from the live network
     * state. This keeps the session registry consistent for later metrics and
     * future admission decisions.
     */
    @Override
    public void allocateCircuit(Circuit circuit) throws Exception {
        QKDActiveConnection pending = pendingQkdAdmissions.remove(circuit);
        try {
            super.allocateCircuit(circuit);
        } catch (Exception exception) {
            if (pending != null) {
                try { provisioningService.release(pending); }
                catch (Exception ignored) { }
            }
            throw exception;
        }

        if (pending != null) {
            sessionRegistry.register(pending.getRequest(), pending);
            trace("QKD_RESOURCE_COMMIT", pending.getRequest(),
                    "activeSessions=" + sessionRegistry.size());
        }

        refreshActiveQkdPhysicalState(circuit);
    }

    /**
     * Classical departures can only remove interference. Refresh the remaining
     * active QKD sessions after the legacy resource release completes.
     */
    @Override
    public void releaseCircuit(Circuit circuit) throws Exception {
        super.releaseCircuit(circuit);
        refreshActiveQkdPhysicalState(circuit);
    }

    @Override
    public boolean handleRequisition(RequestForConnection request) throws Exception {
        lastQkdRejectCause = null;
        if (request != null) blockingDetails.arrival();
        final qkd.capacity.QKDSharedResourceManager rmLog = provisioningService.getResourceManager();
        final boolean protectedReq = request != null && request.isQkdProtected();
        if (protectedReq && rmLog != null) rmLog.beginQchAttemptLog();
        boolean ok = false;
        java.util.List<Object[]> tried = java.util.Collections.emptyList();
        try {
            ok = handleRequisitionQkd(request);
        } finally {
            if (protectedReq && rmLog != null) tried = rmLog.endQchAttemptLog();
        }
        if (request != null) {
            blockingDetails.observe(this, request, ok, ok ? null : lastQkdRejectCause);
            // Part 33: QCh bands on the quantum core (observation only)
            if (protectedReq) {
                java.util.List<Object[]> bands = tried;
                if (ok) bands = sessionBands(sessionRegistry.get(request));
                blockingDetails.markQch(request, ok, bands, !tried.isEmpty() || lastQkdRejectCause != null);
            }
        }
        return ok;
    }

    /** Part 33: {link, core, band} of each QCh of an admitted session. */
    private static java.util.List<Object[]> sessionBands(QKDActiveConnection c) {
        java.util.List<Object[]> out = new java.util.ArrayList<Object[]>();
        if (c == null || c.getQkdSession() == null || c.getQkdSession().getAllocations() == null) return out;
        for (qkd.resource.QKDChannelAllocation a : c.getQkdSession().getAllocations()) {
            if (a == null || a.getLink() == null || a.getSpectrumBand() == null) continue;
            out.add(new Object[] {a.getLink(), Integer.valueOf(a.getQuantumCoreId()), a.getSpectrumBand().clone()});
        }
        return out;
    }

    /** Part 25: blocking details (observation only, see BlockingDetailRecorder). */
    public qkd.metrics.BlockingDetailRecorder.Snapshot snapshotBlockingDetails() {
        return blockingDetails.snapshot();
    }

    private static String qkdCauseOf(String reason) {
        if (reason == null) return qkd.metrics.BlockingDetailRecorder.C_QKD_OTHER;
        if (reason.startsWith("[SPECTRUM]")) return qkd.metrics.BlockingDetailRecorder.C_QKD_SPECTRUM;
        if (reason.startsWith("[SKR]")) return qkd.metrics.BlockingDetailRecorder.C_QKD_SKR;
        if (reason.startsWith("[PICH]")) return qkd.metrics.BlockingDetailRecorder.C_QKD_PICH;
        if (reason.startsWith("[QTRX]")) return qkd.metrics.BlockingDetailRecorder.C_QKD_QTRX;
        return qkd.metrics.BlockingDetailRecorder.C_QKD_OTHER;
    }

    private boolean handleRequisitionQkd(RequestForConnection request) throws Exception {
        if (request != null) observeArrival();
        if (request != null) totalRequestAttempts++;
        if (request == null || !request.isQkdProtected()) {
            return super.handleRequisition(request);
        }

        qkdProtectedAttempts++;
        final long[] pairStats = pairStats(request);
        pairStats[0]++;

        final boolean classicalSuccess = super.handleRequisition(request);
        if (!classicalSuccess) {
            releasePendingForRequest(request);
            qkdRejected++;
            pairStats[1]++;
            countBlockingCause(lastQkdPrecommitFailureReason);
            trace(lastQkdPrecommitFailureReason == null
                            ? "CLASSICAL_REJECT" : "QKD_REJECT",
                    request, lastQkdPrecommitFailureReason);
            lastQkdPrecommitFailureReason = null;
            return false;
        }

        /*
         * Normal no-grooming path: allocateCircuit has already committed the
         * pre-checked QKD connection. A fallback is kept for grooming paths
         * that can satisfy a request without establishing a new circuit and
         * therefore never pass through isAdmissibleGeneralCrosstalk.
         */
        QKDActiveConnection connection = sessionRegistry.get(request);
        if (connection == null) {
            final QKDProvisioningResult fallback = provisioningService.provision(request);
            if (!fallback.isSuccess()) {
                super.finalizeConnection(request);
                markQkdAdmissionFailure(request);
                lastQkdRejectCause = qkdCauseOf(fallback.getFailureReason());
                qkdRejected++;
                pairStats[1]++;
                countBlockingCause(fallback.getFailureReason());
                trace("QKD_REJECT_FALLBACK", request, fallback.getFailureReason());
                return false;
            }
            connection = fallback.getConnection();
            sessionRegistry.register(request, connection);
        }

        qkdAccepted++;
        recordAdmissionMetrics(connection);
        trace("QKD_ADMIT", request,
                "activeSessions=" + sessionRegistry.size());
        return true;
    }

    @Override
    public void finalizeConnection(RequestForConnection request) throws Exception {
        Exception qkdFailure = null;

        QKDActiveConnection connection = sessionRegistry.remove(request);
        if (connection != null) {
            try {
                if (provisioningService.release(connection)) {
                    qkdReleased++;
                }
                trace("QKD_RELEASE", request,
                        "activeSessions=" + sessionRegistry.size());
            } catch (Exception exception) {
                qkdFailure = exception;
            }
        }

        /* Always try to release the classical resources as well. */
        super.finalizeConnection(request);

        if (qkdFailure != null) {
            throw qkdFailure;
        }
    }

    private void refreshActiveQkdPhysicalState(Circuit changed) {
        if (sessionRegistry.isEmpty() || !classicalNoiseCoupled) {
            return;
        }
        qkdActiveSessionRefreshes +=
                existingSessionImpactEvaluator.refreshActiveSessions(
                        sessionRegistry, changed);
    }

    private long[] pairStats(RequestForConnection request) {
        String key = request.getPair() == null ? "?|?"
                : request.getPair().getSource().getName() + "|" + request.getPair().getDestination().getName();
        long[] v = qkdPerPair.get(key);
        if (v == null) { v = new long[2]; qkdPerPair.put(key, v); }
        return v;
    }

    /** Failure reasons carry a [CAUSE] tag set by the provisioning service. */
    private void countBlockingCause(String reason) {
        if (reason == null) { qkdBlockedClassical++; return; }
        if (reason.startsWith("[SPECTRUM]")) qkdBlockedSpectrum++;
        else if (reason.startsWith("[SKR]")) qkdBlockedSkr++;
        else if (reason.startsWith("[PICH]")) qkdBlockedPich++;
        else if (reason.startsWith("[QTRX]")) qkdBlockedQtrx++;
        else qkdBlockedOther++;
    }

    private RequestForConnection primaryRequest(Circuit circuit) {
        if (circuit == null || circuit.getRequests() == null
                || circuit.getRequests().isEmpty()) {
            return null;
        }
        return circuit.getRequests().get(0);
    }

    private void releasePendingForRequest(RequestForConnection request) {
        if (request == null || pendingQkdAdmissions.isEmpty()) return;
        java.util.Iterator<Map.Entry<Circuit, QKDActiveConnection>> it =
                pendingQkdAdmissions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Circuit, QKDActiveConnection> e = it.next();
            if (e.getValue() != null && e.getValue().getRequest() == request) {
                try { provisioningService.release(e.getValue()); }
                catch (Exception ignored) { }
                it.remove();
            }
        }
    }

    private void markQkdAdmissionFailure(RequestForConnection request) {
        if (request.getCircuits() == null || request.getCircuits().isEmpty()) {
            return;
        }
        Circuit circuit = request.getCircuits().get(0);
        circuit.setWasBlocked(true);
        circuit.setBlockCause(Circuit.BY_OTHER);
    }

    public QKDSessionRegistry getQkdSessionRegistry() {
        return sessionRegistry;
    }

    public int getActiveQkdSessionCount() {
        return sessionRegistry.size();
    }

    public QKDActiveConnection getActiveQkdConnection(
            RequestForConnection request) {
        return sessionRegistry.get(request);
    }

    public long getTotalRequestAttempts() { return totalRequestAttempts; }

    public long getQkdProtectedAttempts() {
        return qkdProtectedAttempts;
    }

    public long getQkdAccepted() {
        return qkdAccepted;
    }

    public long getQkdRejected() {
        return qkdRejected;
    }

    public long getQkdReleased() {
        return qkdReleased;
    }

    public long getQkdExistingSessionImpactChecks() {
        return qkdExistingSessionImpactChecks;
    }

    public long getQkdClassicalRejectionsByExistingSessionImpact() {
        return qkdClassicalRejectionsByExistingSessionImpact;
    }

    public long getQkdActiveSessionRefreshes() {
        return qkdActiveSessionRefreshes;
    }

    public long getQkdPrecommitAdmissionChecks() {
        return qkdPrecommitAdmissionChecks;
    }

    public long getQkdPrecommitAdmissionRejections() {
        return qkdPrecommitAdmissionRejections;
    }

    private void recordAdmissionMetrics(QKDActiveConnection connection) {
        if (connection.isAlternativeRoute()) qkdAlternativeRouteAccepted++;
        qkdAdmittedQTrxSum += connection.getQkdSession().getRoutePlan().getTotalSpanCount();
        qkdAdmittedHopsSum += connection.getQkdRoute().getLinkList().size();
        qkdAdmittedRouteKmSum += connection.getQkdRoute().getDistanceAllLinks();
        qkdAcceptedDemandBpsSum += connection.getRequiredKeyRateBps();
        double raw = connection.getRawPhysicalMinimumSecretKeyRateBps();
        qkdAdmissionRawSkrBpsSum += raw;
        qkdAdmissionRawSkrBpsMin = Math.min(qkdAdmissionRawSkrBpsMin, raw);
        qkdAdmissionQberSum += connection.getMaximumQber();
        qkdAdmissionQberMax = Math.max(qkdAdmissionQberMax, connection.getMaximumQber());
        for (QKDSpanPhysicalResult span : connection.getPhysicalResult().getSpanResults())
            qkdAdmissionPxtWMax = Math.max(qkdAdmissionPxtWMax, span.getCrosstalkPowerW());
    }

    public QKDMetricsSnapshot snapshotQkdMetrics(int loadPoint, int replication) {
        QKDMetricsSnapshot m = new QKDMetricsSnapshot();
        m.loadPoint=loadPoint; m.replication=replication;
        m.totalRequestAttempts=totalRequestAttempts; m.protectedAttempts=qkdProtectedAttempts; m.accepted=qkdAccepted; m.rejected=qkdRejected; m.released=qkdReleased;
        m.existingSessionImpactChecks=qkdExistingSessionImpactChecks;
        m.classicalRejectionsByQkdImpact=qkdClassicalRejectionsByExistingSessionImpact;
        m.activeRefreshes=qkdActiveSessionRefreshes;
        m.precommitAdmissionChecks=qkdPrecommitAdmissionChecks;
        m.precommitAdmissionRejections=qkdPrecommitAdmissionRejections;
        qkd.capacity.QKDSharedResourceManager rm = provisioningService.getResourceManager();
        m.activatedQchs=rm.getActivatedChannels(); m.deactivatedQchs=rm.getDeactivatedChannels();
        m.tdmReuses=rm.getReusedCapacityReservations(); m.capacityFailures=rm.getFailedCapacityReservations();
        m.peakActiveQchs=rm.getPeakActiveChannels(); m.peakQTrxPairs=rm.getPeakQTrxPairs();
        m.totalDemandMbps=qkdAcceptedDemandBpsSum/1e6;
        m.acceptanceRatio = qkdProtectedAttempts > 0 ? ((double) qkdAccepted) / qkdProtectedAttempts : Double.NaN;
        m.blockingProbability = qkdProtectedAttempts > 0 ? ((double) qkdRejected) / qkdProtectedAttempts : Double.NaN;
        if (occupancySamples > 0) {
            m.meanActiveQchs = activeQchSampleSum / occupancySamples;
            m.meanActiveQTrxPairs = activeQtrxSampleSum / occupancySamples;
            m.meanActiveSessions = activeSessionSampleSum / occupancySamples;
        }
        m.qtndKm = ((QKDMesh) getMesh()).getQkdNetworkParameters().getQtndKm();
        m.blockedClassical = qkdBlockedClassical; m.blockedSpectrum = qkdBlockedSpectrum;
        m.blockedSkr = qkdBlockedSkr; m.blockedPich = qkdBlockedPich; m.blockedOther = qkdBlockedOther;
        m.blockedQtrx = qkdBlockedQtrx;
        m.qEquipmentFailures = rm.getQEquipmentFailures();
        m.qEquipment.putAll(rm.equipmentStatistics());
        qkd.capacity.QKDPichPool pp = provisioningService.getPichPool();
        if (pp != null) { m.meanOpenPichs = pp.getMeanOpenChannels(); m.peakOpenPichs = pp.getPeakOpenChannels(); m.pichOpened = pp.getOpened(); }
        m.alternativeRouteAccepted = qkdAlternativeRouteAccepted;
        if (qkdAccepted > 0) {
            m.meanQTrxPerSession = qkdAdmittedQTrxSum / qkdAccepted;
            m.meanHopsPerSession = qkdAdmittedHopsSum / qkdAccepted;
            m.meanRouteKmPerSession = qkdAdmittedRouteKmSum / qkdAccepted;
        }
        m.routeCandidateBandsEvaluated = rm.getRouteCandidateBandsEvaluated();
        m.plannedTrustedNodes = ((QKDMesh) getMesh()).getQkdTopologyPlan().getTrustedNodeCount();
        m.plannedQkdSpans = ((QKDMesh) getMesh()).getQkdTopologyPlan().getTotalSpanCount();
        for (java.util.Map.Entry<String, long[]> e : qkdPerPair.entrySet())
            m.perPair.put(e.getKey(), new long[] {e.getValue()[0], e.getValue()[1]});
        m.warmupArrivals = warmupArrivals;
        if(qkdAccepted>0){
            m.meanAdmissionRawSkrMbps=(qkdAdmissionRawSkrBpsSum/qkdAccepted)/1e6;
            m.minimumAdmissionRawSkrMbps=qkdAdmissionRawSkrBpsMin/1e6;
            m.meanAdmissionQber=qkdAdmissionQberSum/qkdAccepted;
            m.maximumAdmissionQber=qkdAdmissionQberMax;
            m.maximumAdmissionPxtW=qkdAdmissionPxtWMax;
        }
        return m;
    }

    public QKDExistingSessionImpactResult getLastExistingSessionImpactResult() {
        return lastExistingSessionImpactResult;
    }

    private void trace(String action, RequestForConnection request,
            String detail) {
        if (!TRACE) {
            return;
        }
        String pair = request != null && request.getPair() != null
                ? request.getPair().getPairName() : "?";
        System.out.println("[QKD-LIFECYCLE] " + action + " pair=" + pair
                + (detail == null ? "" : " " + detail));
    }
}
