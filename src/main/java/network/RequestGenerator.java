package network;

import java.io.Serializable;

import request.RequestForConnection;
import simulator.Event;
import simulator.EventMachine;
import simulator.eventListeners.ArriveRequestForConexionListener;
import util.RandGenerator;

/**
 * Original SNetS request generator.
 *
 * <p>This class is intentionally kept QKD-agnostic. When QKD is enabled the
 * simulator now uses {@code qkd.traffic.QKDRequestGenerator}, which extends
 * this class and adds only the request-protection classification.</p>
 */
public class RequestGenerator implements Serializable {

    private static final long serialVersionUID = 1L;

    private Pair pair;
    private double bandwidth;
    private double holdRate;
    private double arrivedRate;
    private double incLoad;
    private double atualTimeHours;
    private RandGenerator randGenerator;

    public RequestGenerator(Pair pair, double bandwidth, double holdRate,
            double arrivedRate, double incLoad, RandGenerator randGenerator) {
        this.pair = pair;
        this.bandwidth = bandwidth;
        this.holdRate = holdRate;
        this.arrivedRate = arrivedRate;
        this.incLoad = incLoad;
        this.atualTimeHours = 0.0;
        this.randGenerator = randGenerator;
    }

    public void scheduleNextRequest(EventMachine eventMachine,
            ArriveRequestForConexionListener listener) {

        RequestForConnection request = new RequestForConnection();

        double arriveTime = randGenerator.negexp(arrivedRate);
        atualTimeHours += arriveTime;
        request.setTimeOfRequestHours(atualTimeHours);

        double holdTime = randGenerator.negexp(getHoldRate());
        double finalizeTime = request.getTimeOfRequestHours() + holdTime;
        request.setTimeOfFinalizeHours(finalizeTime);

        request.setPair(pair);
        request.setRequiredBitRate(bandwidth);
        request.setRequestGenerator(this);

        Event event = new Event(request, listener, atualTimeHours);
        eventMachine.insert(event);
    }

    public double getArrivedRate() {
        return arrivedRate;
    }

    public double getHoldRate() {
        return holdRate;
    }

    public void setArrivedRate(double arrivedRate) {
        this.arrivedRate = arrivedRate;
    }

    public void setHoldRate(double holdRate) {
        this.holdRate = holdRate;
    }

    public double getIncLoad() {
        return incLoad;
    }

    public void setIncLoad(double incLoad) {
        this.incLoad = incLoad;
    }

    public Pair getPair() {
        return pair;
    }

    public void setPair(Pair pair) {
        this.pair = pair;
    }

    public double getBandwidth() {
        return bandwidth;
    }

    public void setBandwidth(double bandwidth) {
        this.bandwidth = bandwidth;
    }

    public void incArrivedRate(int mult) {
        arrivedRate = arrivedRate + mult * incLoad;
    }
}
