package qkd.resource;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import qkd.network.QKDRoutePlan;
import qkd.capacity.QKDLinkCapacityReservation;

/**
 * Active logical QKD session and its currently reserved optical resources.
 *
 * <p>This class intentionally models only network-resource state. It does not
 * model encryption, post-processing, key pools, eavesdropping, or protocol
 * messages. Those processes remain outside the abstraction level of SNetS.</p>
 */
public final class QKDSession implements Serializable {

    private static final long serialVersionUID = 1L;

    private final QKDRoutePlan routePlan;
    private final List<QKDChannelAllocation> allocations;
    private final int requiredQuantumSlotsPerPhysicalLink;
    private final List<QKDLinkCapacityReservation> capacityReservations;
    private final boolean sharedCapacityManaged;
    private boolean active;

    QKDSession(QKDRoutePlan routePlan,
            List<QKDChannelAllocation> allocations,
            int requiredQuantumSlotsPerPhysicalLink) {
        if (routePlan == null) {
            throw new IllegalArgumentException("routePlan cannot be null");
        }
        if (allocations == null || allocations.isEmpty()) {
            throw new IllegalArgumentException("allocations cannot be null/empty");
        }
        if (requiredQuantumSlotsPerPhysicalLink <= 0) {
            throw new IllegalArgumentException(
                    "requiredQuantumSlotsPerPhysicalLink must be > 0");
        }
        this.routePlan = routePlan;
        this.allocations = Collections.unmodifiableList(
                new ArrayList<QKDChannelAllocation>(allocations));
        this.requiredQuantumSlotsPerPhysicalLink =
                requiredQuantumSlotsPerPhysicalLink;
        this.capacityReservations = Collections.emptyList();
        this.sharedCapacityManaged = false;
        this.active = true;
    }


    /** Creates a session backed by shared discrete QCh capacity/TDM reservations. */
    public static QKDSession forSharedCapacity(QKDRoutePlan routePlan,
            List<QKDChannelAllocation> allocations,
            List<QKDLinkCapacityReservation> capacityReservations,
            int qchSlots) {
        if (routePlan == null || allocations == null || allocations.isEmpty()
                || capacityReservations == null || capacityReservations.isEmpty()) {
            throw new IllegalArgumentException("shared QKD session inputs cannot be null/empty");
        }
        return new QKDSession(routePlan, allocations, capacityReservations, qchSlots);
    }

    private QKDSession(QKDRoutePlan routePlan,
            List<QKDChannelAllocation> allocations,
            List<QKDLinkCapacityReservation> capacityReservations,
            int qchSlots) {
        this.routePlan = routePlan;
        this.allocations = Collections.unmodifiableList(
                new ArrayList<QKDChannelAllocation>(allocations));
        this.requiredQuantumSlotsPerPhysicalLink = qchSlots;
        this.capacityReservations = Collections.unmodifiableList(
                new ArrayList<QKDLinkCapacityReservation>(capacityReservations));
        this.sharedCapacityManaged = true;
        this.active = true;
    }

    public QKDRoutePlan getRoutePlan() {
        return routePlan;
    }

    public List<QKDChannelAllocation> getAllocations() {
        return allocations;
    }

    public int getRequiredQuantumSlotsPerPhysicalLink() {
        return requiredQuantumSlotsPerPhysicalLink;
    }

    public int getPhysicalLinkAllocationCount() {
        return sharedCapacityManaged ? capacityReservations.size() : allocations.size();
    }

    public boolean isSharedCapacityManaged() { return sharedCapacityManaged; }

    public List<QKDLinkCapacityReservation> getCapacityReservations() {
        return capacityReservations;
    }

    public int getDistinctQuantumChannelCount() {
        if (!sharedCapacityManaged) return allocations.size();
        java.util.HashSet<Long> ids = new java.util.HashSet<Long>();
        for (QKDLinkCapacityReservation r : capacityReservations)
            for (qkd.capacity.QKDChannelShare share : r.getShares()) ids.add(share.getChannel().getId());
        return ids.size();
    }

    public double getProvisionedKeyRateBps() {
        if (!sharedCapacityManaged || capacityReservations.isEmpty()) return Double.NaN;
        double min = Double.POSITIVE_INFINITY;
        for (QKDLinkCapacityReservation r : capacityReservations)
            min = Math.min(min, r.getReservedKeyRateBps());
        return min;
    }

    public int getQTrxPairsUsedBySession() {
        if (!sharedCapacityManaged) return getCoveredQkdSpanCount();
        java.util.HashSet<Long> ids = new java.util.HashSet<Long>();
        int total = 0;
        for (QKDLinkCapacityReservation r : capacityReservations) {
            for (qkd.capacity.QKDChannelShare share : r.getShares()) {
                qkd.capacity.QKDSharedChannel c = share.getChannel();
                if (ids.add(c.getId())) total += c.getQTrxPairs();
            }
        }
        return total;
    }

    public int getCoveredQkdSpanCount() {
        int total = 0;
        for (QKDChannelAllocation allocation : allocations) {
            total += allocation.getCoveredQkdSpans();
        }
        return total;
    }

    public boolean isActive() {
        return active;
    }

    public void markReleased() {
        active = false;
    }

    @Override
    public String toString() {
        return "QKDSession{" + routePlan.getSource() + "->"
                + routePlan.getDestination()
                + ", physicalLinkAllocations=" + allocations.size()
                + ", coveredSpans=" + getCoveredQkdSpanCount()
                + ", requiredSlotsPerLink="
                + requiredQuantumSlotsPerPhysicalLink
                + ", active=" + active
                + '}';
    }
}
