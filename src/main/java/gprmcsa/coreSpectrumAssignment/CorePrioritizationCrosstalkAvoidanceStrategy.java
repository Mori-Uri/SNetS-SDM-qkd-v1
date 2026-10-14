package gprmcsa.coreSpectrumAssignment;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import network.Circuit;
import network.ControlPlane;
import network.Core;
import network.Link;
import util.IntersectionFreeSpectrum;

// CAS algorithm, proposed in LIU 2020.
// Adaptao da estratgia XT avoid de LIU 2020.
// Paper: Routing Core and Spectrum Allocation Algorithm for Inter-Core Crosstalk and Energy Efficiency in Space Division Multiplexing Elastic Optical Networks
// A IMPLEMENTAO A SEGUIR  VLIDA APENAS PARA UMA FIBRA DE 7 NCLEOS E 320 SLOTS EM CADA NCLEO.
/**
 * Represents the CorePrioritizationCrosstalkAvoidanceStrategy component.
 */
public class CorePrioritizationCrosstalkAvoidanceStrategy implements CoreAndSpectrumAssignmentAlgorithmInterface {

	private static final int MAXPESOCORE = 99999;
	private int si; //slot inicial
	private int f; // total number of slots in the core
	private int c; // total number of cores
	private int gi; // total number of cores in group i
	private int eg1[]; // Priority spectrum of group 1
	private int eg2[]; // Priority spectrum of group 2
	private int eg3[]; // Priority spectrum of group 3
	private List<Integer> g1; // Cores that belong to group 1
	private List<Integer> g2; // Cores that belong to group 2
	private List<Integer> g3; // Cores that belong to group 3

	/**
	 * Creates a new instance of CorePrioritizationCrosstalkAvoidanceStrategy.
	 */
	public CorePrioritizationCrosstalkAvoidanceStrategy() {
		this.si = 1;
		this.f = 320;
		this.c = 7;

		this.eg1 = new int[2];
		this.eg2 = new int[2];
		this.eg3 = new int[2];

		this.eg1[0] = 1;
		this.eg1[1] = 137;

		this.eg2[0] = 138;
		this.eg2[1] = 274;

		this.eg3[0] = 275;
		this.eg3[1] = 320;

		this.g1 = new ArrayList<>();
		this.g1.add(1);
		this.g1.add(3);
		this.g1.add(5);

		this.g2 = new ArrayList<>();
		this.g2.add(2);
		this.g2.add(4);
		this.g2.add(6);

		this.g3 = new ArrayList<>();
		this.g3.add(0);
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
		this.c = cp.getMesh().getLinkList().get(0).getNumberOfCores();

		int chosenCore = coreAssignment(circuit);
		int chosen[] = null;
		//int contadorF = numberOfSlots;

    	List<int[]> compositionantiga = IntersectionFreeSpectrum.merge(circuit.getRoute(), circuit.getGuardBand(), chosenCore);

    	if(g1.contains(chosenCore)) {
    		chosen = alocacaoGrupo1(numberOfSlots, compositionantiga, circuit, cp);
    	}

    	if(g2.contains(chosenCore)) {
    		chosen = alocacaoGrupo2(numberOfSlots, compositionantiga, circuit, cp);
    	}

    	if(g3.contains(chosenCore)) {
    		chosen = alocacaoGrupo3(numberOfSlots, compositionantiga, circuit, cp);
    	}

    	circuit.setSpectrumAssigned(chosen);
        circuit.setIndexCore(chosenCore);


        if (chosen == null) {
        	return false;
        }

        return true;
	}

	/**
	 * Returns the core assignment.
	 * @return the result of the operation.
	 */
	@Override
	public int coreAssignment() {
		Random generator = new Random();
		int a = generator.nextInt(this.c);
		return a;
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
		for(int i=6; i>=0; i--) {
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

	//Firt fit
	/**
	 * Returns the policy.
	 * @param numberOfSlots the numberOfSlots.
	 * @param freeSpectrumBands the freeSpectrumBands.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @return the result of the operation.
	 */
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

        return chosen;
	}

	/**
	 * Returns the alocacao grupo1.
	 * @param numberOfSlots the numberOfSlots.
	 * @param freeSpectrumBands the freeSpectrumBands.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @return the result of the operation.
	 */
	public int[] alocacaoGrupo1(int numberOfSlots, List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp) {
		int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
        if(numberOfSlots> maxAmplitude) return null;
    	int chosen[] = null;

    	//Priority group
    	List<int[]> composition1 = novaComposicao1(freeSpectrumBands, circuit, cp);
    	chosen = policy(numberOfSlots, composition1, circuit, cp);
        if (chosen!=null){return chosen;}

        //Priority group 2
        List<int[]> composition2 = novaComposicao2(freeSpectrumBands, circuit, cp);
    	chosen = policy(numberOfSlots, composition2, circuit, cp);
        if (chosen!=null){return chosen;}

        //Lowest-priority group
        List<int[]> composition3 = novaComposicao3(freeSpectrumBands, circuit, cp);
    	chosen = policy(numberOfSlots, composition3, circuit, cp);
        if (chosen!=null){return chosen;}

        return chosen;
	}

	/**
	 * Returns the alocacao grupo2.
	 * @param numberOfSlots the numberOfSlots.
	 * @param freeSpectrumBands the freeSpectrumBands.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @return the result of the operation.
	 */
	public int[] alocacaoGrupo2(int numberOfSlots, List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp) {
		int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
        if(numberOfSlots> maxAmplitude) return null;
    	int chosen[] = null;

    	//Priority group
    	List<int[]> composition2 = novaComposicao2(freeSpectrumBands, circuit, cp);
    	chosen = policy(numberOfSlots, composition2, circuit, cp);
        if (chosen!=null){return chosen;}

        //Priority group 2
        List<int[]> composition3 = novaComposicao3(freeSpectrumBands, circuit, cp);
    	chosen = policy(numberOfSlots, composition3, circuit, cp);
        if (chosen!=null){return chosen;}

        //Lowest-priority group
        List<int[]> composition1 = novaComposicao1(freeSpectrumBands, circuit, cp);
    	chosen = policy(numberOfSlots, composition1, circuit, cp);
        if (chosen!=null){return chosen;}

        return chosen;
	}

	/**
	 * Returns the alocacao grupo3.
	 * @param numberOfSlots the numberOfSlots.
	 * @param freeSpectrumBands the freeSpectrumBands.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @return the result of the operation.
	 */
	public int[] alocacaoGrupo3(int numberOfSlots, List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp) {
		int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
        if(numberOfSlots> maxAmplitude) return null;
    	int chosen[] = null;

    	//Priority group
        List<int[]> composition3 = novaComposicao3(freeSpectrumBands, circuit, cp);
    	chosen = policy(numberOfSlots, composition3, circuit, cp);
        if (chosen!=null){return chosen;}

        //Priority group 2
    	List<int[]> composition1 = novaComposicao1(freeSpectrumBands, circuit, cp);
    	chosen = policy(numberOfSlots, composition1, circuit, cp);
        if (chosen!=null){return chosen;}

        //Lowest-priority group
        List<int[]> composition2 = novaComposicao2(freeSpectrumBands, circuit, cp);
    	chosen = policy(numberOfSlots, composition2, circuit, cp);
        if (chosen!=null){return chosen;}

        return chosen;
	}

	/**
	 * Returns the nova composicao1.
	 * @param freeSpectrumBands the freeSpectrumBands.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @return the result of the operation.
	 */
	private List<int[]> novaComposicao1(List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp) {
		List<int[]> novacomposition = new ArrayList<>();

		for(int[] band : freeSpectrumBands) {
			// Est completamente dentro do intervalo
			if ((band[1] >= this.eg1[0]) && (band[1] <= this.eg1[1]) && (band[0] >= this.eg1[0]) && (band[0] <= this.eg1[1])) {
				novacomposition.add(band.clone());
            }

			// Est parcialmente no limite inferior
			if ((band[1] >= this.eg1[0]) && (band[1] <= this.eg1[1]) && (band[0] < this.eg1[0]) && (band[0] < this.eg1[1])) {
				int[] novo = new int[2];
				novo[0] = this.eg1[0];
				novo[1] = band[1];
				novacomposition.add(novo);
            }

			// Est parcialmente no limite supeior
			if ((band[1] > this.eg1[1]) && (band[1] > this.eg1[0]) && (band[0] <= this.eg1[1]) && (band[0] > this.eg1[0])) {
				int[] novo = new int[2];
				novo[0] = band[0];
				novo[1] = this.eg1[1];
				novacomposition.add(novo);
            }

			// Engloba os limites
			if ((band[1] > this.eg1[1]) && (band[1] > this.eg1[0]) && (band[0] < this.eg1[1]) && (band[0] < this.eg1[0])) {
				int[] novo = new int[2];
				novo[0] = this.eg1[0];
				novo[1] = this.eg1[1];
				novacomposition.add(novo);
			}

		}
		return novacomposition;
	}

	/**
	 * Returns the nova composicao2.
	 * @param freeSpectrumBands the freeSpectrumBands.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @return the result of the operation.
	 */
	private List<int[]> novaComposicao2(List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp) {
		List<int[]> novacomposition = new ArrayList<>();

		for(int[] band : freeSpectrumBands) {
			// Est completamente dentro do intervalo
			if ((band[1] >= this.eg2[0]) && (band[1] <= this.eg2[1]) && (band[0] >= this.eg2[0]) && (band[0] <= this.eg2[1])) {
				novacomposition.add(band.clone());
            }

			// Est parcialmente no limite inferior
			if ((band[1] >= this.eg2[0]) && (band[1] <= this.eg2[1]) && (band[0] < this.eg2[0]) && (band[0] < this.eg2[1])) {
				int[] novo = new int[2];
				novo[0] = this.eg2[0];
				novo[1] = band[1];
				novacomposition.add(novo);
            }

			// Est parcialmente no limite supeior
			if ((band[1] > this.eg2[1]) && (band[1] > this.eg2[0]) && (band[0] <= this.eg2[1]) && (band[0] > this.eg2[0])) {
				int[] novo = new int[2];
				novo[0] = band[0];
				novo[1] = this.eg2[1];
				novacomposition.add(novo);
            }

			// Engloba os limites
			if ((band[1] > this.eg2[1]) && (band[1] > this.eg2[0]) && (band[0] < this.eg2[1]) && (band[0] < this.eg2[0])) {
				int[] novo = new int[2];
				novo[0] = this.eg2[0];
				novo[1] = this.eg2[1];
				novacomposition.add(novo);
			}
		}

		return novacomposition;
	}

	/**
	 * Returns the nova composicao3.
	 * @param freeSpectrumBands the freeSpectrumBands.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @return the result of the operation.
	 */
	private List<int[]> novaComposicao3(List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp) {
		List<int[]> novacomposition = new ArrayList<>();

		for(int[] band : freeSpectrumBands) {
			// Est completamente dentro do intervalo
			if ((band[1] >= this.eg3[0]) && (band[1] <= this.eg3[1]) && (band[0] >= this.eg3[0]) && (band[0] <= this.eg3[1])) {
				novacomposition.add(band.clone());
            }

			// Est parcialmente no limite inferior
			if ((band[1] >= this.eg3[0]) && (band[1] <= this.eg3[1]) && (band[0] < this.eg3[0]) && (band[0] < this.eg3[1])) {
				int[] novo = new int[2];
				novo[0] = this.eg3[0];
				novo[1] = band[1];
				novacomposition.add(novo);
            }

			// Est parcialmente no limite supeior
			if ((band[1] > this.eg3[1]) && (band[1] > this.eg3[0]) && (band[0] <= this.eg3[1]) && (band[0] > this.eg3[0])) {
				int[] novo = new int[2];
				novo[0] = band[0];
				novo[1] = this.eg3[1];
				novacomposition.add(novo);
            }

			// Engloba os limites
			if ((band[1] > this.eg3[1]) && (band[1] > this.eg3[0]) && (band[0] < this.eg3[1]) && (band[0] < this.eg3[0])) {
				int[] novo = new int[2];
				novo[0] = this.eg3[0];
				novo[1] = this.eg3[1];
				novacomposition.add(novo);
			}
		}

		return novacomposition;
	}

}
