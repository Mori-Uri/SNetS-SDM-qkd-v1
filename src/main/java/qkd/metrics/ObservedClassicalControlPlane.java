package qkd.metrics;

import gprmcsa.coreSpectrumAssignment.CoreAndSpectrumAssignmentAlgorithmInterface;
import gprmcsa.integrated.IntegratedRMLSAAlgorithmInterface;
import gprmcsa.modulation.ModulationSelectionAlgorithmInterface;
import gprmcsa.powerAssignment.PowerAssignmentAlgorithmInterface;
import gprmcsa.reallocation.ReallocationAlgorithmInterface;
import gprmcsa.routing.KRoutingAlgorithmInterface;
import gprmcsa.routing.RoutingAlgorithmInterface;
import gprmcsa.spectrumAssignment.SpectrumAssignmentAlgorithmInterface;
import gprmcsa.trafficGrooming.TrafficGroomingAlgorithmInterface;
import network.ControlPlane;
import network.Mesh;
import request.RequestForConnection;

/**
 * Part 25: the unchanged classical control plane plus the observation-only
 * {@link BlockingDetailRecorder}. Every decision is taken by
 * {@link ControlPlane#handleRequisition}; this class only looks at the result,
 * so classical simulations get the same BlockingDetails.csv as QKD ones.
 *
 * <p>Refer\u00eancias: [SNETS17].</p>
 */
@SuppressWarnings("serial")
public class ObservedClassicalControlPlane extends ControlPlane {

    private final BlockingDetailRecorder blockingDetails;

    public ObservedClassicalControlPlane(Mesh mesh, int rmlsaType,
            TrafficGroomingAlgorithmInterface trafficGroomingAlgorithm,
            IntegratedRMLSAAlgorithmInterface integratedRMLSAAlgorithm,
            RoutingAlgorithmInterface routingAlgorithm,
            KRoutingAlgorithmInterface kRoutingAlgorithm,
            SpectrumAssignmentAlgorithmInterface spectrumAssignmentAlgorithm,
            ModulationSelectionAlgorithmInterface modulationSelection,
            CoreAndSpectrumAssignmentAlgorithmInterface coreAndSpectrumAssignment,
            ReallocationAlgorithmInterface reallocation,
            PowerAssignmentAlgorithmInterface powerAssignment,
            double warmupArrivals) {
        super(mesh, rmlsaType, trafficGroomingAlgorithm, integratedRMLSAAlgorithm, routingAlgorithm,
                kRoutingAlgorithm, spectrumAssignmentAlgorithm, modulationSelection, coreAndSpectrumAssignment,
                reallocation, powerAssignment);
        this.blockingDetails = new BlockingDetailRecorder(warmupArrivals, this);
    }

    @Override
    public boolean handleRequisition(RequestForConnection request) throws Exception {
        if (request != null) blockingDetails.arrival();
        boolean ok = super.handleRequisition(request);
        if (request != null) blockingDetails.observe(this, request, ok, null);
        return ok;
    }

    public BlockingDetailRecorder.Snapshot snapshotBlockingDetails() {
        return blockingDetails.snapshot();
    }
}
