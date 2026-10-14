package simulationControl.parsers;

import java.util.ArrayList;
import java.util.List;

/**
 * Original SNetS traffic-file model.
 *
 * <p>No QKD field is added here. The existing {@code traffic} JSON can remain
 * exactly as it was before QKD integration. QKD-specific request generation is
 * selected by the simulator when the QKD scenario is enabled.</p>
 */
public class TrafficConfig {

    private List<RequestGeneratorConfig> requestGenerators;

    public TrafficConfig() {
        requestGenerators = new ArrayList<RequestGeneratorConfig>();
    }

    public List<RequestGeneratorConfig> getRequestGenerators() {
        return requestGenerators;
    }

    public void setRequestGenerators(List<RequestGeneratorConfig> requestGenerators) {
        this.requestGenerators = requestGenerators;
    }

    public static class RequestGeneratorConfig {
        private String source;
        private String destination;
        private double bitRate;
        private double arrivalRate;
        private double holdRate;
        private double arrivalRateIncrease;
        /**
         * Part 23 (QKD addon, optional): fraction of the requests of this
         * generator that are QKD-protected. Absent = the global
         * simulation.qkdProtectedRequestProbability. Ignored without QKD.
         */
        private Double qkdProtectedProbability;
        /**
         * Part 23 (QKD addon, optional): key-material demand of the protected
         * requests of this generator, in Mbit/s. Absent = drawn from
         * simulation.qkdKeyDemand.
         */
        private Double keyRateMbps;

        public Double getQkdProtectedProbability() { return qkdProtectedProbability; }

        public void setQkdProtectedProbability(Double v) { this.qkdProtectedProbability = v; }

        public Double getKeyRateMbps() { return keyRateMbps; }

        public void setKeyRateMbps(Double v) { this.keyRateMbps = v; }

        public RequestGeneratorConfig(String source, String destination,
                double bitRate, double arrivalRate, double holdRate,
                double arrivalRateIncrease) {
            this.source = source;
            this.destination = destination;
            this.bitRate = bitRate;
            this.arrivalRate = arrivalRate;
            this.holdRate = holdRate;
            this.arrivalRateIncrease = arrivalRateIncrease;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getDestination() {
            return destination;
        }

        public void setDestination(String destination) {
            this.destination = destination;
        }

        public double getBitRate() {
            return bitRate;
        }

        public void setBitRate(double bitRate) {
            this.bitRate = bitRate;
        }

        public double getArrivalRate() {
            return arrivalRate;
        }

        public void setArrivalRate(double arrivalRate) {
            this.arrivalRate = arrivalRate;
        }

        public double getHoldRate() {
            return holdRate;
        }

        public void setHoldRate(double holdRate) {
            this.holdRate = holdRate;
        }

        public double getArrivalRateIncrease() {
            return arrivalRateIncrease;
        }

        public void setArrivalRateIncrease(double arrivalRateIncrease) {
            this.arrivalRateIncrease = arrivalRateIncrease;
        }
    }
}
