package qkd.verification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import com.google.gson.Gson;

import gprmcsa.GPRMCSA;
import gprmcsa.routing.Route;
import network.Circuit;
import network.Mesh;
import network.Node;
import network.Pair;
import qkd.capacity.QKDSharedChannel;
import qkd.capacity.QKDSharedResourceManager;
import qkd.control.QKDActiveConnection;
import qkd.control.QKDControlPlane;
import qkd.control.QKDProvisioningResult;
import qkd.control.QKDProvisioningService;
import qkd.key.QKDKeyDemand;
import qkd.network.QKDMesh;
import qkd.network.QKDMeshFactory;
import qkd.physical.BB84DecoyModel;
import qkd.traffic.RequestProtectionType;
import request.RequestForConnection;
import simulationControl.Util;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.OthersConfig;
import simulationControl.parsers.PhysicalLayerConfig;
import simulationControl.parsers.SimulationConfig;
import simulationControl.parsers.TrafficConfig;

/**
 * Part 21: span-granularity QCh pools (Dibaj et al. 2024 trusted-node
 * planning) - one pool per QTND span, Q-TRx = active QChs per span, pools
 * shared by both directions, 256 discrete TDM slots per QCh, no splitting of
 * a demand across QChs and k-th QCh on the k-th lowest-attenuation wavelength.
 */
public final class QKDSpanGranularityVerification {
    public static void main(String[] args) throws Exception {
        Gson gson = new Gson();
        NetworkConfig network = gson.fromJson(networkJson(), NetworkConfig.class);
        TrafficConfig traffic = gson.fromJson(trafficJson(), TrafficConfig.class);
        PhysicalLayerConfig physical = gson.fromJson(physicalLayerJson(), PhysicalLayerConfig.class);
        SimulationConfig simulation = gson.fromJson(simulationJson(), SimulationConfig.class);
        Util util = new Util(); util.projectPath = ".";
        Mesh generic = QKDMeshFactory.create(network, traffic, physical,
                new OthersConfig(), new HashMap<String, HashMap<Double, Double>>(), util, simulation);
        check(generic instanceof QKDMesh, "fixture must create QKDMesh");
        QKDMesh mesh = (QKDMesh) generic;
        check(mesh.getQkdNetworkParameters().isSpanGranularity(), "qchGranularity=span not parsed");
        check(mesh.getQkdNetworkParameters().isUndirectedChannels(), "undirectedChannels not parsed");
        check(mesh.getQkdNetworkParameters().getTdmSlots() == 256, "tdmSlots not parsed");
        check(mesh.getQkdNetworkParameters().isWavelengthGrid(), "wavelengthPolicy=grid not parsed");
        Pair forward = findPair(mesh, "1", "2");
        Pair backward = findPair(mesh, "2", "1");
        Route r12 = directRoute(forward);
        Route r21 = directRoute(backward);

        GPRMCSA a = new GPRMCSA("notrafficgrooming","completesharing","djk","ksp",
                "modulationbydistance","firstfit","aar","qabne","fsalfav1","cpsd");
        QKDControlPlane cp = new QKDControlPlane(mesh, GPRMCSA.RMCSA_INTEGRATED,
                a.instantiateGrooming(), a.instantiateIntegratedRSA(), a.instantiateRouting(),
                a.instantiateKRouting(), a.instantiateSpectrumAssignment(), a.instantiateModulationSelection(),
                a.instantiateCoreAndSpectrumAssignment(), a.instantiateReallocation(), a.instantiatePowerAssignment());
        QKDProvisioningService p = new QKDProvisioningService(mesh);
        QKDSharedResourceManager rm = p.getResourceManager();
        BB84DecoyModel bb84 = new BB84DecoyModel(mesh.getQkdPhysicalParameters());

        QKDActiveConnection c1 = provision(cp, p, forward, r12, new QKDKeyDemand(256, 64.0)); // 4 Mbit/s
        check(rm.getActiveQuantumChannelCount() == 2, "40 km / QTND 25 km: one QCh in each of the 2 spans");
        check(rm.getActiveQTrxPairs() == 2, "span granularity: one Q-TRx pair per active span QCh");
        QKDSharedChannel span0 = channel(rm, 0, 0), span1 = channel(rm, 1, 0);
        double skr25 = bb84.evaluateMultiFiber(1550.0, 25.0).getSecretKeyRateBps();
        double skr15 = bb84.evaluateMultiFiber(1550.0, 15.0).getSecretKeyRateBps();
        check(Math.abs(span0.getCapacityBps() - skr25) < 1e-3, "span-0 QCh capacity must be SKR(1550 nm, 25 km)");
        check(Math.abs(span1.getCapacityBps() - skr15) < 1e-3, "span-1 QCh capacity must be SKR(1550 nm, 15 km)");
        int slots4 = (int) Math.ceil(4e6 / skr25 * 256 - 1e-12);
        check(span0.getUsedSlots() == slots4, "4 Mbit/s on span 0 must reserve ceil(4/SKR*256) TDM slots");
        check(r12.getLinkList().get(0).getCore(0).getUsedSlots() == 1, "QCh lane 0 must occupy one slot on 1->2");
        check(r21.getLinkList().get(0).getCore(0).getUsedSlots() == 1, "undirected pools occupy the lane on 2->1 too");
        System.out.println("[OK] one QCh per QTND span (25 km + 15 km), Q-TRx=2, capacities SKR(25 km)="
                + skr25 / 1e6 + " / SKR(15 km)=" + skr15 / 1e6 + " Mbit/s, " + slots4 + " TDM slots for 4 Mbit/s");

        QKDActiveConnection c2 = provision(cp, p, backward, r21, new QKDKeyDemand(256, 128.0)); // 2 Mbit/s
        check(rm.getActiveQuantumChannelCount() == 2, "reverse direction must reuse the same span pools");
        int slots2 = (int) Math.ceil(2e6 / skr25 * 256 - 1e-12);
        check(span0.getUsedSlots() == slots4 + slots2, "reverse request must add its TDM slots to the shared QCh");
        System.out.println("[OK] 2->1 request reuses the 1->2 span QChs (undirected pools); span-0 slots="
                + span0.getUsedSlots());

        List<QKDActiveConnection> fill = new ArrayList<QKDActiveConnection>();
        while (span0.getFreeSlots() >= slots4) fill.add(provision(cp, p, forward, r12, new QKDKeyDemand(256, 64.0)));
        check(rm.getActiveQuantumChannelCount() == 2, "a QCh is reused while its free TDM slots fit the demand");
        QKDActiveConnection overflow = provision(cp, p, forward, r12, new QKDKeyDemand(256, 64.0));
        QKDSharedChannel lane1 = channel(rm, 0, 1);
        check(lane1 != null, "a demand that does not fit the free slots must activate a new QCh (no splitting)");
        check(rm.getActiveQTrxPairs() == 3, "the second QCh is only needed on the 25 km span");
        check(lane1.getWavelengthNm() != 1550.0 && Math.abs(lane1.getWavelengthNm() - 1550.0) < 0.2,
                "second QCh must use the next wavelength of the 12.5 GHz grid");
        check(lane1.getCapacityBps() < span0.getCapacityBps(), "off-centre wavelength must give a lower SKR");
        System.out.println("[OK] full QCh (" + span0.getUsedSlots() + "/256 slots) -> new QCh only on the 25 km span at "
                + lane1.getWavelengthNm() + " nm, Q-TRx=" + rm.getActiveQTrxPairs());

        RequestForConnection big = request(forward, new QKDKeyDemand(150, 1.0)); // 150 Mbit/s > one QCh
        Circuit c = cp.createNewCircuit(big); c.setRoute(r12);
        int before = rm.getActiveQTrxPairs();
        check(!p.provision(big).isSuccess(), "a demand larger than one QCh must be rejected (reference TDM rule)");
        check(rm.getActiveQTrxPairs() == before, "a rejected demand must not leave QChs active");
        System.out.println("[OK] demand above one QCh capacity is rejected and rolled back");

        p.release(overflow);
        for (QKDActiveConnection x : fill) p.release(x);
        p.release(c2); p.release(c1);
        check(rm.getActiveQuantumChannelCount() == 0, "all QChs must be released");
        check(r12.getLinkList().get(0).getCore(0).getUsedSlots() == 0
                && r21.getLinkList().get(0).getCore(0).getUsedSlots() == 0, "QKD spectrum leaked after release");
        System.out.println("[OK] all span QChs, TDM slots and both-direction lanes released");
        System.out.println("\nQKD Part 21 span-granularity verification: ALL CHECKS PASSED");
    }

    private static QKDSharedChannel channel(QKDSharedResourceManager rm, int span, int lane) {
        for (QKDSharedChannel c : rm.snapshotChannels()) if (c.getSpanIndex() == span && c.getLane() == lane) return c;
        return null;
    }

    private static RequestForConnection request(Pair pair, QKDKeyDemand demand) {
        RequestForConnection r=new RequestForConnection(); r.setPair(pair); r.setRequiredBitRate(1e9);
        r.setProtectionType(RequestProtectionType.QKD_PROTECTED); r.setQkdKeyDemand(demand);
        return r;
    }

    private static QKDActiveConnection provision(QKDControlPlane cp,QKDProvisioningService p,
            Pair pair,Route route,QKDKeyDemand demand) {
        RequestForConnection r=new RequestForConnection(); r.setPair(pair); r.setRequiredBitRate(1e9);
        r.setProtectionType(RequestProtectionType.QKD_PROTECTED); r.setQkdKeyDemand(demand);
        Circuit c=cp.createNewCircuit(r); c.setRoute(route);
        QKDProvisioningResult result=p.provision(r);
        check(result.isSuccess(),"provisioning failed: "+result.getFailureReason());
        return result.getConnection();
    }
    private static Pair findPair(Mesh mesh,String s,String d){ for(Pair p:mesh.getPairList()) if(s.equals(p.getSource().getName())&&d.equals(p.getDestination().getName())) return p; throw new IllegalStateException(); }
    private static Route directRoute(Pair pair){ Vector<Node> n=new Vector<Node>();n.add(pair.getSource());n.add(pair.getDestination());return new Route(n); }
    private static void check(boolean c,String m){if(!c)throw new AssertionError(m);}
    private static String networkJson(){return "{"+"\"nodes\":[{\"name\":\"1\",\"transmitters\":20,\"receivers\":20,\"regenerators\":0},{\"name\":\"2\",\"transmitters\":20,\"receivers\":20,\"regenerators\":0}],"+"\"links\":[{\"source\":\"1\",\"destination\":\"2\",\"slots\":40,\"spectrum\":1.25E10,\"size\":40.0},{\"source\":\"2\",\"destination\":\"1\",\"slots\":40,\"spectrum\":1.25E10,\"size\":40.0}],"+"\"modulations\":[{\"name\":\"4QAM\",\"maxRange\":5000.0,\"M\":4.0,\"SNR\":8.95,\"XT\":-19.03}],"+"\"cores\":[{\"id\":0,\"adjacentCores\":[1]},{\"id\":1,\"adjacentCores\":[0]}],\"guardBand\":0,\"bvtSpectralAmplitude\":1000,\"qkd\":{\"enabled\":true,\"qtndKm\":25.0,\"quantumCoreId\":0,\"qchGranularity\":\"span\",\"undirectedChannels\":true,\"tdmSlots\":256,\"wavelengthPolicy\":\"grid\"}}";}
    private static String trafficJson(){return "{\"requestGenerators\":[{\"source\":\"1\",\"destination\":\"2\",\"bitRate\":1.0E9,\"arrivalRate\":1.0,\"holdRate\":1.0,\"arrivalRateIncrease\":0.0}]}";}
    private static String simulationJson(){return "{\"requests\":1,\"rmlsaType\":1,\"routing\":\"djk\",\"kRouting\":\"ksp\",\"spectrumAssignment\":\"firstfit\",\"coreAndSpectrumAssignment\":\"qabne\",\"integratedRmlsa\":\"completesharing\",\"modulationSelection\":\"modulationbydistance\",\"grooming\":\"notrafficgrooming\",\"reallocation\":\"fsalfav1\",\"powerAssignment\":\"cpsd\",\"loadPoints\":1,\"replications\":1,\"activeMetrics\":{},\"regeneratorAssignment\":\"aar\",\"networkType\":0,\"threads\":1,\"qkdProtectedRequestProbability\":0.0}";}
    private static String physicalLayerJson(){return "{\"physicalLayerModel\":0,\"crosstalkModel\":0,\"activeQoT\":false,\"activeQoTForOther\":false,\"activeASE\":false,\"activeNLI\":false,\"activeXT\":false,\"activeXTForOther\":false,\"rateOfFEC\":0.25,\"typeOfTestQoT\":0,\"power\":0.0,\"spanLength\":80.0,\"fiberLoss\":0.2,\"fiberNonlinearity\":0.0013,\"fiberDispersion\":1.6E-5,\"centerFrequency\":1.9385E14,\"constantOfPlanck\":6.626E-34,\"noiseFigureOfOpticalAmplifier\":5.0,\"powerSaturationOfOpticalAmplifier\":16.0,\"noiseFactorModelParameterA1\":100.0,\"noiseFactorModelParameterA2\":4.0,\"typeOfAmplifierGain\":0,\"amplificationFrequency\":1.9385E14,\"switchInsertionLoss\":5.0,\"fixedPowerSpectralDensity\":false,\"referenceBandwidthForPowerSpectralDensity\":1.25E10,\"propagationConstant\":1.0E7,\"bendingRadius\":0.01,\"couplingCoefficient\":1.0E-12,\"corePitch\":4.5E-5,\"polarizationModes\":2.0}";}
}
