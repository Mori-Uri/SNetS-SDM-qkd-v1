package qkd.network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Logical QKD plan associated with one already-selected classical SNetS route.
 *
 * <p>The plan does not allocate spectrum and does not modify the classical
 * route. It only concatenates the precomputed QKD spans of the physical links
 * traversed by that route.</p>
 *
 * <p>Refer\u00eancias: [DIBAJ24], [MORI26].</p>
 */
public final class QKDRoutePlan implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String source;
    private final String destination;
    private final List<QKDLinkPlan> linkPlans;
    private final List<QKDSpan> spans;
    private final double totalDistanceKm;

    public QKDRoutePlan(String source, String destination,
            List<QKDLinkPlan> linkPlans) {
        if (source == null || source.trim().isEmpty()
                || destination == null || destination.trim().isEmpty()) {
            throw new IllegalArgumentException("route endpoints cannot be blank");
        }
        if (linkPlans == null || linkPlans.isEmpty()) {
            throw new IllegalArgumentException("linkPlans cannot be null/empty");
        }

        this.source = source;
        this.destination = destination;
        this.linkPlans = Collections.unmodifiableList(
                new ArrayList<QKDLinkPlan>(linkPlans));

        List<QKDSpan> flattened = new ArrayList<QKDSpan>();
        double distance = 0.0;
        for (QKDLinkPlan linkPlan : linkPlans) {
            if (linkPlan == null) {
                throw new IllegalArgumentException("linkPlans cannot contain null");
            }
            flattened.addAll(linkPlan.getSpans());
            distance += linkPlan.getPhysicalLengthKm();
        }

        this.spans = Collections.unmodifiableList(flattened);
        this.totalDistanceKm = distance;
    }

    public String getSource() {
        return source;
    }

    public String getDestination() {
        return destination;
    }

    public List<QKDLinkPlan> getLinkPlans() {
        return linkPlans;
    }

    public List<QKDSpan> getSpans() {
        return spans;
    }

    public int getPhysicalLinkCount() {
        return linkPlans.size();
    }

    public int getTotalSpanCount() {
        return spans.size();
    }

    public double getTotalDistanceKm() {
        return totalDistanceKm;
    }

    /** Trusted nodes inserted logically inside physical links due to QTND. */
    public int getVirtualTrustedNodeCount() {
        int total = 0;
        for (QKDLinkPlan linkPlan : linkPlans) {
            total += linkPlan.getVirtualTrustedNodeCount();
        }
        return total;
    }

    /**
     * Original SNetS nodes between source and destination also act as trusted
     * relay boundaries in this simplified QKD model.
     */
    public int getPhysicalIntermediateTrustedNodeCount() {
        return Math.max(0, linkPlans.size() - 1);
    }

    public int getTotalTrustedRelayCount() {
        return getVirtualTrustedNodeCount()
                + getPhysicalIntermediateTrustedNodeCount();
    }

    @Override
    public String toString() {
        return "QKDRoutePlan{" + source + "->" + destination
                + ", physicalLinks=" + getPhysicalLinkCount()
                + ", spans=" + getTotalSpanCount()
                + ", virtualTrustedNodes=" + getVirtualTrustedNodeCount()
                + ", physicalIntermediateTrustedNodes="
                + getPhysicalIntermediateTrustedNodeCount()
                + ", totalTrustedRelays=" + getTotalTrustedRelayCount()
                + ", totalDistanceKm=" + totalDistanceKm
                + '}';
    }
}
