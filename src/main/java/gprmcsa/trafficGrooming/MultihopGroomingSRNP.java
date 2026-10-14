package gprmcsa.trafficGrooming;

import gprmcsa.trafficGrooming.util.SRNP;
import network.Circuit;
import network.ControlPlane;
import network.Node;
import network.Pair;
import request.RequestForConnection;

import java.util.ArrayList;

/**
 * This class represents a Multihop Traffic Grooming algorithm whith the spectrum reservation scheme presented in "Dynamic Traf?c Grooming in Sliceable Bandwidth-Variable Transponder-Enabled Elastic Optical Networks".
 *
 * Extends this class to implement diferent trafic grooming policies.
 */
public abstract class MultihopGroomingSRNP extends MultihopGrooming {

    private SRNP srnp;

    /**
     * Returns the complement solution.
     * @param ms the ms.
     * @param rfc the rfc.
     * @param cp the cp.
     * @return the result of the operation.
     */
    protected Circuit complementSolution(MultihopGroomingSRNP.MultihopSolution ms, RequestForConnection rfc, ControlPlane cp) throws Exception {
        Node s = ms.pairComplement.getSource();
        Node d = ms.pairComplement.getDestination();
        Circuit newCircuit = cp.createNewCircuit(rfc, new Pair(s, d));
        if (!srnp.establishCircuit(newCircuit)) {
            return null;
        } else {
            newCircuit.removeRequest(rfc);
            rfc.setCircuit(new ArrayList<>());
            return newCircuit;
        }
    }

    /**
     * Returns the search circuits for grooming.
     * @param rfc the rfc.
     * @param cp the cp.
     * @return true if the condition is met; false otherwise.
     */
    public boolean searchCircuitsForGrooming(final RequestForConnection rfc, final ControlPlane cp) throws Exception {
        if(srnp==null){
            srnp = new SRNP(cp);
        }
        return super.searchCircuitsForGrooming(rfc,cp);
    }

    /**
     * Executes the finish connection operation.
     * @param rfc the rfc.
     * @param cp the cp.
     */
    public void finishConnection(RequestForConnection rfc, ControlPlane cp) throws Exception {
        for (Circuit circuit : rfc.getCircuits()) {
            if (circuit.getRequests().size() == 1) {
                cp.releaseCircuit(circuit);
                this.removeCircuitVirtualRouting(circuit);
            } else {
                srnp.retractCircuit(circuit,rfc);
            }
        }
    }

}



