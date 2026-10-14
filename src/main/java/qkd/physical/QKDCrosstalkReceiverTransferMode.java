package qkd.physical;

/**
 * Defines how span-generated inter-core crosstalk power is referenced to the
 * input of the QKD receiver.
 *
 * <p>{@link #RECEIVER_REFERENCED} preserves the operational approximation used
 * in Parts 7-11: the span-specific PXT value is interpreted as already
 * referenced to Bob's optical input (T_{span->Rx}=1).</p>
 *
 * <p>{@link #DISTRIBUTED_QUANTUM_ATTENUATION_REFERENCE} is a verification and
 * sensitivity model derived from the distributed expression in the technical
 * note. It assumes constant classical interferer power, constant spectral
 * overlap and coupling coefficient along the span, while the crosstalk
 * contribution generated at position z experiences the remaining quantum
 * attenuation to Bob. It is intentionally not described as a full distributed
 * MCF propagation model because P_n(z), h(z) and ISO(z) remain constant.</p>
 *
 * <p>Refer\u00eancias: [NOTA-RX] U. P. Mori, nota t\u00e9cnica "Defini\u00e7\u00e3o da contribui\u00e7\u00e3o de ru\u00eddo do crosstalk intercore no receptor QKD", 2026 (RECEIVER_REFERENCED e DISTRIBUTED); Eq. (1) de [MOGH21] E. E. Moghaddam, H. Beyranvand, J. A. Salehi, "Resource Allocation in SDM-EONs Secured With QKD", IEEE JSAC 39(9), 2021 (ATTENUATED_SPAN).</p>
 */
public enum QKDCrosstalkReceiverTransferMode {

    RECEIVER_REFERENCED,
    DISTRIBUTED_QUANTUM_ATTENUATION_REFERENCE,
    /**
     * Part 26: P_Rx = P_n * ISO * h * L * 10^(-alpha L / 10), the inter-core crosstalk noise
     * of a classical channel that propagates with the QCh over the whole span (Moghaddam,
     * Beyranvand, Salehi, IEEE JSAC 39(9), 2021, Eq. (1): I_XT(L) = P_0 e^{-alpha L} h L).
     */
    ATTENUATED_SPAN;

    /**
     * Parses the value used in scenario JSON files.
     */
    public static QKDCrosstalkReceiverTransferMode fromConfig(String value) {
        if (value == null || value.trim().isEmpty()) {
            return RECEIVER_REFERENCED;
        }

        String normalized = value.trim()
                .toUpperCase()
                .replace('-', '_')
                .replace(' ', '_');

        if ("RECEIVER_REFERENCED".equals(normalized)
                || "T_EQ_1".equals(normalized)
                || "T1".equals(normalized)) {
            return RECEIVER_REFERENCED;
        }

        if ("DISTRIBUTED_QUANTUM_ATTENUATION_REFERENCE".equals(normalized)
                || "DISTRIBUTED".equals(normalized)
                || "DISTRIBUTED_REFERENCE".equals(normalized)) {
            return DISTRIBUTED_QUANTUM_ATTENUATION_REFERENCE;
        }

        if ("ATTENUATED".equals(normalized) || "ATTENUATED_SPAN".equals(normalized)
                || "MOGHADDAM".equals(normalized)) {
            return ATTENUATED_SPAN;
        }

        throw new IllegalArgumentException(
                "Unknown QKD receiver transfer model: " + value);
    }
}
