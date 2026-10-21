package simulationControl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import simulationControl.parsers.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.regex.Pattern;

/**
 * Represents the SimulationFileManager component.
 */
public class SimulationFileManager {

    /**
     * Reads the simulation.
     * @param path the path.
     * @param name the name.
     * @return the result of the operation.
     */
    private static final String JSON_EXTENSION = ".json";

    private static final String[] CONFIG_FILE_NAMES = {
            "network",
            "simulation",
            "physicalLayer",
            "others",
            "traffic",
            qkd.physical.QKDPhysicalLayerConfig.FILE_NAME
    };

    /**
     * Reads the simulation. Configuration files can use the historical name
     * without an extension (for example {@code network}) or the JSON name
     * (for example {@code network.json}). When both forms exist, the
     * {@code .json} file has priority.
     *
     * @param path the path.
     * @param name the name.
     * @return the result of the operation.
     */
    public SimulationRequest readSimulation(String path, String name) throws FileNotFoundException {
        SimulationRequest sr = new SimulationRequest();
        sr.setName(name);
        sr.setPath(path);

        String separator = System.getProperty("file.separator");
        String filesPath = path + separator + name;

        File folder = new File(filesPath);
        File[] listOfFiles = folder.listFiles();
        if (listOfFiles == null) {
            throw new FileNotFoundException("Simulation directory not found or cannot be read: " + filesPath);
        }

        Gson gson = new GsonBuilder().create();

        File networkFile = resolveConfigFileForRead(folder, "network");
        if (networkFile != null) {
            sr.setNetworkConfig(gson.fromJson(readFile(networkFile), NetworkConfig.class));
        }

        File simulationFile = resolveConfigFileForRead(folder, "simulation");
        if (simulationFile != null) {
            sr.setSimulationConfig(gson.fromJson(readFile(simulationFile), SimulationConfig.class));
        }

        File physicalLayerFile = resolveConfigFileForRead(folder, "physicalLayer");
        if (physicalLayerFile != null) {
            sr.setPhysicalLayerConfig(gson.fromJson(readFile(physicalLayerFile), PhysicalLayerConfig.class));
        }

        File othersFile = resolveConfigFileForRead(folder, "others");
        if (othersFile != null) {
            sr.setOthersConfig(gson.fromJson(readFile(othersFile), OthersConfig.class));
        }

        File trafficFile = resolveConfigFileForRead(folder, "traffic");
        if (trafficFile != null) {
            sr.setTrafficConfig(gson.fromJson(readFile(trafficFile), TrafficConfig.class));
        }

        qkd.physical.QKDPhysicalLayerConfig qkdPhysicalLayer = null;
        File qkdPhysicalLayerFile = resolveConfigFileForRead(
                folder, qkd.physical.QKDPhysicalLayerConfig.FILE_NAME);
        if (qkdPhysicalLayerFile != null) {
            qkdPhysicalLayer = gson.fromJson(readFile(qkdPhysicalLayerFile),
                    qkd.physical.QKDPhysicalLayerConfig.class);
        }

        // Result files keep their original handling. Configuration files are
        // skipped here so network.json, traffic.json, etc. are not interpreted
        // as result files.
        for (int i = 0; i < listOfFiles.length; i++) {
            if (!listOfFiles[i].isFile() || isConfigFileName(listOfFiles[i].getName())) {
                continue;
            }

            String metric = listOfFiles[i].getName();
            String s[] = metric.split("_");
            metric = s[s.length - 1];

            switch (metric) {
                case SimulationRequest.Result.FILE_BITRATE_BLOCKING_PROBABILITY:
                    sr.getResult().bitRateBlockingProbability = readFile(listOfFiles[i], true);
                    break;
                case SimulationRequest.Result.FILE_BLOCKING_PROBABILITY:
                    sr.getResult().blockingProbability = readFile(listOfFiles[i], true);
                    break;
                case SimulationRequest.Result.FILE_CONSUMED_ENERGY:
                    sr.getResult().consumedEnergy = readFile(listOfFiles[i], true);
                    break;
                case SimulationRequest.Result.FILE_EXTERNAL_FRAGMENTATION:
                    sr.getResult().externalFragmentation = readFile(listOfFiles[i], true);
                    break;
                case SimulationRequest.Result.FILE_GROOMING_STATISTICS:
                    sr.getResult().groomingStatistics = readFile(listOfFiles[i], true);
                    break;
                case SimulationRequest.Result.FILE_MODULATION_UTILIZATION:
                    sr.getResult().modulationUtilization = readFile(listOfFiles[i], true);
                    break;
                case SimulationRequest.Result.FILE_RELATIVE_FRAGMENTATION:
                    sr.getResult().relativeFragmentation = readFile(listOfFiles[i], true);
                    break;
                case SimulationRequest.Result.FILE_SPECTRUM_STATISTICS:
                    sr.getResult().spectrumStatistics = readFile(listOfFiles[i], true);
                    break;
                case SimulationRequest.Result.FILE_SPECTRUM_UTILIZATION:
                    sr.getResult().spectrumUtilization = readFile(listOfFiles[i], true);
                    break;
                case SimulationRequest.Result.FILE_TRANSMITTERS_RECEIVERS_REGENERATORS_UTILIZATION:
                    sr.getResult().transmittersReceiversRegeneratorsUtilization = readFile(listOfFiles[i], true);
                    break;
                case SimulationRequest.Result.FILE_DATASET_INFORMATION:
                    sr.getResult().dataSetInformation = readFile(listOfFiles[i], true);
                    break;
                case SimulationRequest.Result.FILE_PHYSICAL_LAYER_STATISTICS:
                    sr.getResult().physicalLayerStatistics = readFile(listOfFiles[i], true);
                    break;
                case SimulationRequest.Result.FILE_QKD_STATISTICS:
                    sr.getResult().qkdStatistics = readFile(listOfFiles[i], true);
                    break;
                case SimulationRequest.Result.FILE_QKD_METRICS:
                    sr.getResult().qkdMetrics = readFile(listOfFiles[i], true);
                    break;
                case SimulationRequest.Result.FILE_BLOCKING_DETAILS:
                    sr.getResult().blockingDetails = readFile(listOfFiles[i], true);
                    break;
            }
        }

        if (qkdPhysicalLayer != null && sr.getPhysicalLayerConfig() != null) {
            sr.getPhysicalLayerConfig().setQkdPhysicalLayer(qkdPhysicalLayer);
            if (sr.getPhysicalLayerConfig().hasLegacyQkdBlocks()) {
                System.out.println("Note: " + qkd.physical.QKDPhysicalLayerConfig.FILE_NAME
                        + " found; the qkdPhysical/qkdReceiver blocks of physicalLayer are ignored.");
            }
        }

        return sr;
    }

    /**
     * Resolves a configuration file for reading. The explicit JSON form is the
     * preferred representation; the legacy extensionless name remains fully
     * supported for backwards compatibility.
     */
    private File resolveConfigFileForRead(File folder, String baseName) {
        File jsonFile = new File(folder, baseName + JSON_EXTENSION);
        File legacyFile = new File(folder, baseName);

        if (jsonFile.isFile()) {
            if (legacyFile.isFile()) {
                System.out.println("Warning: both '" + baseName + "' and '"
                        + baseName + JSON_EXTENSION + "' exist in " + folder.getPath()
                        + ". Using the .json file.");
            }
            return jsonFile;
        }

        return legacyFile.isFile() ? legacyFile : null;
    }

    /** Returns true when the supplied file is one of the known config files. */
    private boolean isConfigFileName(String fileName) {
        String baseName = removeJsonExtension(fileName);
        for (String configName : CONFIG_FILE_NAMES) {
            if (configName.equals(baseName)) {
                return true;
            }
        }
        return false;
    }

    /** Removes only the optional .json suffix (case-insensitive). */
    private String removeJsonExtension(String fileName) {
        if (fileName != null && fileName.toLowerCase().endsWith(JSON_EXTENSION)) {
            return fileName.substring(0, fileName.length() - JSON_EXTENSION.length());
        }
        return fileName;
    }

    /**
     * Writes the simulation.
     * @param path the path.
     * @param sr the sr.
     */
    public void writeSimulation(String path, SimulationRequest sr) throws IOException {
        // Create a folder with the name and add it to the path
        path = path + System.getProperty("file.separator") + sr.getName();
        Path p = Paths.get(path);
        if (!Files.exists(p)) {
            Files.createDirectories(p);
        }

        saveConfig(path,sr);
        saveResult(path,sr);
    }

    /**
     * Reads the file.
     * @param f the f.
     * @param carriageReturn the carriageReturn.
     * @return the result of the operation.
     */
    private String readFile(File f, boolean carriageReturn) throws FileNotFoundException {
        Scanner scanner = new Scanner(f);
        StringBuilder res = new StringBuilder();

        while (scanner.hasNextLine()) {
            res.append(scanner.nextLine());
            if (carriageReturn) {
            	res.append("\n");
            }
        }
        scanner.close();

        return res.toString();
    }

    /**
     * Reads the file.
     * @param f the f.
     * @return the result of the operation.
     */
    private String readFile(File f) throws FileNotFoundException {
        return readFile(f, false);
    }

    /**
     * Saves the config.
     * @param path the path.
     * @param sr the sr.
     */
    private void saveConfig(String path, SimulationRequest sr) throws IOException {
        Gson gson = new GsonBuilder().create();
        saveConfig(path, "network", gson.toJson(sr.getNetworkConfig()));
        saveConfig(path, "simulation", gson.toJson(sr.getSimulationConfig()));
        saveConfig(path, "physicalLayer", gson.toJson(sr.getPhysicalLayerConfig()));
        saveConfig(path, "others", gson.toJson(sr.getOthersConfig()));
        saveConfig(path, "traffic", gson.toJson(sr.getTrafficConfig()));
        if (sr.getPhysicalLayerConfig() != null && sr.getPhysicalLayerConfig().getQkdPhysicalLayer() != null) {
            saveConfig(path, qkd.physical.QKDPhysicalLayerConfig.FILE_NAME,
                    new GsonBuilder().setPrettyPrinting().create().toJson(sr.getPhysicalLayerConfig().getQkdPhysicalLayer()));
        }
    }

    /**
     * Saves the result.
     * @param path the path.
     * @param sr the sr.
     */
    private void saveResult(String path, SimulationRequest sr) throws IOException {
        if(sr.getSimulationConfig().getActiveMetrics().BlockingProbability){
            saveResult(path, SimulationRequest.Result.FILE_BLOCKING_PROBABILITY,sr.getResult().blockingProbability);
        }
        if(sr.getSimulationConfig().getActiveMetrics().BitRateBlockingProbability){
            saveResult(path, SimulationRequest.Result.FILE_BITRATE_BLOCKING_PROBABILITY,sr.getResult().bitRateBlockingProbability);
        }
        if(sr.getSimulationConfig().getActiveMetrics().ExternalFragmentation){
            saveResult(path, SimulationRequest.Result.FILE_EXTERNAL_FRAGMENTATION,sr.getResult().externalFragmentation);
        }
        if(sr.getSimulationConfig().getActiveMetrics().RelativeFragmentation){
            saveResult(path, SimulationRequest.Result.FILE_RELATIVE_FRAGMENTATION,sr.getResult().relativeFragmentation);
        }
        if(sr.getSimulationConfig().getActiveMetrics().SpectrumUtilization){
            saveResult(path, SimulationRequest.Result.FILE_SPECTRUM_UTILIZATION,sr.getResult().spectrumUtilization);
        }
        if(sr.getSimulationConfig().getActiveMetrics().SpectrumSizeStatistics){
            saveResult(path, SimulationRequest.Result.FILE_SPECTRUM_STATISTICS,sr.getResult().spectrumStatistics);
        }
        if(sr.getSimulationConfig().getActiveMetrics().ModulationUtilization){
            saveResult(path, SimulationRequest.Result.FILE_MODULATION_UTILIZATION,sr.getResult().modulationUtilization);
        }
        if(sr.getSimulationConfig().getActiveMetrics().ConsumedEnergy){
            saveResult(path, SimulationRequest.Result.FILE_CONSUMED_ENERGY,sr.getResult().consumedEnergy);
        }
        if(sr.getSimulationConfig().getActiveMetrics().GroomingStatistics){
            saveResult(path, SimulationRequest.Result.FILE_GROOMING_STATISTICS,sr.getResult().groomingStatistics);
        }
        if(sr.getSimulationConfig().getActiveMetrics().TransmittersReceiversRegeneratorsUtilization){
            saveResult(path, SimulationRequest.Result.FILE_TRANSMITTERS_RECEIVERS_REGENERATORS_UTILIZATION,sr.getResult().transmittersReceiversRegeneratorsUtilization);
        }
        if(sr.getSimulationConfig().getActiveMetrics().DataSetInformation){
            saveResult(path, SimulationRequest.Result.FILE_DATASET_INFORMATION,sr.getResult().dataSetInformation);
        }
        if(sr.getSimulationConfig().getActiveMetrics().PhysicalLayerStatistics){
            saveResult(path, SimulationRequest.Result.FILE_PHYSICAL_LAYER_STATISTICS,sr.getResult().physicalLayerStatistics);
        }
        if(sr.getResult().qkdStatistics != null){
            saveResult(path, SimulationRequest.Result.FILE_QKD_STATISTICS,sr.getResult().qkdStatistics);
        }
        if(sr.getResult().qkdMetrics != null && sr.getSimulationConfig().getActiveMetrics().QKDMetrics){
            saveResult(path, SimulationRequest.Result.FILE_QKD_METRICS,sr.getResult().qkdMetrics);
        }
        if(sr.getResult().blockingDetails != null && sr.getSimulationConfig().getActiveMetrics().BlockingProbability){
            saveResult(path, SimulationRequest.Result.FILE_BLOCKING_DETAILS,sr.getResult().blockingDetails);
        }
        if(sr.getResult().slotUtilization != null && sr.getSimulationConfig().getActiveMetrics().SpectrumUtilization
                && sr.getSimulationConfig().getActiveMetrics().SlotUtilization){
            saveResult(path, SimulationRequest.Result.FILE_SLOT_UTILIZATION,sr.getResult().slotUtilization);
        }
        if(sr.getResult().slotBlocking != null && sr.getSimulationConfig().getActiveMetrics().BlockingProbability
                && sr.getSimulationConfig().getActiveMetrics().SlotBlocking){
            saveResult(path, SimulationRequest.Result.FILE_SLOT_BLOCKING,sr.getResult().slotBlocking);
        }
    }

    /**
     * Saves the file.
     * @param path the path.
     * @param value the value.
     */
    private void saveFile(String path, String value) throws IOException {
        if(value==null) return;

        FileWriter fw;

        fw = new FileWriter(new File(path));
        fw.write(value);
        fw.close();
    }

    /**
     * Saves the result.
     * @param path the path.
     * @param metric the metric.
     * @param resultCsv the resultCsv.
     */
    private void saveResult(String path, String metric, String resultCsv) throws IOException {
        String separador = System.getProperty("file.separator");
        String aux[] = path.split(Pattern.quote(separador));
        String nomePasta = aux[aux.length - 1];
        String p = path + separador + nomePasta +"_"+ metric;
        saveFile(p, resultCsv);
    }

    /**
     * Saves the config.
     * @param path the path.
     * @param config the config.
     * @param value the value.
     */
    private void saveConfig(String path, String config, String value) throws IOException {
        File target = resolveConfigFileForWrite(new File(path), config);
        saveFile(target.getPath(), value);
    }

    /**
     * Keeps the filename style of an existing case. If both variants exist, the
     * .json file is updated because it is also the read priority. New files use
     * the explicit .json extension.
     */
    private File resolveConfigFileForWrite(File folder, String baseName) {
        File jsonFile = new File(folder, baseName + JSON_EXTENSION);
        if (jsonFile.isFile()) {
            return jsonFile;
        }

        File legacyFile = new File(folder, baseName);
        if (legacyFile.isFile()) {
            return legacyFile;
        }

        return jsonFile;
    }

}
