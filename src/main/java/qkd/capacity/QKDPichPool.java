package qkd.capacity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import network.Circuit;
import network.Core;
import network.Link;

/**
 * Part 26: public interaction channels (PICh) that follow the QCh.
 *
 * <p>The QCh of a QKD session is terminated at every node of the QKD route (network
 * nodes, and the in-line trusted nodes every QTND inside each link). The PICh carries
 * the public discussion (sifting, error correction, privacy amplification) of each of
 * those QKD links, so it ends at the same nodes. Because SNetS allocates spectrum per
 * physical link, a PICh here is one band of {@code pichSlots} slots on one classical
 * core of one physical link. All sessions whose QKD route uses that link share it,
 * as they share the QChs of the link, up to {@code pichSessionsPerChannel} sessions
 * (0 = unlimited). A new PICh is opened only when no open PICh of the link has room,
 * and a PICh is closed when its last session leaves.</p>
 *
 * <p>Modelo da Parte 26 (pedido do coorientador): o PICh acompanha o QCh e termina nos mesmos n\u00f3s.</p>
 *
 * <p>Refer\u00eancias: [MOGH21] E. E. Moghaddam, H. Beyranvand, J. A. Salehi, "Resource Allocation in SDM-EONs Secured With QKD", IEEE JSAC 39(9), 2021 (Sec. III-A e Eq. (22): QCh, CCh e DCh na mesma rota); [AHMED26a] I. Ahmed, B. C. Chatterjee, E. Oki, "QKD-Analytical: Analytical Model for Blocking Probabilities in Priority-Aware QKD over SDM-EONs", IEEE INFOCOM 2026 (canal cl\u00e1ssico QKD com banda de guarda); [CHAT26] B. C. Chatterjee, E. Oki, "Towards Secure and Flexible Optical Networks - A Tutorial on Quantum-Classical Coexistence", ICNC 2026.</p>
 */
public final class QKDPichPool implements Serializable {
    private static final long serialVersionUID = 1L;

    /** One open PICh on one physical link. */
    public static final class Channel implements Serializable {
        private static final long serialVersionUID = 1L;
        public final Link link;
        public final int coreId;
        public final int[] band;
        public final int guardBand;
        int sessions;

        Channel(Link link, int coreId, int[] band, int guardBand) {
            this.link = link; this.coreId = coreId; this.band = band; this.guardBand = guardBand;
        }

        public int getSessions() { return sessions; }
    }

    private final int pichSlots;
    private final int sessionsPerChannel;
    private final Map<Link, List<Channel>> byLink = new IdentityHashMap<Link, List<Channel>>();
    private int openChannels;
    private long peakOpenChannels;
    private long opened, closed;
    private double openSampleSum;
    private long samples;

    public QKDPichPool(int pichSlots, int sessionsPerChannel) {
        this.pichSlots = pichSlots;
        this.sessionsPerChannel = sessionsPerChannel;
    }

    /**
     * Joins (or opens) one PICh on every link of the QKD route. The slots of the
     * pending classical candidate are treated as busy when a new PICh is opened.
     * Returns the PIChs used, or null (nothing held) when some link has no room.
     */
    public List<Channel> acquire(List<Link> links, int guardBand, Circuit candidate) {
        List<Channel> got = new ArrayList<Channel>();
        for (Link l : links) {
            Channel c = joinOrOpen(l, guardBand, candidate);
            if (c == null) {
                release(got);
                return null;
            }
            got.add(c);
        }
        return got;
    }

    private Channel joinOrOpen(Link link, int guardBand, Circuit candidate) {
        List<Channel> list = byLink.get(link);
        if (list != null) {
            for (Channel c : list) {
                if (sessionsPerChannel <= 0 || c.sessions < sessionsPerChannel) {
                    c.sessions++;
                    return c;
                }
            }
        }
        for (Core core : link.getCores()) {
            if (core.isQkdReserved()) continue;
            int n = core.getNumOfSlots();
            boolean[] free = new boolean[n + 2];
            for (int[] b : core.getFreeSpectrumBands(guardBand))
                for (int k = Math.max(1, b[0]); k <= Math.min(n, b[1]); k++) free[k] = true;
            if (candidate != null && candidate.getSpectrumAssigned() != null && candidate.getRoute() != null
                    && rawCore(candidate) == core.getId() && traverses(candidate, link)) {
                int[] cb = candidate.getSpectrumAssigned();
                for (int k = Math.max(1, cb[0] - guardBand); k <= Math.min(n, cb[1] + guardBand); k++) free[k] = false;
            }
            for (int s = 1; s + pichSlots - 1 <= n; s++) {
                boolean ok = true;
                for (int k = s; k < s + pichSlots; k++) if (!free[k]) { ok = false; break; }
                if (!ok) continue;
                int[] band = new int[] {s, s + pichSlots - 1};
                try {
                    if (!core.useSpectrum(band, guardBand)) continue;
                } catch (Exception e) {
                    continue;
                }
                Channel c = new Channel(link, core.getId(), band, guardBand);
                c.sessions = 1;
                if (list == null) { list = new ArrayList<Channel>(); byLink.put(link, list); }
                list.add(c);
                openChannels++;
                opened++;
                peakOpenChannels = Math.max(peakOpenChannels, openChannels);
                return c;
            }
        }
        return null;
    }

    /** Leaves the given PIChs; a PICh without sessions is closed and its slots freed. */
    public void release(List<Channel> channels) {
        if (channels == null) return;
        for (Channel c : channels) {
            c.sessions--;
            if (c.sessions <= 0) {
                try { c.link.getCore(c.coreId).liberateSpectrum(c.band, c.guardBand); } catch (Exception ignored) { }
                List<Channel> list = byLink.get(c.link);
                if (list != null) { list.remove(c); if (list.isEmpty()) byLink.remove(c.link); }
                openChannels--;
                closed++;
            }
        }
        channels.clear();
    }

    private static int rawCore(Circuit c) {
        try {
            java.lang.reflect.Field f = Circuit.class.getDeclaredField("indexCore");
            f.setAccessible(true);
            return f.getInt(c);
        } catch (Exception e) {
            return -1;
        }
    }

    private static boolean traverses(Circuit c, Link link) {
        for (Link l : c.getRoute().getLinkList()) if (l == link) return true;
        return false;
    }

    public void sample() { samples++; openSampleSum += openChannels; }

    public void resetStatistics() {
        peakOpenChannels = openChannels; opened = 0; closed = 0; openSampleSum = 0; samples = 0;
    }

    public int getOpenChannels() { return openChannels; }
    public long getPeakOpenChannels() { return peakOpenChannels; }
    public double getMeanOpenChannels() { return samples > 0 ? openSampleSum / samples : Double.NaN; }
    public long getOpened() { return opened; }
    public long getClosed() { return closed; }
}
