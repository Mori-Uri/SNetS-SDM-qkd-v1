package qkd.capacity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import network.Link;
import qkd.network.QKDLinkPlan;
import qkd.resource.QKDChannelAllocation;

/**
 * One active discrete QCh whose SKR is shared by key demands through TDM.
 *
 * <p>Two granularities are supported (network.qkd.qchGranularity):</p>
 * <ul>
 * <li><b>link</b> (original Parts 12/13 behaviour): the QCh covers every
 * QTND segment of a physical link; its capacity is the bottleneck segment
 * and it counts one Q-TRx pair per covered segment.</li>
 * <li><b>span</b>: the QCh belongs to one QTND segment only (one Q-TRx pair),
 * as in the trusted-node planning of Dibaj et al. (2024) and the
 * qualification reconstruction. {@link #getEvaluationPlan()} then returns a
 * single-segment plan.</li>
 * </ul>
 *
 * <p>When {@code tdmSlots > 0} the capacity is divided in that many discrete
 * time slots and each demand reserves ceil(demand / capacity * slots).</p>
 *
 * <p>Refer\u00eancias: [DIBAJ24] (QCh compartilhado; um par Q-TRx por trecho).</p>
 */
public final class QKDSharedChannel implements Serializable {
    private static final long serialVersionUID = 1L;
    private final long id;
    private final Link link;
    private final QKDLinkPlan linkPlan;
    private final QKDLinkPlan evaluationPlan;
    private final QKDChannelAllocation allocation;
    private final int spanIndex;
    private final int lane;
    private final double wavelengthNm;
    private final int tdmSlots;
    private final Object poolKey;
    private final List<Link> spectrumLinks;
    private QKDLinkChannelPhysicalResult physical;
    private double usedKeyRateBps;
    private int usedSlots;

    QKDSharedChannel(long id, Link link, QKDLinkPlan linkPlan,
            QKDChannelAllocation allocation, QKDLinkChannelPhysicalResult physical) {
        this(id, link, linkPlan, linkPlan, allocation, physical, -1, 0,
                Double.NaN, 0, null, Collections.singletonList(link));
    }

    QKDSharedChannel(long id, Link link, QKDLinkPlan linkPlan,
            QKDLinkPlan evaluationPlan, QKDChannelAllocation allocation,
            QKDLinkChannelPhysicalResult physical, int spanIndex, int lane,
            double wavelengthNm, int tdmSlots, Object poolKey,
            List<Link> spectrumLinks) {
        this.id = id; this.link = link; this.linkPlan = linkPlan;
        this.evaluationPlan = evaluationPlan == null ? linkPlan : evaluationPlan;
        this.allocation = allocation; this.physical = physical;
        this.spanIndex = spanIndex; this.lane = lane;
        this.wavelengthNm = wavelengthNm; this.tdmSlots = tdmSlots;
        this.poolKey = poolKey;
        this.spectrumLinks = Collections.unmodifiableList(new ArrayList<Link>(spectrumLinks));
    }

    public long getId() { return id; }
    public Link getLink() { return link; }
    public QKDLinkPlan getLinkPlan() { return linkPlan; }
    /** Plan used for the physical evaluation (whole link, or the single span). */
    public QKDLinkPlan getEvaluationPlan() { return evaluationPlan; }
    public QKDChannelAllocation getAllocation() { return allocation; }
    public QKDLinkChannelPhysicalResult getPhysicalResult() { return physical; }
    public void setPhysicalResult(QKDLinkChannelPhysicalResult v) { physical = v; }
    public double getCapacityBps() { return physical.getMinimumSecretKeyRateBps(); }
    public double getCapacityMbps() { return getCapacityBps()/1e6; }
    public double getUsedKeyRateBps() { return usedKeyRateBps; }
    public double getResidualKeyRateBps() { return Math.max(0.0, getCapacityBps()-usedKeyRateBps); }
    /** Span index for span-granularity QChs, -1 for link-granularity QChs. */
    public int getSpanIndex() { return spanIndex; }
    public boolean isSpanChannel() { return spanIndex >= 0; }
    /** Index of the QCh inside its pool (also the wavelength-grid position). */
    public int getLane() { return lane; }
    public int getTdmSlots() { return tdmSlots; }
    public int getUsedSlots() { return usedSlots; }
    public int getFreeSlots() { return Math.max(0, tdmSlots - usedSlots); }
    Object getPoolKey() { return poolKey; }
    List<Link> getSpectrumLinks() { return spectrumLinks; }

    /** Wavelength of this QCh, or the supplied default when it has none of its own. */
    public double wavelengthOr(double defaultNm) {
        return Double.isNaN(wavelengthNm) ? defaultNm : wavelengthNm;
    }

    public double getWavelengthNm() { return wavelengthNm; }

    public int getQTrxPairs() { return isSpanChannel() ? 1 : allocation.getCoveredQkdSpans(); }

    /** Number of TDM slots needed by a demand on a channel of the given capacity. */
    public static int slotsFor(double demandBps, double capacityBps, int tdmSlots) {
        if (tdmSlots <= 0) return 0;
        if (!(capacityBps > 0.0)) return Integer.MAX_VALUE;
        return Math.max(1, (int) Math.ceil(demandBps / capacityBps * tdmSlots - 1e-12));
    }

    void reserve(double rate) { usedKeyRateBps += rate; }
    void reserve(double rate, int slots) { usedKeyRateBps += rate; usedSlots += slots; }
    void release(double rate) { usedKeyRateBps -= rate; if (usedKeyRateBps < 1e-9) usedKeyRateBps = 0.0; }
    void release(double rate, int slots) {
        release(rate);
        usedSlots -= slots;
        if (usedSlots < 0) usedSlots = 0;
    }
    public boolean isIdle() { return usedKeyRateBps <= 1e-9 && usedSlots <= 0; }
}
