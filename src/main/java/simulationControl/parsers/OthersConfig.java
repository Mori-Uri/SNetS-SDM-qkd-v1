package simulationControl.parsers;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents the OthersConfig component.
 */
public class OthersConfig implements Serializable {

	 private Map<String, String> variables = new HashMap<>();
	 private Map<String, Map<String, Double>> kbpWeights = new HashMap<>();

	 /**
	  * Creates a new instance of OthersConfig.
	  */
	 public OthersConfig() {
		 //Constructor
	 }

	 /**
	  * Returns the variables.
	  * @return the variables.
	  */
	 public Map<String, String> getVariables() {
		 return this.variables;
	 }

	 /**
	  * Sets the variables.
	  * @param variables the variables.
	  */
	 public void setVariables(Map<String, String> variables) {
		 this.variables = variables;
	 }

	 /**
	  * Returns the kbp weights.
	  * @return the kbp weights.
	  */
	 public Map<String, Map<String, Double>> getKbpWeights() {
		 return kbpWeights;
	 }

	 /**
	  * Sets the kbp weights.
	  * @param kbpWeights the kbpWeights.
	  */
	 public void setKbpWeights(Map<String, Map<String, Double>> kbpWeights) {
		 this.kbpWeights = kbpWeights;
	 }
}
