package gprmcsa.trafficGrooming;

import gprmcsa.trafficGrooming.util.SRNP;
import network.Circuit;
import network.ControlPlane;
import request.RequestForConnection;

/**
 * Represents the AuxiliaryGraphGrooming_SRNP component.
 */
public class AuxiliaryGraphGrooming_SRNP extends AuxiliaryGraphGrooming {
    private SRNP srnp;

    /**
     * Executes the init operation.
     * @param cp the cp.
     */
    @Override
    protected void init(ControlPlane cp) {
        srnp = new SRNP(cp);
        super.init(cp);
    }

    /**
     * Returns the establish circuit.
     * @param c the c.
     * @param cp the cp.
     * @return true if the condition is met; false otherwise.
     */
    @Override
    protected boolean establishCircuit(Circuit c, ControlPlane cp) {
        try {
            return srnp.establishCircuit(c);
        } catch (Exception e) {
            e.printStackTrace();
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Executes the finish connection operation.
     * @param rfc the rfc.
     * @param cp the cp.
     */
    @Override
    public void finishConnection(RequestForConnection rfc, ControlPlane cp) throws Exception {
        for (Circuit circuit : rfc.getCircuits()) {
            if (circuit.getRequests().size() == 1) {
                cp.releaseCircuit(circuit);
            } else {
                srnp.retractCircuit(circuit,rfc);
            }
        }
    }
}
