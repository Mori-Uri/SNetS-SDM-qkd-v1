package gprmcsa.reallocation;

import java.util.ArrayList;

import gprmcsa.reallocation.tecniques.PushPull;
import gprmcsa.reallocation.util.SortListCircuits;
import gprmcsa.routing.Route;
import network.Circuit;
import network.ControlPlane;
import network.Link;

/**
 * The trigger is crosstalk blocking. It selects the active circuits that are
 * causing crosstalk on the new circuit to be established. The selected active
 * circuits are reallocated to cores neighboring the new circuit. After each
 * reallocation, the algorithm tries again to serve the new circuit. The
 * procedure finishes once the new circuit is successfully established.
 *
 * @author gustavo
 *
 */
public class PushPull_CSBASDM implements ReallocationAlgorithmInterface {

	// using ArrayList makes list sorting easier
	private ArrayList<Circuit> selectedCircuits;// selected circuits
	private SortListCircuits sortListCircuits;// class for sorting circuit lists
	private PushPull pushPull;

	/**
	 * Creates a new instance of PushPull_CSBASDM.
	 */
	public PushPull_CSBASDM() {
		// this.selectedCircuits = new HashSet<>();
		this.sortListCircuits = new SortListCircuits();
		this.pushPull = new PushPull();
	}

	/**
	 * Selection of circuits that share the links (core) of the circuit route
	 * finalizado.
	 */
	@Override
	public void selectActivesCircuits(ControlPlane cp, Circuit circuitoFinalizado) {

		// create an empty list
		this.selectedCircuits = new ArrayList<>();

		Route rota = circuitoFinalizado.getRoute();
		int indexNucleo = circuitoFinalizado.getIndexCore();
//		System.out.println("Finalized circuit core: "+indexNucleo);
//		System.out.println("Demais cicruitos:");;

		// iterating through the route links
		for (Link enlace : rota.getLinkList()) {
			// percorrendo list of circuits on the core - core fixado
			for (Circuit circuit : enlace.getCore(indexNucleo).getCircuitList()) {
				if (!selectedCircuits.contains(circuit)) {

					selectedCircuits.add(circuit);
//					System.out.println(circuit.getIndexCore());

				}
			}
		}

	}

	/**
	 * Executes the choose new resources for selected circuits operation.
	 * @param cp the cp.
	 */
	@Override
	public void chooseNewResourcesForSelectedCircuits(ControlPlane cp) {
		// TODO Auto-generated method stub

	}

	/**
	 * Executes the traffic migration operation.
	 */
	@Override
	public void trafficMigration() {
		// TODO Auto-generated method stub

	}

	/**
	 * Returns the strategy.
	 * @param circuitoFinalizado the circuitoFinalizado.
	 * @param cp the cp.
	 * @return true if the condition is met; false otherwise.
	 */
	@Override
	public boolean strategy(Circuit circuitoFinalizado, ControlPlane cp) {
		// select circuits
		selectActivesCircuits(cp, circuitoFinalizado);
		// empty list
		if (this.selectedCircuits.size() == 0) {
			return false;
		}

		int indexNucleo = circuitoFinalizado.getIndexCore();

		if (indexNucleo == 1 || indexNucleo == 3 || indexNucleo == 5) {
			// sorting the circuit list
			sortListCircuits.sortIndexSlot(this.selectedCircuits, "indiceslotcrescente");

//			System.out.println("List 1: "+this.selectedCircuits.size());
//			imprimeListaCircuitos();
//			System.out.println("***************************");

			for (Circuit circuit : this.selectedCircuits) {

				try {
					pushPull.executeDown(circuit, cp);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

		}
		if (indexNucleo == 2 || indexNucleo == 4 || indexNucleo == 6) {
			// sorting the circuit list
			sortListCircuits.sortIndexSlot(selectedCircuits, "indiceslotdecrescente");

			for (Circuit circuit : this.selectedCircuits) {

				try {
					pushPull.executeUpper(circuit, cp);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

//		System.out.println("Finished");
//		System.out.println();

		return false;

	}

	/**
	 * Prints the circuit list.
	 * @param cp the cp.
	 */
	private void imprimeListaCircuitos(ControlPlane cp) {
		String criterio = "core";
		System.out.println(" CIRCUITS ");

		if (criterio.equals("hops")) {
			for (Circuit circuit : selectedCircuits) {
				System.out.println(circuit.getRoute().getHops());
			}
		}

		if (criterio.equals("distance")) {
			for (Circuit circuit : selectedCircuits) {
				System.out.println(circuit.getRoute().getDistanceAllLinks());
			}
		}
		if (criterio.equals("crosstalk")) {
			for (Circuit circuit : selectedCircuits) {
				System.out.println(circuit.getXt());
			}
		}
		if (criterio.equals("deltacrosstalk")) {
			for (Circuit circuit : selectedCircuits) {
				System.out.println((-1) * (circuit.getXt() - circuit.getModulation().getXTthreshold()));
			}
		}
		if (criterio.equals("recurso")) {
			for (Circuit circuit : selectedCircuits) {
				System.out.println(circuit.getRoute().getHops() * circuit.getSpectrumAssigned().length);
			}
		}
		if (criterio.equals("indiceslot")) {
			for (Circuit circuit : selectedCircuits) {
				System.out.println(circuit.getSpectrumAssigned()[0]);
			}
		}
		if (criterio.equals("core")) {
			for (Circuit circuit : selectedCircuits) {
				System.out.println(circuit.getIndexCore());
			}
		}

	}

	/**
	 * Prints the circuit data.
	 * @param c the c.
	 */
	private void imprimeDadosCircuito(Circuit c) {
		// TODO Auto-generated method stub
		System.out.println("Circuit: " + c);
		System.out.println("Spectrum: " + c.getSpectrumAssigned()[0] + "-" + c.getSpectrumAssigned()[1]);
		System.out.println("Modulation: " + c.getModulation());
		System.out.println("Crosstalk: " + c.getXt());
		System.out.println("Core: " + c.getIndexCore());
		System.out.println("-------------------------");
	}

	/**
	 * Returns the circuit copy.
	 * @param c the c.
	 * @return the result of the operation.
	 */
	private Circuit copiaCircuito(Circuit c) {
		// TODO Auto-generated method stub
		Circuit cCopia = new Circuit();

		cCopia.setPair(c.getPair());
		cCopia.setRequests(c.getRequests());

		cCopia.setRoute(c.getRoute());
		cCopia.setModulation(c.getModulation());
		cCopia.setGuardBand(c.getGuardBand());
		cCopia.setIndexCore(c.getIndexCore());
		cCopia.setSpectrumAssigned(c.getSpectrumAssigned());

		cCopia.setSNR(c.getSNR());
		cCopia.setQoT(c.isQoT());
		cCopia.setQoTForOther(c.isQoTForOther());
		cCopia.setXt(c.getXt());
		cCopia.setXtAdmissible(c.getXtAdmissible());
		cCopia.setXtAdmissibleInOther(c.getXtAdmissibleInOther());

		return cCopia;

	}

}
