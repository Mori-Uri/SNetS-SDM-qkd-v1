package qkd.physical;

import java.io.Serializable;

/**
 * Intermediate quantities of the crosstalk-power to detector-noise conversion.
 */
public final class QKDCrosstalkNoiseResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final double receivedCrosstalkPowerW;
    private final double detectorCrosstalkPowerW;
    private final double photonEnergyJ;
    private final double meanDetectedXtEvents;
    private final double crosstalkBackgroundProbability;
    private final double totalBackgroundProbability;

    public QKDCrosstalkNoiseResult(
            double receivedCrosstalkPowerW,
            double detectorCrosstalkPowerW,
            double photonEnergyJ,
            double meanDetectedXtEvents,
            double crosstalkBackgroundProbability,
            double totalBackgroundProbability) {
        this.receivedCrosstalkPowerW = receivedCrosstalkPowerW;
        this.detectorCrosstalkPowerW = detectorCrosstalkPowerW;
        this.photonEnergyJ = photonEnergyJ;
        this.meanDetectedXtEvents = meanDetectedXtEvents;
        this.crosstalkBackgroundProbability = crosstalkBackgroundProbability;
        this.totalBackgroundProbability = totalBackgroundProbability;
    }

    public double getReceivedCrosstalkPowerW() { return receivedCrosstalkPowerW; }
    public double getDetectorCrosstalkPowerW() { return detectorCrosstalkPowerW; }
    public double getPhotonEnergyJ() { return photonEnergyJ; }
    public double getMeanDetectedXtEvents() { return meanDetectedXtEvents; }
    public double getCrosstalkBackgroundProbability() { return crosstalkBackgroundProbability; }
    public double getTotalBackgroundProbability() { return totalBackgroundProbability; }

    @Override
    public String toString() {
        return "QKDCrosstalkNoiseResult{" +
                "PxtRxW=" + receivedCrosstalkPowerW +
                ", PxtDetectorW=" + detectorCrosstalkPowerW +
                ", photonEnergyJ=" + photonEnergyJ +
                ", meanXtEvents=" + meanDetectedXtEvents +
                ", pXt=" + crosstalkBackgroundProbability +
                ", pb=" + totalBackgroundProbability +
                '}';
    }
}
