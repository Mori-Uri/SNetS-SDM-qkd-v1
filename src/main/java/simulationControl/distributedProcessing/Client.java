package simulationControl.distributedProcessing;

import java.io.File;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import simulationControl.SimulationFileManager;
import simulationControl.parsers.SimulationRequest;

/**
 * Represents the Client component.
 */
public class Client {

    /**
     * Executes the run client operation.
     * @param serverMLocation the serverMLocation.
     * @param path the path.
     * @param manyPaths the manyPaths.
     */
    public static void runClient(String serverMLocation, String path, boolean manyPaths){
        //Reports that there are several simulation folders
    	if(manyPaths)
    	{
    		File baseDir = new File(path);

            if (!baseDir.exists() || !baseDir.isDirectory()) {
                System.out.println("Invalid folder: " + baseDir.getAbsolutePath());
                return;
            }

            File[] subFolders = baseDir.listFiles(File::isDirectory);

            if (subFolders == null || subFolders.length == 0) {
                System.out.println("No subfolders found.");
                return;
            }

            for (File folder : subFolders) {
                String folderTemp = folder.getAbsolutePath();

                runClient(serverMLocation, folderTemp);
            }
    	}
    	else
    	{
    		runClient(serverMLocation, path);
    	}

    }

    /**
     * Executes the run client operation.
     * @param serverMLocation the serverMLocation.
     * @param path the path.
     */
    public static void runClient(String serverMLocation, String path){
    	try {
            ServerMInterface server = (ServerMInterface) Naming.lookup("//"+serverMLocation+"/ServerM");

            File f = new File(path);
            String name = f.getName();
            path = f.getAbsoluteFile().getParentFile().getPath();

            System.out.println("Path: " + path);
            System.out.println("Simulation: " + name);
            System.out.println("Reading files");

            SimulationFileManager sfm = new SimulationFileManager();
            SimulationRequest sr = sfm.readSimulation(path, name);

            Gson gson = new GsonBuilder().create();
            String simReqJSON = gson.toJson(sr);
            simReqJSON = server.simulationBundleRequest(simReqJSON, new ClientProgressCallback());
            sr = gson.fromJson(simReqJSON,SimulationRequest.class);

            System.out.println("Saving results.");
            sfm.writeSimulation(path, sr);

            System.out.println("Simulation ends.");

        }catch (RemoteException ex){
            ex.printStackTrace();
        }catch (MalformedURLException e) {
            e.printStackTrace();
        }catch (NotBoundException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class ClientProgressCallback extends UnicastRemoteObject implements ClientProgressCallbackInterface {

    	private double progress = 0;

        protected ClientProgressCallback() throws RemoteException {

        }

        /**
         * Updates the progress.
         * @param progress the progress.
         */
        @Override
        public void updateProgress(double progress) throws RemoteException {
        	if (this.progress != progress) {
        		this.progress = progress;
        		System.out.println("progress: "+ (progress * 100) + "%");
        	}
        }
    }

}
