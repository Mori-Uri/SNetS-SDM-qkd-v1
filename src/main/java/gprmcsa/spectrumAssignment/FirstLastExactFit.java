package gprmcsa.spectrumAssignment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import gprmcsa.routing.Route;
import network.Circuit;
import network.ControlPlane;
import network.Link;
import util.IntersectionFreeSpectrum;

/**
 * This class represents the spectrum allocation technique called FirstLastExactFit.
 * Algorithm based on: A spectrum allocation scheme based on first-last-exact fit policy
 *                     for elastic optical networks (2016)
 *
 * @author Alexandre
 */
public class FirstLastExactFit implements SpectrumAssignmentAlgorithmInterface {

	List<Route> disjointConnectionGroup;

    /**
     * Returns the assign spectrum.
     * @param numberOfSlots the numberOfSlots.
     * @param circuit the circuit.
     * @param cp the cp.
     * @param indexCore the indexCore.
     * @return true if the condition is met; false otherwise.
     */
    @Override
    public boolean assignSpectrum(int numberOfSlots, Circuit circuit, ControlPlane cp, int indexCore) {
        List<int[]> composition = IntersectionFreeSpectrum.merge(circuit.getRoute(), circuit.getGuardBand(), indexCore);

        int chosen[] = policy(numberOfSlots, composition, circuit, cp);
        circuit.setSpectrumAssigned(chosen);

        if (chosen == null)
        	return false;

        return true;
    }

    /**
     * Creates the disjoint connection group
     *
     * @param cp ControlPlane
     */
    public void createGraphCheckDisjoint(ControlPlane cp){
		if(disjointConnectionGroup != null){
			return;
		}

		HashMap<Route, List<Route>> pathGraph = new HashMap<>();
		Vector<Route> routesForAllPairs = new Vector<Route>();

		if(cp.getIntegrated() != null && cp.getIntegrated().getRoutingAlgorithm() != null &&
		   cp.getIntegrated().getRoutingAlgorithm().getRoutesForAllPairs() != null){
			HashMap<String, List<Route>> routes = cp.getIntegrated().getRoutingAlgorithm().getRoutesForAllPairs();

			for(String pair : routes.keySet()){
				for(Route route : routes.get(pair)){
					routesForAllPairs.add(route);
				}
			}

		}else if(cp.getRouting().getRoutesForAllPairs() != null){
			HashMap<String, Route> routes = cp.getRouting().getRoutesForAllPairs();
			routesForAllPairs = new Vector<Route>(routes.values());
		}

		for(int i = 0; i < routesForAllPairs.size(); i++){
			Route routeI = routesForAllPairs.get(i);
			List<Route> listRoute = new ArrayList<>();

			for(int j = 0; j < routesForAllPairs.size(); j++){
				Route routeJ = routesForAllPairs.get(j);

				if(!routeI.equals(routeJ)){
					for(int l = 0; l < routeJ.getLinkList().size(); l++){
						Link linkJ = routeJ.getLinkList().get(l);

						if(routeI.containThisLink(linkJ)){
							listRoute.add(routeJ);
							break;
						}
					}
				}
			}

			pathGraph.put(routeI, listRoute);
		}

		disjointConnectionGroup = new ArrayList<>();
		for(int i = 0; i < routesForAllPairs.size(); i++){

			Route routeI = routesForAllPairs.get(i);
			List<Route> listRoute = pathGraph.get(routeI);

			boolean flagPertence = false;
			for(int j = 0; j < listRoute.size(); j++){
				Route routeJ = listRoute.get(j);

				if(disjointConnectionGroup.contains(routeJ)){
					flagPertence = true;
					break;
				}
			}

			if(!flagPertence){
				disjointConnectionGroup.add(routeI);
			}
		}
	}

    /**
	 * Apply first exact fit policy
	 *
	 * @param numberOfSlots int
	 * @param freeSpectrumBands List<int[]>
	 * @return int[]
	 */
	public static int[] firstExactFit(int numberOfSlots, List<int[]> freeSpectrumBands){
		int chosen[] = null;

		// applies exact fit starting from the lowest slot index
		for (int[] band : freeSpectrumBands) {
			int bandSize = band[1] - band[0] + 1;
			if(bandSize == numberOfSlots){
				chosen = band.clone();
				chosen[1] = chosen[0] + numberOfSlots - 1; //It is not necessary to allocate the entire band, just the amount of slots required
				break;
			}
		}

		if(chosen == null){ // did not find any contiguous tracks and is still available
			// now just look for the free range and apply first fit policy

			for (int[] band : freeSpectrumBands) {
	            if (band[1] - band[0] + 1 >= numberOfSlots) {
	                chosen = band.clone();
	                chosen[1] = chosen[0] + numberOfSlots - 1; //It is not necessary to allocate the entire band, just the amount of slots required
	                break;
	            }
	        }
		}

		return chosen;
	}

	/**
	 * Apply first exact fit policy
	 *
	 * @param numberOfSlots
	 * @param livres
	 * @return
	 */
	public static int[] lastExactFit(int numberOfSlots, List<int[]> freeSpectrumBands){
		int chosen[] = null;

		// applies exact fit starting from the largest slot index
		for (int i = freeSpectrumBands.size()-1; i >= 0; i--) {
			int band[] = freeSpectrumBands.get(i);

			int bandSize = band[1] - band[0] + 1;
			if(bandSize == numberOfSlots){
				chosen = band.clone();
				chosen[0] = chosen[1] - numberOfSlots + 1;// it is not necessary to allocate the whole band, only the required number of slots
				break;
			}
		}

		if(chosen == null){ // did not find any contiguous tracks and is still available
			// now just look for the free range and apply last fit policy

			for (int i = freeSpectrumBands.size() - 1; i >= 0; i--) {
	            int band[] = freeSpectrumBands.get(i);
	            if (band[1] - band[0] + 1 >= numberOfSlots) {
	                chosen = band.clone();
	                chosen[0] = chosen[1] - numberOfSlots + 1;//It is not necessary to allocate the entire band, just the amount of slots required
	                break;
	            }
	        }
		}

		return chosen;
	}

	/**
	 * Returns the policy.
	 * @param numberOfSlots the numberOfSlots.
	 * @param freeSpectrumBands the freeSpectrumBands.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @return the result of the operation.
	 */
	@Override
	public int[] policy(int numberOfSlots, List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp){
		int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
		if(numberOfSlots> maxAmplitude) return null;
		createGraphCheckDisjoint(cp);

		if (disjointConnectionGroup.contains(circuit.getRoute())) {
			return firstExactFit(numberOfSlots, freeSpectrumBands);

		}else {
			return lastExactFit(numberOfSlots, freeSpectrumBands);
		}
	}

}
