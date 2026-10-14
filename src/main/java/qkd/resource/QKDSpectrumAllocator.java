package qkd.resource;

import java.util.ArrayList;
import java.util.List;

import gprmcsa.routing.Route;
import network.Core;
import network.Link;
import qkd.network.QKDLinkPlan;
import qkd.network.QKDRoutePlan;

/**
 * First resource allocator for QKD channels on the dedicated QKD core.
 *
 * <p>Part 8 intentionally does not implement end-to-end QKD spectrum
 * continuity. Each physical link independently receives its first available
 * contiguous band on the configured quantum core. Consequently two
 * consecutive physical links may use different QKD slot indices. This keeps
 * the present model simple and leaves continuity/wavelength optimization for a
 * later algorithmic stage.</p>
 *
 * <p>The operation is transactional: if any physical link cannot be reserved,
 * every reservation already made for the same attempt is rolled back.</p>
 */
public final class QKDSpectrumAllocator {

    public QKDSpectrumAllocationResult allocate(Route classicalRoute,
            QKDRoutePlan qkdRoutePlan, int quantumCoreId,
            int requiredQuantumSlotsPerPhysicalLink, int guardBand) {

        validateInputs(classicalRoute, qkdRoutePlan, quantumCoreId,
                requiredQuantumSlotsPerPhysicalLink, guardBand);

        List<QKDChannelAllocation> committed =
                new ArrayList<QKDChannelAllocation>();

        try {
            for (int i = 0; i < classicalRoute.getLinkList().size(); i++) {
                Link link = classicalRoute.getLinkList().get(i);
                QKDLinkPlan linkPlan = qkdRoutePlan.getLinkPlans().get(i);

                ensureSamePhysicalLink(link, linkPlan, i);

                Core qkdCore = link.getCore(quantumCoreId);
                if (qkdCore == null) {
                    rollbackQuietly(committed);
                    return QKDSpectrumAllocationResult.failure(i,
                            "QKD core " + quantumCoreId
                            + " does not exist on link " + link.getName());
                }
                if (!qkdCore.isQkdReserved()) {
                    rollbackQuietly(committed);
                    return QKDSpectrumAllocationResult.failure(i,
                            "Core " + quantumCoreId + " on link "
                            + link.getName() + " is not reserved for QKD");
                }

                int[] selectedBand = firstFit(
                        qkdCore.getFreeSpectrumBandsForQKD(guardBand),
                        requiredQuantumSlotsPerPhysicalLink);
                if (selectedBand == null) {
                    rollbackQuietly(committed);
                    return QKDSpectrumAllocationResult.failure(i,
                            "Insufficient contiguous QKD spectrum on link "
                            + link.getName());
                }

                boolean allocated = qkdCore.useSpectrumForQKD(
                        selectedBand, guardBand);
                if (!allocated) {
                    rollbackQuietly(committed);
                    return QKDSpectrumAllocationResult.failure(i,
                            "QKD spectrum allocation rejected on link "
                            + link.getName());
                }

                committed.add(new QKDChannelAllocation(
                        link,
                        quantumCoreId,
                        selectedBand,
                        guardBand,
                        linkPlan.getSpanCount()));
            }
        } catch (Exception exception) {
            rollbackQuietly(committed);
            return QKDSpectrumAllocationResult.failure(
                    Math.min(committed.size(),
                            classicalRoute.getLinkList().size() - 1),
                    "Exception during QKD spectrum allocation: "
                    + exception.getMessage());
        }

        QKDSession session = new QKDSession(
                qkdRoutePlan, committed,
                requiredQuantumSlotsPerPhysicalLink);
        return QKDSpectrumAllocationResult.success(session);
    }

    /**
     * Releases every physical-link QKD reservation belonging to the session.
     * Returns false when the session had already been released.
     */
    public boolean release(QKDSession session) throws Exception {
        if (session == null) {
            throw new IllegalArgumentException("session cannot be null");
        }
        if (!session.isActive()) {
            return false;
        }

        /*
         * Release in reverse order. This mirrors transactional rollback and is
         * useful if later stages attach additional per-link state.
         */
        List<QKDChannelAllocation> allocations = session.getAllocations();
        for (int i = allocations.size() - 1; i >= 0; i--) {
            QKDChannelAllocation allocation = allocations.get(i);
            Core core = allocation.getLink().getCore(
                    allocation.getQuantumCoreId());
            if (core == null || !core.isQkdReserved()) {
                throw new IllegalStateException(
                        "QKD core disappeared before release on link "
                        + allocation.getLink().getName());
            }
            core.liberateSpectrumForQKD(
                    allocation.getSpectrumBand(),
                    allocation.getGuardBand());
        }

        session.markReleased();
        return true;
    }

    /** First-fit inside the already guard-band-adjusted free intervals. */
    int[] firstFit(List<int[]> freeBands, int requiredSlots) {
        if (freeBands == null) {
            return null;
        }
        for (int[] band : freeBands) {
            if (band == null || band.length != 2) {
                continue;
            }
            int available = band[1] - band[0] + 1;
            if (available >= requiredSlots) {
                return new int[] {
                        band[0],
                        band[0] + requiredSlots - 1
                };
            }
        }
        return null;
    }

    private void validateInputs(Route classicalRoute,
            QKDRoutePlan qkdRoutePlan, int quantumCoreId,
            int requiredQuantumSlotsPerPhysicalLink, int guardBand) {
        if (classicalRoute == null) {
            throw new IllegalArgumentException("classicalRoute cannot be null");
        }
        if (qkdRoutePlan == null) {
            throw new IllegalArgumentException("qkdRoutePlan cannot be null");
        }
        if (classicalRoute.getLinkList() == null
                || classicalRoute.getLinkList().isEmpty()) {
            throw new IllegalArgumentException(
                    "classicalRoute must contain at least one link");
        }
        if (qkdRoutePlan.getPhysicalLinkCount()
                != classicalRoute.getLinkList().size()) {
            throw new IllegalArgumentException(
                    "qkdRoutePlan and classicalRoute link counts differ");
        }
        if (quantumCoreId < 0) {
            throw new IllegalArgumentException("quantumCoreId must be >= 0");
        }
        if (requiredQuantumSlotsPerPhysicalLink <= 0) {
            throw new IllegalArgumentException(
                    "requiredQuantumSlotsPerPhysicalLink must be > 0");
        }
        if (guardBand < 0) {
            throw new IllegalArgumentException("guardBand must be >= 0");
        }
    }

    private void ensureSamePhysicalLink(Link link, QKDLinkPlan plan,
            int index) {
        String source = link.getSource().getName();
        String destination = link.getDestination().getName();
        if (!source.equals(plan.getSource())
                || !destination.equals(plan.getDestination())) {
            throw new IllegalArgumentException(
                    "QKD route/link mismatch at physical-link index " + index
                    + ": route has " + source + "->" + destination
                    + " but QKD plan has " + plan.getSource() + "->"
                    + plan.getDestination());
        }
    }

    private void rollbackQuietly(List<QKDChannelAllocation> allocations) {
        for (int i = allocations.size() - 1; i >= 0; i--) {
            QKDChannelAllocation allocation = allocations.get(i);
            try {
                Core core = allocation.getLink().getCore(
                        allocation.getQuantumCoreId());
                if (core != null && core.isQkdReserved()) {
                    core.liberateSpectrumForQKD(
                            allocation.getSpectrumBand(),
                            allocation.getGuardBand());
                }
            } catch (Exception ignored) {
                /*
                 * Best effort only. Verification checks that the normal
                 * resource-failure path rolls back cleanly.
                 */
            }
        }
    }
}
