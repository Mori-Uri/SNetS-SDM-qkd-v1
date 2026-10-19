package gprmcsa.coreSpectrumAssignment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import gprmcsa.modulation.Modulation;
import gprmcsa.routing.Route;
import network.Circuit;
import network.ControlPlane;
import network.Core;
import network.Link;
import util.IntersectionFreeSpectrum;

// Algoritmo proposto em: "Dynamic impairment-aware RMCSA in multi-core fiber-based elastic optical networks" 
// Autores: Jisong Su, Juan Zhang, JianWang, Danping Ren, Jinhua Hu, Jijun Zhao.
// Revista: Optics Communications
// Ano: 2022
//

/**
 * Represents the DiRMCSA component.
 */
public class DiRMCSA implements CoreAndSpectrumAssignmentAlgorithmInterface{
	private int coreOfTheTime;
	private Circuit circuitBestCandidate;
	private double h; //XT
	private double slotBandwidth; // Hz
	private double lowerFrequency; // Hz
	private double centerFrequency; //Frequency of light
	
	/**
	 * Creates a new DiRMCSA instance.
	 */
	public DiRMCSA() {
		this.slotBandwidth = 12.5 * 1000000000; //Hz
		this.centerFrequency = 1.9385E14;
		double totalSlots = 320.0;
		this.lowerFrequency = centerFrequency - (slotBandwidth * (totalSlots / 2.0)); // Hz, Half slots are removed because center Frequency = 193.55E+12 is the central frequency of the optical spectrum
	}
	
	@Override
	public boolean assignCoreAndSpectrum(int numberOfSlots, Circuit circuit, ControlPlane cp) {

		this.circuitBestCandidate = new Circuit();
		this.coreOfTheTime = 0;
		this.h = cp.getMesh().getPhysicalLayer().getCrosstalk().getH();
					
		circuit.setSpectrumAssigned(policy(numberOfSlots, null, circuit, cp));
        circuit.setIndexCore(circuitBestCandidate.getIndexCore());
        circuit.setPeso(circuitBestCandidate.getPeso());
        
        if (circuit.getSpectrumAssigned() == null) {
 	       return false;
 		}
        
        return true;
	}
	
	@Override
	public int coreAssignment() {
		int temp = this.coreOfTheTime;
		this.coreOfTheTime++;
		return temp;
	}

	@Override
	public int[] policy(int numberOfSlots, List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp) {
	//	List<int[]> sbs = new ArrayList<int[]>();
		List<Circuit> listaSBS = new ArrayList<Circuit>();

		while (coreOfTheTime < cp.getMesh().getLinkList().get(0).getNumberOfCores()) {
			List<int[]> composition = IntersectionFreeSpectrum.merge(circuit.getRoute(), circuit.getGuardBand(), coreOfTheTime);
			
			int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
	        if(numberOfSlots> maxAmplitude) return null;
			
			for (int[] band : composition) {
		    	
				for (int i = band[0]; i <= band[1]; i++) {
					int chosen[] = null;
		            if (band[1] - i + 1 >= numberOfSlots) {
		                chosen = band.clone();
		                chosen[0] = i;
		                chosen[1] = chosen[0] + numberOfSlots - 1;//It is not necessary to allocate the entire band, just the amount of slots required
		                
		                Circuit circuitCandidate = new Circuit();
		                //circuitCandidate = circuit;
		                circuitCandidate.setPair(circuit.getPair());
		                circuitCandidate.setRequests(circuit.getRequests());
		                circuitCandidate.setRoute(circuit.getRoute());
		        		circuitCandidate.setModulation(circuit.getModulation());
		        		circuitCandidate.setGuardBand(circuit.getGuardBand());
		        		
		                circuitCandidate.setSpectrumAssigned(chosen);
		                circuitCandidate.setIndexCore(coreOfTheTime);
		                circuitCandidate.setRoute(circuit.getRoute().clone());
		                circuitCandidate.setXt(cp.getMesh().getPhysicalLayer().getCrosstalk().calculateCrosstalk(circuitCandidate, null, false)); //calcula Xt
		                circuitCandidate.setPeso(calculeXCI(circuitCandidate, circuitCandidate.getRoute(), circuitCandidate.getIndexCore(), circuitCandidate.getModulation(), circuitCandidate.getSpectrumAssigned()) + calculeXTi(circuitCandidate, (circuitCandidate.getSpectrumAssigned()[1] - circuitCandidate.getSpectrumAssigned()[0] +1)));
		                
		                //System.out.println(circuitCandidate.getWeight());
		                
		                listaSBS.add(circuitCandidate);
       		                
		            }
				}	            
	        }
			
			coreAssignment();
		}
		

		double menorD = 99999999.0;
		
		//sort
		for(Circuit c : listaSBS) {
			if(c.getXt() <= c.getModulation().getXTthreshold()) {
				if(cp.computeQualityOfTransmission(c, null, false)) {
					//if(crosstalk.isAdmissibleInOthers(c)) { // XTO check
						if(c.getPeso() < menorD) {
							circuitBestCandidate = c;
							menorD = c.getPeso();
						}
					//}
				}
			}
		}
		
		return circuitBestCandidate.getSpectrumAssigned();
	}
	
	private double calculeXCI(Circuit circuit, Route route, int core, Modulation modulation, int spectrumAssigned[]) {
		double slotBandwidth = 12.5 * 1000000000; //Hz
		//double numslotsRequired = spectrumAssigned[1] - spectrumAssigned[0] + 1; // Number of slots required
		double numSlotsRequired = modulation.requiredSlots(circuit.getRequiredBitRate());
		double Bsi = numSlotsRequired * slotBandwidth; // Circuit bandwidth
		double fi = lowerFrequency + (slotBandwidth * (spectrumAssigned[0] - 1.0)) + (Bsi / 2.0); // Central frequency of circuit
		
		double xciTotal = 0.0;
		
		for(Link link : route.getLinkList()) {
			//List<Link> caAlocados = new ArrayList<Link>();
			HashSet<Circuit> caAlocados = link.getCore(core).getCircuitList();
			
			for(Circuit j : caAlocados) {
				
				int[] saJ = j.getSpectrumAssignedByLink(link);
				int numOfSlots = saJ[1] - saJ[0] + 1;
				double Bsj;
				double fj;
				
				Bsj = numOfSlots * slotBandwidth; // Circuit bandwidth, less the guard band
				fj = lowerFrequency + (slotBandwidth * (saJ[0] - 1.0)) + (Bsj / 2.0); // Central frequency of circuit
				
				double deltaFij = fi - fj;
				if(deltaFij < 0.0) {
					deltaFij = -1.0 * deltaFij;
				}
				
				xciTotal = xciTotal + xci(Bsi, deltaFij, Math.abs(numSlotsRequired - (j.getSpectrumAssigned()[1] - j.getSpectrumAssigned()[0] +1)));
			}
		}
		
		//System.out.println("xci--"+xciTotal);
		return xciTotal;
	}
	
	private double xci(double bi, double deltafij, double deltaFS) {
		double termo1 = Math.abs(Math.abs(deltafij) - (bi/2));
		double termo2 = 1 + (bi/termo1);
		double termo3 = Math.abs(deltaFS) +1;
		
		return (Math.log(termo2) * termo3);
	}
	
	private double calculeXTi(Circuit circuit, int numberOfSlots) {
		Route rota = circuit.getRoute();
		double totalXT = 0.0;
		
		for(Link link: rota.getLinkList()) {
			totalXT = totalXT + calculeCrosstalkInLink(circuit, link, numberOfSlots);
		}
		
		//System.out.println("xti--"+totalXT);
		return totalXT;
	}
		
	private double calculeCrosstalkInLink(Circuit circuit, Link link, int numberOfSlots) {
		
		double xtInLink = 0.0;
		ArrayList<Core> adjacentsCores = link.getAdjacentCores(circuit.getIndexCore());
		
		for(Core core : adjacentsCores) {
			//xtInLink = xtInLink + ((SIGNALPOWER*h*link.getDistance()*1000)/SIGNALPOWER);
			xtInLink = xtInLink + ((calculeIsoij(circuit, core, numberOfSlots)*h*link.getDistance()*1000));
		}
		
		return xtInLink;
	}
	
	private double calculeIsoij(Circuit circuit, Core core, int numberOfSlots) {
		//int[] spectrum1 = circuit.getSpectrumAssigned();
		double nsoij = 0.0; // number of overlapping slots between i and j
		//double nsj = 0.0; //number of slots of the connection j 
		double nsi = numberOfSlots;
		double Isoij = 0.0;
		//int quantInter = 0;
		
		for(Circuit circuit2 : core.getCircuitList()) {
			if(isIntersection(circuit.getSpectrumAssigned(), circuit2.getSpectrumAssigned())) {
				//nsj = sizeSpectrumAllocate(circuit2.getSpectrumAssigned());
				nsoij = numberOfOverlapping(circuit.getSpectrumAssigned(), circuit2.getSpectrumAssigned());
				//quantInter++;
				//nsoij = numberOfOverlapping(spectrum1, circuit2.getSpectrumAssigned());
				
				if(nsi != 0.0){
					Isoij += nsoij/nsi;
				}else {
					Isoij += 0.0;
				}
			}
		}
		
		return Isoij;
		
//		if(quantInter==0) {
//			//System.out.println("1");
//			return 0;
//		}else {
//			//System.out.println("2");
//			return nsoij/nsi;
//		}
	}
	
	private boolean isIntersection(int[] spectrum1, int[] spectrum2) {
		ArrayList<Integer> slots2 = new ArrayList<Integer>();
		
		for(int i=spectrum2[0]; i<=spectrum2[1]; i++) {
			slots2.add(i);
		}
				
		for(int i=spectrum1[0]; i<=spectrum1[1]; i++) {
			if(slots2.contains(i)) {
				return true;
			}
		}
		
		return false;
	}
	
	private int numberOfOverlapping(int[] spectrum1, int[] spectrum2) {
		int numberOfOverlapping = 0;
		
		for(int i=spectrum1[0]; i<=spectrum1[1]; i++) {
			if ((i>=spectrum2[0]) && (i<=spectrum2[1])) {
				numberOfOverlapping++;
			}
		}
				
		return numberOfOverlapping;
	}
	
	private int sizeSpectrumAllocate(int[] spectrum) {
		return spectrum[1]-spectrum[0]+1;
	}
	
}
