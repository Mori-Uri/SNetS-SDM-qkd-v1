package network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

import gprmcsa.modulation.Modulation;
import gprmcsa.routing.Route;
import request.RequestForConnection;
import simulationControl.Util;
import simulationControl.parsers.PhysicalLayerConfig;
import simulationControl.parsers.NetworkConfig.CoreConfig;

/**
 * This class represents the physical layer of the optical network.
 *
 * @author Alexandre
 */
public class PhysicalLayer implements Serializable {

	public static final double maxOSNR = 999999.9; //Maximum OSNR value

	public static final int MODEL_HABIBI = 0; //Model Habibi
	public static final int MODEL_JOHANNISSON = 1; //Model Johannisson

	public static final int XT_SEPARATE = 0; //Crosstalk separate from other physical layer effects
	public static final int XT_TOGETHER = 1; //Crosstalk together with other physical layer effects

	public static final int AMP_GAIN_FIXED = 0; //Fixed amplifier gain
	public static final int AMP_GAIN_SATURATED = 1; //Saturated amplifier gain

	private int physicalLayerModel = MODEL_JOHANNISSON; //To select between Model Johannisson  or Model Habibi
	private int XTModel = XT_SEPARATE; //To select how to compute Crosstalk

	// Allows you to enable or disable the calculations of physical layer
    private boolean activeQoT; // QoTN
    private boolean activeQoTForOther; // QoTO

    private boolean activeASE; // Active the ASE noise of the amplifier
    private boolean activeNLI; // Active nonlinear noise in the fibers

    private boolean activeXT; // Active the Crosstalk
	private boolean activeXTForOther; // Active the Crosstalk on others circuits

    private double rateOfFEC; // FEC (Forward Error Correction), The most used rate is 7% which corresponds to the BER of 3.8E-3
    private int typeOfTestQoT; //0, To check for the SNR threshold (Signal-to-Noise Ratio), or another value, to check for the BER threshold (Bit Error Rate)

    private double power;           // Power per channel, dBm
    private double L;               // Size of a span, km
    private double alpha;           // Fiber loss, dB/km
    private double gamma;           // Fiber nonlinearity,  (W*m)^-1
    private double D;               // Dispersion parameter, s/m^2 or s/m/m
    private double centerFrequency; //Frequency of light

    private double h;                      // Constant of Planck
    private double NF;                     // Amplifier noise figure, dB
    private double pSat;                   // Saturation power of the amplifier, dBm
    private double A1;                     // Amplifier noise factor parameter, A1
    private double A2;                     // Amplifier noise factor parameter, A2
    private double amplificationFrequency; // Frequency used for amplification
    private int typeOfAmplifierGain = AMP_GAIN_FIXED; // Type of amplifier gain

    private double Lsss; // Switch insertion loss, dB
    private double LsssLinear;

    private boolean fixedPowerSpectralDensity; // To enable or disable fixed power spectral density
	private double referenceBandwidth; // Reference bandwidth for power spectral density

	private Amplifier boosterAmp; // Booster amplifier
	private Amplifier lineAmp;    // Line amplifier
	private Amplifier preAmp;     // Pre amplifier

	private double PowerLinear; // Transmitter power, Watt
	private double alphaLinear; // 1/m
	private double beta2;       // Group-velocity dispersion
	private double attenuationBySpanLinear;

	private double slotBandwidth; // Hz
	private double lowerFrequency; // Hz

	private double polarizationModes; // Number of polarization modes

	private Util util;
	private Crosstalk crosstalk;

	/**
	 * Creates a new instance of PhysicalLayerConfig
	 *
	 * @param plc PhysicalLayerConfig
	 */
    public PhysicalLayer(PhysicalLayerConfig plc, Mesh mesh, Util util){
        this.util = util;

        this.physicalLayerModel = plc.getPhysicalLayerModel();
        this.XTModel = plc.getCrosstalkModel();

    	this.activeQoT = plc.isActiveQoT();
        this.activeQoTForOther = plc.isActiveQoTForOther();

        this.activeASE = plc.isActiveASE();
        this.activeNLI = plc.isActiveNLI();

        this.activeXT = plc.isActiveXT();
        this.activeXTForOther = plc.isActiveXTForOther();

        this.typeOfTestQoT = plc.getTypeOfTestQoT();
        this.rateOfFEC = plc.getRateOfFEC();

        this.power = plc.getPower();
        this.L = plc.getSpanLength();
        this.alpha = plc.getFiberLoss();
        this.gamma = plc.getFiberNonlinearity();
        this.D = plc.getFiberDispersion();
        this.centerFrequency = plc.getCenterFrequency();

        this.h = plc.getConstantOfPlanck();
        this.NF = plc.getNoiseFigureOfOpticalAmplifier();
        this.pSat = plc.getPowerSaturationOfOpticalAmplifier();
        this.A1 = plc.getNoiseFactorModelParameterA1();
        this.A2 = plc.getNoiseFactorModelParameterA2();
        this.typeOfAmplifierGain = plc.getTypeOfAmplifierGain();
        this.amplificationFrequency = plc.getAmplificationFrequency();

        this.Lsss = plc.getSwitchInsertionLoss();
        this.LsssLinear = ratioOfDB(Lsss);

        this.fixedPowerSpectralDensity = plc.isFixedPowerSpectralDensity();
        this.referenceBandwidth = plc.getReferenceBandwidthForPowerSpectralDensity();

        this.polarizationModes = plc.getPolarizationModes();
        if(this.polarizationModes == 0.0) {
        	this.polarizationModes = 2.0;
        }

        this.PowerLinear = dBm_to_W(power); // converting to Watts
        this.alphaLinear = computeAlphaLinear(alpha);
        this.beta2 = computeBeta2(D, centerFrequency);

        double spanMeter = L * 1000.0; // span in meter
        this.attenuationBySpanLinear = Math.pow(Math.E, alphaLinear * spanMeter);
        double boosterAmpGainLinear = LsssLinear * LsssLinear * LsssLinear; //mux * demux * switch
        double lineAmpGainLinear = attenuationBySpanLinear;
        double preAmpGainLinear = attenuationBySpanLinear;

        this.boosterAmp = new Amplifier(ratioForDB(boosterAmpGainLinear), pSat, NF, h, amplificationFrequency, 0.0, A1, A2);
        this.lineAmp = new Amplifier(ratioForDB(lineAmpGainLinear), pSat, NF, h, amplificationFrequency, 0.0, A1, A2);
        this.preAmp = new Amplifier(ratioForDB(preAmpGainLinear), pSat, NF, h, amplificationFrequency, 0.0, A1, A2);

        this.slotBandwidth = mesh.getLinkList().firstElement().getCore(0).getSlotSpectrumBand(); //Hz
        double totalSlots = mesh.getLinkList().firstElement().getCore(0).getNumOfSlots();
		this.lowerFrequency = centerFrequency - (slotBandwidth * (totalSlots / 2.0)); // Hz, Half slots are removed because center Frequency = 193.55E+12 is the central frequency of the optical spectrum

		this.crosstalk = new Crosstalk(this, plc);
    }

	/**
	 * Returns if QoTN check is active or not
	 *
	 * @return the activeQoT
	 */
	public boolean isActiveQoT() {
		return activeQoT;
	}

	/**
	 * Returns if QoTO check is active or not
	 *
	 * @return the activeQoTForOther
	 */
	public boolean isActiveQoTForOther() {
		return activeQoTForOther;
	}

	/**
	 * Returns if XT check is active or not
	 *
	 * @return the activeQoT
	 */
	public boolean isActiveXT() {
		return activeXT;
	}

	/**
	 * Returns if XT on other circuits check is active or not
	 *
	 * @return the activeQoT
	 */
	public boolean isActiveXTForOther() {
		return activeXTForOther;
	}

	/**
	 * Returns the Size of a span (Km)
	 *
	 * @return the L
	 */
	public double getSpanLength() {
		return L;
	}

	/**
	 * Returns the rate of FEC
	 *
	 * @return double
	 */
	public double getRateOfFEC(){
		return rateOfFEC;
	}

	/**
	 * Return the number of polarization modes
	 *
	 * @return double
	 */
	public double getPolarizationModes() {
		return this.polarizationModes;
	}

	/**
	 * Return the fixed power spectral density
	 *
	 * @return boolean
	 */
	public boolean getFixedPowerSpectralDensity() {
		return fixedPowerSpectralDensity;
	}

	/**
	 * Sets the fixedPowerSpectralDensity
	 *
	 * @param value boolean
	 */
	public void setFixedPowerSpectralDensity(boolean value) {
		this.fixedPowerSpectralDensity = value;
	}

	/**
	 * Return the crosstalk
	 *
	 * @return crosstalk
	 */
	public Crosstalk getCrosstalk() {
		return crosstalk;
	}

	/**
	 * Return the XTModel
	 *
	 * @return XTModel
	 */
	public int getXTModel() {
		return XTModel;
	}

	/**
	 * Returns the power
	 *
	 * @return double
	 */
	public double getPower() {
		return power;
	}

	/**
	 * Returns the power linear
	 *
	 * @return double
	 */
	public double getPowerLinear() {
		return PowerLinear;
	}

	/**
	 * This method returns the number of amplifiers on a link including the booster and pre
	 *
	 * @param distance double
	 * @return double double
	 */
	public double getNumberOfAmplifiers(double distance){
		return 2.0 + roundUp((distance / L) - 1.0);
	}

	/**
	 * This method returns the number of line amplifiers on a link
	 *
	 * @param distance double
	 * @return double
	 */
	public double getNumberOfLineAmplifiers(double distance){
		return roundUp((distance / L) - 1.0);
	}

	/**
	 * This method calculates the BER threshold based on the SNR threshold of a given modulation format
	 *
	 * @param modulation Modulation
	 * @return double
	 */
	public double getBERthreshold(Modulation modulation){
		double BERthreshold = getBER(modulation.getSNRthresholdLinear(), modulation.getM());
		return BERthreshold;
	}

	/**
	 * Verifies if the calculated SNR for the circuit agrees to the modulation format threshold
	 *
	 * @param modulation Modulation
	 * @param SNRlinear double
	 * @return boolean
	 */
	public boolean isAdmissible(Modulation modulation, double SNRlinear){
		if(typeOfTestQoT == 0){ //Check by SNR threshold
			double SNRthreshold = modulation.getSNRthresholdLinear();

			if(SNRlinear >= SNRthreshold){
				return true;
			}

		} else { //Check by BER threshold
			double BERthreshold = getBERthreshold(modulation);
			double BER = getBER(SNRlinear, modulation.getM());

			if(BER <= BERthreshold){
				return true;
			}
		}

		return false;
	}

	/**
	 * Checks whether admissible2.
	 * @param bitRate the bitRate.
	 * @param modulation the modulation.
	 * @param OSNRlinear the OSNRlinear.
	 * @return true if the condition is met; false otherwise.
	 */
	public boolean isAdmissible2(double bitRate, Modulation modulation, double OSNRlinear){
		if(typeOfTestQoT == 0){ //Check by SNR threshold
			double SNRthresholdLinear = modulation.getSNRthresholdLinear();

			double B0 = 12.5E+9;
			double OverallBitRate = bitRate * (1.0 + rateOfFEC);
			double OSNRthreshold = (OverallBitRate / (2.0 * B0)) * SNRthresholdLinear;

			if(OSNRlinear >= OSNRthreshold){
				return true;
			}

		} else { //Check by BER threshold
			double BERthreshold = getBERthreshold(modulation);
			double BER = getBER(OSNRlinear, modulation.getM());

			if(BER <= BERthreshold){
				return true;
			}
		}

		return false;
	}

	/**
	 * Verifies that the QoT of the circuit is acceptable with the modulation format
	 * The circuit in question must not have allocated the network resources
	 *
	 * @param circuit Circuit
	 * @param route Route
	 * @param modulation Modulation
	 * @param spectrumAssigned int[]
	 * @param testCircuit Circuit
	 * @param addTestCircuit boolean
	 * @return boolean
	 */
	public boolean isAdmissibleModultion(Circuit circuit, Route route, Modulation modulation, int core, int spectrumAssigned[], Circuit testCircuit, boolean addTestCircuit){
		double SNR = computeSNRSegment(circuit, route, 0, route.getNodeList().size() - 1, modulation, core, spectrumAssigned, testCircuit, addTestCircuit);

		circuit.setSNRlinear(SNR);

		boolean QoT = isAdmissible(modulation, SNR);
		//boolean QoT2 = isAdmissible2(circuit.getRequiredBandwidth(), modulation, SNR);

		return QoT;
	}

	/**
	 * Verifies that the QoT of the circuit is acceptable with the modulation format for segment
	 * The circuit in question must not have allocated the network resources
	 *
	 * @param circuit Circuit
	 * @param route Route
	 * @param sourceNodeIndex int
	 * @param destinationNodeIndex int
	 * @param modulation Modulation
	 * @param spectrumAssigned int[]
	 * @param testCircuit Circuit
	 * @param addTestCircuit boolean
	 * @return boolean
	 */
	public boolean isAdmissibleModultionBySegment(Circuit circuit, Route route, int sourceNodeIndex, int destinationNodeIndex, Modulation modulation, int core, int spectrumAssigned[], Circuit testCircuit, boolean addTestCircuit){
		double SNR = computeSNRSegment(circuit, route, sourceNodeIndex, destinationNodeIndex, modulation, core, spectrumAssigned, testCircuit, addTestCircuit);

		circuit.setSNRlinear(SNR);

		boolean QoT = isAdmissible(modulation, SNR);
		//boolean QoT2 = isAdmissible2(circuit.getRequiredBandwidth(), modulation, SNR);

		return QoT;
	}

	/**
	 * Based on articles:
	 *  - Nonlinear Impairment Aware Resource Allocation in Elastic Optical Networks (2015)
	 *  - Modeling of Nonlinear Signal Distortion in Fiber-Optic Networks (2014)
	 *
	 * @param circuit Circuit
	 * @param route Route
	 * @param sourceNodeIndex int - Segment start node index
	 * @param destinationNodeIndex int - Segment end node index
	 * @param modulation Modulation
	 * @param spectrumAssigned int[]
	 * @param testCircuit Circuit - Circuit used to verify the impact on the other circuit informed
	 * @param addTestCircuit boolean - To add the test circuit to the circuit list
	 * @return double - SNR (linear)
	 */
	public double computeSNRSegment(Circuit circuit, Route route, int sourceNodeIndex, int destinationNodeIndex, Modulation modulation, int core, int spectrumAssigned[], Circuit testCircuit, boolean addTestCircuit){

		//double numSlotsRequired = spectrumAssigned[1] - spectrumAssigned[0] + 1; // Number of slots required
		double numSlotsRequired = modulation.requiredSlots(circuit.getRequiredBitRate()); // Number of slots required
		//double Bsi = (numSlotsRequired - circuit.getGuardBand()) * slotBandwidth; // Circuit bandwidth, less the guard band
		double Bsi = numSlotsRequired * slotBandwidth; // Circuit bandwidth
		double fi = lowerFrequency + (slotBandwidth * (spectrumAssigned[0] - 1.0)) + (Bsi / 2.0); // Central frequency of circuit

		Bsi = modulation.getBandwidthFromBitRate(circuit.getRequiredBitRate()); //Effective circuit bandwidth

		double circuitPowerLinear = getCircuitLaunchPower(circuit, modulation);
		if (physicalLayerModel == MODEL_JOHANNISSON) { // Model Johannisson
			circuitPowerLinear = circuitPowerLinear / polarizationModes; // Determining the power for each polarization mode
		}

		double I = getSignalPowerSpectralDensity(circuitPowerLinear, Bsi);

		double Iase = 0.0;
		double Inli = 0.0;
		double Ixt = 0.0;

		Node sourceNode = null;
		Node destinationNode = null;
		Link link = null;
		TreeSet<Circuit> circuitList = null;

		double Nl = 0.0; // Number of line amplifiers
		double noiseNli = 0.0;
		double totalPower = 0.0;
		double boosterAmpNoiseAse = 0.0;
		double preAmpNoiseAse = 0.0;
		double lineAmpNoiseAse = 0.0;
		double lastFiberSegment = 0.0;
		double Pxt = 0.0;

		for(int i = sourceNodeIndex; i < destinationNodeIndex; i++){
			sourceNode = route.getNode(i);
			destinationNode = route.getNode(i + 1);
			link = sourceNode.getOxc().linkTo(destinationNode.getOxc());
			Nl = getNumberOfLineAmplifiers(link.getDistance());

			circuitList = getCircuitList(link, core, circuit, testCircuit, addTestCircuit);

			if(activeNLI){
				if (physicalLayerModel == MODEL_JOHANNISSON) { // Model Johannisson
					noiseNli = getGnli(circuit, link, circuitPowerLinear, Bsi, I, fi, circuitList); // Computing the NLI for each polarization mode

				} else { // Model Habibi
					noiseNli = getGnli2(circuit, link, circuitPowerLinear, Bsi, I, fi, circuitList, modulation); // Computing the NLI for both polarization modes
				}

				noiseNli = (Nl + 1.0) * noiseNli; // Ns + 1 corresponds to the line amplifiers span more the preamplifier span
				Inli = Inli + noiseNli;
			}

			if(activeASE){
				if(typeOfAmplifierGain == AMP_GAIN_SATURATED){
					totalPower = getTotalPowerInTheLink(circuitList);
				}

				// Computing the last span amplifier gain
				lastFiberSegment = link.getDistance() - (Nl * L);
				preAmp.setGain(alpha * lastFiberSegment);

				// Computing the ASE for each amplifier type
				boosterAmpNoiseAse = boosterAmp.getAseByGain(totalPower, boosterAmp.getGainByType(totalPower, typeOfAmplifierGain));
				lineAmpNoiseAse = lineAmp.getAseByGain(totalPower, lineAmp.getGainByType(totalPower, typeOfAmplifierGain));
				preAmpNoiseAse = preAmp.getAseByGain(totalPower, preAmp.getGainByType(totalPower, typeOfAmplifierGain));

				if (physicalLayerModel == MODEL_JOHANNISSON) { // Model Johannisson
					// Determining the ASE for each polarization mode
					boosterAmpNoiseAse = boosterAmpNoiseAse / polarizationModes;
					lineAmpNoiseAse = lineAmpNoiseAse / polarizationModes;
					preAmpNoiseAse = preAmpNoiseAse / polarizationModes;
				}

				lineAmpNoiseAse = Nl * lineAmpNoiseAse; // Computing ASE for all line amplifier spans

				Iase = Iase + (boosterAmpNoiseAse + lineAmpNoiseAse + preAmpNoiseAse);
			}

			if(activeXT && XTModel == XT_TOGETHER){
				Pxt = crosstalk.calculateCrosstalk2(circuit, testCircuit, addTestCircuit);

				Ixt = Ixt + (Pxt / Bsi); // Convert to power spectral density
			}
		}

		// Corrects negative terms (which should never exist)
		if (I < 0.0) {
			I = 0.0;
			System.out.println("I is negative!");
		}
	    if (Iase < 0.0) {
	    	Iase = 0.0;
	    	System.out.println("Iase is negative!");
	    }
	    if (Inli < 0.0) {
	    	Inli = 0.0;
	    	System.out.println("Inli is negative!");
	    }
	    if (Ixt < 0.0) {
	    	Ixt = 0.0;
	    	System.out.println("Ixt is negative!");
	    }

		// Ensures that the denominator will never be zero.
		double denom = Iase + Inli + Ixt;
	    if (denom < 1e-30) {
	    	denom = 1e-30;
	    }

		double SNR = I / denom;

		// NLI vs. spans test
		/*
		double juraPnli = 0.0;
		double juraGnli = Inli;
		double juraBsi = Bsi;
		double juraGxt = I;
		double juraPxt = 0.0;
		double juraPnliNormalized = 0.0;
		double juraGase = Iase;
		double juraPase = 0.0;
		double juraPaseNormalized = 0.0;

		juraPxt = juraGxt * juraBsi;
		juraPnli = juraGnli * juraBsi;
		juraPase = juraGase * juraBsi;

		juraPnliNormalized = juraPnli / (juraPxt * juraPxt * juraPxt);
		juraPaseNormalized = juraPase / (juraPxt * juraPxt * juraPxt);

		System.out.println("---------------------\nHabibi:\nRoute distance: "+circuit.getRoute().getDistanceAllLinks()+"\nNumber of spans: "+circuit.getRoute().getDistanceAllLinks()/100+"\nBsi: "+Bsi+"\nModulation: "+circuit.getModulation().getName()+"\nNumber of slots: "+(circuit.getSpectrumAssigned()[1]-circuit.getSpectrumAssigned()[0])+"\nPxt: "+ratioForDB(juraPxt));
		System.out.println("\nPnli: "+ratioForDB(juraPnli));
		System.out.println("Pnli Normalized: "+ratioForDB(juraPnliNormalized));

		System.out.println("\nGase :"+ratioForDB(juraGase)+" dB");
		System.out.println("Pase :"+ratioForDB(juraPase)+" dB");
		System.out.println("Pase Normalized: "+ratioForDB(juraPaseNormalized)+" dB");

		System.out.println("\nOSNR: "+ratioForDB(juraGxt/(juraGase+juraGnli)));

		System.out.println("PNLI linear: "+juraPnli+"\nPch linear: "+juraPxt);
		*/

		return SNR;
	}

	/**
	 * Based on articles:
	 *  - Nonlinear Impairment Aware Resource Allocation in Elastic Optical Networks (2015)
	 *  - Modeling of Nonlinear Signal Distortion in Fiber-Optic Networks (2014)
	 *
	 * @param circuit Circuit
	 * @param route Route
	 * @param sourceNodeIndex int - Segment start node index
	 * @param destinationNodeIndex int - Segment end node index
	 * @param modulation Modulation
	 * @param spectrumAssigned int[]
	 * @param testCircuit Circuit - Circuit used to verify the impact on the other circuit informed
	 * @param addTestCircuit boolean - To add the test circuit to the circuit list
	 * @return double - SNR (linear)
	 */
	public double computeSNRSegment2(Circuit circuit, Route route, int sourceNodeIndex, int destinationNodeIndex, Modulation modulation, int core, int spectrumAssigned[], Circuit testCircuit, boolean addTestCircuit){

		//double numSlotsRequired = spectrumAssigned[1] - spectrumAssigned[0] + 1; // Number of slots required
		double numSlotsRequired = modulation.requiredSlots(circuit.getRequiredBitRate()); // Number of slots required
		//double Bsi = (numSlotsRequired - circuit.getGuardBand()) * slotBandwidth; // Circuit bandwidth, less the guard band
		double Bsi = numSlotsRequired * slotBandwidth; // Circuit bandwidth
		double fi = lowerFrequency + (slotBandwidth * (spectrumAssigned[0] - 1.0)) + (Bsi / 2.0); // Central frequency of circuit

		Bsi = modulation.getBandwidthFromBitRate(circuit.getRequiredBitRate()); //Effective circuit bandwidth

		double circuitPowerLinear = this.PowerLinear;
		if(circuit.getLaunchPowerLinear() != Double.POSITIVE_INFINITY) {
			circuitPowerLinear = circuit.getLaunchPowerLinear();
		}

		if (physicalLayerModel == MODEL_JOHANNISSON) { // Model Johannisson
			circuitPowerLinear = circuitPowerLinear / polarizationModes; // Determining the power for each polarization mode
		}

		double I = circuitPowerLinear / referenceBandwidth; // Signal power density for the reference bandwidth
		if(!fixedPowerSpectralDensity){
			I = circuitPowerLinear / Bsi; // Signal power spectral density calculated according to the requested bandwidth
		}

		double Iase = 0.0;
		double Inli = 0.0;
		double Ixt = 0.0;

		Node sourceNode = null;
		Node destinationNode = null;
		Link link = null;
		TreeSet<Circuit> circuitList = null;

		double Nl = 0.0; // Number of line amplifiers
		double noiseNli = 0.0;
		double totalPower = 0.0;
		double boosterAmpNoiseAse = 0.0;
		double preAmpNoiseAse = 0.0;
		double lineAmpNoiseAse = 0.0;
		double lastFiberSegment = 0.0;
		double Pxt = 0.0;

		for(int i = sourceNodeIndex; i < destinationNodeIndex; i++){
			sourceNode = route.getNode(i);
			destinationNode = route.getNode(i + 1);
			link = sourceNode.getOxc().linkTo(destinationNode.getOxc());
			Nl = getNumberOfLineAmplifiers(link.getDistance());

			circuitList = getCircuitList(link, core, circuit, testCircuit, addTestCircuit);

			if(activeNLI){
				if (physicalLayerModel == MODEL_JOHANNISSON) { // Model Johannisson
					noiseNli = getGnli(circuit, link, circuitPowerLinear, Bsi, I, fi, circuitList); // Computing the NLI for each polarization mode

				} else { // Model Habibi
					noiseNli = getGnli2(circuit, link, circuitPowerLinear, Bsi, I, fi, circuitList, modulation); // Computing the NLI for both polarization modes
				}

				noiseNli = (Nl + 1.0) * noiseNli; // Ns + 1 corresponds to the line amplifiers span more the preamplifier span
				Inli = Inli + noiseNli;
			}

			if(activeASE){
				if(typeOfAmplifierGain == AMP_GAIN_SATURATED){
					totalPower = getTotalPowerInTheLink(circuitList);
				}

				// Computing the last span amplifier gain
				lastFiberSegment = link.getDistance() - (Nl * L);
				preAmp.setGain(alpha * lastFiberSegment);

				// Computing the ASE for each amplifier type
				boosterAmpNoiseAse = boosterAmp.getAseByGain(totalPower, boosterAmp.getGainByType(totalPower, typeOfAmplifierGain));
				lineAmpNoiseAse = lineAmp.getAseByGain(totalPower, lineAmp.getGainByType(totalPower, typeOfAmplifierGain));
				preAmpNoiseAse = preAmp.getAseByGain(totalPower, preAmp.getGainByType(totalPower, typeOfAmplifierGain));

				if (physicalLayerModel == MODEL_JOHANNISSON) { // Model Johannisson
					// Determining the ASE for each polarization mode
					boosterAmpNoiseAse = boosterAmpNoiseAse / polarizationModes;
					lineAmpNoiseAse = lineAmpNoiseAse / polarizationModes;
					preAmpNoiseAse = preAmpNoiseAse / polarizationModes;
				}

				lineAmpNoiseAse = Nl * lineAmpNoiseAse; // Computing ASE for all line amplifier spans

				Iase = Iase + (boosterAmpNoiseAse + lineAmpNoiseAse + preAmpNoiseAse);
			}

			if(activeXT && XTModel == XT_TOGETHER){
				Pxt = crosstalk.calculateCrosstalk2(circuit, testCircuit, addTestCircuit);

				Ixt = Ixt + (Pxt / Bsi);
			}
		}

		double SNR = I / (Iase + Inli + Ixt);

		return SNR;
	}

	/**
	 * This method returns the circuit launch power linear
	 *
	 * @param circuit Circuit
	 * @return double - circuitPower (Linear)
	 */
	public double getCircuitLaunchPower(Circuit circuit, Modulation modulation) {
		double circuitPower = circuit.getLaunchPowerLinear();

		if (circuitPower == Double.POSITIVE_INFINITY) {
			circuitPower = this.PowerLinear; // If PSD is not fixed

			if (fixedPowerSpectralDensity) { // PSD is fixed
				double Bsi = modulation.getBandwidthFromBitRate(circuit.getRequiredBitRate()); //Circuit bandwidth
				double I = this.PowerLinear / this.referenceBandwidth; // PSD for the reference bandwidth

				circuitPower = I * Bsi; // Power calculated according to the requested bandwidth
			}

			circuit.setLaunchPowerLinear(circuitPower);
		}

		return circuitPower;
	}

	/**
	 * This method return the signal power spectral density
	 *
	 * @param circuit Circuit
	 * @param circuitPowerLinear double
	 * @param Bsi double
	 * @return double - PSD (Linear)
	 */
	public double getSignalPowerSpectralDensity(double circuitPowerLinear, double Bsi) {
		double PSD = 0.0; // Signal power spectral density

		if (fixedPowerSpectralDensity) { // PSD is fixed
			double referencePower = this.PowerLinear;

			if (physicalLayerModel == MODEL_JOHANNISSON) { // Model Johannisson
				referencePower = referencePower / polarizationModes; // Determining the power for each polarization mode
			}

			PSD = referencePower / this.referenceBandwidth; // PSD for the reference bandwidth

		} else {  // PSD is not fixed
			PSD = circuitPowerLinear / Bsi; // PSD calculated according to the requested bandwidth
		}

		return PSD;
	}

	/**
	 * Create a list of the circuits that use the link
	 *
	 * @param link Link
	 * @param circuit Circuit
	 * @param testCircuit Circuit
	 * @param addTestCircuit
	 * @return TreeSet<Circuit>
	 */
	private TreeSet<Circuit> getCircuitList(Link link, int core, Circuit circuit, Circuit testCircuit, boolean addTestCircuit){
		TreeSet<Circuit> circuitList = new TreeSet<Circuit>();

		for (Circuit circtuiTemp : link.getCore(core).getCircuitList()) {
			circuitList.add(circtuiTemp);
		}

		if(!circuitList.contains(circuit)){
			circuitList.add(circuit);
		}

		if(testCircuit != null && testCircuit.getRoute().containThisLink(link)) {

			if(!circuitList.contains(testCircuit) && addTestCircuit) {
				circuitList.add(testCircuit);
			}

			if(circuitList.contains(testCircuit) && !addTestCircuit) {
				circuitList.remove(testCircuit);
			}
		}

		return circuitList;
	}

	/**
	 * Total input power on the link
	 *
	 * @param circuitList TreeSet<Circuit>
	 * @return double
	 */
	public double getTotalPowerInTheLink(TreeSet<Circuit> circuitList){
		double totalPower = 0.0;
		double circuitPower = 0.0;

		for(Circuit circuitJ : circuitList){
			circuitPower = getCircuitLaunchPower(circuitJ, circuitJ.getModulation());

			if (physicalLayerModel == MODEL_JOHANNISSON) { // Model Johannisson
				circuitPower = circuitPower / polarizationModes; // Determining the power for each polarization mode
			}

			totalPower += circuitPower;
		}

		return totalPower;
	}

	/**
	 * Based on article:
	 *  - Nonlinear Impairment Aware Resource Allocation in Elastic Optical Networks (2015)
	 *
	 * @param circuitI Circuit
	 * @param link Link
	 * @param powerI double
	 * @param BsI double
	 * @param Gi double
	 * @param fI double
	 * @param circuitList TreeSet<Circuit>
	 * @return double
	 */
	public double getGnli(Circuit circuitI, Link link, double powerI, double BsI, double Gi, double fI, TreeSet<Circuit> circuitList){
		double beta21 = Math.abs(beta2);

		//double mi = Gi * (3.0 * gamma * gamma) / (2.0 * Math.PI * alphaLinear * beta21);
		double mi = Gi * (3.0 * 64.0 * gamma * gamma) / (2.0 * 81.0 * Math.PI * alphaLinear * beta21);
		//double ro =  BsI * BsI * (Math.PI * Math.PI * beta21) / (2.0 * alphaLinear);
		double ro =  BsI * BsI * (Math.PI * Math.PI * beta21) / alphaLinear;
		if (ro < 1.000001) { //Avoid negative or zero logs
			ro = 1.000001;
		}
		//double term_self = Gi * Gi * arcsinh(ro);
		double term_self = Gi * Gi * Math.log(ro);

		double term_cross = 0.0;
		int saJ[] = null;
		double numOfSlots = 0.0;
		double Bsj = 0.0;
		double fJ = 0.0;
		double deltaFij = 0.0;
		double d1 = 0.0;
		double d2 = 0.0;
		double ratio = 0.0;
		double ln = 0.0;
		double powerJ = powerI; // Power of the circuit j
		double Gj = Gi; // Power spectral density of the circuit j

		for(Circuit circuitJ : circuitList){

			if(!circuitI.equals(circuitJ)){
				saJ = circuitJ.getSpectrumAssignedByLink(link);
				//numOfSlots = saJ[1] - saJ[0] + 1.0;
				numOfSlots = circuitJ.getModulation().requiredSlots(circuitJ.getRequiredBitRate()); // Number of slots required

				//Bsj = (numOfSlots - circuitJ.getGuardBand()) * slotBandwidth; // Circuit bandwidth, less the guard band
				Bsj = numOfSlots * slotBandwidth; // Circuit bandwidth, less the guard band
				fJ = lowerFrequency + (slotBandwidth * (saJ[0] - 1.0)) + (Bsj / 2.0); // Central frequency of circuit

				Bsj = circuitJ.getModulation().getBandwidthFromBitRate(circuitJ.getRequiredBitRate());

				powerJ = getCircuitLaunchPower(circuitJ, circuitJ.getModulation());
				powerJ = powerJ / polarizationModes; // Determining the power for each polarization mode (Model Johannisson)

				//if(circuitJ.getLaunchPowerLinear() != Double.POSITIVE_INFINITY) {
				//	powerJ = circuitJ.getLaunchPowerLinear();
				//	powerJ = powerJ / polarizationModes; // Determining the power for each polarization mode
				//}

				if(!fixedPowerSpectralDensity){
					Gj = powerJ / Bsj; // Power spectral density of the circuit j calculated according to the required bandwidth
				}

				deltaFij = Math.abs(fI - fJ);

				d1 = deltaFij + (Bsj / 2.0);
				d2 = deltaFij - (Bsj / 2.0);

				if(d2 <= 0.0) { //To avoid division by zero
		            d2 = 1.0E-30;
				}

				ratio = d1 / d2;
				if (ratio < 1.000001) { //Avoid negative or zero logs
					ratio = 1.000001;
				}

				ln = Math.log(ratio);
				term_cross += Gj * Gj * ln;
			}
		}

		double gnli = mi * (term_self + term_cross);

		if(gnli < 0.0){
			gnli = 0.0;
		}

		return gnli;
	}

	/**
	 * Based on article:
	 *  - 2019 - Habibi - Impairment-Aware Manycast Routing, Modulation Level, and Spectrum Assignment in Elastic Optical Networks
	 *
	 * @param circuitI Circuit
	 * @param link Link
	 * @param powerI double
	 * @param BsI double
	 * @param Gi double
	 * @param fI double
	 * @param circuitList TreeSet<Circuit>
	 * @param modulation Modulation
	 * @return double
	 */
	public double getGnli2(Circuit circuitI, Link link, double powerI, double BsI, double Gi, double fI, TreeSet<Circuit> circuitList, Modulation modulation){
		double beta21 = Math.abs(beta2);

		double alfaCampo = alphaLinear / 2.0;
		double Ls = L * 1000.0; // span in meter
		double Leff = (1.0 - Math.pow(Math.E, -2.0 * alfaCampo * Ls)) / (2.0 * alfaCampo);
		double E = (8.0 * gamma * gamma * Leff * Leff * 2.0 * alfaCampo) / (27.0 * Math.PI * beta21);

		//Paper: Impairment-Aware Manycast Routing, Modulation Level, and Spectrum Assignment in Elastic Optical Networks (2019)
		double ro = (((Math.PI * Math.PI) / 2.0) * beta21 * BsI * BsI) / (2.0 * alfaCampo);
		double ro2 = E * arcsinh(ro);
		double gsci = Gi * Gi * Gi * ro2;
		double gxci = 0.0;

		// Correction term related to signal modulation
		double Wnn = 0;
		double Wnm = 0;

		//Paper: Impairment-Aware Manycast Routing, Modulation Level, and Spectrum Assignment in Elastic Optical Networks (2019)
		double Phii = getValueOfModulationConstant(modulation);
		Wnn = Gi * Gi * Gi * (E * (5.0 * Phii) / (3.0 * alfaCampo * Ls));
		Wnm = 0.0;

		int saJ[] = null;
		double numOfSlots = 0.0;
		double Bsj = 0.0;
		double fJ = 0.0;
		double deltaFij = 0.0;
		double d1 = 0.0;
		double d2 = 0.0;
		double powerJ = powerI; // Power of the circuit j
		double Gj = Gi; // Power spectral density of the circuit j

		for(Circuit circuitJ : circuitList){

			if(!circuitI.equals(circuitJ)){
				saJ = circuitJ.getSpectrumAssignedByLink(link);
				//numOfSlots = saJ[1] - saJ[0] + 1.0;
				numOfSlots = circuitJ.getModulation().requiredSlots(circuitJ.getRequiredBitRate()); // Number of slots required

				//Bsj = (numOfSlots - circuitJ.getGuardBand()) * slotBandwidth; // Circuit bandwidth, less the guard band
				Bsj = numOfSlots * slotBandwidth; // Circuit bandwidth
				fJ = lowerFrequency + (slotBandwidth * (saJ[0] - 1.0)) + (Bsj / 2.0); // Central frequency of circuit

				Bsj = circuitJ.getModulation().getBandwidthFromBitRate(circuitJ.getRequiredBitRate());

				powerJ = getCircuitLaunchPower(circuitJ, circuitJ.getModulation());

				if(!fixedPowerSpectralDensity){
					Gj = powerJ / Bsj; // Power spectral density of the circuit j calculated according to the required bandwidth
				}

				deltaFij = Math.abs(fI - fJ);

				d1 = deltaFij + (Bsj / 2.0);
				d2 = deltaFij - (Bsj / 2.0);

				double nm1 = (Math.PI * Math.PI * beta21 * BsI * d1) / (2.0 * alfaCampo);
				double nm2 = (Math.PI * Math.PI * beta21 * BsI * d2) / (2.0 * alfaCampo);
				double pnm =  E * (arcsinh(nm1) - arcsinh(nm2));
				gxci += Gi * Gj * Gj * pnm;

				//Paper: Impairment-Aware Manycast Routing, Modulation Level, and Spectrum Assignment in Elastic Optical Networks (2019)
				double Phij = getValueOfModulationConstant(circuitJ.getModulation());
				Wnm += Gi * Gj * Gj * (E * (5.0 * Phij * Bsj) / (6.0 * alfaCampo * Ls * deltaFij));
			}
		}

		//Paper: Impairment-Aware Manycast Routing, Modulation Level, and Spectrum Assignment in Elastic Optical Networks (2019)
		double gnli = gsci + gxci;
		double gcorr = Wnn + Wnm;

		//Applying the correction term for the GN model
		double gnli_res = gnli - gcorr;

		if(gnli_res < 0.0){
			gnli_res = 0.0;
		}

		return gnli_res;
	}

	/**
	 * Returns the value of modulation constant
	 *
	 * @param modulation Modulation
	 * @return double
	 */
	public double getValueOfModulationConstant(Modulation modulation) {
		double o = 0.0;

		if (modulation.getM() == 2.0) { //BPSK
			o = 1.0;
		} else if (modulation.getM() == 4.0) { //QPSK
			o = 1.0;
		} else if (modulation.getM() == 8.0) { //8QAM
			o = 2.0 / 3.0;
		} else if (modulation.getM() == 16.0) { //16QAM
			o = 17.0 / 25.0;
		} else if (modulation.getM() == 32.0) { //32QAM
			o = 69.0 / 100.0;
		} else if (modulation.getM() == 64.0) { //64QAM
			o = 13.0 / 21.0;
		} else if (modulation.getM() > 64.0) { //x-QAM
			o = 3.0 / 5.0;
		}

		return o;
	}

	/**
	 * Function that returns the inverse hyperbolic sine of the argument
	 * asinh == arcsinh
	 *
	 * @param x double
	 * @return double
	 */
	public static double arcsinh(double x){
		// Casos especiais
	    if (Double.isNaN(x)) return Double.NaN;
	    if (x == Double.POSITIVE_INFINITY) return Double.POSITIVE_INFINITY;
	    if (x == Double.NEGATIVE_INFINITY) return Double.NEGATIVE_INFINITY;
	    if (x == 0.0) return 0.0; // Preserva sinal de zero

	    final double absX = Math.abs(x);
	    final double sign = Math.copySign(1.0, x);

	    // 1. Taylor expansion for very small x
	    if (absX < 1e-4) {
	        double x2 = x * x;
	        return x * (1.0 - x2 * (1.0 / 6.0 - x2 * (3.0 / 40.0)));
	    }

	    // 2. Asymptotic approximation for very large x
	    if (absX > 1e6) {
	        double inv2 = 1.0 / (absX * absX);
	        double correction = (inv2 / 4.0) - (5.0 * inv2 * inv2 / 32.0);
	        return sign * (Math.log(2.0 * absX) - correction);
	    }

	    // 3. Intermediate region - standard calculation with numerical stability
	    // Uses log1p for better precision at medium/small values
	    double sqrtTerm = Math.sqrt(absX * absX + 1.0);
	    double inner = absX + (absX * absX) / (1.0 + sqrtTerm);
	    double result = Math.log1p(inner);

	    return sign * result;
	}

	/**
	 * This method returns the BER (Bit Error Rate) for a modulation scheme M-QAM.
	 * Based on articles:
	 *  - Capacity Limits of Optical Fiber Networks (2010)
	 *  - Analysis of the ASE noise impact in transparent elastic optical networks using multiple modulation formats (2015)
	 *
	 * @param SNR double
	 * @param M double
	 * @return double
	 */
	public static double getBER(double SNR, double M){
		double SNRb = SNR / log2(M); // SNR per bit -> this is probably incorrect; it would correspond to SNR per symbol

		double p1 = (3.0 * SNRb * log2(M)) / (2.0 * (M - 1.0));
		double p2 = erfc(Math.sqrt(p1));
		double BER = (2.0 / log2(M)) * ((Math.sqrt(M) - 1.0) / Math.sqrt(M)) * p2;

		return BER;
	}

	/**
	 * Complementary error function
	 *
	 * @param x double
	 * @return double
	 */
	public static double erfc(double x){
		return (1.0 - erf(x));
	}

	/**
	 * Error function - approximation
	 * http://www.galileu.esalq.usp.br/mostra_topico.php?cod=240
	 *
	 * @param x double
	 * @return double
	 */
	public static double erf(double x){
		double a = 0.140012;
		double v = sgn(x) * Math.sqrt(1.0 - Math.exp(-1.0 * (x * x) * (((4.0 / Math.PI) + (a * x * x)) / (1.0 + (a * x * x)))));
		return v;
	}

	/**
	 * Signal function
	 *
	 * @param x double
	 * @return double
	 */
	public static double sgn(double x){
		double s = 1.0;
		if(x < 0.0){
			s = s * -1.0;
		}else if(x == 0.0){
			s = 0.0;
		}
		return s;
	}

	/**
	 * Converts a ratio (linear) to decibel (dB)
	 *
	 * @param ratio double
	 * @return double dB
	 */
	public static double ratioForDB(double ratio) {
		double dB = 10.0 * Math.log10(ratio);
		return dB;
	}

	/**
	 * Converts a value in decibel (dB) to a linear value (ratio)
	 *
	 * @param dB double
	 * @return double ratio
	 */
	public static double ratioOfDB(double dB) {
		double ratio = Math.pow(10.0, (dB / 10.0));
		return ratio;
	}

	/**
	 * Converts a Watts (linear) to dBm
	 *
	 * @param w double
	 * @return double dBm
	 */
	public static double W_to_dBm(double w) {
		double dBm = 10.0 * Math.log10(w * 1000.0);
		return dBm;
	}

	/**
	 * Converts a value in dBm to a linear value (Watts)
	 *
	 * @param dBm double
	 * @return double Watts
	 */
	public static double dBm_to_W(double dBm) {
		double w = Math.pow(10.0, (dBm / 10.0)) * 1.0E-3;
		return w;
	}

	/**
	 * Logarithm in base 2
	 *
	 * @param x double
	 * @return double
	 */
	public static double log2(double x){
		return (Math.log10(x) / Math.log10(2.0));
	}

	/**
	 * Rounds up a double value for int
	 *
	 * @param res double
	 * @return int
	 */
	public static int roundUp(double res){
		if(res < 0.0) {
			return 0;
		}

		int res2 = (int) res;
		if(res - res2 != 0.0){
			res2++;
		}
		return res2;
	}

	/**
	 * Returns the beta2 parameter
	 *
	 * @param D double
	 * @param frequency double
	 * @return double
	 */
	public static double computeBeta2(double D, double frequencia){
		double c = 299792458.0; // speed of light, m/s
		double lambda = c / frequencia;
		double beta2 = -1.0 * D * (lambda * lambda) / (2.0 * Math.PI * c);
		return beta2;
	}

	/**
	 * Returns the alpha linear value (1/m) of a value in dB/km
	 *
	 * Example:
	 * 10 * Log10(e^(alpha * km)) = 0.2 dB/km
	 * (alpha * km) * 10 * Log10(e) = 0.2 dB/km
	 * alpha = (0.2 dB/km) / (km * 10 * Log10(e))
	 * alpha = (0.2 dB/km) / (1000 * m * 10 * Log10(e))
	 * alpha = (0.2 dB/km) / (10000 * Log10(e) * m)
	 * alpha (dB/km) = (0.2 dB/km) / (10000 * Log10(e) * m)
	 * alpha (linear) = (0.2 dB/km) / (10000 * Log10(e) * m * dB/km)
	 * alpha (linear) = 4.60517E-5 / m
	 *
	 * @param alpha double
	 * @return double
	 */
	public static double computeAlphaLinear(double alpha){
		double alphaLinear = alpha / (1.0E+4 * Math.log10(Math.E));
		return alphaLinear;
	}

	/**
	 * Calculates the transmission distances of the modulation formats
	 *
	 * @param mesh Mesh
	 * @param avaliableModulations List<Modulation>
	 * @return HashMap<Modulation, HashMap<Double, Double>>
	 */
	public HashMap<String, HashMap<Double, Double>> computesModulationsDistances(Mesh mesh, List<Modulation> avaliableModulations) {
		//System.out.println("Computing of the distances of the modulation formats");

		Set<Double> bitRateList = util.bitRateList;
		HashMap<String, HashMap<Double, Double>> modsTrsDistances = new HashMap<>();

		for(int m = 0; m < avaliableModulations.size(); m++) {
			Modulation mod = avaliableModulations.get(m);

			for(double bitRate : bitRateList) {

				HashMap<Double, Double> slotsDist = modsTrsDistances.get(mod.getName());
				if(slotsDist == null) {
					slotsDist = new HashMap<>();
					modsTrsDistances.put(mod.getName(), slotsDist);
				}

				Double dist = slotsDist.get(bitRate);
				if(dist == null) {
					dist = 0.0;
				}
				slotsDist.put(bitRate, dist);
			}
		}

		for(int m = 0; m < avaliableModulations.size(); m++) {
			Modulation mod = avaliableModulations.get(m);

			for(double bitRate : bitRateList) {

				double distance = computeModulationDistanceByBitRate(mod, bitRate, mesh);
				modsTrsDistances.get(mod.getName()).put(bitRate, distance);
			}
		}


//		for(double transmissionRate : transmissionRateList) {
//			System.out.println("TR(Gbps) = " + (transmissionRate / 1.0E+9));
//
//			for(int m = 0; m < avaliableModulations.size(); m++) {
//				Modulation mod = avaliableModulations.get(m);
//				int slotNumber = mod.requiredSlots(transmissionRate) - mod.getGuardBand();
//
//				System.out.println("Mod = " + mod.getName() + ", slot num = " + slotNumber);
//			}
//		}


//		for(double transmissionRate : transmissionRateList) {
//			System.out.println("TR(Gbps) = " + (transmissionRate / 1.0E+9));
//
//			for(int m = 0; m < avaliableModulations.size(); m++) {
//				Modulation mod = avaliableModulations.get(m);
//
//				double modTrDist = modsTrsDistances.get(mod.getName()).get(transmissionRate);
//				System.out.println("Mod = " + mod.getName() + ", distance(km) = " + modTrDist);
//			}
//		}

		return modsTrsDistances;
	}


	// New method to compute the modulation distance
	/**
	 * Computes the modulation distance by bit rate.
	 * @param mod the mod.
	 * @param bitRate the bitRate.
	 * @param mesh the mesh.
	 * @return the result of the operation.
	 */
	public double computeModulationDistanceByBitRate(Modulation mod, double bitRate, Mesh mesh) {

		int totalSlots = mesh.getLinkList().firstElement().getCore(0).getNumOfSlots();
		int guardBand = mesh.getGuardBand();
		//int numberOfCores = mesh.getLinkList().firstElement().getNumberOfCores();

		double minDistance = 12.5; //km
		double totalDistance = 50000.0; //km
		int quantSpansPorEnlace = (int)(totalDistance / minDistance); // number of spans per link

		int slotNumber = mod.requiredSlots(bitRate);
		int sa[] = new int[2];
		sa[0] = 1;
		sa[1] = sa[0] + slotNumber - 1;
		int indexCore = 0;

		List<CoreConfig> coreList = new ArrayList<>();
		coreList.add(new CoreConfig(0, new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6))));
		coreList.add(new CoreConfig(1, new ArrayList<>(Arrays.asList(0, 2, 6))));
		coreList.add(new CoreConfig(2, new ArrayList<>(Arrays.asList(0, 1, 3))));
		coreList.add(new CoreConfig(3, new ArrayList<>(Arrays.asList(0, 2, 4))));
		coreList.add(new CoreConfig(4, new ArrayList<>(Arrays.asList(0, 3, 5))));
		coreList.add(new CoreConfig(5, new ArrayList<>(Arrays.asList(0, 4, 6))));
		coreList.add(new CoreConfig(6, new ArrayList<>(Arrays.asList(0, 1, 5))));

		double modTrDistance = 0.0;

		for(int ns = 1; ns <= quantSpansPorEnlace; ns++){
			double distance = ns * minDistance;

			Node n1 = new Node("1", 1000, 1000, 0, 1000);
			Node n2 = new Node("2", 1000, 1000, 0, 1000);
			n1.getOxc().addLink(new Link(n1.getOxc(), n2.getOxc(), totalSlots, slotBandwidth, distance, coreList));

			Vector<Node> listNodes = new Vector<Node>();
			listNodes.add(n1);
			listNodes.add(n2);

			Route route = new Route(listNodes);
			Pair pair = new Pair(n1, n2);

			RequestForConnection requestTemp = new RequestForConnection();
			requestTemp.setPair(pair);
			requestTemp.setRequiredBitRate(bitRate);

			Circuit circuitTemp = new Circuit();
			circuitTemp.setPair(pair);
			circuitTemp.setRoute(route);
			circuitTemp.setModulation(mod);
			circuitTemp.setGuardBand(guardBand);
			circuitTemp.setIndexCore(indexCore);
			circuitTemp.setSpectrumAssigned(sa);
			circuitTemp.addRequest(requestTemp);

			double launchPower = Double.POSITIVE_INFINITY;
			if(!fixedPowerSpectralDensity){
				launchPower = computeMaximumPower(circuitTemp, bitRate, route, 0, route.getNodeList().size() - 1, mod, indexCore, sa);
			}
			circuitTemp.setLaunchPowerLinear(launchPower);

			route.getLink(0).getCore(0).addCircuit(circuitTemp);

			double OSNR = computeSNRSegment(circuitTemp, circuitTemp.getRoute(), 0, circuitTemp.getRoute().getNodeList().size() - 1, circuitTemp.getModulation(), circuitTemp.getIndexCore(), circuitTemp.getSpectrumAssigned(), null, false);
			double OSNRdB = PhysicalLayer.ratioForDB(OSNR);

			if((OSNRdB >= mod.getSNRthreshold()) && (distance > modTrDistance)){
				modTrDistance = distance;
			}
		}

		return modTrDistance;
	}

	// New method to compute distances
	/**
	 * Computes the modulation distance by bandwidth3.
	 * @param mod the mod.
	 * @param bitRate the bitRate.
	 * @param mesh the mesh.
	 * @return the result of the operation.
	 */
	public double computeModulationDistanceByBandwidth3(Modulation mod, double bitRate, Mesh mesh) {

		int totalSlots = mesh.getLinkList().firstElement().getCore(0).getNumOfSlots();
		int guardBand = mesh.getGuardBand();
		//int numberOfCores  = mesh.getLinkList().firstElement().getNumberOfCores();
		int indexCore = 0;

		double minDistance = 12.5; // km, minimum distance
		double totalDistance = 50000.0; // km, maximum distance
		int quantSpansPorEnlace = (int)(totalDistance / minDistance); // number of spans per link

		int slotNumber = mod.requiredSlots(bitRate);

		List<CoreConfig> coreList = new ArrayList<>();
		coreList.add(new CoreConfig(0, new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6))));
		coreList.add(new CoreConfig(1, new ArrayList<>(Arrays.asList(0, 2, 6))));
		coreList.add(new CoreConfig(2, new ArrayList<>(Arrays.asList(0, 1, 3))));
		coreList.add(new CoreConfig(3, new ArrayList<>(Arrays.asList(0, 2, 4))));
		coreList.add(new CoreConfig(4, new ArrayList<>(Arrays.asList(0, 3, 5))));
		coreList.add(new CoreConfig(5, new ArrayList<>(Arrays.asList(0, 4, 6))));
		coreList.add(new CoreConfig(6, new ArrayList<>(Arrays.asList(0, 1, 5))));

		double modTrDistance = 0.0;

		for(int ns = 1; ns <= quantSpansPorEnlace; ns++){
			double distance = ns * minDistance;

			Node n1 = new Node("1", 1000, 1000, 0, 1000);
			Node n2 = new Node("2", 1000, 1000, 0, 1000);
			n1.getOxc().addLink(new Link(n1.getOxc(), n2.getOxc(), totalSlots, slotBandwidth, distance, coreList));

			Vector<Node> listNodes = new Vector<Node>();
			listNodes.add(n1);
			listNodes.add(n2);

			Route route = new Route(listNodes);
			Pair pair = new Pair(n1, n2);

			int Nch = 15; //number of circuits
			Circuit circuitCentral = null;

			int saTemp = 0;

			for (int c = 1; c <= Nch; c++) {
				RequestForConnection requestTemp = new RequestForConnection();
				requestTemp.setPair(pair);
				requestTemp.setRequiredBitRate(bitRate);

				int sa[] = new int[2];
				sa[0] = saTemp + 1;
				sa[1] = sa[0] + slotNumber - 1;

				saTemp = sa[1]; //stores the final slot of the circuit

				Circuit circuitTemp = new Circuit();
				circuitTemp.setPair(pair);
				circuitTemp.setRoute(route);
				circuitTemp.setModulation(mod);
				circuitTemp.setSpectrumAssigned(sa);
				circuitTemp.setGuardBand(guardBand);
				circuitTemp.setIndexCore(indexCore);
				circuitTemp.addRequest(requestTemp);

				double launchPower = Double.POSITIVE_INFINITY;
				if(!fixedPowerSpectralDensity){
					launchPower = computeMaximumPower(circuitTemp, bitRate, route, 0, route.getNodeList().size() - 1, mod, indexCore, sa);
				}
				circuitTemp.setLaunchPowerLinear(launchPower);

				route.getLink(0).getCore(1).addCircuit(circuitTemp);

				if (c == 8) { //central circuit
					circuitCentral = circuitTemp;
				}
			}

			double OSNR = computeSNRSegment(circuitCentral, circuitCentral.getRoute(), 0, circuitCentral.getRoute().getNodeList().size() - 1, circuitCentral.getModulation(), circuitCentral.getIndexCore(), circuitCentral.getSpectrumAssigned(), null, false);
			double OSNRdB = PhysicalLayer.ratioForDB(OSNR);

			if((OSNRdB >= mod.getSNRthreshold()) && (distance > modTrDistance)){
				modTrDistance = distance;
			}
		}

		return modTrDistance;
	}


	// Jurandir test. NLI vs. span
	/**
	 * Computes the modulation distance by bandwidth4.
	 * @param mod the mod.
	 * @param bitRate the bitRate.
	 * @param mesh the mesh.
	 * @return the result of the operation.
	 */
	public double computeModulationDistanceByBandwidth4(Modulation mod, double bitRate, Mesh mesh) {

		int totalSlots = mesh.getLinkList().firstElement().getCore(0).getNumOfSlots();
		int guardBand = mesh.getGuardBand();
		//int numberOfCores = mesh.getLinkList().firstElement().getNumberOfCores();
		int indexCore = 0;

		double minDistance = 100.0; // km, minimum distance
		double totalDistance = 50000.0; // km, maximum distance
		int quantSpansPorEnlace = (int)(totalDistance / minDistance); // number of spans per link

		int slotNumber = mod.requiredSlots(bitRate);

		List<CoreConfig> coreList = new ArrayList<>();
		coreList.add(new CoreConfig(0, new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6))));
		coreList.add(new CoreConfig(1, new ArrayList<>(Arrays.asList(0, 2, 6))));
		coreList.add(new CoreConfig(2, new ArrayList<>(Arrays.asList(0, 1, 3))));
		coreList.add(new CoreConfig(3, new ArrayList<>(Arrays.asList(0, 2, 4))));
		coreList.add(new CoreConfig(4, new ArrayList<>(Arrays.asList(0, 3, 5))));
		coreList.add(new CoreConfig(5, new ArrayList<>(Arrays.asList(0, 4, 6))));
		coreList.add(new CoreConfig(6, new ArrayList<>(Arrays.asList(0, 1, 5))));

		double modTrDistance = 0.0;

		int numSpan = 50;

		for(int ns = 1; ns <= numSpan; ns++){
			double distance = ns * minDistance;

			Node n1 = new Node("1", 1000, 1000, 0, 1000);
			Node n2 = new Node("2", 1000, 1000, 0, 1000);
			n1.getOxc().addLink(new Link(n1.getOxc(), n2.getOxc(), totalSlots, slotBandwidth, distance, coreList));

			Vector<Node> listNodes = new Vector<Node>();
			listNodes.add(n1);
			listNodes.add(n2);

			Route route = new Route(listNodes);
			Pair pair = new Pair(n1, n2);

			int Nch = 15; //number of circuits
			Circuit circuitCentral = null;

			int saTemp = 0;

			for (int c = 1; c <= Nch; c++) {
				RequestForConnection requestTemp = new RequestForConnection();
				requestTemp.setPair(pair);
				requestTemp.setRequiredBitRate(bitRate);

				int sa[] = new int[2];
				sa[0] = saTemp + 1;
				sa[1] = sa[0] + slotNumber - 1;

				saTemp = sa[1]; //stores the final slot of the circuit

				Circuit circuitTemp = new Circuit();
				circuitTemp.setPair(pair);
				circuitTemp.setRoute(route);
				circuitTemp.setModulation(mod);
				circuitTemp.setSpectrumAssigned(sa);
				circuitTemp.setGuardBand(guardBand);
				circuitTemp.setIndexCore(indexCore);
				circuitTemp.addRequest(requestTemp);

				double launchPower = Double.POSITIVE_INFINITY;
				if(!fixedPowerSpectralDensity){
					launchPower = computeMaximumPower(circuitTemp, bitRate, route, 0, route.getNodeList().size() - 1, mod, indexCore, sa);
				}
				circuitTemp.setLaunchPowerLinear(launchPower);

				route.getLink(0).getCore(1).addCircuit(circuitTemp);

				if (c == 8) { //central circuit
					circuitCentral = circuitTemp;
				}
			}

			System.out.println("\n\n\n"+ns);

			double OSNR = computeSNRSegment(circuitCentral, circuitCentral.getRoute(), 0, circuitCentral.getRoute().getNodeList().size() - 1, circuitCentral.getModulation(), circuitCentral.getIndexCore(), circuitCentral.getSpectrumAssigned(), null, false);
			double OSNRdB = PhysicalLayer.ratioForDB(OSNR);

			System.out.println("\n\n\n");


			if((OSNRdB >= mod.getSNRthreshold()) && (distance > modTrDistance)){
				modTrDistance = distance;
			}
		}

		return modTrDistance;
	}

	/**
	 * Calculates the distance to a modulation format considering the bandwidth
	 *
	 * @param mod
	 * @param bitRate
	 * @param mesh
	 * @return double
	 */
/*	public double computeModulationDistanceByBandwidth(Modulation mod, double bitRate, Mesh mesh) {

		int totalSlots = mesh.getLinkList().firstElement().getCore(0).getNumOfSlots();
		int numberOfCores = mesh.getLinkList().firstElement().getNumberOfCores();

		Vector<Link> linkList = mesh.getLinkList();
		double sumLastFiberSegment = 0.0;
		for(int l = 0; l < linkList.size(); l++) {
			double Ns = getNumberOfLineAmplifiers(linkList.get(l).getDistance());
			double lastFiberSegment = linkList.get(l).getDistance() - (Ns * L);
			sumLastFiberSegment += lastFiberSegment;
		}
		double averageLastFiberSegment = sumLastFiberSegment / linkList.size();

		double totalDistance = 50000.0; //km
		int quantSpansPorEnlace = (int)(totalDistance / L); // number of spans per link

		int slotNumber = mod.requiredSlots(bitRate);
		int sa[] = new int[2];
		sa[0] = 1;
		sa[1] = sa[0] + slotNumber - 1;

		double modTrDistance = 0.0;

		for(int ns = 0; ns <= quantSpansPorEnlace; ns++){
			double distance = (ns * L) + averageLastFiberSegment;

			Node n1 = new Node("1", 1000, 1000, 0, 1000);
			Node n2 = new Node("2", 1000, 1000, 0, 1000);
			n1.getOxc().addLink(new Link(n1.getOxc(), n2.getOxc(), totalSlots, slotBandwidth, distance));

			Vector<Node> listNodes = new Vector<Node>();
			listNodes.add(n1);
			listNodes.add(n2);

			Route route = new Route(listNodes);
			Pair pair = new Pair(n1, n2);

			RequestForConnection requestTemp = new RequestForConnection();
			requestTemp.setPair(pair);
			requestTemp.setRequiredBandwidth(bitRate);

			Circuit circuitTemp = new Circuit();
			circuitTemp.setPair(pair);
			circuitTemp.setRoute(route);
			circuitTemp.setModulation(mod);
			circuitTemp.setSpectrumAssigned(sa);
			circuitTemp.addRequest(requestTemp);

			double launchPower = Double.POSITIVE_INFINITY;
			if(!fixedPowerSpectralDensity){
				launchPower = computeMaximumPower2(bitRate, route, 0, route.getNodeList().size() - 1, mod, sa);
			}
			circuitTemp.setLaunchPowerLinear(launchPower);

			route.getLink(0).getCore(0).addCircuit(circuitTemp); // Check!!!

			double OSNR = computeSNRSegment(circuitTemp, circuitTemp.getRoute(), 0, circuitTemp.getRoute().getNodeList().size() - 1, circuitTemp.getModulation(), circuitTemp.getSpectrumAssigned(), null, false);
			double OSNRdB = PhysicalLayer.ratioForDB(OSNR);

			if((OSNRdB >= mod.getSNRthreshold()) && (distance > modTrDistance)){
				modTrDistance = distance;
			}
		}

		return modTrDistance;
	}
*/

	/**
	 * This method searches for optimal power, considering constraints, that produces the highest possible ONSR.
	 * The constraints consider both ONSR and crosstalk.
	 *
	 * @param bitRate double
	 * @param route Route
	 * @param sourceNodeIndex int
	 * @param destinationNodeIndex int
	 * @param modulation Modulation
	 * @param spectrumAssigned int[]
	 * @return double - Linear value
	 */
	public double computeMaximumPower(Circuit circuit, double bitRate, Route route, int sourceNodeIndex, int destinationNodeIndex, Modulation modulation, int core, int spectrumAssigned[]){

		//Power that maximizes OSNR
		double PmaxOSNR = computePowerThatMaximizesOSNR(circuit, bitRate, route, sourceNodeIndex, destinationNodeIndex, modulation, core, spectrumAssigned);

		// Check if it really is the optical power
		PmaxOSNR = checkMaximumPower(PmaxOSNR, circuit, route, modulation, core, spectrumAssigned);

		if (activeXT && XTModel == XT_SEPARATE) {

			//The maximum power that does not violate the XT threshold for a modulation M
			double PmaxXT = crosstalk.computeMaximumPowerAllowedByXT(circuit, route, modulation, core, spectrumAssigned);

			//Optimal Power is the point within this window that produces the highest possible OSNR
			double Popt = Math.min(PmaxOSNR, PmaxXT);

			PmaxOSNR = Popt;
		}

		return PmaxOSNR;
	}

	/**
	 * This method computes the launch power that reaches the maximum OSNR.
	 *
	 * @param circuit Circuit
	 * @param bitRate double
	 * @param route Route
	 * @param sourceNodeIndex int
	 * @param destinationNodeIndex int
	 * @param modulation Modulation
	 * @param core int
	 * @param spectrumAssigned int[]
	 * @return double (Linear power)
	 */
	public double computePowerThatMaximizesOSNR(Circuit circuit, double bitRate, Route route, int sourceNodeIndex, int destinationNodeIndex, Modulation modulation, int core, int spectrumAssigned[]){

		//double numSlotsRequired = spectrumAssigned[1] - spectrumAssigned[0] + 1; // Number of slots required
		//double Bsi = (numSlotsRequired - modulation.getGuardBand()) * slotBandwidth; // Circuit bandwidth, less the guard band
		double Bsi = modulation.getBandwidthFromBitRate(bitRate); // Circuit bandwidth

		//double I = getSignalPowerSpectralDensity(this.PowerLinear, Bsi); // Signal power spectral density

		Node sourceNode = null;
		Node destinationNode = null;
		Link link = null;

		double Iase = 0.0;
		double Inli = 0.0;

		double Nl = 0.0; // Number of line amplifiers
		double totalPower = 0.0;
		double boosterAmpGain = 0.0;
		double lineAmpGain = 0.0;
		double preAmpGain = 0.0;
		double boosterAmpNoiseAse = 0.0;
		double preAmpNoiseAse = 0.0;
		double lineAmpNoiseAse = 0.0;
		double lastFiberSegment = 0.0;

		for(int i = sourceNodeIndex; i < destinationNodeIndex; i++){
			sourceNode = route.getNode(i);
			destinationNode = route.getNode(i + 1);
			link = sourceNode.getOxc().linkTo(destinationNode.getOxc());
			Nl = getNumberOfLineAmplifiers(link.getDistance());

			double Snli = 0.0;
			double Sase = 0.0;
			//double lossAndGain = 1.0;

			if(activeNLI){
				double beta21 = Math.abs(beta2);

				if (physicalLayerModel == MODEL_JOHANNISSON) { // Model Johannisson

					double mi = (1.0/Bsi) * (3.0 * 64.0 * gamma * gamma) / (2.0 * 81.0 * Math.PI * alphaLinear * beta21);
					double ro =  Bsi * Bsi * (Math.PI * Math.PI * beta21) / alphaLinear;
					if (ro < 1.000001) { //Avoid negative or zero logs
						ro = 1.000001;
					}
					double p1 = (1.0/Bsi) * (1.0/Bsi) * Math.log(ro);
					Snli = mi * p1;

					if(Snli < 0.0) {
						Snli = 0.0;
					}

				} else { // Model Habibi

					double Phii = getValueOfModulationConstant(modulation);

					double alfaCampo = alphaLinear / 2.0;
					double Ls = L * 1000.0; // span in meter
					double Leff = (1.0 - Math.pow(Math.E, -2.0 * alfaCampo * Ls)) / (2.0 * alfaCampo);
					double E = (8.0 * gamma * gamma * Leff * Leff * 2.0 * alfaCampo) / (27.0 * Math.PI * beta21);

					double ro = (((Math.PI * Math.PI) / 2.0) * beta21 * Bsi * Bsi) / (2.0 * alfaCampo);
					double ro2 = E * arcsinh(ro);
					double gsci = (1.0/Bsi) * (1.0/Bsi) * (1.0/Bsi) * ro2;

					double Wnn = (1.0/Bsi) * (1.0/Bsi) * (1.0/Bsi) * (E * (5.0 * Phii) / (3.0 * alfaCampo * Ls));

					Snli = gsci - Wnn;

					if(Snli < 0.0) {
						Snli = 0.0;
					}
				}

				Snli = (Nl + 1.0) * Snli; // Nl + 1 corresponds to the line amplifiers span more the preamplifier span
			}

			if(activeASE){
				// Computing the last span amplifier gain
				lastFiberSegment = link.getDistance() - (Nl * L);
				preAmp.setGain(alpha * lastFiberSegment);

				// Amplifier gain
				boosterAmpGain = boosterAmp.getGainByType(totalPower, typeOfAmplifierGain);
				lineAmpGain = lineAmp.getGainByType(totalPower, typeOfAmplifierGain);
				preAmpGain = preAmp.getGainByType(totalPower, typeOfAmplifierGain);

				// Computing the ASE for each amplifier type
				boosterAmpNoiseAse = boosterAmp.getAseByGain(totalPower, boosterAmpGain);
				lineAmpNoiseAse = lineAmp.getAseByGain(totalPower, lineAmpGain);
				preAmpNoiseAse = preAmp.getAseByGain(totalPower, preAmpGain);

				if (physicalLayerModel == MODEL_JOHANNISSON) { // Model Johannisson
					// Determining the ASE for each polarization mode
					boosterAmpNoiseAse = boosterAmpNoiseAse / polarizationModes;
					lineAmpNoiseAse = lineAmpNoiseAse / polarizationModes;
					preAmpNoiseAse = preAmpNoiseAse / polarizationModes;
				}

				lineAmpNoiseAse = Nl * lineAmpNoiseAse; // Computing ASE for all line amplifier spans

				Sase = (boosterAmpNoiseAse + lineAmpNoiseAse + preAmpNoiseAse);

				//lossAndGain = lossAndGain * (boosterAmpGain / attenuationBySpanLinear);
				//lossAndGain = Nl * (lossAndGain * (lineAmpGain / attenuationBySpanLinear));
				//lossAndGain = lossAndGain * (preAmpGain / attenuationBySpanLinear);
			}

			//Inli += Snli * lossAndGain;
			//Iase += Sase * lossAndGain;
			Inli += Snli;
			Iase += Sase;
		}

		// Calcula denominador
		double denominator = 2.0 * Inli;

		// Protects against division by zero while preserving the sign
		if (Math.abs(denominator) < 1E-12) {
		    denominator = Math.copySign(1E-12, denominator);
		}

		double Pmax = Math.cbrt(Iase / denominator);

		return Pmax;
	}

	/**
	 * This method computes the launch power that reaches the maximum OSNR.
	 * In this method, gains and losses are applied at each step.
	 *
	 * @param circuit Circuit
	 * @param bitRate double
	 * @param route Route
	 * @param sourceNodeIndex int
	 * @param destinationNodeIndex int
	 * @param modulation Modulation
	 * @param core int
	 * @param spectrumAssigned int[]
	 * @return double (Linear power)
	 */
	public double computePowerThatMaximizesOSNR2(Circuit circuit, double bitRate, Route route, int sourceNodeIndex, int destinationNodeIndex, Modulation modulation, int core, int spectrumAssigned[]){

		double Iase = 0.0;
		double Inli = 0.0;

		//double numSlotsRequired = spectrumAssigned[1] - spectrumAssigned[0] + 1; // Number of slots required
		//double Bsi = (numSlotsRequired - modulation.getGuardBand()) * slotBandwidth; // Circuit bandwidth, less the guard band
		double Bsi = modulation.getBandwidthFromBitRate(bitRate); // Circuit bandwidth

		//double I = getSignalPowerSpectralDensity(this.PowerLinear, Bsi); // Signal power spectral density

		Node sourceNode = null;
		Node destinationNode = null;
		Link link = null;

		double Nl = 0.0; // Number of line amplifiers
		double totalPower = 0.0;

		for(int i = sourceNodeIndex; i < destinationNodeIndex; i++){
			sourceNode = route.getNode(i);
			destinationNode = route.getNode(i + 1);
			link = sourceNode.getOxc().linkTo(destinationNode.getOxc());
			Nl = getNumberOfLineAmplifiers(link.getDistance());

			// Switch insertion loss
			Inli = Inli / LsssLinear;
			Iase = Iase / LsssLinear;

			// MUX insertion loss
			Inli = Inli / LsssLinear;
			Iase = Iase / LsssLinear;

			Inli = Inli * boosterAmp.getGainByType(totalPower, typeOfAmplifierGain);
			Iase = Iase * boosterAmp.getGainByType(totalPower, typeOfAmplifierGain);

			//I = I / LsssLinear;
			//I = I * boosterAmp.getGainByType(totalPower, typeOfAmplifierGain);

			if(activeASE){
				double Sase = boosterAmp.getAseByGain(totalPower, boosterAmp.getGainByType(totalPower, typeOfAmplifierGain));

				if (physicalLayerModel == MODEL_JOHANNISSON) { // Model Johannisson
					Sase = Sase / polarizationModes;
				}

				Iase = Iase + Sase;
			}

			for(int span = 0; span < Nl; span++) {

				Inli = Inli / attenuationBySpanLinear;
				Iase = Iase / attenuationBySpanLinear;

				Inli = Inli * lineAmp.getGainByType(totalPower, typeOfAmplifierGain);
				Iase = Iase * lineAmp.getGainByType(totalPower, typeOfAmplifierGain);

				//I = I / attenuationBySpanLinear;
				//I = I * lineAmp.getGainByType(totalPower, typeOfAmplifierGain);

				if(activeNLI){
					double beta21 = Math.abs(beta2);
					double Snli = 0.0;

					if (physicalLayerModel == MODEL_JOHANNISSON) { // Model Johannisson
						double mi = (1.0/Bsi) * (3.0 * 64.0 * gamma * gamma) / (2.0 * 81.0 * Math.PI * alphaLinear * beta21);
						double ro =  Bsi * Bsi * (Math.PI * Math.PI * beta21) / alphaLinear;
						if (ro < 1.000001) { //Avoid negative or zero logs
							ro = 1.000001;
						}
						double p1 = (1.0/Bsi) * (1.0/Bsi) * Math.log(ro);
						Snli = mi * p1;

						if(Snli < 0.0) {
							Snli = 0.0;
						}

					} else { // Model Habibi
						double Phii = getValueOfModulationConstant(modulation);

						double alfaCampo = alphaLinear / 2.0;
						double Ls = L * 1000.0; // span in meter
						double Leff = (1.0 - Math.pow(Math.E, -2.0 * alfaCampo * Ls)) / (2.0 * alfaCampo);
						double E = (8.0 * gamma * gamma * Leff * Leff * 2.0 * alfaCampo) / (27.0 * Math.PI * beta21);

						double ro = (((Math.PI * Math.PI) / 2.0) * beta21 * Bsi * Bsi) / (2.0 * alfaCampo);
						double ro2 = E * arcsinh(ro);
						double gsci = (1.0/Bsi) * (1.0/Bsi) * (1.0/Bsi) * ro2;

						double Wnn = (1.0/Bsi) * (1.0/Bsi) * (1.0/Bsi) * (E * (5.0 * Phii) / (3.0 * alfaCampo * Ls));

						Snli = gsci - Wnn;

						if(Snli < 0.0) {
							Snli = 0.0;
						}
					}

					Inli = Inli + Snli;
				}

				if(activeASE){
					double Sase = lineAmp.getAseByGain(totalPower, lineAmp.getGainByType(totalPower, typeOfAmplifierGain));

					if (physicalLayerModel == MODEL_JOHANNISSON) { // Model Johannisson
						Sase = Sase / polarizationModes;
					}

					Iase = Iase + Sase;
				}
			}

			double lastFiberSegment = link.getDistance() - (Nl * L);
			double attenuationBySpanPreAmpLinear = ratioOfDB(alpha * lastFiberSegment);
			preAmp.setGain(alpha * lastFiberSegment);

			Inli = Inli / attenuationBySpanPreAmpLinear;
			Iase = Iase / attenuationBySpanPreAmpLinear;

			Inli = Inli * preAmp.getGainByType(totalPower, typeOfAmplifierGain);
			Iase = Iase * preAmp.getGainByType(totalPower, typeOfAmplifierGain);

			//I = I / attenuationBySpanPreAmpLinear;
			//I = I * preAmp.getGainByType(totalPower, typeOfAmplifierGain);

			if(activeNLI){
				double beta21 = Math.abs(beta2);
				double Snli = 0.0;

				if (physicalLayerModel == MODEL_JOHANNISSON) { // Model Johannisson
					double mi = (1.0/Bsi) * (3.0 * 64.0 * gamma * gamma) / (2.0 * 81.0 * Math.PI * alphaLinear * beta21);
					double ro =  Bsi * Bsi * (Math.PI * Math.PI * beta21) / alphaLinear;
					if (ro < 1.000001) { //Avoid negative or zero logs
						ro = 1.000001;
					}
					double p1 = (1.0/Bsi) * (1.0/Bsi) * Math.log(ro);
					Snli = mi * p1;

					if(Snli < 0.0) {
						Snli = 0.0;
					}

				} else { // Model Habibi
					double Phii = getValueOfModulationConstant(modulation);

					double alfaCampo = alphaLinear / 2.0;
					double Ls = L * 1000.0; // span in meter
					double Leff = (1.0 - Math.pow(Math.E, -2.0 * alfaCampo * Ls)) / (2.0 * alfaCampo);
					double E = (8.0 * gamma * gamma * Leff * Leff * 2.0 * alfaCampo) / (27.0 * Math.PI * beta21);

					double ro = (((Math.PI * Math.PI) / 2.0) * beta21 * Bsi * Bsi) / (2.0 * alfaCampo);
					double ro2 = E * arcsinh(ro);
					double gsci = (1.0/Bsi) * (1.0/Bsi) * (1.0/Bsi) * ro2;

					double Wnn = (1.0/Bsi) * (1.0/Bsi) * (1.0/Bsi) * (E * (5.0 * Phii) / (3.0 * alfaCampo * Ls));

					Snli = gsci - Wnn;

					if(Snli < 0.0) {
						Snli = Wnn - gsci;
					}
				}

				Inli = Inli + Snli;
			}

			if(activeASE){
				double Sase = preAmp.getAseByGain(totalPower, preAmp.getGainByType(totalPower, typeOfAmplifierGain));

				if (physicalLayerModel == MODEL_JOHANNISSON) { // Model Johannisson
					Sase = Sase / polarizationModes;
				}

				Iase = Iase + Sase;
			}

			// DEMUX insertion loss
			Inli = Inli / LsssLinear;
			Iase = Iase / LsssLinear;

			//I = I / LsssLinear;
		}

		// Switch insertion loss
		Inli = Inli / LsssLinear;
		Iase = Iase / LsssLinear;

		//double SNRi = Ii / (Iase + Inli);
		//double SNR = I / (Iase + Inli);

		double Pmax = Math.cbrt(Iase / (2.0 * Inli));

		return Pmax;
	}

	/**
	 * This method uses ternary search to find the power that generates the maximum OSNR.
	 * Adaptive range expansion from P0, automatically detecting the maximum region.
	 * Protection against negative values ​​(there are no negative powers).
	 *
	 * @param Pstart double
	 * @param circuit Circuit
	 * @param route Route
	 * @param mod Modulation
	 * @param core int
	 * @param sa int[]
	 * @return double - liner (Watts)
	 */
	private double checkMaximumPower(double Pstart, Circuit circuit, Route route, Modulation mod, int core, int sa[]) {
		// =============================================
	    // CONSTANTES DE CONTROLE
	    // =============================================
		final int    MAX_EXPANSION_ITER = 50;
	    final double MIN_POWER          = 1e-9;   // por exemplo -60 dBm
	    final double MAX_POWER          = 0.1;    // por exemplo +20 dBm

		// =============================================
		// Adaptive expansion of the [Pmin, Pmax] interval from P0
		// Automatically detecting the region of the maximum.
		// =============================================

	    // Ensures that Pstart is within the physical limits
	    double P0 = Math.max(MIN_POWER, Math.min(MAX_POWER, Pstart));

	    // -------------------------------
    	// Passo em dBm (0.5 dB)
    	// -------------------------------
    	double step_dB   = 0.5;
    	double P0_dBm    = W_to_dBm(P0);
    	double left_dBm  = P0_dBm - step_dB;
    	double right_dBm = P0_dBm + step_dB;

    	double P_left  = dBm_to_W(left_dBm);
    	double P_right = dBm_to_W(right_dBm);

    	P_left  = Math.max(MIN_POWER, Math.min(MAX_POWER, P_left));
    	P_right = Math.max(MIN_POWER, Math.min(MAX_POWER, P_right));

	    // -------------------------------
    	// OSNR em P0, P_left, P_right
    	// -------------------------------
	    circuit.setLaunchPowerLinear(P0);
	    double OSNR_central = computeSNRSegment(circuit, route, 0, route.getNodeList().size() - 1, mod, core, sa, null, false);

		circuit.setLaunchPowerLinear(P_left);
		double OSNR_left = computeSNRSegment(circuit, route, 0, route.getNodeList().size() - 1, mod, core, sa, null, false);

		circuit.setLaunchPowerLinear(P_right);
		double OSNR_right = computeSNRSegment(circuit, route, 0, route.getNodeList().size() - 1, mod, core, sa, null, false);

		double Pmax = 0.0;
		double Pmin = 0.0;

	    if (OSNR_left < OSNR_central && OSNR_right < OSNR_central) { // Case where P0 is already a local maximum
	        Pmin = P_left;
	        Pmax = P_right;
	    }
	    else if (OSNR_right > OSNR_central) { // Expansion to the right
	        Pmin = P0;
	        Pmax = P_right;

			int iter = 0;
    		while (iter < MAX_EXPANSION_ITER) {

    			double P_prev = Pmax;
    			double P_next = dBm_to_W(W_to_dBm(Pmax) + step_dB);

    			P_next = Math.max(MIN_POWER, Math.min(MAX_POWER, P_next));

	            circuit.setLaunchPowerLinear(P_prev);
	            double OSNR_prev = computeSNRSegment(circuit, route, 0, route.getNodeList().size() - 1, mod, core, sa, null, false);

				circuit.setLaunchPowerLinear(P_next);
				double OSNR_next = computeSNRSegment(circuit, route, 0, route.getNodeList().size() - 1, mod, core, sa, null, false);

				// Went past the peak to the right
				if (OSNR_next <= OSNR_prev) {
    				Pmin = P_prev;
    				Pmax = P_next;
    				break;
    			}

    			Pmin = P_prev;
    			Pmax = P_next;
    			iter++;
			}
	    }
	    else if (OSNR_left > OSNR_central) { // Expansion to the left
	        Pmax = P0;
	        Pmin = P_left;

	        int iter = 0;
    		while (iter < MAX_EXPANSION_ITER) {

    			double P_prev = Pmin;
    			double P_next = dBm_to_W(W_to_dBm(Pmin) - step_dB);

	            P_next = Math.max(MIN_POWER, Math.min(MAX_POWER, P_next));

	            circuit.setLaunchPowerLinear(P_prev);
	            double OSNR_prev = computeSNRSegment(circuit, route, 0, route.getNodeList().size() - 1, mod, core, sa, null, false);

				circuit.setLaunchPowerLinear(P_next);
				double OSNR_next = computeSNRSegment(circuit, route, 0, route.getNodeList().size() - 1, mod, core, sa, null, false);

				// Went past the peak to the left
				if (OSNR_next <= OSNR_prev) {
    				Pmin = P_next;
    				Pmax = P_prev;
    				break;
    			}

    			Pmax = P_prev;
    			Pmin = P_next;
    			iter++;
	        }
	    }

		// =============================================
		// Ternary search for the OSNR maximum in the [Pmin, Pmax] interval
		// =============================================
	    final int    MAX_TERNARY_ITER   = 50;
	    final double MIN_POWER_TER = 1e-15;

	    // Better hybrid tolerance for broad power ranges
	    final double ABS_TOL = 1e-12;   // safe for very small powers (1e-12 W)
	    final double REL_TOL = 1e-3;    // 0.1% relative tolerance

	    for (int i = 0; i < MAX_TERNARY_ITER; i++) {

			double p1 = Pmin + (Pmax - Pmin) / 3.0;
			double p2 = Pmax - (Pmax - Pmin) / 3.0;

			// Protection against negative values
	        if (p1 < MIN_POWER_TER) p1 = MIN_POWER_TER;
	        if (p2 < MIN_POWER_TER) p2 = MIN_POWER_TER;

			circuit.setLaunchPowerLinear(p1);
			double OSNRp1 = computeSNRSegment(circuit, route, 0, route.getNodeList().size() - 1, mod, core, sa, null, false);

			circuit.setLaunchPowerLinear(p2);
			double OSNRp2 = computeSNRSegment(circuit, route, 0, route.getNodeList().size() - 1, mod, core, sa, null, false);

			if (OSNRp1 < OSNRp2) {
				Pmin = p1; // maximum is to the right
			} else {
				Pmax = p2; // maximum is to the left
			}

			double Pmid = (Pmin + Pmax) * 0.5;
			double tol = Math.max(ABS_TOL, REL_TOL * Pmid);

			if (Math.abs(Pmax - Pmin) <= tol) {
				break;
			}
		}

	    // Returns the refined midpoint
		double Popt =  (Pmin + Pmax) * 0.5;

    	return Popt;
    }


	/**
	 * Executes the test camada fisica operation.
	 */
	public void testCamadaFisica() {

		int totalSlots = 320;
		double distance = 1000.0;
		//int numberOfCores = 1;

		List<CoreConfig> coreList = new ArrayList<>();
		coreList.add(new CoreConfig(0, new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6))));
		coreList.add(new CoreConfig(1, new ArrayList<>(Arrays.asList(0, 2, 6))));
		coreList.add(new CoreConfig(2, new ArrayList<>(Arrays.asList(0, 1, 3))));
		coreList.add(new CoreConfig(3, new ArrayList<>(Arrays.asList(0, 2, 4))));
		coreList.add(new CoreConfig(4, new ArrayList<>(Arrays.asList(0, 3, 5))));
		coreList.add(new CoreConfig(5, new ArrayList<>(Arrays.asList(0, 4, 6))));
		coreList.add(new CoreConfig(6, new ArrayList<>(Arrays.asList(0, 1, 5))));

		Node n1 = new Node("1", 1000, 1000, 0, 100);
		Node n2 = new Node("2", 1000, 1000, 0, 100);
		n1.getOxc().addLink(new Link(n1.getOxc(), n2.getOxc(), totalSlots, slotBandwidth, distance, coreList));

		Vector<Node> listNodes = new Vector<Node>();
		listNodes.add(n1);
		listNodes.add(n2);

		Route route = new Route(listNodes);
		Pair pair = new Pair(n1, n2);

		int guardBand = 0;
		Modulation mod_BPSK = new Modulation("BPSK", 10000.0, 2.0, 5.5, 0.28, 12.5E+9, 2.0, -14.0);
		Modulation mod_QPSK = new Modulation("QPSK", 5000.0, 4.0, 8.5, 0.28, 12.5E+9, 2.0, -18.5);
		Modulation mod_8QAM = new Modulation("8QAM", 2500.0, 8.0, 12.5, 0.28, 12.5E+9, 2.0, -21.0);

		// circuit 1
		double tr1 = 100.0E+9; //bps
		int slotNumber1 = 3; //mod_QPSK.requiredSlots(tr1);
		int sa1[] = new int[2];
		sa1[0] = 1;
		sa1[1] = sa1[0] + slotNumber1 - 1;
		int indexCore1 = 0;

		RequestForConnection requestTemp1 = new RequestForConnection();
		requestTemp1.setPair(pair);
		requestTemp1.setRequiredBitRate(tr1);

		Circuit circuit1 = new Circuit();
		circuit1.setPair(pair);
		circuit1.setRoute(route);
		circuit1.setModulation(mod_QPSK);
		circuit1.setGuardBand(guardBand);
		circuit1.setIndexCore(indexCore1);
		circuit1.setSpectrumAssigned(sa1);
		circuit1.addRequest(requestTemp1);

		// circuit 2
		double tr2 = 100.0E+9; //bps
		int slotNumber2 = 3; //mod_BPSK.requiredSlots(tr2);
		int sa2[] = new int[2];
		sa2[0] = sa1[1] + 1;
		sa2[1] = sa2[0] + slotNumber2 - 1;
		int indexCore2 = 0;

		RequestForConnection requestTemp2 = new RequestForConnection();
		requestTemp2.setPair(pair);
		requestTemp2.setRequiredBitRate(tr2);

		Circuit circuit2 = new Circuit();
		circuit2.setPair(pair);
		circuit2.setRoute(route);
		circuit2.setModulation(mod_QPSK);
		circuit2.setGuardBand(guardBand);
		circuit2.setIndexCore(indexCore2);
		circuit2.setSpectrumAssigned(sa2);
		circuit2.addRequest(requestTemp2);

		// circuit 3
		double tr3 = 100.0E+9; //bps
		int slotNumber3 = 3; //mod_8QAM.requiredSlots(tr3);
		int sa3[] = new int[2];
		sa3[0] = sa2[1] + 1;
		sa3[1] = sa3[0] + slotNumber3 - 1;
		int indexCore3 = 0;

		RequestForConnection requestTemp3 = new RequestForConnection();
		requestTemp3.setPair(pair);
		requestTemp3.setRequiredBitRate(tr3);

		Circuit circuit3 = new Circuit();
		circuit3.setPair(pair);
		circuit3.setRoute(route);
		circuit3.setModulation(mod_QPSK);
		circuit3.setGuardBand(guardBand);
		circuit3.setIndexCore(indexCore3);
		circuit3.setSpectrumAssigned(sa3);
		circuit3.addRequest(requestTemp3);

		// Adding the circuits to the network
		route.getLink(0).getCore(circuit1.getIndexCore()).addCircuit(circuit1);
		route.getLink(0).getCore(circuit2.getIndexCore()).addCircuit(circuit2);
		route.getLink(0).getCore(circuit3.getIndexCore()).addCircuit(circuit3);


		//Calculating the OSNR of each circuit
		double c1_OSNR = computeSNRSegment(circuit1, circuit1.getRoute(), 0, circuit1.getRoute().getNodeList().size() - 1, circuit1.getModulation(), circuit1.getIndexCore(), circuit1.getSpectrumAssigned(), null, false);
		double c1_OSNRdB = PhysicalLayer.ratioForDB(c1_OSNR);

		double c2_OSNR = computeSNRSegment(circuit2, circuit2.getRoute(), 0, circuit2.getRoute().getNodeList().size() - 1, circuit2.getModulation(), circuit2.getIndexCore(), circuit2.getSpectrumAssigned(), null, false);
		double c2_OSNRdB = PhysicalLayer.ratioForDB(c2_OSNR);

		double c3_OSNR = computeSNRSegment(circuit3, circuit3.getRoute(), 0, circuit3.getRoute().getNodeList().size() - 1, circuit3.getModulation(), circuit3.getIndexCore(), circuit3.getSpectrumAssigned(), null, false);
		double c3_OSNRdB = PhysicalLayer.ratioForDB(c3_OSNR);


		System.out.println("c1: OSNR = " + c1_OSNR + ", OSNR(dB) = " + c1_OSNRdB);
		System.out.println("c2: OSNR = " + c2_OSNR + ", OSNR(dB) = " + c2_OSNRdB);
		System.out.println("c3: OSNR = " + c3_OSNR + ", OSNR(dB) = " + c3_OSNRdB);

		System.out.println("fim");
	}

	/**
	 * Executes the test camada fisica xt operation.
	 */
	public void testCamadaFisicaXT() {

	    int totalSlots = 320;
	    double distance = 1000.0;

	    // Core configuration (MCF 7-core)
	    List<CoreConfig> coreList = new ArrayList<>();
	    coreList.add(new CoreConfig(0, new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6))));
	    coreList.add(new CoreConfig(1, new ArrayList<>(Arrays.asList(0, 2, 6))));
	    coreList.add(new CoreConfig(2, new ArrayList<>(Arrays.asList(0, 1, 3))));
	    coreList.add(new CoreConfig(3, new ArrayList<>(Arrays.asList(0, 2, 4))));
	    coreList.add(new CoreConfig(4, new ArrayList<>(Arrays.asList(0, 3, 5))));
	    coreList.add(new CoreConfig(5, new ArrayList<>(Arrays.asList(0, 4, 6))));
	    coreList.add(new CoreConfig(6, new ArrayList<>(Arrays.asList(0, 1, 5))));

	    Node n1 = new Node("1", 1000, 1000, 0, 100);
	    Node n2 = new Node("2", 1000, 1000, 0, 100);
	    n1.getOxc().addLink(new Link(n1.getOxc(), n2.getOxc(), totalSlots, slotBandwidth, distance, coreList));

	    Vector<Node> listNodes = new Vector<>();
	    listNodes.add(n1);
	    listNodes.add(n2);

	    Route route = new Route(listNodes);
	    Pair pair = new Pair(n1, n2);

	    int guardBand = 0;
	    Modulation mod_BPSK = new Modulation("BPSK", 10000.0, 2.0, 5.5, 0.28, 12.5E+9, 2.0, -14.0);
	    Modulation mod_QPSK = new Modulation("QPSK", 5000.0, 4.0, 8.5, 0.28, 12.5E+9, 2.0, -18.5);
	    Modulation mod_8QAM = new Modulation("8QAM", 2500.0, 8.0, 12.5, 0.28, 12.5E+9, 2.0, -21.0);

	    // ============================
	    // Circuit 1 (reference) on core 0
	    // ============================
	    double tr1 = 100.0E+9; // bps
	    int slotNumber1 = 3;   // p/ simplificar, 3 slots
	    int sa1[] = new int[2];
	    sa1[0] = 1;
	    sa1[1] = sa1[0] + slotNumber1 - 1; // [1,3]
	    int indexCore1 = 0;

	    RequestForConnection requestTemp1 = new RequestForConnection();
	    requestTemp1.setPair(pair);
	    requestTemp1.setRequiredBitRate(tr1);

	    Circuit circuit1 = new Circuit();
	    circuit1.setPair(pair);
	    circuit1.setRoute(route);
	    circuit1.setModulation(mod_QPSK);
	    circuit1.setGuardBand(guardBand);
	    circuit1.setIndexCore(indexCore1);
	    circuit1.setSpectrumAssigned(sa1);
	    circuit1.addRequest(requestTemp1);

	    // ============================
	    // Circuits on the SAME core (NLI / intra-core interference)
	    // ============================
	    double tr2 = 100.0E+9;
	    int slotNumber2 = 3;
	    int sa2[] = new int[2];
	    sa2[0] = sa1[1] + 1;
	    sa2[1] = sa2[0] + slotNumber2 - 1; // [4,6]
	    int indexCore2 = 0;

	    RequestForConnection requestTemp2 = new RequestForConnection();
	    requestTemp2.setPair(pair);
	    requestTemp2.setRequiredBitRate(tr2);

	    Circuit circuit2 = new Circuit();
	    circuit2.setPair(pair);
	    circuit2.setRoute(route);
	    circuit2.setModulation(mod_QPSK);
	    circuit2.setGuardBand(guardBand);
	    circuit2.setIndexCore(indexCore2);
	    circuit2.setSpectrumAssigned(sa2);
	    circuit2.addRequest(requestTemp2);

	    double tr3 = 100.0E+9;
	    int slotNumber3 = 3;
	    int sa3[] = new int[2];
	    sa3[0] = sa2[1] + 1;
	    sa3[1] = sa3[0] + slotNumber3 - 1; // [7,9]
	    int indexCore3 = 0;

	    RequestForConnection requestTemp3 = new RequestForConnection();
	    requestTemp3.setPair(pair);
	    requestTemp3.setRequiredBitRate(tr3);

	    Circuit circuit3 = new Circuit();
	    circuit3.setPair(pair);
	    circuit3.setRoute(route);
	    circuit3.setModulation(mod_QPSK);
	    circuit3.setGuardBand(guardBand);
	    circuit3.setIndexCore(indexCore3);
	    circuit3.setSpectrumAssigned(sa3);
	    circuit3.addRequest(requestTemp3);

	    // ============================
	    // Circuits on adjacent cores (generate XT on circuit 1)
	    // ============================
	    // All with the SAME c1 slot band ([1,3]) to maximize XT
	    int[] saXT = new int[]{sa1[0], sa1[1]};
	    double trXT = 100.0E+9;

	    List<Circuit> xtNeighbors = new ArrayList<>();
	    int[] adjacentCores = new int[]{1,2,3,4,5,6}; // adjacent cores of core 0

	    for (int coreId : adjacentCores) {
	        RequestForConnection req = new RequestForConnection();
	        req.setPair(pair);
	        req.setRequiredBitRate(trXT);

	        Circuit cXT = new Circuit();
	        cXT.setPair(pair);
	        cXT.setRoute(route);
	        cXT.setModulation(mod_QPSK);
	        cXT.setGuardBand(guardBand);
	        cXT.setIndexCore(coreId);
	        cXT.setSpectrumAssigned(saXT);
	        cXT.addRequest(req);

	        xtNeighbors.add(cXT);
	    }

	    // ============================
	    // Adding all circuits to the network
	    // ============================
	    Link link = route.getLink(0);
	    // core 0
	    link.getCore(circuit1.getIndexCore()).addCircuit(circuit1);
	    link.getCore(circuit2.getIndexCore()).addCircuit(circuit2);
	    link.getCore(circuit3.getIndexCore()).addCircuit(circuit3);

	    // adjacent cores
	    for (Circuit cXT : xtNeighbors) {
	        link.getCore(cXT.getIndexCore()).addCircuit(cXT);
	    }

	    // ============================
	    // Power sweep to generate OSNR x P and XT x P curves
	    // ============================

	    System.out.println("#P_dBm; OSNR_dB; XT_dB");

	    // example: scans from -5 dBm to +5 dBm with a 0.5 dB step
	    for (double P_dBm = -5.0; P_dBm <= 5.0; P_dBm += 0.5) {

	        // converts dBm to Watts
	        double P_Watts = dBm_to_W(P_dBm); // use your actual method

	        // configures the launch power of circuit 1
	        circuit1.setLaunchPowerLinear(P_Watts);

	        // OSNR of circuit 1
	        double c1_OSNR = computeSNRSegment(
	                circuit1,
	                circuit1.getRoute(),
	                0,
	                circuit1.getRoute().getNodeList().size() - 1,
	                circuit1.getModulation(),
	                circuit1.getIndexCore(),
	                circuit1.getSpectrumAssigned(),
	                null,
	                false
	        );
	        double c1_OSNRdB = PhysicalLayer.ratioForDB(c1_OSNR);

	        // XT that circuit 1 experiences from neighbors (inter-core + intra-core)
	        // Here you should use the actual function that already exists in your PhysicalLayer/Crosstalk
	        double c1_XTdB = crosstalk.calculateCrosstalk(circuit1, null, false);

	        System.out.println("P(dBm): " + P_dBm + ", OSNR(dB): " + c1_OSNRdB + ", XT(dB): " + c1_XTdB);
	    }

	    System.out.println("fim");
	}


}
