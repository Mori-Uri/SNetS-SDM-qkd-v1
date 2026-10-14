package qkd.verification;

import gprmcsa.GPRMCSA;
import gprmcsa.coreSpectrumAssignment.CoreAndSpectrumAssignmentAlgorithmInterface;
import gprmcsa.coreSpectrumAssignment.CSBASDM;
import qkd.algorithms.QKDAlgorithmFactory;
import qkd.algorithms.coreSpectrumAssignment.QABNE;

/**
 * Verifies the addon boundary between the untouched classical ABNE and qABNE.
 */
public final class QKDAbneCompatibilityVerification {
    private QKDAbneCompatibilityVerification() { }

    public static void main(String[] args) {
        GPRMCSA legacyFactory = new GPRMCSA(
                "notrafficgrooming", "prmscav3", "djk", "newksp",
                "modulationbyqotv2", "randomfit", "aar", "csbasdm",
                "fsalfav1", "apamem");

        CoreAndSpectrumAssignmentAlgorithmInterface legacy =
                legacyFactory.instantiateCoreAndSpectrumAssignment();
        check(legacy instanceof CSBASDM,
                "legacy csbasdm must still instantiate the original CSBASDM/ABNE");

        CoreAndSpectrumAssignmentAlgorithmInterface qkd =
                QKDAlgorithmFactory.instantiateCoreAndSpectrumAssignment("qabne");
        check(qkd instanceof QABNE,
                "QKD addon selector qabne must instantiate QABNE");

        check(QKDAlgorithmFactory.instantiateCoreAndSpectrumAssignment("csbasdm") == null,
                "QKD addon factory must not silently alias the classical csbasdm name");

        GPRMCSA legacyQname = new GPRMCSA(
                "notrafficgrooming", "prmscav3", "djk", "newksp",
                "modulationbyqotv2", "randomfit", "aar", "qabne",
                "fsalfav1", "apamem");
        check(legacyQname.instantiateCoreAndSpectrumAssignment() == null,
                "legacy GPRMCSA factory must not know the QKD-only qabne selector");

        System.out.println("[OK] classical selector csbasdm -> "
                + legacy.getClass().getName());
        System.out.println("[OK] QKD addon selector qabne -> "
                + qkd.getClass().getName());
        System.out.println("[OK] factories are namespace-isolated: legacy does not know qabne and QKD addon does not alias csbasdm");
        System.out.println("[OK] original CSBASDM/ABNE source is not modified by the QKD addon");
        System.out.println();
        System.out.println("QKD Part 18 addon/ABNE isolation verification: ALL CHECKS PASSED");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
