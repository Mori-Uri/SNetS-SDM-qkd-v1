package qkd.capacity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import network.Link;

/** TDM/key-rate reservation made by one request on one physical link. */
public final class QKDLinkCapacityReservation implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Link link;
    private final double requiredKeyRateBps;
    private final List<QKDChannelShare> shares;
    public QKDLinkCapacityReservation(Link link, double requiredKeyRateBps, List<QKDChannelShare> shares) {
        this.link = link; this.requiredKeyRateBps = requiredKeyRateBps;
        this.shares = Collections.unmodifiableList(new ArrayList<QKDChannelShare>(shares));
    }
    public Link getLink() { return link; }
    public double getRequiredKeyRateBps() { return requiredKeyRateBps; }
    public List<QKDChannelShare> getShares() { return shares; }
    public double getReservedKeyRateBps() {
        double sum=0; for (QKDChannelShare s:shares) sum += s.getReservedKeyRateBps(); return sum;
    }
}
