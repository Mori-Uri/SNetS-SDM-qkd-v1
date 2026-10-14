package gprmcsa.trafficGrooming.util;

import network.Circuit;
import network.ControlPlane;
import network.Pair;
import request.RequestForConnection;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * This class implements the Spectrum Reservation by Node Pair presented in
 * "Dynamic Traffic Grooming in Sliceable Bandwidth-Variable Transponder-Enabled Elastic Optical Networks".
 */
public class SRNP {

    private ControlPlane cp;
    private double reservationTarget = 0;
    private HashMap<String,Double> reservesByNode = new HashMap<>();

    /**
     * Creates a new instance of SRNP.
     * @param cp the cp.
     */
    public SRNP(ControlPlane cp){
        this.cp = cp;

        Iterator<Pair> iterator = cp.getMesh().getPairList().iterator();
        while(iterator.hasNext()){
            reservesByNode.put(iterator.next().getName(),0.0);
        }

        Map<String, String> uv = cp.getMesh().getOthersConfig().getVariables();
        this.reservationTarget = Double.parseDouble((String)uv.get("reservationTarget"));
    }

    /**
     * Computes the residual capacity.
     */
    public void computeResidualCapacity(){
        Iterator<Pair> iterator = cp.getMesh().getPairList().iterator();
        while(iterator.hasNext()){
            reservesByNode.put(iterator.next().getName(),0.0);
        }

        Iterator<Circuit> iterator2 = cp.getConnections().iterator();
        while(iterator2.hasNext()){
            Circuit c = iterator2.next();
            reservesByNode.put(c.getPair().getName(),reservesByNode.get(c.getPair().getName())+c.getResidualCapacity());

        }
    }

    /**
     * Returns the establish circuit.
     * @param circuit the circuit.
     * @return true if the condition is met; false otherwise.
     */
    public boolean establishCircuit(Circuit circuit) throws Exception{
        computeResidualCapacity();
        RequestForConnection tempReq = new RequestForConnection();
        tempReq.setPair(circuit.getPair());
        String pair = circuit.getPair().getPairName();
        double newReserve = reservationTarget - reservesByNode.get(pair);

        if(newReserve>0) {
            //System.out.println("est reservando - " + reservationTarget + "  -  " + reservesByNode.get(pair));
            tempReq.setRequiredBitRate(newReserve);
            circuit.addRequest(tempReq);
        }

        if(cp.establishCircuit(circuit)){//try stabilish with reserve
            circuit.removeRequest(tempReq);
            //reservesByNode.put(pair,reservationTarget);
            return true;
        }

        circuit.removeRequest(tempReq); //try stabilish without reserve
        if(cp.establishCircuit(circuit)){
            return true;
        }

        return false; //fail to stabilish new circuit
    }

    /**
     * Returns the reservation target.
     * @return the reservation target.
     */
    public double getReservationTarget() {
        return reservationTarget;
    }

    /**
     * Returns the reserves by node.
     * @return the reserves by node.
     */
    public HashMap<String, Double> getReservesByNode() {
        return reservesByNode;
    }

    /**
     * Executes the retract circuit operation.
     * @param circuit the circuit.
     * @param rfc the rfc.
     */
    public void retractCircuit(Circuit circuit, RequestForConnection rfc) throws Exception {
        circuit.removeRequest(rfc);
        this.computeResidualCapacity();

        Double actualReserve = this.getReservesByNode().get(circuit.getPair().getName());
        Double retract = actualReserve - this.getReservationTarget();
        Double retractInThisCirc;

        if (circuit.getResidualCapacity() > retract) {
            retractInThisCirc = retract;
        } else {
            retractInThisCirc = circuit.getResidualCapacity();
        }

        if (retractInThisCirc > 0) {//retract the circuit

            int numFinalSlots = circuit.getModulation().requiredSlots(circuit.getBandwidth() - retractInThisCirc);
            int numCurrentSlots = circuit.getSpectrumAssigned()[1] - circuit.getSpectrumAssigned()[0] + 1;
            int release = numCurrentSlots - numFinalSlots;
            int[] releaseBand = new int[2];

            if (release != 0) {
                releaseBand[1] = circuit.getSpectrumAssigned()[1];
                releaseBand[0] = releaseBand[1] - release + 1;
                cp.retractCircuit(circuit, 0, release);
            }
        }
    }

}
