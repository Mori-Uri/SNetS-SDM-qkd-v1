package qkd.network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.Vector;

import gprmcsa.routing.Route;
import network.Link;
import network.Mesh;
import network.Node;

/**
 * Part 23: candidate QKD routes between a source and a destination, used when
 * the QCh of a QKD-protected request cannot be provisioned on the route of its
 * classical circuit (qualification, slide 29: "a requisição QKD pode seguir a
 * mesma rota topológica do circuito clássico ou usar uma rota alternativa").
 *
 * <p>Yen's algorithm over the physical topology, shortest total length first
 * (ties broken by the node sequence, so the order is deterministic). Routes are
 * computed once per ordered pair and cached.</p>
 *
 * <p>Refer\u00eancias: [MORI26] U. P. Mori, qualifica\u00e7\u00e3o "Planejamento de N\u00f3s Confi\u00e1veis para QKD em SDM-EONs: An\u00e1lise da Coexist\u00eancia Qu\u00e2ntico-Cl\u00e1ssica", UFPI, 2026 (rotas QKD alternativas); k menores caminhos de [CHAT15] B. C. Chatterjee, N. Sarma, E. Oki, "Routing and Spectrum Allocation in Elastic Optical Networks: A Tutorial", IEEE COMST 17(3), 2015.</p>
 */
public final class QKDAlternativeRoutes implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Mesh mesh;
    private final int k;
    private final Map<String, List<Route>> cache = new HashMap<String, List<Route>>();

    public QKDAlternativeRoutes(Mesh mesh, int k) {
        this.mesh = mesh;
        this.k = Math.max(0, k);
    }

    /**
     * Up to {@code k} simple routes from source to destination, excluding the
     * route with the same node sequence as {@code exclude} (may be null).
     */
    public synchronized List<Route> alternatives(String source, String destination, Route exclude) {
        if (k == 0) return Collections.emptyList();
        String key = source + "->" + destination;
        List<Route> all = cache.get(key);
        if (all == null) {
            all = yen(source, destination, k + 1);
            cache.put(key, all);
        }
        String ex = exclude == null ? null : signature(exclude.getNodeList());
        List<Route> out = new ArrayList<Route>();
        for (Route r : all) {
            if (ex != null && ex.equals(signature(r.getNodeList()))) continue;
            out.add(r);
            if (out.size() >= k) break;
        }
        return out;
    }

    private static String signature(List<Node> nodes) {
        StringBuilder sb = new StringBuilder();
        for (Node n : nodes) sb.append(n.getName()).append('|');
        return sb.toString();
    }

    // ------------------------------------------------------------------ Yen

    private List<Route> yen(String src, String dst, int count) {
        List<List<String>> accepted = new ArrayList<List<String>>();
        List<List<String>> candidates = new ArrayList<List<String>>();
        List<String> first = dijkstra(src, dst, new HashSet<String>(), new HashSet<String>());
        if (first == null) return Collections.emptyList();
        accepted.add(first);
        while (accepted.size() < count) {
            List<String> last = accepted.get(accepted.size() - 1);
            for (int i = 0; i < last.size() - 1; i++) {
                String spur = last.get(i);
                List<String> root = last.subList(0, i + 1);
                Set<String> removedEdges = new HashSet<String>();
                for (List<String> p : accepted) {
                    if (p.size() > i && p.subList(0, i + 1).equals(root)) removedEdges.add(p.get(i) + ">" + p.get(i + 1));
                }
                Set<String> removedNodes = new HashSet<String>(root.subList(0, root.size() - 1));
                List<String> spurPath = dijkstra(spur, dst, removedNodes, removedEdges);
                if (spurPath == null) continue;
                List<String> total = new ArrayList<String>(root.subList(0, root.size() - 1));
                total.addAll(spurPath);
                if (!containsPath(candidates, total) && !containsPath(accepted, total)) candidates.add(total);
            }
            if (candidates.isEmpty()) break;
            Collections.sort(candidates, (a, b) -> {
                int c = Double.compare(length(a), length(b));
                return c != 0 ? c : String.join("|", a).compareTo(String.join("|", b));
            });
            accepted.add(candidates.remove(0));
        }
        List<Route> routes = new ArrayList<Route>();
        for (List<String> p : accepted) {
            Vector<Node> nodes = new Vector<Node>();
            for (String n : p) nodes.add(mesh.searchNode(n));
            routes.add(new Route(nodes));
        }
        return routes;
    }

    private static boolean containsPath(List<List<String>> list, List<String> p) {
        for (List<String> q : list) if (q.equals(p)) return true;
        return false;
    }

    private double length(List<String> p) {
        double d = 0;
        for (int i = 0; i < p.size() - 1; i++) {
            Link l = link(p.get(i), p.get(i + 1));
            d += l == null ? Double.POSITIVE_INFINITY : l.getDistance();
        }
        return d;
    }

    private Link link(String a, String b) {
        Node na = mesh.searchNode(a), nb = mesh.searchNode(b);
        if (na == null || nb == null) return null;
        return na.getOxc().linkTo(nb.getOxc());
    }

    private List<String> dijkstra(String src, String dst, Set<String> removedNodes, Set<String> removedEdges) {
        final Map<String, Double> dist = new HashMap<String, Double>();
        final Map<String, String> prev = new HashMap<String, String>();
        PriorityQueue<Object[]> pq = new PriorityQueue<Object[]>((a, b) -> {
            int c = Double.compare((Double) a[0], (Double) b[0]);
            return c != 0 ? c : ((String) a[1]).compareTo((String) b[1]);
        });
        dist.put(src, 0.0);
        pq.add(new Object[] {0.0, src});
        Set<String> done = new HashSet<String>();
        while (!pq.isEmpty()) {
            Object[] top = pq.poll();
            String u = (String) top[1];
            if (!done.add(u)) continue;
            if (u.equals(dst)) break;
            Node nu = mesh.searchNode(u);
            if (nu == null) continue;
            for (Node nv : mesh.getAdjacentsNodes(nu)) {
                if (nv == null) continue;
                String v = nv.getName();
                if (removedNodes.contains(v) || removedEdges.contains(u + ">" + v) || done.contains(v)) continue;
                Link l = nu.getOxc().linkTo(nv.getOxc());
                if (l == null) continue;
                double nd = (Double) top[0] + l.getDistance();
                Double old = dist.get(v);
                if (old == null || nd < old - 1e-12) {
                    dist.put(v, nd);
                    prev.put(v, u);
                    pq.add(new Object[] {nd, v});
                }
            }
        }
        if (!dist.containsKey(dst)) return null;
        List<String> path = new ArrayList<String>();
        for (String at = dst; at != null; at = prev.get(at)) {
            path.add(at);
            if (at.equals(src)) break;
        }
        Collections.reverse(path);
        return path.get(0).equals(src) ? path : null;
    }
}
