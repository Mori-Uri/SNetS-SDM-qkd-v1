package simulationControl.parsers;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the SimulationServer component.
 */
public class SimulationServer {

    private boolean online = false;
    private List<SimulationRequest> simulationQueue = new ArrayList<>();

    /**
     * Checks whether online.
     * @return true if the condition is met; false otherwise.
     */
    public boolean isOnline() {
        return online;
    }

    /**
     * Sets the online.
     * @param online the online.
     */
    public void setOnline(boolean online) {
        this.online = online;
    }

    /**
     * Returns the simulation queue.
     * @return the simulation queue.
     */
    public List<SimulationRequest> getSimulationQueue() {
        return simulationQueue;
    }

    /**
     * Sets the simulation queue.
     * @param simulationQueue the simulationQueue.
     */
    public void setSimulationQueue(List<SimulationRequest> simulationQueue) {
        this.simulationQueue = simulationQueue;
    }
}
