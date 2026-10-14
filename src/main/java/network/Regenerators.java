package network;

import java.io.Serializable;

/**
 * This class represents a bank of regenerators on a network node.
 *
 * @author Alexandre
 */
public class Regenerators implements Serializable {

	private int regenUtilization;
    private int numRegenerators;

    /**
     * Creates a new instance of Regenerators.
     * @param numberOfRegenerators the numberOfRegenerators.
     */
    public Regenerators(int numberOfRegenerators){
    	this.regenUtilization = 0;
    	this.numRegenerators = numberOfRegenerators;
    }

    /**
     * Returns the allocates regenerators.
     * @param circuit the circuit.
     * @return true if the condition is met; false otherwise.
     */
    public boolean allocatesRegenerators(Circuit circuit){
    	int numberOfRegenerators = getAmountOfRequiredRegenerators(circuit);

    	if(quantRegeneratorsFree() >= numberOfRegenerators){
    		regenUtilization += numberOfRegenerators;

    		return true;
    	}
		return false;
	}

	/**
	 * Returns the releases regenerators.
	 * @param circuit the circuit.
	 * @return true if the condition is met; false otherwise.
	 */
	public boolean releasesRegenerators(Circuit circuit){
		int numberOfRegenerators = getAmountOfRequiredRegenerators(circuit);

		if((regenUtilization - numberOfRegenerators) >= 0){
			regenUtilization -= numberOfRegenerators;

			return true;
		}
		return false;
	}

	/**
	 * Returns the regenerators free.
	 * @return true if the condition is met; false otherwise.
	 */
	public boolean regeneratorsFree(){
		return (regenUtilization < numRegenerators);
	}

	/**
	 * Returns the quant regenerators free.
	 * @return the result of the operation.
	 */
	public int quantRegeneratorsFree(){
		return numRegenerators - regenUtilization;
	}

	/**
	 * @return the regenUtilization
	 */
	public int getRegenUtilization() {
		return regenUtilization;
	}

	/**
	 * @return the numRegenerators
	 */
	public int getNumRegenerators() {
		return numRegenerators;
	}

	/**
	 * @param numRegenerators the numRegenerators to set
	 */
	public void setNumRegenerators(int numRegenerators) {
		this.numRegenerators = numRegenerators;
	}

	/**
	 * Checks if there are sufficient free regenerators to attend the required amount
	 *
	 * @param numberOfRegenerators int
	 * @return boolean
	 */
	public boolean canRegenerate(int numberOfRegenerators) {
        if (quantRegeneratorsFree() >= numberOfRegenerators) {
            return true;
        }
        return false;
    }

	/**
	 * Returns the amount of regenerators required by the circuit
	 * @param circuit - Circuit
	 * @return int - number of regenerators
	 */
	public int getAmountOfRequiredRegenerators(Circuit circuit){
		double BR = 100.0; //Gbps
		double Bn = circuit.getRequiredBitRate() / 1000000000.0;
		int quantRegeneradoresRequeridos = PhysicalLayer.roundUp(Bn / BR);
		return quantRegeneradoresRequeridos;
	}
}
