package qkd.physical;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

import gprmcsa.modulation.Modulation;
import gprmcsa.routing.Route;
import network.Circuit;
import network.Core;
import network.Crosstalk;
import network.Link;
import network.PhysicalLayer;
import qkd.capacity.QKDLinkChannelPhysicalResult;
import qkd.network.QKDLinkPlan;
import qkd.network.QKDRoutePlan;
import qkd.network.QKDSpan;
import qkd.resource.QKDChannelAllocation;
import qkd.resource.QKDSession;

/**
 * QKD physical layer of the SNetS-SDM QKD addon (Part 26). It covers everything
 * between the live classical state of the multi-core fibre and the background
 * probability that enters the BB84 model. The BB84 decoy-state key-rate
 * equations stay in {@link BB84DecoyModel}, and the parameters are read from the
 * case file {@code qkdPhysicalLayer} ({@link QKDPhysicalParameters}).
 *
 * <p>Calculation chain of one QKD span (a QCh between two trusted nodes):</p>
 * <pre>
 * classical circuits on adjacent cores (+ optional pending candidate)
 *   -&gt; inter-core XT power on the span  P_XT = sum_n P_n * ISO(q,n) * h * L          [section 1]
 *   -&gt; referenced to the QKD receiver   P_Rx = T(span-&gt;Rx) * P_XT                      [section 2]
 *   -&gt; detector background              N = eta_d * eta_f * P_Rx * T_g / (h nu),
 *                                       p_XT = 1 - exp(-N), p_b = 1 - (1-p_dark)(1-p_XT) [section 3]
 *   -&gt; BB84 decoy QBER and SKR          BB84DecoyModel.evaluate(lambda, L, p_b)
 *   -&gt; route / link results             [sections 4 and 5]
 * </pre>
 *
 * <p>Refer\u00eancias (PDFs em {@code docs/artigos}; lista completa em docs/REFERENCIAS_QKD.md):</p>
 * <ul>
 *   <li>[DIBAJ24] M. R. Dibaj et al., "Cost-Optimized Quantum Communication Networks: The Crucial Role of
 *       Trusted Node Placement in Multi-Band and Multi-Fiber Realms", IWCIT 2024 - artigo-base: n\u00facleo/fibra
 *       qu\u00e2ntica dedicada, n\u00f3s confi\u00e1veis a cada QTND, BB84 com estados decoy (eq. 1).</li>
 *   <li>[LACERDA24] J. C. Lacerda Jr., A. V. T. Cartaxo, A. C. B. Soares, "Novel dynamic impairment-aware
 *       algorithm for modulation, core, and spectrum assignment in SDM-EONs", Opt. Switch. Netw. 51, 2024, e
 *       [LOBATO19] F. R. L. Lobato et al., Opt. Switch. Netw. 33, 2019 - modelo de crosstalk do SNetS
 *       (P_n * ISO * h * L, \u00edndice de sobreposi\u00e7\u00e3o espectral ISO), base da se\u00e7\u00e3o 1.</li>
 *   <li>[HAYASHI11] T. Hayashi et al., Opt. Express 2011 - coeficiente de acoplamento de pot\u00eancia h.</li>
 *   <li>[NOTA-RX] U. P. Mori, nota t\u00e9cnica "Defini\u00e7\u00e3o da contribui\u00e7\u00e3o de ru\u00eddo do crosstalk
 *       intercore no receptor QKD", 2026 - se\u00e7\u00f5es 2 e 3 (refer\u00eancia ao receptor, contagem de f\u00f3tons
 *       na janela do detector, p_b).</li>
 *   <li>[MOGH21] E. E. Moghaddam, H. Beyranvand, J. A. Salehi, IEEE JSAC 39(9), 2021 - eq. (1) do ru\u00eddo
 *       de XT intercore (modo ATTENUATED_SPAN) e eqs. (12)-(14) do fundo p_b.</li>
 * </ul>
 *
 * <p>This class replaces the former QKDCrosstalkPowerCalculator, QKDCrosstalkNoiseModel,
 * QKDCrosstalkReceiverTransfer, QKDRoutePhysicalEvaluator and
 * capacity.QKDLinkChannelEvaluator. The equations and their order of evaluation are
 * unchanged (bit-identical results).</p>
 */
public final class QKDPhysicalLayer {

    private final QKDPhysicalParameters parameters;
    private final BB84DecoyModel bb84Model;

    public QKDPhysicalLayer() {
        this(new QKDPhysicalParameters());
    }

    public QKDPhysicalLayer(QKDPhysicalParameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("parameters cannot be null");
        }
        this.parameters = parameters;
        this.bb84Model = new BB84DecoyModel(parameters);
    }

    public QKDPhysicalParameters getParameters() {
        return parameters;
    }

    /** The BB84 decoy-state model used for QBER/SKR. */
    public BB84DecoyModel getBb84Model() {
        return bb84Model;
    }

    /** Fibre attenuation alpha(lambda) in dB/km (quadratic fit around the minimum, [DIBAJ24]). */
    public double attenuationDbPerKm(double wavelengthNm) {
        return parameters.attenuationDbPerKm(wavelengthNm);
    }

    // =====================================================================
    // 1. Inter-core crosstalk power on a QKD span
    // =====================================================================


    /** Evaluates the whole physical link with the live SNetS physical layer. */
    public QKDCrosstalkPowerResult evaluateLink(
            Link link,
            final PhysicalLayer physicalLayer,
            int quantumCoreId,
            int[] quantumSpectrumAssigned) {

        if (link == null) {
            throw new IllegalArgumentException("link cannot be null");
        }
        return evaluateSegment(
                link,
                physicalLayer,
                quantumCoreId,
                quantumSpectrumAssigned,
                link.getDistance());
    }

    /**
     * Production evaluation of one logical QKD span contained in a physical
     * SNetS link.
     */
    public QKDCrosstalkPowerResult evaluateSegment(
            Link link,
            final PhysicalLayer physicalLayer,
            int quantumCoreId,
            int[] quantumSpectrumAssigned,
            double evaluatedLengthKm) {

        return evaluateSegmentWithAdditionalClassicalCircuit(
                link,
                physicalLayer,
                quantumCoreId,
                quantumSpectrumAssigned,
                evaluatedLengthKm,
                null);
    }

    /**
     * Evaluates one QKD span while hypothetically adding one classical circuit
     * that has not yet been committed to the network.
     *
     * <p>The additional circuit contributes only when all of the following are
     * true for this physical link:</p>
     * <ul>
     *   <li>the candidate route actually traverses the link;</li>
     *   <li>the candidate core is adjacent to the QKD core;</li>
     *   <li>the candidate spectrum overlaps the QKD spectrum; and</li>
     *   <li>the candidate is not already present in the active circuit list.</li>
     * </ul>
     *
     * <p>No {@link Core}, {@link Link} or spectrum state is modified by this
     * method.</p>
     */
    public QKDCrosstalkPowerResult evaluateSegmentWithAdditionalClassicalCircuit(
            Link link,
            final PhysicalLayer physicalLayer,
            int quantumCoreId,
            int[] quantumSpectrumAssigned,
            double evaluatedLengthKm,
            Circuit additionalClassicalCircuit) {

        if (physicalLayer == null) {
            throw new IllegalArgumentException("physicalLayer cannot be null");
        }
        final Crosstalk crosstalk = physicalLayer.getCrosstalk();
        if (crosstalk == null) {
            throw new IllegalStateException(
                    "SNetS PhysicalLayer does not have a Crosstalk model");
        }

        return evaluateInternal(
                link,
                quantumCoreId,
                quantumSpectrumAssigned,
                crosstalk.getH(),
                evaluatedLengthKm,
                new LaunchPowerResolver() {
                    @Override
                    public double resolve(Circuit circuit) {
                        final Modulation modulation = circuit.getModulation();
                        if (modulation == null) {
                            throw new IllegalStateException(
                                    "Classical circuit " + circuit.getId()
                                    + " has no modulation assigned");
                        }
                        return physicalLayer.getCircuitLaunchPower(circuit, modulation);
                    }
                },
                additionalClassicalCircuit);
    }

    /** Deterministic verification path using stored launch powers. */
    public QKDCrosstalkPowerResult evaluateLinkUsingStoredLaunchPowers(
            Link link,
            int quantumCoreId,
            int[] quantumSpectrumAssigned,
            double couplingCoefficientPerM) {

        if (link == null) {
            throw new IllegalArgumentException("link cannot be null");
        }
        return evaluateSegmentUsingStoredLaunchPowers(
                link,
                quantumCoreId,
                quantumSpectrumAssigned,
                couplingCoefficientPerM,
                link.getDistance());
    }

    /** Deterministic span evaluation using stored launch powers. */
    public QKDCrosstalkPowerResult evaluateSegmentUsingStoredLaunchPowers(
            Link link,
            int quantumCoreId,
            int[] quantumSpectrumAssigned,
            double couplingCoefficientPerM,
            double evaluatedLengthKm) {

        return evaluateSegmentUsingStoredLaunchPowersWithAdditionalClassicalCircuit(
                link,
                quantumCoreId,
                quantumSpectrumAssigned,
                couplingCoefficientPerM,
                evaluatedLengthKm,
                null);
    }

    /**
     * Deterministic "what-if" variant used by Part-10 verification. The same
     * candidate filtering rules as the production method are applied.
     */
    public QKDCrosstalkPowerResult evaluateSegmentUsingStoredLaunchPowersWithAdditionalClassicalCircuit(
            Link link,
            int quantumCoreId,
            int[] quantumSpectrumAssigned,
            double couplingCoefficientPerM,
            double evaluatedLengthKm,
            Circuit additionalClassicalCircuit) {

        return evaluateInternal(
                link,
                quantumCoreId,
                quantumSpectrumAssigned,
                couplingCoefficientPerM,
                evaluatedLengthKm,
                new LaunchPowerResolver() {
                    @Override
                    public double resolve(Circuit circuit) {
                        final double powerW = circuit.getLaunchPowerLinear();
                        requireNonNegativeFinite(
                                powerW,
                                "stored launch power of circuit " + circuit.getId());
                        return powerW;
                    }
                },
                additionalClassicalCircuit);
    }

    private QKDCrosstalkPowerResult evaluateInternal(
            Link link,
            int quantumCoreId,
            int[] quantumSpectrumAssigned,
            double couplingCoefficientPerM,
            double evaluatedLengthKm,
            LaunchPowerResolver launchPowerResolver,
            Circuit additionalClassicalCircuit) {

        if (link == null) {
            throw new IllegalArgumentException("link cannot be null");
        }
        validateSpectrumBand(quantumSpectrumAssigned, "quantumSpectrumAssigned");
        requireNonNegativeFinite(couplingCoefficientPerM, "couplingCoefficientPerM");
        requirePositiveFinite(evaluatedLengthKm, "evaluatedLengthKm");

        if (evaluatedLengthKm - link.getDistance() > 1e-9) {
            throw new IllegalArgumentException(
                    "evaluatedLengthKm=" + evaluatedLengthKm
                    + " exceeds physical link length=" + link.getDistance());
        }

        if (link.getCore(quantumCoreId) == null) {
            throw new IllegalArgumentException(
                    "Link " + safeLinkName(link) + " has no core with ID " + quantumCoreId);
        }

        final List<Core> adjacentCores = link.getAdjacentCores(quantumCoreId);
        if (adjacentCores == null) {
            throw new IllegalStateException(
                    "Adjacent-core list is null for QKD core " + quantumCoreId
                    + " in link " + safeLinkName(link));
        }

        final List<InterfererReference> interferers =
                collectInterferers(link, adjacentCores, additionalClassicalCircuit);

        int overlappingCircuits = 0;
        double totalPowerW = 0.0;
        final List<QKDCrosstalkContribution> contributions =
                new ArrayList<QKDCrosstalkContribution>();

        for (InterfererReference reference : interferers) {
            final Circuit interferer = reference.circuit;
            final int[] interfererSpectrum = interferer.getSpectrumAssigned();
            if (interfererSpectrum == null) {
                continue;
            }
            validateSpectrumBand(interfererSpectrum, "interfererSpectrum");

            final int overlapSlots = numberOfOverlappingSlots(
                    quantumSpectrumAssigned,
                    interfererSpectrum);
            if (overlapSlots <= 0) {
                continue;
            }

            final int interfererSlots = spectrumSize(interfererSpectrum);
            final double overlapIndex = overlapSlots / (double) interfererSlots;
            final double launchPowerW = launchPowerResolver.resolve(interferer);
            final double contributionW = calculateContributionPowerW(
                    launchPowerW,
                    overlapIndex,
                    couplingCoefficientPerM,
                    evaluatedLengthKm);

            requireNonNegativeFinite(contributionW, "XT contribution");
            totalPowerW += contributionW;
            overlappingCircuits++;

            contributions.add(new QKDCrosstalkContribution(
                    interferer.getId(),
                    reference.coreId,
                    interfererSpectrum[0],
                    interfererSpectrum[1],
                    overlapSlots,
                    interfererSlots,
                    overlapIndex,
                    launchPowerW,
                    couplingCoefficientPerM,
                    evaluatedLengthKm,
                    contributionW));
        }

        return new QKDCrosstalkPowerResult(
                safeLinkName(link),
                quantumCoreId,
                quantumSpectrumAssigned[0],
                quantumSpectrumAssigned[1],
                couplingCoefficientPerM,
                evaluatedLengthKm,
                adjacentCores.size(),
                interferers.size(),
                overlappingCircuits,
                contributions,
                totalPowerW);
    }

    private static List<InterfererReference> collectInterferers(
            Link link,
            List<Core> adjacentCores,
            Circuit additionalClassicalCircuit) {

        final List<InterfererReference> result =
                new ArrayList<InterfererReference>();
        final HashSet<Circuit> seen = new HashSet<Circuit>();

        for (Core adjacentCore : adjacentCores) {
            if (adjacentCore == null || adjacentCore.getCircuitList() == null) {
                continue;
            }
            for (Circuit circuit : adjacentCore.getCircuitList()) {
                if (circuit != null && seen.add(circuit)) {
                    result.add(new InterfererReference(circuit, adjacentCore.getId()));
                }
            }
        }

        if (additionalClassicalCircuit != null
                && !seen.contains(additionalClassicalCircuit)
                && candidateTraversesLink(additionalClassicalCircuit, link)) {

            final int candidateCoreId = qkd.metrics.BlockingDetailRecorder.rawCore(additionalClassicalCircuit);
            if (isAdjacentCore(adjacentCores, candidateCoreId)) {
                result.add(new InterfererReference(
                        additionalClassicalCircuit,
                        candidateCoreId));
            }
        }

        return result;
    }

    private static boolean candidateTraversesLink(Circuit candidate, Link link) {
        if (candidate == null || candidate.getRoute() == null
                || candidate.getRoute().getLinkList() == null) {
            return false;
        }
        for (Link routeLink : candidate.getRoute().getLinkList()) {
            if (routeLink == link) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAdjacentCore(List<Core> adjacentCores, int coreId) {
        for (Core core : adjacentCores) {
            if (core != null && core.getId() == coreId) {
                return true;
            }
        }
        return false;
    }

    /**
     * Computes one term P_n * ISO * h * L using the same km -> m conversion
     * used in the current SNetS Crosstalk implementation.
     *
     * <p>Refer\u00eancias: P_n * ISO * h * L, o mesmo termo do modelo de crosstalk do SNetS de [LACERDA24] J. C. Lacerda Jr., A. V. T. Cartaxo, A. C. B. Soares, "Novel dynamic impairment-aware algorithm for modulation, core, and spectrum assignment in SDM-EONs", Opt. Switch. Netw. 51, 2024 e [LOBATO19] F. R. L. Lobato et al., "Inter-core crosstalk aware greedy algorithm for spectrum and core assignment in SDM-EONs", Opt. Switch. Netw. 33, 2019; h = 2 k^2 r/(beta Lambda) de [HAYASHI11] T. Hayashi et al., "Design and fabrication of ultra-low crosstalk and low-loss multi-core fiber", Opt. Express, 2011.</p>
     */
    public static double calculateContributionPowerW(
            double launchPowerW,
            double overlapIndex,
            double couplingCoefficientPerM,
            double linkDistanceKm) {

        requireNonNegativeFinite(launchPowerW, "launchPowerW");
        requireProbability(overlapIndex, "overlapIndex");
        requireNonNegativeFinite(couplingCoefficientPerM, "couplingCoefficientPerM");
        requireNonNegativeFinite(linkDistanceKm, "linkDistanceKm");

        return launchPowerW
                * overlapIndex
                * couplingCoefficientPerM
                * linkDistanceKm
                * 1000.0;
    }

    /** Same ISO definition used by SNetS. */
    public static double calculateOverlapIndex(
            int[] quantumSpectrumAssigned,
            int[] interfererSpectrumAssigned) {

        validateSpectrumBand(quantumSpectrumAssigned, "quantumSpectrumAssigned");
        validateSpectrumBand(interfererSpectrumAssigned, "interfererSpectrumAssigned");

        final int overlap = numberOfOverlappingSlots(
                quantumSpectrumAssigned,
                interfererSpectrumAssigned);
        return overlap / (double) spectrumSize(interfererSpectrumAssigned);
    }

    /** Counts inclusive slot overlap. A band [10,19] contains ten slots. */
    public static int numberOfOverlappingSlots(int[] firstBand, int[] secondBand) {
        validateSpectrumBand(firstBand, "firstBand");
        validateSpectrumBand(secondBand, "secondBand");

        final int first = Math.max(firstBand[0], secondBand[0]);
        final int last = Math.min(firstBand[1], secondBand[1]);
        return last < first ? 0 : last - first + 1;
    }

    /** Returns the inclusive number of slots in [firstSlot,lastSlot]. */
    public static int spectrumSize(int[] spectrumBand) {
        validateSpectrumBand(spectrumBand, "spectrumBand");
        return spectrumBand[1] - spectrumBand[0] + 1;
    }

    private static void validateSpectrumBand(int[] band, String name) {
        if (band == null || band.length < 2) {
            throw new IllegalArgumentException(name + " must contain [firstSlot,lastSlot]");
        }
        if (band[0] > band[1]) {
            throw new IllegalArgumentException(
                    name + " has firstSlot > lastSlot: [" + band[0] + ',' + band[1] + ']');
        }
    }




    private static String safeLinkName(Link link) {
        try {
            return link.getName();
        } catch (RuntimeException exception) {
            return "<unnamed-link>";
        }
    }

    private interface LaunchPowerResolver {
        double resolve(Circuit circuit);
    }

    private static final class InterfererReference {
        private final Circuit circuit;
        private final int coreId;

        private InterfererReference(Circuit circuit, int coreId) {
            this.circuit = circuit;
            this.coreId = coreId;
        }
    }

    // =====================================================================
    // 2. Reference of the span XT power to the QKD receiver input
    // =====================================================================


    private static final double LN_10 = Math.log(10.0);


    /**
     * Applies the selected receiver-transfer approximation.
     *
     * @param generatedSpanPowerW PXT produced by the span-length expression
     * @param spanLengthKm logical QKD span length
     * @param attenuationDbPerKm QCh attenuation coefficient at the wavelength
     * @param mode receiver-transfer model
     * @return power interpreted at the QKD receiver optical input
     *
     * <p>Refer\u00eancias: [NOTA-RX] (refer\u00eancia ao receptor) e Eq. (1) de [MOGH21] (modo ATTENUATED_SPAN).</p>
     */
    public static double toReceiverPowerW(
            double generatedSpanPowerW,
            double spanLengthKm,
            double attenuationDbPerKm,
            QKDCrosstalkReceiverTransferMode mode) {

        requireNonNegativeFinite(generatedSpanPowerW, "generatedSpanPowerW");
        requirePositiveFinite(spanLengthKm, "spanLengthKm");
        requireNonNegativeFinite(attenuationDbPerKm, "attenuationDbPerKm");
        if (mode == null) {
            throw new IllegalArgumentException("mode cannot be null");
        }

        switch (mode) {
            case RECEIVER_REFERENCED:
                return generatedSpanPowerW;

            case DISTRIBUTED_QUANTUM_ATTENUATION_REFERENCE:
                return generatedSpanPowerW * distributedTransferFactor(
                        spanLengthKm,
                        attenuationDbPerKm);

            case ATTENUATED_SPAN:
                return generatedSpanPowerW * Math.pow(10.0, -attenuationDbPerKm * spanLengthKm / 10.0);

            default:
                throw new IllegalStateException("Unsupported receiver transfer mode: " + mode);
        }
    }

    /**
     * Effective length, in km, of the distributed reference approximation:
     *
     * <pre>
     * L_eff = integral_0^L 10^[-alpha (L-z)/10] dz
     * </pre>
     *
     * <p>This assumes constant P_n, ISO and h along the span and represents
     * only the remaining attenuation from each coupling point to Bob.</p>
     */
    public static double distributedEffectiveLengthKm(
            double spanLengthKm,
            double attenuationDbPerKm) {

        requirePositiveFinite(spanLengthKm, "spanLengthKm");
        requireNonNegativeFinite(attenuationDbPerKm, "attenuationDbPerKm");

        if (attenuationDbPerKm == 0.0) {
            return spanLengthKm;
        }

        final double attenuationNepersPerKm =
                attenuationDbPerKm * LN_10 / 10.0;

        return -Math.expm1(-attenuationNepersPerKm * spanLengthKm)
                / attenuationNepersPerKm;
    }

    /**
     * Ratio L_eff/L used to map the linear span PXT approximation into the
     * distributed receiver-reference sensitivity model.
     */
    public static double distributedTransferFactor(
            double spanLengthKm,
            double attenuationDbPerKm) {

        return distributedEffectiveLengthKm(spanLengthKm, attenuationDbPerKm)
                / spanLengthKm;
    }



    // =====================================================================
    // 3. XT power -> detector background probability p_b
    // =====================================================================






    /**
     * Converts absolute XT power at the optical input of the QKD receiver.
     * Receiver filter/demux loss is represented by eta_f from the parameters.
     *
     * <p>Refer\u00eancias: [NOTA-RX]: contagem de f\u00f3tons na janela do detector (Poisson) e p_b = 1-(1-p_dark)(1-p_XT); fundo p_b de [MOGH21], Eqs. (12)-(14).</p>
     */
    public QKDCrosstalkNoiseResult evaluateFromReceivedPower(
            double receivedCrosstalkPowerW,
            double wavelengthNm) {
        return evaluateFromReceivedPower(
                receivedCrosstalkPowerW,
                wavelengthNm,
                parameters.getReceiverOpticalEfficiency(),
                parameters.getDetectorGateWindowS()
        );
    }

    /**
     * Same calculation with receiver efficiency and gate window explicitly
     * supplied. This overload is preferred for controlled experiments because
     * it makes the two assumptions visible at the call site.
     */
    public QKDCrosstalkNoiseResult evaluateFromReceivedPower(
            double receivedCrosstalkPowerW,
            double wavelengthNm,
            double receiverOpticalEfficiency,
            double detectorGateWindowS) {

        requireNonNegative(receivedCrosstalkPowerW, "receivedCrosstalkPowerW");
        requirePositive(wavelengthNm, "wavelengthNm");
        requireProbability(receiverOpticalEfficiency, "receiverOpticalEfficiency");
        requirePositive(detectorGateWindowS, "detectorGateWindowS");

        final double wavelengthM = wavelengthNm * 1e-9;
        final double photonFrequencyHz = parameters.getSpeedOfLightMPerS() / wavelengthM;
        final double photonEnergyJ = parameters.getPlanckConstantJs() * photonFrequencyHz;

        final double detectorPowerW = receiverOpticalEfficiency * receivedCrosstalkPowerW;
        final double meanEvents = parameters.getDetectorEfficiency()
                * detectorPowerW
                * detectorGateWindowS
                / photonEnergyJ;

        final double pXt = -Math.expm1(-meanEvents);
        final double pDark = parameters.getDarkCountProbability();
        final double totalBackground = combineIndependentProbabilities(pDark, pXt);

        return new QKDCrosstalkNoiseResult(
                receivedCrosstalkPowerW,
                detectorPowerW,
                photonEnergyJ,
                meanEvents,
                pXt,
                totalBackground
        );
    }

    /**
     * Applies an explicit effective transfer factor T_(e->Rx) before the
     * receiver calculation. A value of 1.0 means the supplied link contribution
     * is already referenced to the QKD receiver input.
     */
    public QKDCrosstalkNoiseResult evaluateFromLinkContribution(
            double linkCrosstalkPowerW,
            double transferToReceiver,
            double wavelengthNm,
            double receiverOpticalEfficiency,
            double detectorGateWindowS) {
        requireNonNegative(linkCrosstalkPowerW, "linkCrosstalkPowerW");
        requireProbability(transferToReceiver, "transferToReceiver");
        return evaluateFromReceivedPower(
                linkCrosstalkPowerW * transferToReceiver,
                wavelengthNm,
                receiverOpticalEfficiency,
                detectorGateWindowS
        );
    }

    /**
     * Probability of at least one event when two independent background-event
     * probabilities are present in the same detection window.
     */
    public static double combineIndependentProbabilities(double p1, double p2) {
        requireProbability(p1, "p1");
        requireProbability(p2, "p2");
        return p1 + p2 - p1 * p2;
    }




    // =====================================================================
    // 4. Route evaluation: every QTND span of a route (sections 1-3 + BB84DecoyModel)
    // =====================================================================






    /**
     * Production path using the live SNetS PhysicalLayer to resolve coupling
     * coefficient and classical launch power.
     */
    public QKDRoutePhysicalResult evaluateRoute(
            Route classicalRoute,
            QKDRoutePlan qkdRoutePlan,
            PhysicalLayer physicalLayer,
            int quantumCoreId,
            int[] quantumSpectrumAssigned,
            double wavelengthNm,
            double minimumSecretKeyRateBps,
            double maximumQber) {

        validateCommon(
                classicalRoute,
                qkdRoutePlan,
                quantumSpectrumAssigned,
                wavelengthNm,
                minimumSecretKeyRateBps,
                maximumQber);
        if (physicalLayer == null) {
            throw new IllegalArgumentException("physicalLayer cannot be null");
        }

        final List<QKDSpanPhysicalResult> results =
                new ArrayList<QKDSpanPhysicalResult>();
        final Vector<Link> physicalLinks = classicalRoute.getLinkList();

        for (int linkIndex = 0; linkIndex < physicalLinks.size(); linkIndex++) {
            final Link link = physicalLinks.get(linkIndex);
            final QKDLinkPlan linkPlan = qkdRoutePlan.getLinkPlans().get(linkIndex);
            verifyLinkCorrespondence(link, linkPlan);

            for (QKDSpan span : linkPlan.getSpans()) {
                final QKDCrosstalkPowerResult power = this.evaluateSegment(
                        link,
                        physicalLayer,
                        quantumCoreId,
                        quantumSpectrumAssigned,
                        span.getLengthKm());

                results.add(evaluateSpanResult(
                        span,
                        power,
                        wavelengthNm,
                        minimumSecretKeyRateBps,
                        maximumQber));
            }
        }

        return new QKDRoutePhysicalResult(qkdRoutePlan, results);
    }


    /**
     * Production evaluation for a QKD session that has already reserved
     * spectrum. Part 8 permits independent first-fit bands on consecutive
     * physical links, so this overload evaluates each link using the exact
     * band stored in the corresponding {@link QKDChannelAllocation}.
     */
    public QKDRoutePhysicalResult evaluateAllocatedSession(
            Route classicalRoute,
            QKDRoutePlan qkdRoutePlan,
            PhysicalLayer physicalLayer,
            QKDSession session,
            double wavelengthNm,
            double minimumSecretKeyRateBps,
            double maximumQber) {

        if (session == null) {
            throw new IllegalArgumentException("session cannot be null");
        }
        if (!session.isActive()) {
            throw new IllegalArgumentException("session must still be active");
        }
        if (physicalLayer == null) {
            throw new IllegalArgumentException("physicalLayer cannot be null");
        }
        if (classicalRoute == null || qkdRoutePlan == null) {
            throw new IllegalArgumentException(
                    "classicalRoute/qkdRoutePlan cannot be null");
        }
        if (classicalRoute.getLinkList() == null
                || classicalRoute.getLinkList().isEmpty()) {
            throw new IllegalArgumentException(
                    "classicalRoute must contain at least one physical link");
        }
        if (classicalRoute.getLinkList().size()
                != qkdRoutePlan.getPhysicalLinkCount()) {
            throw new IllegalArgumentException(
                    "classicalRoute and qkdRoutePlan physical-link counts differ");
        }
        if (session.getPhysicalLinkAllocationCount()
                != classicalRoute.getLinkList().size()) {
            throw new IllegalArgumentException(
                    "QKD session allocation count differs from route link count");
        }
        if (!Double.isFinite(wavelengthNm) || wavelengthNm <= 0.0) {
            throw new IllegalArgumentException("wavelengthNm must be finite and > 0");
        }
        if (!Double.isFinite(minimumSecretKeyRateBps)
                || minimumSecretKeyRateBps < 0.0) {
            throw new IllegalArgumentException(
                    "minimumSecretKeyRateBps must be finite and >= 0");
        }
        if (!Double.isFinite(maximumQber)
                || maximumQber < 0.0 || maximumQber > 1.0) {
            throw new IllegalArgumentException(
                    "maximumQber must be finite and in [0,1]");
        }

        final List<QKDSpanPhysicalResult> results =
                new ArrayList<QKDSpanPhysicalResult>();
        final Vector<Link> physicalLinks = classicalRoute.getLinkList();
        final List<QKDChannelAllocation> allocations = session.getAllocations();

        for (int linkIndex = 0; linkIndex < physicalLinks.size(); linkIndex++) {
            final Link link = physicalLinks.get(linkIndex);
            final QKDLinkPlan linkPlan = qkdRoutePlan.getLinkPlans().get(linkIndex);
            final QKDChannelAllocation allocation = allocations.get(linkIndex);

            verifyLinkCorrespondence(link, linkPlan);
            verifyAllocationCorrespondence(link, allocation, linkIndex);

            for (QKDSpan span : linkPlan.getSpans()) {
                final QKDCrosstalkPowerResult power = this.evaluateSegment(
                        link,
                        physicalLayer,
                        allocation.getQuantumCoreId(),
                        allocation.getSpectrumBand(),
                        span.getLengthKm());

                results.add(evaluateSpanResult(
                        span,
                        power,
                        wavelengthNm,
                        minimumSecretKeyRateBps,
                        maximumQber));
            }
        }

        return new QKDRoutePhysicalResult(qkdRoutePlan, results);
    }

    /**
     * Part-10 what-if evaluation for an already allocated QKD session while a
     * new classical circuit is still only a candidate. The candidate is not
     * inserted into any SNetS active-circuit list; its XT contribution is added
     * analytically only on shared links, adjacent cores and overlapping slots.
     *
     * <p>This method is used before classical resource commitment so a new
     * classical circuit can be rejected when it would make an existing QKD
     * session violate its current SKR/QBER admission criteria.</p>
     */
    public QKDRoutePhysicalResult evaluateAllocatedSessionWithAdditionalClassicalCircuit(
            Route classicalRoute,
            QKDRoutePlan qkdRoutePlan,
            PhysicalLayer physicalLayer,
            QKDSession session,
            network.Circuit additionalClassicalCircuit,
            double wavelengthNm,
            double minimumSecretKeyRateBps,
            double maximumQber) {

        if (additionalClassicalCircuit == null) {
            return evaluateAllocatedSession(
                    classicalRoute, qkdRoutePlan, physicalLayer, session,
                    wavelengthNm, minimumSecretKeyRateBps, maximumQber);
        }
        if (session == null || !session.isActive()) {
            throw new IllegalArgumentException("session must be non-null and active");
        }
        if (physicalLayer == null) {
            throw new IllegalArgumentException("physicalLayer cannot be null");
        }
        if (classicalRoute == null || qkdRoutePlan == null) {
            throw new IllegalArgumentException(
                    "classicalRoute/qkdRoutePlan cannot be null");
        }
        if (classicalRoute.getLinkList() == null
                || classicalRoute.getLinkList().isEmpty()) {
            throw new IllegalArgumentException(
                    "classicalRoute must contain at least one physical link");
        }
        if (classicalRoute.getLinkList().size()
                != qkdRoutePlan.getPhysicalLinkCount()) {
            throw new IllegalArgumentException(
                    "classicalRoute and qkdRoutePlan physical-link counts differ");
        }
        if (session.getPhysicalLinkAllocationCount()
                != classicalRoute.getLinkList().size()) {
            throw new IllegalArgumentException(
                    "QKD session allocation count differs from route link count");
        }
        if (!Double.isFinite(wavelengthNm) || wavelengthNm <= 0.0) {
            throw new IllegalArgumentException("wavelengthNm must be finite and > 0");
        }
        if (!Double.isFinite(minimumSecretKeyRateBps)
                || minimumSecretKeyRateBps < 0.0) {
            throw new IllegalArgumentException(
                    "minimumSecretKeyRateBps must be finite and >= 0");
        }
        if (!Double.isFinite(maximumQber)
                || maximumQber < 0.0 || maximumQber > 1.0) {
            throw new IllegalArgumentException(
                    "maximumQber must be finite and in [0,1]");
        }

        final List<QKDSpanPhysicalResult> results =
                new ArrayList<QKDSpanPhysicalResult>();
        final Vector<Link> physicalLinks = classicalRoute.getLinkList();
        final List<QKDChannelAllocation> allocations = session.getAllocations();

        for (int linkIndex = 0; linkIndex < physicalLinks.size(); linkIndex++) {
            final Link link = physicalLinks.get(linkIndex);
            final QKDLinkPlan linkPlan = qkdRoutePlan.getLinkPlans().get(linkIndex);
            final QKDChannelAllocation allocation = allocations.get(linkIndex);

            verifyLinkCorrespondence(link, linkPlan);
            verifyAllocationCorrespondence(link, allocation, linkIndex);

            for (QKDSpan span : linkPlan.getSpans()) {
                final QKDCrosstalkPowerResult power =
                        this.evaluateSegmentWithAdditionalClassicalCircuit(
                                link,
                                physicalLayer,
                                allocation.getQuantumCoreId(),
                                allocation.getSpectrumBand(),
                                span.getLengthKm(),
                                additionalClassicalCircuit);

                results.add(evaluateSpanResult(
                        span,
                        power,
                        wavelengthNm,
                        minimumSecretKeyRateBps,
                        maximumQber));
            }
        }

        return new QKDRoutePhysicalResult(qkdRoutePlan, results);
    }

    /**
     * Deterministic verification/debug path. It reads the same live SNetS
     * Core/Circuit lists as production code, but uses each circuit's stored
     * launch power and an explicitly supplied coupling coefficient.
     */
    public QKDRoutePhysicalResult evaluateUsingStoredLaunchPowers(
            Route classicalRoute,
            QKDRoutePlan qkdRoutePlan,
            int quantumCoreId,
            int[] quantumSpectrumAssigned,
            double couplingCoefficientPerM,
            double wavelengthNm,
            double minimumSecretKeyRateBps,
            double maximumQber) {

        validateCommon(
                classicalRoute,
                qkdRoutePlan,
                quantumSpectrumAssigned,
                wavelengthNm,
                minimumSecretKeyRateBps,
                maximumQber);
        if (!Double.isFinite(couplingCoefficientPerM)
                || couplingCoefficientPerM < 0.0) {
            throw new IllegalArgumentException(
                    "couplingCoefficientPerM must be finite and >= 0");
        }

        final List<QKDSpanPhysicalResult> results =
                new ArrayList<QKDSpanPhysicalResult>();
        final Vector<Link> physicalLinks = classicalRoute.getLinkList();

        for (int linkIndex = 0; linkIndex < physicalLinks.size(); linkIndex++) {
            final Link link = physicalLinks.get(linkIndex);
            final QKDLinkPlan linkPlan = qkdRoutePlan.getLinkPlans().get(linkIndex);
            verifyLinkCorrespondence(link, linkPlan);

            for (QKDSpan span : linkPlan.getSpans()) {
                final QKDCrosstalkPowerResult power =
                        this.evaluateSegmentUsingStoredLaunchPowers(
                                link,
                                quantumCoreId,
                                quantumSpectrumAssigned,
                                couplingCoefficientPerM,
                                span.getLengthKm());

                results.add(evaluateSpanResult(
                        span,
                        power,
                        wavelengthNm,
                        minimumSecretKeyRateBps,
                        maximumQber));
            }
        }

        return new QKDRoutePhysicalResult(qkdRoutePlan, results);
    }

    private QKDSpanPhysicalResult evaluateSpanResult(
            QKDSpan span,
            QKDCrosstalkPowerResult power,
            double wavelengthNm,
            double minimumSecretKeyRateBps,
            double maximumQber) {

        final double attenuationDbPerKm =
                parameters.attenuationDbPerKm(wavelengthNm);

        final double receivedCrosstalkPowerW =
                toReceiverPowerW(
                        power.getTotalPowerW(),
                        span.getLengthKm(),
                        attenuationDbPerKm,
                        parameters.getReceiverTransferMode());

        final QKDCrosstalkNoiseResult noise = this.evaluateFromReceivedPower(
                receivedCrosstalkPowerW,
                wavelengthNm,
                parameters.getReceiverOpticalEfficiency(),
                parameters.getDetectorGateWindowS());

        final QKDPhysicalResult physical = bb84Model.evaluate(
                wavelengthNm,
                span.getLengthKm(),
                noise.getTotalBackgroundProbability());

        return new QKDSpanPhysicalResult(
                span,
                power,
                noise,
                physical,
                minimumSecretKeyRateBps,
                maximumQber);
    }

    private static void validateCommon(
            Route classicalRoute,
            QKDRoutePlan qkdRoutePlan,
            int[] quantumSpectrumAssigned,
            double wavelengthNm,
            double minimumSecretKeyRateBps,
            double maximumQber) {

        if (classicalRoute == null) {
            throw new IllegalArgumentException("classicalRoute cannot be null");
        }
        if (qkdRoutePlan == null) {
            throw new IllegalArgumentException("qkdRoutePlan cannot be null");
        }
        if (classicalRoute.getLinkList() == null
                || classicalRoute.getLinkList().isEmpty()) {
            throw new IllegalArgumentException(
                    "classicalRoute must contain at least one physical link");
        }
        if (classicalRoute.getLinkList().size()
                != qkdRoutePlan.getPhysicalLinkCount()) {
            throw new IllegalArgumentException(
                    "classicalRoute and qkdRoutePlan physical-link counts differ");
        }
        if (quantumSpectrumAssigned == null
                || quantumSpectrumAssigned.length < 2
                || quantumSpectrumAssigned[0] > quantumSpectrumAssigned[1]) {
            throw new IllegalArgumentException(
                    "quantumSpectrumAssigned must be [firstSlot,lastSlot]");
        }
        if (!Double.isFinite(wavelengthNm) || wavelengthNm <= 0.0) {
            throw new IllegalArgumentException("wavelengthNm must be finite and > 0");
        }
        if (!Double.isFinite(minimumSecretKeyRateBps)
                || minimumSecretKeyRateBps < 0.0) {
            throw new IllegalArgumentException(
                    "minimumSecretKeyRateBps must be finite and >= 0");
        }
        if (!Double.isFinite(maximumQber)
                || maximumQber < 0.0 || maximumQber > 1.0) {
            throw new IllegalArgumentException(
                    "maximumQber must be finite and in [0,1]");
        }
    }


    private static void verifyAllocationCorrespondence(
            Link link,
            QKDChannelAllocation allocation,
            int linkIndex) {
        if (allocation == null || allocation.getLink() != link) {
            throw new IllegalArgumentException(
                    "QKD session allocation does not correspond to classical link index "
                    + linkIndex);
        }
    }

    private static void verifyLinkCorrespondence(
            Link link,
            QKDLinkPlan linkPlan) {

        final String source = link.getSource().getName();
        final String destination = link.getDestination().getName();

        if (!source.equals(linkPlan.getSource())
                || !destination.equals(linkPlan.getDestination())) {
            throw new IllegalArgumentException(
                    "QKD link plan " + linkPlan.getSource() + "->"
                    + linkPlan.getDestination()
                    + " does not correspond to classical link "
                    + source + "->" + destination);
        }
        if (Math.abs(link.getDistance() - linkPlan.getPhysicalLengthKm()) > 1e-9) {
            throw new IllegalArgumentException(
                    "QKD link-plan length differs from classical SNetS link length");
        }
    }

    // =====================================================================
    // 5. Link evaluation: one discrete QCh on every QTND span of one physical link
    // =====================================================================



    public QKDLinkChannelPhysicalResult evaluateLinkChannel(Link link, QKDLinkPlan plan,
            PhysicalLayer physicalLayer, QKDChannelAllocation allocation,
            double wavelengthNm) {
        return evaluateLinkChannelInternal(link, plan, physicalLayer, allocation, wavelengthNm, null);
    }

    public QKDLinkChannelPhysicalResult evaluateLinkChannelWithAdditionalClassicalCircuit(Link link,
            QKDLinkPlan plan, PhysicalLayer physicalLayer,
            QKDChannelAllocation allocation, double wavelengthNm,
            Circuit candidate) {
        return evaluateLinkChannelInternal(link, plan, physicalLayer, allocation, wavelengthNm, candidate);
    }

    private QKDLinkChannelPhysicalResult evaluateLinkChannelInternal(Link link, QKDLinkPlan plan,
            PhysicalLayer physicalLayer, QKDChannelAllocation allocation,
            double wavelengthNm, Circuit candidate) {
        if (link == null || plan == null || physicalLayer == null || allocation == null)
            throw new IllegalArgumentException("link/plan/physicalLayer/allocation cannot be null");
        List<QKDSpanPhysicalResult> results = new ArrayList<QKDSpanPhysicalResult>();
        for (QKDSpan span : plan.getSpans()) {
            QKDCrosstalkPowerResult power;
            if (!parameters.isIntercoreCrosstalkEnabled()) {
                int[] b = allocation.getSpectrumBand();
                power = new QKDCrosstalkPowerResult(link.getName(), allocation.getQuantumCoreId(),
                        b[0], b[1], 0.0, span.getLengthKm(), 0, 0, 0,
                        Collections.<qkd.physical.QKDCrosstalkContribution>emptyList(), 0.0);
            } else {
                power = candidate == null
                        ? this.evaluateSegment(link, physicalLayer,
                                allocation.getQuantumCoreId(), allocation.getSpectrumBand(), span.getLengthKm())
                        : this.evaluateSegmentWithAdditionalClassicalCircuit(link, physicalLayer,
                                allocation.getQuantumCoreId(), allocation.getSpectrumBand(), span.getLengthKm(), candidate);
            }
            double attenuation = parameters.attenuationDbPerKm(wavelengthNm);
            double pxtRx = toReceiverPowerW(
                    power.getTotalPowerW(), span.getLengthKm(), attenuation,
                    parameters.getReceiverTransferMode());
            QKDCrosstalkNoiseResult noise = this.evaluateFromReceivedPower(
                    pxtRx, wavelengthNm, parameters.getReceiverOpticalEfficiency(),
                    parameters.getDetectorGateWindowS());
            QKDPhysicalResult physical = bb84Model.evaluate(
                    wavelengthNm, span.getLengthKm(), noise.getTotalBackgroundProbability());
            results.add(new QKDSpanPhysicalResult(span, power, noise, physical, 0.0, 1.0));
        }
        return new QKDLinkChannelPhysicalResult(results);
    }

    // =====================================================================
    // argument checks
    // =====================================================================

    private static void requireProbability(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0,1]");
        }
    }

    private static void requireNonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and >= 0");
        }
    }

    private static void requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and > 0");
        }
    }

    private static void requirePositive(double value, String name) {
        requirePositiveFinite(value, name);
    }

    private static void requireNonNegative(double value, String name) {
        requireNonNegativeFinite(value, name);
    }
}
