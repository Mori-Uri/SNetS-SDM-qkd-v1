package simulationControl.resultManagers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import measurement.PhyscialLayerStatistics;
import measurement.Measurement;
import network.Pair;

/**
 * Represents the PhysicalLayerStatisticsResultManager component.
 */
public class PhysicalLayerStatisticsResultManager implements ResultManagerInterface {

	private HashMap<Integer, HashMap<Integer, PhyscialLayerStatistics>> pl; // Contains the physical layer metric for all load points and replications
	private List<Integer> loadPoints;
	private List<Integer> replications;
	private List<Pair> pairs;
	private List<Integer> overlapsList;
	private final static String sep = ",";

	/**
	 * This method organizes the data by load point and replication.
	 *
	 * @param llms List<List<Measurement>>
	 */
	public void config(List<List<Measurement>> llms){
		pl = new HashMap<>();
		Set<Integer> uniqueSorted = new TreeSet<>();

		for (List<Measurement> loadPoint : llms) {
			int load = loadPoint.get(0).getLoadPoint();
			HashMap<Integer, PhyscialLayerStatistics>  reps = new HashMap<>();
			pl.put(load, reps);

			for (Measurement pl : loadPoint) {
				reps.put(pl.getReplication(), (PhyscialLayerStatistics)pl);

				for (Integer v : ((PhyscialLayerStatistics) pl).getSortedOverlaps()) {
				    uniqueSorted.add(v);
				}
			}
		}
		PhyscialLayerStatistics pls = (PhyscialLayerStatistics) llms.get(0).get(0);
		loadPoints = new ArrayList<>(pl.keySet());
		replications = new ArrayList<>(pl.values().iterator().next().keySet());
		pairs = new ArrayList<>(pls.getUtil().pairs);
		overlapsList = new ArrayList<>(uniqueSorted);
	}

	/**
	 * Returns a string corresponding to the result file for physical layer
	 *
	 * @return String
	 */
	public String result(List<List<Measurement>> llms){
		config(llms);

		StringBuilder res = new StringBuilder();
		res.append("Metrics" + sep + "LoadPoint" + sep + "overlaps" + sep + "src" + sep + "dest" + sep + " ");

		for (Integer rep : replications) { // Checks how many replications have been made and creates the header of each column
			res.append(sep + "rep" + rep);
		}
		res.append("\n");

		res.append(resultAverageXtPerCircuit());
		res.append("\n\n");

		res.append(resultAverageOsnrPerCircuit());
		res.append("\n\n");

		res.append(resultAveragePowerPerCircuit());
		res.append("\n\n");


		res.append(resultAverageXtPerOverlaps());
		res.append("\n\n");

		res.append(resultMinXtPerOverlaps());
		res.append("\n\n");

		res.append(resultMaxXtPerOverlaps());
		res.append("\n\n");


		res.append(resultAverageXtPerPair());
		res.append("\n\n");

		res.append(resultAverageOsnrPerPair());
		res.append("\n\n");

		res.append(resultAveragePowerPerPair());
		res.append("\n\n");

		return res.toString();
	}

	/**
	 * Returns the average XT per circuit
	 * @return String
	 */
	private String resultAverageXtPerCircuit(){
		StringBuilder res = new StringBuilder();
		for (Integer loadPoint : loadPoints) {
			res.append("Average Crosstalk per circuit (dB)" + sep + loadPoint + sep + "all" + sep + "all" + sep + "all" + sep + " ");
			for (Integer rep : replications) {
				res.append(sep + pl.get(loadPoint).get(rep).getXtPerCircuit());
			}
			res.append("\n");
		}
		return res.toString();
	}

	/**
	 * Returns the average OSNR per circuit
	 * @return String
	 */
	private String resultAverageOsnrPerCircuit(){
		StringBuilder res = new StringBuilder();
		for (Integer loadPoint : loadPoints) {
			res.append("Average OSNR per circuit (dB)" + sep + loadPoint + sep + "all" + sep + "all" + sep + "all" + sep + " ");
			for (Integer rep : replications) {
				res.append(sep + pl.get(loadPoint).get(rep).getOsnrPerCircuit());
			}
			res.append("\n");
		}
		return res.toString();
	}

	/**
	 * Returns the average Power per circuit
	 * @return String
	 */
	private String resultAveragePowerPerCircuit(){
		StringBuilder res = new StringBuilder();
		for (Integer loadPoint : loadPoints) {
			res.append("Average Launch Power per circuit (dBm)" + sep + loadPoint + sep + "all" + sep + "all" + sep + "all" + sep + " ");
			for (Integer rep : replications) {
				res.append(sep + pl.get(loadPoint).get(rep).getPowerPerCircuit());
			}
			res.append("\n");
		}
		return res.toString();
	}

	/**
	 * Returns the average XT per overlaps
	 * @return String
	 */
	private String resultAverageXtPerOverlaps(){
		StringBuilder res = new StringBuilder();
		for (Integer loadPoint : loadPoints) {
			String aux = "Average Crosstalk per overlaps (dB)" + sep + loadPoint;

			for (int i = 0; i < overlapsList.size(); i++) {
				int overlap = overlapsList.get(i);

				String aux2 = aux + sep + overlap + sep + "all" + sep + "all" + sep + " ";
				for (Integer rep : replications) {
					aux2 = aux2 + sep + pl.get(loadPoint).get(rep).getAverageXtPerOverlaps(overlap);
				}
				res.append(aux2 + "\n");
			}
		}
		return res.toString();
	}

	/**
	 * Returns the minimum crosstalk per overlaps
	 * @return String
	 */
	private String resultMinXtPerOverlaps(){
		StringBuilder res = new StringBuilder();
		for (Integer loadPoint : loadPoints) {
			String aux = "Minimum Crosstalk per overlaps (dB)" + sep + loadPoint;

			for (int i = 0; i < overlapsList.size(); i++) {
				int overlap = overlapsList.get(i);

				String aux2 = aux + sep + overlap + sep + "all" + sep + "all" + sep + " ";
				for (Integer rep : replications) {
					aux2 = aux2 + sep + pl.get(loadPoint).get(rep).getMinXtPerOverlaps(overlap);
				}
				res.append(aux2 + "\n");
			}
		}
		return res.toString();
	}

	/**
	 * Returns the maximum crosstalk per overlaps
	 * @return String
	 */
	private String resultMaxXtPerOverlaps(){
		StringBuilder res = new StringBuilder();
		for (Integer loadPoint : loadPoints) {
			String aux = "Maximum Crosstalk per overlaps (dB)" + sep + loadPoint;

			for (int i = 0; i < overlapsList.size(); i++) {
				int overlap = overlapsList.get(i);

				String aux2 = aux + sep + overlap + sep + "all" + sep + "all" + sep + " ";
				for (Integer rep : replications) {
					aux2 = aux2 + sep + pl.get(loadPoint).get(rep).getMaxXtPerOverlaps(overlap);
				}
				res.append(aux2 + "\n");
			}
		}
		return res.toString();
	}

	/**
	 * Returns the average XT per pair
	 * @return String
	 */
	private String resultAverageXtPerPair(){
		StringBuilder res = new StringBuilder();
		for (Integer loadPoint : loadPoints) {
			String aux = "Average Crosstalk per pair (dB)" + sep + loadPoint + sep + "all";

			for (Pair pair : this.pairs) {
				String aux2 = aux + sep + pair.getSource().getName() + sep + pair.getDestination().getName() + sep + " ";
				for (Integer replic : replications) {
					aux2 = aux2 + sep + pl.get(loadPoint).get(replic).getAverageXtPerPair(pair);
				}
				res.append(aux2 + "\n");
			}
		}
		return res.toString();
	}

	/**
	 * Returns the average OSNR per pair
	 * @return String
	 */
	private String resultAverageOsnrPerPair(){
		StringBuilder res = new StringBuilder();
		for (Integer loadPoint : loadPoints) {
			String aux = "Average OSNR per pair (dB)" + sep + loadPoint + sep + "all";

			for (Pair pair : this.pairs) {
				String aux2 = aux + sep + pair.getSource().getName() + sep + pair.getDestination().getName() + sep + " ";
				for (Integer replic : replications) {
					aux2 = aux2 + sep + pl.get(loadPoint).get(replic).getAverageOsnrPerPair(pair);
				}
				res.append(aux2 + "\n");
			}
		}
		return res.toString();
	}

	/**
	 * Returns the average Power per pair
	 * @return String
	 */
	private String resultAveragePowerPerPair(){
		StringBuilder res = new StringBuilder();
		for (Integer loadPoint : loadPoints) {
			String aux = "Average Launch Power per pair (dBm)" + sep + loadPoint + sep + "all";

			for (Pair pair : this.pairs) {
				String aux2 = aux + sep + pair.getSource().getName() + sep + pair.getDestination().getName() + sep + " ";
				for (Integer replic : replications) {
					aux2 = aux2 + sep + pl.get(loadPoint).get(replic).getAveragePowerPerPair(pair);
				}
				res.append(aux2 + "\n");
			}
		}
		return res.toString();
	}
}
