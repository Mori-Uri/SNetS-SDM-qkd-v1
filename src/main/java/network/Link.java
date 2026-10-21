package network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import simulationControl.parsers.NetworkConfig.CoreConfig;

/**
 * Directed optical link in the SNetS topology.
 *
 * <p>Part 4 preserves the original public API and additionally propagates the
 * QKD reservation flag from {@link CoreConfig} to the runtime {@link Core}
 * objects. No classical node/link topology is changed.</p>
 */
public class Link implements Serializable {

    private static final long serialVersionUID = 1L;

    private ArrayList<Core> cores;
    private Oxc source;
    private Oxc destination;
    private double cost;
    private double distance;

    public Link(Oxc source, Oxc destination, int numOfSlots,
            double slotSpectrumBand, double distance,
            List<CoreConfig> coreConfigs) {
        this.source = source;
        this.destination = destination;
        this.distance = distance;
        startCores(coreConfigs, numOfSlots, slotSpectrumBand);
    }

    public boolean isAdjacentOxc(Oxc oxc) {
        return destination == oxc;
    }

    public Oxc getDestination() {
        return destination;
    }

    public void setDestination(Oxc destination) {
        this.destination = destination;
    }

    public void setSource(Oxc source) {
        this.source = source;
    }

    public Oxc getSource() {
        return source;
    }

    public double getCost() {
        return cost;
    }

    public void setCost(double cost) {
        this.cost = cost;
    }

    public double getDistance() {
        return distance;
    }

    public String getName() {
        return "<" + getSource().getName() + "," + getDestination().getName()
                + ">";
    }

    /** Builds runtime cores and then wires their adjacency relationships. */
    private void startCores(List<CoreConfig> coreConfigs, int numOfSlots,
            double slotSpectrumBand) {
        cores = new ArrayList<Core>();

        for (CoreConfig coreConfig : coreConfigs) {
            final Core core = new Core(
                    coreConfig.getId(),
                    numOfSlots,
                    slotSpectrumBand,
                    coreConfig.isQkdReserved());
            core.setDisabled(coreConfig.isDisabled());

            /*
             * The original SNetS stores a core at the list index equal to its
             * id. Existing scenarios use contiguous ids (0..N-1), so this
             * preserves the original behavior exactly.
             */
            cores.add(coreConfig.getId(), core);
        }

        for (CoreConfig coreConfig : coreConfigs) {
            final ArrayList<Core> adjacent = new ArrayList<Core>();
            final List<Integer> adjacentIds = coreConfig.getAdjacentCores();
            for (Integer adjacentId : adjacentIds) {
                adjacent.add(getCore(adjacentId.intValue()));
            }
            getCore(coreConfig.getId()).setAdjacentCores(adjacent);
        }
    }

    public Core getCore(int id) {
        for (Core core : cores) {
            if (core.getId() == id) {
                return core;
            }
        }
        return null;
    }

    public ArrayList<Core> getAdjacentCores(int coreId) {
        return getCore(coreId).getAdjacentCores();
    }

    /** Physical number of slots across all cores, including the QKD core. */
    public int totalNumberOfSlots() {
        int slots = 0;
        for (Core core : cores) {
            slots += core.getNumOfSlots();
        }
        return slots;
    }

    /** Number of slots available to the classical subsystem only. */
    public int totalNumberOfClassicalSlots() {
        int slots = 0;
        for (Core core : cores) {
            if (!core.isQkdReserved()) {
                slots += core.getNumOfSlots();
            }
        }
        return slots;
    }

    /** Original physical-core view retained for compatibility. */
    public ArrayList<Core> getCores() {
        return cores;
    }

    /** Returns a copy containing only cores available to classical traffic. */
    public ArrayList<Core> getClassicalCores() {
        final ArrayList<Core> result = new ArrayList<Core>();
        for (Core core : cores) {
            if (!core.isQkdReserved()) {
                result.add(core);
            }
        }
        return result;
    }

    /** Returns a copy containing cores dedicated to QKD (one in Part 4). */
    public ArrayList<Core> getQkdReservedCores() {
        final ArrayList<Core> result = new ArrayList<Core>();
        for (Core core : cores) {
            if (core.isQkdReserved()) {
                result.add(core);
            }
        }
        return result;
    }

    /** Convenience accessor for the current single-QKD-core model. */
    public Core getQkdReservedCore() {
        for (Core core : cores) {
            if (core.isQkdReserved()) {
                return core;
            }
        }
        return null;
    }

    public boolean hasQkdReservedCore() {
        return getQkdReservedCore() != null;
    }

    public void updatePesosOfCores(int idCore) {
        final ArrayList<Core> adjacent = getAdjacentCores(idCore);
        for (Core core : adjacent) {
            core.incrementPeso();
        }
    }

    public void renewAllPesosOfCores() {
        for (Core core : cores) {
            core.renewPeso();
        }
    }

    /** Physical number of cores, preserving the original SNetS meaning. */
    public int getNumberOfCores() {
        return cores.size();
    }

    /** Number of cores available for classical allocations. */
    public int getNumberOfClassicalCores() {
        return getClassicalCores().size();
    }
}
