package qkd.resource;

import java.io.Serializable;

/** Result of an attempted QKD spectrum reservation. */
public final class QKDSpectrumAllocationResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean success;
    private final QKDSession session;
    private final int failedPhysicalLinkIndex;
    private final String failureReason;

    private QKDSpectrumAllocationResult(boolean success, QKDSession session,
            int failedPhysicalLinkIndex, String failureReason) {
        this.success = success;
        this.session = session;
        this.failedPhysicalLinkIndex = failedPhysicalLinkIndex;
        this.failureReason = failureReason;
    }

    public static QKDSpectrumAllocationResult success(QKDSession session) {
        if (session == null) {
            throw new IllegalArgumentException("session cannot be null");
        }
        return new QKDSpectrumAllocationResult(true, session, -1, null);
    }

    public static QKDSpectrumAllocationResult failure(int failedPhysicalLinkIndex,
            String failureReason) {
        return new QKDSpectrumAllocationResult(false, null,
                failedPhysicalLinkIndex, failureReason);
    }

    public boolean isSuccess() {
        return success;
    }

    public QKDSession getSession() {
        return session;
    }

    public int getFailedPhysicalLinkIndex() {
        return failedPhysicalLinkIndex;
    }

    public String getFailureReason() {
        return failureReason;
    }

    @Override
    public String toString() {
        if (success) {
            return "QKDSpectrumAllocationResult{success, session=" + session + '}';
        }
        return "QKDSpectrumAllocationResult{failure, failedPhysicalLinkIndex="
                + failedPhysicalLinkIndex + ", reason='" + failureReason + "'}";
    }
}
