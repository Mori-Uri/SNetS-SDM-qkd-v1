package util.tools.machineLearning;

import java.io.Serializable;

import network.Circuit;
import network.ControlPlane;


/**
 * This interface represents the DataSet structure
 */
public interface DataSetInterface extends Serializable {

	/**
	 * Method that creates the DataSet header
	 *
	 * @return String
	 */
	public String getHeader();

	/**
	 * This method creates a String based on the DataSetStructure
	 *
	 * @return String
	 */
	public String dataSetToString();

	/**
	 * This method sets the DataSetStructure
	 *
	 * @param cp ControlPlane
	 * @param c Circuit
	 */
	public void setDatasetStructure(ControlPlane cp, Circuit c);
}
