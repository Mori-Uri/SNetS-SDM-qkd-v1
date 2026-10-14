package simulationControl.resultManagers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import measurement.BitRateBlockingProbability;
import measurement.Measurement;
import network.Pair;

/**
 * This class is responsible for formatting the file with results of bitRate blocking probability
 *
 * @author Iallen
 */
public class BitRateBlockingProbResultManager implements ResultManagerInterface {

	private HashMap<Integer, HashMap<Integer, BitRateBlockingProbability>> bbps; // Contains the bitRate blocking probability metric for all load points and replications
	private List<Integer> loadPoints;
	private List<Integer> replications;
	private List<Pair> pairs;
	private final static String sep = ",";
	private int maxCoresByLinks;

	/**
	 * This method organizes the data by load point and replication.
	 *
	 * @param llms List<List<Measurement>>
	 */
	public void config(List<List<Measurement>> llms){
		bbps = new HashMap<>();

		for (List<Measurement> loadPoint : llms) {
			int load = loadPoint.get(0).getLoadPoint();
			HashMap<Integer, BitRateBlockingProbability>  reps = new HashMap<>();
			bbps.put(load, reps);

			for (Measurement bbp : loadPoint) {
				reps.put(bbp.getReplication(), (BitRateBlockingProbability)bbp);
			}
		}
		BitRateBlockingProbability bbp = (BitRateBlockingProbability) llms.get(0).get(0);
		loadPoints = new ArrayList<>(bbps.keySet());
		replications = new ArrayList<>(bbps.values().iterator().next().keySet());
		this.pairs = new ArrayList<>(bbp.getUtil().pairs);
		maxCoresByLinks = bbp.getMaxCoresByLinks();
	}

	/**
	 * Returns a string corresponding to the result file for bitRate blocking probabilities
	 *
	 * @return String
	 */
	@Override
	public String result(List<List<Measurement>> llms){
		config(llms);

		StringBuilder res = new StringBuilder();
		res.append("Metrics" + sep + "LoadPoint" + sep + "BitRate" + sep + "src" + sep + "dest" + sep + " ");

		for (Integer rep : replications) { // Checks how many replications have been made and creates the header of each column
			res.append(sep + "rep" + rep);
		}
		res.append("\n");

		res.append(resultGeneral());
		res.append("\n\n");
		res.append(resultGeneralRequestedBitRate());
		res.append("\n\n");

		res.append(resultGeneralLackTx());
		res.append("\n\n");
		res.append(resultGeneralLackRx());
		res.append("\n\n");
		res.append(resultGeneralFrag());
		res.append("\n\n");
		res.append(resultGeneralQoTN());
		res.append("\n\n");
		res.append(resultGeneralQoTO());
		res.append("\n\n");
		res.append(resultGeneralOther());
		res.append("\n\n");
		res.append(resultGeneralXt());
		res.append("\n\n");
		res.append(resultGeneralXtOther());
		res.append("\n\n");

		res.append(resultBlockPerCore());
		res.append("\n\n");

		res.append(resultPair());
		res.append("\n\n");
		res.append(resultBitRate());
		res.append("\n\n");
		res.append(resultPairBandwidth());
		res.append("\n\n");

		return res.toString();
	}

	/**
	 * Returns the bitRate blocking probability general
	 *
	 * @return String
	 */
	private String resultGeneral(){
		StringBuilder res = new StringBuilder();
		for (Integer loadPoint : loadPoints) {
			res.append("BitRate blocking probability" + sep + loadPoint + sep + "all" + sep + "all" + sep + "all" + sep + " ");
			for (Integer replic : replications) {
				res.append(sep + bbps.get(loadPoint).get(replic).getProbBlockGeneral());
			}
			res.append("\n");
		}
		return res.toString();
	}

	/**
	 * Returns the general requested bit rate
	 *
	 * @return String
	 */
	private String resultGeneralRequestedBitRate(){
		StringBuilder res = new StringBuilder();
		for (Integer loadPoint : loadPoints) {
			res.append("General requested bitRate" + sep + loadPoint + sep + "all" + sep + "all" + sep + "all" + sep + " ");
			for (Integer replic : replications) {
				res.append(sep + bbps.get(loadPoint).get(replic).getGeneralRequestedBitRate());
			}
			res.append("\n");
		}
		return res.toString();
	}

	/**
	 * Returns the bitRate blocking probability by lack of transmitters
	 *
	 * @return String
	 */
	private String resultGeneralLackTx(){
		StringBuilder res = new StringBuilder();
		for (Integer loadPoint : loadPoints) {
			res.append("BitRate blocking probability by lack of transmitters" + sep + loadPoint + sep + "all" + sep + "all" + sep + "all" + sep + " ");
			for (Integer replic : replications) {
				res.append(sep + bbps.get(loadPoint).get(replic).getBitRateBlockingByLackTx());
			}
			res.append("\n");
		}
		return res.toString();
	}

	/**
	 * Returns the bitRate blocking probability by lack of receivers
	 *
	 * @return String
	 */
	private String resultGeneralLackRx(){
		StringBuilder res = new StringBuilder();
		for (Integer loadPoint : loadPoints) {
			res.append("BitRate blocking probability by lack of receivers" + sep + loadPoint + sep + "all" + sep + "all" + sep + "all" + sep + " ");
			for (Integer replic : replications) {
				res.append(sep + bbps.get(loadPoint).get(replic).getBitRateBlockingByLackRx());
			}
			res.append("\n");
		}
		return res.toString();
	}

	/**
	 * Returns the bitRate blocking probability by fragmentation
	 *
	 * @return String
	 */
	private String resultGeneralFrag(){
		StringBuilder res = new StringBuilder();
		for (Integer loadPoint : loadPoints) {
			res.append("BitRate blocking probability by fragmentation" + sep + loadPoint + sep + "all" + sep + "all" + sep + "all" + sep + " ");
			for (Integer replic : replications) {
				res.append(sep + bbps.get(loadPoint).get(replic).getBitRateBlockingByFragmentation());
			}
			res.append("\n");
		}
		return res.toString();
	}

	/**
	 * Returns the bitRate blocking probability by QoTN
	 *
	 * @return String
	 */
	private String resultGeneralQoTN(){
		StringBuilder res = new StringBuilder();
		for (Integer loadPoint : loadPoints) {
			res.append("BitRate blocking probability by QoTN" + sep + loadPoint + sep + "all" + sep + "all" + sep + "all" + sep + " ");
			for (Integer replic : replications) {
				res.append(sep + bbps.get(loadPoint).get(replic).getBitRateBlockingByQoTN());
			}
			res.append("\n");
		}
		return res.toString();
	}

	/**
	 * Returns the bitRate blocking probability by QoTO
	 *
	 * @return String
	 */
	private String resultGeneralQoTO(){
		StringBuilder res = new StringBuilder();
		for (Integer loadPoint : loadPoints) {
			res.append("BitRate blocking probability by QoTO" + sep + loadPoint + sep + "all" + sep + "all" + sep + "all" + sep + " ");
			for (Integer replic : replications) {
				res.append(sep + bbps.get(loadPoint).get(replic).getBitRateBlockingByQoTO());
			}
			res.append("\n");
		}
		return res.toString();
	}

	/**
	 * Returns the bitRate blocking probability by other
	 *
	 * @return String
	 */
	private String resultGeneralOther(){
		StringBuilder res = new StringBuilder();
		for (Integer loadPoint : loadPoints) {
			res.append("BitRate blocking probability by other" + sep + loadPoint + sep + "all" + sep + "all" + sep + "all" + sep + " ");
			for (Integer replic : replications) {
				res.append(sep + bbps.get(loadPoint).get(replic).getBitRateBlockingByOther());
			}
			res.append("\n");
		}
		return res.toString();
	}

	/**
	 * Returns the bitRate probability by Crosstalk
	 *
	 * @return String
	 */
	private String resultGeneralXt(){
		StringBuilder res = new StringBuilder();
		for (Integer loadPoint : loadPoints) {
			res.append("BitRate blocking probability by Crosstalk" + sep + loadPoint + sep + "all" + sep + "all" + sep + "all" + sep + " ");
			for (Integer replic : replications) {
				res.append(sep + bbps.get(loadPoint).get(replic).getBitRateBlockingByXt());
			}
			res.append("\n");
		}
		return res.toString();
	}

	/**
	 * Returns the bitRate probability by Crosstalk int Others
	 *
	 * @return String
	 */
	private String resultGeneralXtOther(){
		StringBuilder res = new StringBuilder();
		for (Integer loadPoint : loadPoints) {
			res.append("BitRate blocking probability by Crosstalk in Others" + sep + loadPoint + sep + "all" + sep + "all" + sep + "all" + sep + " ");
			for (Integer replic : replications) {
				res.append(sep + bbps.get(loadPoint).get(replic).getBitRateBlockingByXtOther());
			}
			res.append("\n");
		}
		return res.toString();
	}

	/**
	 * Returns the bitRate blocking probability per pair
	 *
	 * @return String
	 */
	private String resultPair(){
		StringBuilder res = new StringBuilder();
		for (Integer loadPoint : loadPoints) {
			String aux = "BitRate blocking probability per pair" + sep + loadPoint + sep + "all";

			for (Pair pair : this.pairs) {
				String aux2 = aux + sep + pair.getSource().getName() + sep + pair.getDestination().getName() + sep + " ";
				for (Integer replic : replications) {
					aux2 = aux2 + sep + bbps.get(loadPoint).get(replic).getProbBlockPair(pair);
				}
				res.append(aux2 + "\n");
			}
		}
		return res.toString();
	}

	/**
	 * Returns the bitRate blocking probability per bitRate
	 *
	 * @return String
	 */
	private String resultBitRate(){
		StringBuilder res = new StringBuilder();
		for (Integer loadPoint : loadPoints) {
			String aux = "BitRate blocking probability per bitRate" + sep + loadPoint;

			for (Double bitRate : bbps.get(0).get(0).getUtil().bitRateList) {
				String aux2 = aux + sep + (bitRate/1000000000.0) + "Gbps" + sep + "all" + sep + "all" + sep + " ";
				for (Integer rep : replications) {
					aux2 = aux2 + sep + bbps.get(loadPoint).get(rep).getProbBlockBitRate(bitRate);
				}
				res.append(aux2 + "\n");
			}
		}
		return res.toString();
	}

	/**
	 * Returns the bitRate blocking probability per pair and bitRate
	 *
	 * @return
	 */
	private String resultPairBandwidth(){
		StringBuilder res = new StringBuilder();
		for (Integer loadPoint : loadPoints) {
			String aux = "BitRate blocking probability per pair and bitRate" + sep + loadPoint;

			for (Double bitRate : bbps.get(0).get(0).getUtil().bitRateList) {
				String aux2 = aux + sep + (bitRate/1000000000.0) + "Gbps";
				for (Pair pair :  bbps.get(0).get(0).getUtil().pairs) {
					String aux3 = aux2 + sep + pair.getSource().getName() + sep + pair.getDestination().getName() + sep + " ";
					for(Integer rep :  replications){
						aux3 = aux3 + sep + bbps.get(loadPoint).get(rep).getProbBlockPairBitRate(pair, bitRate);
					}
					res.append(aux3 + "\n");
				}
			}
		}
		return res.toString();
	}

	/**
	 * Returns the bitRate blocking probability per core
	 *
	 * @return String
	 */
	private String resultBlockPerCore(){
		StringBuilder res = new StringBuilder();
		for(Integer core = 0; core < maxCoresByLinks; core++) {
			for (Integer loadPoint : loadPoints) {
				res.append("BitRate blocking probability per core " + core + sep + loadPoint + sep + "all" + sep + "all" + sep + "all" + sep + " ");
				for (Integer replic : replications) {
					res.append(sep + bbps.get(loadPoint).get(replic).getProbBlockPerCore(core));
				}
				res.append("\n");
			}
		}
		return res.toString();
	}

}
