package util.tools.machineLearning;

import java.util.ArrayList;
import java.util.List;

import network.Circuit;
import network.ControlPlane;

/**
 * This class represents the DataSet structure, its representation in entity form is
 * important for storing and transmitting this type of configuration in JSON format.
 *
 */
public class DataSetStructure implements DataSetInterface {

	private Double bandwidth;
	private Integer hops;
	private Double length;
	private Integer core;
	private Double modulation;
	private Integer firstSlot;
	private Integer lastSlot;
	private Double XT;
	private Double OSNR;
	private Boolean wasBlocked;
	private Integer blockCause;

	private List<Integer> coreUtilization = new ArrayList<>();


	/**
	 * Method that creates the DataSet header
	 *
	 * @return String
	 */
	public String getHeader() {
		StringBuilder res = new StringBuilder();

		//Use header order to organize data as well
		res.append("bandwidth").append(DataSetManager.strSeparator);
		res.append("hops").append(DataSetManager.strSeparator);
		res.append("length").append(DataSetManager.strSeparator);
		res.append("core").append(DataSetManager.strSeparator);
		res.append("modulation").append(DataSetManager.strSeparator);
		res.append("first_slot").append(DataSetManager.strSeparator);
		res.append("last_slot").append(DataSetManager.strSeparator);
		res.append("XT").append(DataSetManager.strSeparator);
		res.append("OSNR").append(DataSetManager.strSeparator);

    	for (int i = 0; i < coreUtilization.size(); i++) {
    		res.append("core_utilization_").append(i).append(DataSetManager.strSeparator);
    	}

    	res.append("was_blocked").append(DataSetManager.strSeparator);
		res.append("block_cause").append(DataSetManager.strSeparator);

		return res.toString();
	}

	/**
	 * This method creates a String based on the DataSetStructure
	 *
	 * @return String
	 */
	public String dataSetToString() {
		StringBuilder res = new StringBuilder();

		//Add data in the same order as the DataSet header
		res.append(getBandwidth()).append(DataSetManager.strSeparator);

		res.append(getHops()).append(DataSetManager.strSeparator);
		res.append(getLength()).append(DataSetManager.strSeparator);

		res.append(getCore()).append(DataSetManager.strSeparator);
		res.append(getModulation()).append(DataSetManager.strSeparator);
		res.append(getFirstSlot()).append(DataSetManager.strSeparator);
		res.append(getLastSlot()).append(DataSetManager.strSeparator);

		res.append(getXT()).append(DataSetManager.strSeparator);
		res.append(getOSNR()).append(DataSetManager.strSeparator);

    	for (Integer utilization : coreUtilization) {
    		res.append(utilization).append(DataSetManager.strSeparator);
    	}

    	res.append(getWasBlocked()).append(DataSetManager.strSeparator);
		res.append(getBlockCause()).append(DataSetManager.strSeparator);

		return res.toString();
	}

	/**
	 * This method sets the DataSetStructure
	 *
	 * @param cp ControlPlane
	 * @param c Circuit
	 */
	public void setDatasetStructure(ControlPlane cp, Circuit c) {
		int numberOfCores = cp.getMesh().maximumCoresByLinks();

		setBandwidth(c.getRequiredBitRate());

		setHops(c.getRoute().getHops());
    	setLength(c.getRoute().getDistanceAllLinks());

    	setCore(c.getIndexCore());
    	setModulation(c.getModulation().getM());
    	setFirstSlot(c.getSpectrumAssigned()[0]);
    	setLastSlot(c.getSpectrumAssigned()[1]);

    	setXT(c.getXt());
    	setOSNR(c.getSNR());


		List<Integer> coreUtilization = new ArrayList<>(numberOfCores);
    	for (int i = 0; i < numberOfCores; i++) {
    		int slotUtilizationByCore = 0;
    		if(c.getSpectrumUtilizationAbsolut() != null) {
    			slotUtilizationByCore = c.getSpectrumUtilizationAbsolut()[i];
    		}
    		coreUtilization.add(slotUtilizationByCore);
    	}

    	setCoreUtilization(coreUtilization);

    	setWasBlocked(c.isWasBlocked());
		setBlockCause(c.getBlockCause());
	}

	/**
	 * Returns the hops.
	 * @return the hops.
	 */
	public Integer getHops() {
		return hops;
	}

	/**
	 * Sets the hops.
	 * @param hops the hops.
	 */
	public void setHops(Integer hops) {
		this.hops = hops;
	}

	/**
	 * Returns the length.
	 * @return the length.
	 */
	public Double getLength() {
		return length;
	}

	/**
	 * Sets the length.
	 * @param length the length.
	 */
	public void setLength(Double length) {
		this.length = length;
	}

	/**
	 * Returns the core.
	 * @return the core.
	 */
	public Integer getCore() {
		return core;
	}

	/**
	 * Sets the core.
	 * @param core the core.
	 */
	public void setCore(Integer core) {
		this.core = core;
	}

	/**
	 * Returns the modulation.
	 * @return the modulation.
	 */
	public Double getModulation() {
		return modulation;
	}

	/**
	 * Sets the modulation.
	 * @param modulation the modulation.
	 */
	public void setModulation(Double modulation) {
		this.modulation = modulation;
	}

	/**
	 * Returns the first slot.
	 * @return the first slot.
	 */
	public Integer getFirstSlot() {
		return firstSlot;
	}

	/**
	 * Sets the first slot.
	 * @param firstSlot the firstSlot.
	 */
	public void setFirstSlot(Integer firstSlot) {
		this.firstSlot = firstSlot;
	}

	/**
	 * Returns the last slot.
	 * @return the last slot.
	 */
	public Integer getLastSlot() {
		return lastSlot;
	}

	/**
	 * Sets the last slot.
	 * @param lastSlot the lastSlot.
	 */
	public void setLastSlot(Integer lastSlot) {
		this.lastSlot = lastSlot;
	}

	/**
	 * Returns the xt.
	 * @return the xt.
	 */
	public Double getXT() {
		return XT;
	}

	/**
	 * Sets the xt.
	 * @param xT the xT.
	 */
	public void setXT(Double xT) {
		this.XT = xT;
	}

	/**
	 * Returns the osnr.
	 * @return the osnr.
	 */
	public Double getOSNR() {
		return OSNR;
	}

	/**
	 * Sets the osnr.
	 * @param oSNR the oSNR.
	 */
	public void setOSNR(Double oSNR) {
		this.OSNR = oSNR;
	}

	/**
	 * Returns the bandwidth.
	 * @return the bandwidth.
	 */
	public Double getBandwidth() {
		return bandwidth;
	}

	/**
	 * Sets the bandwidth.
	 * @param bandwidth the bandwidth.
	 */
	public void setBandwidth(Double bandwidth) {
		this.bandwidth = bandwidth;
	}

	/**
	 * Returns the core utilization.
	 * @return the core utilization.
	 */
	public List<Integer> getCoreUtilization() {
		return coreUtilization;
	}

	/**
	 * Sets the core utilization.
	 * @param coreUtilization the coreUtilization.
	 */
	public void setCoreUtilization(List<Integer> coreUtilization) {
		this.coreUtilization = coreUtilization;
	}

	/**
	 * Returns the was blocked.
	 * @return the was blocked.
	 */
	public Boolean getWasBlocked() {
		return wasBlocked;
	}

	/**
	 * Sets the was blocked.
	 * @param wasBlocked the wasBlocked.
	 */
	public void setWasBlocked(Boolean wasBlocked) {
		this.wasBlocked = wasBlocked;
	}

	/**
	 * Returns the block cause.
	 * @return the block cause.
	 */
	public Integer getBlockCause() {
		return blockCause;
	}

	/**
	 * Sets the block cause.
	 * @param blockCause the blockCause.
	 */
	public void setBlockCause(Integer blockCause) {
		this.blockCause = blockCause;
	}

}
