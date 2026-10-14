package util.tools.machineLearning.collectingData;

import network.Circuit;
import network.ControlPlane;
import util.tools.machineLearning.DataSetInterface;

/**
 * Represents the LineData component.
 */
public class LineData implements DataSetInterface {

	private int request;
	private String algorithm;
	private double biteRate;
    private String route;
    private int hops;
    private double distance;
    private String mod;
    private String band;
    private int core;
    private double osnr;
    private double deltaOsnr;
    private double deltaOsnrNeigh;
    private double xt;
    private double deltaXT;
    private double deltaXTAdj;
    private int overlaps;
    private double launchPower;
    private double ut;
    private double frag;
    private int wasBlocked;
    private int blockCause;


    /**
	 * Method that creates the header
	 *
	 * @return String
	 */
    public String getHeader() {
		StringBuilder buffer = new StringBuilder();

		buffer.append("iteration").append(LineDataManager.strSeparator);
		buffer.append("request").append(LineDataManager.strSeparator);
		buffer.append("algorithm").append(LineDataManager.strSeparator);
		buffer.append("biteRate").append(LineDataManager.strSeparator);

		buffer.append("route").append(LineDataManager.strSeparator);
		buffer.append("hops").append(LineDataManager.strSeparator);
		buffer.append("distance").append(LineDataManager.strSeparator);

		buffer.append("modulation").append(LineDataManager.strSeparator);
		buffer.append("spectrum").append(LineDataManager.strSeparator);
		buffer.append("core").append(LineDataManager.strSeparator);

		buffer.append("OSNR(dB)").append(LineDataManager.strSeparator);
		buffer.append("deltaOSNR").append(LineDataManager.strSeparator);
		buffer.append("minDeltaOSNRofNeighbors").append(LineDataManager.strSeparator);

		buffer.append("XT(dB)").append(LineDataManager.strSeparator);
		buffer.append("deltaXT").append(LineDataManager.strSeparator);
		buffer.append("minDeltaXTofAdjacents").append(LineDataManager.strSeparator);
		buffer.append("overlaps").append(LineDataManager.strSeparator);

		buffer.append("launchPower").append(LineDataManager.strSeparator);

		buffer.append("utilization").append(LineDataManager.strSeparator);
		buffer.append("fragmentation").append(LineDataManager.strSeparator);

		buffer.append("was_blocked").append(LineDataManager.strSeparator);
		buffer.append("block_cause").append(LineDataManager.strSeparator);

		return buffer.toString();
	}

	/**
	 * A method that creates a String based on the attributes.
	 *
	 * @return String
	 */
    public String dataSetToString() {
    	StringBuilder buffer = new StringBuilder();

    	buffer.append(request).append(LineDataManager.strSeparator);
		buffer.append(algorithm).append(LineDataManager.strSeparator);
		buffer.append(biteRate).append(LineDataManager.strSeparator);

		buffer.append(route).append(LineDataManager.strSeparator);
		buffer.append(hops).append(LineDataManager.strSeparator);
		buffer.append(distance).append(LineDataManager.strSeparator);

		buffer.append(mod).append(LineDataManager.strSeparator);
		buffer.append(band).append(LineDataManager.strSeparator);
		buffer.append(core).append(LineDataManager.strSeparator);

		buffer.append(osnr).append(LineDataManager.strSeparator);
		buffer.append(deltaOsnr).append(LineDataManager.strSeparator);
		buffer.append(deltaOsnrNeigh).append(LineDataManager.strSeparator);

		buffer.append(xt).append(LineDataManager.strSeparator);
		buffer.append(deltaXT).append(LineDataManager.strSeparator);
		buffer.append(deltaXTAdj).append(LineDataManager.strSeparator);
		buffer.append(overlaps).append(LineDataManager.strSeparator);

		buffer.append(launchPower).append(LineDataManager.strSeparator);

		buffer.append(ut).append(LineDataManager.strSeparator);
		buffer.append(frag).append(LineDataManager.strSeparator);

		buffer.append(wasBlocked).append(LineDataManager.strSeparator);
		buffer.append(blockCause).append(LineDataManager.strSeparator);

		return buffer.toString();
    }

    /**
	 * This method sets the DataSetStructure
	 *
	 * @param cp ControlPlane
	 * @param c Circuit
	 */
	public void setDatasetStructure(ControlPlane cp, Circuit c) {
		//
	}

	/**
	 * Sets the request.
	 * @param request the request.
	 */
	public void setRequest(int request) {
		this.request = request;
	}

	/**
	 * Sets the algorithm.
	 * @param algorithm the algorithm.
	 */
	public void setAlgorithm(String algorithm) {
		this.algorithm = algorithm;
	}

	/**
	 * Sets the bite rate.
	 * @param biteRate the biteRate.
	 */
	public void setBiteRate(double biteRate) {
		this.biteRate = biteRate;
	}

	/**
	 * Sets the route.
	 * @param route the route.
	 */
	public void setRoute(String route) {
		this.route = route;
	}

	/**
	 * Sets the hops.
	 * @param hops the hops.
	 */
	public void setHops(int hops) {
		this.hops = hops;
	}

	/**
	 * Sets the distance.
	 * @param distance the distance.
	 */
	public void setDistance(double distance) {
		this.distance = distance;
	}

	/**
	 * Sets the mod.
	 * @param mod the mod.
	 */
	public void setMod(String mod) {
		this.mod = mod;
	}

	/**
	 * Sets the band.
	 * @param band the band.
	 */
	public void setBand(String band) {
		this.band = band;
	}

	/**
	 * Sets the core.
	 * @param core the core.
	 */
	public void setCore(int core) {
		this.core = core;
	}

	/**
	 * Sets the osnr.
	 * @param osnr the osnr.
	 */
	public void setOsnr(double osnr) {
		this.osnr = osnr;
	}

	/**
	 * Sets the delta osnr.
	 * @param deltaOsnr the deltaOsnr.
	 */
	public void setDeltaOsnr(double deltaOsnr) {
		this.deltaOsnr = deltaOsnr;
	}

	/**
	 * Sets the delta osnr neigh.
	 * @param deltaOsnrNeigh the deltaOsnrNeigh.
	 */
	public void setDeltaOsnrNeigh(double deltaOsnrNeigh) {
		this.deltaOsnrNeigh = deltaOsnrNeigh;
	}

	/**
	 * Sets the xt.
	 * @param xt the xt.
	 */
	public void setXt(double xt) {
		this.xt = xt;
	}

	/**
	 * Sets the delta xt.
	 * @param deltaXT the deltaXT.
	 */
	public void setDeltaXT(double deltaXT) {
		this.deltaXT = deltaXT;
	}

	/**
	 * Sets the delta xt adj.
	 * @param deltaXTAdj the deltaXTAdj.
	 */
	public void setDeltaXTAdj(double deltaXTAdj) {
		this.deltaXTAdj = deltaXTAdj;
	}

	/**
	 * Sets the overlaps.
	 * @param overlaps the overlaps.
	 */
	public void setOverlaps(int overlaps) {
		this.overlaps = overlaps;
	}

	/**
	 * Sets the launch power.
	 * @param launchPower the launchPower.
	 */
	public void setLaunchPower(double launchPower) {
		this.launchPower = launchPower;
	}

	/**
	 * Sets the ut.
	 * @param ut the ut.
	 */
	public void setUt(double ut) {
		this.ut = ut;
	}

	/**
	 * Sets the frag.
	 * @param frag the frag.
	 */
	public void setFrag(double frag) {
		this.frag = frag;
	}

	/**
	 * Sets the was blocked.
	 * @param wasBlocked the wasBlocked.
	 */
	public void setWasBlocked(int wasBlocked) {
		this.wasBlocked = wasBlocked;
	}

	/**
	 * Sets the block cause.
	 * @param blockCause the blockCause.
	 */
	public void setBlockCause(int blockCause) {
		this.blockCause = blockCause;
	}


}
