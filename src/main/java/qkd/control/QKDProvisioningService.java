package qkd.control;

import gprmcsa.routing.Route;
import network.Core;
import network.Link;
import network.Circuit;
import qkd.key.QKDKeyDemand;
import qkd.network.QKDMesh;
import qkd.network.QKDRoutePlan;
import qkd.network.QKDRoutePlanner;
import qkd.physical.QKDPhysicalParameters;
import qkd.physical.QKDPhysicalLayer;
import qkd.physical.QKDRoutePhysicalResult;
import qkd.resource.QKDSession;
import qkd.capacity.QKDSharedAllocationResult;
import qkd.capacity.QKDSharedResourceManager;
import request.RequestForConnection;

/**
 * Performs QKD-side planning, discrete-QCh/TDM capacity reservation and
 * physical feasibility evaluation for a QKD-protected request.
 *
 * <p>The service supports both committed-state evaluation and a pre-commit
 * mode in which one candidate classical circuit is included hypothetically in
 * the crosstalk calculation. The latter is used by the QKD control plane so
 * classical and quantum feasibility are checked before the classical circuit
 * is permanently committed.</p>
 *
 * <p>Refer\u00eancias: [MORI26], [DIBAJ24], [MOGH21] (PICh).</p>
 */
public final class QKDProvisioningService {

    private static final double MAXIMUM_QBER_FOR_STAGE_11 = 1.0;
    private static final int QKD_GUARD_BAND_SLOTS = 0;

    private final QKDMesh mesh;
    private final QKDRoutePlanner routePlanner;
    private final QKDSharedResourceManager resourceManager;
    private final QKDPhysicalLayer physicalEvaluator;
    private final QKDPhysicalParameters physicalParameters;
    private final qkd.network.QKDAlternativeRoutes alternativeRoutes;
    private final int pichSlots;
    /** Part 26: PIChs that follow the QCh (one per link, shared), when pichMode = segment. */
    private final qkd.capacity.QKDPichPool pichPool;
    /** Part 23 counters (reset with the QKD statistics after the warm-up). */
    private long alternativeRouteAdmissions;
    private long pichFailures;
    private long routesTried;

    public QKDProvisioningService(QKDMesh mesh) {
        if (mesh == null) {
            throw new IllegalArgumentException("mesh cannot be null");
        }
        this.mesh = mesh;
        this.routePlanner = new QKDRoutePlanner(mesh.getQkdTopologyPlan());
        this.resourceManager = new QKDSharedResourceManager(mesh);
        this.physicalParameters = mesh.getQkdPhysicalParameters();
        this.physicalEvaluator = new QKDPhysicalLayer(physicalParameters);
        this.alternativeRoutes = new qkd.network.QKDAlternativeRoutes(mesh,
                mesh.getQkdNetworkParameters().getAlternativeRoutes());
        this.pichSlots = mesh.getQkdNetworkParameters().getPichSlots();
        this.pichPool = pichSlots > 0 && mesh.getQkdNetworkParameters().isPichSegmented()
                ? new qkd.capacity.QKDPichPool(pichSlots, mesh.getQkdNetworkParameters().getPichSessionsPerChannel())
                : null;
    }

    /**
     * Provisions QKD against the currently committed classical network state.
     * Kept for direct verification and for callers that do not have a pending
     * classical candidate.
     */
    public QKDProvisioningResult provision(RequestForConnection request) {
        return provision(request, null);
    }

    /**
     * Pre-commit QKD feasibility/provisioning. When additionalClassicalCircuit
     * is non-null, its crosstalk contribution is included as a hypothetical
     * interferer even though its classical spectrum has not yet been committed.
     * QKD resources are tentatively reserved and must either be committed by
     * the caller (by registering the returned connection after classical
     * allocation) or released on failure.
     */
    public QKDProvisioningResult provision(RequestForConnection request,
            Circuit additionalClassicalCircuit) {
        if (request == null) {
            return QKDProvisioningResult.failure("request is null");
        }
        if (!request.isQkdProtected()) {
            return QKDProvisioningResult.failure(
                    "QKD provisioning requested for a classical-only request");
        }
        final QKDKeyDemand keyDemand = request.getQkdKeyDemand();
        if (keyDemand == null) {
            return QKDProvisioningResult.failure(
                    "QKD-protected request has no key-demand requirement");
        }
        if (request.getCircuits() == null || request.getCircuits().size() != 1) {
            return QKDProvisioningResult.failure(
                    "Part 9 currently requires exactly one classical circuit per QKD request");
        }
        if (request.getCircuits().get(0).getRoute() == null) {
            return QKDProvisioningResult.failure(
                    "classical circuit has no selected route");
        }

        final Route classicalRoute = request.getCircuits().get(0).getRoute();
        final java.util.List<Route> candidates = new java.util.ArrayList<Route>();
        candidates.add(classicalRoute);
        candidates.addAll(alternativeRoutes.alternatives(
                classicalRoute.getSource().getName(),
                classicalRoute.getDestination().getName(), classicalRoute));

        String failure = null;
        for (int r = 0; r < candidates.size(); r++) {
            routesTried++;
            QKDProvisioningResult result = provisionOnRoute(request, keyDemand, candidates.get(r),
                    classicalRoute, r > 0, additionalClassicalCircuit);
            if (result.isSuccess()) {
                if (r > 0) alternativeRouteAdmissions++;
                return result;
            }
            // most informative cause over all routes: SKR > PICH > SPECTRUM > OTHER
            if (failure == null || causeRank(result.getFailureReason()) > causeRank(failure))
                failure = result.getFailureReason();
        }
        return QKDProvisioningResult.failure(
                candidates.size() > 1 ? failure + " (also tried " + (candidates.size() - 1) + " alternative QKD route(s))" : failure);
    }

    private QKDProvisioningResult provisionOnRoute(RequestForConnection request, QKDKeyDemand keyDemand,
            Route route, Route classicalRoute, boolean alternative, Circuit additionalClassicalCircuit) {
        final QKDRoutePlan routePlan;
        try {
            routePlan = routePlanner.plan(request, route);
        } catch (RuntimeException exception) {
            return QKDProvisioningResult.failure(
                    "[OTHER] QKD route planning failed: " + exception.getMessage());
        }

        final int quantumCoreId = mesh.getQkdNetworkParameters().getQuantumCoreId();
        final int requiredSlots;
        try {
            requiredSlots = calculateRequiredQuantumSlots(route, quantumCoreId);
        } catch (RuntimeException exception) {
            return QKDProvisioningResult.failure(
                    "[OTHER] QKD slot calculation failed: " + exception.getMessage());
        }

        final QKDSharedAllocationResult allocation = resourceManager.allocate(
                route,
                routePlan,
                keyDemand.getRequiredKeyRateBps(),
                requiredSlots,
                QKD_GUARD_BAND_SLOTS,
                physicalParameters.getAttenuationCenterNm(),
                additionalClassicalCircuit);

        if (!allocation.isSuccess()) {
            String reason = allocation.getFailureReason();
            if (reason == null || !reason.startsWith("[")) reason = "[SKR] " + reason;
            return QKDProvisioningResult.failure(
                    reason.substring(0, reason.indexOf(']') + 1)
                    + " QKD shared-capacity allocation failed: " + reason.substring(reason.indexOf(']') + 1).trim());
        }

        final QKDSession session = allocation.getSession();
        final QKDRoutePhysicalResult physicalResult;
        try {
            physicalResult = additionalClassicalCircuit == null
                    ? resourceManager.buildSessionPhysicalSnapshot(session)
                    : resourceManager.buildSessionPhysicalSnapshotWithAdditionalClassicalCircuit(
                            session, additionalClassicalCircuit,
                            physicalParameters.getAttenuationCenterNm());
        } catch (RuntimeException exception) {
            releaseQuietly(session);
            return QKDProvisioningResult.failure(
                    "[OTHER] QKD physical snapshot failed: " + exception.getMessage());
        }

        if (session.getProvisionedKeyRateBps() + 1e-6 < keyDemand.getRequiredKeyRateBps()) {
            releaseQuietly(session);
            return QKDProvisioningResult.failure(
                    "[SKR] QKD route cannot satisfy key demand: required="
                    + keyDemand.getRequiredKeyRateMbps()
                    + " Mbit/s, provisioned="
                    + (session.getProvisionedKeyRateBps() / 1.0e6)
                    + " Mbit/s");
        }

        QKDActiveConnection connection = new QKDActiveConnection(
                request, classicalRoute, session, physicalResult);
        connection.setQkdRoute(route, alternative);

        if (pichPool != null) {
            java.util.List<qkd.capacity.QKDPichPool.Channel> shares =
                    pichPool.acquire(route.getLinkList(), mesh.getGuardBand(), additionalClassicalCircuit);
            if (shares == null) {
                pichFailures++;
                releaseQuietly(session);
                return QKDProvisioningResult.failure("[PICH] no " + pichSlots
                        + "-slot PICh available on some link of the QKD route");
            }
            connection.setPichShares(shares);
        } else if (pichSlots > 0) {
            QKDActiveConnection.PichReservation pich = reservePich(route, additionalClassicalCircuit);
            if (pich == null) {
                pichFailures++;
                releaseQuietly(session);
                return QKDProvisioningResult.failure("[PICH] no " + pichSlots
                        + "-slot band free on a classical core of every link of the QKD route");
            }
            connection.setPich(pich);
        }
        return QKDProvisioningResult.success(connection);
    }

    /**
     * Part 23 (qualification, slide 30): the PICh of a QKD session occupies the
     * same contiguous slots of one classical core on every link of the QKD
     * route (first-fit: lowest classical core, then lowest slot). The slots of
     * the pending classical candidate are treated as busy. The PICh only
     * occupies spectrum; its (low) power is not added to the crosstalk.
     */
    private QKDActiveConnection.PichReservation reservePich(Route route, Circuit candidate) {
        final java.util.List<Link> links = route.getLinkList();
        final int guard = mesh.getGuardBand();
        final Link first = links.get(0);
        for (Core core : first.getCores()) {
            if (core.isQkdReserved()) continue;
            final int coreId = core.getId();
            int n = Integer.MAX_VALUE;
            boolean usable = true;
            for (Link l : links) {
                Core c = l.getCore(coreId);
                if (c == null || c.isQkdReserved()) { usable = false; break; }
                n = Math.min(n, c.getNumOfSlots());
            }
            if (!usable) continue;
            boolean[] free = new boolean[n + 2];
            java.util.Arrays.fill(free, 1, n + 1, true);
            for (Link l : links) {
                boolean[] f = new boolean[n + 2];
                for (int[] b : l.getCore(coreId).getFreeSpectrumBands(guard))
                    for (int k = Math.max(1, b[0]); k <= Math.min(n, b[1]); k++) f[k] = true;
                if (candidate != null && candidate.getIndexCore() == coreId && candidate.getSpectrumAssigned() != null
                        && candidate.getRoute() != null && containsLink(candidate.getRoute(), l)) {
                    int[] cb = candidate.getSpectrumAssigned();
                    for (int k = Math.max(1, cb[0] - guard); k <= Math.min(n, cb[1] + guard); k++) f[k] = false;
                }
                for (int k = 1; k <= n; k++) free[k] = free[k] && f[k];
            }
            for (int s = 1; s + pichSlots - 1 <= n; s++) {
                boolean ok = true;
                for (int k = s; k < s + pichSlots; k++) if (!free[k]) { ok = false; break; }
                if (!ok) continue;
                int[] band = new int[] {s, s + pichSlots - 1};
                java.util.List<Link> done = new java.util.ArrayList<Link>();
                try {
                    for (Link l : links) {
                        if (!l.getCore(coreId).useSpectrum(band, guard)) throw new IllegalStateException("busy");
                        done.add(l);
                    }
                    return new QKDActiveConnection.PichReservation(
                            java.util.Collections.unmodifiableList(new java.util.ArrayList<Link>(links)), coreId, band, guard);
                } catch (Exception e) {
                    for (Link l : done) {
                        try { l.getCore(coreId).liberateSpectrum(band, guard); } catch (Exception ignored) { }
                    }
                }
            }
        }
        return null;
    }

    private static int causeRank(String reason) {
        if (reason == null) return 0;
        if (reason.startsWith("[SKR]")) return 5;
        if (reason.startsWith("[QTRX]")) return 4;
        if (reason.startsWith("[PICH]")) return 3;
        if (reason.startsWith("[SPECTRUM]")) return 2;
        return 1;
    }

    private static boolean containsLink(Route route, Link link) {
        for (Link l : route.getLinkList()) if (l == link) return true;
        return false;
    }

    private void releasePich(QKDActiveConnection connection) {
        if (pichPool != null && connection.getPichShares() != null) {
            pichPool.release(connection.getPichShares());
            connection.setPichShares(null);
        }
        QKDActiveConnection.PichReservation p = connection.getPich();
        if (p == null) return;
        for (Link l : p.links) {
            try { l.getCore(p.coreId).liberateSpectrum(p.band, p.guardBand); } catch (Exception ignored) { }
        }
        connection.setPich(null);
    }

    public long getAlternativeRouteAdmissions() { return alternativeRouteAdmissions; }
    public long getPichFailures() { return pichFailures; }
    public long getRoutesTried() { return routesTried; }
    public int getPichSlots() { return pichSlots; }

    /** Part 26: shared PICh pool (null when pichMode = route or pichSlots = 0). */
    public qkd.capacity.QKDPichPool getPichPool() { return pichPool; }

    public void resetStatistics() {
        if (pichPool != null) pichPool.resetStatistics();
        alternativeRouteAdmissions = 0;
        pichFailures = 0;
        routesTried = 0;
        resourceManager.resetStatistics();
    }

    public boolean release(QKDActiveConnection connection) throws Exception {
        if (connection == null) {
            return false;
        }
        releasePich(connection);
        return resourceManager.release(connection.getQkdSession());
    }

    /**
     * Converts the reference QCh bandwidth into the number of SNetS slots on
     * the dedicated quantum core. In the current NSFNet scenario both are
     * 12.5 GHz, so this resolves to one slot without hard-coding that result.
     */
    int calculateRequiredQuantumSlots(Route route, int quantumCoreId) {
        if (route == null || route.getLinkList() == null
                || route.getLinkList().isEmpty()) {
            throw new IllegalArgumentException("route cannot be empty");
        }

        int required = 1;
        for (Link link : route.getLinkList()) {
            Core core = link.getCore(quantumCoreId);
            if (core == null || !core.isQkdReserved()) {
                throw new IllegalStateException(
                        "configured QKD core is unavailable on link " + link.getName());
            }
            double slotBandwidth = core.getSlotSpectrumBand();
            if (!Double.isFinite(slotBandwidth) || slotBandwidth <= 0.0) {
                throw new IllegalStateException(
                        "invalid QKD slot bandwidth on link " + link.getName());
            }
            int linkRequired = (int) Math.ceil(
                    physicalParameters.getQchBandwidthHz() / slotBandwidth - 1e-12);
            required = Math.max(required, Math.max(1, linkRequired));
        }
        return required;
    }

    public QKDSharedResourceManager getResourceManager() {
        return resourceManager;
    }

    public double getStageWavelengthNm() {
        return physicalParameters.getAttenuationCenterNm();
    }

    public int getQkdGuardBandSlots() {
        return QKD_GUARD_BAND_SLOTS;
    }

    private void releaseQuietly(QKDSession session) {
        try {
            resourceManager.release(session);
        } catch (Exception ignored) {
            // Best effort during an admission rollback.
        }
    }
}
