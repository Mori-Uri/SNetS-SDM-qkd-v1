package gprmcsa.modulation;

import network.PhysicalLayer;

import java.io.Serializable;

/**
 * This class represents the modulation formats.
 *
 * @author Iallen
 */
public class Modulation implements Serializable, Cloneable {

    private String name;
    private double maxRange; // max range in Km
    private double M; // Number of modulation format symbols
    private double bitsPerSymbol;

    private double SNRthreshold; // dB
    private double SNRthresholdLinear;

    private double XTthreshold; // dB
    private double XTthresholdLinear;

    private double rateFEC; // rate of Forward Error Correction
	private double freqSlot;

	private double p; // number of polarization modes

	/**
	 * Creates a new instance of Modulation
	 *
	 * @param name String
	 * @param maxRange double
	 * @param M double
	 * @param SNRthreshold double
	 * @param rateFEC double
	 * @param freqSlot double
	 * @param guardBand double
	 * @param p double
	 * @param XTthreshold double
	 */
    public Modulation(String name, double maxRange, double M, double SNRthreshold, double rateFEC, double freqSlot, double p, double XTthreshold) {
        this.name = name;
        this.maxRange = maxRange;
        this.M = M;
        this.SNRthreshold = SNRthreshold;
        this.rateFEC = rateFEC;
        this.freqSlot = freqSlot;
        this.p = p;
        this.XTthreshold = XTthreshold;

        this.bitsPerSymbol = PhysicalLayer.log2(M); // Calculation based on article: Capacity Limits of Optical Fiber Networks (2010)
        this.SNRthresholdLinear = PhysicalLayer.ratioOfDB(SNRthreshold);
        this.XTthresholdLinear = PhysicalLayer.ratioOfDB(XTthreshold);
    }

    /**
     * Returns the number of slots required according to the bit rate
     *
     * Based on articles:
     *  - Efficient Resource Allocation for All-Optical Multicasting Over Spectrum-Sliced Elastic Optical Networks (2013)
	 *  - Influence of Physical Layer Configuration on Performance of Elastic Optical OFDM Networks (2014)
	 *  - Analysis of the ASE noise impact in transparent elastic optical networks using multiple modulation formats (2015)
     *
     * @param bitRate
     * @return int - slotsNumberTemp
     */
    public int requiredSlots(double bitRate) {
    	double slotsNumber = (bitRate * (1.0 + rateFEC)) / (p * bitsPerSymbol * freqSlot);

        int slotsNumberTemp = (int)slotsNumber;
        if (slotsNumber - slotsNumberTemp != 0.0) {
        	slotsNumberTemp++;
        }

        //slotsNumberTemp = slotsNumberTemp + guardBand; // Adds another slot needed to be used as a guard band
        return slotsNumberTemp;
    }

    /**
     * Compute the potential bit rate when @slotsNumber slots are utilized
     *
     * @param slotsNumber int
     * @return double
     */
    public double potentialBitRate(int slotsNumber){
    	//slotsNumber = slotsNumber - guardBand; // Remove the slot required to be used as a guard band
        return (slotsNumber * p * bitsPerSymbol * freqSlot) / (1.0 + rateFEC);
    }

    /**
     * Returns the bandwidth from a bit rate
     *
     * @param bitRate
     * @return double
     */
    public double getBandwidthFromBitRate(double bitRate) {
    	double bandwidth = (bitRate * (1.0 + rateFEC)) / (p * bitsPerSymbol);
    	return bandwidth;
    }

	/**
	 * Returns the name of the modulation
	 *
     * @return the name - String
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the bits per symbol
     *
     * @return the bitsPerSymbol - double
     */
    public double getBitsPerSymbol() {
        return bitsPerSymbol;
    }

    /**
     * Returns the maximum range
     *
     * @return the maxRange - double
     */
    public double getMaxRange() {
        return maxRange;
    }

    /**
     * Sets the maximum range
     *
     * @param maxRange - double
     */
    public void setMaxRange(double maxRange) {
    	this.maxRange = maxRange;
    }

    /**
     * Returns the M
     *
     * @return double
     */
    public double getM() {
    	return M;
    }

    /**
     * Returns the SNR threshold (dB) of the modulation
     *
     * @return double
     */
    public double getSNRthreshold(){
    	return SNRthreshold;
    }

    /**
     * Returns the SNR threshold linear of the modulation
     *
     * @return double
     */
    public double getSNRthresholdLinear(){
    	return SNRthresholdLinear;
    }

    /**
     * Returns the XT threshold (dB) of the modulation
     *
     * @return double
     */
    public double getXTthreshold(){
    	return XTthreshold;
    }

    /**
     * Return the XT threshold linear of the modulation
     *
     * @return double
     */
    public double getXTthresholdLinear() {
		return XTthresholdLinear;
	}

	/**
	 * Creates and returns a copy of this object.
	 * @return the result.
	 */
	@Override
	public Object clone() throws CloneNotSupportedException{
		return super.clone();
    }
}
