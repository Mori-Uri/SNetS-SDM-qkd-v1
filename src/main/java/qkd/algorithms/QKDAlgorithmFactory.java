package qkd.algorithms;

import gprmcsa.coreSpectrumAssignment.CoreAndSpectrumAssignmentAlgorithmInterface;
import gprmcsa.integrated.IntegratedRMLSAAlgorithmInterface;
import qkd.algorithms.coreSpectrumAssignment.QABNE;
import qkd.algorithms.coreSpectrumAssignment.QKDFirstFit;
import qkd.algorithms.integrated.QKDReferenceRMSTA;

/**
 * Factory for algorithms that belong to the optional QKD addon.
 *
 * <p>No QKD algorithm name is added to the legacy {@code GPRMCSA} factory.
 * Classical algorithm selection therefore remains unchanged.</p>
 *
 * <p>Refer\u00eancias: [SNETS17], [ABNE20], [DIBAJ24].</p>
 */
public final class QKDAlgorithmFactory {

    /** ABNE adapted to a reserved QKD core. */
    public static final String CORE_SPECTRUM_QABNE = "qabne";
    /** First fit over the classical cores (lowest slot, then lowest core). */
    public static final String CORE_SPECTRUM_QFIRSTFIT = "qfirstfit";
    /** Classical RMSTA of Dibaj et al. (2024): K paths, min ending channel index. */
    public static final String INTEGRATED_QRMSTA = "qrmsta";

    private QKDAlgorithmFactory() {
    }

    public static CoreAndSpectrumAssignmentAlgorithmInterface
            instantiateCoreAndSpectrumAssignment(String name) {
        if (name == null) {
            return null;
        }
        String n = name.trim();
        if (CORE_SPECTRUM_QABNE.equalsIgnoreCase(n)) {
            return new QABNE();
        }
        if (CORE_SPECTRUM_QFIRSTFIT.equalsIgnoreCase(n)) {
            return new QKDFirstFit();
        }
        return null;
    }

    public static boolean isQkdCoreAndSpectrumAlgorithm(String name) {
        String n = name == null ? "" : name.trim();
        return CORE_SPECTRUM_QABNE.equalsIgnoreCase(n) || CORE_SPECTRUM_QFIRSTFIT.equalsIgnoreCase(n);
    }

    /** Returns the QKD-addon integrated RMLSA for this name, or null when the name is not a QKD one. */
    public static IntegratedRMLSAAlgorithmInterface instantiateIntegrated(String name) {
        if (name != null && INTEGRATED_QRMSTA.equalsIgnoreCase(name.trim())) {
            return new QKDReferenceRMSTA();
        }
        return null;
    }

    public static boolean isQkdIntegratedAlgorithm(String name) {
        return name != null && INTEGRATED_QRMSTA.equalsIgnoreCase(name.trim());
    }
}
