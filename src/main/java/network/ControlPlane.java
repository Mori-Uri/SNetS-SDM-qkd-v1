package network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import gprmcsa.GPRMCSA;
import gprmcsa.coreSpectrumAssignment.CoreAndSpectrumAssignmentAlgorithmInterface;
import gprmcsa.integrated.IntegratedRMLSAAlgorithmInterface;
import gprmcsa.modulation.Modulation;
import gprmcsa.modulation.ModulationSelectionAlgorithmInterface;
import gprmcsa.modulation.ModulationSelectionByDistanceAndBitRate;
import gprmcsa.powerAssignment.PowerAssignmentAlgorithmInterface;
import gprmcsa.reallocation.ReallocationAlgorithmInterface;
import gprmcsa.reallocation.util.SpectrumCompactness;
import gprmcsa.routing.KRoutingAlgorithmInterface;
import gprmcsa.routing.Route;
import gprmcsa.routing.RoutingAlgorithmInterface;
import gprmcsa.spectrumAssignment.SpectrumAssignmentAlgorithmInterface;
import gprmcsa.trafficGrooming.TrafficGroomingAlgorithmInterface;
import request.RequestForConnection;
import util.IntersectionFreeSpectrum;

/**
 * Class that represents the control plane for a Transparent Elastic Optical
 * Network. This class should make calls to RMLSA algorithms, store routes in
 * case of fixed routing, provide information about the state of the network,
 * etc.
 *
 * @author Iallen
 */
public class ControlPlane implements Serializable {

	protected int rsaType;
	protected RoutingAlgorithmInterface routing;
	protected KRoutingAlgorithmInterface kRouting;
	protected SpectrumAssignmentAlgorithmInterface spectrumAssignment;
	protected CoreAndSpectrumAssignmentAlgorithmInterface coreAndSpectrumAssignment;
	protected IntegratedRMLSAAlgorithmInterface integrated;
	protected ModulationSelectionAlgorithmInterface modulationSelection;
	protected TrafficGroomingAlgorithmInterface grooming;
	protected ReallocationAlgorithmInterface reallocation;// added by Selles
	protected PowerAssignmentAlgorithmInterface powerAssignment;

	public SpectrumCompactness sc;

	protected ModulationSelectionAlgorithmInterface modSelectByDistForEvaluation; // used to check the blocking types

	protected Mesh mesh;

	/**
	 * The first key represents the source node. The second key represents the
	 * destination node.
	 */
	protected HashMap<String, HashMap<String, List<Circuit>>> activeCircuits;

	private HashSet<Circuit> connectionList;

	/**
	 * Instance the control plane with the list of active circuits in empty
	 *
	 * @param mesh                        Mesh
	 * @param rmlsaType                   int
	 * @param trafficGroomingAlgorithm    TrafficGroomingAlgorithmInterface
	 * @param integratedRMLSAAlgorithm    IntegratedRMLSAAlgorithmInterface
	 * @param routingAlgorithm            RoutingAlgorithmInterface
	 * @param kRoutingAlgorithm            KRoutingAlgorithmInterface
	 * @param spectrumAssignmentAlgorithm SpectrumAssignmentAlgorithmInterface
	 * @param modulationSelection         ModulationSelectionAlgorithmInterface
	 * @param coreAndSpectrumAssignment   CoreAndSpectrumAssignmentAlgorithmInterface
	 * @param reallocation                ReallocationAlgorithmInterface
	 * @param powerAssignment             PowerAssignmentAlgorithmInterface
	 */
	public ControlPlane(Mesh mesh, int rmlsaType,
			TrafficGroomingAlgorithmInterface trafficGroomingAlgorithm,
			IntegratedRMLSAAlgorithmInterface integratedRMLSAAlgorithm,
			RoutingAlgorithmInterface routingAlgorithm,
			KRoutingAlgorithmInterface kRoutingAlgorithm,
			SpectrumAssignmentAlgorithmInterface spectrumAssignmentAlgorithm,
			ModulationSelectionAlgorithmInterface modulationSelection,
			CoreAndSpectrumAssignmentAlgorithmInterface coreAndSpectrumAssignment,
			ReallocationAlgorithmInterface reallocation,
			PowerAssignmentAlgorithmInterface powerAssignment) {

		this.activeCircuits = new HashMap<>();
		this.connectionList = new HashSet<>();

		this.rsaType = rmlsaType;
		this.grooming = trafficGroomingAlgorithm;
		this.integrated = integratedRMLSAAlgorithm;
		this.routing = routingAlgorithm;
		this.kRouting = kRoutingAlgorithm;
		this.spectrumAssignment = spectrumAssignmentAlgorithm;
		this.coreAndSpectrumAssignment = coreAndSpectrumAssignment;
		this.modulationSelection = modulationSelection;
		this.powerAssignment = powerAssignment;

		this.reallocation = reallocation;// added by Selles
		this.sc = new SpectrumCompactness();// added by Selles

		this.modSelectByDistForEvaluation = new ModulationSelectionByDistanceAndBitRate();

		setMesh(mesh);
	}



	/**
	 * This method creates a new transparent circuit.
	 *
	 * @param rfc RequestForConnection
	 * @return Circuit
	 */
	public Circuit createNewCircuit(RequestForConnection rfc) {
		Circuit circuit = new Circuit();
		circuit.setPair(rfc.getPair());
		circuit.addRequest(rfc);
		circuit.setGuardBand(mesh.getGuardBand());
		ArrayList<Circuit> circs = new ArrayList<>();
		circs.add(circuit);
		rfc.setCircuit(circs);
		return circuit;
	}

	/**
	 * This method creates a new transparent circuit.
	 *
	 * @param rfc RequestForConnection
	 * @param p   Pair
	 * @return Circuit
	 */
	public Circuit createNewCircuit(RequestForConnection rfc, Pair p) {
		Circuit circuit = new Circuit();
		circuit.setPair(p);
		circuit.addRequest(rfc);
		circuit.setGuardBand(mesh.getGuardBand());
		if (rfc.getCircuits() == null) {
			rfc.setCircuit(new ArrayList<>());
		}
		rfc.getCircuits().add(circuit);
		return circuit;
	}

	/**
	 * Configures the network mesh
	 *
	 * @param mesh the mesh to set
	 */
	public void setMesh(Mesh mesh) {
		this.mesh = mesh;

		mesh.computesPowerConsmption(this);

		// Initialize the active circuit list
		for (Node node1 : mesh.getNodeList()) {
			HashMap<String, List<Circuit>> hmAux = new HashMap<>();

			for (Node node2 : mesh.getNodeList()) {
				if (!node1.equals(node2)) {
					hmAux.put(node2.getName(), new ArrayList<>());
				}
			}
			activeCircuits.put(node1.getName(), hmAux);
		}
	}

	/**
	 * Returns the network mesh
	 *
	 * @return the mesh
	 */
	public Mesh getMesh() {
		return mesh;
	}

	/**
	 * Returns the modulation selection
	 *
	 * @return ModulationSelection
	 */
	public ModulationSelectionAlgorithmInterface getModulationSelection() {
		return modulationSelection;
	}

	/**
	 * Returns the spectrum assignment
	 *
	 * @return SpectrumAssignmentAlgorithmInterface
	 */
	public SpectrumAssignmentAlgorithmInterface getSpectrumAssignment() {
		return spectrumAssignment;
	}

	/**
	 * Sets the spectrumAssignment
	 *
	 * @param spectrumAssignment SpectrumAssignmentAlgorithmInterface
	 */
	public void setSpectrumAssignmentAlgorithm(SpectrumAssignmentAlgorithmInterface spectrumAssignment) {
		this.spectrumAssignment = spectrumAssignment;
	}

	/**
     * Returns the core and spectrum assignment
     *
     * @return CoreAndSpectrumAssignmentAlgorithmInterface
     */
    public CoreAndSpectrumAssignmentAlgorithmInterface getCoreAndSpectrumAssignment(){
    	return coreAndSpectrumAssignment;
    }

    /**
     * Returns the power assignment algorithm
     *
     * @return PowerAssignmentAlgorithmInterface
     */
    public PowerAssignmentAlgorithmInterface getPowerAssignment() {
    	return powerAssignment;
    }

	/**
	 * Returns the routing algorithm
	 *
	 * @return RoutingAlgorithmInterface
	 */
	public RoutingAlgorithmInterface getRouting() {
		return routing;
	}

	/**
	 * Returns the k routing algorithm
	 *
	 * @return KRoutingAlgorithmInterface
	 */
	public KRoutingAlgorithmInterface getKRouting() {
		return kRouting;
	}

	/**
	 * Returns the integrated RMLSA algorithm
	 *
	 * @return IntegratedRMLSAAlgorithmInterface
	 */
	public IntegratedRMLSAAlgorithmInterface getIntegrated() {
		return integrated;
	}

	/**
	 * This method tries to satisfy a certain request by checking if there are
	 * available resources for the establishment of the circuit. This method
	 * verifies the possibility of satisfying a circuit request.
	 *
	 * @param rfc RequestForConnection
	 * @return boolean
	 */
	public boolean handleRequisition(RequestForConnection rfc) throws Exception {
		return grooming.searchCircuitsForGrooming(rfc, this);
	}

	/**
	 * This method ends a connection
	 *
	 * @param rfc RequestForConnection
	 */
	public void finalizeConnection(RequestForConnection rfc) throws Exception {
		this.grooming.finishConnection(rfc, this);
	}

	/**
	 * This method is called after executing RMLSA algorithms to allocate resources
	 * in the network
	 *
	 * @param circuit Circuit
	 */
	public void allocateCircuit(Circuit circuit) throws Exception {

		if (!allocateSpectrum(circuit, circuit.getSpectrumAssigned(), circuit.getRoute().getLinkList(), circuit.getGuardBand())) {
			throw new Exception("Bad RMLSA choice. Spectrum cant be allocated.");
		}

		// Allocates transmitter and receiver
		circuit.getSource().getTxs().allocatesTransmitters();
		circuit.getDestination().getRxs().allocatesReceivers();

		addConnection(circuit);

		updateNetworkPowerConsumption();
	}

	/**
	 * This method allocates the spectrum band selected for the circuit in the route
	 * links
	 *
	 * @param circuit Circuit
	 * @param band    int[]
	 * @param links   List<Link>
	 */
	public boolean allocateSpectrum(Circuit circuit, int band[], List<Link> links, int guardBand) throws Exception {
		for (int i = 0; i < links.size(); i++) {
			Link link = links.get(i);

			if (!link.getCore(circuit.getIndexCore()).useSpectrum(band, guardBand)) { // spectrum already in use
				i--;
				for (; i >= 0; i--) {
					links.get(i).getCore(circuit.getIndexCore()).liberateSpectrum(band, guardBand);
				}
				return false;
			}
		}
		return true;
	}

	/**
	 * Releases the resources being used by a given circuit
	 *
	 * @param circuit
	 */
	public void releaseCircuit(Circuit circuit) throws Exception {

		releaseSpectrum(circuit, circuit.getSpectrumAssigned(), circuit.getRoute().getLinkList(), circuit.getGuardBand());

		// Release transmitter and receiver
		circuit.getSource().getTxs().releasesTransmitters();
		circuit.getDestination().getRxs().releasesReceivers();

		removeConnection(circuit);

		updateNetworkPowerConsumption();

		// Added by Selles - Call the reallocation strategy
		// Trigger
//		this.reallocation.strategy(circuit, this);
	}

	/**
	 * This method releases the allocated spectrum for the circuit
	 *
	 * @param circuit Circuit
	 * @param band    int[]
	 * @param links   List<Link>
	 */
	public void releaseSpectrum(Circuit circuit, int band[], List<Link> links, int guardBand) throws Exception {
		for (int i = 0; i < links.size(); i++) {
			Link link = links.get(i);

			// link.liberateSpectrum(band, guardBand);
			link.getCore(circuit.getIndexCore()).liberateSpectrum(band, guardBand);
		}
	}

	/**
	 * This method tries to establish a new circuit in the network
	 *
	 * @param circuit Circuit
	 * @return true if the circuit has been successfully allocated, false if the
	 *         circuit can not be allocated.
	 */
	public boolean establishCircuit(Circuit circuit) throws Exception {

		// Check if there are free transmitters
		if (circuit.getSource().getTxs().hasFreeTransmitters()) {
			// Check if there are free receivers
			if (circuit.getDestination().getRxs().hasFreeRecivers()) {

				// Try to find a solution for the RMCSA problem
				if (tryEstablishNewCircuit(circuit)) {

					// QoT verification
					if (isAdmissibleQualityOfTransmission(circuit)) { // OSNR
						if (isAdmissibleGeneralCrosstalk(circuit)) { // XT

                			allocateCircuit(circuit);
                			circuit.setBlockCause(Circuit.NO_BLOCKING);

                			return true; // Admits the circuit
						}
					}
				}

				// Reset physical layer checks to repeat tests
				circuit.setSNR(PhysicalLayer.maxOSNR);
				circuit.setQoT(true);
				circuit.setQoTForOther(true);
				circuit.setXt(Crosstalk.lowestXT);
				circuit.setXtAdmissible(true);
				circuit.setXtAdmissibleInOther(true);

				// Identify the cause of the blocking
				if (isBlockingByLackOfFreeSpectrum(circuit)) {
					circuit.setBlockCause(Circuit.BY_OTHER);

				} else if(isBlockingByFragmentation(circuit)) {
					circuit.setBlockCause(Circuit.BY_FRAGMENTATION);

				} else if(isBlockingByXT(circuit)) {
            		circuit.setBlockCause(Circuit.BY_XTN);

				} else if(isBlockingByQoTN(circuit)) {
					circuit.setBlockCause(Circuit.BY_QOTN);

				} else if(isBlockingByXTO(circuit)) {
            		circuit.setBlockCause(Circuit.BY_XTO);

            	} else if(isBlockingByQoTO(circuit)) {
            		circuit.setBlockCause(Circuit.BY_QOTO);

            	} else{
            		circuit.setBlockCause(Circuit.BY_OTHER);
            	}
			} else {
				circuit.setBlockCause(Circuit.BY_LACK_RX);
			}
		} else {
			circuit.setBlockCause(Circuit.BY_LACK_TX);
		}

		circuit.setWasBlocked(true);
		//imprimeLogBloqueado(circuit);

		return false; // Rejects the circuit
	}

	/**
	 * This method verify that it is to test whether the blocking was by
	 * fragmentation
	 *
	 * @param circuit Circuit
	 * @return boolean
	 */
	private boolean shouldTestFragmentation(Circuit circuit) {
		Modulation modBD = modSelectByDistForEvaluation.selectModulation(circuit, circuit.getRoute(), coreAndSpectrumAssignment, this);
		Modulation modCirc = circuit.getModulation();
		return !(modBD.getSNRthreshold() >= modCirc.getSNRthreshold());
	}

	/**
	 * This method tries to answer a given request by allocating the necessary
	 * resources to the same one
	 *
	 * @param circuit Circuit
	 * @return boolean
	 */
	protected boolean tryEstablishNewCircuit(Circuit circuit) {

		switch (this.rsaType) {
            case GPRMCSA.RMCSA_INTEGRATED:
                return integrated.rsa(circuit, this);

            case GPRMCSA.RMCSA_SEQUENCIAL:
            	 if (routing.findRoute(circuit, this.getMesh())) {
                     Modulation mod = modulationSelection.selectModulation(circuit, circuit.getRoute(), coreAndSpectrumAssignment, this);
                     circuit.setModulation(mod);
                     if(mod != null){
 	                    return coreAndSpectrumAssignment.assignCoreAndSpectrum(mod.requiredSlots(circuit.getRequiredBitRate()), circuit, this);
                     }
                 }
        }

		return false;
	}

	/**
	 * Increases the number of slots used by a given circuit
	 *
	 * @param circuit      Circuit
	 * @param numSlotsDown int
	 * @param numSlotsUp   int
	 * @return boolean
	 * @throws Exception
	 */
	public boolean expandCircuit(Circuit circuit, int numSlotsDown, int numSlotsUp) throws Exception {
		int currentSlots = circuit.getSpectrumAssigned()[1] - circuit.getSpectrumAssigned()[0] + 1;
		int maxAmplitude = circuit.getPair().getSource().getTxs().getMaxSpectralAmplitude();

		if (currentSlots + numSlotsDown + numSlotsUp > maxAmplitude) {
			return false;
		}

		// Calculates the spectrum band at top
		int upperBand[] = new int[2];
		upperBand[0] = circuit.getSpectrumAssigned()[1] + 1;
		upperBand[1] = upperBand[0] + numSlotsUp - 1;

		// Calculates the spectrum band at bottom
		int bottomBand[] = new int[2];
		bottomBand[1] = circuit.getSpectrumAssigned()[0] - 1;
		bottomBand[0] = bottomBand[1] - numSlotsDown + 1;

		// Saves the allocated spectrum band without the expansion
		int specAssigAt[] = circuit.getSpectrumAssigned();

		// New spectrum band with expansion
		int newSpecAssigAt[] = specAssigAt.clone();
		newSpecAssigAt[0] = bottomBand[0];
		newSpecAssigAt[1] = upperBand[1];

		// Releasing the spectrum and guard bands already allocated
		releaseSpectrum(circuit, specAssigAt, circuit.getRoute().getLinkList(), circuit.getGuardBand());

		// Try to expand circuit
		circuit.setSpectrumAssigned(newSpecAssigAt);
		if (!allocateSpectrum(circuit, newSpecAssigAt, circuit.getRoute().getLinkList(), circuit.getGuardBand())) {
			throw new Exception("Bad RMLSA. Spectrum cant be allocated.");
		}

		// Verifies if the expansion did not affect the QoT of the circuit or other already active circuits
		boolean SNR = isAdmissibleQualityOfTransmission(circuit);

		boolean XT = isAdmissibleCrosstalk(circuit); // Check the XT of the circuit
		boolean XTinOthers = true;
		if (XT) {
			XTinOthers = isAdmissibleCrosstalkInOther(circuit);  // Check the XT of the other already active circuits
		}

		if (!SNR || !XT || !XTinOthers) {

			// QoT was not acceptable after expansion, releasing the spectrum
			releaseSpectrum(circuit, newSpecAssigAt, circuit.getRoute().getLinkList(), circuit.getGuardBand());

			// Reallocating the spectrum and guard bands without the expansion
			circuit.setSpectrumAssigned(specAssigAt);
			if (!allocateSpectrum(circuit, specAssigAt, circuit.getRoute().getLinkList(), circuit.getGuardBand())) {
				throw new Exception("Bad RMLSA. Spectrum cant be allocated.");
			}

			// Recalculates the QoT and OSNR of the circuit
			computeQualityOfTransmission(circuit, null, false);

			// Recalculates the XT of the circuit
			computeCrosstalk(circuit);

		} else { // QoT and XT are acceptable
			mesh.getPhysicalLayer().getCrosstalk().updateXTinOthers(circuit, true, false);
			this.updateNetworkPowerConsumption();
		}

		return (SNR && XT && XTinOthers);
	}

	/**
	 * Decreases the number of slots used by a given circuit
	 *
	 * @param circuit      Circuit
	 * @param numSlotsDown int
	 * @param numSlotsUp   int
	 * @throws Exception
	 */
	public void retractCircuit(Circuit circuit, int numSlotsDown, int numSlotsUp) throws Exception {

		// Calculates the spectrum band at top
		int upperBand[] = new int[2];
		upperBand[1] = circuit.getSpectrumAssigned()[1];
		upperBand[0] = upperBand[1] - numSlotsUp + 1;

		// Calculates the spectrum band at bottom
		int bottomBand[] = new int[2];
		bottomBand[0] = circuit.getSpectrumAssigned()[0];
		bottomBand[1] = bottomBand[0] + numSlotsDown - 1;

		// New spectrum band after retraction
		int newSpecAssign[] = circuit.getSpectrumAssigned().clone();
		newSpecAssign[0] = bottomBand[1] + 1;
		newSpecAssign[1] = upperBand[0] - 1;

		// Releasing the spectrum and guard bands already allocated
		releaseSpectrum(circuit, circuit.getSpectrumAssigned(), circuit.getRoute().getLinkList(), circuit.getGuardBand());

		// Reallocates the spectrum and guard bands after retraction
		circuit.setSpectrumAssigned(newSpecAssign);
		if (!allocateSpectrum(circuit, newSpecAssign, circuit.getRoute().getLinkList(), circuit.getGuardBand())) {
			throw new Exception("Bad RMLSA. Spectrum cant be allocated.");
		}

		// Recalculates the QoT and OSNR of the circuit
		computeQualityOfTransmission(circuit, null, false);

		// Recalculates the XT of the circuit
		computeCrosstalk(circuit);
		mesh.getPhysicalLayer().getCrosstalk().updateXTinOthers(circuit, true, false);

		this.updateNetworkPowerConsumption();
	}

	/**
	 * To find active circuits on the network with specified source and destination
	 *
	 * @param source      String
	 * @param destination String
	 * @return List<Circuit>
	 */
	public List<Circuit> searchForActiveCircuits(String source, String destination) {
		return this.activeCircuits.get(source).get(destination);
	}

	/**
	 * To find active circuits on the network with specified source
	 *
	 * @param source String
	 * @return List<Circuit>
	 */
	public List<Circuit> searchForActiveCircuits(String source) {
		List<Circuit> res = new ArrayList<>();
		for (List<Circuit> lc : activeCircuits.get(source).values()) {
			res.addAll(lc);
		}
		return res;
	}

	/**
	 * To find active circuits on the network
	 *
	 * @return List<Circuit>
	 */
	public List<Circuit> searchForActiveCircuits() {
		List<Circuit> res = new ArrayList<>();
		for (HashMap<String, List<Circuit>> hA : activeCircuits.values()) {
			for (List<Circuit> lc : hA.values()) {
				res.addAll(lc);
			}
		}
		return res;
	}

	/**
	 * This method verifies the transmission quality of the circuit in the
	 * establishment and also verifies the transmission quality of the other already
	 * active circuits
	 *
	 * @param circuit Circuit
	 * @return boolean
	 */
	public boolean isAdmissibleQualityOfTransmission(Circuit circuit) throws Exception {

		// Check if OSNR is acceptable
		boolean OSNR = isAdmissibleOSNR(circuit);
		if (OSNR) {

			// Check if the OSNR of the other circuits is acceptable
			boolean OSNRInOther = isAdmissibleQoTForOther(circuit);
			if (OSNRInOther) {
				return true; // Acceptable OSNR levels
			}
		}

		return false; // Circuit can not be established
	}

	/**
	 * This method checks for crosstalk on the circuit being established and
	 * also checks for crosstalk on other circuits that are already active.
	 *
	 * @param circuit Circuit
	 * @return boolean
	 */
	public boolean isAdmissibleGeneralCrosstalk(Circuit circuit) throws Exception {

		// Check if XT is acceptable
		boolean XT = isAdmissibleCrosstalk(circuit);
		if (XT) {

			// Check if the XT on the other circuits is acceptable
			boolean XTInOther = isAdmissibleCrosstalkInOther(circuit);
			if (XTInOther) {
				return true; // Acceptable XT levels
			}
		}

		return false; // Circuit can not be established
	}

	/**
     * This method computes the OSNR in a circuit and checks if it is at acceptable levels
     *
     * @param circuit Circuit
     * @return boolean - true if OSNR is acceptable otherwise false
     */
	public boolean isAdmissibleOSNR(Circuit circuit) {

		// Check if it is to test the OSNR
		if (mesh.getPhysicalLayer().isActiveQoT()) {

			boolean isAdmissible = computeQualityOfTransmission(circuit, null, false);
			return isAdmissible;
		}

		// If it does not check the OSNR then it returns acceptable
		return true;
	}

	/**
     * This method checks whether OSNR is acceptable on other circuits already active in the network.
     * Updates OSNR and QoT of other circuits.
     *
     * @param circuit Circuit
     * @return boolean - true if OSNR is acceptable on other circuits otherwise false
     */
    public boolean isAdmissibleQoTForOther(Circuit circuit) {

    	// Check if it is to test the OSNR of other already active circuits
		if (mesh.getPhysicalLayer().isActiveQoTForOther()) {

	    	boolean QoTForOther = computeQoTForOther(circuit);
	    	circuit.setQoTForOther(QoTForOther);

	    	return QoTForOther;
		}

		// If it does not check the OSNR on other circuits then it returns acceptable
		return true;
    }

    /**
     * This method checks whether OSNR is acceptable on other circuits already active in the network.
     * Does not update OSNR and QoT of other circuits.
     *
     * @param circuit Circuit
     * @return boolean - true if OSNR is acceptable on other circuits otherwise false
     */
    public boolean isAdmissibleOSNRInOther(Circuit circuit) {

    	// Check if it is to test the OSNR of other already active circuits
		if (mesh.getPhysicalLayer().isActiveQoTForOther()) {

	    	boolean OSNRForOther = checkOSNRForOther(circuit);
	    	circuit.setQoTForOther(OSNRForOther);

	    	return OSNRForOther;
		}

		// If it does not check the OSNR on other circuits then it returns acceptable
		return true;
    }

	/**
     * This method computes the crosstalk in a circuit and checks if it is at acceptable levels
     *
     * @param circuit Circuit
     * @return boolean - true if crosstalk is acceptable otherwise false
     */
	public boolean isAdmissibleCrosstalk(Circuit circuit) {

		// Check if it is to test the crosstalk
		if (mesh.getPhysicalLayer().isActiveXT() && mesh.getPhysicalLayer().getXTModel() == PhysicalLayer.XT_SEPARATE) {

			double xt = mesh.getPhysicalLayer().getCrosstalk().calculateCrosstalk(circuit, null, false);
			boolean isAdmissible = mesh.getPhysicalLayer().getCrosstalk().isAdmissible(circuit, xt);

			circuit.setXt(xt);
			circuit.setXtAdmissible(isAdmissible);

			return isAdmissible;
		}

		// If it does not check the crosstalk then it returns acceptable
		return true;
	}

	/**
     * This method checks whether crosstalk is acceptable on other circuits already active in the network
     *
     * @param circuit Circuit
     * @return boolean - true if crosstalk is acceptable on other circuits otherwise false
     */
    public boolean isAdmissibleCrosstalkInOther(Circuit circuit) {

    	// Check if it is to test the crosstalk on other circuits
		if (mesh.getPhysicalLayer().isActiveXTForOther() && mesh.getPhysicalLayer().getXTModel() == PhysicalLayer.XT_SEPARATE) {

	    	boolean isAdmissible = mesh.getPhysicalLayer().getCrosstalk().isAdmissibleInOthers(circuit);
	    	circuit.setXtAdmissibleInOther(isAdmissible);

	    	return isAdmissible;
		}

		// If it does not check the crosstalk on other circuits then it returns acceptable
		return true;
    }

	/**
	 * This method verifies the quality of the transmission of the circuit The
	 * circuit in question has already allocated the network resources
	 *
	 * @param circuit Circuit
	 * @param addTestCircuit boolean - To add the test circuit to the circuit list
	 * @return boolean - True, if QoT is acceptable, or false, otherwise
	 */
	public boolean computeQualityOfTransmission(Circuit circuit, Circuit testCircuit, boolean addTestCircuit) {
		double SNR = mesh.getPhysicalLayer().computeSNRSegment(circuit, circuit.getRoute(), 0, circuit.getRoute().getNodeList().size() - 1,
					 circuit.getModulation(), circuit.getIndexCore(), circuit.getSpectrumAssigned(), testCircuit, addTestCircuit);
		boolean QoT = mesh.getPhysicalLayer().isAdmissible(circuit.getModulation(), SNR);

		circuit.setSNRlinear(SNR);
		circuit.setQoT(QoT);

		return QoT;
	}

	/**
	 * This method verifies the crosstalk of the circuit The circuit in question has
	 * already allocated the network resources
	 *
	 * @param circuit        Circuit
	 * @param addTestCircuit boolean - To add the test circuit to the circuit list
	 * @return boolean - True, if QoT is acceptable, or false, otherwise
	 */
	public boolean computeCrosstalk(Circuit circuit) {
		double xt = mesh.getPhysicalLayer().getCrosstalk().calculateCrosstalk(circuit, null, false);
		boolean xtAdmissible = mesh.getPhysicalLayer().getCrosstalk().isAdmissible(circuit, xt);

		circuit.setXt(xt);
		circuit.setXtAdmissible(xtAdmissible);

		return xtAdmissible;
	}

	/**
	 * This method verifies the transmission quality of the other already active circuits.
	 * Updates OSNR and QoT of other circuits.
	 *
	 * @param circuit Circuit
	 * @return boolean - True, if it did not affect another circuit, or false otherwise
	 */
	public boolean computeQoTForOther(Circuit circuit) {
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
			boolean QoT = computeQualityOfTransmission(circuitTemp, circuit, true);

			if (!QoT) {

				// Returns the SNR and QoT values of circuits before the establishment of the circuit in evaluation
				for (Circuit circuitAux : circuitsSNR.keySet()) {
					circuitAux.setSNR(circuitsSNR.get(circuitAux));
					circuitAux.setQoT(circuitsQoT.get(circuitAux));
				}

				return false;
			}
		}

		return true;
	}

	/**
	 * This method verifies the transmission quality of the other already active circuits.
	 * Does not update OSNR and QoT of other circuits.
	 *
	 * @param circuit Circuit
	 * @return boolean - True, if it did not affect another circuit, or false otherwise
	 */
	public boolean checkOSNRForOther(Circuit circuit) {
		HashSet<Circuit> circuits = new HashSet<Circuit>(); // Circuit list for test

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

			//Test this way so as not to alter the SNR and QoT of the circuit under evaluation.
			double SNR = mesh.getPhysicalLayer().computeSNRSegment(circuitTemp, circuitTemp.getRoute(), 0, circuitTemp.getRoute().getNodeList().size() - 1,
					circuitTemp.getModulation(), circuitTemp.getIndexCore(), circuitTemp.getSpectrumAssigned(), circuit, true);

			boolean QoT = mesh.getPhysicalLayer().isAdmissible(circuitTemp.getModulation(), SNR);

			if (!QoT) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Method that returns the lowest delta OSNR value of the neighbors circuits of the informed circuit.
	 *
	 * @param circuit Circuit
	 * @param route Route
	 * @param core int
	 * @param cp ControlPlane
	 * @return Double lowest delta OSNR
	 */
	public double getMinDeltaOSNRofNeighbors(Circuit circuit, Route route, int core, ControlPlane cp) {
		HashSet<Circuit> circuitList = new HashSet<Circuit>();
		for (Link link : route.getLinkList()) {
			HashSet<Circuit> circuitsAux = link.getCore(core).getCircuitList();

			for(Circuit circuitTemp : circuitsAux){
				if(!circuit.equals(circuitTemp) && !circuitList.contains(circuitTemp)){
					circuitList.add(circuitTemp);
				}
			}
		}

		double deltaSNR = 0.0;
		double mintDeltaSNR = Double.MAX_VALUE;

		for(Circuit circuitTemp : circuitList){

			cp.computeQualityOfTransmission(circuitTemp, circuit, true);
			deltaSNR = circuitTemp.getSNRlinear() - circuitTemp.getModulation().getSNRthresholdLinear();

			if(deltaSNR < mintDeltaSNR){
				mintDeltaSNR = deltaSNR;
			}
		}

		return mintDeltaSNR;
	}

	/**
	 * Calculates the amount of SNR impacted by a circuit in other circuits
	 *
	 * @param circuit Circuit
	 * @return double - SNR impact
	 */
	public double computesImpactOnSNROther(Circuit circuit) {
		HashSet<Circuit> circuits = new HashSet<Circuit>(); // Circuit list for test
		// TreeSet<Circuit> circuits = new TreeSet<Circuit>(); // Circuit list for test
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

		double SNRimpact = 0.0;
		double SNRtemp = 0.0;
		double SNRtemp2 = 0.0;
		double SNRdif = 0.0;

		for (Circuit circuitTemp : circuits) {

			// Stores the SNR and QoT values
			circuitsSNR.put(circuitTemp, circuitTemp.getSNR());
			circuitsQoT.put(circuitTemp, circuitTemp.isQoT());
			SNRtemp2 = circuitTemp.getSNR();

			// Computes the SNR of the circuitTemp without considering the circuit
			computeQualityOfTransmission(circuitTemp, circuit, false);
			SNRtemp = circuitTemp.getSNR();

			// Computes the SNR of the circuitTemp considering the circuit
			// computeQualityOfTransmission(circuitTemp, circuit, true);
			// double SNRtemp3 = circuitTemp.getSNR();

			circuitTemp.setSNR(circuitsSNR.get(circuitTemp));
			circuitTemp.setQoT(circuitsQoT.get(circuitTemp));

			SNRdif = SNRtemp - SNRtemp2;
			if (SNRdif < 0.0) {
				SNRdif = -1.0 * SNRdif;
			}

			SNRimpact += SNRdif;
		}

		return SNRimpact;
	}

	/**
	 * This method returns the power consumption of a given circuit.
	 *
	 * @return double - power consumption (W)
	 */
	public double getPowerConsumption(Circuit circuit) {
		double powerConsumption = EnergyConsumption.computePowerConsumptionBySegment(this, circuit, circuit.getRoute(),
				0, circuit.getRoute().getNodeList().size() - 1, circuit.getModulation(), circuit.getIndexCore(), circuit.getSpectrumAssigned());

		circuit.setPowerConsumption(powerConsumption);

		return powerConsumption;
	}

	/**
	 * This method returns the list of active circuits
	 *
	 * @return Circuit
	 */
	public HashSet<Circuit> getConnections() {
		return connectionList;
	}

	/**
	 * This method adds a circuit to the list of active circuits
	 *
	 * @param circuit Circuit
	 */
	public void addConnection(Circuit circuit) {
		activeCircuits.get(circuit.getSource().getName()).get(circuit.getDestination().getName()).add(circuit);

		if (!connectionList.contains(circuit)) {
			connectionList.add(circuit);
		}

		for (int i = 0; i < circuit.getRoute().getLinkList().size(); i++) {
			circuit.getRoute().getLinkList().get(i).getCore(circuit.getIndexCore()).addCircuit(circuit);
		}

		mesh.getPhysicalLayer().getCrosstalk().updateXTinOthers(circuit, true, false);
	}

	/**
	 * This method removes a circuit from the active circuit list
	 *
	 * @param circuit Circuit
	 */
	public void removeConnection(Circuit circuit) {
		activeCircuits.get(circuit.getSource().getName()).get(circuit.getDestination().getName()).remove(circuit);

		if (connectionList.contains(circuit)) {
			connectionList.remove(circuit);
		}

		for (int i = 0; i < circuit.getRoute().getLinkList().size(); i++) {
			circuit.getRoute().getLinkList().get(i).getCore(circuit.getIndexCore()).removeCircuit(circuit);
		}

		//mesh.getCrosstalk().atualizaXTnosOutrosRemocao(circuit);
	    mesh.getPhysicalLayer().getCrosstalk().updateXTinOthers(circuit, false, false);
	}

	/**
	 * This method checks whether the circuit blocking was by QoTN
	 * Returns true if the blocking was by QoTN and false otherwise
	 *
	 * @return boolean
	 */
	public boolean isBlockingByQoTN(Circuit circuit) {
		// Check if it is to test the QoT
		if (mesh.getPhysicalLayer().isActiveQoT()) {

			// Check if it is possible to compute the circuit QoT
			if (circuit.getRoute() != null && circuit.getModulation() != null && circuit.getSpectrumAssigned() != null) {

				// Check if the QoT is acceptable
				if (!computeQualityOfTransmission(circuit, null, false)) {
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * This method checks whether the circuit blocking was by QoTO
	 * Returns true if the blocking was by QoTO and false otherwise
	 *
	 * @return boolean
	 */
	public boolean isBlockingByQoTO(Circuit circuit) {
		// Check if it is to test the QoTO
		if (mesh.getPhysicalLayer().isActiveQoTForOther()) {

			// Check if it is possible to compute the circuit QoT
			if (circuit.getRoute() != null && circuit.getModulation() != null && circuit.getSpectrumAssigned() != null) {

				// Check if the QoTO is acceptable
				boolean QoTO = computeQoTForOther(circuit);
				circuit.setQoTForOther(QoTO);

				if (!QoTO) {
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * This method checks whether the circuit blocking was by XT
	 * Returns true if the blocking was by XT and false otherwise
	 *
	 * @return boolean
	 */
	public boolean isBlockingByXT(Circuit circuit) {
		// Check if it is to test the crosstalk
		if (mesh.getPhysicalLayer().isActiveXT()) {

			// Check if it is possible to compute the circuit XT
			if (circuit.getRoute() != null && circuit.getModulation() != null && circuit.getSpectrumAssigned() != null) {

				// Check if the XT is acceptable
				if (!computeCrosstalk(circuit)) {
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * This method checks whether the circuit blocking was by XTO
	 * Returns true if the blocking was by XTO and false otherwise
	 *
	 * @return boolean
	 */
	public boolean isBlockingByXTO(Circuit circuit) {
		// Check if it is to test the crosstalk on other circuits
		if (mesh.getPhysicalLayer().isActiveXTForOther()) {

			// Check if it is possible to compute the circuit XT
			if (circuit.getRoute() != null && circuit.getModulation() != null && circuit.getSpectrumAssigned() != null) {

				// Check if the XTO is acceptable
				boolean XTO = mesh.getPhysicalLayer().getCrosstalk().isAdmissibleInOthers(circuit);
		    	circuit.setXtAdmissibleInOther(XTO);

				if (!XTO) {
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * This method checks whether the circuit blocking was by fragmentation
	 * Returns true if the blocking was by fragmentation and false otherwise
	 *
	 * @param circuit Circuit
	 * @return boolean
	 */
	public boolean isBlockingByFragmentation(Circuit circuit) {

		if (circuit.getRoute() == null)
			return false;

		// Checks if can select merge slots with some modulation
		List<int[]> merge = IntersectionFreeSpectrum.merge(circuit.getRoute(), circuit.getGuardBand(), circuit.getIndexCore());

		List<Modulation> avaliableModulations = mesh.getAvaliableModulations();
		for (int m = avaliableModulations.size()-1; m >= 0; m--) {
			Modulation mod = avaliableModulations.get(m);

			int numSlotsRequired = mod.requiredSlots(circuit.getRequiredBitRate());

			for (int[] band : merge) {
	            if (band[1] - band[0] + 1 >= numSlotsRequired) {
	            	return false; // If you can select slots it is not blocking due to fragmentation
	            }
	        }
		}

		// Check whether it would be possible to establish a circuit if the free slots were together
		int totalFreeSlotsInMerge = 0;
		for (int[] band : merge) {
			totalFreeSlotsInMerge += (band[1] - band[0] + 1);
		}

		for (int m = avaliableModulations.size()-1; m >= 0; m--) {
			Modulation mod = avaliableModulations.get(m);

			int numSlotsRequired = mod.requiredSlots(circuit.getRequiredBitRate());

			if (totalFreeSlotsInMerge >= numSlotsRequired) {
				return true; // It is fragmentation blocking if it was possible to allocate the merge slots together
			}
		}

		// Checks whether it would be possible to establish a circuit if the spectrum is defragmented
		List<Link> links = new ArrayList<>(circuit.getRoute().getLinkList());
		Map<String, Integer> totalFreeSlotsPerLink = new HashMap<String, Integer>();

		for (int i = 0; i < links.size(); i++) {
			Link link = links.get(i);
			List<int[]> spectrumBands = link.getCore(circuit.getIndexCore()).getFreeSpectrumBands(circuit.getGuardBand());

			int totalFreeSlots = 0;
			for (int[] band : spectrumBands) {
				totalFreeSlots += (band[1] - band[0] + 1);
			}

			totalFreeSlotsPerLink.put(link.getName(), totalFreeSlots);
		}

		for (int m = avaliableModulations.size()-1; m >= 0; m--) {
			Modulation mod = avaliableModulations.get(m);

			int numSlotsRequired = mod.requiredSlots(circuit.getRequiredBitRate());
			int successfulLinksNumber = 0; // Number of links that were able to allocate numSlotsRequired

			for (int i = 0; i < links.size(); i++) {
				Link link = links.get(i);
				int totalFreeSlots = totalFreeSlotsPerLink.get(link.getName()); // total free slots on the link

				if (totalFreeSlots >= numSlotsRequired) {
					successfulLinksNumber++;
				}
			}

			// It is fragmentation blocking if it was possible to allocate free slots on all links
			if(successfulLinksNumber == links.size()) {
				return true;
			}
		}

		return false;
	}

	/**
	 * This method checks if there is any route link that does not have free slots
	 * Returns true if any link on the route does not have free slots and false otherwise
	 *
	 * @param circuit Circuit
	 * @return boolean
	 */
	public boolean isBlockingByLackOfFreeSpectrum(Circuit circuit) {

		if (circuit.getRoute() == null)
			return false;

		boolean lackOfFreeSpectrum = false;

		List<Link> links = new ArrayList<>(circuit.getRoute().getLinkList());
		for (int i = 0; i < links.size(); i++) {

			List<int[]> spectrumBands = links.get(i).getCore(circuit.getIndexCore()).getFreeSpectrumBands(circuit.getGuardBand());

			if(spectrumBands.isEmpty()) { //If there are no free slots on the link
				lackOfFreeSpectrum = true;
				break;
			}
		}

		return lackOfFreeSpectrum;
	}

	/**
	 * Returns the list of modulation used by the circuit
	 *
	 * @param circuit
	 * @return List<Modulation>
	 */
	public List<Modulation> getModulationsUsedByCircuit(Circuit circuit) {
		List<Modulation> modList = new ArrayList<>();
		modList.add(circuit.getModulation());
		return modList;
	}

	/**
	 * This method returns the circuit SNR delta Can change according to the type of
	 * circuit
	 *
	 * @return double - delta SNR (dB)
	 */
	public double getDeltaSNR(Circuit circuit) {
		double SNR = mesh.getPhysicalLayer().computeSNRSegment(circuit, circuit.getRoute(), 0, circuit.getRoute().getNodeList().size() - 1,
				circuit.getModulation(), circuit.getIndexCore(), circuit.getSpectrumAssigned(), null, false);
		double SNRdB = PhysicalLayer.ratioForDB(SNR); //dB

		double modulationSNRthreshold = circuit.getModulation().getSNRthreshold(); //dB
		double deltaSNR = SNRdB - modulationSNRthreshold;

		return deltaSNR;
	}

	/**
	 * Updates the network's power consumption
	 */
	public void updateNetworkPowerConsumption() {
		this.mesh.computesPowerConsmption(this);
	}

	/**
	 * Returns the data transmitted
	 *
	 * @return double
	 */
	public double getDataTransmitted() {
		double dataTransmitted = 0.0;

		HashSet<Circuit> circuitList = this.getConnections();
		for (Circuit circuit : circuitList) {
			dataTransmitted += circuit.getRequiredBitRate();
		}

		return dataTransmitted;
	}

}
