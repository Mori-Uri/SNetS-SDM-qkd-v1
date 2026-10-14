package measurement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import gprmcsa.modulation.Modulation;
import network.ControlPlane;
import network.Core;
import network.Link;
import network.Mesh;
import request.RequestForConnection;
import simulationControl.parsers.SimulationRequest;
import simulationControl.resultManagers.RelativeFragmentationResultManager;
import util.ComputesFragmentation;

/**
 * This class represents the relative fragmentation metric.
 * The metric represented by this class is associated with a load point and a replication.
 *
 * @author Iallen
 */
public class RelativeFragmentation extends Measurement {

    public final static String SEP = "-";

    private HashMap<Integer, Double> relativeFrag;
    private int numberObservations;

    /**
     * Creates a new instance of RelativeFragmentation
     *
     * @param loadPoint int
     * @param rep int
     * @param mesh Mesh
     */
    public RelativeFragmentation(int loadPoint, int rep) {
        super(loadPoint, rep);

        relativeFrag = new HashMap<>();
        numberObservations = 0;

		resultManager = new RelativeFragmentationResultManager();
    }

    /**
     * Adds a new observation of external fragmentation of the network
     *
     * @param cp ControlPlane
     * @param success boolean
     * @param request RequestForConnection
     */
    public void addNewObservation(ControlPlane cp, boolean success, RequestForConnection request) {

    	if(relativeFrag.isEmpty()) {
	    	Set<Double> bitRateList = cp.getMesh().getUtil().bitRateList;
	    	List<Modulation> avaliableModulations = cp.getMesh().getAvaliableModulations();

	    	for(Double bitRate : bitRateList) {
	    		for (int m = avaliableModulations.size()-1; m >= 0; m--) {
	    			Modulation mod = avaliableModulations.get(m);

	    			int slotsNumber = mod.requiredSlots(bitRate);
	    			relativeFrag.put(slotsNumber, 0.0);
	    		}
	    	}
    	}

        this.observationLinks(cp.getMesh());
        numberObservations++;
    }

    /**
     * Returns the file name.
     * @return the file name.
     */
    @Override
    public String getFileName() {
        return SimulationRequest.Result.FILE_RELATIVE_FRAGMENTATION;
    }

    /**
     * Makes a observation of the average relative fragmentation on all links for each configured c value
     */
    private void observationLinks(Mesh mesh) {
        for (Integer c : relativeFrag.keySet()) {
            this.observationAllLinks(c, mesh);
        }
    }

    /**
     * Make a note of the average relative fragmentation on all links to the value of c passed as a parameter
     *
     * @param c
     */
    private void observationAllLinks(Integer c, Mesh mesh) {
    	ComputesFragmentation cf = new ComputesFragmentation();
        double averageFragLink = 0.0;

        for (Link link : mesh.getLinkList()) {
        	double fragAllCores = 0.0;

        	for (Core core: link.getCores()) {
        		fragAllCores += cf.relativeFragmentation(core.getFreeSpectrumBands(0), c);
        	}

        	fragAllCores = fragAllCores / link.getNumberOfCores();
        	averageFragLink += fragAllCores;
        }

        averageFragLink = averageFragLink / ((double) mesh.getLinkList().size());

        double fCurrent = this.relativeFrag.get(c);
        fCurrent += averageFragLink;
        this.relativeFrag.put(c, fCurrent);
    }

    /**
     * Returns the list of configured C values for the realization of observations relative
     * of relative fragmentation
     *
     * @return
     */
    public List<Integer> getCList() {
        return new ArrayList<>(relativeFrag.keySet());
    }

    /**
     * Returns the average relative fragmentation
     *
     * @param c int
     * @return double
     */
    public double getAverageRelativeFragmentation(int c) {
        return this.relativeFrag.get(c) / ((double) this.numberObservations);
    }

}
