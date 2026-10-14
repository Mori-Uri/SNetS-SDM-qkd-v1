package qkd.reference;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

/** Undirected topology used by the article-base MF reconstruction. */
public class ReferenceTopologySpec {

    private String name;
    private double offeredClassicalGbps;
    private double offeredSkrMbps;
    private List<Edge> edges;

    private transient Map<Integer, List<Edge>> adjacency;

    public static ReferenceTopologySpec load(File file) throws IOException {
        FileReader reader = new FileReader(file);
        try {
            ReferenceTopologySpec spec =
                    new Gson().fromJson(reader, ReferenceTopologySpec.class);
            spec.validateAndIndex();
            return spec;
        } finally {
            reader.close();
        }
    }

    private void validateAndIndex() {
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("topology name is required");
        if (edges == null || edges.isEmpty()) throw new IllegalArgumentException("topology edges cannot be empty");
        adjacency = new HashMap<Integer, List<Edge>>();
        for (Edge edge : edges) {
            if (edge.u == edge.v || !(edge.lengthKm > 0.0)) throw new IllegalArgumentException("invalid edge in " + name);
            addAdj(edge.u, edge);
            addAdj(edge.v, edge);
        }
        for (List<Edge> list : adjacency.values()) {
            Collections.sort(list, (a,b) -> Integer.compare(a.otherNodeForSort(), b.otherNodeForSort()));
        }
    }

    private void addAdj(int node, Edge edge) {
        List<Edge> list = adjacency.get(Integer.valueOf(node));
        if (list == null) {
            list = new ArrayList<Edge>();
            adjacency.put(Integer.valueOf(node), list);
        }
        list.add(edge);
    }

    public List<Integer> getNodes() {
        List<Integer> nodes = new ArrayList<Integer>(adjacency.keySet());
        Collections.sort(nodes);
        return nodes;
    }

    public List<Edge> getAdjacentEdges(int node) {
        List<Edge> list = adjacency.get(Integer.valueOf(node));
        if (list == null) return Collections.emptyList();
        return new ArrayList<Edge>(list);
    }

    public double getEdgeLengthKm(int a, int b) {
        for (Edge edge : getAdjacentEdges(a)) {
            if (edge.other(a) == b) return edge.lengthKm;
        }
        throw new IllegalArgumentException("No edge " + a + "-" + b + " in " + name);
    }

    public String getName(){return name;}
    public double getOfferedClassicalGbps(){return offeredClassicalGbps;}
    public double getOfferedSkrMbps(){return offeredSkrMbps;}
    public List<Edge> getEdges(){return new ArrayList<Edge>(edges);}

    public static class Edge {
        private int u;
        private int v;
        private double lengthKm;
        public int getU(){return u;}
        public int getV(){return v;}
        public double getLengthKm(){return lengthKm;}
        public int other(int node) {
            if (node == u) return v;
            if (node == v) return u;
            throw new IllegalArgumentException("node " + node + " not on edge");
        }
        // Deterministic helper for adjacency sorting; not semantically relevant.
        private int otherNodeForSort() { return Math.max(u, v); }
        public String key(){return Math.min(u,v)+"-"+Math.max(u,v);}
    }
}
