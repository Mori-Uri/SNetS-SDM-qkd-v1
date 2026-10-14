package gprmcsa.reallocation;

import java.util.ArrayList;

import gprmcsa.reallocation.tecniques.FastSwitching;
import gprmcsa.reallocation.util.SortListCircuits;
import network.Circuit;
import network.ControlPlane;
import network.Core;
import network.Link;

/**
 * The trigger is crosstalk blocking. It selects the active circuits that are
 * causing crosstalk on the new circuit to be established. The selected active
 * circuits are reallocated to cores neighboring the new circuit. After each
 * reallocation, the algorithm tries again to serve the new circuit. The
 * procedure finishes once the new circuit is successfully established.
 *
 * Difference from Alfa_v1:
 *
 * Do not try to re-establish the blocked request.
 * It considers only non-adjacent cores of the blocked request for circuit reallocation.
 *
 * @author gustavo
 *
 */
public class FastSwitching_Alfa_v2 implements ReallocationAlgorithmInterface {

	// using ArrayList makes list sorting easier
	private ArrayList<Circuit> selectedCircuits;
	private ArrayList<Core> coresAdjacents;// adjacent cores
	private ArrayList<Core> coresNoAdjacents;// non-adjacent cores other than the center core
	private SortListCircuits sortListCircuits;
	private FastSwitching fastSwitching;

	/**
	 * Creates a new instance of FastSwitching_Alfa_v2.
	 */
	public FastSwitching_Alfa_v2() {
		this.sortListCircuits = new SortListCircuits();
		this.fastSwitching = new FastSwitching();
	}

	/**
	 * Selects the actives circuits.
	 * @param cp the cp.
	 * @param request the request.
	 */
	@Override
	public void selectActivesCircuits(ControlPlane cp, Circuit request) {

		this.selectedCircuits = new ArrayList<>();

		// index of the new circuit core
		int indiceNucleo = request.getIndexCore();

		/*
		 * Circuits that are on neighboring cores of the new circuit core. Selection
		 * of circuits that share spectrum, that is, those that cause interference
		 */
		for (Link enlace : request.getRoute().getLinkList()) {
			for (Core core : enlace.getAdjacentCores(indiceNucleo)) {
				// adding the adjacent core to the list for future use
				for (Circuit circuit : core.getCircuitList()) {
					// checks whether the circuit is already in the list of selected circuits; if not,
					// add them
					// check whether the active circuit shares slots with the new circuit
					if (!selectedCircuits.contains(circuit)) {
						if (espectroEmComum(request, circuit)) {
							selectedCircuits.add(circuit);
						}

					}
				}

			}

		}

		// sorting the selected circuit list
		this.sortListCircuits.sort(selectedCircuits, "hops", cp);

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
	 * @param requisicao the request.
	 * @param cp the cp.
	 * @return true if the condition is met; false otherwise.
	 */
	@Override
	public boolean strategy(Circuit requisicao, ControlPlane cp) {

		this.coresAdjacents = new ArrayList<>();
		this.coresNoAdjacents = new ArrayList<>();

		// do not perform fast switching when the request is in the central core
		if (requisicao.getIndexCore() == 0) {
			return false;
		}

		// select circuits
		this.selectActivesCircuits(cp, requisicao);

		// no circuit was selected for reallocation
		if (this.selectedCircuits.size() == 0) {
			return false;
		}

		// list of adjacent cores
		this.coresAdjacents = requisicao.getRoute().getLinkList().get(0).getAdjacentCores(requisicao.getIndexCore());

		// populate the list of non-adjacent cores
		ArrayList<Core> coreList = requisicao.getRoute().getLinkList().get(0).getCores();
		for (Core c : coreList) {
			if (!coresAdjacents.contains(c) && c.getId() != requisicao.getIndexCore()) {
				this.coresNoAdjacents.add(c);
			}
		}

		// iterating over the circuit list to try reallocation
		// try to reallocate to a non-neighboring core

		for (Circuit circuit : this.selectedCircuits) {

			for (Core coreNoAdjacent : this.coresNoAdjacents) {

				try {

					boolean sucess = this.fastSwitching.execute(circuit, coreNoAdjacent.getId(), cp);

					// successful reallocation - there is no need to try reallocating to other cores
					if (sucess) {

						break;// to process the next circuit, since the circuit was reallocated
					}

				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}

		return false;

	}

	/**
	 * checks se tem slots em comum entre dois circuitos de cores diferentes
	 *
	 */
	protected boolean espectroEmComum(Circuit novoC, Circuit c) {

		int[] espectroNovoC = novoC.getSpectrumAssigned();
		int[] espectro = c.getSpectrumAssigned();

		for (int i = espectroNovoC[0]; i <= espectroNovoC[1]; i++) {
			if (i >= espectro[0] && i <= espectro[1]) {
				return true;
			}
		}

		return false;

	}

}
