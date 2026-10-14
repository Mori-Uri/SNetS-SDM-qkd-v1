package qkd.control;

import java.io.Serializable;

/** Result of the QKD side of admission after the classical circuit exists. */
public final class QKDProvisioningResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean success;
    private final QKDActiveConnection connection;
    private final String failureReason;

    private QKDProvisioningResult(boolean success,
            QKDActiveConnection connection,
            String failureReason) {
        this.success = success;
        this.connection = connection;
        this.failureReason = failureReason;
    }

    public static QKDProvisioningResult success(QKDActiveConnection connection) {
        if (connection == null) {
            throw new IllegalArgumentException("connection cannot be null");
        }
        return new QKDProvisioningResult(true, connection, null);
    }

    public static QKDProvisioningResult failure(String reason) {
        return new QKDProvisioningResult(false, null,
                reason == null ? "unspecified QKD provisioning failure" : reason);
    }

    public boolean isSuccess() {
        return success;
    }

    public QKDActiveConnection getConnection() {
        return connection;
    }

    public String getFailureReason() {
        return failureReason;
    }

    @Override
    public String toString() {
        return success
                ? "QKDProvisioningResult{success, " + connection + '}':
                "QKDProvisioningResult{failure, reason='" + failureReason + "'}";
    }
}
