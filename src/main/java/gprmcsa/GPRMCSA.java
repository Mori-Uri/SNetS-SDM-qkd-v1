package gprmcsa;

import gprmcsa.coreSpectrumAssignment.*;
import gprmcsa.guardBandSelection.*;
import gprmcsa.integrated.*;
import gprmcsa.modulation.*;
import gprmcsa.powerAssignment.*;
import gprmcsa.reallocation.*;
import gprmcsa.regeneratorAssignment.*;
import gprmcsa.routing.*;
import gprmcsa.spectrumAssignment.*;
import gprmcsa.trafficGrooming.*;

import java.io.Serializable;

/**
 * This class is responsible for instantiating the GPRMCSA algorithms.
 * It contains a list of GPRMCSA algorithms available for simulation.
 *
 * @author Iallen
 */
public class GPRMCSA implements Serializable {

	// Network type
	public static final int TRANSPARENT = 0;
	public static final int TRANSLUCENT = 1;

	// Constants that indicate which type are the RSA algorithms (sequential or integrated)
	public static final int RMCSA_SEQUENCIAL = 0;
	public static final int RMCSA_INTEGRATED = 1;

	// Constants for indication of RMLSA algorithms Optical traffic aggregation
	private static final String GROOMING_OPT_NOTRAFFICGROOMING = "notrafficgrooming";
	private static final String GROOMING_OPT_SIMPLETRAFFICGROOMING = "simpletrafficgrooming";
	private static final String GROOMING_OPT_MGFCCF = "mgfccf";// equivalent to mtgsr
	private static final String GROOMING_OPT_MTGSR = "mtgsr";
	private static final String GROOMING_OPT_MGFCCFSRNP = "mgfccfsrnp";// equivalent to mtgsr_srnp
	private static final String GROOMING_OPT_MTGSRSRNP = "mtgsr_srnp";
	private static final String GROOMING_OPT_AUXILIARYGRAPHGROOMING = "agg";// Added by Selles
	private static final String GROOMING_OPT_AUXILIARYGRAPHGROOMINGSRNP = "agg_srnp";// Added by Selles
	private static final String GROOMING_OPT_AUXILIARYGRAPHGROOMINGSSTG1 = "agg_sstg1";// Added by Selles
	private static final String GROOMING_OPT_AUXILIARYGRAPHGROOMINGSSTG2 = "agg_sstg2";// Added by Selles

	// Routing
	private static final String ROUTING_DJK = "djk";
	private static final String ROUTING_FIXEDROUTES = "fixedroutes";
	private static final String ROUTING_SCSP = "scsp";
	private static final String ROUTING_MMRDS = "mmrds";

	// K routing
	private static final String ROUTING_K_FIXEDROUTES = "kfixedroutes";
	private static final String ROUTING_K_SP = "ksp";
	private static final String ROUTING_K_SP_NEW = "newksp";
	private static final String ROUTING_K_SPD = "kspd";
	private static final String ROUTING_K_SPH = "ksph";
	private static final String ROUTING_K_BP = "kbp";

	// Spectrum assignment
	private static final String SPECTRUM_ASSIGNMENT_FISTFIT = "firstfit";
	private static final String SPECTRUM_ASSIGNMENT_BESTFIT = "bestfit";
	private static final String SPECTRUM_ASSIGNMENT_WORSTFIT = "worstfit";
	private static final String SPECTRUM_ASSIGNMENT_EXACTFIT = "exactfit";
	private static final String SPECTRUM_ASSIGNMENT_LASTFIT = "lastfit";
	private static final String SPECTRUM_ASSIGNMENT_RANDOMFIT = "randomfit";
	private static final String SPECTRUM_ASSIGNMENT_FIRSTLASTFIT = "firstlastfit";
	private static final String SPECTRUM_ASSIGNMENT_FIRSTLASTEXACTFIT = "firstlastexactfit";

	// Integrated
	private static final String INTEGRATED_COMPLETESHARING = "completesharing";
	private static final String INTEGRATED_COMPLETESHARING_V2 = "completesharingv2";
	private static final String INTEGRATED_SPCSA = "spcsa";
	private static final String INTEGRATED_SPCSA_V2 = "spcsav2";
    private static final String INTEGRATED_KSPCSA = "kspcsa";
    private static final String INTEGRATED_KSPCSA_V2 = "kspcsav2";
    private static final String INTEGRATED_KSPCSA_V3 = "kspcsav3";
    private static final String INTEGRATED_PRMSCA = "prmsca";
    private static final String INTEGRATED_PRMSCA_V2 = "prmscav2";
    private static final String INTEGRATED_PRMSCA_V3 = "prmscav3";
    private static final String INTEGRATED_MDJK = "mdjk";
    private static final String INTEGRATED_MDJK_V2 = "mdjkv2";
    private static final String INTEGRATED_KSPXT = "kspxt";

	// Regenerator assignment
	private static final String ALL_ASSIGNMENT_OF_REGENERATOR = "aar";

	// Modulation selection
	private static final String MODULATION_BY_DISTANCE = "modulationbydistance";
	private static final String MODULATION_BY_DISTANCE2 = "modulationbydistance2";
	private static final String MODULATION_BY_QOT = "modulationbyqot";
	private static final String MODULATION_BY_QOT_SIGMA = "modulationbyqotsigma";
	private static final String MODULATION_BY_QOT_V2 = "modulationbyqotv2";
	private static final String MODULATION_BY_DISTANCE_BANDWIDTH = "modulationbydistancebandwidth";

	// Core and Spectrum assignment
	private static final String CORE_SPECTRUM_ASSIGNMENT_FIXEDCOREFIRSTFIT = "fixedcorefirstfit";
	private static final String CORE_SPECTRUM_ASSIGNMENT_INCREMENTALCOREFIRSTFIT = "incrementalcorefirstfit";
	private static final String CORE_SPECTRUM_ASSIGNMENT_INCREMENTALCOREFIRSTFITV2 = "incrementalcorefirstfitv2";
	private static final String CORE_SPECTRUM_ASSIGNMENT_RANDOMCOREFIRSTFIT = "randomcorefirstfit";
	private static final String CORE_SPECTRUM_ASSIGNMENT_RANDOMCORERANDOMFIT = "randomcorerandomfit";
	private static final String CORE_SPECTRUM_ASSIGNMENT_CSBASDM = "csbasdm"; //ABNE
	private static final String CORE_SPECTRUM_ASSIGNMENT_CSBASDM2 = "csbasdm2";
	private static final String CORE_SPECTRUM_ASSIGNMENT_COREPRIORITIZATIONFIRSTFIT = "coreprioritizationfirstfit";
	private static final String CORE_SPECTRUM_ASSIGNMENT_COREPRIORITIZATIONRANDOMFIT = "coreprioritizationrandomfit";
	private static final String CORE_SPECTRUM_ASSIGNMENT_XTAWAREGREEDYALGORITHM = "xtawaregreedyalgorithm";
	private static final String CORE_SPECTRUM_ASSIGNMENT_RCCAS = "rccas";
	private static final String CORE_SPECTRUM_ASSIGNMENT_CPCAS = "cpcas";
	private static final String CORE_SPECTRUM_ASSIGNMENT_ICXTAA = "icxtaa";

	// Reallocation
	private static final String REALLOCATION_FASTSWITCHING_ALFAV1 = "fsalfav1";
	private static final String REALLOCATION_FASTSWITCHING_ALFAV2 = "fsalfav2";
	private static final String REALLOCATION_FASTSWITCHING_ALFAV3 = "fsalfav3";
	private static final String REALLOCATION_CASD_PUSHPULL = "casdpushpull";
	private static final String REALLOCATION_PUSHPULL_CSBASDM = "pushpullcsbasdm";

	// Power assignment
	private static final String POWER_ASSIGNMENT_CPSD = "cpsd";
	private static final String POWER_ASSIGNMENT_CPA = "cpa";
	private static final String POWER_ASSIGNMENT_EPA = "epa";
	private static final String POWER_ASSIGNMENT_ENPA = "enpa";
	private static final String POWER_ASSIGNMENT_APA = "apa";
	private static final String POWER_ASSIGNMENT_APA_V2 = "apav2";
	private static final String POWER_ASSIGNMENT_APAMEN = "apamem";
	private static final String POWER_ASSIGNMENT_APANOMEN = "apanomem";
	private static final String POWER_ASSIGNMENT_PABS = "pabs";
	private static final String POWER_ASSIGNMENT_PABS_V2 = "pabsv2";
	private static final String POWER_ASSIGNMENT_IMPA = "impa";

	// Guard Band Selection
	private static final String GBS_KSPGBSA = "kspgbsa";
	private static final String GBS_KSPFGBA = "kspfgba";
	private static final String GBS_DJKGBSA = "djkgbsa";
	private static final String GBS_DJKFGBA = "djkfgba";

	// End of constants

	private String grooming;
	private String integrated;
	private String routing;
	private String kRouting;
	private String modulationSelection;
	private String spectrumAssignment;
	private String regeneratorAssignment;
	private String coreAndSpectrumAssignment;
	private String reallocation;
	private String powerAssignment;

	/**
	 * Creates a new instance of GRMLSA
	 *
	 * @param grooming                    String
	 * @param integrated                  String
	 * @param routing                     String
	 * @param kRounting                   String
	 * @param modulationSelection         String
	 * @param spectrumAssignment          String
	 * @param regeneratorAssignment       String
	 * @param coreAndSpectrumAssignment   String
	 * @param reallocation                String
	 * @param powerAssignment             String
	 */
	public GPRMCSA(String grooming, String integrated, String routing, String kRouting, String modulationSelection,
			String spectrumAssignment, String regeneratorAssignment, String coreAndSpectrumAssignment,
			String reallocation, String powerAssignment) {

		this.grooming = grooming;
		this.integrated = integrated;
		this.routing = routing;
		this.kRouting = kRouting;
		this.modulationSelection = modulationSelection;
		this.spectrumAssignment = spectrumAssignment;
		this.regeneratorAssignment = regeneratorAssignment;
		this.coreAndSpectrumAssignment = coreAndSpectrumAssignment;
		this.reallocation = reallocation;
		this.powerAssignment = powerAssignment;

		if (grooming == null)
			this.grooming = "";
		if (integrated == null)
			this.integrated = "";
		if (routing == null)
			this.routing = "";
		if (kRouting == null)
			this.kRouting = "";
		if (modulationSelection == null)
			this.modulationSelection = "";
		if (spectrumAssignment == null)
			this.spectrumAssignment = "";
		if (regeneratorAssignment == null)
			this.regeneratorAssignment = "";
		if (coreAndSpectrumAssignment == null)
			this.coreAndSpectrumAssignment = "";
		if (reallocation == null)
			this.reallocation = "";
		if (powerAssignment == null)
			this.powerAssignment = "";
	}

	/**
	 * Instance the optical traffic aggregation algorithm
	 *
	 * @throws Exception
	 * @return TrafficGroomingAlgorithm
	 */
	public TrafficGroomingAlgorithmInterface instantiateGrooming() {
		switch (this.grooming) {
		case GROOMING_OPT_NOTRAFFICGROOMING:
			return new NoTrafficGrooming();
		case GROOMING_OPT_SIMPLETRAFFICGROOMING:
			return new SimpleTrafficGrooming();
		case GROOMING_OPT_MGFCCF:
		case GROOMING_OPT_MTGSR: // equivalent
			return new MTGSR();
		case GROOMING_OPT_MGFCCFSRNP:
		case GROOMING_OPT_MTGSRSRNP: // equivalent
			return new MTGSR_SRNP();
		case GROOMING_OPT_AUXILIARYGRAPHGROOMING:
			return new AuxiliaryGraphGrooming();
		case GROOMING_OPT_AUXILIARYGRAPHGROOMINGSRNP:
			return new AuxiliaryGraphGrooming_SRNP();
		case GROOMING_OPT_AUXILIARYGRAPHGROOMINGSSTG1:
			return new AuxiliaryGraphGrooming_SSTG1();
		case GROOMING_OPT_AUXILIARYGRAPHGROOMINGSSTG2:
			return new AuxiliaryGraphGrooming_SSTG2();
		default:
			return null;
		}
	}

	/**
	 * Instance the routing algorithm
	 *
	 * @throws Exception
	 * @return RoutingInterface
	 */
	public RoutingAlgorithmInterface instantiateRouting() {
		switch (this.routing) {
			case ROUTING_DJK:
				return new DJK();
			case ROUTING_FIXEDROUTES:
				return new FixedRoutes();
			case ROUTING_MMRDS:
				return new MMRDS();
			case ROUTING_SCSP:
				return new SCSP();
			default:
				return null;
		}
	}

	/**
	 * Instance the routing algorithm
	 *
	 * @throws Exception
	 * @return RoutingInterface
	 */
	public KRoutingAlgorithmInterface instantiateKRouting() {
		switch (this.kRouting) {
			case ROUTING_K_FIXEDROUTES:
				return new KFixedRoutes();
			case ROUTING_K_SP:
				return new KShortestPaths();
			case ROUTING_K_SP_NEW:
				return new NewKShortestPaths();
			case ROUTING_K_SPD:
				return new KSPDistance();
			case ROUTING_K_SPH:
				return new KSPHops();
			case ROUTING_K_BP:
				return new KBP();
			default:
				return null;
		}
	}

	/**
	 * Instance the spectrum assignment algorithm
	 *
	 * @throws Exception
	 * @return SpectrumAssignmentInterface
	 */
	public SpectrumAssignmentAlgorithmInterface instantiateSpectrumAssignment() {
		switch (this.spectrumAssignment) {
			case SPECTRUM_ASSIGNMENT_FISTFIT:
				return new FirstFit();
			case SPECTRUM_ASSIGNMENT_BESTFIT:
				return new BestFit();
			case SPECTRUM_ASSIGNMENT_WORSTFIT:
				return new WorstFit();
			case SPECTRUM_ASSIGNMENT_EXACTFIT:
				return new ExactFit();
			case SPECTRUM_ASSIGNMENT_LASTFIT:
				return new LastFit();
			case SPECTRUM_ASSIGNMENT_RANDOMFIT:
				return new RandomFit();
			case SPECTRUM_ASSIGNMENT_FIRSTLASTFIT:
				return new FirstLastFit();
			case SPECTRUM_ASSIGNMENT_FIRSTLASTEXACTFIT:
				return new FirstLastExactFit();
			default:
				return null;
		}
	}

	/**
	 * Instance the integrated RMLSA algorithm
	 *
	 * @throws Exception
	 * @return IntegratedRSAAlgoritm
	 */
	public IntegratedRMLSAAlgorithmInterface instantiateIntegratedRSA() {
		switch (this.integrated) {
			case INTEGRATED_COMPLETESHARING:
                return new CompleteSharing();
			case INTEGRATED_COMPLETESHARING_V2:
                return new CompleteSharing_v2();
            case INTEGRATED_SPCSA:
            	return new ShortestPathsCoreAndSpectrumAssignment();
            case INTEGRATED_SPCSA_V2:
            	return new ShortestPathsCoreAndSpectrumAssignment_v2();
            case INTEGRATED_KSPCSA:
            	return new KShortestPathsCoreAndSpectrumAssignment();
            case INTEGRATED_KSPCSA_V2:
            	return new KShortestPathsCoreAndSpectrumAssignment_v2();
            case INTEGRATED_KSPCSA_V3:
            	return new KShortestPathsCoreAndSpectrumAssignment_v3();
            case INTEGRATED_PRMSCA:
            	return new IntegratedPRMCSAAlgorithm();
            case INTEGRATED_PRMSCA_V2:
            	return new IntegratedPRMCSAAlgorithm_v2();
            case INTEGRATED_PRMSCA_V3:
            	return new IntegratedPRMCSAAlgorithm_v3();
            case INTEGRATED_MDJK:
            	return new ModifiedDijkstraPathsComputation();
            case INTEGRATED_MDJK_V2:
            	return new ModifiedDijkstraPathsComputation_v2();
            case INTEGRATED_KSPXT:
            	return new KSPXT();
            case GBS_KSPGBSA:
            	return new KSPGBSAlgorithm();
            case GBS_KSPFGBA:
            	return new KSPFixedGBAlgorithm();
            case GBS_DJKGBSA:
            	return new DJKGBSAlgorithm();
            case GBS_DJKFGBA:
            	return new DJKFixedGBAlgorithm();
            default:
                return null;
		}
	}

	/**
	 * Instance the regenerators assignment algorithm
	 *
	 * @throws Exception
	 * @return RegeneratorAssignmentAlgorithmInterface
	 */
	public RegeneratorAssignmentAlgorithmInterface instantiateRegeneratorAssignment() {
		switch (this.regeneratorAssignment) {
			case ALL_ASSIGNMENT_OF_REGENERATOR:
				return new AllAssignmentOfRegenerator();
			default:
				return null;
		}
	}

	/**
	 * Instance the modulation selection algorithm
	 *
	 * @return ModulationSelectionAlgorithmInterface
	 * @throws Exception
	 */
	public ModulationSelectionAlgorithmInterface instantiateModulationSelection() {
		switch (this.modulationSelection) {
			case MODULATION_BY_DISTANCE:
				return new ModulationSelectionByDistance();
			case MODULATION_BY_DISTANCE2:
				return new ModulationSelectionByDistance2();
			case MODULATION_BY_QOT:
				return new ModulationSelectionByQoT();
			case MODULATION_BY_QOT_SIGMA:
				return new ModulationSelectionByQoTAndSigma();
			case MODULATION_BY_QOT_V2:
				return new ModulationSelectionByQoTv2();
			case MODULATION_BY_DISTANCE_BANDWIDTH:
				return new ModulationSelectionByDistanceAndBitRate();
			default:
				return null;
		}
	}

	/**
	 * Instance the core and spectrum assignment algorithm
	 *
	 * @return CoreAndSpectrumAssignmentAlgorithmInterface
	 * @throws Exception
	 */
	public CoreAndSpectrumAssignmentAlgorithmInterface instantiateCoreAndSpectrumAssignment() {
		switch (this.coreAndSpectrumAssignment) {
            case CORE_SPECTRUM_ASSIGNMENT_FIXEDCOREFIRSTFIT:
                return new FixedCoreFirstFit();
            case CORE_SPECTRUM_ASSIGNMENT_INCREMENTALCOREFIRSTFIT:
                return new IncrementalCoreFirstFit();
            case CORE_SPECTRUM_ASSIGNMENT_INCREMENTALCOREFIRSTFITV2:
                return new IncrementalCoreFirstFitv2();
            case CORE_SPECTRUM_ASSIGNMENT_RANDOMCOREFIRSTFIT:
                return new RandomCoreFirstFit();
            case CORE_SPECTRUM_ASSIGNMENT_RANDOMCORERANDOMFIT:
                return new RandomCoreRandomFit();
            case CORE_SPECTRUM_ASSIGNMENT_COREPRIORITIZATIONFIRSTFIT:
            	return new CorePrioritizationFirstFit();
            case CORE_SPECTRUM_ASSIGNMENT_COREPRIORITIZATIONRANDOMFIT:
            	return new CorePrioritizationRandomFit();
            case CORE_SPECTRUM_ASSIGNMENT_XTAWAREGREEDYALGORITHM:
            	return new XtAwareGreedyAlgorithm();
            case CORE_SPECTRUM_ASSIGNMENT_RCCAS:
            	return new RandomCoreCrosstalkAvoidanceStrategy();
            case CORE_SPECTRUM_ASSIGNMENT_CPCAS:
            	return new CorePrioritizationCrosstalkAvoidanceStrategy();
            case CORE_SPECTRUM_ASSIGNMENT_ICXTAA:
            	return new IcxtAwareAlgorithm();
            case CORE_SPECTRUM_ASSIGNMENT_CSBASDM:
                return new CSBASDM();
            case CORE_SPECTRUM_ASSIGNMENT_CSBASDM2:
                return new CSBASDM2();
            default:
                return null;
        }
	}

	/**
	 * Instance the reallocation algorithm
	 *
	 * @return ReallocationAlgorithmInterface
	 * @throws Exception
	 */
	public ReallocationAlgorithmInterface instantiateReallocation() {
		switch (this.reallocation) {
			case REALLOCATION_FASTSWITCHING_ALFAV1:
				return new FastSwitching_Alfa_v1();
			case REALLOCATION_FASTSWITCHING_ALFAV2:
				return new FastSwitching_Alfa_v2();
			case REALLOCATION_FASTSWITCHING_ALFAV3:
				return new FastSwitching_Alfa_v3();
			case REALLOCATION_CASD_PUSHPULL:
				return new CASDPushPull();
			case REALLOCATION_PUSHPULL_CSBASDM:
				return new PushPull_CSBASDM();
			default:
				return null;
		}
	}

	/**
	 * Instance the power assignment algorithm
	 *
	 * @return PowerAssignmentAlgorithmInterface
	 * @throws Exception
	 */
	public PowerAssignmentAlgorithmInterface instantiatePowerAssignment() {
		switch (this.powerAssignment) {
			case POWER_ASSIGNMENT_CPSD:
				return new CPSDAlgorithm();
			case POWER_ASSIGNMENT_CPA:
				return new CPAAlgorithm();
			case POWER_ASSIGNMENT_EPA:
				return new EPAAlgorithm();
			case POWER_ASSIGNMENT_ENPA:
				return new EnPAAlgorithm();
			case POWER_ASSIGNMENT_APA:
				return new APAAlgorithm();
			case POWER_ASSIGNMENT_PABS:
				return new PABSAlgorithm();
			case POWER_ASSIGNMENT_PABS_V2:
				return new PABSAlgorithm_v2();
			case POWER_ASSIGNMENT_APA_V2:
				return new APAAlgorithm_v2();
			case POWER_ASSIGNMENT_APAMEN:
				return new APAmem();
			case POWER_ASSIGNMENT_APANOMEN:
				return new APAnoMem();
			case POWER_ASSIGNMENT_IMPA:
				return new IMPA();
			default:
				return null;
		}
	}

}
