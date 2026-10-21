package qkd.capacity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import gprmcsa.routing.Route;
import network.Circuit;
import network.Core;
import network.Link;
import qkd.network.QKDLinkPlan;
import qkd.network.QKDMesh;
import qkd.network.QKDNetworkParameters;
import qkd.network.QKDRoutePlan;
import qkd.network.QKDSpan;
import qkd.physical.QKDPhysicalParameters;
import qkd.resource.QKDChannelAllocation;
import qkd.resource.QKDSession;

/**
 * Simulation-local manager for discrete QChs, Q-TRx pairs and TDM/key-rate
 * sharing.
 *
 * <p><b>Link granularity</b> (default, Parts 12/13): one active QCh occupies
 * spectrum once on a physical link and can serve multiple key demands while
 * the sum of their reserved rates does not exceed that QCh's bottleneck SKR
 * across the QTND spans of the link.</p>
 *
 * <p><b>Span granularity</b> (network.qkd.qchGranularity = "span"): every
 * QTND span keeps its own pool of QChs, as in the trusted-node planning of
 * Dibaj et al. (2024): a demand first reuses an active QCh of the span with
 * enough capacity and otherwise activates a new one; Q-TRx usage is the number
 * of active QChs summed over all spans. The k-th QCh of a span uses lane k of
 * the reserved QKD core of the link (and, with wavelengthPolicy = "grid", the
 * k-th lowest-attenuation wavelength of the QCh grid). With
 * undirectedChannels = true both directions of a fiber pair share the pools.</p>
 *
 * <p>With tdmSlots &gt; 0 each QCh is divided in that many time slots; a
 * demand reserves ceil(demand / capacity * slots) slots of a single QCh and is
 * never split across QChs (reference TDM rule). With tdmSlots = 0 the original
 * continuous key-rate sharing is used.</p>
 *
 * <p>Refer\u00eancias: [DIBAJ24] M. R. Dibaj et al., "Cost-Optimized Quantum Communication Networks: The Crucial Role of Trusted Node Placement in Multi-Band and Multi-Fiber Realms", IWCIT 2024 (artigo-base) (QChs compartilhados por trecho, pares Q-TRx, TDM); [XIE24] C. Xie, Y. Sun et al., "Synergistic Resource Allocation with Key Generation and Consumption Matching in Quantum Secured Multicore Fiber Optical Networks", ACP/IPOC 2024 (casamento entre gera\u00e7\u00e3o e consumo de chave em MCF); [MORI26] U. P. Mori, qualifica\u00e7\u00e3o "Planejamento de N\u00f3s Confi\u00e1veis para QKD em SDM-EONs: An\u00e1lise da Coexist\u00eancia Qu\u00e2ntico-Cl\u00e1ssica", UFPI, 2026.</p>
 */
public final class QKDSharedResourceManager implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final double EPS = 1e-6;

    private final QKDMesh mesh;
    private final QKDPhysicalParameters physicalParameters;
    private final qkd.physical.QKDPhysicalLayer evaluator;
    private final boolean spanGranularity;
    private final boolean undirected;
    private final int tdmSlots;
    private final boolean wavelengthGrid;
    private final boolean wavelengthLanes;
    private final boolean routeGranularity;
    /** Part 26: one QCh per session (no reuse of spare key rate). */
    private final boolean dedicatedQch;
    /** Part 26: QKD transmitters/receivers in use per QKD node: {QTx, QRx, peak QTx, peak QRx}. */
    private final Map<String, long[]> qEquipment = new java.util.TreeMap<String, long[]>();
    /** Part 26: arrival-sampled sums {QTx, QRx} per node, for the mean occupancy. */
    private final Map<String, double[]> qEquipmentSums = new java.util.TreeMap<String, double[]>();
    private long qEquipmentSamples;
    private long qEquipmentFailures;
    private final Set<String> networkNodeNames = new java.util.HashSet<String>();
    /** Part 26: why the last QCh activation failed: null, "SPECTRUM" or "QTRX". */
    private String lastActivationFailure;

    /*
     * Part 33 (observation only): the QCh bands touched while one request is handled (channels reserved or
     * activated, and the bands evaluated in route mode), for the spectrum maps of the quantum core
     * (SlotBlocking.csv). Null when no request is being observed. Does not change any decision.
     */
    private transient List<Object[]> qchAttemptLog;

    public synchronized void beginQchAttemptLog() { qchAttemptLog = new ArrayList<Object[]>(); }

    public synchronized List<Object[]> endQchAttemptLog() {
        List<Object[]> out = qchAttemptLog;
        qchAttemptLog = null;
        return out == null ? Collections.<Object[]>emptyList() : out;
    }

    private void logQch(QKDChannelAllocation a) {
        if (qchAttemptLog != null && a != null && a.getLink() != null && a.getSpectrumBand() != null)
            qchAttemptLog.add(new Object[] {a.getLink(), Integer.valueOf(a.getQuantumCoreId()), a.getSpectrumBand().clone()});
    }
    private String lastActivationFailureNode;

    /** Link-granularity channels. */
    private final Map<Link, List<QKDSharedChannel>> channelsByLink =
            new IdentityHashMap<Link, List<QKDSharedChannel>>();
    /** Span-granularity pools (key = span identity). */
    private final Map<Object, List<QKDSharedChannel>> spanPools =
            new LinkedHashMap<Object, List<QKDSharedChannel>>();
    /** Reference counts of QKD-core lanes occupied on each link (span mode). */
    private final Map<Link, Map<Integer, Integer>> laneRefs =
            new IdentityHashMap<Link, Map<Integer, Integer>>();
    private final Map<String, QKDLinkPlan> singleSpanPlans = new HashMap<String, QKDLinkPlan>();
    private final Set<QKDSharedChannel> allChannels = new LinkedHashSet<QKDSharedChannel>();
    private List<Double> wavelengthGridNm = new ArrayList<Double>();
    private long nextChannelId = 1L;

    private long activatedChannels;
    private long deactivatedChannels;
    private long reusedCapacityReservations;
    private long failedCapacityReservations;
    private long peakActiveChannels;
    private long peakQTrxPairs;
    /** Part 23 (route granularity) failure counters and evaluation effort. */
    private long routeSpectrumFailures;
    private long routeSkrFailures;
    private long routeCandidateBandsEvaluated;

    public QKDSharedResourceManager(QKDMesh mesh) {
        if (mesh == null) throw new IllegalArgumentException("mesh cannot be null");
        this.mesh = mesh;
        this.physicalParameters = mesh.getQkdPhysicalParameters();
        this.evaluator = new qkd.physical.QKDPhysicalLayer(physicalParameters);
        QKDNetworkParameters np = mesh.getQkdNetworkParameters();
        this.spanGranularity = np.isSpanGranularity();
        this.undirected = np.isUndirectedChannels();
        this.tdmSlots = np.getTdmSlots();
        this.wavelengthGrid = np.isWavelengthGrid();
        this.wavelengthLanes = np.isWavelengthLanes();
        this.routeGranularity = np.isRouteGranularity();
        this.dedicatedQch = np.isDedicatedQch();
        if (mesh.getNodeList() != null)
            for (network.Node n : mesh.getNodeList()) networkNodeNames.add(n.getName());
    }

    // ------------------------------------------------------------------ Part 26: QTx / QRx

    /**
     * Each QKD span of an active QCh uses one QTx at its source node and one QRx at
     * its destination node (network node or in-line trusted node). A QCh shared by
     * several sessions (rate sharing or TDM) uses one QTx/QRx pair per span, as the
     * Q-TRx count of Dibaj et al. (2024).
     */
    private boolean equipmentAvailable(List<QKDSpan> spans) {
        QKDNetworkParameters np = mesh.getQkdNetworkParameters();
        if (!np.hasQEquipmentLimits()) return true;
        Map<String, int[]> need = new HashMap<String, int[]>();
        for (QKDSpan sp : spans) {
            int[] a = need.get(sp.getLogicalSource()); if (a == null) { a = new int[2]; need.put(sp.getLogicalSource(), a); } a[0]++;
            int[] b = need.get(sp.getLogicalDestination()); if (b == null) { b = new int[2]; need.put(sp.getLogicalDestination(), b); } b[1]++;
        }
        for (Map.Entry<String, int[]> e : need.entrySet()) {
            String node = e.getKey();
            boolean net = networkNodeNames.contains(node);
            long[] used = qEquipment.get(node);
            long tx = used == null ? 0 : used[0], rx = used == null ? 0 : used[1];
            int lt = np.getQTransmitterLimit(node, net), lr = np.getQReceiverLimit(node, net);
            if ((lt > 0 && tx + e.getValue()[0] > lt) || (lr > 0 && rx + e.getValue()[1] > lr)) {
                lastActivationFailure = "QTRX";
                lastActivationFailureNode = node;
                qEquipmentFailures++;
                return false;
            }
        }
        return true;
    }

    private void addEquipment(QKDSharedChannel c, int sign) {
        for (QKDSpan sp : c.getEvaluationPlan().getSpans()) {
            long[] a = equipment(sp.getLogicalSource());
            a[0] += sign; a[2] = Math.max(a[2], a[0]);
            long[] b = equipment(sp.getLogicalDestination());
            b[1] += sign; b[3] = Math.max(b[3], b[1]);
        }
    }

    private long[] equipment(String node) {
        long[] v = qEquipment.get(node);
        if (v == null) { v = new long[4]; qEquipment.put(node, v); }
        return v;
    }

    /** Samples the QTx/QRx in use (called once per arrival after the warm-up, PASTA). */
    public synchronized void sampleEquipment() {
        qEquipmentSamples++;
        for (Map.Entry<String, long[]> e : qEquipment.entrySet()) {
            double[] s = qEquipmentSums.get(e.getKey());
            if (s == null) { s = new double[2]; qEquipmentSums.put(e.getKey(), s); }
            s[0] += e.getValue()[0]; s[1] += e.getValue()[1];
        }
    }

    /** node -> {mean QTx, mean QRx, peak QTx, peak QRx, QTx limit, QRx limit, network node 1/0}. */
    public synchronized Map<String, double[]> equipmentStatistics() {
        Map<String, double[]> out = new java.util.TreeMap<String, double[]>();
        QKDNetworkParameters np = mesh.getQkdNetworkParameters();
        for (Map.Entry<String, long[]> e : qEquipment.entrySet()) {
            double[] s = qEquipmentSums.get(e.getKey());
            boolean net = networkNodeNames.contains(e.getKey());
            out.put(e.getKey(), new double[] {
                    qEquipmentSamples > 0 && s != null ? s[0] / qEquipmentSamples : 0.0,
                    qEquipmentSamples > 0 && s != null ? s[1] / qEquipmentSamples : 0.0,
                    e.getValue()[2], e.getValue()[3],
                    np.getQTransmitterLimit(e.getKey(), net), np.getQReceiverLimit(e.getKey(), net), net ? 1 : 0});
        }
        return out;
    }

    public long getQEquipmentFailures() { return qEquipmentFailures; }

    private String activationFailureReason(String what) {
        if ("QTRX".equals(lastActivationFailure))
            return "[QTRX] no free QTx/QRx at node " + lastActivationFailureNode + " for a new QCh (" + what + ")";
        if ("SPECTRUM".equals(lastActivationFailure))
            return "[SPECTRUM] no free QCh lane/band in the QKD core (" + what + ")";
        return "insufficient QKD key-generation capacity on " + what;
    }

    public synchronized QKDSharedAllocationResult allocate(Route route,
            QKDRoutePlan routePlan, double requiredKeyRateBps,
            int qchSlots, int guardBand, double wavelengthNm) {
        return allocate(route, routePlan, requiredKeyRateBps, qchSlots,
                guardBand, wavelengthNm, null);
    }

    public synchronized QKDSharedAllocationResult allocate(Route route,
            QKDRoutePlan routePlan, double requiredKeyRateBps,
            int qchSlots, int guardBand, double wavelengthNm,
            Circuit additionalClassicalCircuit) {
        if (route == null || routePlan == null || requiredKeyRateBps <= 0.0)
            return QKDSharedAllocationResult.failure("invalid QKD shared-capacity request");
        if (route.getLinkList().size() != routePlan.getPhysicalLinkCount())
            return QKDSharedAllocationResult.failure("route/QKD-plan link-count mismatch");
        lastActivationFailure = null;
        lastActivationFailureNode = null;
        if (routeGranularity)
            return allocateRouteContinuous(route, routePlan, requiredKeyRateBps, qchSlots,
                    guardBand, wavelengthNm, additionalClassicalCircuit);
        List<QKDLinkCapacityReservation> reservations =
                new ArrayList<QKDLinkCapacityReservation>();
        List<QKDSharedChannel> newlyActivated = new ArrayList<QKDSharedChannel>();
        List<QKDChannelShare> allShares = new ArrayList<QKDChannelShare>();
        try {
            for (int i = 0; i < route.getLinkList().size(); i++) {
                Link link = route.getLinkList().get(i);
                QKDLinkPlan plan = routePlan.getLinkPlans().get(i);
                if (spanGranularity) {
                    for (QKDSpan span : plan.getSpans()) {
                        Object key = poolKey(link, span);
                        List<QKDSharedChannel> pool = spanPools.get(key);
                        if (pool == null) { pool = new ArrayList<QKDSharedChannel>(); spanPools.put(key, pool); }
                        List<QKDChannelShare> shares = reserveOnPool(pool, link, plan, span, key,
                                requiredKeyRateBps, qchSlots, guardBand, wavelengthNm,
                                additionalClassicalCircuit, newlyActivated, allShares);
                        if (shares == null) {
                            failedCapacityReservations++;
                            rollback(allShares, newlyActivated, guardBand);
                            return QKDSharedAllocationResult.failure(activationFailureReason(
                                    "span " + span.getIndex() + " of link " + link.getName()));
                        }
                        reservations.add(new QKDLinkCapacityReservation(link, requiredKeyRateBps, shares));
                    }
                } else {
                    List<QKDSharedChannel> existing = getOrCreateChannelList(link);
                    List<QKDChannelShare> shares = reserveOnPool(existing, link, plan, null, null,
                            requiredKeyRateBps, qchSlots, guardBand, wavelengthNm,
                            additionalClassicalCircuit, newlyActivated, allShares);
                    if (shares == null) {
                        failedCapacityReservations++;
                        rollback(allShares, newlyActivated, guardBand);
                        return QKDSharedAllocationResult.failure(activationFailureReason(
                                "link " + link.getName()));
                    }
                    reservations.add(new QKDLinkCapacityReservation(link, requiredKeyRateBps, shares));
                }
            }
            Set<QKDChannelAllocation> unique = new LinkedHashSet<QKDChannelAllocation>();
            for (QKDLinkCapacityReservation lr : reservations)
                for (QKDChannelShare share : lr.getShares())
                    unique.add(share.getChannel().getAllocation());
            QKDSession session = QKDSession.forSharedCapacity(routePlan,
                    new ArrayList<QKDChannelAllocation>(unique), reservations, qchSlots);
            updatePeaks();
            return QKDSharedAllocationResult.success(session);
        } catch (Exception ex) {
            failedCapacityReservations++;
            rollback(allShares, newlyActivated, guardBand);
            return QKDSharedAllocationResult.failure("QKD shared allocation exception: " + ex.getMessage());
        }
    }

    /**
     * Reserves the demand on one pool (the channels of a link, or of a span).
     * Returns the shares, or null when the demand cannot be served.
     */
    private List<QKDChannelShare> reserveOnPool(List<QKDSharedChannel> pool, Link link,
            QKDLinkPlan plan, QKDSpan span, Object key, double demand, int qchSlots,
            int guardBand, double wavelengthNm, Circuit additional,
            List<QKDSharedChannel> newlyActivated, List<QKDChannelShare> allShares)
            throws Exception {
        List<QKDChannelShare> shares = new ArrayList<QKDChannelShare>();
        if (tdmSlots > 0) {
            for (QKDSharedChannel channel : (dedicatedQch ? java.util.Collections.<QKDSharedChannel>emptyList() : pool)) {
                double capacity = additional == null ? channel.getCapacityBps()
                        : projectedCapacity(channel, wavelengthNm, additional);
                int slots = QKDSharedChannel.slotsFor(demand, capacity, tdmSlots);
                if (slots <= tdmSlots && channel.getUsedSlots() + slots <= tdmSlots) {
                    channel.reserve(demand, slots);
                    QKDChannelShare share = new QKDChannelShare(channel, demand, slots);
                    logQch(channel.getAllocation());
                    shares.add(share); allShares.add(share);
                    reusedCapacityReservations++;
                    return shares;
                }
            }
            QKDSharedChannel channel = span == null
                    ? activateChannel(link, plan, qchSlots, guardBand, wavelengthNm, additional)
                    : activateSpanChannel(link, plan, span, key, pool, qchSlots, guardBand,
                            wavelengthNm, additional);
            if (channel != null) newlyActivated.add(channel);
            if (channel == null || channel.getCapacityBps() <= EPS) return null;
            int slots = QKDSharedChannel.slotsFor(demand, channel.getCapacityBps(), tdmSlots);
            if (slots > tdmSlots) return null;
            channel.reserve(demand, slots);
            QKDChannelShare share = new QKDChannelShare(channel, demand, slots);
            logQch(channel.getAllocation());
            shares.add(share); allShares.add(share);
            return shares;
        }
        double remaining = demand;
        for (QKDSharedChannel channel : (dedicatedQch ? java.util.Collections.<QKDSharedChannel>emptyList() : pool)) {
            if (remaining <= EPS) break;
            double available = channel.getResidualKeyRateBps();
            if (additional != null) {
                available = Math.max(0.0, projectedCapacity(channel, wavelengthNm, additional)
                        - channel.getUsedKeyRateBps());
            }
            double take = Math.min(remaining, available);
            if (take > EPS) {
                channel.reserve(take);
                QKDChannelShare share = new QKDChannelShare(channel, take);
                logQch(channel.getAllocation());
                shares.add(share); allShares.add(share);
                remaining -= take;
                reusedCapacityReservations++;
            }
        }
        while (remaining > EPS) {
            QKDSharedChannel channel = span == null
                    ? activateChannel(link, plan, qchSlots, guardBand, wavelengthNm, additional)
                    : activateSpanChannel(link, plan, span, key, pool, qchSlots, guardBand,
                            wavelengthNm, additional);
            if (channel != null) newlyActivated.add(channel);
            if (channel == null || channel.getCapacityBps() <= EPS) return null;
            double take = Math.min(remaining, channel.getResidualKeyRateBps());
            if (take <= EPS) return null;
            channel.reserve(take);
            QKDChannelShare share = new QKDChannelShare(channel, take);
            logQch(channel.getAllocation());
            shares.add(share); allShares.add(share);
            remaining -= take;
        }
        return shares;
    }

    private double projectedCapacity(QKDSharedChannel channel, double wavelengthNm, Circuit additional) {
        return evaluator.evaluateLinkChannelWithAdditionalClassicalCircuit(channel.getLink(),
                channel.getEvaluationPlan(), mesh.getPhysicalLayer(), channel.getAllocation(),
                channel.wavelengthOr(wavelengthNm), additional).getMinimumSecretKeyRateBps();
    }

    public synchronized boolean release(QKDSession session) throws Exception {
        if (session == null || !session.isActive()) return false;
        if (!session.isSharedCapacityManaged()) return false;
        Set<QKDSharedChannel> touched = new LinkedHashSet<QKDSharedChannel>();
        for (QKDLinkCapacityReservation lr : session.getCapacityReservations()) {
            for (QKDChannelShare share : lr.getShares()) {
                share.getChannel().release(share.getReservedKeyRateBps(), share.getReservedSlots());
                touched.add(share.getChannel());
            }
        }
        for (QKDSharedChannel c : touched) {
            if (c.isIdle()) deactivateChannel(c);
        }
        session.markReleased();
        return true;
    }

    public synchronized int refreshAllChannels(double wavelengthNm) {
        int count = 0;
        for (QKDSharedChannel c : allChannels) {
            c.setPhysicalResult(evaluator.evaluateLinkChannel(c.getLink(), c.getEvaluationPlan(),
                    mesh.getPhysicalLayer(), c.getAllocation(), c.wavelengthOr(wavelengthNm)));
            count++;
        }
        return count;
    }

    public synchronized QKDProjectedImpact evaluateAdditionalClassicalCircuit(
            Circuit candidate, double wavelengthNm, double maximumQber) {
        double worstMargin = Double.POSITIVE_INFINITY;
        QKDSharedChannel worst = null;
        QKDLinkChannelPhysicalResult projectedWorst = null;
        int checked = 0;
        for (QKDSharedChannel c : allChannels) {
            checked++;
            QKDLinkChannelPhysicalResult projected =
                    evaluator.evaluateLinkChannelWithAdditionalClassicalCircuit(
                            c.getLink(), c.getEvaluationPlan(), mesh.getPhysicalLayer(),
                            c.getAllocation(), c.wavelengthOr(wavelengthNm), candidate);
            double margin = projected.getMinimumSecretKeyRateBps() - c.getUsedKeyRateBps();
            if (margin < worstMargin) { worstMargin = margin; worst = c; projectedWorst = projected; }
            if (margin < -EPS || projected.getMaximumQber() > maximumQber) {
                return new QKDProjectedImpact(false, checked, c,
                        c.getCapacityBps(), projected.getMinimumSecretKeyRateBps(),
                        c.getUsedKeyRateBps(), projected.getMaximumQber());
            }
        }
        return new QKDProjectedImpact(true, checked, worst,
                worst == null ? 0.0 : worst.getCapacityBps(),
                projectedWorst == null ? 0.0 : projectedWorst.getMinimumSecretKeyRateBps(),
                worst == null ? 0.0 : worst.getUsedKeyRateBps(),
                projectedWorst == null ? 0.0 : projectedWorst.getMaximumQber());
    }

    // ------------------------------------------------------------------ route granularity (Part 23)

    /**
     * One dedicated QCh per QKD session, occupying the SAME contiguous slots of
     * the QKD core on every link (hence on every QTND span) of the QKD route.
     * Bands free on all links are scanned first-fit (lowest slot first); for
     * each band the SKR of every span is evaluated at the band wavelength
     * (wavelengthPolicy "grid": wavelength of the slot on the core grid,
     * centred at the attenuation minimum; "fixed": the reference wavelength)
     * with the current classical interference (plus the pending candidate
     * circuit, when given). The first band whose minimum span SKR meets the
     * key demand is allocated. Failure reasons start with [SPECTRUM] (no band
     * free on all links) or [SKR] (free bands exist but none has enough SKR).
     */
    private QKDSharedAllocationResult allocateRouteContinuous(Route route, QKDRoutePlan routePlan,
            double demand, int qchSlots, int guardBand, double defaultWl, Circuit additional) {
        final int coreId = mesh.getQkdNetworkParameters().getQuantumCoreId();
        final List<Link> links = route.getLinkList();
        int n = Integer.MAX_VALUE;
        for (Link link : links) {
            Core core = link.getCore(coreId);
            if (core == null || !core.isQkdReserved())
                return QKDSharedAllocationResult.failure("[SPECTRUM] QKD core unavailable on link " + link.getName());
            n = Math.min(n, core.getNumOfSlots());
        }
        boolean[] free = new boolean[n + 2];
        java.util.Arrays.fill(free, 1, n + 1, true);
        for (Link link : links) {
            boolean[] f = new boolean[n + 2];
            for (int[] b : link.getCore(coreId).getFreeSpectrumBandsForQKD(guardBand))
                for (int k = Math.max(1, b[0]); k <= Math.min(n, b[1]); k++) f[k] = true;
            for (int k = 1; k <= n; k++) free[k] = free[k] && f[k];
        }
        List<QKDSpan> allSpans = new ArrayList<QKDSpan>();
        for (QKDLinkPlan lp : routePlan.getLinkPlans()) allSpans.addAll(lp.getSpans());
        if (!equipmentAvailable(allSpans)) {
            failedCapacityReservations++;
            return QKDSharedAllocationResult.failure(activationFailureReason("route"));
        }
        int candidates = 0;
        double bestSeen = 0.0;
        List<Integer> starts = new ArrayList<Integer>();
        for (int start = 1; start + qchSlots - 1 <= n; start++) {
            boolean ok = true;
            for (int k = start; k < start + qchSlots; k++) if (!free[k]) { ok = false; break; }
            if (ok) starts.add(Integer.valueOf(start));
        }
        if (mesh.getQkdNetworkParameters().isLowestLossSlots()) {
            final Core ref = links.get(0).getCore(coreId);
            final int q = qchSlots;
            Collections.sort(starts, new Comparator<Integer>() {
                @Override public int compare(Integer a, Integer b) {
                    double la = physicalParameters.attenuationDbPerKm(slotWavelength(ref, new int[] {a, a + q - 1}, defaultWl));
                    double lb = physicalParameters.attenuationDbPerKm(slotWavelength(ref, new int[] {b, b + q - 1}, defaultWl));
                    int c = Double.compare(la, lb);
                    return c != 0 ? c : a.compareTo(b);
                }
            });
        }
        for (Integer startObj : starts) {
            final int start = startObj.intValue();
            candidates++;
            routeCandidateBandsEvaluated++;
            int[] band = new int[] {start, start + qchSlots - 1};
            double wl = slotWavelength(links.get(0).getCore(coreId), band, defaultWl);
            List<QKDLinkChannelPhysicalResult> results = new ArrayList<QKDLinkChannelPhysicalResult>();
            double minSkr = Double.POSITIVE_INFINITY;
            for (int i = 0; i < links.size(); i++) {
                Link link = links.get(i);
                QKDLinkPlan plan = routePlan.getLinkPlans().get(i);
                QKDChannelAllocation alloc = new QKDChannelAllocation(link, coreId, band, guardBand, plan.getSpanCount());
                logQch(alloc);
                QKDLinkChannelPhysicalResult r = additional == null
                        ? evaluator.evaluateLinkChannel(link, plan, mesh.getPhysicalLayer(), alloc, wl)
                        : evaluator.evaluateLinkChannelWithAdditionalClassicalCircuit(link, plan, mesh.getPhysicalLayer(),
                                alloc, wl, additional);
                minSkr = Math.min(minSkr, r.getMinimumSecretKeyRateBps());
                if (r.getMinimumSecretKeyRateBps() + EPS < demand) break;
                results.add(r);
            }
            if (results.size() < links.size()) { bestSeen = Math.max(bestSeen, minSkr); continue; }
            // commit the same band on every link of the route
            List<QKDSharedChannel> created = new ArrayList<QKDSharedChannel>();
            try {
                for (int i = 0; i < links.size(); i++) {
                    Link link = links.get(i);
                    Core core = link.getCore(coreId);
                    if (!core.useSpectrumForQKD(band, guardBand)) throw new IllegalStateException("band busy");
                    QKDLinkPlan plan = routePlan.getLinkPlans().get(i);
                    QKDChannelAllocation alloc = new QKDChannelAllocation(link, coreId, band, guardBand, plan.getSpanCount());
                    QKDSharedChannel c = new QKDSharedChannel(nextChannelId++, link, plan, plan, alloc,
                            results.get(i), -1, start, wavelengthGrid ? wl : Double.NaN, 0, null,
                            Collections.singletonList(link));
                    c.reserve(demand);
                    getOrCreateChannelList(link).add(c);
                    allChannels.add(c);
                    addEquipment(c, +1);
                    activatedChannels++;
                    created.add(c);
                }
            } catch (Exception ex) {
                for (QKDSharedChannel c : created) {
                    c.release(demand);
                    try { deactivateChannel(c); } catch (Exception ignored) { }
                }
                failedCapacityReservations++;
                return QKDSharedAllocationResult.failure("[SPECTRUM] QCh commit failed: " + ex.getMessage());
            }
            List<QKDLinkCapacityReservation> reservations = new ArrayList<QKDLinkCapacityReservation>();
            List<QKDChannelAllocation> allocations = new ArrayList<QKDChannelAllocation>();
            for (QKDSharedChannel c : created) {
                reservations.add(new QKDLinkCapacityReservation(c.getLink(), demand,
                        Collections.singletonList(new QKDChannelShare(c, demand))));
                allocations.add(c.getAllocation());
            }
            updatePeaks();
            return QKDSharedAllocationResult.success(
                    QKDSession.forSharedCapacity(routePlan, allocations, reservations, qchSlots));
        }
        failedCapacityReservations++;
        if (candidates == 0) {
            routeSpectrumFailures++;
            return QKDSharedAllocationResult.failure("[SPECTRUM] no " + qchSlots
                    + "-slot band free on every link of the QKD route");
        }
        routeSkrFailures++;
        return QKDSharedAllocationResult.failure("[SKR] none of the " + candidates
                + " free QCh bands reaches the key demand on every span (best min SKR="
                + String.format(Locale.US, "%.3f", bestSeen / 1e6) + " Mbit/s, demand="
                + String.format(Locale.US, "%.3f", demand / 1e6) + " Mbit/s)");
    }

    /** Wavelength of a band of the QKD core: slot grid centred at the attenuation minimum. */
    public double slotWavelength(Core core, int[] band, double defaultWl) {
        if (!wavelengthGrid || core == null) return defaultWl;
        final double c = physicalParameters.getSpeedOfLightMPerS();
        final double f0 = c / (physicalParameters.getAttenuationCenterNm() * 1e-9);
        final double mid = (band[0] + band[1]) / 2.0;
        final double centreSlot = (core.getNumOfSlots() + 1) / 2.0;
        final double f = f0 + (mid - centreSlot) * core.getSlotSpectrumBand();
        return c / f * 1e9;
    }

    public boolean isRouteGranularity() { return routeGranularity; }
    public long getRouteSpectrumFailures() { return routeSpectrumFailures; }
    public long getRouteSkrFailures() { return routeSkrFailures; }
    public long getRouteCandidateBandsEvaluated() { return routeCandidateBandsEvaluated; }

    // ------------------------------------------------------------------ link granularity

    private QKDSharedChannel activateChannel(Link link, QKDLinkPlan plan,
            int qchSlots, int guardBand, double wavelengthNm,
            Circuit additionalClassicalCircuit) throws Exception {
        int coreId = mesh.getQkdNetworkParameters().getQuantumCoreId();
        Core core = link.getCore(coreId);
        if (core == null || !core.isQkdReserved()) return null;
        if (!equipmentAvailable(plan.getSpans())) return null;
        int[] band = firstFit(core.getFreeSpectrumBandsForQKD(guardBand), qchSlots);
        if (band == null || !core.useSpectrumForQKD(band, guardBand)) { lastActivationFailure = "SPECTRUM"; return null; }
        QKDChannelAllocation allocation = new QKDChannelAllocation(
                link, coreId, band, guardBand, plan.getSpanCount());
        logQch(allocation);
        QKDLinkChannelPhysicalResult physical = additionalClassicalCircuit == null
                ? evaluator.evaluateLinkChannel(link, plan, mesh.getPhysicalLayer(), allocation, wavelengthNm)
                : evaluator.evaluateLinkChannelWithAdditionalClassicalCircuit(link, plan, mesh.getPhysicalLayer(),
                        allocation, wavelengthNm, additionalClassicalCircuit);
        QKDSharedChannel c;
        if (tdmSlots > 0) {
            c = new QKDSharedChannel(nextChannelId++, link, plan, plan, allocation, physical,
                    -1, 0, Double.NaN, tdmSlots, null, Collections.singletonList(link));
        } else {
            c = new QKDSharedChannel(nextChannelId++, link, plan, allocation, physical);
        }
        getOrCreateChannelList(link).add(c);
        allChannels.add(c);
        addEquipment(c, +1);
        activatedChannels++;
        updatePeaks();
        return c;
    }

    // ------------------------------------------------------------------ span granularity

    private Object poolKey(Link link, QKDSpan span) {
        if (undirected) {
            String a = link.getSource().getName(), b = link.getDestination().getName();
            String lo = a.compareTo(b) <= 0 ? a : b, hi = a.compareTo(b) <= 0 ? b : a;
            return "U|" + lo + "|" + hi + "|" + span.getIndex() + "|"
                    + String.format(Locale.US, "%.9f", span.getLengthKm());
        }
        return "D|" + link.getName() + "|" + span.getIndex();
    }

    private QKDLinkPlan singleSpanPlan(QKDLinkPlan plan, QKDSpan span) {
        String key = plan.getSource() + "->" + plan.getDestination() + "#" + span.getIndex()
                + "@" + plan.getQtndKm();
        QKDLinkPlan p = singleSpanPlans.get(key);
        if (p == null) {
            p = new QKDLinkPlan(plan.getSource(), plan.getDestination(), span.getLengthKm(),
                    plan.getQtndKm(), Collections.singletonList(span));
            singleSpanPlans.put(key, p);
        }
        return p;
    }

    private QKDSharedChannel activateSpanChannel(Link link, QKDLinkPlan plan, QKDSpan span,
            Object key, List<QKDSharedChannel> pool, int qchSlots, int guardBand,
            double wavelengthNm, Circuit additionalClassicalCircuit) throws Exception {
        int coreId = mesh.getQkdNetworkParameters().getQuantumCoreId();
        Core core = link.getCore(coreId);
        if (core == null || !core.isQkdReserved()) return null;
        if (!equipmentAvailable(java.util.Collections.singletonList(span))) return null;
        int maxLanes = core.getNumOfSlots() / Math.max(1, qchSlots + guardBand);
        Set<Integer> used = new LinkedHashSet<Integer>();
        for (QKDSharedChannel c : pool) used.add(Integer.valueOf(c.getLane()));
        int lane = 0;
        while (used.contains(Integer.valueOf(lane))) lane++;
        if (lane >= maxLanes) { lastActivationFailure = "SPECTRUM"; return null; }
        // SNetS spectrum slots are numbered 1..numOfSlots
        int start = wavelengthLanes ? laneStartAtWavelength(core, lane, maxLanes, qchSlots, guardBand)
                : 1 + lane * (qchSlots + guardBand);
        if (start < 1) { lastActivationFailure = "SPECTRUM"; return null; }
        int[] band = new int[] {start, start + qchSlots - 1};
        List<Link> links = new ArrayList<Link>();
        links.add(link);
        if (undirected) {
            Link reverse = mesh.getLink(link.getDestination().getName(), link.getSource().getName());
            if (reverse != null && reverse != link && reverse.getCore(coreId) != null
                    && reverse.getCore(coreId).isQkdReserved()) links.add(reverse);
        }
        List<Link> occupied = new ArrayList<Link>();
        for (Link l : links) {
            if (!occupyLane(l, coreId, lane, band, guardBand)) {
                for (Link o : occupied) freeLane(o, coreId, lane, band, guardBand);
                lastActivationFailure = "SPECTRUM";
                return null;
            }
            occupied.add(l);
        }
        double wl = wavelengthGrid ? gridWavelength(lane, maxLanes) : Double.NaN;
        QKDLinkPlan evalPlan = singleSpanPlan(plan, span);
        QKDChannelAllocation allocation = new QKDChannelAllocation(link, coreId, band, guardBand, 1);
        logQch(allocation);
        double evalWl = Double.isNaN(wl) ? wavelengthNm : wl;
        QKDLinkChannelPhysicalResult physical = additionalClassicalCircuit == null
                ? evaluator.evaluateLinkChannel(link, evalPlan, mesh.getPhysicalLayer(), allocation, evalWl)
                : evaluator.evaluateLinkChannelWithAdditionalClassicalCircuit(link, evalPlan, mesh.getPhysicalLayer(),
                        allocation, evalWl, additionalClassicalCircuit);
        QKDSharedChannel c = new QKDSharedChannel(nextChannelId++, link, plan, evalPlan, allocation,
                physical, span.getIndex(), lane, wl, tdmSlots, key, occupied);
        pool.add(c);
        allChannels.add(c);
        addEquipment(c, +1);
        activatedChannels++;
        updatePeaks();
        return c;
    }

    /**
     * spanLanePlacement = "wavelength": first slot of the lane whose centre
     * frequency is the lane's grid wavelength (lane 0 at the attenuation
     * minimum, i.e. mid-band, then alternating around it). Returns 0 when the
     * lane falls outside the core.
     */
    private int laneStartAtWavelength(Core core, int lane, int maxLanes, int qchSlots, int guardBand) {
        final double c = physicalParameters.getSpeedOfLightMPerS();
        final double f0 = c / (physicalParameters.getAttenuationCenterNm() * 1e-9);
        final double f = c / (gridWavelength(lane, maxLanes) * 1e-9);
        final double centreSlot = (core.getNumOfSlots() + 1) / 2.0;
        final double width = qchSlots + guardBand;
        final double offsetLanes = Math.rint((f - f0) / (core.getSlotSpectrumBand() * width));
        final int start = (int) Math.floor(centreSlot - (qchSlots - 1) / 2.0 + offsetLanes * width);
        if (start < 1 || start + qchSlots - 1 > core.getNumOfSlots()) return 0;
        return start;
    }

    private boolean occupyLane(Link link, int coreId, int lane, int[] band, int guardBand) throws Exception {
        Map<Integer, Integer> refs = laneRefs.get(link);
        if (refs == null) { refs = new HashMap<Integer, Integer>(); laneRefs.put(link, refs); }
        Integer n = refs.get(Integer.valueOf(lane));
        if (n == null || n.intValue() == 0) {
            Core core = link.getCore(coreId);
            if (band[1] > core.getNumOfSlots()) return false;
            if (!core.useSpectrumForQKD(band, guardBand)) return false;
            refs.put(Integer.valueOf(lane), Integer.valueOf(1));
        } else {
            refs.put(Integer.valueOf(lane), Integer.valueOf(n.intValue() + 1));
        }
        return true;
    }

    private void freeLane(Link link, int coreId, int lane, int[] band, int guardBand) throws Exception {
        Map<Integer, Integer> refs = laneRefs.get(link);
        if (refs == null) return;
        Integer n = refs.get(Integer.valueOf(lane));
        if (n == null) return;
        if (n.intValue() <= 1) {
            refs.remove(Integer.valueOf(lane));
            link.getCore(coreId).liberateSpectrumForQKD(band, guardBand);
        } else {
            refs.put(Integer.valueOf(lane), Integer.valueOf(n.intValue() - 1));
        }
    }

    /**
     * QCh wavelength grid of the reference reconstruction: channels spaced by
     * the QCh bandwidth around the attenuation minimum, ordered by attenuation
     * (then by distance to the centre, then by wavelength).
     */
    private double gridWavelength(int lane, int count) {
        if (wavelengthGridNm.size() < count) wavelengthGridNm = buildWavelengthGrid(count);
        return wavelengthGridNm.get(lane).doubleValue();
    }

    private List<Double> buildWavelengthGrid(int count) {
        final double c = physicalParameters.getSpeedOfLightMPerS();
        final double centerNm = physicalParameters.getAttenuationCenterNm();
        final double spacingHz = physicalParameters.getQchBandwidthHz();
        final double f0 = c / (centerNm * 1e-9);
        List<Integer> offsets = new ArrayList<Integer>();
        for (int k = 0; offsets.size() < count; k++) {
            if (k == 0) offsets.add(Integer.valueOf(0));
            else {
                offsets.add(Integer.valueOf(-k));
                if (offsets.size() < count) offsets.add(Integer.valueOf(k));
            }
        }
        List<Double> wavelengths = new ArrayList<Double>();
        for (Integer offset : offsets) {
            double frequency = f0 + offset.intValue() * spacingHz;
            wavelengths.add(Double.valueOf(c / frequency * 1e9));
        }
        Collections.sort(wavelengths, new Comparator<Double>() {
            @Override public int compare(Double a, Double b) {
                double aa = physicalParameters.attenuationDbPerKm(a.doubleValue());
                double ab = physicalParameters.attenuationDbPerKm(b.doubleValue());
                int x = Double.compare(aa, ab); if (x != 0) return x;
                x = Double.compare(Math.abs(a.doubleValue() - centerNm), Math.abs(b.doubleValue() - centerNm));
                if (x != 0) return x;
                return Double.compare(a.doubleValue(), b.doubleValue());
            }
        });
        return wavelengths;
    }

    // ------------------------------------------------------------------ common

    private void deactivateChannel(QKDSharedChannel channel) throws Exception {
        int coreId = channel.getAllocation().getQuantumCoreId();
        int[] band = channel.getAllocation().getSpectrumBand();
        int guard = channel.getAllocation().getGuardBand();
        if (channel.isSpanChannel()) {
            for (Link l : channel.getSpectrumLinks()) freeLane(l, coreId, channel.getLane(), band, guard);
            List<QKDSharedChannel> pool = spanPools.get(channel.getPoolKey());
            if (pool != null) { pool.remove(channel); if (pool.isEmpty()) spanPools.remove(channel.getPoolKey()); }
        } else {
            Core core = channel.getLink().getCore(coreId);
            core.liberateSpectrumForQKD(band, guard);
            List<QKDSharedChannel> list = channelsByLink.get(channel.getLink());
            if (list != null) { list.remove(channel); if (list.isEmpty()) channelsByLink.remove(channel.getLink()); }
        }
        if (allChannels.remove(channel)) addEquipment(channel, -1);
        deactivatedChannels++;
    }

    private void rollback(List<QKDChannelShare> shares,
            List<QKDSharedChannel> newlyActivated, int guardBand) {
        for (int i=shares.size()-1;i>=0;i--) {
            QKDChannelShare s=shares.get(i);
            s.getChannel().release(s.getReservedKeyRateBps(), s.getReservedSlots());
        }
        for (int i=newlyActivated.size()-1;i>=0;i--) {
            QKDSharedChannel c=newlyActivated.get(i);
            if (c.isIdle() && allChannels.contains(c)) try { deactivateChannel(c); } catch (Exception ignored) { }
        }
        shares.clear();
        newlyActivated.clear();
    }

    private List<QKDSharedChannel> getOrCreateChannelList(Link link) {
        List<QKDSharedChannel> list = channelsByLink.get(link);
        if (list == null) { list = new ArrayList<QKDSharedChannel>(); channelsByLink.put(link, list); }
        return list;
    }

    private static int[] firstFit(List<int[]> bands, int slots) {
        for (int[] b : bands) if (b[1]-b[0]+1 >= slots) return new int[]{b[0], b[0]+slots-1};
        return null;
    }

    private void updatePeaks() {
        peakActiveChannels = Math.max(peakActiveChannels, getActiveQuantumChannelCount());
        peakQTrxPairs = Math.max(peakQTrxPairs, getActiveQTrxPairs());
    }

    /**
     * Restarts the cumulative counters at the end of the warm-up period. Peaks
     * restart from the current occupancy, so they keep describing the
     * resources in use during the observation window.
     */
    public synchronized void resetStatistics() {
        activatedChannels = 0;
        deactivatedChannels = 0;
        reusedCapacityReservations = 0;
        failedCapacityReservations = 0;
        routeSpectrumFailures = 0;
        routeSkrFailures = 0;
        routeCandidateBandsEvaluated = 0;
        peakActiveChannels = getActiveQuantumChannelCount();
        peakQTrxPairs = getActiveQTrxPairs();
        for (long[] v : qEquipment.values()) { v[2] = v[0]; v[3] = v[1]; }
        qEquipmentSums.clear();
        qEquipmentSamples = 0;
        qEquipmentFailures = 0;
    }

    public synchronized qkd.physical.QKDRoutePhysicalResult buildSessionPhysicalSnapshot(
            QKDSession session) {
        if (session == null || !session.isSharedCapacityManaged())
            throw new IllegalArgumentException("shared-capacity session required");
        List<qkd.physical.QKDSpanPhysicalResult> spans =
                new ArrayList<qkd.physical.QKDSpanPhysicalResult>();
        for (QKDLinkCapacityReservation lr : session.getCapacityReservations()) {
            QKDSharedChannel representative = null;
            for (QKDChannelShare share : lr.getShares()) {
                QKDSharedChannel c = share.getChannel();
                if (representative == null || c.getCapacityBps() < representative.getCapacityBps())
                    representative = c;
            }
            if (representative == null)
                throw new IllegalStateException("link reservation without QCh");
            spans.addAll(representative.getPhysicalResult().getSpans());
        }
        return new qkd.physical.QKDRoutePhysicalResult(session.getRoutePlan(), spans);
    }

    public synchronized qkd.physical.QKDRoutePhysicalResult buildSessionPhysicalSnapshotWithAdditionalClassicalCircuit(
            QKDSession session, Circuit candidate, double wavelengthNm) {
        if (candidate == null) return buildSessionPhysicalSnapshot(session);
        if (session == null || !session.isSharedCapacityManaged())
            throw new IllegalArgumentException("shared-capacity session required");
        List<qkd.physical.QKDSpanPhysicalResult> spans = new ArrayList<qkd.physical.QKDSpanPhysicalResult>();
        for (QKDLinkCapacityReservation lr : session.getCapacityReservations()) {
            QKDLinkChannelPhysicalResult representative = null;
            Set<Long> seen = new LinkedHashSet<Long>();
            for (QKDChannelShare share : lr.getShares()) {
                QKDSharedChannel c = share.getChannel();
                if (!seen.add(c.getId())) continue;
                QKDLinkChannelPhysicalResult pr = evaluator.evaluateLinkChannelWithAdditionalClassicalCircuit(
                        c.getLink(), c.getEvaluationPlan(), mesh.getPhysicalLayer(), c.getAllocation(),
                        c.wavelengthOr(wavelengthNm), candidate);
                if (representative == null || pr.getMinimumSecretKeyRateBps() < representative.getMinimumSecretKeyRateBps())
                    representative = pr;
            }
            if (representative == null) throw new IllegalStateException("link reservation without QCh");
            spans.addAll(representative.getSpans());
        }
        return new qkd.physical.QKDRoutePhysicalResult(session.getRoutePlan(), spans);
    }

    public synchronized int getActiveQuantumChannelCount() {
        return allChannels.size();
    }

    public synchronized int getActiveQTrxPairs() {
        int total=0; for (QKDSharedChannel c:allChannels) total += c.getQTrxPairs(); return total;
    }

    public synchronized double getTotalReservedKeyRateBps() {
        double total=0; for (QKDSharedChannel c:allChannels) total += c.getUsedKeyRateBps(); return total;
    }

    public synchronized double getTotalAvailableKeyRateBps() {
        double total=0; for (QKDSharedChannel c:allChannels) total += c.getCapacityBps(); return total;
    }

    /** Number of distinct trusted-node sites (span boundaries) currently hosting at least one QCh. */
    public synchronized int getActiveSpanCount() {
        Set<Object> spans = new LinkedHashSet<Object>();
        for (QKDSharedChannel c : allChannels) {
            spans.add(c.isSpanChannel() ? c.getPoolKey() : c.getLink());
        }
        return spans.size();
    }

    public synchronized List<QKDSharedChannel> snapshotChannels() {
        return Collections.unmodifiableList(new ArrayList<QKDSharedChannel>(allChannels));
    }

    public boolean isSpanGranularity() { return spanGranularity; }
    public int getTdmSlots() { return tdmSlots; }
    public long getActivatedChannels(){return activatedChannels;}
    public long getDeactivatedChannels(){return deactivatedChannels;}
    public long getReusedCapacityReservations(){return reusedCapacityReservations;}
    public long getFailedCapacityReservations(){return failedCapacityReservations;}
    public long getPeakActiveChannels(){return peakActiveChannels;}
    public long getPeakQTrxPairs(){return peakQTrxPairs;}
}
