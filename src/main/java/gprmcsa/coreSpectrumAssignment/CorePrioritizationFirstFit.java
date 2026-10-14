package gprmcsa.coreSpectrumAssignment;

import java.util.ArrayList;
import java.util.List;

import network.Circuit;
import network.ControlPlane;
import network.Core;
import network.Link;
import util.IntersectionFreeSpectrum;

/**
 * Represents the CorePrioritizationFirstFit component.
 */
public class CorePrioritizationFirstFit implements CoreAndSpectrumAssignmentAlgorithmInterface{
	private static final int MAXPESOCORE = 9999;

	private int numberOfCores;
	private int[] coreOfTheTime;
	private int[] pesos;
	private int contador;

	/**
	 * Creates a new instance of CorePrioritizationFirstFit.
	 */
	public CorePrioritizationFirstFit(){
		this.numberOfCores = -1;
	}

	/**
	 * Returns the assign core and spectrum.
	 * @param numberOfSlots the numberOfSlots.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @return true if the condition is met; false otherwise.
	 */
	@Override
	public boolean assignCoreAndSpectrum(int numberOfSlots, Circuit circuit, ControlPlane cp) {
		if (numberOfCores == -1) {
			numberOfCores = cp.getMesh().getLinkList().get(0).getNumberOfCores();

			coreOfTheTime = new int[numberOfCores];
			pesos = new int[numberOfCores];

			for(int i = 0; i < numberOfCores; i++) {
				pesos[i] = 0;
			}

			if (numberOfCores == 7) {
				coreOfTheTime[0] = 6;
				coreOfTheTime[1] = 4;
				coreOfTheTime[2] = 2;
				coreOfTheTime[3] = 3;
				coreOfTheTime[4] = 1;
				coreOfTheTime[5] = 5;
				coreOfTheTime[6] = 0;

			} else if(numberOfCores == 12) {
				coreOfTheTime[0] = 1;
				coreOfTheTime[1] = 2;
				coreOfTheTime[2] = 3;
				coreOfTheTime[3] = 4;
				coreOfTheTime[4] = 5;
				coreOfTheTime[5] = 6;
				coreOfTheTime[6] = 7;
				coreOfTheTime[7] = 8;
				coreOfTheTime[8] = 9;
				coreOfTheTime[9] = 10;
				coreOfTheTime[10] = 11;
				coreOfTheTime[11] = 12;

			} else if(numberOfCores == 19) {
				coreOfTheTime[0] = 3;
				coreOfTheTime[1] = 2;
				coreOfTheTime[2] = 1;
				coreOfTheTime[3] = 6;
				coreOfTheTime[4] = 5;
				coreOfTheTime[5] = 4;
				coreOfTheTime[6] = 7;
				coreOfTheTime[7] = 0;
				coreOfTheTime[8] = 9;
				coreOfTheTime[9] = 8;
				coreOfTheTime[10] = 13;
				coreOfTheTime[11] = 12;
				coreOfTheTime[12] = 14;
				coreOfTheTime[13] = 16;
				coreOfTheTime[14] = 15;
				coreOfTheTime[15] = 14;
				coreOfTheTime[16] = 19;
				coreOfTheTime[17] = 18;
				coreOfTheTime[18] = 17;
			}

			contador = 0;
		}

		int chosenCore = coreAssignment(circuit);
    	List<int[]> composition = IntersectionFreeSpectrum.merge(circuit.getRoute(), circuit.getGuardBand(), chosenCore);

        int chosen[] = policy(numberOfSlots, composition, circuit, cp);
        circuit.setSpectrumAssigned(chosen);
        circuit.setIndexCore(chosenCore);

        //System.out.println("Verificao do xt dos neighbors de:"+circuit.getId());
        //Crosstalk crosstalk = new Crosstalk();
        //crosstalk.isAdmissibleInOthers(circuit);
        //System.out.println("--------------------------------\n\n");

       //System.out.println("\n\nCorePrioritization FF");
       //System.out.println("Selected core: "+chosenCore);
       //System.out.println("Slot range: "+chosen[0]+"-"+chosen[1]);

        //imprimeLog(circuit);

        if (chosen == null)
        	return false;

        return true;
	}

	/**
	 * Returns the core assignment.
	 * @return the result of the operation.
	 */
	@Override
	public int coreAssignment() {
		int escolha;
		escolha = contador;

		if (contador == numberOfCores -1) {
    		contador = 0;
    		return coreOfTheTime[escolha];
    	}else {
    		contador++;
    		return coreOfTheTime[escolha];
    	}
	}

	/**
	 * Returns the core assignment.
	 * @param circuit the circuit.
	 * @return the result of the operation.
	 */
	public int coreAssignment(Circuit circuit) {
		int coreEscolhido = 0;
		int pesoCoreEscolhido = MAXPESOCORE * circuit.getRoute().getLinkList().size();

		// Checking which core has the lowest total weight
		for(int i=numberOfCores-1; i>=0; i--) {
			int pesoCore = 0;

			for(Link link : circuit.getRoute().getLinkList()) {
				pesoCore = pesoCore + link.getCore(i).getPeso();
			}

			if(pesoCore < pesoCoreEscolhido) {
				coreEscolhido = i;
				pesoCoreEscolhido = pesoCore;
			}
		}

		//Updating the weights
		for(Link link : circuit.getRoute().getLinkList()) {
			link.getCore(coreEscolhido).setPeso(MAXPESOCORE);
			link.updatePesosOfCores(coreEscolhido);
		}

		// reset the weights
		for(Link link : circuit.getRoute().getLinkList()) {
			boolean flag = true;

			for(Core core : link.getCores()) {
				if(core.getPeso() < MAXPESOCORE) {
					flag = false;
					break;
				}
			}

			if(flag) {
				//System.out.println("Weight reset on link "+link.getName());
				link.renewAllPesosOfCores();
			}
		}

		// Reset the weights
		//if(pesoCoreEscolhido >= (MAXPESOCORE * circuit.getRoute().getLinkList().size() ) ){
			//for(Link link : circuit.getRoute().getLinkList()) {
				//link.renovaTodosOsPesos();
			//}
		//}

		return coreEscolhido;
	}

	/**
	 * Returns the policy.
	 * @param numberOfSlots the numberOfSlots.
	 * @param freeSpectrumBands the freeSpectrumBands.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @return the result of the operation.
	 */
	@Override
	public int[] policy(int numberOfSlots, List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp) {
		int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
        if(numberOfSlots> maxAmplitude) return null;
    	int chosen[] = null;

        for (int[] band : freeSpectrumBands) {

            if (band[1] - band[0] + 1 >= numberOfSlots) {
                chosen = band.clone();
                chosen[1] = chosen[0] + numberOfSlots - 1;//It is not necessary to allocate the entire band, just the amount of slots required
                break;
            }
        }

       // System.out.println("test2");

        return chosen;
	}

//	private int returnPrioridade() {
//		int core;
//
//		for(int i=6; i>=0; i--) {
//
//		}
//	}

	/**
	 * Executes the increment adjacents operation.
	 * @param id the id.
	 * @param circuit the circuit.
	 */
	private void incrementAdjacents(int id, Circuit circuit) {
		ArrayList<Integer> listof = returnAdjacents(id, circuit);

		for(int i : listof) {
			pesos[i]++;
		}
	}

	/**
	 * Returns the return adjacents.
	 * @param id the id.
	 * @param circuit the circuit.
	 * @return the result of the operation.
	 */
	private ArrayList<Integer> returnAdjacents(int id, Circuit circuit) {
		ArrayList<Integer> listof = new ArrayList<Integer>();

		ArrayList<Core> adjacentCores = circuit.getRoute().getLink(0).getAdjacentCores(id);

		for(int i = 0; i < adjacentCores.size(); i++) {
			listof.add(adjacentCores.get(i).getId());
		}

		return listof;
	}

	/**
	 * Executes the imprime log operation.
	 * @param circuit the circuit.
	 */
	private void imprimeLog(Circuit circuit) {
		System.out.println("\n");

		System.out.println("Optical path ID: "+circuit.getId());
		System.out.println("Optical path route: "+circuit.getRoute().getRouteInString());
		System.out.println("Total route length: "+circuit.getRoute().getDistanceAllLinks());
		System.out.println("Optical path modulation format: "+circuit.getModulation().getName());

		if(circuit.getSpectrumAssigned()==null) {
			System.out.println("Number of slots used by the optical path: --");
			System.out.println("Slots used by the optical path: --");
		}else {
			System.out.println("Number of slots used by the optical path: "+(circuit.getSpectrumAssigned()[1] - circuit.getSpectrumAssigned()[0] + 1));
			System.out.println("Slots used by the optical path: "+circuit.getSpectrumAssigned()[0]+"-"+circuit.getSpectrumAssigned()[1]);
		}

		System.out.println("Core used by the optical path: "+circuit.getIndexCore());
		System.out.println("Crosstalk in this optical path: "+circuit.getXt());
	}
}
