package simulationControl;

import simulationControl.parsers.SimulationRequest;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Represents the SNetSAPI component.
 */
public class SNetSAPI {

    /**
     * Returns the run simulation.
     * @param sr the sr.
     * @param spl the spl.
     * @return the result of the operation.
     */
    public SimulationRequest runSimulation(SimulationRequest sr, SimulationManagement.SimulationProgressListener spl){
        SimulationManagement sm = new SimulationManagement(sr);
        sm.startSimulations(spl);
        return sr;
    }

    /**
     * Reads the simulation.
     * @param path the path.
     * @param name the name.
     * @return the result of the operation.
     */
    public SimulationRequest readSimulation(String path, String name) throws FileNotFoundException {
        SimulationFileManager sfm = new SimulationFileManager();
        return sfm.readSimulation(path,name);
    }

    /**
     * Writes the simulation.
     * @param sr the sr.
     * @param path the path.
     */
    public void writeSimulation(SimulationRequest sr,String path) throws IOException {
        SimulationFileManager sfm = new SimulationFileManager();
        sfm.writeSimulation(path,sr);
    }

}
