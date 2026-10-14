package qkd.control;

import java.io.Serializable;

import gprmcsa.routing.Route;
import qkd.physical.QKDRoutePhysicalResult;
import qkd.resource.QKDSession;
import request.RequestForConnection;

/**
 * Runtime state associated with one accepted QKD-protected request.
 *
 * <p>The object groups the classical route, the reserved QKD optical resources
 * and the physical-layer result obtained at admission time. It intentionally
 * does not model encryption, key pools, post-processing, or attacks.</p>
 *
 * <p>Refer\u00eancias: [MORI26].</p>
 */
public final class QKDActiveConnection implements Serializable {

    private static final long serialVersionUID = 1L;

    private final RequestForConnection request;
    private final Route classicalRoute;
    private final QKDSession qkdSession;
    private QKDRoutePhysicalResult physicalResult;
    /** Part 23: route actually used by the QCh (classical route or an alternative). */
    private Route qkdRoute;
    private boolean alternativeRoute;
    /** Part 23: PICh slots held on a classical core along the QKD route (null = none). */
    private PichReservation pich;

    /** PICh reservation: the same band on one classical core of every link of the QKD route. */
    public static final class PichReservation implements Serializable {
        private static final long serialVersionUID = 1L;
        public final java.util.List<network.Link> links;
        public final int coreId;
        public final int[] band;
        public final int guardBand;
        public PichReservation(java.util.List<network.Link> links, int coreId, int[] band, int guardBand) {
            this.links = links; this.coreId = coreId; this.band = band; this.guardBand = guardBand;
        }
    }

    public Route getQkdRoute() { return qkdRoute == null ? classicalRoute : qkdRoute; }
    public boolean isAlternativeRoute() { return alternativeRoute; }
    public void setQkdRoute(Route route, boolean alternative) { this.qkdRoute = route; this.alternativeRoute = alternative; }
    /** Part 26: PIChs (one per link of the QKD route) shared with other sessions. */
    private java.util.List<qkd.capacity.QKDPichPool.Channel> pichShares;
    public java.util.List<qkd.capacity.QKDPichPool.Channel> getPichShares() { return pichShares; }
    public void setPichShares(java.util.List<qkd.capacity.QKDPichPool.Channel> v) { this.pichShares = v; }
    public PichReservation getPich() { return pich; }
    public void setPich(PichReservation pich) { this.pich = pich; }

    public QKDActiveConnection(RequestForConnection request,
            Route classicalRoute,
            QKDSession qkdSession,
            QKDRoutePhysicalResult physicalResult) {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        if (classicalRoute == null) {
            throw new IllegalArgumentException("classicalRoute cannot be null");
        }
        if (qkdSession == null) {
            throw new IllegalArgumentException("qkdSession cannot be null");
        }
        if (physicalResult == null) {
            throw new IllegalArgumentException("physicalResult cannot be null");
        }
        this.request = request;
        this.classicalRoute = classicalRoute;
        this.qkdSession = qkdSession;
        this.physicalResult = physicalResult;
    }

    public RequestForConnection getRequest() {
        return request;
    }

    public Route getClassicalRoute() {
        return classicalRoute;
    }

    public QKDSession getQkdSession() {
        return qkdSession;
    }

    public QKDRoutePhysicalResult getPhysicalResult() {
        return physicalResult;
    }

    /**
     * Updates the stored physical state after the committed classical network
     * state changes. The QKD optical allocation itself is unchanged; only the
     * PXT/QBER/SKR snapshot is refreshed.
     */
    public void updatePhysicalResult(QKDRoutePhysicalResult physicalResult) {
        if (physicalResult == null) {
            throw new IllegalArgumentException("physicalResult cannot be null");
        }
        this.physicalResult = physicalResult;
    }

    public double getMinimumSecretKeyRateBps() {
        if (qkdSession.isSharedCapacityManaged()) {
            return qkdSession.getProvisionedKeyRateBps();
        }
        return physicalResult.getMinimumSecretKeyRateBps();
    }

    public double getRawPhysicalMinimumSecretKeyRateBps() {
        return physicalResult.getMinimumSecretKeyRateBps();
    }

    public double getRequiredKeyRateBps() {
        return request.getRequiredQkdKeyRateBps();
    }

    public double getRequiredKeyRateMbps() {
        return request.getRequiredQkdKeyRateMbps();
    }

    public double getKeyRateMarginBps() {
        return getMinimumSecretKeyRateBps() - getRequiredKeyRateBps();
    }

    public double getMaximumQber() {
        return physicalResult.getMaximumQber();
    }

    public boolean isActive() {
        return qkdSession.isActive();
    }

    @Override
    public String toString() {
        return "QKDActiveConnection{" + request.getPair().getPairName()
                + ", keyDemand_Mbps=" + getRequiredKeyRateMbps()
                + ", minSKR_Mbps="
                + physicalResult.getMinimumSecretKeyRateMbps()
                + ", margin_Mbps=" + (getKeyRateMarginBps() / 1.0e6)
                + ", maxQBER=" + physicalResult.getMaximumQber()
                + ", active=" + qkdSession.isActive()
                + '}';
    }
}
