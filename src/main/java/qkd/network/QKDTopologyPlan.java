package qkd.network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable pre-simulation QKD view of the entire classical network topology.
 *
 * <p>It stores logical span plans only. It never inserts nodes into the SNetS
 * {@code Mesh}, so classical routing, OXC counts, amplifier calculations, and
 * link metrics remain unchanged.</p>
 *
 * <p>Refer\u00eancias: [DIBAJ24], [MORI26].</p>
 */
public final class QKDTopologyPlan implements Serializable {

    private static final long serialVersionUID = 1L;

    private final QKDNetworkParameters parameters;
    private final List<QKDLinkPlan> links;

    public QKDTopologyPlan(QKDNetworkParameters parameters,
            List<QKDLinkPlan> links) {
        if (parameters == null) {
            throw new IllegalArgumentException("parameters cannot be null");
        }
        if (links == null) {
            throw new IllegalArgumentException("links cannot be null");
        }
        this.parameters = parameters;
        this.links = Collections.unmodifiableList(
                new ArrayList<QKDLinkPlan>(links));
    }

    public QKDNetworkParameters getParameters() {
        return parameters;
    }

    public boolean isEnabled() {
        return parameters.isEnabled();
    }

    public List<QKDLinkPlan> getLinks() {
        return links;
    }

    public int getPhysicalLinkCount() {
        return links.size();
    }

    public int getTotalSpanCount() {
        int total = 0;
        for (QKDLinkPlan link : links) {
            total += link.getSpanCount();
        }
        return total;
    }

    /**
     * Counts logical trusted-node boundaries per directed SNetS link.
     *
     * <p>At this stage reciprocal directed links are intentionally not merged
     * into one physical trusted-node object. No Q-TRx/resource allocation is
     * performed yet, so this count is diagnostic only.</p>
     */
    public int getTotalVirtualTrustedNodeBoundaries() {
        int total = 0;
        for (QKDLinkPlan link : links) {
            total += link.getVirtualTrustedNodeCount();
        }
        return total;
    }

    /** Part 23: trusted-node sites inside the links (span boundaries), counted once per fiber pair. */
    public int getTrustedNodeCount() {
        java.util.Set<String> seen = new java.util.HashSet<String>();
        int count = 0;
        for (QKDLinkPlan l : getLinks()) {
            String a = l.getSource(), b = l.getDestination();
            String key = a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
            if (seen.add(key)) count += Math.max(0, l.getSpanCount() - 1);
        }
        return count;
    }

    public QKDLinkPlan findLink(String source, String destination) {
        for (QKDLinkPlan link : links) {
            if (link.getSource().equals(source)
                    && link.getDestination().equals(destination)) {
                return link;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "QKDTopologyPlan{" +
                "parameters=" + parameters +
                ", physicalLinks=" + getPhysicalLinkCount() +
                ", spans=" + getTotalSpanCount() +
                ", virtualTrustedNodeBoundaries="
                + getTotalVirtualTrustedNodeBoundaries() +
                '}';
    }
}
