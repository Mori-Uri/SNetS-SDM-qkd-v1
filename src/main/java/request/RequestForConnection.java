package request;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import network.Circuit;
import network.Pair;
import network.RequestGenerator;
import qkd.key.QKDKeyDemand;
import qkd.traffic.RequestProtectionType;

/**
 * Connection request handled by the SNetS event engine.
 *
 * <p>Part 5 added the protection classification and Part 11 adds an optional
 * QKD key-demand object. Existing SNetS code remains compatible because
 * requests default to {@link RequestProtectionType#CLASSICAL} and therefore
 * carry no QKD key demand.</p>
 */
public class RequestForConnection implements Serializable {

    private static final long serialVersionUID = 1L;

    protected Pair pair;
    protected double timeOfRequestHours;
    protected double timeOfFinalizeHours;
    protected double requiredBitRate;
    protected RequestGenerator rg;
    protected List<Circuit> circuit;

    /** CLASSICAL for legacy requests, QKD_PROTECTED for the selected fraction. */
    protected RequestProtectionType protectionType;

    /**
     * Key-consumption requirement of a QKD-protected request.
     * Classical requests keep this field null.
     */
    protected QKDKeyDemand qkdKeyDemand;

    public RequestForConnection() {
        circuit = new ArrayList<Circuit>();
        protectionType = RequestProtectionType.CLASSICAL;
        qkdKeyDemand = null;
    }

    public Pair getPair() {
        return pair;
    }

    public void setPair(Pair pair) {
        this.pair = pair;
    }

    public double getTimeOfRequestHours() {
        return timeOfRequestHours;
    }

    public void setTimeOfRequestHours(double timeOfRequestHours) {
        this.timeOfRequestHours = timeOfRequestHours;
    }

    public double getTimeOfFinalizeHours() {
        return timeOfFinalizeHours;
    }

    public void setTimeOfFinalizeHours(double timeOfFinalizeHours) {
        this.timeOfFinalizeHours = timeOfFinalizeHours;
    }

    public double getRequiredBitRate() {
        return requiredBitRate;
    }

    public void setRequiredBitRate(double requiredBitRate) {
        this.requiredBitRate = requiredBitRate;
    }

    public List<Circuit> getCircuits() {
        return circuit;
    }

    public void setCircuit(List<Circuit> circuit) {
        this.circuit = circuit;
    }

    public RequestGenerator getRequestGenerator() {
        return rg;
    }

    public void setRequestGenerator(RequestGenerator rg) {
        this.rg = rg;
    }

    public RequestProtectionType getProtectionType() {
        return protectionType;
    }

    public void setProtectionType(RequestProtectionType protectionType) {
        this.protectionType = protectionType == null
                ? RequestProtectionType.CLASSICAL
                : protectionType;
        if (this.protectionType == RequestProtectionType.CLASSICAL) {
            this.qkdKeyDemand = null;
        }
    }

    /** Convenience method for later ControlPlane integration. */
    public boolean isQkdProtected() {
        return protectionType == RequestProtectionType.QKD_PROTECTED;
    }

    public QKDKeyDemand getQkdKeyDemand() {
        return qkdKeyDemand;
    }

    public void setQkdKeyDemand(QKDKeyDemand qkdKeyDemand) {
        if (qkdKeyDemand == null) {
            this.qkdKeyDemand = null;
            return;
        }
        if (!isQkdProtected()) {
            throw new IllegalStateException(
                    "QKD key demand can only be assigned to QKD-protected requests");
        }
        this.qkdKeyDemand = qkdKeyDemand;
    }

    public double getRequiredQkdKeyRateBps() {
        return qkdKeyDemand == null ? 0.0
                : qkdKeyDemand.getRequiredKeyRateBps();
    }

    public double getRequiredQkdKeyRateMbps() {
        return qkdKeyDemand == null ? 0.0
                : qkdKeyDemand.getRequiredKeyRateMbps();
    }
}
