package qkd.verification;

import java.util.HashMap;
import java.util.Vector;

import com.google.gson.Gson;

import gprmcsa.GPRMCSA;
import gprmcsa.routing.Route;
import network.Circuit;
import network.Mesh;
import network.Node;
import network.Pair;
import qkd.capacity.QKDSharedResourceManager;
import qkd.control.QKDActiveConnection;
import qkd.control.QKDControlPlane;
import qkd.control.QKDProvisioningResult;
import qkd.control.QKDProvisioningService;
import qkd.key.QKDKeyDemand;
import qkd.network.QKDMesh;
import qkd.network.QKDMeshFactory;
import qkd.traffic.RequestProtectionType;
import request.RequestForConnection;
import simulationControl.Util;
import simulationControl.parsers.NetworkConfig;
import simulationControl.parsers.OthersConfig;
import simulationControl.parsers.PhysicalLayerConfig;
import simulationControl.parsers.SimulationConfig;
import simulationControl.parsers.TrafficConfig;

/** Parts 12/13: discrete QCh activation, Q-TRx accounting and TDM sharing. */
public final class QKDDiscreteCapacityAndTdmVerification {
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
        Pair pair = findPair(mesh, "1", "2");
        Route route = directRoute(pair);

        GPRMCSA a = new GPRMCSA("notrafficgrooming","completesharing","djk","ksp",
                "modulationbydistance","firstfit","aar","qabne","fsalfav1","cpsd");
        QKDControlPlane cp = new QKDControlPlane(mesh, GPRMCSA.RMCSA_INTEGRATED,
                a.instantiateGrooming(), a.instantiateIntegratedRSA(), a.instantiateRouting(),
                a.instantiateKRouting(), a.instantiateSpectrumAssignment(), a.instantiateModulationSelection(),
                a.instantiateCoreAndSpectrumAssignment(), a.instantiateReallocation(), a.instantiatePowerAssignment());
        QKDProvisioningService p = new QKDProvisioningService(mesh);
        QKDSharedResourceManager rm = p.getResourceManager();

        QKDActiveConnection c1 = provision(cp,p,pair,route,new QKDKeyDemand(256,64.0)); // 4 Mbps
        check(rm.getActiveQuantumChannelCount()==1,"first request must activate one QCh");
        check(rm.getActiveQTrxPairs()==2,"40 km / QTND 25 km must require 2 Q-TRx pairs per active QCh");
        int usedSlotsAfterFirst = route.getLinkList().get(0).getCore(0).getUsedSlots();
        check(usedSlotsAfterFirst==1,"one QCh must consume one 12.5-GHz slot");
        System.out.println("[OK] discrete QCh #1 activated; activeQCh=1, QTRxPairs=2");

        QKDActiveConnection c2 = provision(cp,p,pair,route,new QKDKeyDemand(256,128.0)); // 2 Mbps
        check(rm.getActiveQuantumChannelCount()==1,"second small demand should reuse existing QCh through TDM");
        check(route.getLinkList().get(0).getCore(0).getUsedSlots()==1,"TDM reuse must not allocate another spectral slot");
        check(rm.getReusedCapacityReservations()>0,"TDM reuse counter was not incremented");
        System.out.println("[OK] second request shares QCh through key-rate/TDM capacity; no new slot/Q-TRx");

        QKDActiveConnection c3 = provision(cp,p,pair,route,new QKDKeyDemand(150,1.0)); // 150 Mbps
        check(rm.getActiveQuantumChannelCount()>=2,"150-Mbit/s demand plus existing load must activate parallel QCh capacity");
        check(rm.getActiveQTrxPairs()>=4,"parallel QCh must add one Q-TRx pair for every QTND span");
        check(c3.getQkdSession().getDistinctQuantumChannelCount()>=2,
                "large request must stripe its key-rate reservation over multiple QChs");
        System.out.println("[OK] large demand activates multiple discrete QChs; activeQCh="
                +rm.getActiveQuantumChannelCount()+", QTRxPairs="+rm.getActiveQTrxPairs());

        p.release(c3);
        check(rm.getActiveQuantumChannelCount()==1,"unused parallel QCh should be deactivated after large request departure");
        p.release(c2); p.release(c1);
        check(rm.getActiveQuantumChannelCount()==0,"all QChs must be released after last consumer leaves");
        check(rm.getActiveQTrxPairs()==0,"all Q-TRx pairs must return to zero after release");
        check(route.getLinkList().get(0).getCore(0).getUsedSlots()==0,"QKD spectrum leaked after final release");
        System.out.println("[OK] idle QChs are deactivated and spectrum/Q-TRx are released");
        System.out.println("\nQKD Parts 12/13 verification: ALL CHECKS PASSED");
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
    private static String networkJson(){return "{"+"\"nodes\":[{\"name\":\"1\",\"transmitters\":20,\"receivers\":20,\"regenerators\":0},{\"name\":\"2\",\"transmitters\":20,\"receivers\":20,\"regenerators\":0}],"+"\"links\":[{\"source\":\"1\",\"destination\":\"2\",\"slots\":20,\"spectrum\":1.25E10,\"size\":40.0},{\"source\":\"2\",\"destination\":\"1\",\"slots\":20,\"spectrum\":1.25E10,\"size\":40.0}],"+"\"modulations\":[{\"name\":\"4QAM\",\"maxRange\":5000.0,\"M\":4.0,\"SNR\":8.95,\"XT\":-19.03}],"+"\"cores\":[{\"id\":0,\"adjacentCores\":[1]},{\"id\":1,\"adjacentCores\":[0]}],\"guardBand\":0,\"bvtSpectralAmplitude\":1000,\"qkd\":{\"enabled\":true,\"qtndKm\":25.0,\"quantumCoreId\":0}}";}
    private static String trafficJson(){return "{\"requestGenerators\":[{\"source\":\"1\",\"destination\":\"2\",\"bitRate\":1.0E9,\"arrivalRate\":1.0,\"holdRate\":1.0,\"arrivalRateIncrease\":0.0}]}";}
    private static String simulationJson(){return "{\"requests\":1,\"rmlsaType\":1,\"routing\":\"djk\",\"kRouting\":\"ksp\",\"spectrumAssignment\":\"firstfit\",\"coreAndSpectrumAssignment\":\"qabne\",\"integratedRmlsa\":\"completesharing\",\"modulationSelection\":\"modulationbydistance\",\"grooming\":\"notrafficgrooming\",\"reallocation\":\"fsalfav1\",\"powerAssignment\":\"cpsd\",\"loadPoints\":1,\"replications\":1,\"activeMetrics\":{},\"regeneratorAssignment\":\"aar\",\"networkType\":0,\"threads\":1,\"qkdProtectedRequestProbability\":0.0}";}
    private static String physicalLayerJson(){return "{\"physicalLayerModel\":0,\"crosstalkModel\":0,\"activeQoT\":false,\"activeQoTForOther\":false,\"activeASE\":false,\"activeNLI\":false,\"activeXT\":false,\"activeXTForOther\":false,\"rateOfFEC\":0.25,\"typeOfTestQoT\":0,\"power\":0.0,\"spanLength\":80.0,\"fiberLoss\":0.2,\"fiberNonlinearity\":0.0013,\"fiberDispersion\":1.6E-5,\"centerFrequency\":1.9385E14,\"constantOfPlanck\":6.626E-34,\"noiseFigureOfOpticalAmplifier\":5.0,\"powerSaturationOfOpticalAmplifier\":16.0,\"noiseFactorModelParameterA1\":100.0,\"noiseFactorModelParameterA2\":4.0,\"typeOfAmplifierGain\":0,\"amplificationFrequency\":1.9385E14,\"switchInsertionLoss\":5.0,\"fixedPowerSpectralDensity\":false,\"referenceBandwidthForPowerSpectralDensity\":1.25E10,\"propagationConstant\":1.0E7,\"bendingRadius\":0.01,\"couplingCoefficient\":1.0E-12,\"corePitch\":4.5E-5,\"polarizationModes\":2.0}";}
}
