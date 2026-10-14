package simulationControl;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import network.Pair;

/**
 * This class should record the pairs, bandwidths and other values necessary for
 * recording the results of the simulation on file
 *
 * @author Iallen
 */
public class Util implements Serializable {

	public Set<Double> bitRateList = new HashSet<>();

	public Set<Pair> pairs = new HashSet<>();

	public String projectPath = "";

	/**
	 * Resets the value.
	 */
	public void reset(){
		bitRateList = new HashSet<>();
		pairs = new HashSet<Pair>();
	}
}
