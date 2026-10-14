package simulator;

import measurement.Measurements;
import network.ControlPlane;
import network.Mesh;
import network.TranslucentControlPlane;
import qkd.algorithms.QKDAlgorithmFactory;
import qkd.control.QKDControlPlane;
import qkd.network.QKDMesh;
import simulationControl.Util;
import simulationControl.parsers.SimulationConfig;

import java.io.Serializable;

import gprmcsa.GPRMCSA;
import gprmcsa.coreSpectrumAssignment.CoreAndSpectrumAssignmentAlgorithmInterface;
import gprmcsa.integrated.IntegratedRMLSAAlgorithmInterface;
import gprmcsa.modulation.ModulationSelectionAlgorithmInterface;
import gprmcsa.powerAssignment.PowerAssignmentAlgorithmInterface;
import gprmcsa.reallocation.ReallocationAlgorithmInterface;
import gprmcsa.regeneratorAssignment.RegeneratorAssignmentAlgorithmInterface;
import gprmcsa.routing.KRoutingAlgorithmInterface;
import gprmcsa.routing.RoutingAlgorithmInterface;
import gprmcsa.spectrumAssignment.SpectrumAssignmentAlgorithmInterface;
import gprmcsa.trafficGrooming.TrafficGroomingAlgorithmInterface;

/**
 * Represents one SNetS simulation replication.
 *
 * <p>The legacy SNetS algorithm factory remains responsible for classical
 * simulations. When the runtime mesh is a {@link QKDMesh}, only algorithm
 * names that actually require QKD adaptation are resolved through the QKD
 * addon factory. This keeps the original GPRMCSA/ABNE implementation intact.
 * Algorithms that do not need QKD-specific behavior continue to be reused
 * unchanged by the addon.</p>
 */
@SuppressWarnings("serial")
public class Simulation implements Serializable {

    private Mesh mesh;
    private ControlPlane controlPlane;
    private Measurements measurements;
    private Util util;

    private int loadPoint;
    private int replication;

    public Simulation(SimulationConfig sc, Mesh mesh, int loadPoint,
            int replication, Util util) {
        this.loadPoint = loadPoint;
        this.replication = replication;
        this.measurements = new Measurements(sc.getRequests(), loadPoint,
                replication, mesh, sc.getActiveMetrics());
        this.mesh = mesh;
        this.util = util;

        GPRMCSA legacyFactory = new GPRMCSA(sc.getGrooming(),
                sc.getIntegratedRmlsa(), sc.getRouting(), sc.getkRouting(),
                sc.getModulationSelection(), sc.getSpectrumAssignment(),
                sc.getRegeneratorAssignment(),
                sc.getCoreAndSpectrumAssignment(), sc.getReallocation(),
                sc.getPowerAssignment());

        final boolean qkdEnabled = mesh instanceof QKDMesh;

        TrafficGroomingAlgorithmInterface grooming =
                legacyFactory.instantiateGrooming();
        IntegratedRMLSAAlgorithmInterface integrated =
                qkdEnabled && QKDAlgorithmFactory.isQkdIntegratedAlgorithm(sc.getIntegratedRmlsa())
                        ? QKDAlgorithmFactory.instantiateIntegrated(sc.getIntegratedRmlsa())
                        : legacyFactory.instantiateIntegratedRSA();
        RoutingAlgorithmInterface routing = legacyFactory.instantiateRouting();
        KRoutingAlgorithmInterface kRouting = legacyFactory.instantiateKRouting();
        SpectrumAssignmentAlgorithmInterface spectrum =
                legacyFactory.instantiateSpectrumAssignment();
        ModulationSelectionAlgorithmInterface modulation =
                legacyFactory.instantiateModulationSelection();
        ReallocationAlgorithmInterface reallocation =
                legacyFactory.instantiateReallocation();
        PowerAssignmentAlgorithmInterface power =
                legacyFactory.instantiatePowerAssignment();
        RegeneratorAssignmentAlgorithmInterface regenerator =
                legacyFactory.instantiateRegeneratorAssignment();

        /*
         * Critical addon boundary: classical scenarios resolve CSA directly
         * through the untouched GPRMCSA factory. QKD scenarios resolve their
         * explicitly named QKD CSA through QKDAlgorithmFactory instead.
         */
        CoreAndSpectrumAssignmentAlgorithmInterface coreAndSpectrum;
        if (qkdEnabled) {
            coreAndSpectrum = QKDAlgorithmFactory
                    .instantiateCoreAndSpectrumAssignment(
                            sc.getCoreAndSpectrumAssignment());
        } else {
            coreAndSpectrum = legacyFactory
                    .instantiateCoreAndSpectrumAssignment();
        }

        checkNotNull(grooming, "grooming: " + sc.getGrooming());
        checkNotNull(integrated, "integrated: " + sc.getIntegratedRmlsa());
        checkNotNull(routing, "routing: " + sc.getRouting());
        checkNotNull(kRouting, "kRouting: " + sc.getkRouting());
        checkNotNull(spectrum,
                "spectrumAssignment: " + sc.getSpectrumAssignment());
        checkNotNull(coreAndSpectrum,
                (qkdEnabled ? "QKD addon " : "legacy ")
                + "coreAndSpectrumAssignment: "
                + sc.getCoreAndSpectrumAssignment());
        checkNotNull(modulation,
                "modulationSelection: " + sc.getModulationSelection());
        checkNotNull(power, "powerAssignment: " + sc.getPowerAssignment());
        checkNotNull(reallocation,
                "reallocation: " + sc.getReallocation());
        checkNotNull(regenerator,
                "regeneratorAssignment: " + sc.getRegeneratorAssignment());

        if (sc.getNetworkType() == GPRMCSA.TRANSPARENT) {
            if (qkdEnabled) {
                controlPlane = new QKDControlPlane(
                        (QKDMesh) mesh,
                        sc.getRmlsaType(),
                        grooming,
                        integrated,
                        routing,
                        kRouting,
                        spectrum,
                        modulation,
                        coreAndSpectrum,
                        reallocation,
                        power);
            } else {
                /*
                 * Legacy transparent path: same original SNetS components. The
                 * Part 25 subclass only observes each decision to write
                 * BlockingDetails.csv (same transient rule: 10 % of the requests).
                 */
                controlPlane = new qkd.metrics.ObservedClassicalControlPlane(mesh, sc.getRmlsaType(),
                        grooming, integrated, routing, kRouting, spectrum,
                        modulation, coreAndSpectrum, reallocation, power,
                        0.1 * sc.getRequests());
            }
        } else if (sc.getNetworkType() == GPRMCSA.TRANSLUCENT) {
            if (qkdEnabled) {
                throw new IllegalArgumentException(
                        "QKD addon currently supports transparent networks only");
            }
            controlPlane = new TranslucentControlPlane(mesh,
                    sc.getRmlsaType(), grooming, integrated, routing, kRouting,
                    spectrum, regenerator, modulation, coreAndSpectrum,
                    reallocation, power);
        }
    }

    private static void checkNotNull(Object obj, String name) {
        if (obj == null) {
            throw new IllegalArgumentException(
                    "Class/algorithm not found -> " + name);
        }
    }

    public int getLoadPoint() {
        return loadPoint;
    }

    public int getReplication() {
        return replication;
    }

    public Mesh getMesh() {
        return mesh;
    }

    public void setMesh(Mesh mesh) {
        this.mesh = mesh;
    }

    public ControlPlane getControlPlane() {
        return controlPlane;
    }

    public Measurements getMeasurements() {
        return measurements;
    }

    public Util getUtil() {
        return this.util;
    }
}
