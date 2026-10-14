package qkd.capacity;

import java.io.Serializable;

/** Portion of one request's key-rate demand carried by one active QCh. */
public final class QKDChannelShare implements Serializable {
    private static final long serialVersionUID = 1L;
    private final QKDSharedChannel channel;
    private final double reservedKeyRateBps;
    private final int reservedSlots;

    public QKDChannelShare(QKDSharedChannel channel, double reservedKeyRateBps) {
        this(channel, reservedKeyRateBps, 0);
    }

    public QKDChannelShare(QKDSharedChannel channel, double reservedKeyRateBps, int reservedSlots) {
        if (channel == null || !Double.isFinite(reservedKeyRateBps) || reservedKeyRateBps <= 0.0
                || reservedSlots < 0)
            throw new IllegalArgumentException("invalid channel share");
        this.channel = channel; this.reservedKeyRateBps = reservedKeyRateBps;
        this.reservedSlots = reservedSlots;
    }
    public QKDSharedChannel getChannel() { return channel; }
    public double getReservedKeyRateBps() { return reservedKeyRateBps; }
    /** TDM slots reserved by this share (0 in continuous sharing). */
    public int getReservedSlots() { return reservedSlots; }
}
