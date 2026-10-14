package simulationControl.parsers;

/**
 * This class represents the physical layer configuration file, its representation in entity form is
 * important for the storage and transmission of this type of configuration in the JSON format.
 *
 * @author Alexandre.
 */
public class PhysicalLayerConfig {

	private int physicalLayerModel;   // 1 = Model Johannisson or 2 (or another value) = Model Habibi
	private int crosstalkModel; //0 = Crosstalk separate from other physical layer effects or 1 = Crosstalk with other physical layer effects

	// Allows you to enable or disable transmission quality computing
    private boolean activeQoT; // Active the QoTN
	private boolean activeQoTForOther; // Active the QoTO

	private boolean activeASE; // Active the ASE noise of the amplifier
	private boolean activeNLI; // Active the nonlinear noise in the fibers

	private boolean activeXT; // Active the Crosstalk
	private boolean activeXTForOther; // Active the Crosstalk on others circuits

	private double rateOfFEC; // Rate of FEC (Forward Error Correction), The most used rate is 7% which corresponds to the BER of 3.8E-3
	private int typeOfTestQoT; // 0, To check for the SNR threshold (Signal-to-Noise Ratio), or another value, to check for the BER threshold (Bit Error Rate)

	private double power;             // Power per channel, dBm
	private double spanLength;        // L, Size of a span, km
	private double fiberLoss;         // alpha, dB/km, Fiber loss
	private double fiberNonlinearity; // gamma, Fiber nonlinearity
	private double fiberDispersion;   // beta2, ps^2 = E-24, Dispersion parameter
	private double centerFrequency;   // v, Frequency of light

	private double constantOfPlanck;                  // h, Constant of Planck
	private double noiseFigureOfOpticalAmplifier;     // NF, Amplifier noise figure, dB
	private double powerSaturationOfOpticalAmplifier; //pSat, Saturation power of the amplifier, dBm
	private double noiseFactorModelParameterA1;       // A1, Amplifier noise factor parameter
	private double noiseFactorModelParameterA2;       // A2, Amplifier noise factor parameter
	private int typeOfAmplifierGain;                  // Type of amplifier gain, 0 to fixed gain and 1 to saturated gain
	private double amplificationFrequency;            // Frequency used for amplification

	private double switchInsertionLoss; // dB

	private boolean fixedPowerSpectralDensity; // To enable or disable fixed power spectral density
	private double referenceBandwidthForPowerSpectralDensity; // Reference bandwidth for power spectral density

	private double propagationConstant; // Beta, propagation constant, 1/m
	private double bendingRadius;       // R, Bending radius, m
	private double couplingCoefficient; // k, Coupling coefficient, m^-1
	private double corePitch;           // Lambda, Core pitch, m

	private double polarizationModes; // Number of polarization modes

	// QKD receiver-side assumptions used only by QKD-enabled simulations.
	// Keeping them in the physical-layer JSON makes the PXT -> detector-noise
	// bridge explicit instead of hiding Tg/eta_f in Java constants.
	// Legacy location (Parts 1-25). Since Part 26 the QKD values live in the case file
	// "qkdPhysicalLayer"; null here means "not present" so a rewrite of physicalLayer
	// does not add QKD blocks back.
	private QKDReceiverConfig qkdReceiver = null;
	/** Optional BB84 decoy-state parameters; absent = reference values of Dibaj et al. (2024). */
	private QKDPhysicalConfig qkdPhysical = null;

	/** Part 26: contents of the qkdPhysicalLayer case file (not written into physicalLayer). */
	private transient qkd.physical.QKDPhysicalLayerConfig qkdPhysicalLayer;

	public qkd.physical.QKDPhysicalLayerConfig getQkdPhysicalLayer() {
		return qkdPhysicalLayer;
	}

	public void setQkdPhysicalLayer(qkd.physical.QKDPhysicalLayerConfig qkdPhysicalLayer) {
		this.qkdPhysicalLayer = qkdPhysicalLayer;
	}

	/** True when this physicalLayer file still carries the legacy QKD blocks. */
	public boolean hasLegacyQkdBlocks() {
		return qkdReceiver != null || qkdPhysical != null;
	}

	/**
	 * @return boolean the activeQoT
	 */
	public boolean isActiveQoT() {
		return activeQoT;
	}
	/**
	 * @param activeQoT boolean
	 */
	public void setActiveQoT(boolean activeQoT) {
		this.activeQoT = activeQoT;
	}
	/**
	 * @return boolean the activeQoTForOther
	 */
	public boolean isActiveQoTForOther() {
		return activeQoTForOther;
	}
	/**
	 * @param activeQoTForOther boolean
	 */
	public void setActiveQoTForOther(boolean activeQoTForOther) {
		this.activeQoTForOther = activeQoTForOther;
	}
	/**
	 * @return boolean the activeAse
	 */
	public boolean isActiveASE() {
		return activeASE;
	}
	/**
	 * @param activeASE boolean
	 */
	public void setActiveASE(boolean activeASE) {
		this.activeASE = activeASE;
	}
	/**
	 * @return boolean the activeNli
	 */
	public boolean isActiveNLI() {
		return activeNLI;
	}
	/**
	 * @param activeNLI boolean
	 */
	public void setActiveNLI(boolean activeNLI) {
		this.activeNLI = activeNLI;
	}
	/**
	 * @return boolean the isActiveXT
	 */
	public boolean isActiveXT() {
		return activeXT;
	}
	/**
	 * @param activeXT boolean
	 */
	public void setActiveXT(boolean activeXT) {
		this.activeXT = activeXT;
	}
	/**
	 * @return boolean the isActiveXTForOther
	 */
	public boolean isActiveXTForOther() {
		return activeXTForOther;
	}
	/**
	 * @param activeXTForOther boolean
	 */
	public void setActiveXTForOther(boolean activeXTForOther) {
		this.activeXTForOther = activeXTForOther;
	}
	/**
	 * @return int the typeOfTestQoT
	 */
	public int getTypeOfTestQoT() {
		return typeOfTestQoT;
	}
	/**
	 * @param typeOfTestQoT int
	 */
	public void setTypeOfTestQoT(int typeOfTestQoT) {
		this.typeOfTestQoT = typeOfTestQoT;
	}
	/**
	 * @return double the typeOfFEC
	 */
	public double getRateOfFEC() {
		return rateOfFEC;
	}
	/**
	 * @param rateOfFEC double
	 */
	public void setRateOfFEC(double rateOfFEC) {
		this.rateOfFEC = rateOfFEC;
	}
	/**
	 * @return double the power
	 */
	public double getPower() {
		return power;
	}
	/**
	 * @param power double
	 */
	public void setPower(double power) {
		this.power = power;
	}
	/**
	 * @return double the spanLength
	 */
	public double getSpanLength() {
		return spanLength;
	}
	/**
	 * @param spanLength double
	 */
	public void setSpanLength(double spanLength) {
		this.spanLength = spanLength;
	}
	/**
	 * @return double the fiberLoss
	 */
	public double getFiberLoss() {
		return fiberLoss;
	}
	/**
	 * @param fiberLoss double
	 */
	public void setFiberLoss(double fiberLoss) {
		this.fiberLoss = fiberLoss;
	}
	/**
	 * @return double the fiberNonlinearity
	 */
	public double getFiberNonlinearity() {
		return fiberNonlinearity;
	}
	/**
	 * @param fiberNonlinearity double
	 */
	public void setFiberNonlinearity(double fiberNonlinearity) {
		this.fiberNonlinearity = fiberNonlinearity;
	}
	/**
	 * @return double the fiberDispersion
	 */
	public double getFiberDispersion() {
		return fiberDispersion;
	}
	/**
	 * @param fiberDispersion double
	 */
	public void setFiberDispersion(double fiberDispersion) {
		this.fiberDispersion = fiberDispersion;
	}
	/**
	 * @return double the centerFrequency
	 */
	public double getCenterFrequency() {
		return centerFrequency;
	}
	/**
	 * @param centerFrequency double
	 */
	public void setCenterFrequency(double centerFrequency) {
		this.centerFrequency = centerFrequency;
	}
	/**
	 * @return double the constantOfPlanck
	 */
	public double getConstantOfPlanck() {
		return constantOfPlanck;
	}
	/**
	 * @param constantOfPlanck double
	 */
	public void setConstantOfPlanck(double constantOfPlanck) {
		this.constantOfPlanck = constantOfPlanck;
	}
	/**
	 * @return double the noiseFigureOfOpticalAmplifier
	 */
	public double getNoiseFigureOfOpticalAmplifier() {
		return noiseFigureOfOpticalAmplifier;
	}
	/**
	 * @param noiseFigureOfOpticalAmplifier double
	 */
	public void setNoiseFigureOfOpticalAmplifier(double noiseFigureOfOpticalAmplifier) {
		this.noiseFigureOfOpticalAmplifier = noiseFigureOfOpticalAmplifier;
	}
	/**
	 * @return double the powerSaturationOfOpticalAmplifier
	 */
	public double getPowerSaturationOfOpticalAmplifier() {
		return powerSaturationOfOpticalAmplifier;
	}
	/**
	 * @param powerSaturationOfOpticalAmplifier double
	 */
	public void setPowerSaturationOfOpticalAmplifier(double powerSaturationOfOpticalAmplifier) {
		this.powerSaturationOfOpticalAmplifier = powerSaturationOfOpticalAmplifier;
	}
	/**
	 * @return double the noiseFactorModelParameterA1
	 */
	public double getNoiseFactorModelParameterA1() {
		return noiseFactorModelParameterA1;
	}
	/**
	 * @param noiseFactorModelParameterA1 double
	 */
	public void setNoiseFactorModelParameterA1(double noiseFactorModelParameterA1) {
		this.noiseFactorModelParameterA1 = noiseFactorModelParameterA1;
	}
	/**
	 * @return double the noiseFactorModelParameterA2
	 */
	public double getNoiseFactorModelParameterA2() {
		return noiseFactorModelParameterA2;
	}
	/**
	 * @param noiseFactorModelParameterA2 double
	 */
	public void setNoiseFactorModelParameterA2(double noiseFactorModelParameterA2) {
		this.noiseFactorModelParameterA2 = noiseFactorModelParameterA2;
	}
	/**
	 * @return int the typeOfAmplifierGain
	 */
	public int getTypeOfAmplifierGain() {
		return typeOfAmplifierGain;
	}
	/**
	 * @param typeOfAmplifierGain int
	 */
	public void setTypeOfAmplifierGain(int typeOfAmplifierGain) {
		this.typeOfAmplifierGain = typeOfAmplifierGain;
	}
	/**
	 * @return double  the amplificationFrequency
	 */
	public double getAmplificationFrequency() {
		return amplificationFrequency;
	}
	/**
	 * @param amplificationFrequency double
	 */
	public void setAmplificationFrequency(double amplificationFrequency) {
		this.amplificationFrequency = amplificationFrequency;
	}
	/**
	 * @return double the switchInsertionLoss
	 */
	public double getSwitchInsertionLoss() {
		return switchInsertionLoss;
	}
	/**
	 * @param switchInsertionLoss double
	 */
	public void setSwitchInsertionLoss(double switchInsertionLoss) {
		this.switchInsertionLoss = switchInsertionLoss;
	}
	/**
	 * @return double the fixedPowerSpectralDensity
	 */
	public boolean isFixedPowerSpectralDensity() {
		return fixedPowerSpectralDensity;
	}
	/**
	 * @param fixedPowerSpectralDensity double
	 */
	public void setFixedPowerSpectralDensity(boolean fixedPowerSpectralDensity) {
		this.fixedPowerSpectralDensity = fixedPowerSpectralDensity;
	}
	/**
	 * @return double the referenceBandwidthForPowerSpectralDensity
	 */
	public double getReferenceBandwidthForPowerSpectralDensity() {
		return referenceBandwidthForPowerSpectralDensity;
	}
	/**
	 * @param referenceBandwidthForPowerSpectralDensity double
	 */
	public void setReferenceBandwidthForPowerSpectralDensity(double referenceBandwidthForPowerSpectralDensity) {
		this.referenceBandwidthForPowerSpectralDensity = referenceBandwidthForPowerSpectralDensity;
	}
	/**
	 * @return double the polarizationModes
	 */
	public double getPolarizationModes() {
		return polarizationModes;
	}
	/**
	 * @param polarizationModes double
	 */
	public void setPolarizationModes(double polarizationModes) {
		this.polarizationModes = polarizationModes;
	}
	/**
	 * @return int the physicalLayerModel
	 */
	public int getPhysicalLayerModel() {
		return physicalLayerModel;
	}
	/**
	 * @param physicalLayerModel int
	 */
	public void setPhysicalLayerModel(int physicalLayerModel) {
		this.physicalLayerModel = physicalLayerModel;
	}
	/**
	 * @return int the crosstalkModel
	 */
	public int getCrosstalkModel() {
		return crosstalkModel;
	}
	/**
	 * @param crosstalkModel int
	 */
	public void setCrosstalkModel(int crosstalkModel) {
		this.crosstalkModel = crosstalkModel;
	}
	/**
	 * @return double the propagationConstant
	 */
	public double getPropagationConstant() {
		return propagationConstant;
	}
	/**
	 * @param propagationConstant double
	 */
	public void setPropagationConstant(double propagationConstant) {
		this.propagationConstant = propagationConstant;
	}
	/**
	 * @return double the bendingRadius
	 */
	public double getBendingRadius() {
		return bendingRadius;
	}
	/**
	 * @param bendingRadius double
	 */
	public void setBendingRadius(double bendingRadius) {
		this.bendingRadius = bendingRadius;
	}
	/**
	 * @return double couplingCoefficient
	 */
	public double getCouplingCoefficient() {
		return couplingCoefficient;
	}
	/**
	 * @param couplingCoefficient double
	 */
	public void setCouplingCoefficient(double couplingCoefficient) {
		this.couplingCoefficient = couplingCoefficient;
	}
	/**
	 * @return double the corePitch
	 */
	public double getCorePitch() {
		return corePitch;
	}
	/**
	 * @param corePitch double
	 */
	public void setCorePitch(double corePitch) {
		this.corePitch = corePitch;
	}

	/**
	 * Returns the QKD receiver assumptions. Legacy scenarios that do not have
	 * this JSON object receive the explicit Part-11.5 defaults.
	 */
	public QKDReceiverConfig getQkdReceiver() {
		QKDReceiverConfig r = qkdReceiver == null ? new QKDReceiverConfig() : qkdReceiver;
		r.validate();
		return r;
	}

	public void setQkdReceiver(QKDReceiverConfig qkdReceiver) {
		if (qkdReceiver == null) {
			throw new IllegalArgumentException("qkdReceiver cannot be null");
		}
		qkdReceiver.validate();
		this.qkdReceiver = qkdReceiver;
	}

	/**
	 * Receiver parameters used by the QKD crosstalk-to-background conversion.
	 *
	 * <p>The defaults preserve Parts 1-11: Tg=100 ps, eta_f=1 and the
	 * receiver-referenced PXT approximation. These are explicit modelling
	 * assumptions for verification, not claims about a particular commercial
	 * receiver.</p>
	 */
	public static class QKDReceiverConfig {

		private double detectorGateWindowPs = 100.0;
		private double receiverOpticalEfficiency = 1.0;
		private String receiverTransferModel = "receiver_referenced";
		private boolean intercoreCrosstalkEnabled = true;

		public double getDetectorGateWindowPs() {
			validate();
			return detectorGateWindowPs;
		}

		public double getDetectorGateWindowS() {
			return getDetectorGateWindowPs() * 1e-12;
		}

		public void setDetectorGateWindowPs(double detectorGateWindowPs) {
			this.detectorGateWindowPs = detectorGateWindowPs;
			validate();
		}

		public double getReceiverOpticalEfficiency() {
			validate();
			return receiverOpticalEfficiency;
		}

		public void setReceiverOpticalEfficiency(double receiverOpticalEfficiency) {
			this.receiverOpticalEfficiency = receiverOpticalEfficiency;
			validate();
		}

		public boolean isIntercoreCrosstalkEnabled() { return intercoreCrosstalkEnabled; }
		public void setIntercoreCrosstalkEnabled(boolean enabled) { this.intercoreCrosstalkEnabled = enabled; }

		public String getReceiverTransferModel() {
			validate();
			return receiverTransferModel;
		}

		public void setReceiverTransferModel(String receiverTransferModel) {
			this.receiverTransferModel = receiverTransferModel;
			validate();
		}

		public void validate() {
			if (!Double.isFinite(detectorGateWindowPs)
					|| detectorGateWindowPs <= 0.0) {
				throw new IllegalArgumentException(
						"physicalLayer.qkdReceiver.detectorGateWindowPs must be finite and > 0");
			}
			if (!Double.isFinite(receiverOpticalEfficiency)
					|| receiverOpticalEfficiency < 0.0
					|| receiverOpticalEfficiency > 1.0) {
				throw new IllegalArgumentException(
						"physicalLayer.qkdReceiver.receiverOpticalEfficiency must be in [0,1]");
			}
			if (receiverTransferModel == null
					|| receiverTransferModel.trim().isEmpty()) {
				throw new IllegalArgumentException(
						"physicalLayer.qkdReceiver.receiverTransferModel cannot be empty");
			}
		}
	}


	public QKDPhysicalConfig getQkdPhysical() {
		QKDPhysicalConfig q = qkdPhysical == null ? new QKDPhysicalConfig() : qkdPhysical;
		q.validate();
		return q;
	}

	public void setQkdPhysical(QKDPhysicalConfig qkdPhysical) {
		this.qkdPhysical = qkdPhysical;
	}

	/**
	 * BB84 decoy-state and quantum-channel parameters. Defaults are the
	 * values of Dibaj et al. (2024) / Table 2 of the qualification.
	 */
	public static class QKDPhysicalConfig implements java.io.Serializable {
		private static final long serialVersionUID = 1L;
		private double meanPhotonNumber = 0.7;
		private double pulseIntervalPs = 250.0;
		private double errorCorrectionInefficiency = 1.16;
		private double misalignmentError = 0.015;
		private double detectorEfficiency = 0.3;
		private double darkCountProbability = 1e-8;
		private double qchBandwidthGHz = 12.5;
		private double qchPowerDbm = -80.0;
		private double attenuationFloorDbPerKm = 0.18;
		private double attenuationQuadraticDbPerKmNm2 = 8e-6;
		private double attenuationCenterNm = 1550.0;

		public double getMeanPhotonNumber() { return meanPhotonNumber; }
		public double getPulseIntervalPs() { return pulseIntervalPs; }
		public double getErrorCorrectionInefficiency() { return errorCorrectionInefficiency; }
		public double getMisalignmentError() { return misalignmentError; }
		public double getDetectorEfficiency() { return detectorEfficiency; }
		public double getDarkCountProbability() { return darkCountProbability; }
		public double getQchBandwidthGHz() { return qchBandwidthGHz; }
		public double getQchPowerDbm() { return qchPowerDbm; }
		public double getAttenuationFloorDbPerKm() { return attenuationFloorDbPerKm; }
		public double getAttenuationQuadraticDbPerKmNm2() { return attenuationQuadraticDbPerKmNm2; }
		public double getAttenuationCenterNm() { return attenuationCenterNm; }

		public void validate() {
			check(meanPhotonNumber > 0, "meanPhotonNumber must be > 0");
			check(pulseIntervalPs > 0, "pulseIntervalPs must be > 0");
			check(errorCorrectionInefficiency > 0, "errorCorrectionInefficiency must be > 0");
			check(misalignmentError >= 0 && misalignmentError <= 1, "misalignmentError must be in [0,1]");
			check(detectorEfficiency >= 0 && detectorEfficiency <= 1, "detectorEfficiency must be in [0,1]");
			check(darkCountProbability >= 0 && darkCountProbability <= 1, "darkCountProbability must be in [0,1]");
			check(qchBandwidthGHz > 0, "qchBandwidthGHz must be > 0");
			check(attenuationFloorDbPerKm >= 0, "attenuationFloorDbPerKm must be >= 0");
			check(attenuationQuadraticDbPerKmNm2 >= 0, "attenuationQuadraticDbPerKmNm2 must be >= 0");
			check(attenuationCenterNm > 0, "attenuationCenterNm must be > 0");
		}

		private static void check(boolean ok, String message) {
			if (!ok) {
				throw new IllegalArgumentException("physicalLayer.qkdPhysical." + message);
			}
		}
	}
}
