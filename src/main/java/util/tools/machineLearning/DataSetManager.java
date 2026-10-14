package util.tools.machineLearning;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.opencsv.CSVWriter;

import network.Circuit;
import network.ControlPlane;

/**
 * Class that manages the DataSet
 */
public class DataSetManager {

	public final static char charSeparator = ',';
	public final static String strSeparator = charSeparator + "";

	public final static String DATASET_FILE_FOLDER = "dataSet_loadPoint_replication";
	public final static String DATASET_FILE_NAME = "dataSetML";

	private final static Map<String, CSVWriter> csvWriters = new HashMap<>();

	/**
	 * This method creates a new DataSetStructure
	 *
	 * @param cp ControlPlane
	 * @param c Circuit
	 * @return DataSetStructure
	 */
	public static DataSetInterface createNewDatasetStructure(ControlPlane cp, Circuit c) {
		DataSetStructure newDataSetInfo = new DataSetStructure();
		newDataSetInfo.setDatasetStructure(cp, c);

		return newDataSetInfo;
	}

	/**
	 * This method creates the CSV file from the DataSetStructure List
	 *
	 * synchronized: Ensures that two threads do not write to the same file at the same time.
	 *
	 * @param dataSetList List<DataSetInterface>
	 * @param projectPath String
	 * @param complementName String
	 */
	public synchronized static void createDatasetFileCSV(List<DataSetInterface> dataSetList, String projectPath, String complementName) {
		String separator = System.getProperty("file.separator");
		String dataSetFolder = projectPath + separator + DATASET_FILE_FOLDER;
		String dataSetPath = dataSetFolder + separator + DATASET_FILE_NAME + complementName + ".csv";

		try {
			//To create the DataSet folder
			File fileFolder = new File(dataSetFolder);
			if (!fileFolder.exists()) { //Check if the folder does not exist before trying to create it
				fileFolder.mkdirs();
			}

			CSVWriter csvWriter = csvWriters.get(dataSetPath);
            if (csvWriter == null) {
				Writer writer = Files.newBufferedWriter(Paths.get(dataSetPath));
				csvWriter = new CSVWriter(writer, charSeparator, CSVWriter.NO_QUOTE_CHARACTER, CSVWriter.DEFAULT_ESCAPE_CHARACTER, CSVWriter.DEFAULT_LINE_END);

				//Adding the header
	    		String dataSetHeader = dataSetList.get(0).getHeader();
	    		csvWriter.writeNext(dataSetHeader.split(strSeparator), false);
            }

			for (int i = 0; i < dataSetList.size(); i++) {
				String[] line = dataSetList.get(i).dataSetToString().split(strSeparator);
				csvWriter.writeNext(line, false);
			}

			csvWriter.flush();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
