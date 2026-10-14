package qkd.network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pre-simulation logical QKD segmentation of one physical SNetS link.
 *
 * <p>Refer\u00eancias: [DIBAJ24] (trechos QKD de cada enlace).</p>
 */
public final class QKDLinkPlan implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final double EPSILON_KM = 1e-9;

    private final String source;
    private final String destination;
    private final double physicalLengthKm;
    private final double qtndKm;
    private final List<QKDSpan> spans;

    public QKDLinkPlan(String source, String destination,
            double physicalLengthKm, double qtndKm, List<QKDSpan> spans) {
        if (source == null || source.trim().isEmpty()
                || destination == null || destination.trim().isEmpty()) {
            throw new IllegalArgumentException("link endpoints cannot be blank");
        }
        if (!Double.isFinite(physicalLengthKm) || physicalLengthKm <= 0.0) {
            throw new IllegalArgumentException("physicalLengthKm must be finite and > 0");
        }
        if (!Double.isFinite(qtndKm) || qtndKm <= 0.0) {
            throw new IllegalArgumentException("qtndKm must be finite and > 0");
        }
        if (spans == null || spans.isEmpty()) {
            throw new IllegalArgumentException("spans cannot be null/empty");
        }

        double sum = 0.0;
        for (QKDSpan span : spans) {
            if (span == null) {
                throw new IllegalArgumentException("spans cannot contain null");
            }
            if (span.getLengthKm() - qtndKm > EPSILON_KM) {
                throw new IllegalArgumentException(
                        "QKD span exceeds configured QTND: " + span);
            }
            sum += span.getLengthKm();
        }
        if (Math.abs(sum - physicalLengthKm) > EPSILON_KM) {
            throw new IllegalArgumentException(
                    "sum of QKD spans (" + sum
                    + " km) differs from physical link length ("
                    + physicalLengthKm + " km)");
        }

        this.source = source;
        this.destination = destination;
        this.physicalLengthKm = physicalLengthKm;
        this.qtndKm = qtndKm;
        this.spans = Collections.unmodifiableList(
                new ArrayList<QKDSpan>(spans));
    }

    public String getSource() {
        return source;
    }

    public String getDestination() {
        return destination;
    }

    public double getPhysicalLengthKm() {
        return physicalLengthKm;
    }

    public double getQtndKm() {
        return qtndKm;
    }

    public List<QKDSpan> getSpans() {
        return spans;
    }

    public int getSpanCount() {
        return spans.size();
    }

    /**
     * Number of logical trusted-node boundaries required inside this link.
     */
    public int getVirtualTrustedNodeCount() {
        return Math.max(0, spans.size() - 1);
    }

    @Override
    public String toString() {
        return "QKDLinkPlan{" + source + "->" + destination
                + ", physicalLengthKm=" + physicalLengthKm
                + ", qtndKm=" + qtndKm
                + ", spans=" + spans.size()
                + ", virtualTrustedNodes=" + getVirtualTrustedNodeCount()
                + '}';
    }
}
