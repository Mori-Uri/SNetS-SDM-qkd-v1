package util.tools;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import measurement.BlockingProbability;
import measurement.Measurement;
import measurement.Measurements;
import measurement.SpectrumUtilization;
import simulationControl.Main;
import simulationControl.SimulationFileManager;
import simulationControl.SimulationManagement;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.NetworkConfig.LinkConfig;
import simulationControl.parsers.SimulationRequest;

/**
 * Represents the KBP_Tuning component.
 */
public class KBP_Tuning {

	private static String pathFileOfSimulation;
	private static String separator;
	private static int bestIteration;
	private static List<Double> BPbyIteractions;

	// If true, keep searching using the weights informed in the 'others' file
	private static boolean loadeFileWeights = false;

	/**
	 * Runs the application entry point.
	 * @param args the args.
	 */
	public static void main(String[] args) {

		if(args.length == 0){
            System.out.println("Error: Enter the simulation path");

		} else {

			separator = System.getProperty("file.separator");
			pathFileOfSimulation = args[0];

			NetworkConfig networkConfig = loadNetworkConfig(pathFileOfSimulation);

			double TB = 0.5; // Taxa de balanceamento
			int A = 60; // Number of iterations

			System.out.println("==========================================================");
			System.out.println("*** KBP Tuning - start ***");
			System.out.println("--------------------------");

			Map<String, Map<String, Double>> kbpWeightsFinal = tuning(TB, A, networkConfig);
			createLinkWeightFile(pathFileOfSimulation, "others_BestBP", kbpWeightsFinal); // Stores the weight values that produced the lowest blocking probability
			saveBPbyIteractionsFile(pathFileOfSimulation, BPbyIteractions); // Saves the BPs for each iteration

			System.out.println("------------------------");
			System.out.println("*** KBP Tuning - end ***");
			System.out.println("Iteration with the smallest BP: " + bestIteration);
			System.out.println("==========================================================");
		}
	}

	/**
	 * Returns the tuning.
	 * @param balancingRate the balancingRate.
	 * @param numIterations the numIterations.
	 * @param networkConfig the networkConfig.
	 * @return the result of the operation.
	 */
	public static Map<String, Map<String, Double>> tuning(double balancingRate, int  numIterations, NetworkConfig networkConfig) {

		// Inicializacao
		Map<String, Map<String, Double>> kbpWeightsFinal = new HashMap<>();
		Map<String, Map<String, Double>> kbpWeights = new HashMap<>();

		// Initializes the auxiliary weights with zero (0)
		List<LinkConfig> linkList = networkConfig.getLinks();
		for(LinkConfig link : linkList) {
			String source = link.getSource();
			String destination = link.getDestination();

			Map<String, Double> kbpWeightsTemp = kbpWeights.get(source);
			if(kbpWeightsTemp == null) {
				kbpWeightsTemp = new HashMap<>();
			}
			kbpWeightsTemp.put(destination, 0.0);
			kbpWeights.put(source, kbpWeightsTemp);

			Map<String, Double> kbpWeightsFinalTemp = kbpWeightsFinal.get(source);
			if(kbpWeightsFinalTemp == null) {
				kbpWeightsFinalTemp = new HashMap<>();
			}
			kbpWeightsFinalTemp.put(destination, 0.0);
			kbpWeightsFinal.put(source, kbpWeightsFinalTemp);
		}

		if (loadeFileWeights) {
			loadLinkWeightFile(pathFileOfSimulation, kbpWeights); // Loads the weight values from the 'others' file
		} else {
			createLinkWeightFile(pathFileOfSimulation, "others", kbpWeights); // Stores the weight values
		}

		// Iterecoes
		int i = 0;
		double BPmin = Double.MAX_VALUE;
		double BP = 0.0;
		Map<String, Map<String, Double>> UEE = null; // Spectrum utilization per link

		BPbyIteractions = new ArrayList<>();
		bestIteration = 0;

		while (i < numIterations) {

			System.out.println("--------------------------------");
			System.out.println("*** Iteration: " + (i+1) + " ***");
			System.out.println("--------------------------------");

			// Run a simulation to obtain metric results
			SimulationManagement sm = runSimulation();

			// Check whether a lower blocking result was achieved
			BP = computePB(sm);
			BPbyIteractions.add(BP);
			if(BP <= BPmin) {
				BPmin = BP; // Updates the lowest blocking value
				bestIteration = i+1;
				copyWeights(kbpWeightsFinal, kbpWeights, networkConfig); // Stores the weight values with the lowest blocking
			}

			UEE = computeUEE(sm, networkConfig);
			updateWeights(kbpWeights, networkConfig, UEE, balancingRate); // Updates the link weights
			createLinkWeightFile(pathFileOfSimulation, "others", kbpWeights); // Stores the new link weight values

			i++;
		}

		return kbpWeightsFinal;
	}

	/**
	 * Executes the copy weights operation.
	 * @param kbpWeightsFinal the kbpWeightsFinal.
	 * @param kbpWeights the kbpWeights.
	 * @param networkConfig the networkConfig.
	 */
	public static void copyWeights(Map<String, Map<String, Double>> kbpWeightsFinal, Map<String, Map<String, Double>> kbpWeights, NetworkConfig networkConfig) {
		List<LinkConfig> linkList = networkConfig.getLinks();

		for(LinkConfig link : linkList) {
			String source = link.getSource();
			String destination = link.getDestination();
			Double value = kbpWeights.get(source).get(destination);

			kbpWeightsFinal.get(source).put(destination, value);
		}
	}

	/**
	 * Updates the weights.
	 * @param kgpWeights the kgpWeights.
	 * @param networkConfig the networkConfig.
	 * @param UEE the UEE.
	 * @param balancingRate the balancingRate.
	 */
	public static void updateWeights(Map<String, Map<String, Double>> kgpWeights, NetworkConfig networkConfig, Map<String, Map<String, Double>> UEE, double balancingRate) {
		List<LinkConfig> linkList = networkConfig.getLinks();

		for(LinkConfig link : linkList) {
			String source = link.getSource();
			String destination = link.getDestination();

			Double vUEE = UEE.get(source).get(destination);
			Double maxUEE = 1.0; // Utilizacao maxima = 100%

			Double oldValue = kgpWeights.get(source).get(destination);
			Double newValue = oldValue + ((vUEE / maxUEE) * balancingRate);

			kgpWeights.get(source).put(destination, newValue);
		}
	}

	/**
	 * Returns the run simulation.
	 * @return the result of the operation.
	 */
	public static SimulationManagement runSimulation() {

		try {

			return Main.localSimulation(pathFileOfSimulation);

		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	/**
	 * Computes the pb.
	 * @param sm the sm.
	 * @return the result of the operation.
	 */
	public static double computePB(SimulationManagement sm){
		double mediaPB = 0.0;

		List<List<Measurements>> mainMeasuremens = sm.getMainMeasuremens();

		int lastLoadPoint = mainMeasuremens.size() -1; // Ultimo ponto de carga

		int numMetrics = mainMeasuremens.get(lastLoadPoint).get(0).getMetrics().size();
		for(int m = 0; m < numMetrics; m++){

			Measurement metric = mainMeasuremens.get(lastLoadPoint).get(0).getMetrics().get(m);
			if(metric instanceof BlockingProbability){

				double sumPB = 0.0, contPB = 0.0;

				for(List<Measurements> listMeasurements : mainMeasuremens){
					for(Measurements measurements : listMeasurements){

						if(measurements.getLoadPoint() == lastLoadPoint){
							BlockingProbability pb = (BlockingProbability)measurements.getMetrics().get(m);

							sumPB += pb.getGeneralBlockProb();
							contPB++;
						}
					}
				}

				mediaPB = sumPB / contPB;

				break;
			}
		}

		return mediaPB;
	}

	/**
	 * Computes the uee.
	 * @param sm the sm.
	 * @param networkConfig the networkConfig.
	 * @return the result of the operation.
	 */
	public static Map<String, Map<String, Double>> computeUEE(SimulationManagement sm, NetworkConfig networkConfig){
		Map<String, Map<String, Double>> UEE = new HashMap<>();

		List<List<Measurements>> mainMeasuremens = sm.getMainMeasuremens();

		int lastLoadPoint = mainMeasuremens.size() -1; // Ultimo ponto de carga

		int numMetrics = mainMeasuremens.get(lastLoadPoint).get(0).getMetrics().size();
		for(int m = 0; m < numMetrics; m++){

			Measurement metric = mainMeasuremens.get(lastLoadPoint).get(0).getMetrics().get(m);
			if(metric instanceof SpectrumUtilization){

				List<LinkConfig> linkList = networkConfig.getLinks();

				for(LinkConfig link : linkList) {
					String source = link.getSource();
					String destination = link.getDestination();
					String linkName = source + "-" + destination;

					double sumUEE = 0.0, contUEE = 0.0;

					for (List<Measurements> listMeasurements : mainMeasuremens) {
						for (Measurements measurements : listMeasurements) {

							if(measurements.getLoadPoint() == lastLoadPoint){
								SpectrumUtilization su = (SpectrumUtilization)measurements.getMetrics().get(m);

								sumUEE += su.getUtilizationPerLink(linkName);
								contUEE++;
							}
						}
					}

					double mediaUEE = sumUEE / contUEE;

					Map<String, Double> UEETemp = UEE.get(source);
					if(UEETemp == null) {
						UEETemp = new HashMap<>();
					}
					UEETemp.put(destination, mediaUEE);
					UEE.put(source, UEETemp);
				}

				break;
			}
		}

		return UEE;
	}

	/**
	 * Loads the network config.
	 * @param path the path.
	 * @return the result of the operation.
	 */
	public static NetworkConfig loadNetworkConfig(String path) {
		File f = new File(path);
        String name = f.getName();
        String pathFile = f.getAbsoluteFile().getParentFile().getPath();

        SimulationFileManager sfm = new SimulationFileManager();
        SimulationRequest sr = null;

		try {
			sr = sfm.readSimulation(pathFile, name);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

        NetworkConfig networkConfig = sr.getNetworkConfig();

        return networkConfig;
	}

	/**
	 * Creates the link weight file.
	 * @param path the path.
	 * @param fileName the fileName.
	 * @param kbpWeights the kbpWeights.
	 */
	public static void createLinkWeightFile(String path, String fileName, Map<String, Map<String, Double>> kbpWeights) {

		File f = new File(path);
        String name = f.getName();
        String pathFile = f.getAbsoluteFile().getParentFile().getPath();

        SimulationFileManager sfm = new SimulationFileManager();
        SimulationRequest sr = null;

		try {
			sr = sfm.readSimulation(pathFile, name);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

        sr.getOthersConfig().setKbpWeights(kbpWeights); // Updates the new weights

        // Saves the new configuration of the others file
        Gson gson = new GsonBuilder().create();
        String p = path + separator + fileName;
        saveFile(p, gson.toJson(sr.getOthersConfig()));
	}

	/**
	 * Loads the link weight file.
	 * @param path the path.
	 * @param kbpWeights the kbpWeights.
	 */
	public static void loadLinkWeightFile(String path, Map<String, Map<String, Double>> kbpWeights) {

		File f = new File(path);
        String name = f.getName();
        String pathFile = f.getAbsoluteFile().getParentFile().getPath();

        SimulationFileManager sfm = new SimulationFileManager();
        SimulationRequest sr = null;

		try {
			sr = sfm.readSimulation(pathFile, name);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

        Map<String, Map<String, Double>> kbpWeightsFile = sr.getOthersConfig().getKbpWeights(); // File weights

        for(String source : kbpWeightsFile.keySet()) {
        	for(String destination : kbpWeightsFile.get(source).keySet()) {
        		Double value = kbpWeightsFile.get(source).get(destination);
        		kbpWeights.get(source).put(destination, value);
        	}
        }

	}

	/**
	 * Saves the b pby iteractions file.
	 * @param path the path.
	 * @param BPbyIteractions the BPbyIteractions.
	 */
	public static void saveBPbyIteractionsFile(String path, List<Double> BPbyIteractions) {
        Gson gson = new GsonBuilder().create();
        String p = path + separator + "BPbyIteractions.txt";
        saveFile(p, gson.toJson(BPbyIteractions));

        // Saving to a CSV file
        String sep = ",";
        StringBuilder res = new StringBuilder();

        res.append("Iteration" + sep + "Blocking probability" + "\n");
        for(int i = 0; i < BPbyIteractions.size(); i++) {
        	Double bp = BPbyIteractions.get(i);
        	res.append((i+1) + sep + bp  + "\n");
        }

        String p2 = path + separator + "BPbyIteractions.csv";
        saveFile(p2, res.toString());
	}

	/**
	 * Saves the file.
	 * @param path the path.
	 * @param value the value.
	 */
	private static void saveFile(String path, String value) {
        if(value==null) return;

        FileWriter fw = null;

        try {
			fw = new FileWriter(new File(path));
			fw.write(value);
	        fw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
}
