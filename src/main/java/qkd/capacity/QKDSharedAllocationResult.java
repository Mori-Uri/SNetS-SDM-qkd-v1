package qkd.capacity;

import qkd.resource.QKDSession;

public final class QKDSharedAllocationResult {
    private final boolean success;
    private final QKDSession session;
    private final String failureReason;
    private QKDSharedAllocationResult(boolean success, QKDSession session, String failureReason) {
        this.success=success; this.session=session; this.failureReason=failureReason;
    }
    public static QKDSharedAllocationResult success(QKDSession s){ return new QKDSharedAllocationResult(true,s,null); }
    public static QKDSharedAllocationResult failure(String r){ return new QKDSharedAllocationResult(false,null,r); }
    public boolean isSuccess(){return success;} public QKDSession getSession(){return session;} public String getFailureReason(){return failureReason;}
}
