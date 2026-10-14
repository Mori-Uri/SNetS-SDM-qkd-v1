package measurement;

import java.util.ArrayList;
import java.util.List;

import network.Circuit;
import network.ControlPlane;
import request.RequestForConnection;
import simulationControl.Util;
import simulationControl.parsers.SimulationRequest;
import simulationControl.resultManagers.DataSetInfoResultManager;
import util.tools.machineLearning.DataSetInterface;
import util.tools.machineLearning.DataSetManager;
import util.tools.machineLearning.collectingData.LineDataManager;

/**
 * Represents the DataSetInformation component.
 */
public class DataSetInformation extends Measurement {

    public final static String SEP = "-";

    private List<DataSetInterface> dataSetList;
    private Util util;

    private int differentQuantity;
    private LineDataManager lineDataManager;

    private int identifyResultsFile;

    /**
     * Creates a new instance of BlockingProbability
     *
     * @param loadPoint int
     * @param rep int
     * @param util Util
     */
    public DataSetInformation(int loadPoint, int rep, Util util) {
        super(loadPoint, rep);

        this.util = util;
        this.dataSetList = new ArrayList<>();

		this.resultManager = new DataSetInfoResultManager();

		this.differentQuantity = 0;
		this.lineDataManager = new LineDataManager();

		this.identifyResultsFile = 0;
    }

    /**
     * Adds a new observation of DataSet
     *
     * @param cp ControlPlane
     * @param success boolean
     * @param request RequestForConnection
     */
    public void addNewObservation(ControlPlane cp, boolean success, RequestForConnection request) {

    	//Information about the circuits that attend the request
    	for (Circuit c: request.getCircuits()) {

//    		if (cp.getIntegrated() instanceof SAR_and_BF5) {
//    			identifyResultsFile = 1;
//
//				differentQuantity = ((SAR_and_BF5)cp.getIntegrated()).getDifferentQuantity();
//				lineDataManager.toRecord(((SAR_and_BF5)cp.getIntegrated()).getLineDataList(), cp, getLoadPoint(), getReplication());
//
//    		} else {
    			DataSetInterface newDataSetInfo = DataSetManager.createNewDatasetStructure(cp, c);
        		if (newDataSetInfo != null) {
        			this.dataSetList.add(newDataSetInfo);
        		}
//    		}

    	}
    }

    /**
     * Returns the file name.
     * @return the file name.
     */
    @Override
    public String getFileName() {
        return SimulationRequest.Result.FILE_DATASET_INFORMATION;
    }

    /**
     * Returns the DataSet List
     *
     * @return List<DataSetInterface>
     */
    public List<DataSetInterface> getDataSetList() {
		return dataSetList;
	}

    /**
     * Returns the util
     *
     * @return util
     */
    public Util getUtil() {
        return util;
    }

    /**
     * Returns the differentQuantity
     *
     * @return differentQuantity
     */
	public int getDifferentQuantity() {
		return differentQuantity;
	}

	/**
	 * Returns the identifyResultsFile
	 *
	 * @return identifyResultsFile
	 */
	public int getIdentifyResultsFile() {
		return identifyResultsFile;
	}


}
