package simulationControl.distributedProcessing;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Represents the ServerMInterface component.
 */
public interface ServerMInterface extends Remote {

    /**
     * Executes the register operation.
     * @param server the server.
     */
    public void register(ServerSInterface server) throws RemoteException;

    /**
     * Returns the simulation bundle request.
     * @param simReqJSON the simReqJSON.
     * @param cpci the cpci.
     * @return the result of the operation.
     */
    public String simulationBundleRequest(String simReqJSON, ClientProgressCallbackInterface cpci) throws Exception;
}
