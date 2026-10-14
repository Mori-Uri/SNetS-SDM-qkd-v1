package util.tools.artificialBeeColony;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the Algo component.
 */
public interface Algo {

	/**
	 * Returns the execute.
	 * @return true if the condition is met; false otherwise.
	 */
	public boolean execute();
	/**
	 * Returns the ig depoch.
	 * @return the ig depoch.
	 */
	public List<Double> getIGDepoch();
	/**
	 * Returns the archive.
	 * @return the archive.
	 */
	public ArrayList<FoodSource> getArchive();
}
