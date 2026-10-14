package gprmcsa.reallocation;

import java.util.ArrayList;
import java.util.List;

import network.Circuit;
import network.ControlPlane;
import network.Core;
import network.Link;
import util.IntersectionFreeSpectrum;

/**
 * Defragmentation algorithm for multicore optical networks extracted from the paper
 * "Empowering Hitless Spectral Defragmentation in Elastic Optical Networks with
 * Spatial Multiplexing"
 *
 * @author gustavo
 *
 */
public class CASDPushPull implements ReallocationAlgorithmInterface {

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
	 * @param requisicaoCircuito the requisicaoCircuito.
	 * @param controlPlane the controlPlane.
	 * @return true if the condition is met; false otherwise.
	 */
	@Override
	public boolean strategy(Circuit requisicaoCircuito, ControlPlane controlPlane) {
		ArrayList<Core> coresSCabaixoLimiar = new ArrayList<>();
		boolean realocacaoSucesso = false;

		// calculate SC for all cores of all network links
		for (Link link : controlPlane.getMesh().getLinkList()) {
			for (Core core : link.getCores()) {
				double scCurrent = controlPlane.sc.compute(core);

//				if (core.getCircuitList().isEmpty()) {
//					System.out.println(" entered here");
//				}
				if (core.getCircuitList().size() != 0) {

					if (scCurrent > 0 && scCurrent < 50 && core.getCircuitList().size() != 0) {
						System.out.println(scCurrent);
						System.out.println(core.getCircuitList());
						//list of circuits on the core
						for (Circuit circuitAtivo : core.getCircuitList()) {
							// System.out.println(circuitAtivo);
							for (int i = 0; i <= 6; i++) {
								if (core.getId() != i) {
									List<int[]> composition = IntersectionFreeSpectrum.merge(circuitAtivo.getRoute(), circuitAtivo.getGuardBand(), i);
									double scAntiga = controlPlane.sc.compute(core);
									System.out.println(scAntiga);

									for (int[] freeBand : composition) {
										if (circuitAtivo.getSpectrumAssigned()[0] >= freeBand[0] && circuitAtivo.getSpectrumAssigned()[1] <= freeBand[1]) {
											// ssdc
											// creating a copy of the circuit
											Circuit circuitoTeste = new Circuit();
											try {
												circuitoTeste = circuitAtivo.clone();
											} catch (CloneNotSupportedException e1) {
												// TODO Auto-generated catch block
												e1.printStackTrace();
											}
											try {
												// releaseSpectrum(circ, circ.getSpectrumAssigned(),
												// circ.getRoute().getLinkList(), circ.getGuardBand());
//												controlPlane.releaseCircuit2(circuitAtivo);
											} catch (Exception e1) {
												// TODO Auto-generated catch block
												e1.printStackTrace();
											}

											circuitoTeste.setIndexCore(i);
											try {
												if (controlPlane.isAdmissibleQualityOfTransmission(circuitoTeste)) {
													if (controlPlane.isAdmissibleCrosstalk(circuitoTeste)) {
														if (controlPlane.sc.compute(core) > scAntiga || controlPlane.sc.compute(core)>50) {
															circuitAtivo.setIndexCore(i);
															controlPlane.allocateCircuit(circuitAtivo);
															realocacaoSucesso = true;
															System.out.println("realocou");
															System.out.println(controlPlane.sc.compute(core));

															break;
														} else {
															controlPlane.allocateCircuit(circuitAtivo);
														}
													} else {
														controlPlane.allocateCircuit(circuitAtivo);
													}
												} else {
													controlPlane.allocateCircuit(circuitAtivo);
												}
											} catch (Exception e) {
												// TODO Auto-generated catch block
												e.printStackTrace();
											}
										}
									}
								}

								if (realocacaoSucesso) {
									realocacaoSucesso = false;
									break;
								}
							}
						}
					}
				}
			}
		}

		System.out.println();

		return false;
	}

	/**
	 * Executes the ssdc operation.
	 */
	private void ssdc() {
		// TODO Auto-generated method stub
	}

	/**
	 * Selects the actives circuits.
	 * @param cp the cp.
	 * @param requisicao the request.
	 */
	@Override
	public void selectActivesCircuits(ControlPlane cp, Circuit requisicao) {
		// TODO Auto-generated method stub
	}

}
