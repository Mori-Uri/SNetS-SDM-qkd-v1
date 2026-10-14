package qkd.analytical;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Allocation rules of QKD-Analytical (I. Ahmed, B. C. Chatterjee, E. Oki, "QKD-Analytical:
 * Analytical Model for Blocking Probabilities in Priority-Aware Quantum Key Distribution
 * over Space Division Multiplexed Elastic Optical Networks", IEEE INFOCOM 2026,
 * docs/artigos) for one link of a 7-core fibre.
 *
 * <p>Core 7 (the centre, highest degree in the auxiliary graph, Algorithm 1 lines 3-7) is
 * dedicated to quantum channels (QCh). Cores 1-6 form a ring and carry the data channel
 * (DCh, f_dc contiguous slots) and the classical QKD channel (CCh, 1 slot) of each
 * request. The rules below were reconstructed from Sec. III-A and Fig. 2 of the paper; they
 * reproduce exactly its example (|C| = 7, |W| = 4, |D| = 1, f = (2, 1, 1)): 1200 successors
 * of the empty state, 34193 states, and the rates lambda/162 (state t = 2) and lambda/192
 * (state t = 10). See QKDAnalyticalVerification and ladder step QA1.</p>
 * <ol>
 *   <li>sigma (IC-XT / FWM avoided slot): slot w of a ring core is unusable when a
 *       neighbouring ring core has a DCh or CCh slot at w. The quantum core is not
 *       restricted (it carries no classical power).</li>
 *   <li>kappa (guard band of the CCh): a free slot between a CCh and a DCh of the same core
 *       at distance two is a guard slot. A CCh never touches a DCh slot directly.</li>
 *   <li>A CCh may not use slot w if a neighbouring ring core has a CCh or a guard slot at w,
 *       and a guard slot may not coincide with a CCh of a neighbouring core.</li>
 *   <li>The QCh uses any free slot of the quantum core.</li>
 * </ol>
 *
 * <p>State: label per slot of the six ring cores (0 free, 1..D data of class d, CC = CCh)
 * and the occupancy of the quantum core. sigma and kappa are derived from the labels, as
 * in the paper (they are shown, but are not independent state variables).</p>
 *
 * <p>Refer\u00eancias: [AHMED26a] I. Ahmed, B. C. Chatterjee, E. Oki, "QKD-Analytical: Analytical Model for Blocking Probabilities in Priority-Aware QKD over SDM-EONs", IEEE INFOCOM 2026; [AHMED26b] I. Ahmed, B. C. Chatterjee, E. Oki, "AnaQKD: Analytical Modeling of Blocking Probability with QKD in SS-EONs", IEEE ICC 2026.</p>
 */
public final class QKDAnalyticalModel {

    public static final byte FREE = 0;
    public static final byte CC = 7;
    static final int RING = 6;

    final int w;
    final int[] fdc;

    public QKDAnalyticalModel(int slotsPerCore, int[] dataSlotsPerClass) {
        if (slotsPerCore < 2 || slotsPerCore > 10) throw new IllegalArgumentException("slotsPerCore must be in [2,10]");
        if (dataSlotsPerClass.length < 1 || dataSlotsPerClass.length > 6) throw new IllegalArgumentException("1..6 classes");
        this.w = slotsPerCore;
        this.fdc = dataSlotsPerClass.clone();
    }

    public int slots() { return w; }
    public int classes() { return fdc.length; }

    /** Immutable state: ring labels (6 x W) + quantum-core bit mask. */
    public static final class State {
        final byte[] ring;
        final int quantum;
        private final int hash;

        State(byte[] ring, int quantum) {
            this.ring = ring;
            this.quantum = quantum;
            this.hash = 31 * Arrays.hashCode(ring) + quantum;
        }

        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof State)) return false;
            State s = (State) o;
            return s.quantum == quantum && Arrays.equals(s.ring, ring);
        }

        public int requests() { return Integer.bitCount(quantum); }
    }

    public State empty() { return new State(new byte[RING * w], 0); }

    static int left(int c) { return (c + RING - 1) % RING; }
    static int right(int c) { return (c + 1) % RING; }

    private byte at(byte[] r, int c, int s) { return r[c * w + s]; }

    private boolean signal(byte v) { return v != FREE; }

    /** Neighbour ring core has a DCh or CCh at slot s. */
    private boolean sigma(byte[] r, int c, int s) {
        return signal(at(r, left(c), s)) || signal(at(r, right(c), s));
    }

    /** Free slot between a CCh and a DCh of the same core at distance two. */
    private boolean kappa(byte[] r, int c, int s) {
        if (at(r, c, s) != FREE) return false;
        if (s - 1 < 0 || s + 1 >= w) return false;
        byte a = at(r, c, s - 1), b = at(r, c, s + 1);
        return (a == CC && isData(b)) || (b == CC && isData(a));
    }

    private static boolean isData(byte v) { return v != FREE && v != CC; }

    private boolean usable(byte[] r, int c, int s) {
        return at(r, c, s) == FREE && !sigma(r, c, s) && !kappa(r, c, s);
    }

    /** Rules 2-3 on the whole ring after a placement. */
    private boolean consistent(byte[] r) {
        for (int c = 0; c < RING; c++) {
            for (int s = 0; s < w; s++) {
                byte v = at(r, c, s);
                if (v == CC) {
                    if (s > 0 && isData(at(r, c, s - 1))) return false;
                    if (s + 1 < w && isData(at(r, c, s + 1))) return false;
                }
                if (kappa(r, c, s) && (at(r, left(c), s) == CC || at(r, right(c), s) == CC)) return false;
            }
        }
        return true;
    }

    /**
     * Every distinct state reachable by one arrival of class d, in first-fit order
     * (DCh core/slot ascending, then CCh core/slot, then QCh slot). The first element is
     * the CS-FF choice; CS-RF picks uniformly among all of them.
     */
    public List<State> arrivals(State st, int d) {
        Set<State> out = new LinkedHashSet<State>();
        for (int[] p : placements(st, d)) out.add(apply(st, d, p));
        return new ArrayList<State>(out);
    }

    /** Applies a placement {data core, first data slot, CCh core, CCh slot, QCh slot}. */
    public State apply(State st, int d, int[] p) {
        byte[] r = st.ring.clone();
        for (int s = p[1]; s < p[1] + fdc[d]; s++) r[p[0] * w + s] = (byte) (d + 1);
        r[p[2] * w + p[3]] = CC;
        return new State(r, st.quantum | (1 << p[4]));
    }

    /** Removes the resources of one request (placement p of class d). */
    public State remove(State st, int d, int[] p) {
        byte[] r = st.ring.clone();
        for (int s = p[1]; s < p[1] + fdc[d]; s++) r[p[0] * w + s] = FREE;
        r[p[2] * w + p[3]] = FREE;
        return new State(r, st.quantum & ~(1 << p[4]));
    }

    /**
     * Every feasible placement {data core, first data slot, CCh core, CCh slot, QCh slot}
     * of a class-d request, in first-fit order (DCh core/slot ascending, then CCh
     * core/slot, then QCh slot). The first one is the CS-FF choice.
     */
    public List<int[]> placements(State st, int d) {
        List<int[]> out = new ArrayList<int[]>();
        final int f = fdc[d];
        final byte label = (byte) (d + 1);
        byte[] r = st.ring;
        for (int c = 0; c < RING; c++) {
            for (int s0 = 0; s0 + f <= w; s0++) {
                boolean ok = true;
                for (int s = s0; s < s0 + f && ok; s++) ok = usable(r, c, s);
                if (!ok) continue;
                if (s0 > 0 && at(r, c, s0 - 1) == CC) continue;
                if (s0 + f < w && at(r, c, s0 + f) == CC) continue;
                byte[] r1 = r.clone();
                for (int s = s0; s < s0 + f; s++) r1[c * w + s] = label;
                for (int c2 = 0; c2 < RING; c2++) {
                    for (int s2 = 0; s2 < w; s2++) {
                        if (!usable(r1, c2, s2)) continue;
                        if (s2 > 0 && isData(at(r1, c2, s2 - 1))) continue;
                        if (s2 + 1 < w && isData(at(r1, c2, s2 + 1))) continue;
                        if (kappa(r1, left(c2), s2) || kappa(r1, right(c2), s2)) continue;
                        byte[] r2 = r1.clone();
                        r2[c2 * w + s2] = CC;
                        if (!consistent(r2)) continue;
                        for (int q = 0; q < w; q++) {
                            if ((st.quantum & (1 << q)) != 0) continue;
                            out.add(new int[] {c, s0, c2, s2, q});
                        }
                    }
                }
            }
        }
        return out;
    }

    /** True when a class-d request finds no placement (blocking state of class d). */
    public boolean blocked(State st, int d) {
        return placements(st, d).isEmpty();
    }

    /**
     * Distinct states reachable by the departure of one class-d request: any DCh block of
     * class d, any CCh and any QCh of the state are removed together (the labels do not say
     * which resources belong to the same request, as in the paper).
     */
    public List<State> departures(State st, int d) {
        Set<State> out = new LinkedHashSet<State>();
        final int f = fdc[d];
        final byte label = (byte) (d + 1);
        byte[] r = st.ring;
        List<int[]> blocks = new ArrayList<int[]>();
        List<int[]> ccs = new ArrayList<int[]>();
        for (int c = 0; c < RING; c++) {
            int s = 0;
            while (s < w) {
                if (at(r, c, s) == label) {
                    int e = s;
                    while (e < w && at(r, c, e) == label) e++;
                    if ((e - s) % f == 0) for (int b = s; b < e; b += f) blocks.add(new int[] {c, b});
                    s = e;
                } else {
                    if (at(r, c, s) == CC) ccs.add(new int[] {c, s});
                    s++;
                }
            }
        }
        for (int[] b : blocks) for (int[] k : ccs) for (int q = 0; q < w; q++) {
            if ((st.quantum & (1 << q)) == 0) continue;
            byte[] r2 = r.clone();
            for (int s = b[1]; s < b[1] + f; s++) r2[b[0] * w + s] = FREE;
            r2[k[0] * w + k[1]] = FREE;
            out.add(new State(r2, st.quantum & ~(1 << q)));
        }
        return new ArrayList<State>(out);
    }

    /** Number of class-d requests (data slots of class d / f_d). */
    public int requestsOfClass(State st, int d) {
        int n = 0;
        for (byte v : st.ring) if (v == d + 1) n++;
        return n / fdc[d];
    }

    /** Readable form, as Fig. 2 of the paper (cores 1..6 then core 7). */
    public String format(State st) {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < RING; c++) {
            for (int s = 0; s < w; s++) {
                byte v = at(st.ring, c, s);
                sb.append(v == CC ? "2" : isData(v) ? "1" : sigma(st.ring, c, s) ? "s" : kappa(st.ring, c, s) ? "k" : "0");
                if (s + 1 < w) sb.append(',');
            }
            sb.append(" / ");
        }
        for (int s = 0; s < w; s++) { sb.append((st.quantum & (1 << s)) != 0 ? "3" : "0"); if (s + 1 < w) sb.append(','); }
        return sb.toString();
    }

    /** Builds a state from 6 ring rows ('1' data class 1, '2' CCh, other = free) and a quantum mask. */
    public State state(String[] rows, int quantumMask) {
        byte[] r = new byte[RING * w];
        for (int c = 0; c < RING; c++) for (int s = 0; s < w; s++) {
            char ch = rows[c].charAt(s);
            r[c * w + s] = ch == '1' ? 1 : ch == '2' ? CC : FREE;
        }
        return new State(r, quantumMask);
    }
}
