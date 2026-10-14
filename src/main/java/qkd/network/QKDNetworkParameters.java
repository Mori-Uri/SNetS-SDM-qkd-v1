package qkd.network;

import java.io.Serializable;
import java.util.List;

import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.NetworkConfig.CoreConfig;
import simulationControl.parsers.NetworkConfig.QKDConfig;

/**
 * Immutable, validated view of the QKD infrastructure parameters declared in
 * the SNetS {@code network} file.
 *
 * <p>This class separates JSON/parser concerns from the QKD network model. The
 * parser may contain an absent or disabled QKD section, while the rest of the
 * QKD code receives a small object with explicit semantics.</p>
 *
 * <p>Refer\u00eancias: [DIBAJ24] (QTND, n\u00facleo qu\u00e2ntico, TDM de 256 slots), [MORI26] (modos route/link/span, PICh, rotas alternativas), [MOGH21] (CCh/PICh na mesma rota do QCh).</p>
 */
public final class QKDNetworkParameters implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean enabled;
    private final double qtndKm;
    private final int quantumCoreId;
    /** true = one QCh pool per QTND span (Dibaj et al.); false = one QCh per physical link. */
    private final boolean spanGranularity;
    private final boolean undirectedChannels;
    private final int tdmSlots;
    private final boolean wavelengthGrid;
    /** Part 23: one dedicated, route-continuous QCh per session. */
    private final boolean routeGranularity;
    private final int alternativeRoutes;
    private final int pichSlots;
    private boolean lowestLossSlots;
    private boolean wavelengthLanes;
    /** Part 26: PICh ends at every node of the QKD route and is shared (true), or is route-continuous and dedicated (false). */
    private boolean pichSegmented = true;
    private int pichSessionsPerChannel = 0;
    private boolean dedicatedQch = false;
    private int qTxPerNode = 0, qRxPerNode = 0, qTxPerTrustedNode = -1, qRxPerTrustedNode = -1;
    private java.util.Map<String, int[]> nodeQEquipment = java.util.Collections.emptyMap();

    private QKDNetworkParameters(boolean enabled, double qtndKm,
            int quantumCoreId) {
        this(enabled, qtndKm, quantumCoreId, false, false, 0, false, false, 0, 0);
    }

    private QKDNetworkParameters(boolean enabled, double qtndKm,
            int quantumCoreId, boolean spanGranularity,
            boolean undirectedChannels, int tdmSlots, boolean wavelengthGrid,
            boolean routeGranularity, int alternativeRoutes, int pichSlots) {
        this.routeGranularity = routeGranularity;
        this.alternativeRoutes = alternativeRoutes;
        this.pichSlots = pichSlots;
        this.enabled = enabled;
        this.qtndKm = qtndKm;
        this.quantumCoreId = quantumCoreId;
        this.spanGranularity = spanGranularity;
        this.undirectedChannels = undirectedChannels;
        this.tdmSlots = tdmSlots;
        this.wavelengthGrid = wavelengthGrid;
    }

    /**
     * Builds and validates QKD parameters from the network configuration.
     *
     * <p>If the legacy network file has no {@code qkd} section, or if
     * {@code qkd.enabled=false}, a disabled parameter object is returned and
     * no QKD topology is created.</p>
     */
    public static QKDNetworkParameters from(NetworkConfig networkConfig) {
        if (networkConfig == null) {
            throw new IllegalArgumentException("networkConfig cannot be null");
        }

        final QKDConfig config = networkConfig.getQkd();
        if (config == null || !config.isEnabled()) {
            return disabled();
        }

        final double qtndKm = config.getQtndKm();
        if (!Double.isFinite(qtndKm) || qtndKm <= 0.0) {
            throw new IllegalArgumentException(
                    "network.qkd.qtndKm must be finite and > 0 when QKD is enabled");
        }

        final int quantumCoreId = config.getQuantumCoreId();
        if (quantumCoreId < 0) {
            throw new IllegalArgumentException(
                    "network.qkd.quantumCoreId must be >= 0 when QKD is enabled");
        }

        if (!containsCore(networkConfig.getCores(), quantumCoreId)) {
            throw new IllegalArgumentException(
                    "network.qkd.quantumCoreId=" + quantumCoreId
                    + " does not exist in network.cores");
        }

        final String granularity = config.getQchGranularity();
        if (!"link".equals(granularity) && !"span".equals(granularity) && !"route".equals(granularity)) {
            throw new IllegalArgumentException(
                    "network.qkd.qchGranularity must be \"route\", \"link\" or \"span\"");
        }
        if (config.getAlternativeRoutes() < 0) {
            throw new IllegalArgumentException("network.qkd.alternativeRoutes must be >= 0");
        }
        final String slotPolicy = config.getQchSlotPolicy();
        if (!"firstfit".equals(slotPolicy) && !"lowestloss".equals(slotPolicy)) {
            throw new IllegalArgumentException("network.qkd.qchSlotPolicy must be \"firstfit\" or \"lowestloss\"");
        }
        if (config.getPichSlots() < 0) {
            throw new IllegalArgumentException("network.qkd.pichSlots must be >= 0");
        }
        if (config.getTdmSlots() < 0) {
            throw new IllegalArgumentException("network.qkd.tdmSlots must be >= 0");
        }
        final String policy = config.getWavelengthPolicy();
        if (!"fixed".equals(policy) && !"grid".equals(policy)) {
            throw new IllegalArgumentException(
                    "network.qkd.wavelengthPolicy must be \"fixed\" or \"grid\"");
        }
        QKDNetworkParameters out = new QKDNetworkParameters(true, qtndKm, quantumCoreId,
                "span".equals(granularity), config.isUndirectedChannels(),
                config.getTdmSlots(), "grid".equals(policy), "route".equals(granularity),
                config.getAlternativeRoutes(), config.getPichSlots());
        out.lowestLossSlots = "lowestloss".equals(slotPolicy);
        final String lanes = config.getSpanLanePlacement();
        if (!"first".equals(lanes) && !"wavelength".equals(lanes)) {
            throw new IllegalArgumentException("network.qkd.spanLanePlacement must be \"first\" or \"wavelength\"");
        }
        out.wavelengthLanes = "wavelength".equals(lanes);
        final String pm = config.getPichMode();
        if (!"segment".equals(pm) && !"route".equals(pm)) {
            throw new IllegalArgumentException("network.qkd.pichMode must be \"segment\" or \"route\"");
        }
        out.pichSegmented = "segment".equals(pm);
        if (config.getPichSessionsPerChannel() < 0) {
            throw new IllegalArgumentException("network.qkd.pichSessionsPerChannel must be >= 0");
        }
        out.pichSessionsPerChannel = config.getPichSessionsPerChannel();
        final String sharing = config.getQchSharing();
        if (!"shared".equals(sharing) && !"dedicated".equals(sharing)) {
            throw new IllegalArgumentException("network.qkd.qchSharing must be \"shared\" or \"dedicated\"");
        }
        out.dedicatedQch = "dedicated".equals(sharing);
        if (config.getQTransmittersPerNode() < 0 || config.getQReceiversPerNode() < 0) {
            throw new IllegalArgumentException("network.qkd.qTransmittersPerNode/qReceiversPerNode must be >= 0");
        }
        out.qTxPerNode = config.getQTransmittersPerNode();
        out.qRxPerNode = config.getQReceiversPerNode();
        out.qTxPerTrustedNode = config.getQTransmittersPerTrustedNode();
        out.qRxPerTrustedNode = config.getQReceiversPerTrustedNode();
        java.util.Map<String, int[]> per = new java.util.HashMap<String, int[]>();
        if (networkConfig.getNodes() != null) {
            for (NetworkConfig.NodeConfig n : networkConfig.getNodes()) {
                if (n.getQTransmitters() < 0 || n.getQReceivers() < 0) {
                    throw new IllegalArgumentException("nodes[" + n.getName() + "].qTransmitters/qReceivers must be >= 0");
                }
                if (n.getQTransmitters() > 0 || n.getQReceivers() > 0) {
                    per.put(n.getName(), new int[] {n.getQTransmitters(), n.getQReceivers()});
                }
            }
        }
        out.nodeQEquipment = per;
        return out;
    }

    public static QKDNetworkParameters disabled() {
        return new QKDNetworkParameters(false, 0.0, -1);
    }

    private static boolean containsCore(List<CoreConfig> cores, int id) {
        if (cores == null) {
            return false;
        }
        for (CoreConfig core : cores) {
            if (core != null && core.getId() == id) {
                return true;
            }
        }
        return false;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double getQtndKm() {
        return qtndKm;
    }

    public int getQuantumCoreId() {
        return quantumCoreId;
    }

    public boolean isSpanGranularity() {
        return spanGranularity;
    }

    public boolean isUndirectedChannels() {
        return undirectedChannels;
    }

    /** Discrete TDM slots per QCh; 0 means continuous key-rate sharing. */
    public int getTdmSlots() {
        return tdmSlots;
    }

    public boolean isWavelengthGrid() {
        return wavelengthGrid;
    }

    /** Part 23: dedicated QCh with the same slots on every span of the QKD route. */
    public boolean isRouteGranularity() {
        return routeGranularity;
    }

    /** Part 23: alternative QKD routes tried after the classical route (0 = none). */
    public int getAlternativeRoutes() {
        return alternativeRoutes;
    }

    /** Span lanes placed at the slot of their grid wavelength (mid-band first) instead of slot k+1. */
    public boolean isWavelengthLanes() {
        return wavelengthLanes;
    }

    /** Part 23: route-granularity QCh bands tried closest-to-attenuation-minimum first. */
    public boolean isLowestLossSlots() {
        return lowestLossSlots;
    }

    /** Part 23: PICh slots reserved on a classical core per QKD session (0 = not modelled). */
    public int getPichSlots() {
        return pichSlots;
    }

    /** Part 26: PICh per link of the QKD route, shared by sessions (true) or route-continuous and dedicated (false). */
    public boolean isPichSegmented() { return pichSegmented; }
    /** Part 26: sessions per PICh in segment mode (0 = unlimited). */
    public int getPichSessionsPerChannel() { return pichSessionsPerChannel; }
    /** Part 26: one QCh per session in link/span granularity (no rate sharing). */
    public boolean isDedicatedQch() { return dedicatedQch; }

    /**
     * Part 26: QTx limit of a QKD node (0 = unlimited). {@code networkNode} is false
     * for the in-line trusted nodes created by the QTND planning.
     */
    public int getQTransmitterLimit(String node, boolean networkNode) {
        int[] v = nodeQEquipment.get(node);
        if (v != null && v[0] > 0) return v[0];
        if (!networkNode && qTxPerTrustedNode >= 0) return qTxPerTrustedNode;
        return qTxPerNode;
    }

    /** Part 26: QRx limit of a QKD node (0 = unlimited). */
    public int getQReceiverLimit(String node, boolean networkNode) {
        int[] v = nodeQEquipment.get(node);
        if (v != null && v[1] > 0) return v[1];
        if (!networkNode && qRxPerTrustedNode >= 0) return qRxPerTrustedNode;
        return qRxPerNode;
    }

    /** True when some QTx/QRx limit is configured. */
    public boolean hasQEquipmentLimits() {
        return qTxPerNode > 0 || qRxPerNode > 0 || qTxPerTrustedNode > 0 || qRxPerTrustedNode > 0 || !nodeQEquipment.isEmpty();
    }

    @Override
    public String toString() {
        return "QKDNetworkParameters{" +
                "enabled=" + enabled +
                ", qtndKm=" + qtndKm +
                ", quantumCoreId=" + quantumCoreId +
                ", granularity=" + (routeGranularity ? "route" : spanGranularity ? "span" : "link") +
                ", alternativeRoutes=" + alternativeRoutes +
                ", pichSlots=" + pichSlots +
                ", undirectedChannels=" + undirectedChannels +
                ", tdmSlots=" + tdmSlots +
                ", wavelengthPolicy=" + (wavelengthGrid ? "grid" : "fixed") +
                '}';
    }
}
