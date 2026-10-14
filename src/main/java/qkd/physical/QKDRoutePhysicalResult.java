package qkd.physical;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import qkd.network.QKDRoutePlan;

/**
 * Aggregated physical-layer result for all spans of one QKD route plan.
 *
 * <p>End-to-end viability in the current trusted-node abstraction requires
 * every individual QKD span to satisfy the configured SKR and QBER criteria.
 * The minimum span SKR is exposed as the route bottleneck key-generation rate.</p>
 */
public final class QKDRoutePhysicalResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final QKDRoutePlan routePlan;
    private final List<QKDSpanPhysicalResult> spanResults;
    private final boolean viable;
    private final double minimumSecretKeyRateBps;
    private final double maximumQber;
    private final int bottleneckSpanIndex;

    public QKDRoutePhysicalResult(
            QKDRoutePlan routePlan,
            List<QKDSpanPhysicalResult> spanResults) {

        if (routePlan == null) {
            throw new IllegalArgumentException("routePlan cannot be null");
        }
        if (spanResults == null || spanResults.isEmpty()) {
            throw new IllegalArgumentException("spanResults cannot be null/empty");
        }
        if (spanResults.size() != routePlan.getTotalSpanCount()) {
            throw new IllegalArgumentException(
                    "spanResults size differs from QKD route span count");
        }

        this.routePlan = routePlan;
        this.spanResults = Collections.unmodifiableList(
                new ArrayList<QKDSpanPhysicalResult>(spanResults));

        boolean allViable = true;
        double minSkr = Double.POSITIVE_INFINITY;
        double maxQberSeen = 0.0;
        int bottleneck = -1;

        for (int i = 0; i < spanResults.size(); i++) {
            QKDSpanPhysicalResult result = spanResults.get(i);
            if (result == null) {
                throw new IllegalArgumentException(
                        "spanResults cannot contain null");
            }
            allViable &= result.isViable();

            if (result.getSecretKeyRateBps() < minSkr) {
                minSkr = result.getSecretKeyRateBps();
                bottleneck = i;
            }
            if (result.getQber() > maxQberSeen) {
                maxQberSeen = result.getQber();
            }
        }

        this.viable = allViable;
        this.minimumSecretKeyRateBps = minSkr;
        this.maximumQber = maxQberSeen;
        this.bottleneckSpanIndex = bottleneck;
    }

    public QKDRoutePlan getRoutePlan() {
        return routePlan;
    }

    public List<QKDSpanPhysicalResult> getSpanResults() {
        return spanResults;
    }

    public boolean isViable() {
        return viable;
    }

    public double getMinimumSecretKeyRateBps() {
        return minimumSecretKeyRateBps;
    }

    public double getMinimumSecretKeyRateMbps() {
        return minimumSecretKeyRateBps / 1e6;
    }

    public double getMaximumQber() {
        return maximumQber;
    }

    public int getBottleneckSpanIndex() {
        return bottleneckSpanIndex;
    }

    public QKDSpanPhysicalResult getBottleneckSpan() {
        return spanResults.get(bottleneckSpanIndex);
    }

    @Override
    public String toString() {
        return "QKDRoutePhysicalResult{" +
                routePlan.getSource() + "->" + routePlan.getDestination() +
                ", spans=" + spanResults.size() +
                ", minSKR_Mbps=" + getMinimumSecretKeyRateMbps() +
                ", maxQBER=" + maximumQber +
                ", bottleneckSpanIndex=" + bottleneckSpanIndex +
                ", viable=" + viable +
                '}';
    }
}
