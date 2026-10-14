package gprmcsa.reallocation;

import java.io.Serializable;

import network.Circuit;
import network.ControlPlane;


/**
 * Represents the ReallocationAlgorithmInterface component.
 */
public interface ReallocationAlgorithmInterface extends Serializable {

	/**
	 * Method to select the active circuits that will be reallocated
	 * @param cp
	 */
	void selectActivesCircuits(ControlPlane cp, Circuit c);

	/**
	 * Choose new network resources to reallocate the selected circuits
	 * @param cp
	 */
	void chooseNewResourcesForSelectedCircuits(ControlPlane cp);

	/**
	 * Migrates the circuit position in the network - new RMLSA
	 */
	void trafficMigration();

	/**
	 * Returns the strategy.
	 * @param requisicaoCircuito the requisicaoCircuito.
	 * @param cp the cp.
	 * @return true if the condition is met; false otherwise.
	 */
	boolean strategy(Circuit requisicaoCircuito, ControlPlane cp);
}
