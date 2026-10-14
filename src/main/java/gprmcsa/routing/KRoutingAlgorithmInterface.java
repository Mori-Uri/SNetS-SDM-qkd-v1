package gprmcsa.routing;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;

import network.Mesh;
import network.Node;

/**
 * This interface represents a k routing algorithm.
 *
 * @author Alexandre
 */
public interface KRoutingAlgorithmInterface extends Serializable {

	/**
     * Computes the smallest paths for each pair
     *
     * @param mesh Mesha - network topology
     * @param k    int - number of routes to be computed for each pair(s, d)
     */
    public void computeAllRoutes(Mesh mesh, int k);

	/**
     * Returns the k shortest paths between two nodes
     *
     * @param n1 Node
     * @param n2 Node
     * @return List<Route>
     */
    public List<Route> getRoutes(Node n1, Node n2);

	/**
	 * Returns the route list for all pairs
	 *
	 * @return Vector<Route>
	 */
	public HashMap<String, List<Route>> getRoutesForAllPairs();
}