package gprmcsa.coreSpectrumAssignment;

import java.util.ArrayList;
import java.util.List;
import network.Circuit;
import network.ControlPlane;
import util.IntersectionFreeSpectrum;

/**
 * Represents the Pgnie component.
 */
public class Pgnie implements CoreAndSpectrumAssignmentAlgorithmInterface {

	private int numberOfCores;
	private int cont = 1;
	private int tentativa;
	private int limite1 = 107;
	private int limite2 = 214;
	ArrayList<Integer> group1;
	ArrayList<Integer> group2;
	ArrayList<Integer> group3;
	
	/**
	 * Creates a new Pgnie instance.
	 */
	public Pgnie() {
		this.numberOfCores = -1;
		
		group1 = new ArrayList<Integer>();
		group2 = new ArrayList<Integer>();
		group3 = new ArrayList<Integer>();
	}
	
	/**
	 * Executes the assignCoreAndSpectrum operation.
	 * @param numberOfslots the numberOfslots.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @return the result of the operation.
	 */
	public boolean assignCoreAndSpectrum(int numberOfSlots, Circuit circuit, ControlPlane cp) {
		if (numberOfCores == -1) {
			numberOfCores = cp.getMesh().getLinkList().get(0).getNumberOfCores();
			
			if (numberOfCores == 7) {
				group1.add(1);
				group1.add(3);
				group1.add(5);

				group2.add(2);
				group2.add(4);
				group2.add(6);

				group3.add(0);
				
			}else if(numberOfCores == 12) {
				group1.add(0);
				group1.add(3);
				group1.add(6);
				group1.add(9);

				group2.add(1);
				group2.add(4);
				group2.add(7);
				group2.add(10);

				group3.add(2);
				group3.add(5);
				group3.add(8);
				group3.add(11);
				
			}else if(numberOfCores == 19) {
				group1.add(0);
				group1.add(5);
				group1.add(8);
				group1.add(10);
				group1.add(13);
				group1.add(15);

				group2.add(1);
				group2.add(3);
				group2.add(6);
				group2.add(11);
				group2.add(14);
				group2.add(16);
				group2.add(18);

				group3.add(2);
				group3.add(4);
				group3.add(7);
				group3.add(9);
				group3.add(12);
				group3.add(17);
			}
		}
	  
		this.tentativa = 1;
	  
		int chosenCore = coreAssignment(numberOfSlots, circuit);
		int[] chosen = null;
		List<int[]> composition = IntersectionFreeSpectrum.merge(circuit.getRoute(), circuit.getGuardBand(), chosenCore);
	
		for (int i = 0; i < 2; ) {
			if(group1.contains(chosenCore)) {
				chosen = policy(numberOfSlots, composition, circuit, cp);
				
			}else if(group2.contains(chosenCore)) {
				chosen = policy2(numberOfSlots, composition, circuit, cp);
				
			}else if(group3.contains(chosenCore)) {
				chosen = policy3(numberOfSlots, composition, circuit, cp);
			}
			
			if (chosen == null) {
				this.tentativa = 2;
				chosenCore = coreAssignment(numberOfSlots, circuit);
				composition = IntersectionFreeSpectrum.merge(circuit.getRoute(), circuit.getGuardBand(), chosenCore);
				i++;
			} else {
				break;
			}
		}
		
		circuit.setIndexCore(chosenCore);
		circuit.setSpectrumAssigned(chosen);
		
		if (chosen == null)
			return false;
    
		return true;
	}
  
	/**
	 * Executes the coreAssignment operation.
	 * @param numberOfslots the numberOfslots.
	 * @param circuit the circuit.
	 * @return the result of the operation.
	 */
	public int coreAssignment(int numberOfSlots, Circuit circuit) {

		if(numberOfCores == 7) {
			return coreAssignment7(numberOfSlots, circuit);

		}else if(numberOfCores == 12) {
			return coreAssignment12(numberOfSlots, circuit);

		}if(numberOfCores == 19) {
			return coreAssignment19(numberOfSlots, circuit);
		}

		return 0;
	}

	/**
	 * Executes the coreAssignment7 operation.
	 * @param numberOfslots the numberOfslots.
	 * @param circuit the circuit.
	 * @return the result of the operation.
	 */
	public int coreAssignment7(int numberOfSlots, Circuit circuit) {
		int[] cores = new int[numberOfCores];
		if (this.cont == 4)
			this.cont = 1;

		switch (this.cont) {
		case 1:
			cores[0] = 1;
			cores[1] = 3;
			cores[2] = 5;
			cores[3] = 4;
			cores[4] = 6;
			cores[5] = 2;
			cores[6] = 0;
			break;
		case 2:
			cores[0] = 3;
			cores[1] = 5;
			cores[2] = 1;
			cores[3] = 6;
			cores[4] = 2;
			cores[5] = 4;
			cores[6] = 0;
			break;
		case 3:
			cores[0] = 5;
			cores[1] = 1;
			cores[2] = 3;
			cores[3] = 2;
			cores[4] = 4;
			cores[5] = 6;
			cores[6] = 0;
			break;
		} 
		this.cont = this.cont + 1;

		int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
		if (numberOfSlots > maxAmplitude)
			return 0;
		
		byte b;
		int i, arrayOfInt1[];
		for (i = (arrayOfInt1 = cores).length, b = 0; b < i; ) {
			int core = arrayOfInt1[b];
			List<int[]> freeSpectrumBands = IntersectionFreeSpectrum.merge(circuit.getRoute(), circuit.getGuardBand(), core);
			for (int[] band : freeSpectrumBands) {
				if(group1.contains(core)) {
					if (this.tentativa == 1) {
						if (band[0] + numberOfSlots - 1 < this.limite1 && band[1] - band[0] + 1 >= numberOfSlots)
							return core; 
						continue;
					} 
					if (band[0] >= this.limite1 && band[1] - band[0] + 1 >= numberOfSlots)
						return core;
					
				}else if(group2.contains(core)) {
					if (this.tentativa == 1) {
						if (band[0] > this.limite1 && band[0] + numberOfSlots - 1 < this.limite2 && band[1] - band[0] + 1 >= numberOfSlots)
							return core;
						continue;
					}
					if ((band[0] + numberOfSlots - 1 < this.limite1 || band[0] >= this.limite2) && band[1] - band[0] + 1 >= numberOfSlots)
						return core;
					
				}else if(group3.contains(core)) {
					if (this.tentativa == 1) {
						if (band[0] >= this.limite2 && band[1] - band[0] + 1 >= numberOfSlots)
							return core; 
						continue;
					} 
					if (band[0] + numberOfSlots - 1 < this.limite2 && band[1] - band[0] + 1 >= numberOfSlots)
						return core;
				}
			}
			b++;
		} 
		return 0;
	}

	/**
	 * Executes the coreAssignment12 operation.
	 * @param numberOfslots the numberOfslots.
	 * @param circuit the circuit.
	 * @return the result of the operation.
	 */
	public int coreAssignment12(int numberOfSlots, Circuit circuit) {
		int[] cores = new int[numberOfCores];
		if (this.cont == 4)
			this.cont = 1; 
		switch (this.cont) {
		case 1:
			cores[0] = 0;
			cores[1] = 3;
			cores[2] = 6;
			cores[3] = 9;
			cores[4] = 1;
			cores[5] = 4;
			cores[6] = 7;
			cores[7] = 10;
			cores[8] = 2;
			cores[9] = 5;
			cores[10] = 8;
			cores[11] = 11;
			break;
		case 2:
			cores[0] = 3;
			cores[1] = 6;
			cores[2] = 9;
			cores[3] = 0;
			cores[4] = 4;
			cores[5] = 7;
			cores[6] = 10;
			cores[7] = 1;
			cores[8] = 5;
			cores[9] = 8;
			cores[10] = 11;
			cores[11] = 2;
			break;
		case 3:
			cores[0] = 6;
			cores[1] = 9;
			cores[2] = 0;
			cores[3] = 3;
			cores[4] = 7;
			cores[5] = 10;
			cores[6] = 1;
			cores[7] = 4;
			cores[8] = 8;
			cores[9] = 11;
			cores[10] = 2;
			cores[11] = 5;
			break;
		} 

		this.cont = this.cont + 1;
		int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
		if (numberOfSlots > maxAmplitude)
			return 0; 

		byte b;
		int i, arrayOfInt1[];
		for (i = (arrayOfInt1 = cores).length, b = 0; b < i; ) {
			int core = arrayOfInt1[b];
			List<int[]> freeSpectrumBands = IntersectionFreeSpectrum.merge(circuit.getRoute(), circuit.getGuardBand(), core);
			for (int[] band : freeSpectrumBands) {
				if(group1.contains(core)) {
					if (this.tentativa == 1) {
						if (band[0] + numberOfSlots - 1 < this.limite1 && band[1] - band[0] + 1 >= numberOfSlots)
							return core; 
						continue;
					} 
					if (band[0] >= this.limite1 && band[1] - band[0] + 1 >= numberOfSlots)
						return core; 
					
				}else if(group2.contains(core)) {
					if (this.tentativa == 1) {
						if (band[0] > this.limite1 && band[0] + numberOfSlots - 1 < this.limite2 && band[1] - band[0] + 1 >= numberOfSlots)
							return core;
						continue;
					}
					if ((band[0] + numberOfSlots - 1 < this.limite1 || band[0] >= this.limite2) && band[1] - band[0] + 1 >= numberOfSlots)
						return core;
					
				}else if(group3.contains(core)) {
					if (this.tentativa == 1) {
						if (band[0] >= this.limite2 && band[1] - band[0] + 1 >= numberOfSlots)
							return core; 
						continue;
					} 
					if (band[0] + numberOfSlots - 1 < this.limite2 && band[1] - band[0] + 1 >= numberOfSlots)
						return core;
				}
			} 
			b++;
		} 
		return 0;
	}

	/**
	 * Executes the coreAssignment19 operation.
	 * @param numberOfslots the numberOfslots.
	 * @param circuit the circuit.
	 * @return the result of the operation.
	 */
	public int coreAssignment19(int numberOfSlots, Circuit circuit) {
		int[] cores = new int[numberOfCores];
		if (this.cont == 4)
			this.cont = 1; 
		switch (this.cont) {
		case 1:
			cores[0] = 0;
			cores[1] = 5;
			cores[2] = 8;
			cores[3] = 10;
			cores[4] = 13;
			cores[5] = 15;
			cores[6] = 1;
			cores[7] = 3;
			cores[8] = 6;
			cores[9] = 11;
			cores[10] = 14;
			cores[11] = 16;
			cores[12] = 18;
			cores[13] = 2;
			cores[14] = 4; 
			cores[15] = 7;
			cores[16] = 9;
			cores[17] = 12;
			cores[18] = 17;
			break;
		case 2:
			cores[0] = 5;
			cores[1] = 8;
			cores[2] = 10;
			cores[3] = 13;
			cores[4] = 15;
			cores[5] = 0;
			cores[6] = 3;
			cores[7] = 6;
			cores[8] = 11;
			cores[9] = 14;
			cores[10] = 16;
			cores[11] = 18;
			cores[12] = 1;
			cores[13] = 4;
			cores[14] = 7;
			cores[15] = 9;
			cores[16] = 12;
			cores[17] = 17;
			cores[18] = 2;
			break;
		case 3:
			cores[0] = 8;
			cores[1] = 10;
			cores[2] = 13;
			cores[3] = 15;
			cores[4] = 0;
			cores[5] = 5;
			cores[6] = 6;
			cores[7] = 11;
			cores[8] = 14;
			cores[9] = 16;
			cores[10] = 18;
			cores[11] = 1;
			cores[12] = 3;
			cores[13] = 7;
			cores[14] = 9;
			cores[15] = 12;
			cores[16] = 17;
			cores[17] = 2;
			cores[18] = 4;
			break;
		} 

		this.cont = this.cont + 1;
		int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
		if (numberOfSlots > maxAmplitude)
			return 0;

		byte b;
		int i, arrayOfInt1[];
		for (i = (arrayOfInt1 = cores).length, b = 0; b < i; ) {
			int core = arrayOfInt1[b];
			List<int[]> freeSpectrumBands = IntersectionFreeSpectrum.merge(circuit.getRoute(), circuit.getGuardBand(), core);
			for (int[] band : freeSpectrumBands) {
				if(group1.contains(core)) {
					if (this.tentativa == 1) {
						if (band[0] + numberOfSlots - 1 < this.limite1 && band[1] - band[0] + 1 >= numberOfSlots)
							return core; 
						continue;
					} 
					if (band[0] >= this.limite1 && band[1] - band[0] + 1 >= numberOfSlots)
						return core;
					
				}else if(group2.contains(core)) {
					if (this.tentativa == 1) {
						if (band[0] > this.limite1 && band[0] + numberOfSlots - 1 < this.limite2 && band[1] - band[0] + 1 >= numberOfSlots)
							return core;
						continue;
					}
					if ((band[0] + numberOfSlots - 1 < this.limite1 || band[0] >= this.limite2) && band[1] - band[0] + 1 >= numberOfSlots)
						return core;
					
				}else if(group3.contains(core)) {
					if (this.tentativa == 1) {
						if (band[0] >= this.limite2 && band[1] - band[0] + 1 >= numberOfSlots)
							return core; 
						continue;
					} 
					if (band[0] + numberOfSlots - 1 < this.limite2 && band[1] - band[0] + 1 >= numberOfSlots)
						return core;
				} 
			} 
			b++;
		} 
		return 0;
	}
	
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
		if (numberOfSlots > maxAmplitude)
			return null; 
		int[] chosen = null;
		if (this.tentativa == 1) {
			for (int[] band : freeSpectrumBands) {
				if (band[0] + numberOfSlots - 1 < this.limite1 && band[1] - band[0] + 1 >= numberOfSlots) {
					chosen = (int[])band.clone();
					chosen[1] = chosen[0] + numberOfSlots - 1;
					break;
				} 
			} 
		} else {
			int[] band = null;
			for (int i = freeSpectrumBands.size() - 1; i >= 0; i--) {
				band = freeSpectrumBands.get(i);
				if (band[0] >= this.limite1 && band[1] - band[0] + 1 >= numberOfSlots) {
					chosen = (int[])band.clone();
					chosen[0] = chosen[1] - numberOfSlots + 1;
					break;
				} 
			} 
		} 
		return chosen;
	}

	/**
	 * Executes the policy2 operation.
	 * @param numberOfslots the numberOfslots.
	 * @param freeSpectrumBands the freeSpectrumBands.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @return the result of the operation.
	 */
	public int[] policy2(int numberOfSlots, List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp) {
		int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
		if (numberOfSlots > maxAmplitude)
			return null; 
		int[] chosen = null;
		if (this.tentativa == 1) {
			for (int[] band : freeSpectrumBands) {
				if (band[0] > this.limite1 && band[0] + numberOfSlots - 1 < this.limite2 && 
						band[1] - band[0] + 1 >= numberOfSlots) {
					chosen = (int[])band.clone();
					chosen[1] = chosen[0] + numberOfSlots - 1;
					break;
				} 
			} 
		} else {
			int[] band = null;
			for (int i = freeSpectrumBands.size() - 1; i >= 0; i--) {
				band = freeSpectrumBands.get(i);
				if ((band[0] + numberOfSlots - 1 < this.limite1 || band[0] >= this.limite2) && 
						band[1] - band[0] + 1 >= numberOfSlots) {
					chosen = (int[])band.clone();
					chosen[0] = chosen[1] - numberOfSlots + 1;
					break;
				} 
			} 
		} 
		return chosen;
	}
	
	/**
	 * Executes the policy3 operation.
	 * @param numberOfslots the numberOfslots.
	 * @param freeSpectrumBands the freeSpectrumBands.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @return the result of the operation.
	 */
	public int[] policy3(int numberOfSlots, List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp) {
		int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
		if (numberOfSlots > maxAmplitude)
			return null; 
		int[] chosen = null;
		if (this.tentativa == 1) {
			for (int[] band : freeSpectrumBands) {
				if (band[0] >= this.limite2 && band[1] - band[0] + 1 >= numberOfSlots) {
					chosen = (int[])band.clone();
					chosen[1] = chosen[0] + numberOfSlots - 1;
					break;
				} 
			} 
		} else {
			int[] band = null;
			for (int i = freeSpectrumBands.size() - 1; i >= 0; i--) {
				band = freeSpectrumBands.get(i);
				if (band[0] + numberOfSlots - 1 < this.limite2 && band[1] - band[0] + 1 >= numberOfSlots) {
					chosen = (int[])band.clone();
					chosen[0] = chosen[1] - numberOfSlots + 1;
					break;
				} 
			} 
		} 
		return chosen;
	}

	@Override
	public int coreAssignment() {
		return 0;
	}

}

