package simulationControl.distributedProcessing;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Represents the ClientProgressCallbackInterface component.
 */
public interface ClientProgressCallbackInterface extends Remote {

    /**
     * Updates the progress.
     * @param progress the progress.
     */
    public void updateProgress(double progress) throws RemoteException;

}
