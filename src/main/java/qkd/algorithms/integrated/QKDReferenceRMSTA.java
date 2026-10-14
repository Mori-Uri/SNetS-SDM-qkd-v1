package qkd.algorithms.integrated;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import gprmcsa.integrated.IntegratedRMLSAAlgorithmInterface;
import gprmcsa.modulation.Modulation;
import gprmcsa.routing.KRoutingAlgorithmInterface;
import gprmcsa.routing.Route;
import network.Circuit;
import network.ControlPlane;
import network.Core;
import network.Link;
import network.Mesh;
import network.Node;
import util.IntersectionFreeSpectrum;

/**
 * Classical part of the RMSTA heuristic of Dibaj et al. (2024), Fig. 4, as
 * reconstructed in the qualification (Section 4.4.2): K shortest paths,
 * distance-adaptive modulation, first-fit on a fixed grid of classical
 * channels and choice of the path whose allocation ends at the lowest channel
 * index. Name in the simulation file: {@code "qrmsta"} (QKD addon only).
 *
 * <p>Parameters read from the {@code others} file (variables):</p>
 * <ul>
 * <li>{@code k} - number of candidate paths (default 2);</li>
 * <li>{@code classicalChannelGHz} - width of one classical channel (default
 * 75; with 12.5 GHz slots one channel = 6 slots);</li>
 * <li>{@code reservePichChannel} - keep the first channel of every classical
 * fiber/core for the public interaction channel (default true);</li>
 * <li>{@code guardChannels} - extra channels per circuit (default 0).</li>
 * </ul>
 *
 * <p>The capacity of one channel with modulation m is
 * {@code m.potentialBitRate(slotsPerChannel)}; with rateOfFEC = 5 and
 * polarizationModes = 2 this is 25 Gb/s per bit/symbol (150 Gb/s for 64QAM
 * ... 25 Gb/s for BPSK), the table used by the reconstruction.</p>
 *
 * <p>Candidate paths are all simple paths ordered by length and then by the
 * node sequence, which is deterministic and identical to the Python/Java
 * reference reconstruction on the small article topologies.</p>
 *
 * <p>Refer\u00eancias: [DIBAJ24] M. R. Dibaj et al., "Cost-Optimized Quantum Communication Networks: The Crucial Role of Trusted Node Placement in Multi-Band and Multi-Fiber Realms", IWCIT 2024 (artigo-base) (RMSTA: K = 2 caminhos, menor \u00edndice final de canal).</p>
 */
public class QKDReferenceRMSTA implements IntegratedRMLSAAlgorithmInterface {

    private static final long serialVersionUID = 1L;

    private int k = 2;
    private double channelGHz = 75.0;
    private boolean reservePich = true;
    private int guardChannels = 0;
    private boolean initialised = false;
    private final Map<String, List<Route>> routes = new HashMap<String, List<Route>>();

    private void init(ControlPlane cp) {
        if (initialised) {
            return;
        }
        Map<String, String> v = cp.getMesh().getOthersConfig() == null ? null
                : cp.getMesh().getOthersConfig().getVariables();
        if (v != null) {
            if (v.get("k") != null) k = Integer.parseInt(v.get("k").trim());
            if (v.get("classicalChannelGHz") != null) channelGHz = Double.parseDouble(v.get("classicalChannelGHz").trim());
            if (v.get("reservePichChannel") != null) reservePich = Boolean.parseBoolean(v.get("reservePichChannel").trim());
            if (v.get("guardChannels") != null) guardChannels = Integer.parseInt(v.get("guardChannels").trim());
        }
        if (k <= 0) throw new IllegalArgumentException("others.variables.k must be > 0 for qrmsta");
        initialised = true;
    }

    private static final class Candidate {
        Route route; Modulation modulation; int core; int startChannel; int endChannel;
        int slotsPerChannel; int channels;
    }

    @Override
    public boolean rsa(Circuit circuit, ControlPlane cp) {
        init(cp);
        Mesh mesh = cp.getMesh();
        List<Route> candidates = candidateRoutes(mesh, circuit.getSource(), circuit.getDestination());
        Candidate best = null;
        for (Route route : candidates) {
            Modulation modulation = selectModulation(mesh.getAvaliableModulations(), route.getDistanceAllLinks());
            Link first = route.getLinkList().get(0);
            double slotHz = first.getCore(firstClassicalCore(first)).getSlotSpectrumBand();
            int spc = Math.max(1, (int) Math.round(channelGHz * 1e9 / slotHz));
            double channelCapacity = modulation.potentialBitRate(spc);
            int channels = (int) Math.ceil(circuit.getRequiredBitRate() / channelCapacity - 1e-15) + guardChannels;
            Candidate c = firstFit(circuit, route, spc, channels);
            if (c == null) continue;
            c.modulation = modulation;
            if (best == null || better(c, best)) best = c;
        }
        if (best == null) {
            Route route = candidates.isEmpty() ? null : candidates.get(0);
            circuit.setRoute(route);
            if (route != null) {
                circuit.setModulation(selectModulation(mesh.getAvaliableModulations(), route.getDistanceAllLinks()));
                circuit.setIndexCore(firstClassicalCore(route.getLinkList().get(0)));
            }
            circuit.setSpectrumAssigned(null);
            return false;
        }
        circuit.setRoute(best.route);
        circuit.setModulation(best.modulation);
        circuit.setIndexCore(best.core);
        int startSlot = 1 + best.startChannel * best.slotsPerChannel; // slots are 1-based
        circuit.setSpectrumAssigned(new int[] {startSlot, startSlot + best.channels * best.slotsPerChannel - 1});
        return true;
    }

    private static boolean better(Candidate a, Candidate b) {
        if (a.endChannel != b.endChannel) return a.endChannel < b.endChannel;
        if (a.core != b.core) return a.core < b.core;
        int x = Double.compare(a.route.getDistanceAllLinks(), b.route.getDistanceAllLinks());
        if (x != 0) return x < 0;
        return comparePath(a.route, b.route) < 0;
    }

    /** First-fit on the channel grid: lowest start channel, then lowest core. */
    private Candidate firstFit(Circuit circuit, Route route, int spc, int channels) {
        Link first = route.getLinkList().get(0);
        Candidate best = null;
        for (Core core : first.getCores()) {
            if (core.isQkdReserved()) continue;
            int totalChannels = core.getNumOfSlots() / spc;
            List<int[]> free = IntersectionFreeSpectrum.merge(route, circuit.getGuardBand(), core.getId());
            int run = 0;
            for (int ch = reservePich ? 1 : 0; ch < totalChannels; ch++) {
                run = isFree(free, 1 + ch * spc, ch * spc + spc) ? run + 1 : 0;
                if (run >= channels) {
                    int start = ch - channels + 1;
                    if (best == null || start < best.startChannel) {
                        best = new Candidate();
                        best.route = route; best.core = core.getId(); best.startChannel = start;
                        best.endChannel = ch; best.slotsPerChannel = spc; best.channels = channels;
                    }
                    break;
                }
            }
        }
        return best;
    }

    private static boolean isFree(List<int[]> free, int a, int b) {
        for (int[] band : free) {
            if (band[0] <= a && band[1] >= b) return true;
        }
        return false;
    }

    private static Modulation selectModulation(List<Modulation> modulations, double distanceKm) {
        Modulation best = null;
        Modulation mostRobust = null;
        for (Modulation m : modulations) {
            if (mostRobust == null || m.getBitsPerSymbol() < mostRobust.getBitsPerSymbol()) mostRobust = m;
            if (m.getMaxRange() >= distanceKm && (best == null || m.getBitsPerSymbol() > best.getBitsPerSymbol())) best = m;
        }
        return best == null ? mostRobust : best;
    }

    private static int firstClassicalCore(Link link) {
        for (Core core : link.getCores()) if (!core.isQkdReserved()) return core.getId();
        return 0;
    }

    // ------------------------------------------------------------------ candidate paths

    private List<Route> candidateRoutes(Mesh mesh, Node source, Node destination) {
        String key = source.getName() + "->" + destination.getName();
        List<Route> cached = routes.get(key);
        if (cached != null) return cached;
        List<List<Node>> all = new ArrayList<List<Node>>();
        List<Node> current = new ArrayList<Node>();
        Set<String> visited = new HashSet<String>();
        current.add(source);
        visited.add(source.getName());
        dfs(mesh, source, destination, current, visited, all);
        List<Route> list = new ArrayList<Route>();
        for (List<Node> nodes : all) list.add(new Route(new Vector<Node>(nodes)));
        Collections.sort(list, new Comparator<Route>() {
            @Override public int compare(Route a, Route b) {
                int x = Double.compare(a.getDistanceAllLinks(), b.getDistanceAllLinks());
                return x != 0 ? x : comparePath(a, b);
            }
        });
        if (list.size() > k) list = new ArrayList<Route>(list.subList(0, k));
        routes.put(key, list);
        return list;
    }

    private void dfs(Mesh mesh, Node node, Node destination, List<Node> current,
            Set<String> visited, List<List<Node>> out) {
        if (node.getName().equals(destination.getName())) {
            out.add(new ArrayList<Node>(current));
            return;
        }
        List<Node> next = new ArrayList<Node>(mesh.getAdjacentsNodes(node));
        Collections.sort(next, new Comparator<Node>() {
            @Override public int compare(Node a, Node b) { return compareName(a.getName(), b.getName()); }
        });
        for (Node n : next) {
            if (visited.contains(n.getName())) continue;
            if (mesh.getLink(node.getName(), n.getName()) == null) continue;
            visited.add(n.getName());
            current.add(n);
            dfs(mesh, n, destination, current, visited, out);
            current.remove(current.size() - 1);
            visited.remove(n.getName());
        }
    }

    private static int comparePath(Route a, Route b) {
        List<Node> x = a.getNodeList(), y = b.getNodeList();
        int n = Math.min(x.size(), y.size());
        for (int i = 0; i < n; i++) {
            int c = compareName(x.get(i).getName(), y.get(i).getName());
            if (c != 0) return c;
        }
        return Integer.compare(x.size(), y.size());
    }

    private static int compareName(String a, String b) {
        try {
            return Integer.compare(Integer.parseInt(a.trim()), Integer.parseInt(b.trim()));
        } catch (NumberFormatException e) {
            return a.compareTo(b);
        }
    }

    @Override
    public KRoutingAlgorithmInterface getRoutingAlgorithm() {
        return null;
    }
}
