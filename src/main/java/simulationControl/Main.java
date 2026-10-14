package simulationControl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import simulationControl.distributedProcessing.Client;
import simulationControl.distributedProcessing.ServerM;
import simulationControl.distributedProcessing.ServerS;
import simulationControl.parsers.SimulationConfig;
import simulationControl.parsers.SimulationRequest;
import simulationControl.parsers.SimulationServer;
import util.tools.ConfigDirectorySelectorGUI;
import util.tools.NetworkConfigValidator;

/**
 * This class has the main method that will instantiate the parsers to read the
 * configuration files and start the simulation
 *
 * @author Iallen
 */
public class Main {

    /**
     * Main method
     *
     * @param args String[] - arg[0] - Path of configuration files
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {

        // --------------------------------------------------
        // Check if "gui" is present in the arguments
        // --------------------------------------------------
        boolean useGUI = Arrays.stream(args).anyMatch(arg -> arg.equalsIgnoreCase("gui"));

        String configDirectoryFromGUI = null;

        if (useGUI) { // Loads the directory through the Graphical User Interface (GUI)
            ConfigDirectorySelectorGUI dialog = new ConfigDirectorySelectorGUI(null);

            dialog.setVisible(true); // Block here until closed

            configDirectoryFromGUI = dialog.getConfigDirectory();

            if (configDirectoryFromGUI == null) {
                System.out.println("No directory selected. Exiting.");
                System.exit(0);
            }

            System.out.println("Directory selected via GUI:");
            System.out.println(configDirectoryFromGUI);
        }

        // --------------------------------------------------
        // Simulation loading flow
        // --------------------------------------------------
        if (args.length == 0) {
            System.out.println("No option selected");
            System.exit(0);
        } else {
            switch (args[0]) {
                case "-fs": // Firebase Simulation Server
                    simulationServer();
                    break;

                case "-lm": // LAN Simulation Server Master
                    ServerM.runServerM();
                    break;

                case "-ls": // LAN Simulation Server Slave
                    ServerS.runServerS(args[1]);
                    break;

                case "-ls2": // LAN Simulation Server Slave
                    ServerS.runServerS(args[1], args[2]);
                    break;

                case "-lc": // LAN Client
                    Client.runClient(args[1], args[2]);
                    break;

                case "-lc2": // LAN Client
                    Client.runClient(args[1], args[2], true);
                    break;

                default: // Local Simulation
                    if (useGUI) {
                        localSimulation(configDirectoryFromGUI); // Directory selected via GUI
                    } else {
                        localSimulation(args[0]);
                    }
            }
        }
    }

    /**
     * Simulator runs in server mode
     *
     * @throws IOException
     */
    private static void simulationServer() throws IOException {
        initFirebase();

        DatabaseReference simSerRef = FirebaseDatabase.getInstance().getReference("simulationServers").push();
        simSerRef.setValueAsync(new SimulationServer(), 1);
        System.out.println("SNetS Simulation Server Running");
        System.out.println("simulation server key: " + simSerRef.getKey());

        FirebaseDatabase.getInstance().getReference("simulationServers/" + simSerRef.getKey() + "/online")
                .addValueEventListener(new ValueEventListener() {
                    /**
                     * Executes the on data change operation.
                     * @param dataSnapshot the dataSnapshot.
                     */
                    @Override
                    public void onDataChange(DataSnapshot dataSnapshot) {
                        Object value = dataSnapshot.getValue();
                        if (!(value instanceof Boolean) || !((Boolean) value)) {
                            dataSnapshot.getRef().setValueAsync(true);
                        }
                    }

                    /**
                     * Executes the on cancelled operation.
                     * @param databaseError the databaseError.
                     */
                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        // do nothing
                    }
                }); // signalizes that server is alive

        FirebaseDatabase.getInstance().getReference("simulationServers/" + simSerRef.getKey() + "/simulationQueue")
                .addChildEventListener(new ChildEventListener() {
                    /**
                     * Executes the on child added operation.
                     * @param dataSnapshot the dataSnapshot.
                     * @param s the s.
                     */
                    @Override
                    public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                        Gson gson = new GsonBuilder().create();
                        String srjson = dataSnapshot.getValue(false).toString();
                        SimulationRequest sr = gson.fromJson(srjson, SimulationRequest.class);
                        DatabaseReference newRef = FirebaseDatabase.getInstance().getReference("simulations").push();
                        newRef.setValueAsync(sr);
                        dataSnapshot.getRef().removeValueAsync();

                        if ("new".equals(sr.getStatus())) {
                            try {
                                newRef.child("status").setValueAsync("started");
                                newRef.child("progress").setValueAsync(0.0);

                                SimulationManagement sm = new SimulationManagement(sr);
                                sm.startSimulations(new SimulationManagement.SimulationProgressListener() {
                                    /**
                                     * Executes the on simulation progress update operation.
                                     * @param progress the progress.
                                     */
                                    @Override
                                    public void onSimulationProgressUpdate(double progress) {
                                        newRef.child("progress").setValueAsync(progress);
                                    }

                                    /**
                                     * Executes the on simulation finished operation.
                                     */
                                    @Override
                                    public void onSimulationFinished() {
                                        // do nothing
                                    }
                                });

                                sr.setProgress(1.0);
                                sr.setStatus("finished");
                                newRef.setValueAsync(sr);

                            } catch (Exception e) {
                                e.printStackTrace();
                                newRef.child("status").setValueAsync("failed");
                            }
                        }
                    }

                    /**
                     * Executes the on child changed operation.
                     * @param dataSnapshot the dataSnapshot.
                     * @param s the s.
                     */
                    @Override
                    public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                        // do nothing
                    }

                    /**
                     * Executes the on child removed operation.
                     * @param dataSnapshot the dataSnapshot.
                     */
                    @Override
                    public void onChildRemoved(DataSnapshot dataSnapshot) {
                        // do nothing
                    }

                    /**
                     * Executes the on child moved operation.
                     * @param dataSnapshot the dataSnapshot.
                     * @param s the s.
                     */
                    @Override
                    public void onChildMoved(DataSnapshot dataSnapshot, String s) {
                        // do nothing
                    }

                    /**
                     * Executes the on cancelled operation.
                     * @param databaseError the databaseError.
                     */
                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        // do nothing yet
                    }
                });

        while (true) { // Keep the server powered on
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Initialize Firebase
     *
     * @throws IOException
     */
    private static void initFirebase() throws IOException {
        try (FileInputStream serviceAccount = new FileInputStream("private-key-firebase.json")) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .setDatabaseUrl("https://snets-2905e.firebaseio.com")
                    .build();
            FirebaseApp.initializeApp(options);
        }
    }

    /**
     * Simulator runs in local mode
     *
     * @param path String Paths of the simulations configuration files
     * @return SimulationManagement
     * @throws Exception
     */
    public static SimulationManagement localSimulation(String path) throws Exception {

        File f = new File(path);
        String name = f.getName();
        path = f.getAbsoluteFile().getParentFile().getPath();

        System.out.println("Path: " + path);
        System.out.println("Simulation: " + name);
        System.out.println("Reading files...");

        SimulationFileManager sfm = new SimulationFileManager();
        SimulationRequest simulationRequest = sfm.readSimulation(path, name);
        SimulationConfig sc = simulationRequest.getSimulationConfig();

        NetworkConfigValidator.ValidationResult result = NetworkConfigValidator.validate(simulationRequest.getNetworkConfig());
        System.out.println(result.toPrettyString());
        if (!result.isValid()) {
            throw new IllegalStateException(result.toPrettyString());
        }

        System.out.println("Threads running: " + sc.getThreads());

        // Now start the simulations
        System.out.println("Starting simulations");
        SimulationManagement sm = new SimulationManagement(simulationRequest);

        System.out.println("Running...");
        long start = System.nanoTime();

        sm.startSimulations(new SimulationManagement.SimulationProgressListener() {
            /**
             * Executes the on simulation progress update operation.
             * @param progress the progress.
             */
            @Override
            public void onSimulationProgressUpdate(double progress) {
                System.out.println("progress: " + (progress * 100) + "%");
            }

            /**
             * Executes the on simulation finished operation.
             */
            @Override
            public void onSimulationFinished() {

            }
        });
        
        long end = System.nanoTime();
        
        System.out.println("Saving results");
        sfm.writeSimulation(path, simulationRequest);
        System.out.println("Finish!");
        
        long time = end - start;
		System.out.println("Total simulation time (s): " + (time / 1000000000.0));

        return sm;
    }
}
