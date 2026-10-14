package qkd.physical;

import java.io.Serializable;

/**
 * Detailed contribution of one classical SNetS circuit to the inter-core
 * crosstalk power incident on a candidate quantum channel in one link.
 *
 * <p>The power contribution follows the same operational expression used by
 * the current SNetS {@code network.Crosstalk} implementation:</p>
 *
 * <pre>
 * P_XT,n^(e) = P_n * ISO(q,n) * h_e * L_e
 * </pre>
 *
 * <p>where {@code P_n} is the launch power of the classical interferer,
 * {@code ISO(q,n)} is the spectral-overlap index, {@code h_e} is the power
 * coupling coefficient in 1/m and {@code L_e} is the link length in metres.</p>
 *
 * <p>This object is intentionally descriptive. It does not allocate or release
 * resources. Keeping the individual terms available makes the QKD/SNetS bridge
 * auditable during the verification stages.</p>
 */
public final class QKDCrosstalkContribution implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Integer circuitId;
    private final int interfererCoreId;
    private final int interfererSpectrumStart;
    private final int interfererSpectrumEnd;
    private final int overlapSlots;
    private final int interfererSlots;
    private final double overlapIndex;
    private final double launchPowerW;
    private final double couplingCoefficientPerM;
    private final double linkDistanceKm;
    private final double powerContributionW;

    public QKDCrosstalkContribution(
            Integer circuitId,
            int interfererCoreId,
            int interfererSpectrumStart,
            int interfererSpectrumEnd,
            int overlapSlots,
            int interfererSlots,
            double overlapIndex,
            double launchPowerW,
            double couplingCoefficientPerM,
            double linkDistanceKm,
            double powerContributionW) {
        this.circuitId = circuitId;
        this.interfererCoreId = interfererCoreId;
        this.interfererSpectrumStart = interfererSpectrumStart;
        this.interfererSpectrumEnd = interfererSpectrumEnd;
        this.overlapSlots = overlapSlots;
        this.interfererSlots = interfererSlots;
        this.overlapIndex = overlapIndex;
        this.launchPowerW = launchPowerW;
        this.couplingCoefficientPerM = couplingCoefficientPerM;
        this.linkDistanceKm = linkDistanceKm;
        this.powerContributionW = powerContributionW;
    }

    public Integer getCircuitId() { return circuitId; }
    public int getInterfererCoreId() { return interfererCoreId; }
    public int getInterfererSpectrumStart() { return interfererSpectrumStart; }
    public int getInterfererSpectrumEnd() { return interfererSpectrumEnd; }
    public int getOverlapSlots() { return overlapSlots; }
    public int getInterfererSlots() { return interfererSlots; }
    public double getOverlapIndex() { return overlapIndex; }
    public double getLaunchPowerW() { return launchPowerW; }
    public double getCouplingCoefficientPerM() { return couplingCoefficientPerM; }
    public double getLinkDistanceKm() { return linkDistanceKm; }
    public double getPowerContributionW() { return powerContributionW; }

    @Override
    public String toString() {
        return "QKDCrosstalkContribution{" +
                "circuitId=" + circuitId +
                ", core=" + interfererCoreId +
                ", spectrum=[" + interfererSpectrumStart + ',' + interfererSpectrumEnd + ']' +
                ", overlapSlots=" + overlapSlots +
                ", interfererSlots=" + interfererSlots +
                ", ISO=" + overlapIndex +
                ", PnW=" + launchPowerW +
                ", hPerM=" + couplingCoefficientPerM +
                ", linkKm=" + linkDistanceKm +
                ", contributionW=" + powerContributionW +
                '}';
    }
}
