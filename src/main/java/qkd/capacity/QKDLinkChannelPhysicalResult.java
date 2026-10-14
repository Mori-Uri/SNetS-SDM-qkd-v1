package qkd.capacity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import qkd.physical.QKDSpanPhysicalResult;

/** Physical capacity of one discrete QCh over one physical SNetS link. */
public final class QKDLinkChannelPhysicalResult implements Serializable {
    private static final long serialVersionUID = 1L;
    private final List<QKDSpanPhysicalResult> spans;
    private final double minimumSecretKeyRateBps;
    private final double maximumQber;

    public QKDLinkChannelPhysicalResult(List<QKDSpanPhysicalResult> spans) {
        if (spans == null || spans.isEmpty()) {
            throw new IllegalArgumentException("spans cannot be null/empty");
        }
        this.spans = Collections.unmodifiableList(new ArrayList<QKDSpanPhysicalResult>(spans));
        double min = Double.POSITIVE_INFINITY;
        double maxQber = 0.0;
        for (QKDSpanPhysicalResult r : spans) {
            min = Math.min(min, r.getSecretKeyRateBps());
            maxQber = Math.max(maxQber, r.getQber());
        }
        this.minimumSecretKeyRateBps = min;
        this.maximumQber = maxQber;
    }

    public List<QKDSpanPhysicalResult> getSpans() { return spans; }
    public double getMinimumSecretKeyRateBps() { return minimumSecretKeyRateBps; }
    public double getMinimumSecretKeyRateMbps() { return minimumSecretKeyRateBps / 1.0e6; }
    public double getMaximumQber() { return maximumQber; }
    public double getMaximumPxtW() {
        double max = 0.0;
        for (QKDSpanPhysicalResult r : spans) max = Math.max(max, r.getCrosstalkPowerW());
        return max;
    }
    public double getMaximumPXtBackgroundProbability() {
        double max = 0.0;
        for (QKDSpanPhysicalResult r : spans) max = Math.max(max, r.getCrosstalkBackgroundProbability());
        return max;
    }
}
