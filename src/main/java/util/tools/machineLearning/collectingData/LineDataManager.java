package util.tools.machineLearning.collectingData;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.opencsv.CSVWriter;

import network.ControlPlane;
import util.tools.machineLearning.DataSetManager;

/**
 * Represents the LineDataManager component.
 */
public class LineDataManager {

	public final static char charSeparator = ',';
	public final static String strSeparator = charSeparator + "";

	private static final String FILE_NAME = "simulation_data";
	private static final String FILE_FOLDER = "data_folder";

    private final Map<String, CSVWriter> csvWriters = new HashMap<>();


	/**
	 * Save data to a CSV file.
	 *
	 * synchronized: Ensures that two threads do not write to the same file at the same time.
	 */
	public synchronized void toRecord(List<LineData> lineDataList, ControlPlane cp, int loadPoint, int replication) {

		if (lineDataList.isEmpty()) {
			return;
		}

		String sep = System.getProperty("file.separator");
		String dataFolder = cp.getMesh().getUtil().projectPath + sep + FILE_FOLDER;
		String dataPath = dataFolder + sep + FILE_NAME + "_lp" + loadPoint + "_rp" + replication + ".csv";

		//To create the DataSet folder
		File fileFolder = new File(dataFolder);
		if (!fileFolder.exists()) { //Check if the folder does not exist before trying to create it
			fileFolder.mkdirs();
		}

		// Find the last saved index (to continue where you left off).
        int ultimaIteracao = getLastIteration(dataPath);

        try {
        	// Get the writer corresponding to this file (or create a new one).
            CSVWriter csvWriter = csvWriters.get(dataPath);
            if (csvWriter == null) {
	        	BufferedWriter writer = Files.newBufferedWriter(Paths.get(dataPath), StandardOpenOption.CREATE,  StandardOpenOption.APPEND);
	        	csvWriter = new CSVWriter(writer, charSeparator, CSVWriter.NO_QUOTE_CHARACTER, CSVWriter.DEFAULT_ESCAPE_CHARACTER, CSVWriter.DEFAULT_LINE_END);

	            // If the file is new, write the header.
	            if (ultimaIteracao == 0) {
	            	String header = lineDataList.get(0).getHeader();
	            	csvWriter.writeNext(header.split(strSeparator), false);
	            }
	        }

            // It starts with the next iteration.
            int index = 0;
            for (int i = ultimaIteracao; i < ultimaIteracao + lineDataList.size(); i++) {
    			String newLine = ((i + 1) + DataSetManager.strSeparator + lineDataList.get(index).dataSetToString());
    			String[] lineW = newLine.split(strSeparator);
            	csvWriter.writeNext(lineW, false);
    			index++;
            }

            csvWriter.flush();

            lineDataList.clear(); // frees up memory used by the list.

        } catch (IOException e) {
            e.printStackTrace();
        }
	}

	/**
     * Reads the CSV file and returns the value from the last saved iteration.
     */
    private static int getLastIteration(String fileName) {
        File file = new File(fileName);
        if (!file.exists()) {
            return 0; // new file
        }

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long length = file.length();
            if (length == 0) return 0;

            long pos = length - 1;
            StringBuilder lastLine = new StringBuilder();

            // Read from back to front until you find the line break.
            while (pos >= 0) {
                raf.seek(pos);
                char c = (char) raf.readByte();
                if (c == '\n' && pos < length - 1) break;
                lastLine.insert(0, c);
                pos--;
            }

            // Extract the number from the first column (Interaction).
            String[] partes = lastLine.toString().split(",");
            if (partes.length > 0) {
                try {
                    return Integer.parseInt(partes[0].trim());
                } catch (NumberFormatException e) {
                    return 0; // if the final line is invalid
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return 0;
    }

    /**
     * Calling this method at the end of the simulation will close everything correctly.
     */
    public void closeAllWriters() {
        for (CSVWriter writer : csvWriters.values()) {
            try {
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        csvWriters.clear();
    }

}
