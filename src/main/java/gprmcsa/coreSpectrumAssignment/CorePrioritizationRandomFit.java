package gprmcsa.coreSpectrumAssignment;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import network.Circuit;
import network.ControlPlane;
import network.Core;
import network.Link;
import util.IntersectionFreeSpectrum;

/**
 * Represents the CorePrioritizationRandomFit component.
 */
public class CorePrioritizationRandomFit implements CoreAndSpectrumAssignmentAlgorithmInterface{
	private static final int MAXPESOCORE = 9999;

	private int numberOfCores;
	private int[] coreOfTheTime;
	private int[] pesos;
	private int contador;

	/**
	 * Creates a new instance of CorePrioritizationRandomFit.
	 */
	public CorePrioritizationRandomFit(){
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

        //Crosstalk crosstalk = cp.getMesh().getPhysicalLayer().getCrosstalk();
       // System.out.println(crosstalk.isAdmissibleInOthers(circuit));
//
      // System.out.println("\n\nCorePrioritization RF");
       //System.out.println("preciso de "+numberOfSlots+" slots");
       //System.out.println("Selected core: "+chosenCore);
       //System.out.println("Slot range: "+chosen[0]+"-"+chosen[1]);

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
		ArrayList<int[]> bandList = new ArrayList<int[]>();

		for (int[] band : freeSpectrumBands) { //checks and guard the free bands that can establish the requisition
			if(band[1] - band[0] + 1 >= numberOfSlots){
				int faixaTemp[] = band.clone();
				bandList.add(faixaTemp);
			}
		}

		if(bandList.size() > 0){ //if you have free bands, choose one randomly
			Random rand = new Random();
			int indexBand = rand.nextInt(bandList.size());
			chosen = bandList.get(indexBand);
			chosen[1] = chosen[0] + numberOfSlots - 1; //it is not necessary to allocate the entire band, only the number of slots necessary
		}

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
}
