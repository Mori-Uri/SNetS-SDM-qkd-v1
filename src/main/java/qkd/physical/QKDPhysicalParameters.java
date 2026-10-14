package qkd.physical;

import java.io.Serializable;

/**
 * Immutable physical parameters used by the QKD physical model.
 *
 * <p>Refer\u00eancias: valores padr\u00e3o da Tabela 2 de [MORI26] U. P. Mori, qualifica\u00e7\u00e3o "Planejamento de N\u00f3s Confi\u00e1veis para QKD em SDM-EONs: An\u00e1lise da Coexist\u00eancia Qu\u00e2ntico-Cl\u00e1ssica", UFPI, 2026, reconstru\u00e7\u00e3o de [DIBAJ24] M. R. Dibaj et al., "Cost-Optimized Quantum Communication Networks: The Crucial Role of Trusted Node Placement in Multi-Band and Multi-Fiber Realms", IWCIT 2024 (artigo-base); mesmos e_d, f, eta_d e T_d da Tabela I de [MOGH21] E. E. Moghaddam, H. Beyranvand, J. A. Salehi, "Resource Allocation in SDM-EONs Secured With QKD", IEEE JSAC 39(9), 2021; ajuste alpha(lambda) quadr\u00e1tico em torno de 1550 nm usado por [MEHDI24] P. Mehdizadeh, M. R. Dibaj et al., "Quantum-Classical Coexistence in Multi-Band Optical Networks: A Noise Analysis of QKD", IEEE Commun. Lett. 28(3), 2024.</p>
 */
public final class QKDPhysicalParameters implements Serializable {
    private static final long serialVersionUID = 1L;

    private final double mu;
    private final double pulseIntervalS;
    private final double errorCorrectionInefficiency;
    private final double phaseErrorProbability;
    private final double detectorEfficiency;
    private final double darkCountProbability;
    private final double qchBandwidthHz;
    private final double qchPowerDbm;
    private final double attenuationFloorDbPerKm;
    private final double attenuationQuadratic;
    private final double attenuationCenterNm;
    private final double planckConstantJs;
    private final double speedOfLightMPerS;
    private final double detectorGateWindowS;
    private final double receiverOpticalEfficiency;
    private final QKDCrosstalkReceiverTransferMode receiverTransferMode;
    private final boolean intercoreCrosstalkEnabled;

    public QKDPhysicalParameters() {
        this(0.7, 250e-12, 1.16, 0.015, 0.3, 1e-8,
                12.5e9, -80.0, 0.18, 8e-6, 1550.0,
                6.62607015e-34, 299792458.0,
                100e-12, 1.0,
                QKDCrosstalkReceiverTransferMode.RECEIVER_REFERENCED,
                true);
    }

    public QKDPhysicalParameters(double mu, double pulseIntervalS,
            double errorCorrectionInefficiency, double phaseErrorProbability,
            double detectorEfficiency, double darkCountProbability,
            double qchBandwidthHz, double qchPowerDbm,
            double attenuationFloorDbPerKm, double attenuationQuadratic,
            double attenuationCenterNm, double planckConstantJs,
            double speedOfLightMPerS, double detectorGateWindowS,
            double receiverOpticalEfficiency) {
        this(mu, pulseIntervalS, errorCorrectionInefficiency,
                phaseErrorProbability, detectorEfficiency,
                darkCountProbability, qchBandwidthHz, qchPowerDbm,
                attenuationFloorDbPerKm, attenuationQuadratic,
                attenuationCenterNm, planckConstantJs, speedOfLightMPerS,
                detectorGateWindowS, receiverOpticalEfficiency,
                QKDCrosstalkReceiverTransferMode.RECEIVER_REFERENCED, true);
    }

    public QKDPhysicalParameters(double mu, double pulseIntervalS,
            double errorCorrectionInefficiency, double phaseErrorProbability,
            double detectorEfficiency, double darkCountProbability,
            double qchBandwidthHz, double qchPowerDbm,
            double attenuationFloorDbPerKm, double attenuationQuadratic,
            double attenuationCenterNm, double planckConstantJs,
            double speedOfLightMPerS, double detectorGateWindowS,
            double receiverOpticalEfficiency,
            QKDCrosstalkReceiverTransferMode receiverTransferMode) {
        this(mu, pulseIntervalS, errorCorrectionInefficiency,
                phaseErrorProbability, detectorEfficiency,
                darkCountProbability, qchBandwidthHz, qchPowerDbm,
                attenuationFloorDbPerKm, attenuationQuadratic,
                attenuationCenterNm, planckConstantJs, speedOfLightMPerS,
                detectorGateWindowS, receiverOpticalEfficiency,
                receiverTransferMode, true);
    }

    public QKDPhysicalParameters(double mu, double pulseIntervalS,
            double errorCorrectionInefficiency, double phaseErrorProbability,
            double detectorEfficiency, double darkCountProbability,
            double qchBandwidthHz, double qchPowerDbm,
            double attenuationFloorDbPerKm, double attenuationQuadratic,
            double attenuationCenterNm, double planckConstantJs,
            double speedOfLightMPerS, double detectorGateWindowS,
            double receiverOpticalEfficiency,
            QKDCrosstalkReceiverTransferMode receiverTransferMode,
            boolean intercoreCrosstalkEnabled) {
        requirePositive(mu, "mu");
        requirePositive(pulseIntervalS, "pulseIntervalS");
        requirePositive(errorCorrectionInefficiency, "errorCorrectionInefficiency");
        requireProbability(phaseErrorProbability, "phaseErrorProbability");
        requireProbability(detectorEfficiency, "detectorEfficiency");
        requireProbability(darkCountProbability, "darkCountProbability");
        requirePositive(qchBandwidthHz, "qchBandwidthHz");
        requireNonNegative(attenuationFloorDbPerKm, "attenuationFloorDbPerKm");
        requireNonNegative(attenuationQuadratic, "attenuationQuadratic");
        requirePositive(attenuationCenterNm, "attenuationCenterNm");
        requirePositive(planckConstantJs, "planckConstantJs");
        requirePositive(speedOfLightMPerS, "speedOfLightMPerS");
        requirePositive(detectorGateWindowS, "detectorGateWindowS");
        requireProbability(receiverOpticalEfficiency, "receiverOpticalEfficiency");
        if (receiverTransferMode == null) throw new IllegalArgumentException("receiverTransferMode cannot be null");
        this.mu=mu; this.pulseIntervalS=pulseIntervalS;
        this.errorCorrectionInefficiency=errorCorrectionInefficiency;
        this.phaseErrorProbability=phaseErrorProbability;
        this.detectorEfficiency=detectorEfficiency;
        this.darkCountProbability=darkCountProbability;
        this.qchBandwidthHz=qchBandwidthHz; this.qchPowerDbm=qchPowerDbm;
        this.attenuationFloorDbPerKm=attenuationFloorDbPerKm;
        this.attenuationQuadratic=attenuationQuadratic;
        this.attenuationCenterNm=attenuationCenterNm;
        this.planckConstantJs=planckConstantJs;
        this.speedOfLightMPerS=speedOfLightMPerS;
        this.detectorGateWindowS=detectorGateWindowS;
        this.receiverOpticalEfficiency=receiverOpticalEfficiency;
        this.receiverTransferMode=receiverTransferMode;
        this.intercoreCrosstalkEnabled=intercoreCrosstalkEnabled;
    }

    public QKDPhysicalParameters withReceiverAssumptions(double detectorGateWindowS,
            double receiverOpticalEfficiency,
            QKDCrosstalkReceiverTransferMode receiverTransferMode) {
        return withReceiverAssumptions(detectorGateWindowS,
                receiverOpticalEfficiency, receiverTransferMode,
                intercoreCrosstalkEnabled);
    }

    public QKDPhysicalParameters withReceiverAssumptions(double detectorGateWindowS,
            double receiverOpticalEfficiency,
            QKDCrosstalkReceiverTransferMode receiverTransferMode,
            boolean intercoreCrosstalkEnabled) {
        return new QKDPhysicalParameters(mu,pulseIntervalS,
                errorCorrectionInefficiency,phaseErrorProbability,
                detectorEfficiency,darkCountProbability,qchBandwidthHz,
                qchPowerDbm,attenuationFloorDbPerKm,attenuationQuadratic,
                attenuationCenterNm,planckConstantJs,speedOfLightMPerS,
                detectorGateWindowS,receiverOpticalEfficiency,
                receiverTransferMode,intercoreCrosstalkEnabled);
    }

    public double attenuationDbPerKm(double wavelengthNm) {
        requirePositive(wavelengthNm, "wavelengthNm");
        double delta=wavelengthNm-attenuationCenterNm;
        return attenuationFloorDbPerKm+attenuationQuadratic*delta*delta;
    }
    private static void requirePositive(double v,String n){if(!Double.isFinite(v)||v<=0)throw new IllegalArgumentException(n+" must be finite and > 0");}
    private static void requireNonNegative(double v,String n){if(!Double.isFinite(v)||v<0)throw new IllegalArgumentException(n+" must be finite and >= 0");}
    private static void requireProbability(double v,String n){if(!Double.isFinite(v)||v<0||v>1)throw new IllegalArgumentException(n+" must be in [0, 1]");}

    public double getMu(){return mu;} public double getPulseIntervalS(){return pulseIntervalS;}
    public double getErrorCorrectionInefficiency(){return errorCorrectionInefficiency;}
    public double getPhaseErrorProbability(){return phaseErrorProbability;}
    public double getDetectorEfficiency(){return detectorEfficiency;}
    public double getDarkCountProbability(){return darkCountProbability;}
    public double getQchBandwidthHz(){return qchBandwidthHz;} public double getQchPowerDbm(){return qchPowerDbm;}
    public double getAttenuationFloorDbPerKm(){return attenuationFloorDbPerKm;}
    public double getAttenuationQuadratic(){return attenuationQuadratic;}
    public double getAttenuationCenterNm(){return attenuationCenterNm;}
    public double getPlanckConstantJs(){return planckConstantJs;} public double getSpeedOfLightMPerS(){return speedOfLightMPerS;}
    public double getDetectorGateWindowS(){return detectorGateWindowS;}
    public double getReceiverOpticalEfficiency(){return receiverOpticalEfficiency;}
    public QKDCrosstalkReceiverTransferMode getReceiverTransferMode(){return receiverTransferMode;}
    public boolean isIntercoreCrosstalkEnabled(){return intercoreCrosstalkEnabled;}
}
