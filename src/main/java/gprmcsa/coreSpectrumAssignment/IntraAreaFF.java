package gprmcsa.coreSpectrumAssignment;

import java.util.ArrayList;
import java.util.List;

import io.netty.util.internal.IntegerHolder;
import network.Circuit;
import network.ControlPlane;
import network.Core;
import network.Link;
import util.IntersectionFreeSpectrum;

// Esta classe implementa o algoritmo Intra-Area FF Assignment From Highest Rank Core
// Proposto por Tode and Hirata 2017
// Artigo: Routing, Spectrum, and Core and/or Mode Assignment on Space-Division Multiplexing Optical Networks
//

/**
 * Represents the IntraAreaFF component.
 */
public class IntraAreaFF implements CoreAndSpectrumAssignmentAlgorithmInterface{
	public static final int MAXPESOCORE = 9999;
	
	private double w; // Number of slots em um core
	private double y; // Common area ratio parameter
	private double rf[]; // Occupied bandwidth ratio for an f-slot request on the bottleneck link
	private double f[]; // Number of slots requested by the request
	private double bf[]; // Number of optical paths that can be allocated in the area
	private int limitesInf[];
	private int limitesSup[];
	private int limiteComumInf;
	private int limiteComumSup;
	private int fMax;
	
	/**
	 * Creates a new IntraAreaFF instance.
	 */
	public IntraAreaFF() {
		this.w = 320;
		this.y = 0.4; //value do artigo
		this.fMax = 12;
		
		// Rf values set manually based on simulation observations
		this.f = new double[13];
		this.f[0] = 0;
		this.f[1] = 1;
		this.f[2] = 2;
		this.f[3] = 3;
		this.f[4] = 4;
		this.f[5] = 5;
		this.f[6] = 6;
		this.f[7] = 7;
		this.f[8] = 8;
		this.f[9] = 9;
		this.f[10] = 10;
		this.f[11] = 11;
		this.f[12] = 12;
		
		this.rf = new double[13];
		this.rf[1]=0.03724;
		this.rf[2]=0.22627;
		this.rf[3]=0.27998;
		this.rf[4]=0.05093;
		this.rf[5]=0.19366;
		this.rf[6]=0.06837;
		this.rf[7]=0.05720;
		this.rf[8]=0.02926;
		this.rf[9]=0.03787;
		this.rf[10]=0.0;
		this.rf[11]=0.0;
		this.rf[12]=0.01917;
		
		this.bf = new double[13];
		this.bf[1]= calculeBf(this.f[1], this.rf[1]);
		this.bf[2]= calculeBf(this.f[2], this.rf[2]);
		this.bf[3]= calculeBf(this.f[3], this.rf[3]);
		this.bf[4]= calculeBf(this.f[4], this.rf[4]);;
		this.bf[5]= calculeBf(this.f[5], this.rf[5]);
		this.bf[6]= calculeBf(this.f[6], this.rf[6]);
		this.bf[7]= calculeBf(this.f[7], this.rf[7]);
		this.bf[8]= calculeBf(this.f[8], this.rf[8]);
		this.bf[9]= calculeBf(this.f[9], this.rf[9]);
		this.bf[10]= calculeBf(this.f[10], this.rf[10]);
		this.bf[11]= calculeBf(this.f[11], this.rf[11]);
		this.bf[12]= calculeBf(this.f[12], this.rf[12]);
		
		this.limitesInf = new int[13];
		this.limitesSup = new int[13];
		
		this.limitesInf[1] = 0;
		this.limitesSup[1] = this.limitesInf[1] + (int) ((bf[1]*f[1]) + bf[1]);
		
		this.limitesInf[2] = this.limitesSup[1]+1;
		this.limitesSup[2] = this.limitesInf[2] + (int) ((bf[2]*f[2]) + bf[2]);

		this.limitesInf[3] = this.limitesSup[2]+1;
		this.limitesSup[3] = this.limitesInf[3] + (int) ((bf[3]*f[3]) + bf[3]);
		
		this.limitesInf[4] = this.limitesSup[3]+1;
		this.limitesSup[4] = this.limitesInf[4] + (int) ((bf[4]*f[4]) + bf[4]);
		
		this.limitesInf[5] = this.limitesSup[4]+1;
		this.limitesSup[5] = this.limitesInf[5] + (int) ((bf[5]*f[5]) + bf[5]);
		
		this.limitesInf[6] = this.limitesSup[5]+1;
		this.limitesSup[6] = this.limitesInf[6] + (int) ((bf[6]*f[6]) + bf[6]);
		
		this.limitesInf[7] = this.limitesSup[6]+1;
		this.limitesSup[7] = this.limitesInf[7] + (int) ((bf[7]*f[7]) + bf[7]);
		
		this.limitesInf[8] = this.limitesSup[7]+1;
		this.limitesSup[8] = this.limitesInf[8] + (int) ((bf[8]*f[8]) + bf[8]);
		
		this.limitesInf[9] = this.limitesSup[8]+1;
		this.limitesSup[9] = this.limitesInf[9] + (int) ((bf[9]*f[9]) + bf[9]);
		
		this.limitesInf[10] = this.limitesSup[9]+1;
		this.limitesSup[10] = this.limitesInf[10] + (int) ((bf[10]*f[10]) + bf[10]);
		
		this.limitesInf[11] = this.limitesSup[10]+1;
		this.limitesSup[11] = this.limitesInf[11] + (int) ((bf[11]*f[11]) + bf[11]);
		
		this.limitesInf[12] = this.limitesSup[11]+1;
		this.limitesSup[12] = this.limitesInf[12] + (int) ((bf[12]*f[12]) + bf[12]);
		
		this.limiteComumInf = this.limitesSup[12]+1;
		this.limiteComumSup = (int) (this.w -1);
	}

	@Override
	public boolean assignCoreAndSpectrum(int numberOfSlots, Circuit circuit, ControlPlane cp) {
		int chosenCore = coreAssignment(circuit);
		int chosen[] = null;
		int contadorF = numberOfSlots;
		
    	List<int[]> compositionantiga = IntersectionFreeSpectrum.merge(circuit.getRoute(), circuit.getGuardBand(), chosenCore);
    	
    	//Testes
    	//System.out.println("\n\n\n"+bf[0]+"----"+bf[1]+"----"+bf[2]+"----"+bf[3]+"----"+bf[4]+"----"+bf[5]+"----"+bf[6]+"----"+bf[7]+"----"+bf[8]+"----"+bf[9]+"----"+bf[10]+"----"+bf[11]+"----"+bf[12]+"\n");
    	//System.out.println("0--------1--------2--------3--------4--------5--------6--------7--------8--------9--------10--------11--------12--------AC");
    	//System.out.println(limitsInf[0]+"-"+limitsSup[0]+"---"+limitsInf[1]+"-"+limitsSup[1]+"---"+limitsInf[2]+"-"+limitsSup[2]+"---"+limitsInf[3]+"-"+limitsSup[3]+"---"+limitsInf[4]+"-"+limitsSup[4]+"---"+limitsInf[5]+"-"+limitsSup[5]+"---"+limitsInf[6]+"-"+limitsSup[6]+"---"+limitsInf[7]+"-"+limitsSup[7]+"---"+limitsInf[8]+"-"+limitsSup[8]+"---"+limitsInf[9]+"-"+limitsSup[9]+"---"+limitsInf[10]+"-"+limitsSup[10]+"---"+limitsInf[11]+"-"+limitsSup[11]+"---"+limitsInf[12]+"-"+limitsSup[12]+"---"+limiteComumInf+"-"+limiteComumSup+"---");
    	
    	while(chosen == null) {
    		
    		if (contadorF==numberOfSlots) {
    			
    			List<int[]> composition = novaComposicao(contadorF, compositionantiga, circuit, cp); 
    			
    			//for(int i=0; i<oldComposition.size(); i++) {
    				//System.out.println(contadorF+"Old composition: "+oldComposition.get(i)[0]+"--"+oldComposition.get(i)[1]);
    			//}
    			
    			//for(int i=0; i<composition.size();i++) {
    				//System.out.println(contadorF+"COmposition: "+composition.get(i)[0]+"--"+composition.get(i)[1]);
    			//}
    			
    			chosen = policy(numberOfSlots, composition, circuit, cp); //First Fit
    			if (chosen != null) {
    				break;
    			}
    			
    			List<int[]> composition2 = novaComposicao2(contadorF, compositionantiga, circuit, cp); 
    			chosen = policy1(numberOfSlots, composition2, circuit, cp); //Last Fit
    			if (chosen != null) {
    				break;
    			}
    		}
    		
    		if (contadorF>numberOfSlots) {
    			List<int[]> composition3 = novaComposicao(contadorF, compositionantiga, circuit, cp); 
    			chosen = policy1(numberOfSlots, composition3, circuit, cp); //First Fit
    			if (chosen != null) {
    				break;
    			}
    		}
    		
    		contadorF++;
    		if((chosen == null)&&(contadorF > this.fMax)) {
    			return false;
    		}
    	}
    	
        circuit.setSpectrumAssigned(chosen);
        circuit.setIndexCore(chosenCore);
        
        if (chosen == null) {
        	return false;
        }

        return true;
	}

	/**
	 * Executes the coreAssignment operation.
	 * @return the result of the operation.
	 */
	public int coreAssignment() {
		return 0;
	}

	/**
	 * Executes the coreAssignment operation.
	 * @param circuit the circuit.
	 * @return the result of the operation.
	 */
	public int coreAssignment(Circuit circuit) {
		int coreEscolhido = 0;
		int pesoCoreEscolhido = MAXPESOCORE * circuit.getRoute().getLinkList().size();
		
		// Vendo qual core tem o lowest peso total
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
		
		//Atualizando os weights
		for(Link link : circuit.getRoute().getLinkList()) {
			link.getCore(coreEscolhido).setPeso(MAXPESOCORE);
			link.updatePesosOfCores(coreEscolhido);
		}
		
		//zera os weights
		for(Link link : circuit.getRoute().getLinkList()) {
			boolean flag = true;
			
			for(Core core : link.getCores()) {
				if(core.getPeso() < MAXPESOCORE) {
					flag = false;
					break;
				}
			}
			
			if(flag) {
				//System.out.println("Weight resetado no link "+link.getName());
				link.renewAllPesosOfCores();
			}
		}
		
		//Zera os weights
		//if(pesoCoreChosen >= (MAXPESOCORE * circuit.getRoute().getLinkList().size() ) ){
			//for(Link link : circuit.getRoute().getLinkList()) {
				//link.renovaTodosOsWeights();
			//}
		//}
		

		return coreEscolhido;
	}

	//Firt fit
	/**
	 * Executes the policy operation.
	 * @param numberOfslots the numberOfslots.
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
	
	//Last Fit
	/**
	 * Executes the policy1 operation.
	 * @param numberOfslots the numberOfslots.
	 * @param freeSpectrumBands the freeSpectrumBands.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @return the result of the operation.
	 */
	public int[] policy1(int numberOfSlots, List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp) {
		int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
        if(numberOfSlots>maxAmplitude) return null;
    	int chosen[] = null;
        int band[] = null;
        
        for (int i = freeSpectrumBands.size() - 1; i >= 0; i--) {
            band = freeSpectrumBands.get(i);
            
            if (band[1] - band[0] + 1 >= numberOfSlots) {
                chosen = band.clone();
                chosen[0] = chosen[1] - numberOfSlots + 1;//It is not necessary to allocate the entire band, just the amount of slots required
                break;
            }
        }

        return chosen;
	}
	
	private double calculeBf(double f, double rf) {
		return Math.round( (this.w * (1 - this.y) * rf) / (f) );
	}
	
	private List<int[]> novaComposicao(int numberOfSlots, List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp) {
		List<int[]> novacomposition = new ArrayList<>();
		
		for(int[] band : freeSpectrumBands) {
			// It is completely inside the interval
			if ((band[1] >= this.limitesInf[numberOfSlots]) && (band[1] <= this.limitesSup[numberOfSlots]) && (band[0] >= this.limitesInf[numberOfSlots]) && (band[0] <= this.limitesSup[numberOfSlots])) {
				novacomposition.add(band);
            }
			
			// It is partially at the lower boundary
			if ((band[1] >= this.limitesInf[numberOfSlots]) && (band[1] <= this.limitesSup[numberOfSlots]) && (band[0] < this.limitesInf[numberOfSlots]) && (band[0] < this.limitesSup[numberOfSlots])) {
				int[] novo = new int[2];
				novo[0] = this.limitesInf[numberOfSlots];
				novo[1] = band[1];
				novacomposition.add(novo);
            }
			
			// It is partially at the upper boundary
			if ((band[1] > this.limitesSup[numberOfSlots]) && (band[1] > this.limitesInf[numberOfSlots]) && (band[0] <= this.limitesSup[numberOfSlots]) && (band[0] > this.limitesInf[numberOfSlots])) {
				int[] novo = new int[2];
				novo[0] = band[0];
				novo[1] = this.limitesSup[numberOfSlots];
				novacomposition.add(novo);
            }
			
			// Engloba os limits
			if ((band[1] > this.limitesSup[numberOfSlots]) && (band[1] > this.limitesInf[numberOfSlots]) && (band[0] < this.limitesSup[numberOfSlots]) && (band[0] < this.limitesInf[numberOfSlots])) {
				int[] novo = new int[2];
				novo[0] = this.limitesInf[numberOfSlots];
				novo[1] = this.limitesSup[numberOfSlots];
				novacomposition.add(novo);
			}	
		}
		
		return novacomposition;
	}
	
	private List<int[]> novaComposicao2(int numberOfSlots, List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp) {
		List<int[]> novacomposition2 = new ArrayList<>();
		
		for(int[] band : freeSpectrumBands) {
			// It is completely inside the interval
			if ((band[1] >= this.limiteComumInf) && (band[1] <= this.limiteComumSup) && (band[0] >= this.limiteComumInf) && (band[0] <= this.limiteComumSup)) {
				novacomposition2.add(band);
            }
			
			// It is partially at the lower boundary
			if ((band[1] >= this.limiteComumInf) && (band[1] <= this.limiteComumSup) && (band[0] < this.limiteComumInf) && (band[0] < this.limiteComumSup)) {
				int[] novo = new int[2];
				novo[0] = this.limiteComumInf;
				novo[1] = band[1];
				novacomposition2.add(novo);
            }
			
			// It is partially at the upper boundary
			if ((band[1] > this.limiteComumSup) && (band[1] > this.limiteComumInf) && (band[0] <= this.limiteComumSup) && (band[0] > this.limiteComumInf)) {
				int[] novo = new int[2];
				novo[0] = band[0];
				novo[1] = this.limiteComumSup;
				novacomposition2.add(novo);
            }
			
			// Engloba os limits
			if ((band[1] > this.limiteComumSup) && (band[1] > this.limiteComumInf) && (band[0] < this.limiteComumSup) && (band[0] < this.limiteComumInf)) {
				int[] novo = new int[2];
				novo[0] = this.limiteComumInf;
				novo[1] = this.limiteComumSup;
				novacomposition2.add(novo);
			}	
		}
		
		return novacomposition2;
	}
 
}
