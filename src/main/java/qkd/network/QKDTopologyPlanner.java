package qkd.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.NetworkConfig.LinkConfig;

/**
 * Builds the logical QKD topology before the event simulation starts.
 *
 * <p>Each classical SNetS link is divided into consecutive QKD spans no longer
 * than the configured QTND. The division is purely logical: no classical
 * {@code Node}, {@code Link}, or {@code Mesh} object is changed.</p>
 *
 * <p>Refer\u00eancias: [DIBAJ24] M. R. Dibaj et al., "Cost-Optimized Quantum Communication Networks: The Crucial Role of Trusted Node Placement in Multi-Band and Multi-Fiber Realms", IWCIT 2024 (artigo-base) (n\u00f3s confi\u00e1veis a cada QTND: #TN = soma de ceil(L/QTND) - 1) e [MORI26] U. P. Mori, qualifica\u00e7\u00e3o "Planejamento de N\u00f3s Confi\u00e1veis para QKD em SDM-EONs: An\u00e1lise da Coexist\u00eancia Qu\u00e2ntico-Cl\u00e1ssica", UFPI, 2026.</p>
 */
public final class QKDTopologyPlanner {

    private static final double EPSILON_KM = 1e-9;

    /**
     * Creates a complete QKD topology plan from the parsed {@code network}
     * configuration.
     */
    public QKDTopologyPlan plan(NetworkConfig networkConfig) {
        if (networkConfig == null) {
            throw new IllegalArgumentException("networkConfig cannot be null");
        }

        final QKDNetworkParameters parameters =
                QKDNetworkParameters.from(networkConfig);

        if (!parameters.isEnabled()) {
            return new QKDTopologyPlan(
                    parameters,
                    Collections.<QKDLinkPlan>emptyList());
        }

        final List<LinkConfig> configuredLinks = networkConfig.getLinks();
        if (configuredLinks == null || configuredLinks.isEmpty()) {
            throw new IllegalArgumentException(
                    "network.links cannot be null/empty when QKD is enabled");
        }

        final List<QKDLinkPlan> linkPlans =
                new ArrayList<QKDLinkPlan>(configuredLinks.size());

        for (LinkConfig link : configuredLinks) {
            if (link == null) {
                throw new IllegalArgumentException(
                        "network.links cannot contain null when QKD is enabled");
            }
            linkPlans.add(planLink(
                    link.getSource(),
                    link.getDestination(),
                    link.getSize(),
                    parameters.getQtndKm()));
        }

        return new QKDTopologyPlan(parameters, linkPlans);
    }

    /**
     * Splits one physical link into consecutive spans of at most QTND km.
     *
     * <p>Example: distance=80 km and QTND=25 km generates
     * 25 + 25 + 25 + 5 km.</p>
     */
    public QKDLinkPlan planLink(String source, String destination,
            double distanceKm, double qtndKm) {

        if (source == null || source.trim().isEmpty()) {
            throw new IllegalArgumentException("source cannot be blank");
        }
        if (destination == null || destination.trim().isEmpty()) {
            throw new IllegalArgumentException("destination cannot be blank");
        }
        if (!Double.isFinite(distanceKm) || distanceKm <= 0.0) {
            throw new IllegalArgumentException(
                    "link distance must be finite and > 0");
        }
        if (!Double.isFinite(qtndKm) || qtndKm <= 0.0) {
            throw new IllegalArgumentException("QTND must be finite and > 0");
        }

        final int totalSpans = calculateSpanCount(distanceKm, qtndKm);
        final List<QKDSpan> spans = new ArrayList<QKDSpan>(totalSpans);

        for (int i = 0; i < totalSpans; i++) {
            final double startKm = Math.min(distanceKm, i * qtndKm);
            final double endKm = (i == totalSpans - 1)
                    ? distanceKm
                    : Math.min(distanceKm, (i + 1) * qtndKm);

            if (endKm - startKm <= EPSILON_KM) {
                throw new IllegalStateException(
                        "generated zero-length QKD span for "
                        + source + "->" + destination);
            }

            final String logicalSource = (i == 0)
                    ? source
                    : virtualTrustedNodeName(source, destination, i);
            final String logicalDestination = (i == totalSpans - 1)
                    ? destination
                    : virtualTrustedNodeName(source, destination, i + 1);

            spans.add(new QKDSpan(
                    source,
                    destination,
                    i,
                    totalSpans,
                    startKm,
                    endKm,
                    logicalSource,
                    logicalDestination));
        }

        return new QKDLinkPlan(
                source,
                destination,
                distanceKm,
                qtndKm,
                spans);
    }

    public static int calculateSpanCount(double distanceKm, double qtndKm) {
        if (!Double.isFinite(distanceKm) || distanceKm <= 0.0) {
            throw new IllegalArgumentException(
                    "distanceKm must be finite and > 0");
        }
        if (!Double.isFinite(qtndKm) || qtndKm <= 0.0) {
            throw new IllegalArgumentException("qtndKm must be finite and > 0");
        }

        /*
         * Subtracting a very small epsilon avoids creating an accidental
         * additional residual span for exact multiples such as 75/25 when a
         * floating-point representation lands infinitesimally above 3.0.
         */
        final double ratio = distanceKm / qtndKm;
        final int spans = (int) Math.ceil(ratio - 1e-12);
        return Math.max(1, spans);
    }

    /**
     * Diagnostic identity for a logical trusted boundary inside one directed
     * physical link. It is not inserted into the classical SNetS node list.
     */
    public static String virtualTrustedNodeName(String source,
            String destination, int oneBasedIndex) {
        if (oneBasedIndex <= 0) {
            throw new IllegalArgumentException("oneBasedIndex must be > 0");
        }
        return "QKD_TN[" + source + "->" + destination + ":"
                + oneBasedIndex + "]";
    }
}
