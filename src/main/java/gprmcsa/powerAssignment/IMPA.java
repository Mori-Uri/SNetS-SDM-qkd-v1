package gprmcsa.powerAssignment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.Vector;
import java.util.stream.Collectors;

import gprmcsa.modulation.Modulation;
import gprmcsa.routing.Route;
import network.Circuit;
import network.ControlPlane;
import network.Core;
import network.Link;
import network.PhysicalLayer;

/**
 * Represents the IMPA component.
 */
public class IMPA implements PowerAssignmentAlgorithmInterface {

	private Double marginOSNR;
	private Double marginXT;

	/**
	 * Returns the assign launch power.
	 * @param circuit the circuit.
	 * @param route the route.
	 * @param modulation the modulation.
	 * @param core the core.
	 * @param spectrumAssigned the spectrumAssigned.
	 * @param cp the cp.
	 * @return the result of the operation.
	 */
	@Override
	public double assignLaunchPower(Circuit circuit, Route route, Modulation modulation, int core, int[] spectrumAssigned, ControlPlane cp) {

		if(marginOSNR == null){
			Map<String, String> uv = cp.getMesh().getOthersConfig().getVariables();
			if(uv.get("marginOSNR") != null) {
				marginOSNR = Double.parseDouble((String)uv.get("marginOSNR"));
			}

			if(marginOSNR == null) {
				System.out.println("The OSNR margin was not found. Using the default value.");
				marginOSNR = 1.0;
			}
		}

		if(marginXT == null){
			Map<String, String> uv = cp.getMesh().getOthersConfig().getVariables();
			if(uv.get("marginXT") != null) {
				marginXT = Double.parseDouble((String)uv.get("marginXT"));
			}

			if(marginXT == null) {
				System.out.println("The XT margin was not found. Using the default value.");
				marginXT = 1.0;
			}
		}

		//For the IMPA algorithm to work correctly the PSD must be variable.
		if (cp.getMesh().getPhysicalLayer().getFixedPowerSpectralDensity()) {
			cp.getMesh().getPhysicalLayer().setFixedPowerSpectralDensity(false);
		}

		double launchPower = ImpairmentAwareMarginPowerAssignment(circuit, route, modulation, core, spectrumAssigned, marginOSNR, marginXT, cp);
		circuit.setLaunchPowerLinear(launchPower);

		return launchPower;
	}

	/**
	 * This method applies the NewAlgo power assignment strategy.
	 *
	 * @param circuit Circuit
	 * @param route Route
	 * @param modulation Modulation
	 * @param core int
	 * @param spectrumAssigned int[]
	 * @param cp ControlPlane
	 * @return double
	 */
    public double ImpairmentAwareMarginPowerAssignment(Circuit circuit, Route route, Modulation modulation, int core, int spectrumAssigned[], double marginOSNR, double marginXT, ControlPlane cp){

    	//To avoid mistakes
		circuit.setRoute(route);
		circuit.setModulation(modulation);
		circuit.setIndexCore(core);
		circuit.setSpectrumAssigned(spectrumAssigned);

		double OSNRcurrent = 0.0;
		double XTcurrent = 0.0;

		//double OSNRth = modulation.getSNRthresholdLinear(); //Linear
		//OSNRth = OSNRth + this.marginO; //margin is linear

		double OSNRth_dB = modulation.getSNRthreshold();
		OSNRth_dB = OSNRth_dB + marginOSNR;
		double OSNRth = PhysicalLayer.ratioOfDB(OSNRth_dB);

		//double XTth = modulation.getXTthresholdLinear(); //Linear
		//XTth = XTth - this.marginX; //margin is linear

		double XTth_dB = modulation.getXTthreshold();
		XTth_dB = XTth_dB - marginXT;
		double XTth = PhysicalLayer.ratioOfDB(XTth_dB);

		double Plow = 1.0E-11; //W, -80 dBm
		double Pmax = computePmax(circuit, cp);
		double Pmin = computePmin(circuit, Plow, Pmax, cp);

        List<Double> P_candidates = generateCandidatePowers(circuit, Pmin, Pmax, cp);

        // Returns the geometric mean if no valid candidate appears
        Double bestP = Math.sqrt(Pmin * Pmax);

        for (double P : P_candidates) {

        	circuit.setLaunchPowerLinear(P); // Sets the new power in the circuit under analysis

        	//====================================================
        	// QoT of the circuit itself (OSNR)
        	OSNRcurrent = computeOSNR(circuit, cp, null, false);
            if (OSNRcurrent < OSNRth) {
            	continue; // QoTN blocking
            }

            //====================================================
            // XT of the circuit itself (XTN)
            XTcurrent = computeXT(circuit, cp, null, false);
            if (XTcurrent > XTth) {
            	continue; // Blocking due to XT
            }

            //====================================================
            // OSNR in neighboring circuits (QoTO based on OSNR)
            OSNRNeighborInfo infoOSNR = computeOSNRNeighborInfo(circuit, cp);
            if (infoOSNR.violatesThreshold) {
            	continue; // The worst neighbor exceeded the OSNR margin
            }

            //====================================================
            // XT in adjacent circuits (QoTO based on XT)
            XTNeighborInfo infoXT = computeXTNeighborsInfo(circuit, cp);
            if (infoXT.violatesThreshold) {
                continue; // Some neighbor exceeded the XT threshold
            }

            // If execution reached this point, stop and return the power
        	bestP = P;
        	break;
        }

        return bestP;
	}

    /**
     * Structure for the combined return of neighbor analysis
     */
    public static class OSNRNeighborInfo {
        public double minOSNRmargin;
        public double maxOSNRth;
        public boolean violatesThreshold;

        public OSNRNeighborInfo(double minOSNRmargin, double maxOSNRth, boolean violatesThreshold) {
            this.minOSNRmargin = minOSNRmargin;
            this.maxOSNRth = maxOSNRth;
            this.violatesThreshold = violatesThreshold;
        }
    }

    /**
     * Combined function: worst neighbor + worst threshold
     *
     * @param circuit Circuit
     * @param cp ControlPlane
     * @return NeighborInfo
     */
    public OSNRNeighborInfo computeOSNRNeighborInfo(Circuit circuit, ControlPlane cp) {
        HashSet<Circuit> neighbors = new HashSet<Circuit>(); // Circuit list for test

		// Search for all circuits that have links in common with the circuit under evaluation
		Route route = circuit.getRoute();
	    for (Link link : route.getLinkList()) {
	    	// Picks up the active circuits that use the link
	        for (Circuit circuitTemp : link.getCore(circuit.getIndexCore()).getCircuitList()) {
	        	// If the circuit is different from the circuit under evaluation and is not in the circuit list for test
	        	if (!circuit.equals(circuitTemp) && !neighbors.contains(circuitTemp)) {
	                neighbors.add(circuitTemp);
	        	}
	        }
	    }

	    // If there are no neighbors
	    if (neighbors.isEmpty()) {
	        return new OSNRNeighborInfo(0.0, 1.0, false); // (minOSNRmargin = neutral, maxOSNRth = neutral, no neighbor violated the threshold)
	    }

		double minOSNRmargin = Double.POSITIVE_INFINITY;
		double maxOSNRth = 0.0;
		boolean violates = false;

		// Tests the QoT of circuits
		for (Circuit neighbor : neighbors) {

			//Test this way so as not to alter the SNR and QoT of the circuit under evaluation.
			double OSNR = computeOSNR(neighbor, cp, circuit, true);
			double OSNRthreshold = neighbor.getModulation().getSNRthresholdLinear();
			double OSNRmargin = OSNR - OSNRthreshold;

			// Worst margin (most negative)
			if (OSNRmargin < minOSNRmargin) {
                minOSNRmargin = OSNRmargin;
			}

			// highest threshold (for normalization)
            if (OSNRthreshold > maxOSNRth) {
            	maxOSNRth = OSNRthreshold;
            }

            // Checks for violation
            if (OSNRmargin < 0.0) {
                violates = true;
            }
		}

		// numerical safety
		if (maxOSNRth == 0.0) {
			maxOSNRth = 1.0;
		}

        return new OSNRNeighborInfo(minOSNRmargin, maxOSNRth, violates);
    }

    /**
     * Structure for the combined return of the XT analysis for neighboring nodes.
     */
    public static class XTNeighborInfo {
        public double minQuality;
        public boolean violatesThreshold;

        public XTNeighborInfo(double minQuality, boolean violatesThreshold) {
            this.minQuality = minQuality;
            this.violatesThreshold = violatesThreshold;
        }
    }

    /**
     * Analyzes the XT that the 'circuit' circuit causes to its neighbors.
     *
     * @param circuit
     * @param lambdaSmooth
     * @param cp
     * @return XTNeighborInfo
     */
    public XTNeighborInfo computeXTNeighborsInfo(Circuit circuit, ControlPlane cp) {
    	TreeSet<Circuit> neighbors = new TreeSet<Circuit>();

        Route route = circuit.getRoute();
        for(Link link: route.getLinkList()) {
			ArrayList<Core> adjacentsCores = link.getAdjacentCores(circuit.getIndexCore());
			for(Core core : adjacentsCores) {
				for(Circuit adjacentCircuit : core.getCircuitList()) {
					if(cp.getMesh().getPhysicalLayer().getCrosstalk().isIntersection(circuit.getSpectrumAssigned(), adjacentCircuit.getSpectrumAssigned())) {
						if(!neighbors.contains(adjacentCircuit)) {
							neighbors.add(adjacentCircuit);
						}
					}
				}
			}
		}

        // Case where there are no neighbors: XT is not a factor here.
        if (neighbors.isEmpty()) {
            return new XTNeighborInfo(1.0, false); // (minQuality: best possible, no neighbor violates anything)
        }

        double minQuality = Double.POSITIVE_INFINITY;
        boolean violates = false;

        for (Circuit neighbor : neighbors) {

        	// XT that the neighbor experiences because of the evaluated circuit
            double XTk = computeXT(neighbor, cp, circuit, true);
            double XTth_k = neighbor.getModulation().getXTthresholdLinear();

            if (XTk > XTth_k) {
                violates = true;
            }

            // normalized XT quality (1 = optimal, 0 = critical)
            double qk = 1.0 - (XTk / XTth_k);

            // Clamp to [0, 1]
            if (qk < 0.0) qk = 0.0;
            if (qk > 1.0) qk = 1.0;

            // lower quality
            if (qk < minQuality) {
                minQuality = qk;
            }
        }

        return new XTNeighborInfo(minQuality, violates);
    }

    /**
     * Function that generates a list of candidate powers.
     * The list of powers is generated using the log-uniform and linear-uniform strategies.
     *
     * @param Pmin double
     * @param Pmax double
     * @return List<Double>
     */
    public List<Double> generateCandidatePowers(Circuit circuit, double Pmin, double Pmax, ControlPlane cp) {
    	//List of candidate powers
    	List<Double> candidates = new ArrayList<>();

    	// -----------------------------------------------------
        // (1) Obtain network load rho (values between 0 and 1)
    	// We use w = 0.7 as the default to prioritize the path (route).
    	// -----------------------------------------------------
    	double rho = measureNetworkLoad(circuit, 0.7, cp);

    	// -----------------------------------------------------
        // (2) Automatic calculation of the total number of samples
    	//    Depends on:
        //    - Amplitude do intervalo de power (ordens de grandeza)
        //    - Network load (rho)
    	// -----------------------------------------------------
    	double ratio = Pmax / Pmin;
        double orders = Math.log10(ratio);

        int N = (int)(10 + 3 * orders + 10 * rho);

        // Safety limits (to avoid exaggerating in a very heavily loaded network)
        if (N < 12) N = 12;
        if (N > 40) N = 40;

        // Split between log-uniform and uniform
        int Nlog  = N / 2;
        int Nunif = N - Nlog;

        // Safety
        if (Nlog < 4)  Nlog  = 4;
        if (Nunif < 4) Nunif = 4;

        // ----------------------------------
        // 3) Log-uniform sampling
        //    For low power and critical regions
        // ----------------------------------
        for (int i = 0; i < Nlog; i++) {
            double ratioLog = (double)i / (Nlog - 1);
            double P = Pmin * Math.pow(Pmax / Pmin, ratioLog);
            candidates.add(P);
        }

        // ----------------------------------
        // 4) Linear-uniform sampling
        //    To cover the entire interval
        // ----------------------------------
        for (int i = 0; i < Nunif; i++) {
            double P = Pmin + i * (Pmax - Pmin) / (Nunif - 1);
            candidates.add(P);
        }

        // ----------------------------------
        // 5) Ensure boundary values
        // ----------------------------------
        candidates.add(Pmin);
        candidates.add(Pmax);

        // ----------------------------------
        // 6) Remove duplicates and sort
        // ----------------------------------
        List<Double> finalCandidates = candidates.stream().distinct().sorted().collect(Collectors.toList());

        return finalCandidates;
	}
	
    /**
     * Network-load calculation rho in [0,1]
     *
     * @param circuit  circuit
     * @param w double
     * @param cp ControlPlane
     * @return double
     */
    public double measureNetworkLoad(Circuit circuit, double w, ControlPlane cp) {

    	Vector<Link> allLinks = cp.getMesh().getLinkList();
    	Vector<Link> routeLinks = circuit.getRoute().getLinkList();
    	int circuitCore = circuit.getIndexCore();

    	double usedGlobal = 0.0;
    	double totalGlobal = 0.0;
    	double utGlobal = 0.0;
    	for (Link tLink : allLinks) {
			usedGlobal += tLink.getCore(circuitCore).getUsedSlots();
			totalGlobal += tLink.getCore(circuitCore).getNumOfSlots();
    	}

    	if (totalGlobal > 0.0) {
    		utGlobal = usedGlobal / totalGlobal;
    	}

    	double usedRoute = 0.0;
    	double totalRoute = 0.0;
    	double utLocal = 0.0;
		for (Link rLink : routeLinks) {
			usedRoute += rLink.getCore(circuitCore).getUsedSlots();
			totalRoute += rLink.getCore(circuitCore).getNumOfSlots();
		}

		if (totalRoute > 0.0) {
			utLocal = usedRoute / totalRoute;
		}

		// Use of the weighted average
		double p = w * utLocal + ((1.0 - w) * utGlobal);

        return p;
    }

    /**
     * Minimum-power calculation
     *
     * @param circuit Circuit
     * @param P_low double
     * @param P_high double
     * @param cp ControlPlane
     * @return double
     */
    public double computePmin(Circuit circuit, double P_low, double P_high, ControlPlane cp) {

    	//===========================================================
        // Parameters
        //===========================================================
        double OSNRtarget = circuit.getModulation().getSNRthresholdLinear();
        double tolP       = 1e-6;   // power tolerance
        double tolOSNR    = 1e-3;   // tolerance on the OSNR margin
        int ITER          = 40;     // iterations

        double L = P_low;
        double R = P_high;

        //===========================================================
        // (1) Quick test: does P_low already reach the threshold?
        //===========================================================
        circuit.setLaunchPowerLinear(L);
        double OSNR_L = computeOSNR(circuit, cp, null, false);

        if (!Double.isNaN(OSNR_L) && OSNR_L >= OSNRtarget) {
            return L; // It already satisfies the threshold -> returns the smallest possible P
        }

        //===========================================================
        // (2) Quick test: does it still fail even with P_high?
        //===========================================================
        circuit.setLaunchPowerLinear(R);
        double OSNR_R = computeOSNR(circuit, cp, null, false);

        if (Double.isNaN(OSNR_R) || OSNR_R < OSNRtarget) {
            return R; // Even with the maximum power, the required OSNR is not reached.
        }

        //===========================================================
        // (3) Binary search
        //===========================================================
        double lastGood = R; // last valid power found

        for (int i = 0; i < ITER; i++) {

            double Pmid = (L + R) * 0.5;

            circuit.setLaunchPowerLinear(Pmid);
            double osnrMid = computeOSNR(circuit, cp, null, false);

            // Safety check against NaN, infinity, or unusual values
            if (Double.isNaN(osnrMid) || Double.isInfinite(osnrMid)) {
                // assumes that Pmid is not valid -> increases power
                L = Pmid;
                continue;
            }

            double margin = osnrMid - OSNRtarget;

            if (margin >= 0.0) {
                // Pmid satisfies the threshold -> save it as "good"
                lastGood = Pmid;

                // try to reduce power
                R = Pmid;

                if (Math.abs(margin) < tolOSNR) {
                    break; // numerical convergence of the margin
                }
            } else {
                // Pmid is insufficient -> increase power
                L = Pmid;
            }

            if (Math.abs(R - L) < tolP) {
                break; // converged in power
            }
        }

        //===========================================================
        // (4) Return the best estimate found
        //===========================================================
        return lastGood;
    }

    /**
     * Maximum-power calculation
     *
     * @param circuit Circuit
     * @param cp ControlPlane
     * @return double
     */
    public double computePmax(Circuit circuit, ControlPlane cp) {
    	double Pmax = cp.getMesh().getPhysicalLayer().computeMaximumPower(circuit, circuit.getRequiredBitRate(), circuit.getRoute(), 0, circuit.getRoute().getNodeList().size() - 1,
				circuit.getModulation(), circuit.getIndexCore(), circuit.getSpectrumAssigned());
    	return Pmax;
    }

    /**
     * Computes the OSNR of the informed circuit
     *
     * @param circuit Circuit
     * @param cp ControlPlane
     * @param testCircuit Circuit
     * @param addTestCircuit boolean
     * @return double
     */
    public double computeOSNR(Circuit circuit, ControlPlane cp, Circuit testCircuit, boolean addTestCircuit) {
    	double osnr = cp.getMesh().getPhysicalLayer().computeSNRSegment(circuit, circuit.getRoute(), 0, circuit.getRoute().getNodeList().size() - 1,
				circuit.getModulation(), circuit.getIndexCore(), circuit.getSpectrumAssigned(), testCircuit, addTestCircuit);
    	return osnr;
    }

    /**
     * Computes the XT of the informed circuit
     *
     * @param circuit Circuit
     * @param cp ControlPlane
     * @param testCircuit Circuit
     * @param addTestCircuit boolean
     * @return double
     */
    public double computeXT(Circuit circuit, ControlPlane cp, Circuit testCircuit, boolean addTestCircuit) {
    	double xt = cp.getMesh().getPhysicalLayer().getCrosstalk().calculateCrosstalk(circuit, testCircuit, addTestCircuit);
    	xt = PhysicalLayer.ratioOfDB(xt);
    	return xt;
    }

    /**
     * Returns the overlaps.
     * @param cicuit the cicuit.
     * @param cp the cp.
     * @return the overlaps.
     */
    public int getOverlaps(Circuit cicuit, ControlPlane cp) {
    	int overlaps = cp.getMesh().getPhysicalLayer().getCrosstalk().numberSlotsOverlapping(cicuit, cicuit.getRoute(), cicuit.getModulation(), cicuit.getIndexCore(), cicuit.getSpectrumAssigned());
    	return overlaps;
    }
}
