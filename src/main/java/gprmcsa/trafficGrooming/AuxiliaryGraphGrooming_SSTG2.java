package gprmcsa.trafficGrooming;


import gprmcsa.trafficGrooming.util.Grooming;
import network.Circuit;
import network.ControlPlane;
import request.RequestForConnection;

import java.util.Map;

/**
 * Represents the AuxiliaryGraphGrooming_SSTG2 component.
 */
public class AuxiliaryGraphGrooming_SSTG2 extends AuxiliaryGraphGrooming{
    private int sigmaExpansiveness=0;

    /**
     * Executes the init operation.
     * @param cp the cp.
     */
    @Override
    protected void init(ControlPlane cp) {
        super.init(cp);
        Map<String, String> uv = cp.getMesh().getOthersConfig().getVariables();
        this.sigmaExpansiveness = Integer.parseInt((String)uv.get("sigmaExpansiveness"));
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
                int numFinalSlots = circuit.getModulation().requiredSlots(circuit.getRequiredBitRate() - rfc.getRequiredBitRate());
                retractCircuit(circuit,numFinalSlots,cp);
                circuit.removeRequest(rfc);
            }
        }
    }

    /**
     * Returns the decide to expand.
     * @param numMoreSlots the numMoreSlots.
     * @param numLowerFreeSlots the numLowerFreeSlots.
     * @param numUpperFreeSlots the numUpperFreeSlots.
     * @return the result of the operation.
     */
    @Override
    protected int[] decideToExpand(int numMoreSlots, int numLowerFreeSlots, int numUpperFreeSlots) {
        int eu=0, ed=0;

        if(numLowerFreeSlots>numUpperFreeSlots){
            int aux = Math.min(numMoreSlots,numLowerFreeSlots-numUpperFreeSlots);
            ed+=aux;
            numMoreSlots-=aux;
        }else{
            int aux = Math.min(numMoreSlots,numUpperFreeSlots-numLowerFreeSlots);
            eu+=aux;
            numMoreSlots-=aux;
        }

        if(numMoreSlots>0){
            int aux = numMoreSlots/2;
            ed+=aux;
            eu+=(numMoreSlots-aux);
        }

        int res[] = new int[2];
        res[0] = ed;
        res[1] = eu;
        return res;
    }

    /**
     * Executes the retract circuit operation.
     * @param circuit the circuit.
     * @param numFinalSlots the numFinalSlots.
     * @param cp the cp.
     */
    protected void retractCircuit(Circuit circuit, int numFinalSlots, ControlPlane cp){
        int numCurrentSlots = circuit.getSpectrumAssigned()[1] - circuit.getSpectrumAssigned()[0] + 1;
        int release = numCurrentSlots - numFinalSlots;

        int[] freeSlots = Grooming.circuitExpansiveness(circuit);
        int fsd = freeSlots[0];
        int fsu = freeSlots[1];
        int rd = 0, ru = 0;

        int ofsx = (sigmaExpansiveness - numFinalSlots)/2;

        if(fsd>=ofsx){
            ru = release;
            rd = 0;
        }else{
            if(fsd<fsu){
                int aux = Math.min(ofsx-fsd,release);
                aux = Math.min(aux,fsu-fsd);
                rd+=aux;
                release-=aux;
            }else{
                int aux = Math.min(ofsx-fsu,release);
                aux = Math.min(aux,fsd-fsu);
                ru+=aux;
                release-=aux;
            }

            if(release>0){//there are still slots to release
                int aux = ofsx - (fsd + rd);// amount missing to complete the target below
                aux = Math.min(aux,release-release/2);
                rd+=aux;
                release-=aux;
                ru+=release;
            }
        }

        try {
            cp.retractCircuit(circuit, rd, ru);
        } catch (Exception e) {
            e.printStackTrace();
            throw new UnsupportedOperationException();
        }
    }
}
