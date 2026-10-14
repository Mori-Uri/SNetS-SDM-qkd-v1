package measurement;

import java.util.ArrayList;
import java.util.HashMap;

import network.Circuit;
import network.ControlPlane;
import network.Pair;
import network.PhysicalLayer;
import request.RequestForConnection;
import simulationControl.Util;
import simulationControl.parsers.SimulationRequest;
import simulationControl.resultManagers.PhysicalLayerStatisticsResultManager;

/**
 * Represents the PhyscialLayerStatistics component.
 */
public class PhyscialLayerStatistics extends Measurement {

	public final static String SEP = "-";

	private Util util;

	// Per circuit
	private Double sumXtPerCircuit;
	private Double sumOsnrPerCircuit;
	private Double sumPowerPerCircuit;
	private Double numPerCircuit;

	// Crosstalk per overlaps
	private HashMap<Integer, Double> sumXtPerOverlaps;
	private HashMap<Integer, Double> numXtPerOverlaps;
	private HashMap<Integer, Double> minXtPerOverlaps;
	private HashMap<Integer, Double> maxXtPerOverlaps;

	// Per pair
    private HashMap<String, Double> sumXtPerPair;
    private HashMap<String, Double> sumOsnrPerPair;
    private HashMap<String, Double> sumPowerPerPair;
    private HashMap<String, Double> numPair;

	/**
     * Creates a new instance of CrosstalkStatistics
     *  @param loadPoint int
     * @param rep int
     * @param util
     */
    public PhyscialLayerStatistics(int loadPoint, int rep, Util util) {
    	super(loadPoint, rep);

    	this.util = util;

    	this.sumXtPerCircuit = 0.0;
    	this.sumOsnrPerCircuit = 0.0;
    	this.sumPowerPerCircuit = 0.0;
    	this.numPerCircuit = 0.0;

    	this.sumXtPerOverlaps = new HashMap<>();
    	this.numXtPerOverlaps = new HashMap<>();
    	this.minXtPerOverlaps = new HashMap<>();
    	this.maxXtPerOverlaps = new HashMap<>();

    	this.sumXtPerPair = new HashMap<>();
    	this.sumOsnrPerPair = new HashMap<>();
    	this.sumPowerPerPair = new HashMap<>();
    	this.numPair = new HashMap<>();

    	this.resultManager = new PhysicalLayerStatisticsResultManager();
    }

    /**
     * Adds a new observation of block or not a request
     *
     * @param cp ControlPlane
     * @param success boolean
     * @param request RequestForConnection
     */
    public void addNewObservation(ControlPlane cp, boolean success, RequestForConnection request) {

		if(success){
	    	for (Circuit c : request.getCircuits()) {

	    		StringBuilder sbPair = new StringBuilder();
	    		sbPair.append(c.getPair().getSource().getName());
	    		sbPair.append(SEP);
	    		sbPair.append(c.getPair().getDestination().getName());
	    		String pairName = sbPair.toString();

	            Integer overlaps = cp.getMesh().getPhysicalLayer().getCrosstalk().numberSlotsOverlapping(c, c.getRoute(), c.getModulation(), c.getIndexCore(), c.getSpectrumAssigned());
	            Double xt = cp.getMesh().getPhysicalLayer().getCrosstalk().calculateCrosstalk(c, null, false);
	            Double osnr = cp.getMesh().getPhysicalLayer().computeSNRSegment(c, c.getRoute(), 0, c.getRoute().getNodeList().size() - 1, c.getModulation(), c.getIndexCore(), c.getSpectrumAssigned(), null, false);
	            Double power = cp.getMesh().getPhysicalLayer().getCircuitLaunchPower(c, c.getModulation());

	            osnr = PhysicalLayer.ratioForDB(osnr);
	            power = PhysicalLayer.W_to_dBm(power);

	            // ========================================================
	            // Per circuit
	            sumXtPerCircuit += xt;
	            sumOsnrPerCircuit += osnr;
	            sumPowerPerCircuit += power;
	            numPerCircuit += 1.0;

	            // ========================================================
	            // XT per overlaps
	            Double sumXtOverlaps = this.sumXtPerOverlaps.get(overlaps);
	            if (sumXtOverlaps == null) {
	            	sumXtOverlaps = 0.0;
	            }
	            this.sumXtPerOverlaps.put(overlaps, sumXtOverlaps += xt);

	            Double quantXtOverlaps = this.numXtPerOverlaps.get(overlaps);
	            if (quantXtOverlaps == null) {
	            	quantXtOverlaps = 0.0;
	            }
	            this.numXtPerOverlaps.put(overlaps, quantXtOverlaps + 1.0);

	            Double minXtOverlaps = this.minXtPerOverlaps.get(overlaps);
	            if (minXtOverlaps == null) {
	            	minXtOverlaps = Double.POSITIVE_INFINITY;
	            }
	            if (xt < minXtOverlaps) {
	            	this.minXtPerOverlaps.put(overlaps, xt);
	            }

	            Double maxXtOverlaps = this.maxXtPerOverlaps.get(overlaps);
	            if (maxXtOverlaps == null) {
	            	maxXtOverlaps = Double.NEGATIVE_INFINITY;
	            }
	            if (xt > maxXtOverlaps) {
	            	this.maxXtPerOverlaps.put(overlaps, xt);
	            }

	            // ========================================================
	            // Number of pairs
	            Double quantPair = this.numPair.get(pairName);
	            if (quantPair == null) {
	            	quantPair = 0.0;
	            }
	            this.numPair.put(pairName, quantPair + 1.0);

	            // ========================================================
	            // XT per pair
	            Double sumXtPair = this.sumXtPerPair.get(pairName);
	            if (sumXtPair == null) {
	            	sumXtPair = 0.0;
	            }
	            this.sumXtPerPair.put(pairName, sumXtPair + xt);

	            // ========================================================
	            // OSNR per pair
	            Double sumOsnrPair = this.sumOsnrPerPair.get(pairName);
	            if (sumOsnrPair == null) {
	            	sumOsnrPair = 0.0;
	            }
	            this.sumOsnrPerPair.put(pairName, sumOsnrPair + osnr);

	            // ========================================================
	            // Circuit Power per pair
	            Double sumPowerPair = this.sumPowerPerPair.get(pairName);
	            if (sumPowerPair == null) {
	            	sumPowerPair = 0.0;
	            }
	            this.sumPowerPerPair.put(pairName, sumPowerPair + power);

	            // ========================================================
	    	}
        }
    }

    /**
     * Returns the file name.
     * @return the file name.
     */
    @Override
    public String getFileName() {
        return SimulationRequest.Result.FILE_PHYSICAL_LAYER_STATISTICS;
    }

    /**
     * Returns the util
     *
     * @return util
     */
    public Util getUtil() {
        return util;
    }

    /**
     * Returns the average XT per circuit
     * @return double
     */
    public double getXtPerCircuit() {
        return this.sumXtPerCircuit / this.numPerCircuit;
    }

    /**
     * Returns the average OSNR per circuit
     * @return double
     */
    public double getOsnrPerCircuit() {
        return this.sumOsnrPerCircuit / this.numPerCircuit;
    }

    /**
     * Returns the average Power per circuit
     * @return double
     */
    public double getPowerPerCircuit() {
        return this.sumPowerPerCircuit / this.numPerCircuit;
    }

    /**
     * Returns the minimum XT per overlaps
     * @param overlaps int
     * @return double
     */
    public double getMinXtPerOverlaps(int overlaps) {
    	Double res = this.minXtPerOverlaps.get(overlaps);
    	if (res == null) {
    		res = 0.0;
    	}

    	return res;
    }

    /**
     * Returns the maximum XT per overlaps
     * @param overlaps int
     * @return double
     */
    public double getMaxXtPerOverlaps(int overlaps) {
    	Double res = this.maxXtPerOverlaps.get(overlaps);
    	if (res == null) {
    		res = 0.0;
    	}

    	return res;
    }

    /**
     * Returns the average XT per overlaps
     * @param overlaps int
     * @return double
     */
    public double getAverageXtPerOverlaps(int overlaps) {

        Double sum = this.sumXtPerOverlaps.get(overlaps);
        if (sum == null) {
        	sum = 0.0;
        }

        Double quant = this.numXtPerOverlaps.get(overlaps);
        if (quant == null) {
        	return 0.0; // No requests generated for this pair
        }

        double res = sum / quant;

    	return res;
    }

    /**
     * Returns the average XT per pair
     * @param p Pair
     * @return double
     */
    public double getAverageXtPerPair(Pair p) {

        StringBuilder sbPair = new StringBuilder();
		sbPair.append(p.getSource().getName());
		sbPair.append(SEP);
		sbPair.append(p.getDestination().getName());
		String pairName = sbPair.toString();

        Double sum = this.sumXtPerPair.get(pairName);
        if (sum == null) {
        	sum = 0.0;
        }

        Double quant = this.numPair.get(pairName);
        if (quant == null) {
        	return 0.0; // No requests generated for this pair
        }

        double res = sum / quant;

    	return res;
    }

    /**
     * Returns the average OSNR per pair
     * @param p Pair
     * @return double
     */
    public double getAverageOsnrPerPair(Pair p) {

        StringBuilder sbPair = new StringBuilder();
		sbPair.append(p.getSource().getName());
		sbPair.append(SEP);
		sbPair.append(p.getDestination().getName());
		String pairName = sbPair.toString();

        Double sum = this.sumOsnrPerPair.get(pairName);
        if (sum == null) {
        	sum = 0.0;
        }

        Double quant = this.numPair.get(pairName);
        if (quant == null) {
        	return 0.0; // No requests generated for this pair
        }

        double res = sum / quant;

    	return res;
    }

    /**
     * Returns the average Power per pair
     * @param p Pair
     * @return double
     */
    public double getAveragePowerPerPair(Pair p) {

        StringBuilder sbPair = new StringBuilder();
		sbPair.append(p.getSource().getName());
		sbPair.append(SEP);
		sbPair.append(p.getDestination().getName());
		String pairName = sbPair.toString();

        Double sum = this.sumPowerPerPair.get(pairName);
        if (sum == null) {
        	sum = 0.0;
        }

        Double quant = this.numPair.get(pairName);
        if (quant == null) {
        	return 0.0; // No requests generated for this pair
        }

        double res = sum / quant;

    	return res;
    }

    /**
     * Returns the overlaps list
     * @return ArrayList<Integer>
     */
    public ArrayList<Integer> getSortedOverlaps() {
        ArrayList<Integer> sorted = new ArrayList<>(numXtPerOverlaps.keySet());
        sorted.sort(Integer::compareTo);
        return sorted;
    }

}
