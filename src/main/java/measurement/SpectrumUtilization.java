package measurement;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

import network.ControlPlane;
import network.Core;
import network.Link;
import network.Mesh;
import request.RequestForConnection;
import simulationControl.parsers.SimulationRequest;
import simulationControl.resultManagers.SpectrumUtilizationResultManager;

/**
 * This class stored the metrics related to the use of spectrum.
 *
 * @author Iallen
 */
public class SpectrumUtilization extends Measurement {
    public final static String SEP = "-";

    private Mesh mesh;

    private double utilizationGen;
    private int numberObservations;

    private HashMap<String, Double> utilizationPerLink;
    private int[] desUtilizationPerSlot;
    private Integer maxSlotsByLinks;

    private int maxCoresByLinks;
    private HashMap<Integer, Double> utilizationPerCore;
    private HashMap<String, HashMap<Integer, Double>> utilizationPerLinkCore;

    /** Part 27: count of observations in which [link][core][slot] was free (link order of the mesh). */
    private int[][][] freePerLinkCoreSlot;
    private String[] linkNames;

    /**
     * Creates a new instance of SpectrumUtilization
     *
     * @param loadPoint int
     * @param replication int
     * @param mesh Mesh
     */
    public SpectrumUtilization(int loadPoint, int replication, Mesh mesh) {
        super(loadPoint, replication);

        this.mesh = mesh;
        utilizationGen = 0.0;
        numberObservations = 0;
        utilizationPerLink = new HashMap<String, Double>();

        maxSlotsByLinks = mesh.maximumSlotsByLinks();
        desUtilizationPerSlot = new int[maxSlotsByLinks];

        this.maxCoresByLinks = 0;
        this.utilizationPerCore = new HashMap<>();
        this.utilizationPerLinkCore = new HashMap<>();

		resultManager = new SpectrumUtilizationResultManager();
    }

    /**
     * Adds a new usage observation of spectrum utilization
     *
     * @param cp ControlPlane
     * @param success boolean
     * @param request RequestForConnection
     */
    public void addNewObservation(ControlPlane cp, boolean success, RequestForConnection request) {
    	if(maxCoresByLinks == 0) {
        	maxCoresByLinks = cp.getMesh().maximumCoresByLinks();

        	for(Integer core = 0; core < maxCoresByLinks; core++) {
        		this.utilizationPerCore.put(core, 0.0);
        	}
        }

    	this.newObsUtilization();
    }

    /**
     * Returns the file name.
     * @return the file name.
     */
    @Override
    public String getFileName() {
        return SimulationRequest.Result.FILE_SPECTRUM_UTILIZATION;
    }

    /**
     * Observation of the use of the spectrum resource of the network
     */
    private void newObsUtilization() {
        // General use and per link
        Double utGeral = 0.0;
        int linkIndex = -1;

        for (Link link : mesh.getLinkList()) {
        	linkIndex++;
        	String linkName = link.getSource().getName() + SEP + link.getDestination().getName();
        	Double utLinkAllCores = 0.0;

        	for (Core core : link.getCores()) {
        		Integer coreId = core.getId();
        		Double coreUtilization = core.getUtilization();

        		utLinkAllCores += coreUtilization;

	            // Calculates the non-utilization of slots
	            for (int[] faixa : core.getFreeSpectrumBands(0)) {
	                incrementarDesUtFaixa(faixa);
	            }
	            // Part 27: real occupancy of each slot (a QKD-reserved core is read through its QKD view,
	            // since the classical view shows it as fully occupied); guard bands count as occupied
	            int[] freeSlot = freeSlotsOf(linkIndex, link, coreId);
	            if (freeSlot != null) {
	                List<int[]> bands = core.isQkdReserved() ? core.getFreeSpectrumBandsForQKD(0) : core.getFreeSpectrumBands(0);
	                for (int[] faixa : bands) {
	                    for (int i = faixa[0] - 1; i < faixa[1] && i < freeSlot.length; i++) freeSlot[i]++;
	                }
	            }

	            // Increment the utilization per core
	            Double utCore = utilizationPerCore.get(coreId);
	            if(utCore == null) utCore = 0.0;
	            utCore += coreUtilization / (double) mesh.getLinkList().size(); //There is the same core index for all links
	            utilizationPerCore.put(coreId, utCore);

	            // Increment the utilization for link and core
	            HashMap<Integer, Double> uplc = this.utilizationPerLinkCore.get(linkName);
	            if (uplc == null) {
	            	uplc = new HashMap<>();
	                this.utilizationPerLinkCore.put(linkName, uplc);
	            }
	            Double utLinkCore = uplc.get(coreId);
	            if (utLinkCore == null) utLinkCore = 0.0;
	            uplc.put(coreId, utLinkCore + coreUtilization);
        	}

        	utLinkAllCores = utLinkAllCores / (double) link.getCores().size();
        	utGeral += utLinkAllCores;

        	// Increment the utilization per link
            Double utLink = this.utilizationPerLink.get(linkName);
            if (utLink == null) utLink = 0.0;
            this.utilizationPerLink.put(linkName, utLink + utLinkAllCores);
        }

        utGeral = utGeral / (double) mesh.getLinkList().size();

        this.utilizationGen += utGeral;
        this.numberObservations++;
    }

    /** Part 27: the per-slot free counters of a link and core, created on the first observation. */
    private int[] freeSlotsOf(int linkIndex, Link link, int coreId) {
        if (freePerLinkCoreSlot == null) {
            int n = mesh.getLinkList().size();
            freePerLinkCoreSlot = new int[n][][];
            linkNames = new String[n];
        }
        if (linkIndex < 0 || linkIndex >= freePerLinkCoreSlot.length || coreId < 0) return null;
        if (freePerLinkCoreSlot[linkIndex] == null) {
            int cores = 0;
            for (Core c : link.getCores()) cores = Math.max(cores, c.getId() + 1);
            freePerLinkCoreSlot[linkIndex] = new int[cores][];
            linkNames[linkIndex] = link.getSource().getName() + SEP + link.getDestination().getName();
        }
        int[][] perCore = freePerLinkCoreSlot[linkIndex];
        if (coreId >= perCore.length) return null;
        if (perCore[coreId] == null) perCore[coreId] = new int[maxSlotsByLinks];
        return perCore[coreId];
    }

    /**
     * Part 27: appends the rows of SlotUtilization.csv of this load point and replication:
     * metric, load point, &lt;link&gt;, core, replication and the occupancy (1 - free fraction) of each slot.
     */
    public void appendSlotUtilizationRows(StringBuilder sb, int slots) {
        if (freePerLinkCoreSlot == null || numberObservations == 0) return;
        for (int l = 0; l < freePerLinkCoreSlot.length; l++) {
            int[][] perCore = freePerLinkCoreSlot[l];
            if (perCore == null) continue;
            for (int c = 0; c < perCore.length; c++) {
                int[] free = perCore[c];
                if (free == null) continue;
                sb.append("Slot utilization").append(',').append(getLoadPoint()).append(",<").append(linkNames[l]).append(">,")
                  .append(c).append(',').append(getReplication());
                for (int s = 0; s < slots; s++) {
                    sb.append(',');
                    if (s >= free.length) { sb.append('0'); continue; }
                    double u = 1.0 - (double) free[s] / (double) numberObservations;
                    sb.append(compact(u));
                }
                sb.append('\n');
            }
        }
    }

    private static String compact(double v) {
        if (v <= 0.0) return "0";
        if (v >= 1.0) return "1";
        String s = String.format(java.util.Locale.US, "%.4f", v);
        int e = s.length();
        while (e > 0 && s.charAt(e - 1) == '0') e--;
        if (e > 0 && s.charAt(e - 1) == '.') e--;
        return e == 0 ? "0" : s.substring(0, e);
    }

    /**
	 * This method increases slot non-utilization
	 *
	 * @param band int[]
	 */
    private void incrementarDesUtFaixa(int faixa[]) {
        for (int i = faixa[0] - 1; i < faixa[1]; i++) {
            desUtilizationPerSlot[i]++;
        }
    }

    /**
     * Returns the HashMap key set
     * The key set corresponds to the links that were analyzed by the metric
     *
     * @return
     */
    public Set<String> getLinkSet() {
        return this.utilizationPerLink.keySet();
    }

    /**
     * Returns the utilization
     *
     * @return
     */
    public double getUtilizationGen() {
        return this.utilizationGen / (double) this.numberObservations;
    }

    /**
     * Returns the utilization for a given link passed by parameter
     *
     * @param link
     * @return
     */
    public double getUtilizationPerLink(String link) {
        Double utLink = this.utilizationPerLink.get(link);
        if (utLink == null) {
        	return 0.0;
        }
        return (utLink / (double) this.numberObservations);
    }

    /**
     * Returns the utilization for a given slot passed by parameter
     *
     * @param Slot
     * @return double
     */
    public double getUtilizationPerSlot(int Slot) {
        double desUt = (double) desUtilizationPerSlot[Slot - 1] / ((double) this.numberObservations * mesh.getLinkList().size() * maxCoresByLinks);

        return 1 - desUt;
    }

    /**
     * Returns the maximum slots by links
     *
     * @return int
     */
    public int getMaxSlotsByLinks(){
    	return maxSlotsByLinks;
    }

    /**
     * Returns the maximum number of cores between all links
     *
     * @return int
     */
    public int getMaxCoresByLinks() {
    	return maxCoresByLinks;
    }

    /**
     * Returns the utilization for a specific core
     *
     * @param core Integer
     * @return double
     */
    public double getUtilizationPerCore(Integer core) {
        Double upc = this.utilizationPerCore.get(core);
        if(upc == null) {
        	return 0.0;
        }
        return (upc / (double) this.numberObservations);
	}

    /**
     * Returns the utilization per link and core
     *
     * @return double
     */
    public double getUtilizationForLinkAndCore(String link, Integer core) {
    	HashMap<Integer, Double> utLinkCore = utilizationPerLinkCore.get(link);

    	if(utLinkCore == null || utLinkCore.get(core) == null) { // No utilization for this link and core
    		return 0.0;
    	}

    	Double utCore = utLinkCore.get(core);

        return (utCore / (double) this.numberObservations);
	}

}
