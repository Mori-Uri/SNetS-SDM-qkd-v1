package qkd.traffic;

/**
 * Identifies whether an SNetS traffic request needs QKD protection.
 *
 * <p>Part 5 introduces only the request classification. Both request types
 * still follow the original classical provisioning flow until the QKD
 * admission/resource logic is connected in later parts.</p>
 */
public enum RequestProtectionType {

    /** Ordinary optical request. */
    CLASSICAL,

    /** Optical data request that will additionally require QKD resources. */
    QKD_PROTECTED
}
