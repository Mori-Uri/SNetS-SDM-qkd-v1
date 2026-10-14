package qkd.network;

import java.io.Serializable;

/**
 * One logical QKD transmission span inside a physical SNetS link.
 *
 * <p>A span never modifies the classical topology and is not an SNetS
 * {@code Link}. It only records where QKD must terminate/restart because the
 * configured QTND has been reached.</p>
 *
 * <p>Refer\u00eancias: [DIBAJ24] (trecho QKD entre dois n\u00f3s confi\u00e1veis).</p>
 */
public final class QKDSpan implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String physicalLinkSource;
    private final String physicalLinkDestination;
    private final int index;
    private final int totalSpans;
    private final double startKm;
    private final double endKm;
    private final double lengthKm;
    private final String logicalSource;
    private final String logicalDestination;

    public QKDSpan(String physicalLinkSource, String physicalLinkDestination,
            int index, int totalSpans, double startKm, double endKm,
            String logicalSource, String logicalDestination) {

        if (physicalLinkSource == null || physicalLinkSource.trim().isEmpty()) {
            throw new IllegalArgumentException("physicalLinkSource cannot be blank");
        }
        if (physicalLinkDestination == null
                || physicalLinkDestination.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "physicalLinkDestination cannot be blank");
        }
        if (index < 0 || totalSpans <= 0 || index >= totalSpans) {
            throw new IllegalArgumentException("invalid span index/totalSpans");
        }
        if (!Double.isFinite(startKm) || !Double.isFinite(endKm)
                || startKm < 0.0 || endKm <= startKm) {
            throw new IllegalArgumentException("invalid span offsets");
        }
        if (logicalSource == null || logicalSource.trim().isEmpty()
                || logicalDestination == null
                || logicalDestination.trim().isEmpty()) {
            throw new IllegalArgumentException("logical span endpoints cannot be blank");
        }

        this.physicalLinkSource = physicalLinkSource;
        this.physicalLinkDestination = physicalLinkDestination;
        this.index = index;
        this.totalSpans = totalSpans;
        this.startKm = startKm;
        this.endKm = endKm;
        this.lengthKm = endKm - startKm;
        this.logicalSource = logicalSource;
        this.logicalDestination = logicalDestination;
    }

    public String getPhysicalLinkSource() {
        return physicalLinkSource;
    }

    public String getPhysicalLinkDestination() {
        return physicalLinkDestination;
    }

    public int getIndex() {
        return index;
    }

    public int getTotalSpans() {
        return totalSpans;
    }

    public double getStartKm() {
        return startKm;
    }

    public double getEndKm() {
        return endKm;
    }

    public double getLengthKm() {
        return lengthKm;
    }

    public String getLogicalSource() {
        return logicalSource;
    }

    public String getLogicalDestination() {
        return logicalDestination;
    }

    public boolean startsAtPhysicalSource() {
        return index == 0;
    }

    public boolean endsAtPhysicalDestination() {
        return index == totalSpans - 1;
    }

    @Override
    public String toString() {
        return "QKDSpan{" +
                "index=" + index + '/' + totalSpans +
                ", logicalSource='" + logicalSource + '\'' +
                ", logicalDestination='" + logicalDestination + '\'' +
                ", startKm=" + startKm +
                ", endKm=" + endKm +
                ", lengthKm=" + lengthKm +
                '}';
    }
}
