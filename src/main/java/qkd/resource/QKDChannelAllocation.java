package qkd.resource;

import java.io.Serializable;

import network.Link;

/**
 * One spectrum reservation for a QKD channel on one physical SNetS link.
 *
 * <p>In the current simplified model a single reservation on the QKD-reserved
 * core of a physical link represents the quantum resource used by all logical
 * QTND spans contained in that same physical link. This deliberately avoids
 * modifying SNetS {@code Spectrum} into a segment-aware structure at this
 * stage.</p>
 */
public final class QKDChannelAllocation implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Link link;
    private final String source;
    private final String destination;
    private final int quantumCoreId;
    private final int[] spectrumBand;
    private final int guardBand;
    private final int coveredQkdSpans;

    public QKDChannelAllocation(Link link, int quantumCoreId,
            int[] spectrumBand, int guardBand, int coveredQkdSpans) {
        if (link == null) {
            throw new IllegalArgumentException("link cannot be null");
        }
        if (spectrumBand == null || spectrumBand.length != 2
                || spectrumBand[0] > spectrumBand[1]) {
            throw new IllegalArgumentException("invalid spectrumBand");
        }
        if (quantumCoreId < 0) {
            throw new IllegalArgumentException("quantumCoreId must be >= 0");
        }
        if (guardBand < 0) {
            throw new IllegalArgumentException("guardBand must be >= 0");
        }
        if (coveredQkdSpans <= 0) {
            throw new IllegalArgumentException("coveredQkdSpans must be > 0");
        }

        this.link = link;
        this.source = link.getSource().getName();
        this.destination = link.getDestination().getName();
        this.quantumCoreId = quantumCoreId;
        this.spectrumBand = new int[] {spectrumBand[0], spectrumBand[1]};
        this.guardBand = guardBand;
        this.coveredQkdSpans = coveredQkdSpans;
    }

    public Link getLink() {
        return link;
    }

    public String getSource() {
        return source;
    }

    public String getDestination() {
        return destination;
    }

    public int getQuantumCoreId() {
        return quantumCoreId;
    }

    public int[] getSpectrumBand() {
        return new int[] {spectrumBand[0], spectrumBand[1]};
    }

    public int getGuardBand() {
        return guardBand;
    }

    public int getCoveredQkdSpans() {
        return coveredQkdSpans;
    }

    public int getAllocatedSlots() {
        return spectrumBand[1] - spectrumBand[0] + 1;
    }

    @Override
    public String toString() {
        return "QKDChannelAllocation{" + source + "->" + destination
                + ", core=" + quantumCoreId
                + ", spectrum=[" + spectrumBand[0] + ',' + spectrumBand[1] + ']'
                + ", slots=" + getAllocatedSlots()
                + ", guardBand=" + guardBand
                + ", coveredQkdSpans=" + coveredQkdSpans
                + '}';
    }
}
