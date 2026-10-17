package qkd.metrics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import network.Circuit;
import network.Core;
import network.ControlPlane;
import network.Link;
import request.RequestForConnection;

/**
 * Part 25: observation-only blocking details, written to
 * {@code <case>_BlockingDetails.csv}.
 *
 * <p>For every request it records, after the control plane decided:
 * the request class (classical or QKD-protected), the blocking cause, the core
 * of the blocked circuit and the links of the attempted route. It never changes
 * a decision. The cause list is the classical SNetS one, refined as follows:
 * <ul>
 *   <li>the classical "other" cause is split into <em>lack of free spectrum</em>
 *       (some link of the route has no free slot in the core, the SNetS
 *       definition) and <em>other</em>;</li>
 *   <li>requests whose classical candidate was valid but was rejected by the QKD
 *       addon (protection of active sessions, or the QKD precommit of a protected
 *       request) get a QKD cause instead of the classical "other".</li>
 * </ul>
 * The same transient rule as the classical metrics applies: the counters restart
 * once {@code warmupArrivals} requests have arrived.</p>
 *
 * <p>Refer\u00eancias: [SNETS17] A. Fontinele et al., "An Efficient IA-RMLSA Algorithm for Transparent Elastic Optical Networks", Comput. Netw. 118, 2017 (SNetS) (causas de bloqueio); [JAIN91] R. Jain, "The Art of Computer Systems Performance Analysis", Wiley, 1991 (descarte do transiente).</p>
 */
@SuppressWarnings("serial")
public final class BlockingDetailRecorder implements java.io.Serializable {

    public static final String C_FRAG = "Fragmentation";
    public static final String C_QOTN = "QoTN";
    public static final String C_QOTO = "QoTO";
    public static final String C_XT = "Crosstalk";
    public static final String C_XTO = "Crosstalk in other";
    public static final String C_TX = "Lack of transmitters";
    public static final String C_RX = "Lack of receivers";
    public static final String C_SPECTRUM = "Lack of free spectrum";
    public static final String C_OTHER = "Other";
    public static final String C_QKD_PROTECT = "QKD session protection";
    public static final String C_QKD_SPECTRUM = "QKD QCh spectrum";
    public static final String C_QKD_SKR = "QKD SKR";
    public static final String C_QKD_PICH = "QKD PICh";
    public static final String C_QKD_QTRX = "QKD QTx/QRx";
    public static final String C_QKD_OTHER = "QKD other";

    public static final String[] CAUSES = {C_FRAG, C_QOTN, C_QOTO, C_XT, C_XTO, C_TX, C_RX, C_SPECTRUM, C_OTHER,
            C_QKD_PROTECT, C_QKD_SPECTRUM, C_QKD_SKR, C_QKD_PICH, C_QKD_QTRX, C_QKD_OTHER};

    /*
     * Part 27: spectrum diagnosis of each blocked request, independent of the cause chosen by the
     * RMSCA algorithm. On the route of the blocked candidate, with the fewest slots any modulation
     * that reaches the route length needs, the best state over the classical cores is:
     */
    public static final String D_AVAILABLE = "Spectrum: free band available";
    public static final String D_CONTIGUITY = "Spectrum: contiguity fragmentation";
    public static final String D_CONTINUITY = "Spectrum: continuity fragmentation";
    public static final String D_NO_SLOTS = "Spectrum: not enough free slots";
    public static final String D_NO_ROUTE = "Spectrum: no route";
    public static final String D_QKD = "Spectrum: QKD side";
    public static final String[] DIAGNOSES = {D_AVAILABLE, D_CONTIGUITY, D_CONTINUITY, D_NO_SLOTS, D_NO_ROUTE, D_QKD};

    /** Request classes: 0 classical (not protected), 1 QKD-protected. */
    private static final int CLASSICAL = 0, QKD = 1;

    private final double warmupArrivals;
    private long arrivalsSeen;
    private boolean warmupDone;

    private final long[] attempts = new long[2];
    private final long[] blocked = new long[2];
    private final Map<String, long[]> blockedByCause = new LinkedHashMap<String, long[]>();
    private final Map<String, long[]> blockedByDiagnosis = new LinkedHashMap<String, long[]>();
    /** core -> requests whose final circuit used / tried that core (same rule as SNetS). */
    private final Map<Integer, Long> genPerCore = new TreeMap<Integer, Long>();
    /** core -> cause -> blocked requests. */
    private final Map<Integer, Map<String, Long>> blockedPerCore = new TreeMap<Integer, Map<String, Long>>();
    /** link -> {attempts classical, attempts qkd, blocked classical, blocked qkd, blocked by QKD causes}. */
    private final Map<String, long[]> perLink = new TreeMap<String, long[]>();
    private final List<String> linkNames = new ArrayList<String>();

    public BlockingDetailRecorder(double warmupArrivals, ControlPlane cp) {
        this.warmupArrivals = Math.max(0, warmupArrivals);
        this.warmupDone = this.warmupArrivals <= 0;
        for (String c : CAUSES) blockedByCause.put(c, new long[2]);
        for (String d : DIAGNOSES) blockedByDiagnosis.put(d, new long[2]);
        if (cp != null && cp.getMesh() != null) {
            for (Link l : cp.getMesh().getLinkList()) {
                String n = linkName(l);
                linkNames.add(n);
                perLink.put(n, new long[5]);
            }
        }
    }

    private static String linkName(Link l) {
        return l.getSource().getName() + "-" + l.getDestination().getName();
    }

    private void reset() {
        attempts[0] = attempts[1] = blocked[0] = blocked[1] = 0;
        for (long[] v : blockedByCause.values()) v[0] = v[1] = 0;
        for (long[] v : blockedByDiagnosis.values()) v[0] = v[1] = 0;
        genPerCore.clear();
        blockedPerCore.clear();
        for (long[] v : perLink.values()) java.util.Arrays.fill(v, 0);
    }

    /** Called at each arrival, before the request is handled (mirrors the classical transient rule). */
    public void arrival() {
        if (!warmupDone && arrivalsSeen >= warmupArrivals) {
            warmupDone = true;
            reset();
        }
        arrivalsSeen++;
    }

    /**
     * Records one handled request.
     *
     * @param qkdCause a QKD cause constant when the QKD addon rejected the request, otherwise null
     */
    public void observe(ControlPlane cp, RequestForConnection request, boolean success, String qkdCause) {
        if (request == null) return;
        int cls = request.isQkdProtected() ? QKD : CLASSICAL;
        attempts[cls]++;

        Circuit blockedCircuit = null;
        Circuit any = null;
        if (request.getCircuits() != null) {
            for (Circuit c : request.getCircuits()) {
                if (any == null) any = c;
                if (!success && c.isWasBlocked() && blockedCircuit == null) blockedCircuit = c;
            }
        }
        Circuit ref = blockedCircuit != null ? blockedCircuit : any;
        int core = ref == null ? -1 : rawCore(ref);
        if (core >= 0) genPerCore.put(core, genPerCore.containsKey(core) ? genPerCore.get(core) + 1 : 1L);

        // A QKD rejection only explains the block when the classical diagnosis found
        // no physical cause (the candidate itself was valid), i.e. SNetS "other".
        if (qkdCause != null && blockedCircuit != null && blockedCircuit.getBlockCause() != Circuit.BY_OTHER) {
            qkdCause = null;
        }
        String cause = null;
        if (!success) {
            blocked[cls]++;
            cause = qkdCause != null ? qkdCause : classicalCause(cp, blockedCircuit);
            blockedByCause.get(cause)[cls]++;
            String diag;
            try {
                diag = blockedCircuit == null ? (qkdCause != null ? D_QKD : D_NO_ROUTE) : spectrumDiagnosis(cp, blockedCircuit);
            } catch (RuntimeException e) {
                diag = D_NO_ROUTE;
            }
            if (qkdCause != null && D_AVAILABLE.equals(diag)) diag = D_QKD;
            blockedByDiagnosis.get(diag)[cls]++;
            if (core >= 0) {
                Map<String, Long> m = blockedPerCore.get(core);
                if (m == null) { m = new LinkedHashMap<String, Long>(); blockedPerCore.put(core, m); }
                m.put(cause, m.containsKey(cause) ? m.get(cause) + 1 : 1L);
            }
        }

        if (ref != null && ref.getRoute() != null) {
            for (Link l : ref.getRoute().getLinkList()) {
                long[] v = perLink.get(linkName(l));
                if (v == null) continue;
                v[cls]++;
                if (!success) {
                    v[2 + cls]++;
                    if (qkdCause != null) v[4]++;
                }
            }
        }
    }

    /**
     * Part 27: why the spectrum could not hold the blocked request on its route. Uses the free
     * spectrum views of the classical cores (guard bands of the request included), as the
     * fragmentation test of SNetS does, but over every classical core and without depending on the
     * order of the tests of the algorithm.
     */
    static String spectrumDiagnosis(ControlPlane cp, Circuit c) {
        gprmcsa.routing.Route route = c.getRoute();
        if (route == null || route.getLinkList() == null || route.getLinkList().isEmpty()) return D_NO_ROUTE;
        int need = Integer.MAX_VALUE;
        double dist = route.getDistanceAllLinks();
        gprmcsa.modulation.Modulation longest = null;
        for (gprmcsa.modulation.Modulation m : cp.getMesh().getAvaliableModulations()) {
            if (longest == null || m.getMaxRange() > longest.getMaxRange()) longest = m;
            if (m.getMaxRange() >= dist) need = Math.min(need, m.requiredSlots(c.getRequiredBitRate()));
        }
        if (need == Integer.MAX_VALUE && longest != null) need = longest.requiredSlots(c.getRequiredBitRate());
        if (need == Integer.MAX_VALUE || need <= 0) return D_NO_ROUTE;
        int gb = c.getGuardBand();
        Link first = route.getLinkList().get(0);
        int best = 0; // 0 no slots, 1 continuity, 2 contiguity, 3 available
        for (Core core : first.getCores()) {
            if (core.isQkdReserved()) continue;
            int id = core.getId();
            boolean eachLink = true;
            for (Link l : route.getLinkList()) {
                Core lc = l.getCore(id);
                if (lc == null || lc.isQkdReserved()) { eachLink = false; break; }
                int free = 0;
                for (int[] b : lc.getFreeSpectrumBands(gb)) free += b[1] - b[0] + 1;
                if (free < need) { eachLink = false; break; }
            }
            if (!eachLink) continue;
            int total = 0, longestBand = 0;
            for (int[] b : util.IntersectionFreeSpectrum.merge(route, gb, id)) {
                int w = b[1] - b[0] + 1;
                total += w;
                longestBand = Math.max(longestBand, w);
            }
            int state = longestBand >= need ? 3 : total >= need ? 2 : 1;
            best = Math.max(best, state);
            if (best == 3) break;
        }
        return best == 3 ? D_AVAILABLE : best == 2 ? D_CONTIGUITY : best == 1 ? D_CONTINUITY : D_NO_SLOTS;
    }

    private static int rawCore(Circuit c) {
        // Circuit.getIndexCore() prints an error when no core was selected; read it quietly.
        try {
            java.lang.reflect.Field f = Circuit.class.getDeclaredField("indexCore");
            f.setAccessible(true);
            return f.getInt(c);
        } catch (Exception e) {
            return -1;
        }
    }

    private static String classicalCause(ControlPlane cp, Circuit c) {
        if (c == null) return C_OTHER;
        switch (c.getBlockCause()) {
            case Circuit.BY_FRAGMENTATION: return C_FRAG;
            case Circuit.BY_QOTN: return C_QOTN;
            case Circuit.BY_QOTO: return C_QOTO;
            case Circuit.BY_XTN: return C_XT;
            case Circuit.BY_XTO: return C_XTO;
            case Circuit.BY_LACK_TX: return C_TX;
            case Circuit.BY_LACK_RX: return C_RX;
            default:
                try {
                    if (rawCore(c) >= 0 && cp.isBlockingByLackOfFreeSpectrum(c)) return C_SPECTRUM;
                } catch (Exception ignored) {
                    // fall through
                }
                return C_OTHER;
        }
    }

    /** Immutable copy of the counters of one replication. */
    public Snapshot snapshot() {
        Snapshot s = new Snapshot();
        s.attempts = attempts.clone();
        s.blocked = blocked.clone();
        for (Map.Entry<String, long[]> e : blockedByCause.entrySet()) s.blockedByCause.put(e.getKey(), e.getValue().clone());
        for (Map.Entry<String, long[]> e : blockedByDiagnosis.entrySet()) s.blockedByDiagnosis.put(e.getKey(), e.getValue().clone());
        s.genPerCore.putAll(genPerCore);
        for (Map.Entry<Integer, Map<String, Long>> e : blockedPerCore.entrySet())
            s.blockedPerCore.put(e.getKey(), new LinkedHashMap<String, Long>(e.getValue()));
        for (Map.Entry<String, long[]> e : perLink.entrySet()) s.perLink.put(e.getKey(), e.getValue().clone());
        return s;
    }

    @SuppressWarnings("serial")
    public static final class Snapshot implements java.io.Serializable {
        public long[] attempts;
        public long[] blocked;
        public final Map<String, long[]> blockedByCause = new LinkedHashMap<String, long[]>();
        public final Map<String, long[]> blockedByDiagnosis = new LinkedHashMap<String, long[]>();
        public final Map<Integer, Long> genPerCore = new TreeMap<Integer, Long>();
        public final Map<Integer, Map<String, Long>> blockedPerCore = new TreeMap<Integer, Map<String, Long>>();
        public final Map<String, long[]> perLink = new TreeMap<String, long[]>();
    }

    // ------------------------------------------------------------------ CSV

    private interface V { double of(Snapshot s); }

    private static double ratio(double a, double b) { return b > 0 ? a / b : Double.NaN; }

    /**
     * SNetS layout: {@code Metrics,LoadPoint,Class,Core,Cause,Link, ,rep0..}.
     * snaps.get(loadPoint).get(replication); returns null when nothing was recorded.
     */
    public static String toCsv(List<List<Snapshot>> snaps) {
        int reps = 0;
        java.util.TreeSet<Integer> cores = new java.util.TreeSet<Integer>();
        java.util.TreeSet<String> links = new java.util.TreeSet<String>();
        boolean anyQkd = false;
        for (List<Snapshot> row : snaps) {
            reps = Math.max(reps, row.size());
            for (Snapshot s : row) {
                if (s == null) continue;
                cores.addAll(s.genPerCore.keySet());
                cores.addAll(s.blockedPerCore.keySet());
                links.addAll(s.perLink.keySet());
                if (s.attempts[QKD] > 0) anyQkd = true;
            }
        }
        if (reps == 0) return null;
        StringBuilder sb = new StringBuilder("Metrics,LoadPoint,Class,Core,Cause,Link, ");
        for (int r = 0; r < reps; r++) sb.append(",rep").append(r);
        sb.append('\n');
        String[] classes = anyQkd ? new String[] {"all", "classical", "qkd"} : new String[] {"all"};

        for (String cl : classes) {
            final int k = cl.equals("classical") ? CLASSICAL : cl.equals("qkd") ? QKD : -1;
            row(sb, snaps, "Blocking probability", cl, "all", "all", "all",
                    s -> k < 0 ? ratio(s.blocked[0] + s.blocked[1], s.attempts[0] + s.attempts[1]) : ratio(s.blocked[k], s.attempts[k]));
            row(sb, snaps, "Requests", cl, "all", "all", "all",
                    s -> k < 0 ? s.attempts[0] + s.attempts[1] : s.attempts[k]);
            for (String c : CAUSES) {
                if (!anyQkd && c.startsWith("QKD")) continue;
                row(sb, snaps, "Blocking probability by cause", cl, "all", c, "all", s -> {
                    long[] b = s.blockedByCause.get(c);
                    return k < 0 ? ratio(b[0] + b[1], s.attempts[0] + s.attempts[1]) : ratio(b[k], s.attempts[k]);
                });
            }
            for (String d : DIAGNOSES) {
                if (!anyQkd && d.equals(D_QKD)) continue;
                row(sb, snaps, "Blocking probability by spectrum diagnosis", cl, "all", d, "all", s -> {
                    long[] b = s.blockedByDiagnosis.get(d);
                    if (b == null) return Double.NaN;
                    return k < 0 ? ratio(b[0] + b[1], s.attempts[0] + s.attempts[1]) : ratio(b[k], s.attempts[k]);
                });
            }
        }
        for (Integer core : cores) {
            String cs = String.valueOf(core);
            row(sb, snaps, "Blocking probability per core", "all", cs, "all", "all",
                    s -> ratio(sumCore(s, core, null), nz(s.genPerCore.get(core))));
            for (String c : CAUSES) {
                if (!anyQkd && c.startsWith("QKD")) continue;
                row(sb, snaps, "Blocking probability per core by cause", "all", cs, c, "all",
                        s -> ratio(sumCore(s, core, c), nz(s.genPerCore.get(core))));
            }
            for (String c : CAUSES) {
                if (!anyQkd && c.startsWith("QKD")) continue;
                row(sb, snaps, "Share of all requests blocked per core by cause", "all", cs, c, "all",
                        s -> ratio(sumCore(s, core, c), s.attempts[0] + s.attempts[1]));
            }
        }
        for (String link : links) {
            String ln = "<" + link + ">";
            for (String cl : classes) {
                final int k = cl.equals("classical") ? CLASSICAL : cl.equals("qkd") ? QKD : -1;
                row(sb, snaps, "Link blocking probability", cl, "all", "all", ln, s -> {
                    long[] v = s.perLink.get(link);
                    if (v == null) return Double.NaN;
                    return k < 0 ? ratio(v[2] + v[3], v[0] + v[1]) : ratio(v[2 + k], v[k]);
                });
                row(sb, snaps, "Link requests", cl, "all", "all", ln, s -> {
                    long[] v = s.perLink.get(link);
                    if (v == null) return Double.NaN;
                    return k < 0 ? v[0] + v[1] : v[k];
                });
            }
            if (anyQkd) {
                row(sb, snaps, "Link blocking probability by QKD causes", "all", "all", "all", ln, s -> {
                    long[] v = s.perLink.get(link);
                    return v == null ? Double.NaN : ratio(v[4], v[0] + v[1]);
                });
            }
        }
        return sb.toString();
    }

    private static double nz(Long v) { return v == null ? 0 : v; }

    private static double sumCore(Snapshot s, int core, String cause) {
        Map<String, Long> m = s.blockedPerCore.get(core);
        if (m == null) return 0;
        if (cause != null) return nz(m.get(cause));
        double t = 0;
        for (Long v : m.values()) t += v;
        return t;
    }

    private static void row(StringBuilder sb, List<List<Snapshot>> snaps, String metric, String cl, String core,
            String cause, String link, V v) {
        for (int lp = 0; lp < snaps.size(); lp++) {
            sb.append(metric).append(',').append(lp).append(',').append(cl).append(',').append(core).append(',')
                    .append(cause).append(',').append(link).append(", ");
            for (Snapshot s : snaps.get(lp)) sb.append(',').append(s == null ? Double.NaN : v.of(s));
            sb.append('\n');
        }
    }
}
