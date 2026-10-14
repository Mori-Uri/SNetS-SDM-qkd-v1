package simulationControl.resultManagers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import measurement.DataSetInformation;
import measurement.Measurement;
import util.tools.machineLearning.DataSetInterface;
import util.tools.machineLearning.DataSetManager;

/**
 * This class represents the dataset information
 * The metric represented by this class is associated with a load point and a replication
 */
public class DataSetInfoResultManager implements ResultManagerInterface {

	private HashMap<Integer, HashMap<Integer, DataSetInformation>> dsi; // Contains the dataset information for all load points and replications
	private List<Integer> loadPoints;
	private List<Integer> replications;
	private final static String sep = ",";
	private String projectPath;

	/**
	 * This method organizes the data by load point and replication.
	 *
	 * @param llms List<List<Measurement>>
	 */
	public void config(List<List<Measurement>> llms){
		dsi = new HashMap<>();

		for (List<Measurement> loadPoint : llms) {
			int load = loadPoint.get(0).getLoadPoint();
			HashMap<Integer, DataSetInformation>  reps = new HashMap<>();
			dsi.put(load, reps);

			for (Measurement dsilp : loadPoint) {
				reps.put(dsilp.getReplication(), (DataSetInformation)dsilp);
			}
		}
		DataSetInformation dsil = (DataSetInformation) llms.get(0).get(0);
		loadPoints = new ArrayList<>(dsi.keySet());
		replications = new ArrayList<>(dsi.values().iterator().next().keySet());
		projectPath = dsil.getUtil().projectPath;
	}

	/**
	 * Returns a string corresponding to the result file for blocking probabilities
	 *
	 * @return String
	 */
	public String result(List<List<Measurement>> llms){
		config(llms);

		StringBuilder res = new StringBuilder();

		if (dsi.get(0).get(0).getIdentifyResultsFile() == 1) { //SAR and BF5

			res.append("Metrics" + sep + "LoadPoint" + sep + "BitRate" + sep + "src" + sep + "dest" + sep + " ");

			for (Integer rep : replications) {
				res.append(sep + "rep" + rep);
			}
			res.append("\n");

			res.append(resultQuantDifferent());
			res.append("\n\n");

		} else {
			res.append("LoadPoint" + sep + "Replication" + sep + " " + sep);

			String dataSetHeader = dsi.get(0).get(0).getDataSetList().get(0).getHeader();
			res.append(dataSetHeader);
			res.append("\n");

			res.append(resultDataSetByPointAndReplication());
			res.append("\n\n");

			saveDataSetByPointAndReplication();
			saveDataSetByAllPointAndAllReplication();
		}

		return res.toString();
	}

	/**
	 * Returns the dataset by line for points and replicates
	 *
	 * @return String
	 */
	private String resultDataSetByPointAndReplication(){
		StringBuilder res = new StringBuilder();
		for (Integer loadPoint : loadPoints) {
			for (Integer replic : replications) {
				List<DataSetInterface> dataSetList = dsi.get(loadPoint).get(replic).getDataSetList();
				for (DataSetInterface datasetLine : dataSetList) {
					res.append(loadPoint + sep + replic + sep + " " + sep);
					String strDataSetLine = datasetLine.dataSetToString();
					res.append(strDataSetLine);
					res.append("\n");
				}
			}
		}
		return res.toString();
	}

	/**
	 * Save the dataset by points and replicates
	 */
	private void saveDataSetByPointAndReplication(){
		for (Integer loadPoint : loadPoints) {
			for (Integer replic : replications) {
				List<DataSetInterface> dataSetList = dsi.get(loadPoint).get(replic).getDataSetList();
				String complementName = "_lp" + loadPoint + "_rp" + replic;
				DataSetManager.createDatasetFileCSV(dataSetList, projectPath, complementName);
			}
		}
	}

	/**
	 * Save the dataset by all points and all replicates
	 */
	private void saveDataSetByAllPointAndAllReplication(){
		List<DataSetInterface> lines = new ArrayList<>();
		for (Integer loadPoint : loadPoints) {
			for (Integer replic : replications) {
				List<DataSetInterface> dataSetList = dsi.get(loadPoint).get(replic).getDataSetList();
				for (DataSetInterface datasetLine : dataSetList) {
					lines.add(datasetLine);
				}
			}
		}
		String complementName = "_lpAll_rpAll";
		DataSetManager.createDatasetFileCSV(lines, projectPath, complementName);
	}

	/**
	 * Returns the result quant different.
	 * @return the result of the operation.
	 */
	private String resultQuantDifferent(){
		StringBuilder res = new StringBuilder();
		for (Integer loadPoint : loadPoints) {
			res.append("Different Quantity" + sep + loadPoint + sep + "all" + sep + "all" + sep + "all" + sep + " ");
			for (Integer replic : replications) {
				res.append(sep + dsi.get(loadPoint).get(replic).getDifferentQuantity());
			}
			res.append("\n");
		}
		return res.toString();
	}

}
