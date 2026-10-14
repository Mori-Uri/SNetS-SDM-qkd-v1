package gprmcsa.reallocation;

import java.util.ArrayList;

import gprmcsa.reallocation.tecniques.FastSwitching;
import gprmcsa.reallocation.util.SortListCircuits;
import network.Circuit;
import network.ControlPlane;
import network.Core;
import network.Link;

/**
 * O gatilho  o blocking do crosstalk Seleciona os circuitos ativos que estao
 * causing crosstalk on the new circuit to be established. Reallocates the circuit
 * active to cores in the neighbors of the new circuit. At each reallocation, it tries to
 * serve the new circuit. When the new circuit has been served, after
 * realocacoes, o algorithm  finalizado
 *
 * Difenrecial do Alfa_v1 e Alfa_v2:
 *
 * Do not try to re-establish the blocked request. Considers all cores for
 * the circuit reallocation
 *
 * @author gustavo
 *
 */
public class FastSwitching_Alfa_v3 implements ReallocationAlgorithmInterface {

	// using ArrayList makes list sorting easier
	private ArrayList<Circuit> selectedCircuits;
	private SortListCircuits sortListCircuits;
	private FastSwitching fastSwitching;

	/**
	 * Creates a new instance of FastSwitching_Alfa_v3.
	 */
	public FastSwitching_Alfa_v3() {
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
		ArrayList<Integer> cores = new ArrayList<>();

		// select circuits
		this.selectActivesCircuits(cp, requisicao);

		// no circuit was selected for reallocation
		if (this.selectedCircuits.size() == 0) {
			return false;
		}

		// adding cores 1 through 6 to the list
		for (int i = 1; i <= 6; i++) {
			cores.add(i);
		}
		// placing core 0 in the last position
		cores.add(0);

		// iterating over the circuit list to try reallocation
		// try to reallocate to a non-neighboring core

		for (Circuit circuit : this.selectedCircuits) {

			for (int core : cores) {

				try {

					boolean sucess = this.fastSwitching.execute(circuit, core, cp);

					// successful reallocation - there is no need to try reallocating to other cores
					if (sucess) {
//						System.out.println("sucesso na reallocation");

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
