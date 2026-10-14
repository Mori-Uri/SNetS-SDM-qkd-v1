package gprmcsa.reallocation.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import network.Circuit;
import network.ControlPlane;

/**
 * Class intended to sort circuits according to different criteria
 *
 * @author gustavo
 *
 */
public class SortListCircuits {

	/**
	 * Construtor
	 */
	public SortListCircuits() {
		//
	}

	/**
	 * sorting list according to some criterion
	 */
	public void sort(ArrayList<Circuit> circuits, String criterion, ControlPlane cp) {

		Collections.sort(circuits, new Comparator<Circuit>() {
			/**
			 * Returns the compare.
			 * @param o1 the o1.
			 * @param o2 the o2.
			 * @return the result of the operation.
			 */
			@Override
			public int compare(Circuit o1, Circuit o2) {
				return auxCost(o1).compareTo(auxCost(o2));
			}

			/**
			 * Returns the aux cost.
			 * @param le the le.
			 * @return the result of the operation.
			 */
			private Double auxCost(Circuit le) {

				// route hops
				if (criterion.equals("hops")) {
					return (double) le.getRoute().getHops();
				}
				// route distance
				if (criterion.equals("distance")) {
					return le.getRoute().getDistanceAllLinks();
				}
				// circuit crosstalk value
				if (criterion.equals("crosstalk")) {
					return le.getXt();
				}
				// delta crosstalk value of the circuit
				if (criterion.equals("deltacrosstalk")) {
					return (-1) * (le.getXt() - le.getModulation().getXTthreshold());
				}
				// resource used in the circuit (number of slots x route hops)
				return (double) (le.getRoute().getHops() * le.getSpectrumAssigned().length);

			}
		});
	}

	/**
	 * Sorts the index slot.
	 * @param circuits the circuits.
	 * @param criterion the criterion.
	 */
	public void sortIndexSlot(ArrayList<Circuit> circuits, String criterion) {

		Collections.sort(circuits, new Comparator<Circuit>() {
			/**
			 * Returns the compare.
			 * @param o1 the o1.
			 * @param o2 the o2.
			 * @return the result of the operation.
			 */
			@Override
			public int compare(Circuit o1, Circuit o2) {
				return auxCost(o1).compareTo(auxCost(o2));
			}

			/**
			 * Returns the aux cost.
			 * @param le the le.
			 * @return the result of the operation.
			 */
			private Integer auxCost(Circuit le) {
				// ascending slot index
				if (criterion.equals("indiceslotcrescente")) {
					return le.getSpectrumAssigned()[0];
				}
				// descending slot index
				if (criterion.equals("indiceslotdecrescente")) {
					return le.getRoute().getLink(0).getCore(0).getNumOfSlots() - le.getSpectrumAssigned()[0];
				}

				// number of slots of the circuit
				return le.getSpectrumAssigned().length;

			}
		});
	}

	/**
	 * Prints the circuit list.
	 * @param circuits the circuits.
	 * @param cp the cp.
	 */
	private void printCircuitList(ArrayList<Circuit> circuits, ControlPlane cp) {
		String criterio = "deltacrosstalk";
		System.out.println(" CIRCUITS ");

		if (criterio.equals("hops")) {
			for (Circuit circuit : circuits) {
				System.out.println(circuit.getRoute().getHops());
			}
		}

		if (criterio.equals("distance")) {
			for (Circuit circuit : circuits) {
				System.out.println(circuit.getRoute().getDistanceAllLinks());
			}
		}
		if (criterio.equals("crosstalk")) {
			for (Circuit circuit : circuits) {
				System.out.println(circuit.getXt());
			}
		}
		if (criterio.equals("deltacrosstalk")) {
			for (Circuit circuit : circuits) {
				System.out.println((-1) * (circuit.getXt() - circuit.getModulation().getXTthreshold()));
			}
		}
		if (criterio.equals("recurso")) {
			for (Circuit circuit : circuits) {
				System.out.println(circuit.getRoute().getHops() * circuit.getSpectrumAssigned().length);
			}
		}

	}

	/**
	 * Prints the circuit data.
	 * @param c the c.
	 */
	private void printCircuitData(Circuit c) {
		// TODO Auto-generated method stub
		System.out.println("Circuit: " + c);
		System.out.println("Spectrum: " + c.getSpectrumAssigned()[0] + "-" + c.getSpectrumAssigned()[1]);
		System.out.println("Modulation: " + c.getModulation());
		System.out.println("Crosstalk: " + c.getXt());
		System.out.println("Core: " + c.getIndexCore());
		System.out.println("-------------------------");
	}

}
