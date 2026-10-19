package gprmcsa.coreSpectrumAssignment;

import java.util.ArrayList;
import java.util.List;

import network.Circuit;
import network.ControlPlane;
import util.IntersectionFreeSpectrum;

/**
 * Represents the Adein component.
 */
public class Adein implements CoreAndSpectrumAssignmentAlgorithmInterface {

	private static final int DEFAULTPOINTFORCENTRALCORE = 15; // INITIAL SCORE FOR THE CENTRAL CORE
	private static final int DEFAULTPOINTFOROTHERCORE = 10; // INITIAL SCORE FOR EDGE CORES

	private int numberOfCores; // Number of cores in the fiber
	private int centralCoreId; // ID do core Central
	private int[] pointArray; // Core score table
	private int[] coreUse; // Quantas vezes cada core foi used.
	
	ArrayList<Integer> groupBlue;
	ArrayList<Integer> groupOrange;
	ArrayList<Integer> groupGreen;
	ArrayList<Integer> groupBlack;

	/**
	 * Creates a new Adein instance.
	 */
	public Adein() {
		this.numberOfCores = -1;
		
		groupBlue = new ArrayList<Integer>();
		groupOrange = new ArrayList<Integer>();
		groupGreen = new ArrayList<Integer>();
		groupBlack = new ArrayList<Integer>();
	}

	@Override
	public boolean assignCoreAndSpectrum(int numberOfSlots, Circuit circuit, ControlPlane cp) {
		if (numberOfCores == -1) {
			numberOfCores = cp.getMesh().getLinkList().get(0).getNumberOfCores();

			if (numberOfCores == 7) {
				centralCoreId = 0;
				
				groupBlue.add(1);
				groupBlue.add(4);
				
				groupOrange.add(2);
				groupOrange.add(5);
				
				groupGreen.add(3);
				groupGreen.add(6);
				
				groupBlack.add(0);
				
			}else if (numberOfCores == 12) {
				centralCoreId = 0;
				
				groupBlue.add(0);
				groupBlue.add(8);
				groupBlue.add(3);
				groupBlue.add(11);
				
				groupOrange.add(1);
				groupOrange.add(9);
				groupOrange.add(4);
				groupOrange.add(6);
				
				groupGreen.add(2);
				groupGreen.add(10);
				groupGreen.add(5);
				
				groupBlack.add(7);
				
			}else if (numberOfCores == 19) {
				centralCoreId = 18;
				
				groupBlue.add(0);
				groupBlue.add(2);
				groupBlue.add(4);
				groupBlue.add(6);
				groupBlue.add(16);
				groupBlue.add(17);
				
				groupOrange.add(1);
				groupOrange.add(3);
				groupOrange.add(5);
				groupOrange.add(13);
				groupOrange.add(14);
				groupOrange.add(15);
				
				groupGreen.add(7);
				groupGreen.add(8);
				groupGreen.add(9);
				groupGreen.add(10);
				groupGreen.add(11);
				groupGreen.add(12);
				
				groupBlack.add(18);
			}

			// Inicializando table de points e defining marcas de espectro.
			pointArray = new int[numberOfCores];
			coreUse = new int[numberOfCores];

			for (int i = 0; i < numberOfCores; i++) {
				pointArray[i] = DEFAULTPOINTFOROTHERCORE;
				coreUse[i] = 0;
			}
			pointArray[centralCoreId] = DEFAULTPOINTFORCENTRALCORE;
		}

		// Defining Ncleo
		int chosenCore = coreAssignment();
		circuit.setIndexCore(chosenCore);
		// Declarando Varivel de Espectro Chosen.
		int specChosen[] = null;
		// Searching the spectral composition of the chosen core.
		List<int[]> composition = IntersectionFreeSpectrum.merge(circuit.getRoute(), circuit.getGuardBand(), chosenCore);
		// Realizando Tentativa de Aloca com Primeira Poltica.
		specChosen = policy(numberOfSlots, composition, circuit, cp);
		// Realizando Tentativa de Aloca com Segunda Poltica.
		if (specChosen == null)
			specChosen = policy2(numberOfSlots, composition, circuit, cp);

		circuit.setSpectrumAssigned(specChosen);

		if (specChosen == null)
			return false;
		else
			return true;
	}

	@Override
	public int coreAssignment() {
		int chosenCore = checkPointTable(); // Choosing Ncleo Baseado na Table de Points.
		pointTableUpdate(chosenCore); // Atualizando Table de Points.
		return chosenCore;
	}

	private void pointTableUpdate(int currentCore) {
		
		if (currentCore == centralCoreId) { // Central core.
			pointArray[centralCoreId] = DEFAULTPOINTFORCENTRALCORE;
			for (int i = 0; i < numberOfCores; i++) {
				if(i != centralCoreId) {
					pointArray[i]--;
				}
			}
			
		} else { // Other cores.
			if(numberOfCores == 7) {
				pointTableUpdate7(currentCore);
				
			}else if(numberOfCores == 12) {
				pointTableUpdate12(currentCore);
				
			}else if(numberOfCores == 19) {
				pointTableUpdate19(currentCore);
			}
		}
	}
	
	private void pointTableUpdate7(int currentCore) {
		int oppositeCore;
		int adjaLeftCore;
		int adjaRightCore;
		int adOposLeftCore;
		int adOposRightCore;
		
		// Defining Ncleo Oposto
		if (currentCore < 4)
			oppositeCore = currentCore + 3;
		else
			oppositeCore = currentCore - 3;
		
		// Defining Ncleo Adjacente Esquerdo
		if (currentCore == 1)
			adjaLeftCore = 6;
		else
			adjaLeftCore = currentCore - 1;
		
		// Defining Ncleo Adjacente Direito
		if (currentCore == 6)
			adjaRightCore = 1;
		else
			adjaRightCore = currentCore + 1;
		
		// Defining Ncleo Adjacente Oposto Esquerdo
		if (oppositeCore == 1)
			adOposLeftCore = 6;
		else
			adOposLeftCore = oppositeCore - 1;
		
		// Defining Ncleo Adjacente Oposto Direito
		if (oppositeCore == 6)
			adOposRightCore = 1;
		else
			adOposRightCore = oppositeCore + 1;

		// Verificando Ncleos for Reset
		for (int i = 0; i < numberOfCores; i++) {
			if(i != centralCoreId) {
				if (pointArray[i] <= 0) {
					pointArray[i] = DEFAULTPOINTFOROTHERCORE;
				}
			}
		}

		// Aplicando Table de Points
		pointArray[currentCore] = pointArray[currentCore] + 1; // Ncleo Atual
		pointArray[centralCoreId] = pointArray[centralCoreId] - 1; // Ncleo Central
		pointArray[oppositeCore] = pointArray[oppositeCore] - 1; // Nleo Oposto
		pointArray[adjaLeftCore] = pointArray[adjaLeftCore] - 2; // Ncleo Adjacente Esquerdo
		pointArray[adjaRightCore] = pointArray[adjaRightCore] - 2; // Ncleo Adjacente Direito
		pointArray[adOposLeftCore] = pointArray[adOposLeftCore] - 3; // Ncleo Adjacente Oposto Esquerdo
		pointArray[adOposRightCore] = pointArray[adOposRightCore] - 3; // Ncleo Adjacente Oposto Direito
	}
	
	private void pointTableUpdate12(int currentCore) {
		int oppositeCore = 0;
		int adjaLeftCore = 0;
		int adjaRightCore = 0;
		int adOposLeftCore = 0;
		int adOposRightCore = 0;
		
		if(currentCore == 0) {
			adjaLeftCore = 11;
			adjaRightCore = 1;
			oppositeCore = 6;
			adOposLeftCore = 7;
			adOposRightCore = 5;
		}
		else if(currentCore == 1) {
			adjaLeftCore = 0;
			adjaRightCore = 2;
			oppositeCore = 7;
			adOposLeftCore = 8;
			adOposRightCore = 6;
		}
		else if(currentCore == 2) {
			adjaLeftCore = 1;
			adjaRightCore = 3;
			oppositeCore = 8;
			adOposLeftCore = 9;
			adOposRightCore = 7;
		}
		else if(currentCore == 3) {
			adjaLeftCore = 2;
			adjaRightCore = 4;
			oppositeCore = 9;
			adOposLeftCore = 10;
			adOposRightCore = 8;
		}
		else if(currentCore == 4) {
			adjaLeftCore = 3;
			adjaRightCore = 5;
			oppositeCore = 10;
			adOposLeftCore = 11;
			adOposRightCore = 9;
		}
		else if(currentCore == 5) {
			adjaLeftCore = 4;
			adjaRightCore = 6;
			oppositeCore = 11;
			adOposLeftCore = 0;
			adOposRightCore = 10;
		}
		else if(currentCore == 6) {
			adjaLeftCore = 5;
			adjaRightCore = 7;
			oppositeCore = 0;
			adOposLeftCore = 11;
			adOposRightCore = 1;
		}
		else if(currentCore == 7) {
			adjaLeftCore = 6;
			adjaRightCore = 8;
			oppositeCore = 1;
			adOposLeftCore = 0;
			adOposRightCore = 2;
		}
		else if(currentCore == 8) {
			adjaLeftCore = 9;
			adjaRightCore = 7;
			oppositeCore = 2;
			adOposLeftCore = 1;
			adOposRightCore = 3;
		}
		else if(currentCore == 9) {
			adjaLeftCore = 10;
			adjaRightCore = 8;
			oppositeCore = 3;
			adOposLeftCore = 2;
			adOposRightCore = 4;
		}
		else if(currentCore == 10) {
			adjaLeftCore = 11;
			adjaRightCore = 9;
			oppositeCore = 4;
			adOposLeftCore = 3;
			adOposRightCore = 5;
		}
		else if(currentCore == 11) {
			adjaLeftCore = 10;
			adjaRightCore = 0;
			oppositeCore = 5;
			adOposLeftCore = 6;
			adOposRightCore = 4;
		}

		// Verificando Ncleos for Reset
		for (int i = 0; i < numberOfCores; i++) {
			if(i != centralCoreId) {
				if (pointArray[i] <= 0) {
					pointArray[i] = DEFAULTPOINTFOROTHERCORE;
				}
			}
		}

		// Aplicando Table de Points
		pointArray[currentCore] = pointArray[currentCore] + 1; // Ncleo Atual
		pointArray[centralCoreId] = pointArray[centralCoreId] - 1; // Ncleo Central
		pointArray[oppositeCore] = pointArray[oppositeCore] - 1; // Nleo Oposto
		pointArray[adjaLeftCore] = pointArray[adjaLeftCore] - 2; // Ncleo Adjacente Esquerdo
		pointArray[adjaRightCore] = pointArray[adjaRightCore] - 2; // Ncleo Adjacente Direito
		pointArray[adOposLeftCore] = pointArray[adOposLeftCore] - 3; // Ncleo Adjacente Oposto Esquerdo
		pointArray[adOposRightCore] = pointArray[adOposRightCore] - 3; // Ncleo Adjacente Oposto Direito
	}
	
	private void pointTableUpdate19(int currentCore) {
		int oppositeCore = 0;
		int adjaLeftCore = 0;
		int adjaRightCore = 0;
		int adOposLeftCore = 0;
		int adOposRightCore = 0;
		
		if(currentCore == 0) {
			adjaLeftCore = 3;
			adjaRightCore = 1;
			oppositeCore = 17;
			adOposLeftCore = 16;
			adOposRightCore = 14;
		}
		else if(currentCore == 1) {
			adjaLeftCore = 0;
			adjaRightCore = 2;
			oppositeCore = 16;
			adOposLeftCore = 15;
			adOposRightCore = 17;
		}
		else if(currentCore == 2) {
			adjaLeftCore = 1;
			adjaRightCore = 6;
			oppositeCore = 15;
			adOposLeftCore = 11;
			adOposRightCore = 16;
		}
		else if(currentCore == 3) {
			adjaLeftCore = 7;
			adjaRightCore = 0;
			oppositeCore = 14;
			adOposLeftCore = 17;
			adOposRightCore = 16;
		}
		else if(currentCore == 4) {
			adjaLeftCore = 8;
			adjaRightCore = 5;
			oppositeCore = 13;
			adOposLeftCore = 12;
			adOposRightCore = 9;
		}
		else if(currentCore == 5) {
			adjaLeftCore = 4;
			adjaRightCore = 9;
			oppositeCore = 12;
			adOposLeftCore = 8;
			adOposRightCore = 13;
		}
		else if(currentCore == 6) {
			adjaLeftCore = 2;
			adjaRightCore = 10;
			oppositeCore = 11;
			adOposLeftCore = 7;
			adOposRightCore = 15;
		}
		else if(currentCore == 7) {
			adjaLeftCore = 11;
			adjaRightCore = 3;
			oppositeCore = 10;
			adOposLeftCore = 6;
			adOposRightCore = 14;
		}
		else if(currentCore == 8) {
			adjaLeftCore = 4;
			adjaRightCore = 12;
			oppositeCore = 9;
			adOposLeftCore = 5;
			adOposRightCore = 13;
		}
		else if(currentCore == 9) {
			adjaLeftCore = 5;
			adjaRightCore = 13;
			oppositeCore = 8;
			adOposLeftCore = 4;
			adOposRightCore = 12;
		}
		else if(currentCore == 10) {
			adjaLeftCore = 6;
			adjaRightCore = 14;
			oppositeCore = 7;
			adOposLeftCore = 3;
			adOposRightCore = 11;
		}
		else if(currentCore == 11) {
			adjaLeftCore = 7;
			adjaRightCore = 15;
			oppositeCore = 6;
			adOposLeftCore = 2;
			adOposRightCore = 10;
		}
		else if(currentCore == 12) {
			adjaLeftCore = 8;
			adjaRightCore = 13;
			oppositeCore = 5;
			adOposLeftCore = 4;
			adOposRightCore = 9;
		}
		else if(currentCore == 13) {
			adjaLeftCore = 12;
			adjaRightCore = 9;
			oppositeCore = 4;
			adOposLeftCore = 8;
			adOposRightCore = 5;
		}
		else if(currentCore == 14) {
			adjaLeftCore = 17;
			adjaRightCore = 10;
			oppositeCore = 3;
			adOposLeftCore = 7;
			adOposRightCore = 0;
		}
		else if(currentCore == 15) {
			adjaLeftCore = 11;
			adjaRightCore = 16;
			oppositeCore = 2;
			adOposLeftCore = 1;
			adOposRightCore = 6;
		}
		else if(currentCore == 16) {
			adjaLeftCore = 15;
			adjaRightCore = 17;
			oppositeCore = 1;
			adOposLeftCore = 0;
			adOposRightCore = 2;
		}
		else if(currentCore == 17) {
			adjaLeftCore = 16;
			adjaRightCore = 14;
			oppositeCore = 0;
			adOposLeftCore = 3;
			adOposRightCore = 1;
		}
		else if(currentCore == 18) {
			adjaLeftCore = 8;
			adjaRightCore = 9;
			oppositeCore = 0;
			adOposLeftCore = 3;
			adOposRightCore = 1;
		}

		// Verificando Ncleos for Reset
		for (int i = 0; i < numberOfCores; i++) {
			if(i != centralCoreId) {
				if (pointArray[i] <= 0) {
					pointArray[i] = DEFAULTPOINTFOROTHERCORE;
				}
			}
		}

		// Aplicando Table de Points
		pointArray[currentCore] = pointArray[currentCore] + 1; // Ncleo Atual
		pointArray[centralCoreId] = pointArray[centralCoreId] - 1; // Ncleo Central
		pointArray[oppositeCore] = pointArray[oppositeCore] - 1; // Nleo Oposto
		pointArray[adjaLeftCore] = pointArray[adjaLeftCore] - 2; // Ncleo Adjacente Esquerdo
		pointArray[adjaRightCore] = pointArray[adjaRightCore] - 2; // Ncleo Adjacente Direito
		pointArray[adOposLeftCore] = pointArray[adOposLeftCore] - 3; // Ncleo Adjacente Oposto Esquerdo
		pointArray[adOposRightCore] = pointArray[adOposRightCore] - 3; // Ncleo Adjacente Oposto Direito
	}

	// Retorna o ID do Core Chosen.
	private int checkPointTable() {
		int menorValor = 999999;
		int coreID = -1;
		for (int i = 0; i < numberOfCores; i++) {
			if (pointArray[i] <= menorValor) {
				menorValor = pointArray[i];
				coreID = i;
			}
		}
		coreUse[coreID]++;
		return coreID;
	}

	// Primeira attempt de alocar
	/**
	 * Executes the policy operation.
	 * @param numberOfslots the numberOfslots.
	 * @param freeSpectrumBands the freeSpectrumBands.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @return the result of the operation.
	 */
	public int[] policy(int numberOfSlots, List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp) {
		// Checks whether the request is within the signal amplitude limits
		int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
		if (numberOfSlots > maxAmplitude)
			return null;

		// Setando Divisores de Espectro
		int maxSpectrumSize = circuit.getRoute().getLink(0).getCore(0).getNumOfSlots();
		int minSpectrumRange = maxSpectrumSize / numberOfCores;
		int t1 = minSpectrumRange * 2;
		int t2 = minSpectrumRange * 4;
		int t3 = minSpectrumRange * 6;
		// System.out.println("Spec Size [" + maxSpectrumSize + "] T1 [" + t1 + "] T2 ["
		// + t2 + "] T3 [" + t3 + "]");
		// System.out.println("[" + circuit.getIndexCore() + "]");
		//		System.out.println(
		//				"CENTRAL [" + coreUse[CENTRALCOREID] + "] 1 - [" + coreUse[1] + "] 2 - [" + coreUse[2] + "] 3 - ["
		//						+ coreUse[3] + "] 4 - [" + coreUse[4] + "] 5 - [" + coreUse[5] + "] 6 - [" + coreUse[6] + "]");

		// Choosing Espectro
		int chosen[] = null;

		// (BLUE) First Fit
		if (groupBlue.contains(circuit.getIndexCore())) {
			for (int[] band : freeSpectrumBands) {
				if (band[1] - band[0] + 1 >= numberOfSlots && (band[0] + numberOfSlots - 1) < t1) {
					chosen = band.clone();
					chosen[1] = chosen[0] + numberOfSlots - 1;
					//					System.out.println("1 - CORE [" + circuit.getIndexCore() + "] BLUE FIRST FIT REQUISIO ["
					//							+ numberOfslots + "] ESPECTRO = [" + chosen[0] + " - " + chosen[1] + "] BandaCompleta ["
					//							+ band[0] + " - " + band[1] + "]");
					return chosen;
				}
			}
		}
		// (ORANGE) Medium Fit
		else if (groupOrange.contains(circuit.getIndexCore())) {
			int reference = t1 + (t1 / 2);
			int[] chosenBandLeft = null;
			int[] chosenBandRight = null;
			// Percorrendo Frees
			for (int[] band : freeSpectrumBands) {
				// Checks whether the range accepts the request
				if (band[1] - band[0] + 1 >= numberOfSlots) {
					// Scenario 1 - Left - [0] [1] <= Reference
					if (band[0] < reference && band[1] <= reference && band[1] - numberOfSlots + 1 >= t1) {
						if (chosenBandLeft == null) {
							chosenBandLeft = band.clone();
							chosenBandLeft[0] = band[1] - numberOfSlots + 1;
						} else {
							if (reference - band[1] <= reference - chosenBandLeft[1]) {
								chosenBandLeft[1] = band[1];
								chosenBandLeft[0] = band[1] - numberOfSlots + 1;
							}
						}
					}
					// Scenario 2 - Inside - [0] < Reference < [1]
					else if (band[0] <= reference && band[1] >= reference) {
						if ((reference - (numberOfSlots / 2)) > band[0] && (reference + (numberOfSlots / 2)) < band[1]) {
							chosen = new int[2];
							chosen[0] = reference - (numberOfSlots / 2);
							chosen[1] = chosen[0] + numberOfSlots - 1;
							//							System.out.println(
							//									"1 - CORE [" + circuit.getIndexCore() + "] ORANGE IN MIDDLE FIT REQUISIO ["
							//											+ numberOfslots + "] ESPECTRO = [" + chosen[0] + " - " + chosen[1]
							//											+ "] BandaCompleta [" + band[0] + " - " + band[1] + "]");
							return chosen;
						} else {
							chosen = band.clone();
							chosen[1] = chosen[0] + numberOfSlots - 1;
							//							System.out.println(
							//									"1 - CORE [" + circuit.getIndexCore() + "] ORANGE ELSE IN FIRST FIT REQUISIO ["
							//											+ numberOfslots + "] ESPECTRO = [" + chosen[0] + " - " + chosen[1]
							//											+ "] BandaCompleta [" + band[0] + " - " + band[1] + "]");
							return chosen;
						}
					}
					// Scenario 3 - Right - Reference < [0] [1]
					else if (band[0] >= reference && band[1] > reference && band[0] + numberOfSlots - 1 < t2) {
						if (chosenBandRight == null) {
							chosenBandRight = band.clone();
							chosenBandRight[1] = band[0] + numberOfSlots - 1;
						} else {
							if (band[0] - reference <= chosenBandRight[0] - reference) {
								chosenBandRight[0] = band[0];
								chosenBandRight[1] = band[0] + numberOfSlots - 1;
							}
						}
					}
				}
			}

			// Verificando se usar bloco da right ou left
			if (chosenBandLeft == null && chosenBandRight == null) {
				// System.out.println("LEFT AND RIGHT MIDDLE FIT NULL");
				return null;
			} else if (chosenBandLeft == null && chosenBandRight != null) {
				chosen = chosenBandRight.clone();
				//				System.out.println(
				//						"1 - CORE [" + circuit.getIndexCore() + "] ORANGE RIGHT REFERENCE FIRST FIT REQUISI ["
				//								+ numberOfslots + "] ESPECTRO = [" + chosen[0] + " - " + chosen[1] + "]");
				return chosen;
			} else if (chosenBandRight == null && chosenBandLeft != null) {
				chosen = chosenBandLeft.clone();
				//				System.out
				//						.println("1 - CORE [" + circuit.getIndexCore() + "] ORANGE LEFT REFERENCE LAST FIT REQUISIO ["
				//								+ numberOfslots + "] ESPECTRO = [" + chosen[0] + " - " + chosen[1] + "]");
				return chosen;
			} else if (chosenBandLeft[1] - reference <= reference - chosenBandRight[0]) {
				chosen = chosenBandLeft.clone();
				//				System.out
				//						.println("1 - CORE [" + circuit.getIndexCore() + "] ORANGE LEFT REFERENCE LAST FIT REQUISIO ["
				//								+ numberOfslots + "] ESPECTRO = [" + chosen[0] + " - " + chosen[1] + "]");
				return chosen;
			} else if (chosenBandLeft[1] - reference >= reference - chosenBandRight[0]) {
				chosen = chosenBandRight.clone();
				//				System.out.println(
				//						"1 - CORE [" + circuit.getIndexCore() + "] ORANGE RIGHT REFERENCE FIRST FIT REQUISIO ["
				//								+ numberOfslots + "] ESPECTRO = [" + chosen[0] + " - " + chosen[1] + "]");
				return chosen;
			}
		}
		// (GREEN) Last Fit
		else if (groupGreen.contains(circuit.getIndexCore())) {
			int band[] = null;
			for (int i = freeSpectrumBands.size() - 1; i >= 0; i--) {
				band = freeSpectrumBands.get(i);
				if (band[1] - band[0] + 1 >= numberOfSlots) {
					// System.out.println("T3 = " + t3 + " BandaCompleta [" + band[0] + " - " +
					// band[1] + "] REQUISIO [" + numberOfslots + "]");
					// Scenario 1 (Passes through T3) [0] < t3 < [1]
					if (band[0] <= t3 && band[1] >= t3 && (t3 - numberOfSlots) >= band[0] && t3 - numberOfSlots >= t2) {
						chosen = new int[2];
						chosen[1] = t3 - 1;
						chosen[0] = chosen[1] - numberOfSlots + 1;
						//						System.out.println("1 - CORE [" + circuit.getIndexCore() + "] GREEN IN T3 REQUISIO ["
						//								+ numberOfslots + "] ESPECTRO = [" + chosen[0] + " - " + chosen[1] + "] BandaCompleta ["
						//								+ band[0] + " - " + band[1] + "]");
						return chosen;
						// Scenario 2 (Before T3) [0] [1] < t3
					} else if (band[1] < t3 && band[1] - numberOfSlots + 1 >= t2) {
						chosen = band.clone();
						chosen[0] = chosen[1] - numberOfSlots + 1;
						//						System.out.println("1 - CORE [" + circuit.getIndexCore() + "] GREEN BEFORE T3 REQUISIO ["
						//								+ numberOfslots + "] ESPECTRO = [" + chosen[0] + " - " + chosen[1] + "] BandaCompleta ["
						//								+ band[0] + " - " + band[1] + "]");
						return chosen;
					}
				}
			}
		}
		// CENTRAL(BLACK) Last Fit
		else if (groupBlack.contains(circuit.getIndexCore())) {
			int band[] = null;
			for (int i = freeSpectrumBands.size() - 1; i >= 0; i--) {
				band = freeSpectrumBands.get(i);
				if (band[1] - band[0] + 1 >= numberOfSlots) {
					// Cenrio 1 - Passando Inside de T3
					if (band[0] <= t3 && band[1] >= t3 && band[1] - numberOfSlots + 1 >= t3) {
						chosen = band.clone();
						chosen[0] = chosen[1] - numberOfSlots + 1;
						//						System.out.println("1 - CORE [" + circuit.getIndexCore() + "] BLACK LAST IN FIT REQUISIO ["
						//								+ numberOfslots + "] ESPECTRO = [" + chosen[0] + " - " + chosen[1] + "] BandaCompleta ["
						//								+ band[0] + " - " + band[1] + "]");
						return chosen;
						// Cenrio 2 - Passando depois de T3
					} else if (band[0] >= t3) {
						chosen = band.clone();
						chosen[0] = chosen[1] - numberOfSlots + 1;
						//						System.out.println("1 - CORE [" + circuit.getIndexCore() + "] BLACK LAST AFTER FIT REQUISIO ["
						//								+ numberOfslots + "] ESPECTRO = [" + chosen[0] + " - " + chosen[1] + "] BandaCompleta ["
						//								+ band[0] + " - " + band[1] + "]");
						return chosen;
					}
				}
			}
		}
		return null;
	}
	
	// Segunda attempt de alocar
	/**
	 * Executes the policy2 operation.
	 * @param numberOfslots the numberOfslots.
	 * @param freeSpectrumBands the freeSpectrumBands.
	 * @param circuit the circuit.
	 * @param cp the cp.
	 * @return the result of the operation.
	 */
	public int[] policy2(int numberOfSlots, List<int[]> freeSpectrumBands, Circuit circuit, ControlPlane cp) {
		// Checks whether the request is within the signal amplitude limits
		int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();
		if (numberOfSlots > maxAmplitude)
			return null;
		// Setando Divisores de Espectro
		int maxSpectrumSize = circuit.getRoute().getLink(0).getCore(0).getNumOfSlots();
		int minSpectrumRange = maxSpectrumSize / numberOfCores;
		int t1 = minSpectrumRange * 2;
		int t2 = minSpectrumRange * 4;
		int t3 = minSpectrumRange * 6;
		// Choosing Espectro
		int chosen[] = null;

		// (BLUE) First Fit After T1
		if (groupBlue.contains(circuit.getIndexCore())) {
			for (int[] band : freeSpectrumBands) {
				if (band[1] - band[0] + 1 >= numberOfSlots) {
					// Scenario 1 (Passes through T1)
					if (band[0] <= t1 && band[1] >= t1 && (t1 + numberOfSlots - 1) < band[1]) {
						chosen = new int[2];
						chosen[0] = t1;
						chosen[1] = chosen[0] + numberOfSlots - 1;
						//						System.out.println("2 - CORE [" + circuit.getIndexCore() + "] BLUE IN T1 FIST FIT REQUISIO ["
						//								+ numberOfslots + "] ESPECTRO = [" + chosen[0] + " - " + chosen[1] + "] BandaCompleta ["
						//								+ band[0] + " - " + band[1] + "]");
						return chosen;
					}
					// Scenario 2 (After t1)
					else if (band[0] > t1) {
						chosen = band.clone();
						chosen[1] = chosen[0] + numberOfSlots - 1;
						//						System.out.println("2 - CORE [" + circuit.getIndexCore()
						//								+ "] BLUE AFTER F1 FIST FIT REQUISIO [" + numberOfslots + "] ESPECTRO = [" + chosen[0]
						//								+ " - " + chosen[1] + "] BandaCompleta [" + band[0] + " - " + band[1] + "]");
						return chosen;
					}
				}
			}
		}
		// (ORANGE) Last Fit <- t1 | First Fit -> t2
		else if (groupOrange.contains(circuit.getIndexCore())) {
			// Primeiro Bloco Last Fit T1
			int band[] = null;
			// Traversing the free spectrum vector from back to front
			for (int i = freeSpectrumBands.size() - 1; i >= 0; i--) {
				band = freeSpectrumBands.get(i);
				if (band[1] - band[0] + 1 >= numberOfSlots) {
					// Scenario 1 ( Passing through T1)
					if (band[0] <= t1 && band[1] >= t1 && (t1 - numberOfSlots) >= band[0]) {
						chosen = new int[2];
						chosen[1] = t1 - 1;
						chosen[0] = chosen[1] - numberOfSlots + 1;
						//						System.out.println("2 - CORE [" + circuit.getIndexCore()
						//								+ "] ORANGE IN T1 LAST FIT REQUISIO [" + numberOfslots + "] ESPECTRO = [" + chosen[0]
						//								+ " - " + chosen[1] + "] BandaCompleta [" + band[0] + " - " + band[1] + "]");
						return chosen;
					}
					// Scenario 2 (Before T1)
					else if (band[1] < t1) {
						chosen = band.clone();
						chosen[0] = chosen[1] - numberOfSlots + 1;
						//						System.out.println(
						//								"2 - CORE [" + circuit.getIndexCore() + "] ORANGE BEFORE T1 LAST FIT REQUISIO ["
						//										+ numberOfslots + "] ESPECTRO = [" + chosen[0] + " - " + chosen[1]
						//										+ "] BandaCompleta [" + band[0] + " - " + band[1] + "]");
						return chosen;
					}

				}
			}
			// Segundo Bloco First Fit T2
			for (int[] band1 : freeSpectrumBands) {
				if (band1[1] - band1[0] + 1 >= numberOfSlots) {
					// Scenario 1 ( Passes through t2)
					if (band1[0] <= t2 && band1[1] >= t2 && (t2 + numberOfSlots - 1) <= band1[1]) {
						chosen = new int[2];
						chosen[0] = t2;
						chosen[1] = chosen[0] + numberOfSlots - 1;
						//						System.out.println("2 - CORE [" + circuit.getIndexCore()
						//								+ "] ORANGE IN T2 FIST FIT REQUISIO [" + numberOfslots + "] ESPECTRO = [" + chosen[0]
						//								+ " - " + chosen[1] + "] BandaCompleta [" + band[0] + " - " + band[1] + "]");
						return chosen;
					}
					// Scenario 2 (After t2)
					else if (band1[0] >= t2) {
						chosen = band1.clone();
						chosen[1] = chosen[0] + numberOfSlots - 1;
						//						System.out.println(
						//								"2 - CORE [" + circuit.getIndexCore() + "] ORANGE AFTER T2 FIST FIT REQUISIO ["
						//										+ numberOfslots + "] ESPECTRO = [" + chosen[0] + " - " + chosen[1]
						//										+ "] BandaCompleta [" + band[0] + " - " + band[1] + "]");
						return chosen;
					}
				}
			}
		}
		// (GREEN) Last Fit <- t2 | First Fit -> t3
		else if (groupGreen.contains(circuit.getIndexCore())) {
			// Primeiro Bloco Last Fit T2
			int band[] = null;
			// Traversing the free spectrum vector from back to front
			for (int i = freeSpectrumBands.size() - 1; i >= 0; i--) {
				band = freeSpectrumBands.get(i);
				if (band[1] - band[0] + 1 >= numberOfSlots) {
					// Scenario 1 ( Passing through T2)
					if (band[0] <= t2 && band[1] >= t2 && (t2 - numberOfSlots) >= band[0]) {
						chosen = new int[2];
						chosen[1] = t2 - 1;
						chosen[0] = chosen[1] - numberOfSlots + 1;
						//						System.out.println("2 - CORE [" + circuit.getIndexCore() + "] GREEN IN T2 LAST FIT REQUISIO ["
						//								+ numberOfslots + "] ESPECTRO = [" + chosen[0] + " - " + chosen[1] + "] BandaCompleta ["
						//								+ band[0] + " - " + band[1] + "]");
						return chosen;
					}
					// Scenario 2 (Before T2)
					else if (band[1] < t2) {
						chosen = band.clone();
						chosen[0] = chosen[1] - numberOfSlots + 1;
						//						System.out.println(
						//								"2 - CORE [" + circuit.getIndexCore() + "] GREEN BEFORE T2 LAST FIT REQUISIO ["
						//										+ numberOfslots + "] ESPECTRO = [" + chosen[0] + " - " + chosen[1]
						//										+ "] BandaCompleta [" + band[0] + " - " + band[1] + "]");
						return chosen;
					}

				}
			}
			// Segundo Bloco First Fit T3
			for (int[] band1 : freeSpectrumBands) {
				if (band1[1] - band1[0] + 1 >= numberOfSlots) {
					// Scenario 1 ( Passes through t3)
					if (band1[0] <= t3 && band1[1] >= t3 && (t3 + numberOfSlots - 1) <= band1[1]) {
						chosen = new int[2];
						chosen[0] = t3;
						chosen[1] = chosen[0] + numberOfSlots - 1;
						//						System.out.println("2 - CORE [" + circuit.getIndexCore()
						//								+ "] GREEN IN T3 FIRST FIT REQUISIO [" + numberOfslots + "] ESPECTRO = [" + chosen[0]
						//								+ " - " + chosen[1] + "] BandaCompleta [" + band[0] + " - " + band[1] + "]");
						return chosen;
					}
					// Scenario 2 (After t3)
					else if (band1[0] >= t3) {
						chosen = band1.clone();
						chosen[1] = chosen[0] + numberOfSlots - 1;
						//						System.out.println(
						//								"2 - CORE [" + circuit.getIndexCore() + "] GREEN AFTER T3 FIRST FIT REQUISIO ["
						//										+ numberOfslots + "] ESPECTRO = [" + chosen[0] + " - " + chosen[1]
						//										+ "] BandaCompleta [" + band[0] + " - " + band[1] + "]");
						return chosen;
					}
				}
			}
		}
		// CENTRAL(BLACK) Last Fit <- t3
		else if (groupBlack.contains(circuit.getIndexCore())) {
			int band[] = null;
			for (int i = freeSpectrumBands.size() - 1; i >= 0; i--) {
				band = freeSpectrumBands.get(i);
				if (band[1] - band[0] + 1 >= numberOfSlots) {
					// Scenario 1 ( Passes through t3)
					if (band[0] <= t3 && band[1] >= t3 && (t3 - numberOfSlots) >= band[0]) {
						chosen = new int[2];
						chosen[1] = t3 - 1;
						chosen[0] = chosen[1] - numberOfSlots + 1;
						//						System.out.println("2 - CORE [" + circuit.getIndexCore() + "] BLACK IN T3 LAST FIT REQUISIO ["
						//								+ numberOfslots + "] ESPECTRO = [" + chosen[0] + " - " + chosen[1] + "] BandaCompleta ["
						//								+ band[0] + " - " + band[1] + "]");
						return chosen;
					}
					// Scenario 2 (Before t3)
					else if (band[1] < t3) {
						chosen = band.clone();
						chosen[0] = chosen[1] - numberOfSlots + 1;
						//						System.out.println(
						//								"2 - CORE [" + circuit.getIndexCore() + "] BLACK BEFORE T3 LAST FIT REQUISIO ["
						//										+ numberOfslots + "] ESPECTRO = [" + chosen[0] + " - " + chosen[1]
						//										+ "] BandaCompleta [" + band[0] + " - " + band[1] + "]");
						return chosen;
					}
				}
			}
		}

		// Return null if no available spectrum is found.
		return null;
	}

}
