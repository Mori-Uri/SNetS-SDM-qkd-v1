package gprmcsa.coreSpectrumAssignment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import gprmcsa.routing.Route;
import network.Circuit;
import network.ControlPlane;
import network.Link;
import network.Mesh;
import network.PhysicalLayer;
import util.IntersectionFreeSpectrum;

// ICXT-Aware algorithm, proposed in LIU 2020.
// Adaptao da estratgia XT aware de LIU 2020.
// Paper: Routing Core and Spectrum Allocation Algorithm for Inter-Core Crosstalk and Energy Efficiency in Space Division Multiplexing Elastic Optical Networks
// A IMPLEMENTAO A SEGUIR  VLIDA APENAS PARA UMA FIBRA DE 7 NCLEOS E 320 SLOTS EM CADA NCLEO.

/**
 * Represents the IcxtAwareAlgorithm component.
 */
public class IcxtAwareAlgorithm implements CoreAndSpectrumAssignmentAlgorithmInterface {

	private int si; //slot inicial
	private int f; // total number of slots in the core
	private int gi; // total number of cores in group i
	private int eg1[]; // Priority spectrum of group 1
	private int eg2[]; // Priority spectrum of group 2
	private int eg3[]; // Priority spectrum of group 3
	private List<Integer> g1; // Cores that belong to group 1
	private List<Integer> g2; // Cores that belong to group 2
	private List<Integer> g3; // Cores that belong to group 3
	private int numberOfCores; // total number of cores

	/**
	 * Creates a new instance of IcxtAwareAlgorithm.
	 */
	public IcxtAwareAlgorithm() {
		this.numberOfCores = -1;

		this.si = 1;
		this.f = 320;

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
		this.g2 = new ArrayList<>();
		this.g3 = new ArrayList<>();
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
		if(this.numberOfCores == -1) {
			this.numberOfCores = cp.getMesh().getLinkList().get(0).getNumberOfCores();

			if(numberOfCores == 7) {
				this.g1.add(1);
				this.g1.add(3);
				this.g1.add(5);

				this.g2.add(2);
				this.g2.add(4);
				this.g2.add(6);

				this.g3.add(0);

			} else if(numberOfCores == 12) {
				this.g1.add(0);
				this.g1.add(3);
				this.g1.add(6);
				this.g1.add(9);

				this.g2.add(1);
				this.g2.add(4);
				this.g2.add(7);
				this.g2.add(10);

				this.g3.add(2);
				this.g3.add(5);
				this.g3.add(8);
				this.g3.add(11);

			} else if(numberOfCores == 19) {
				this.g1.add(1);
				this.g1.add(3);
				this.g1.add(5);
				this.g1.add(7);
				this.g1.add(9);
				this.g1.add(11);
				this.g1.add(13);
				this.g1.add(15);
				this.g1.add(17);

				this.g2.add(2);
				this.g2.add(4);
				this.g2.add(6);
				this.g2.add(8);
				this.g2.add(10);
				this.g2.add(12);
				this.g2.add(14);
				this.g2.add(16);
				this.g2.add(18);

				this.g3.add(0);
			}
		}

		Circuit circuitoCandidato = new Circuit();
		circuitoCandidato.setPair(circuit.getPair());
		circuitoCandidato.setRequests(circuit.getRequests());
		circuitoCandidato.setRoute(circuit.getRoute());
		circuitoCandidato.setModulation(circuit.getModulation());
		circuitoCandidato.setGuardBand(circuit.getGuardBand());

		//this.circuitoCandidato.setIndexCore(5);
		//System.out.println(circuit.getIndexCore()+"---"+this.circuitoCandidato.getIndexCore());

		int chosen[] = null;
		//int contadorF = numberOfSlots;

		//Primeira tentativa
		for(int c=numberOfCores-1; c>=0; c--) {
			List<int[]> compositionantiga = IntersectionFreeSpectrum.merge(circuitoCandidato.getRoute(), circuitoCandidato.getGuardBand(), c);

			if(g1.contains(c)) {
	    		chosen = alocacaoGrupo1Xt(numberOfSlots, compositionantiga, circuitoCandidato, cp, c);
	    	}

	    	if(g2.contains(c)) {
	    		chosen = alocacaoGrupo2Xt(numberOfSlots, compositionantiga, circuitoCandidato, cp, c);
	    	}

	    	if(g3.contains(c)) {
	    		chosen = alocacaoGrupo3Xt(numberOfSlots, compositionantiga, circuitoCandidato, cp, c);
	    	}

	    	if(chosen!=null) {
	    		circuitoCandidato.setIndexCore(c);
	    		circuitoCandidato.setSpectrumAssigned(chosen);

	    		if(cp.getMesh().getPhysicalLayer().getCrosstalk().isAdmissible(circuitoCandidato) && cp.getMesh().getPhysicalLayer().getCrosstalk().isAdmissibleInOthers(circuitoCandidato)) {
	    			circuit.setIndexCore(c);
	    			circuit.setSpectrumAssigned(chosen);
	    	        return true;
	    		}
	    	}
		}

		//Segunda tentativa
		/*for(int c=6; c>=0; c--) {
			List<int[]> compositionantiga2 = IntersectionFreeSpectrum.merge(circuitoCandidato.getRoute(), circuitoCandidato.getGuardBand(), c);

			if(g1.contains(c)) {
	    		chosen = alocacaoGrupo1(numberOfSlots, compositionantiga2, circuit, cp);
	    	}

	    	if(g2.contains(c)) {
	    		chosen = alocacaoGrupo2(numberOfSlots, compositionantiga2, circuit, cp);
	    	}

	    	if(g3.contains(c)) {
	    		chosen = alocacaoGrupo3(numberOfSlots, compositionantiga2, circuit, cp);
	    	}

	    	if(chosen!=null) {
	    		circuit.setSpectrumAssigned(chosen);
	    	    circuit.setIndexCore(c);
	    	    return true;
	    	}

		}*/

		return false;
	}

	/**
	 * Returns the core assignment.
	 * @return the result of the operation.
	 */
	@Override
	public int coreAssignment() {
		Random generator = new Random();
		int a = generator.nextInt(this.numberOfCores);
		return a;
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

	//Firt fit
	/**
	 * Returns the policy xt.
	 * @param numberOfSlots the numberOfSlots.
	 * @param freeSpectrumBands the freeSpectrumBands.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @param core the core.
	 * @return the result of the operation.
	 */
	public int[] policyXt(int numberOfSlots, List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp, int core) {
		int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
	    if(numberOfSlots> maxAmplitude) return null;
	    int chosen[] = null;

	    for (int[] band : freeSpectrumBands) {

	    	for (int i = band[0]; i <= band[1]; i++) {

				if (band[1] - i + 1 >= numberOfSlots) {
	                chosen = band.clone();
	                chosen[0] = i;
	                chosen[1] = chosen[0] + numberOfSlots - 1;

	                if(chosen!=null) {

	                	circuit.setIndexCore(core);
	                	circuit.setSpectrumAssigned(chosen);

	                	circuit.setQoT(true);
	                	circuit.setQoTForOther(true);
	                	circuit.setSNR(PhysicalLayer.maxOSNR);

	    	    		if(isAdmissibleQualityOfTransmission(circuit, cp.getMesh())) { //QOTN QOTO

		    	    		if(cp.getMesh().getPhysicalLayer().getCrosstalk().isAdmissible(circuit) && cp.getMesh().getPhysicalLayer().getCrosstalk().isAdmissibleInOthers(circuit)) {
		    	    			//circuit.setSpectrumAssigned(chosen);
		    	    	        //circuit.setIndexCore(core);
		    	    	        return chosen;
		    	    		}
	    	    		}
	    	    	}
	            }
	        }
	     }

	    return null;
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
	 * Returns the alocacao grupo1 xt.
	 * @param numberOfSlots the numberOfSlots.
	 * @param freeSpectrumBands the freeSpectrumBands.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @param c the c.
	 * @return the result of the operation.
	 */
	public int[] alocacaoGrupo1Xt(int numberOfSlots, List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp, int c) {
		int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
        if(numberOfSlots> maxAmplitude) return null;
    	int chosen[] = null;

    	//Priority group
    	List<int[]> composition1 = novaComposicao1(freeSpectrumBands, circuit, cp);
    	chosen = policyXt(numberOfSlots, composition1, circuit, cp, c);
        if (chosen!=null){return chosen;}

        //Priority group 2
        List<int[]> composition2 = novaComposicao2(freeSpectrumBands, circuit, cp);
    	chosen = policyXt(numberOfSlots, composition2, circuit, cp, c);
        if (chosen!=null){return chosen;}

        //Lowest-priority group
        List<int[]> composition3 = novaComposicao3(freeSpectrumBands, circuit, cp);
    	chosen = policyXt(numberOfSlots, composition3, circuit, cp, c);
        if (chosen!=null){return chosen;}

        return chosen;
	}

	/**
	 * Returns the alocacao grupo2 xt.
	 * @param numberOfSlots the numberOfSlots.
	 * @param freeSpectrumBands the freeSpectrumBands.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @param c the c.
	 * @return the result of the operation.
	 */
	public int[] alocacaoGrupo2Xt(int numberOfSlots, List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp, int c) {
		int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
        if(numberOfSlots> maxAmplitude) return null;
    	int chosen[] = null;

    	//Priority group
    	List<int[]> composition2 = novaComposicao2(freeSpectrumBands, circuit, cp);
    	chosen = policyXt(numberOfSlots, composition2, circuit, cp, c);
        if (chosen!=null){return chosen;}

        //Priority group 2
        List<int[]> composition3 = novaComposicao3(freeSpectrumBands, circuit, cp);
    	chosen = policyXt(numberOfSlots, composition3, circuit, cp, c);
        if (chosen!=null){return chosen;}

        //Lowest-priority group
        List<int[]> composition1 = novaComposicao1(freeSpectrumBands, circuit, cp);
    	chosen = policyXt(numberOfSlots, composition1, circuit, cp, c);
        if (chosen!=null){return chosen;}

        return chosen;
	}


	/**
	 * Returns the alocacao grupo3 xt.
	 * @param numberOfSlots the numberOfSlots.
	 * @param freeSpectrumBands the freeSpectrumBands.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @param c the c.
	 * @return the result of the operation.
	 */
	public int[] alocacaoGrupo3Xt(int numberOfSlots, List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp, int c) {
		int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
        if(numberOfSlots> maxAmplitude) return null;
    	int chosen[] = null;

    	//Priority group
        List<int[]> composition3 = novaComposicao3(freeSpectrumBands, circuit, cp);
    	chosen = policyXt(numberOfSlots, composition3, circuit, cp, c);
        if (chosen!=null){return chosen;}

        //Priority group 2
    	List<int[]> composition1 = novaComposicao1(freeSpectrumBands, circuit, cp);
    	chosen = policyXt(numberOfSlots, composition1, circuit, cp, c);
        if (chosen!=null){return chosen;}

        //Lowest-priority group
        List<int[]> composition2 = novaComposicao2(freeSpectrumBands, circuit, cp);
    	chosen = policyXt(numberOfSlots, composition2, circuit, cp, c);
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

//
// Added the QoTN and QoTo check method. It is not in the original paper, but it was added to provide a fair comparison
//
	/**
	 * Checks whether admissible quality of transmission.
	 * @param circuit the circuit.
	 * @param mesh the mesh.
	 * @return true if the condition is met; false otherwise.
	 */
	protected boolean isAdmissibleQualityOfTransmission(Circuit circuit, Mesh mesh) {

		// Check if it is to test the QoT
		//if(mesh.getPhysicalLayer().isActiveQoT()){

			// Verifies the QoT of the current circuit
			if(computeQualityOfTransmission(circuit, null, false, mesh)){
				boolean QoTForOther = true;

				// Check if it is to test the QoT of other already active circuits
				//if(mesh.getPhysicalLayer().isActiveQoTForOther()){

				// Calculates the QoT of the other circuits
				QoTForOther = computeQoTForOther(circuit, mesh);
				circuit.setQoTForOther(QoTForOther);
				//}

				return QoTForOther;
			}

			return false; // Circuit can not be established
		//}

		// If it does not check the QoT then it returns acceptable
		//return true;
	}

	/**
	 * Computes the quality of transmission.
	 * @param circuit the circuit.
	 * @param testCircuit the testCircuit.
	 * @param addTestCircuit the addTestCircuit.
	 * @param mesh the mesh.
	 * @return true if the condition is met; false otherwise.
	 */
	public boolean computeQualityOfTransmission(Circuit circuit, Circuit testCircuit, boolean addTestCircuit, Mesh mesh){
		//double sigma = 1.0;
		double SNR = mesh.getPhysicalLayer().computeSNRSegment(circuit, circuit.getRoute(), 0, circuit.getRoute().getNodeList().size() - 1, circuit.getModulation(), circuit.getIndexCore(), circuit.getSpectrumAssigned(), testCircuit, addTestCircuit);
		boolean QoT = mesh.getPhysicalLayer().isAdmissible(circuit.getModulation(), SNR);

		circuit.setSNR(SNR);
		circuit.setQoT(QoT);

		//System.out.println("--------\n A OSNR : "+SNRdB+"\n\n----------");

		return QoT;
	}

	/**
	 * Computes the qo t for other.
	 * @param circuit the circuit.
	 * @param mesh the mesh.
	 * @return true if the condition is met; false otherwise.
	 */
	public boolean computeQoTForOther(Circuit circuit, Mesh mesh){
		HashSet<Circuit> circuits = new HashSet<Circuit>(); // Circuit list for test
		HashMap<Circuit, Double> circuitsSNR = new HashMap<Circuit, Double>(); // To guard the SNR of the test list circuits
		HashMap<Circuit, Boolean> circuitsQoT = new HashMap<Circuit, Boolean>(); // To guard the QoT of the test list circuits

		// Search for all circuits that have links in common with the circuit under evaluation
		Route route = circuit.getRoute();
		for (Link link : route.getLinkList()) {

			// Picks up the active circuits that use the link
			HashSet<Circuit> circuitsTemp = link.getCore(circuit.getIndexCore()).getCircuitList();
	        for (Circuit circuitTemp : circuitsTemp) {

	        	// If the circuit is different from the circuit under evaluation and is not in the circuit list for test
	            if (!circuit.equals(circuitTemp) && !circuits.contains(circuitTemp)) {
	                circuits.add(circuitTemp);
	            }
	        }
		}

		// Tests the QoT of circuits
	    for (Circuit circuitTemp : circuits) {

	    	// Stores the SNR and QoT values
	    	circuitsSNR.put(circuitTemp, circuitTemp.getSNR());
	        circuitsQoT.put(circuitTemp, circuitTemp.isQoT());

	    	// Recalculates the QoT and SNR of the circuit
	        //boolean QoT = computeQualityOfTransmission(circuitTemp, null, true, mesh);

	        boolean QoT = computeQualityOfTransmission(circuitTemp, circuit, true, mesh);

	        // Returns the SNR and QoT values of circuits before the establishment of the circuit in evaluation
	    	//for (Circuit circuitAux : circuitsSNR.keySet()) {
	    		//circuitAux.setSNR(circuitsSNR.get(circuitAux));
	    		//circuitAux.setQoT(circuitsQoT.get(circuitAux));
	    	//}

	        if (!QoT) {

	        	// Returns the SNR and QoT values of circuits before the establishment of the circuit in evaluation
	        	for (Circuit circuitAux : circuitsSNR.keySet()) {
	        		circuitAux.setSNR(circuitsSNR.get(circuitAux));
	        		circuitAux.setQoT(circuitsQoT.get(circuitAux));
	        	}

	            return false;
	        }
	    }

	    // Returns the SNR and QoT values of circuits before the establishment of the circuit in evaluation
		for (Circuit circuitAux : circuitsSNR.keySet()) {
			circuitAux.setSNR(circuitsSNR.get(circuitAux));
			circuitAux.setQoT(circuitsQoT.get(circuitAux));
		}

		return true;
	}

}

