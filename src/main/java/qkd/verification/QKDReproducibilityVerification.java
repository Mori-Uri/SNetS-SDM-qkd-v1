package qkd.verification;

import java.lang.reflect.Field;
import java.util.Vector;

import network.Node;
import network.Pair;
import network.RequestGenerator;
import qkd.key.QKDKeyDemandPolicy;
import qkd.traffic.QKDRequestGenerator;
import qkd.traffic.RequestProtectionType;
import request.RequestForConnection;
import simulator.Event;
import simulator.EventMachine;
import util.RandGenerator;

/**
 * Experiment-readiness verification: QKD classification/key-demand random
 * draws must not perturb the arrival/holding traffic stream.
 */
public final class QKDReproducibilityVerification {
    private QKDReproducibilityVerification() { }

    public static void main(String[] args) throws Exception {
        final long trafficSeed = 20241009L;
        final long protectionSeed = 20241010L;
        Node a = new Node("A"); Node b = new Node("B"); Pair pair = new Pair(a,b);

        RequestGenerator classical = new RequestGenerator(pair, 100e9, 2.0, 3.0, 0.0,
                new RandGenerator(trafficSeed));
        QKDRequestGenerator qkd = new QKDRequestGenerator(pair, 100e9, 2.0, 3.0, 0.0,
                new RandGenerator(trafficSeed), new RandGenerator(protectionSeed), 0.5,
                new QKDKeyDemandPolicy(256, new double[]{64,128,256}, 2024L));

        EventMachine mc = new EventMachine(); EventMachine mq = new EventMachine();
        for (int i=0;i<100;i++) {
            classical.scheduleNextRequest(mc, null);
            qkd.scheduleNextRequest(mq, null);
        }
        Vector<Event> ec = events(mc), eq = events(mq);
        check(ec.size()==eq.size() && ec.size()==100, "unexpected event count");
        for (int i=0;i<100;i++) {
            RequestForConnection rc=(RequestForConnection)ec.get(i).getObject();
            RequestForConnection rq=(RequestForConnection)eq.get(i).getObject();
            checkClose(rc.getTimeOfRequestHours(), rq.getTimeOfRequestHours(), 0.0,
                    "arrival sequence differs at "+i);
            checkClose(rc.getTimeOfFinalizeHours(), rq.getTimeOfFinalizeHours(), 0.0,
                    "holding/finalization sequence differs at "+i);
        }

        QKDRequestGenerator q1 = generator(pair, trafficSeed, protectionSeed);
        QKDRequestGenerator q2 = generator(pair, trafficSeed, protectionSeed);
        QKDRequestGenerator q3 = generator(pair, trafficSeed+1, protectionSeed+1);
        boolean differentReplication=false;
        for(int i=0;i<1000;i++) {
            RequestProtectionType x=q1.drawProtectionType();
            RequestProtectionType y=q2.drawProtectionType();
            RequestProtectionType z=q3.drawProtectionType();
            check(x==y,"same seeds did not reproduce QKD classification stream");
            if(x!=z) differentReplication=true;
        }
        check(differentReplication,"derived replication seed did not change QKD stream");

        System.out.println("[OK] classical and QKD-enabled generators produce identical arrival/holding sequences for the same traffic seed");
        System.out.println("[OK] QKD protection classification uses an independent reproducible random stream");
        System.out.println("[OK] changing the replication-derived seed changes the stochastic realization");
        System.out.println();
        System.out.println("QKD Part 17 reproducibility verification: ALL CHECKS PASSED");
    }

    private static QKDRequestGenerator generator(Pair p,long traffic,long protection){
        return new QKDRequestGenerator(p,100e9,2.0,3.0,0.0,
                new RandGenerator(traffic),new RandGenerator(protection),0.5,
                new QKDKeyDemandPolicy(256,new double[]{64,128,256},2024L));
    }

    @SuppressWarnings("unchecked")
    private static Vector<Event> events(EventMachine m) throws Exception {
        Field f=EventMachine.class.getDeclaredField("eventList"); f.setAccessible(true);
        return (Vector<Event>)f.get(m);
    }
    private static void check(boolean c,String m){if(!c) throw new AssertionError(m);}
    private static void checkClose(double a,double b,double t,String m){
        if(Math.abs(a-b)>t) throw new AssertionError(m+": "+a+" != "+b);
    }
}
