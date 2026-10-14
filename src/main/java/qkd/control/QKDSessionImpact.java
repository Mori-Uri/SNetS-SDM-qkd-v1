package qkd.control;

import java.io.Serializable;

import qkd.physical.QKDRoutePhysicalResult;

/**
 * Projected physical-layer state of one active QKD connection when a new
 * classical circuit candidate is hypothetically added to the network.
 */
public final class QKDSessionImpact implements Serializable {

    private static final long serialVersionUID = 1L;

    private final QKDActiveConnection connection;
    private final boolean affected;
    private final QKDRoutePhysicalResult projectedPhysicalResult;
    private final Boolean explicitViability;
    private final Double explicitProjectedCapacityBps;

    public QKDSessionImpact(QKDActiveConnection connection,
            boolean affected,
            QKDRoutePhysicalResult projectedPhysicalResult) {
        if (connection == null) {
            throw new IllegalArgumentException("connection cannot be null");
        }
        if (projectedPhysicalResult == null) {
            throw new IllegalArgumentException(
                    "projectedPhysicalResult cannot be null");
        }
        this.connection = connection;
        this.affected = affected;
        this.projectedPhysicalResult = projectedPhysicalResult;
        this.explicitViability = null;
        this.explicitProjectedCapacityBps = null;
    }


    public QKDSessionImpact(QKDActiveConnection connection, boolean affected,
            QKDRoutePhysicalResult projectedPhysicalResult,
            boolean remainsViable, double projectedCapacityBps) {
        if (connection == null || projectedPhysicalResult == null)
            throw new IllegalArgumentException("connection/projectedPhysicalResult cannot be null");
        this.connection = connection;
        this.affected = affected;
        this.projectedPhysicalResult = projectedPhysicalResult;
        this.explicitViability = Boolean.valueOf(remainsViable);
        this.explicitProjectedCapacityBps = Double.valueOf(projectedCapacityBps);
    }

    public QKDActiveConnection getConnection() {
        return connection;
    }

    public boolean isAffected() {
        return affected;
    }

    public QKDRoutePhysicalResult getProjectedPhysicalResult() {
        return projectedPhysicalResult;
    }

    public boolean remainsViable() {
        return explicitViability != null ? explicitViability.booleanValue() : projectedPhysicalResult.isViable();
    }

    public double getPreviousMinimumSkrBps() {
        return connection.getPhysicalResult().getMinimumSecretKeyRateBps();
    }

    public double getProjectedMinimumSkrBps() {
        return explicitProjectedCapacityBps != null ? explicitProjectedCapacityBps.doubleValue() : projectedPhysicalResult.getMinimumSecretKeyRateBps();
    }

    public double getPreviousMaximumQber() {
        return connection.getPhysicalResult().getMaximumQber();
    }

    public double getProjectedMaximumQber() {
        return projectedPhysicalResult.getMaximumQber();
    }

    public double getMinimumSkrDeltaBps() {
        return getProjectedMinimumSkrBps() - getPreviousMinimumSkrBps();
    }

    public double getMaximumQberDelta() {
        return getProjectedMaximumQber() - getPreviousMaximumQber();
    }

    @Override
    public String toString() {
        return "QKDSessionImpact{" + connection.getRequest().getPair().getPairName()
                + ", affected=" + affected
                + ", previousMinSKR_Mbps="
                + (getPreviousMinimumSkrBps() / 1e6)
                + ", projectedMinSKR_Mbps="
                + (getProjectedMinimumSkrBps() / 1e6)
                + ", previousMaxQBER=" + getPreviousMaximumQber()
                + ", projectedMaxQBER=" + getProjectedMaximumQber()
                + ", viable=" + remainsViable()
                + '}';
    }
}
