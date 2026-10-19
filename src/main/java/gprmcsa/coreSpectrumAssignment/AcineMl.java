package gprmcsa.coreSpectrumAssignment;

import java.io.IOException;
import java.io.PrintStream;
import java.net.Socket;
import java.rmi.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.Vector;

import network.Circuit;
import network.ControlPlane;
import network.Core;
import network.Link;
import util.IntersectionFreeSpectrum;

// Algoritmo proposto por Jurandir
// SBRC 2021

/**
 * Represents the AcineMl component.
 */
public class AcineMl implements CoreAndSpectrumAssignmentAlgorithmInterface {
	private int coreOfTheTime;
	private double xtCandidate;
	private double xtBestCandidate;
	private Circuit circuitCandidate;
	private Circuit circuitBestCandidate;
	
	private int numberOfCores;
	private int spectrumUtilizationAbsolut[];
	private int ultimoCore;
	
	public static int qc0;
	public static int qc1;
	public static int qc2;
	public static int qc3;
	public static int qc4;
	public static int qc5;
	public static int qc6;
	public static int geral;
	
	private int contCore1;
	
		
	/**
	 * Creates a new AcineMl instance.
	 */
	public AcineMl() {
		this.coreOfTheTime = 1;
		this.numberOfCores = -1; //indices de slots
		//this.spectrumUtilizationAbsolut = new int[numberOfCores];
		contCore1=20000;
	}
	
	@Override
	public boolean assignCoreAndSpectrum(int numberOfSlots, Circuit circuit, ControlPlane cp) {
		if (numberOfCores == -1) {
			numberOfCores = cp.getMesh().getLinkList().get(0).getNumberOfCores();
			this.spectrumUtilizationAbsolut = new int[numberOfCores];
		}
		
		spectrumUtilizationAbsolute2(circuit, cp);
		//calculeAverageXt(circuit, cp);
		
		this.circuitCandidate = new Circuit();
		this.circuitBestCandidate = new Circuit();
		this.xtCandidate = 0.0;
		this.xtBestCandidate = 0.0; 
		this.coreOfTheTime = coreAssignment(circuit, cp);
		
		if(coreOfTheTime==0) {
			this.qc0++;
		}
		
		if(coreOfTheTime==1) {
			this.qc1++;
		}
		
		if(coreOfTheTime==2) {
			this.qc2++;
		}
		
		if(coreOfTheTime==3) {
			this.qc3++;
		}
		
		if(coreOfTheTime==4) {
			this.qc4++;
		}
		
		if(coreOfTheTime==5) {
			this.qc5++;
		}
		
		if(coreOfTheTime==6) {
			this.qc6++;
		}
		
		geral++;
					
		circuit.setSpectrumAssigned(policy(numberOfSlots, null, circuit, cp));
        circuit.setIndexCore(circuitBestCandidate.getIndexCore());
        
        if (circuit.getSpectrumAssigned() == null) {
 	       return false;
 		}
        
        return true;
	}
	
	@Override
	public int coreAssignment() {
		int temp = this.coreOfTheTime;
		this.coreOfTheTime--;
		return temp;
	}
	
	/**
	 * Executes the coreAssignment operation.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @return the result of the operation.
	 */
	public int coreAssignment(Circuit circuit, ControlPlane cp) {
		int numCore = numberOfCores -1;
		int core = numCore;
		Random randon = new Random();
		//System.out.println("teste1");
		
		String mensagem = "";
		//int[] modML = circuit.getModulationML();
		
		//if(contCore1 <= 0) {

			mensagem = circuit.getRoute().getDistanceAllLinks() + "/" + 
			//3 + "/" + 
			circuit.getRoute().getHops() + "/" + //number of hops
			circuit.getSpectrumUtilizationAbsolut()[0] + "/" + 
			circuit.getSpectrumUtilizationAbsolut()[1] + "/" +
			circuit.getSpectrumUtilizationAbsolut()[2] + "/" +
			circuit.getSpectrumUtilizationAbsolut()[3] + "/" +
			circuit.getSpectrumUtilizationAbsolut()[4] + "/" +
			circuit.getSpectrumUtilizationAbsolut()[5] + "/" +
			circuit.getSpectrumUtilizationAbsolut()[6] + "/" +
			//circuit.getAverageXt()[0] + "/" + //XT do core 0
			//circuit.getAverageXt()[1] + "/" + //XT do core 0
			//circuit.getAverageXt()[2] + "/" + //XT do core 0
			//circuit.getAverageXt()[3] + "/" + //XT do core 0
			//circuit.getAverageXt()[4] + "/" + //XT do core 0
			//circuit.getAverageXt()[5] + "/" + //XT do core 0
			//circuit.getAverageXt()[6] + "/" + //XT do core 0
			circuit.getModulation().getM();
	
			//contCore1--;
		
		//}else {
			/*message = circuit.getRoute().getDistanceAllLinks() + "/" + 
			//3 + "/" + 
			1000 + "/" + 
			0 + "/" +
			1000 + "/" +
			0 + "/" +
			0 + "/" +
			0 + "/" +
			1000 + "/" +
			circuit.getAverageXt()[0] + "/" + //XT do core 0
			circuit.getAverageXt()[1] + "/" + //XT do core 0
			circuit.getAverageXt()[2] + "/" + //XT do core 0
			circuit.getAverageXt()[3] + "/" + //XT do core 0
			circuit.getAverageXt()[4] + "/" + //XT do core 0
			circuit.getAverageXt()[5] + "/" + //XT do core 0
			circuit.getAverageXt()[6] + "/" + //XT do core 0
			circuit.getModulation().getM();*/
			
			//contCore1--;
			//return randon.nextInt(6);
		//}

		//System.out.println(message);
		//System.out.println("Average XT: "+circuit.getAverageXt()[0]+"-"+circuit.getAverageXt()[1]+"-"+circuit.getAverageXt()[2]+"-"+circuit.getAverageXt()[3]+"-"+circuit.getAverageXt()[4]+"-"+circuit.getAverageXt()[5]+"-"+circuit.getAverageXt()[6]+"-");
		
		Socket cliente = null;
		try {
			cliente = new Socket("127.0.0.1", 7766);
		} catch (UnknownHostException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		//System.out.println("enviando dados ao servidor");
		// Enviando dados for the servidor
		PrintStream saida = null;
		try {
			saida = new PrintStream(cliente.getOutputStream());
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		saida.println(mensagem);

		//System.out.println("pegando return do servidor");
		//Pegando return do servidor
		Scanner s = null;
		try {
			//System.out.println("teste11");
			//System.out.println(s==null);
			//s = new Scanner(cliente.getInputStream());
			s = new Scanner(cliente.getInputStream());
			//System.out.println("S: " + s);
			//System.out.println("teste22");
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		String restorno_servidor = s.nextLine();
		restorno_servidor = restorno_servidor.substring(restorno_servidor.length() - 1);
		
		//System.out.println(geral);

		s.close();
		saida.close();
		try {
			cliente.close();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		//System.out.println("teste2");
		core = Integer.parseInt(restorno_servidor);
		
		if(core==0) {
			//System.out.println(core);
			return randon.nextInt(numCore)+1;
		}else {
			//System.out.println(core);
			return core;
		}
		//return randon.nextInt(6);
		
	}
	
	@Override
	public int[] policy(int numberOfSlots, List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp) {
		Random rand = new Random();
		
		//Core impar
		if(coreOfTheTime%2 == 1) {
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
		                
		                //circuitCandidate = circuit;
		                
		                circuitCandidate.setPair(circuit.getPair());
		                circuitCandidate.setRequests(circuit.getRequests());
		                circuitCandidate.setRoute(circuit.getRoute());
		        		circuitCandidate.setModulation(circuit.getModulation());
		        		circuitCandidate.setGuardBand(circuit.getGuardBand());
		        		
		                circuitCandidate.setSpectrumAssigned(chosen);
		                circuitCandidate.setIndexCore(coreOfTheTime);
		                circuitCandidate.setXt(cp.getMesh().getPhysicalLayer().getCrosstalk().calculateCrosstalk(circuitCandidate, null, false));
		                
		                if (circuitCandidate.getXt() != 0) {
		                	if(circuitCandidate.getXtAdmissible()) {
		                		//if(crosstalk.isAdmissibleInOthers(circuitCandidate)) {
		                			//deltaXtCandidate = cp.getMesh().getCrosstalk().xtThreshold(circuitCandidate.getModulation()) - circuitCandidate.getXt();
		                		xtCandidate = circuitCandidate.getXt(); 	
		                		if(xtCandidate < xtBestCandidate) {
	                				circuitBestCandidate.setSpectrumAssigned(circuitCandidate.getSpectrumAssigned());
	                				circuitBestCandidate.setIndexCore(coreOfTheTime);
	                				xtBestCandidate = xtCandidate;		                				
		                		}
		                		//}
		                	}
		                }else {
		                	circuitBestCandidate.setSpectrumAssigned(circuitCandidate.getSpectrumAssigned());
		                	circuitBestCandidate.setIndexCore(coreOfTheTime);
		                	return chosen;		                	 
		                }	           		                
		            }
				}	            
	        }
		}
		
		//Core par
		if(coreOfTheTime>0 && coreOfTheTime%2 == 0) {
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
		                
		                //circuitCandidate = circuit;
		                
		                circuitCandidate.setPair(circuit.getPair());
		                circuitCandidate.setRequests(circuit.getRequests());
		                circuitCandidate.setRoute(circuit.getRoute());
		        		circuitCandidate.setModulation(circuit.getModulation());
		        		circuitCandidate.setGuardBand(circuit.getGuardBand());
		        		
		                circuitCandidate.setSpectrumAssigned(chosen);
		                circuitCandidate.setIndexCore(coreOfTheTime);
		                circuitCandidate.setXt(cp.getMesh().getPhysicalLayer().getCrosstalk().calculateCrosstalk(circuitCandidate, null, false));
		                
		                if (circuitCandidate.getXt() != 0) {
		                	if(circuitCandidate.getXtAdmissible()) {
		                		//if(crosstalk.isAdmissibleInOthers(circuitCandidate)) {
		                			//deltaXtCandidate = cp.getMesh().getCrosstalk().xtThreshold(circuitCandidate.getModulation()) - circuitCandidate.getXt();
		                		xtCandidate = circuitCandidate.getXt();	
		                		if(xtCandidate <= xtBestCandidate) {
	                				circuitBestCandidate.setSpectrumAssigned(circuitCandidate.getSpectrumAssigned());
	                				circuitBestCandidate.setIndexCore(coreOfTheTime);
	                				xtBestCandidate = xtCandidate;		                				
		                		}
		                		//}
		                	}
		                }else {
		                	circuitBestCandidate.setSpectrumAssigned(circuitCandidate.getSpectrumAssigned());
		                	circuitBestCandidate.setIndexCore(coreOfTheTime);
		                	return chosen;		                	 
		                }	           		                
		            }
				}	            
	        }
		}
		
		//Central Core
		if (coreOfTheTime == 0 && circuitBestCandidate.getSpectrumAssigned()==null) {
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
		                
		                //circuitCandidate = circuit;
		                
		                circuitCandidate.setPair(circuit.getPair());
		                circuitCandidate.setRequests(circuit.getRequests());
		                circuitCandidate.setRoute(circuit.getRoute());
		        		circuitCandidate.setModulation(circuit.getModulation());
		        		circuitCandidate.setGuardBand(circuit.getGuardBand());
		        		
		                circuitCandidate.setSpectrumAssigned(chosen);
		                circuitCandidate.setIndexCore(coreOfTheTime);
		                circuitCandidate.setXt(cp.getMesh().getPhysicalLayer().getCrosstalk().calculateCrosstalk(circuitCandidate, null, false));
		                
		                if (circuitCandidate.getXt() != 0) {
		                	if(circuitCandidate.getXtAdmissible()) {
		                		if(cp.getMesh().getPhysicalLayer().getCrosstalk().isAdmissibleInOthers(circuitCandidate)) {
		                			xtCandidate = circuitCandidate.getXt();	
			                		if((xtCandidate <= xtBestCandidate) && (rand.nextBoolean())) {
		                				circuitBestCandidate.setSpectrumAssigned(circuitCandidate.getSpectrumAssigned());
		                				circuitBestCandidate.setIndexCore(coreOfTheTime);
		                				xtBestCandidate = xtCandidate;		                				
		                			}
		                		}
		                	}
		                }else {
		                	circuitBestCandidate.setSpectrumAssigned(circuitCandidate.getSpectrumAssigned());
		                	circuitBestCandidate.setIndexCore(coreOfTheTime);
		                	return chosen;		                	 
		                }	           		                
		            }
				}	            
	        }
		}
		
		return circuitBestCandidate.getSpectrumAssigned();
	}
	
//	private boolean xtNeighborIsAdmissible(Circuit circuitCandidate) {
//		ArrayList<Core> adjacentsCores = circuitCandidate.getRoute().getLink(0).coresAdjacents(circuitCandidate.getIndexCore());
//		
//		for(Core neighbor : adjacentsCores) {
//			Circuit circuitJ = new Circuit();
//			circuitJ.setIndexCore(neighbor.getId());
//			circuitJ.setRoute(circuitCandidate.getRoute());
//			circuitJ.sets
//					
//		}
//		
//		return true;
//	}
	
	// Spectrum Utilization Absolute
	private void spectrumUtilizationAbsolute (Circuit circuit, ControlPlane cp) {
		Vector<Link> listaDeLinks;
		ArrayList<Core> coresAdjacentes;
		
		listaDeLinks = circuit.getRoute().getLinkList();
		
		//for (int a = 0; a < this.numberOfslots; a++) {
		//	circuit.refreshSpectrumUtilizationAbsolutUnit(a, 0);;
		//}
		
		for (int i = 0; i<this.numberOfCores; i++) {
			//listaDeLinks = null;
			//coresAdjacentes = null;
			
			coresAdjacentes = circuit.getRoute().getLink(0).getAdjacentCores(i);
			
			for (Link link : listaDeLinks) {
				for (Core coreadj : link.getCores()) {
					if (coresAdjacentes.contains(coreadj)) {
						//circuit.refreshSpectrumUtilizationAbsolutUnit(i, (circuit.getSpectrumUtilizationAbsolut()[i] + 1));
						circuit.refreshSpectrumUtilizationAbsolutUnit(i, circuit.getSpectrumUtilizationAbsolut()[i] + coreadj.getUsedSlots());
						//this.spectrumUtilizationAbsolut[i] = this.spectrumUtilizationAbsolut[i] + coreadj.getUsedslots();
					}
				}				
			}
		}
	}
	
	private void spectrumUtilizationAbsolute2 (Circuit circuit, ControlPlane cp) {
		Vector<Link> listaDeLinks;
		//ArrayList<Integer> coresAdjacentes;
		circuit.refreshSpectrumUtilizationAbsolutUnit(0, 0);
		circuit.refreshSpectrumUtilizationAbsolutUnit(1, 0);
		circuit.refreshSpectrumUtilizationAbsolutUnit(2, 0);
		circuit.refreshSpectrumUtilizationAbsolutUnit(3, 0);
		circuit.refreshSpectrumUtilizationAbsolutUnit(4, 0);
		circuit.refreshSpectrumUtilizationAbsolutUnit(5, 0);
		circuit.refreshSpectrumUtilizationAbsolutUnit(6, 0);
		
		listaDeLinks = circuit.getRoute().getLinkList();
		
		//for (int a = 0; a < this.numberOfslots; a++) {
		//	circuit.refreshSpectrumUtilizationAbsolutUnit(a, 0);;
		//}
		for(Link link : listaDeLinks) {
			for (int i = 0; i<this.numberOfCores; i++) {
				int quantSlots = 0;
				quantSlots = cp.getMesh().getLink(link.getSource().getName(), link.getDestination().getName()).getCore(i).getSpectrum().getUsedSlots();
				circuit.refreshSpectrumUtilizationAbsolutUnit(i, circuit.getSpectrumUtilizationAbsolut()[i] + quantSlots);
			}
		}
	}
	
	private void calculeAverageXt (Circuit circuit, ControlPlane cp) {
		Vector<Link> listaDeLinks;
		//ArrayList<Integer> coresAdjacentes;
		circuit.refreshAverageXt(0, 0);
		circuit.refreshAverageXt(1, 0);
		circuit.refreshAverageXt(2, 0);
		circuit.refreshAverageXt(3, 0);
		circuit.refreshAverageXt(4, 0);
		circuit.refreshAverageXt(5, 0);
		circuit.refreshAverageXt(6, 0);
		
		listaDeLinks = circuit.getRoute().getLinkList();
		
		for (int i = 0; i<this.numberOfCores; i++) {
			double sumXT = 0.0;
			int cont = 0;
			double averageXT = 0.0;
			
			for(Link link : listaDeLinks) {	
				for(Circuit circuitAlocated : link.getCore(i).getCircuitList()) {
					//System.out.println(circuitAlocated.getXt());
					sumXT = sumXT + circuitAlocated.getXt();
					cont++;
				}
			}
			
			averageXT =  sumXT/cont;
			if(cont==0) {
				averageXT = sumXT;
			}
			
			circuit.refreshAverageXt(i,averageXT);
		}
	}

}
