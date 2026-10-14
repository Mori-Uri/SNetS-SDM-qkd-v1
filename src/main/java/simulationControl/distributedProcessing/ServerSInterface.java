package simulationControl.distributedProcessing;

import measurement.Measurements;
import simulator.Simulation;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Represents the ServerSInterface component.
 */
public interface ServerSInterface extends Remote {

    /**
     * Checks whether alive.
     * @return true if the condition is met; false otherwise.
     */
    public boolean isAlive() throws RemoteException;
    /**
     * Returns the name.
     * @return the name.
     */
    public String getName() throws RemoteException;
    /**
     * Returns the simulate.
     * @param simulation the simulation.
     * @return the result of the operation.
     */
    public Measurements simulate(Simulation simulation) throws Exception;

}
