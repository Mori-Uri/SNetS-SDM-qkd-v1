package qkd.reference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import qkd.physical.BB84DecoyModel;

/** Java port of the MF quantum TDM/Q-TRx allocator used in the reference reconstruction. */
public class ReferenceQuantumTDMAllocator {

    private static final class QuantumChannel {
        final double wavelengthNm;
        final double capacityMbps;
        int remainingTimeSlots;
        QuantumChannel(double wavelengthNm,double capacityMbps,int remaining){
            this.wavelengthNm=wavelengthNm;this.capacityMbps=capacityMbps;this.remainingTimeSlots=remaining;
        }
    }

    private static final class SpanId {
        final int edgeU,edgeV,spanIndex; final double lengthKm;
        SpanId(int u,int v,int index,double length){edgeU=Math.min(u,v);edgeV=Math.max(u,v);spanIndex=index;lengthKm=length;}
        @Override public int hashCode(){long b=Double.doubleToLongBits(lengthKm);int h=17;h=31*h+edgeU;h=31*h+edgeV;h=31*h+spanIndex;h=31*h+(int)(b^(b>>>32));return h;}
        @Override public boolean equals(Object o){if(!(o instanceof SpanId))return false;SpanId s=(SpanId)o;return edgeU==s.edgeU&&edgeV==s.edgeV&&spanIndex==s.spanIndex&&Double.doubleToLongBits(lengthKm)==Double.doubleToLongBits(s.lengthKm);}
    }

    private static final class Plan {
        final boolean existing; final int channelIndex; final int slots;
        Plan(boolean existing,int channelIndex,int slots){this.existing=existing;this.channelIndex=channelIndex;this.slots=slots;}
    }

    private final class SpanPool {
        final double spanLengthKm;
        final List<QuantumChannel> channels=new ArrayList<QuantumChannel>();
        SpanPool(double spanLengthKm){this.spanLengthKm=spanLengthKm;}

        Plan plan(double demandMbps) {
            for(int i=0;i<channels.size();i++) {
                QuantumChannel channel=channels.get(i);
                int slots=slotsFor(demandMbps,channel.capacityMbps);
                if(slots<=channel.remainingTimeSlots)return new Plan(true,i,slots);
            }
            int newIndex=channels.size();
            if(newIndex>=wavelengthsNm.size())return null;
            double wavelength=wavelengthsNm.get(newIndex).doubleValue();
            double capacity=skrMbps(wavelength,spanLengthKm);
            if(capacity<=0.0)return null;
            int slots=slotsFor(demandMbps,capacity);
            if(slots>tdmSlots)return null;
            return new Plan(false,newIndex,slots);
        }

        void apply(Plan plan) {
            if(plan.existing) {
                channels.get(plan.channelIndex).remainingTimeSlots-=plan.slots;
                return;
            }
            double wavelength=wavelengthsNm.get(plan.channelIndex).doubleValue();
            double capacity=skrMbps(wavelength,spanLengthKm);
            channels.add(new QuantumChannel(wavelength,capacity,tdmSlots-plan.slots));
        }
    }

    private final ReferenceTopologySpec topology;
    private final double qtndKm;
    private final int tdmSlots;
    private final List<Double> wavelengthsNm;
    private final BB84DecoyModel physicalModel;
    private final Map<SpanId,SpanPool> pools=new LinkedHashMap<SpanId,SpanPool>();

    public ReferenceQuantumTDMAllocator(ReferenceTopologySpec topology,
            double qtndKm,int tdmSlots,int quantumChannelCount) {
        if(!(qtndKm>0.0))throw new IllegalArgumentException("qtndKm must be > 0");
        if(tdmSlots<=0)throw new IllegalArgumentException("tdmSlots must be > 0");
        this.topology=topology;this.qtndKm=qtndKm;this.tdmSlots=tdmSlots;
        this.physicalModel=new BB84DecoyModel();
        this.wavelengthsNm=buildWavelengthGrid(quantumChannelCount);
    }

    public boolean allocate(List<Integer> path,double demandMbps) {
        List<SpanPool> planPools=new ArrayList<SpanPool>();
        List<Plan> plans=new ArrayList<Plan>();
        for(SpanId span:spansForPath(path)) {
            SpanPool pool=pool(span);
            Plan plan=pool.plan(demandMbps);
            if(plan==null)return false;
            planPools.add(pool);plans.add(plan);
        }
        for(int i=0;i<plans.size();i++)planPools.get(i).apply(plans.get(i));
        return true;
    }

    public int getQtrxUsage() {
        int total=0;
        for(SpanPool pool:pools.values())total+=pool.channels.size();
        return total;
    }

    private SpanPool pool(SpanId span) {
        SpanPool pool=pools.get(span);
        if(pool==null){pool=new SpanPool(span.lengthKm);pools.put(span,pool);}return pool;
    }

    private List<SpanId> spansForPath(List<Integer> path) {
        List<SpanId> spans=new ArrayList<SpanId>();
        for(int i=0;i<path.size()-1;i++) {
            int u=path.get(i),v=path.get(i+1);
            double length=topology.getEdgeLengthKm(u,v);
            int full=(int)Math.floor(length/qtndKm+1e-12);
            double remainder=length-full*qtndKm;
            int idx=0;
            for(int j=0;j<full;j++)spans.add(new SpanId(u,v,idx++,round12(qtndKm)));
            if(remainder>1e-9)spans.add(new SpanId(u,v,idx,round12(remainder)));
        }
        return spans;
    }

    private int slotsFor(double demandMbps,double capacityMbps) {
        return Math.max(1,(int)Math.ceil(demandMbps/capacityMbps*tdmSlots-1e-12));
    }

    private double skrMbps(double wavelengthNm,double lengthKm) {
        return physicalModel.evaluateMultiFiber(wavelengthNm,lengthKm)
                .getSecretKeyRateMbps();
    }

    private List<Double> buildWavelengthGrid(int count) {
        if(count<=0)throw new IllegalArgumentException("count must be > 0");
        final double c=299792458.0;
        final double centerNm=1550.0;
        final double spacingHz=12.5e9;
        final double f0=c/(centerNm*1e-9);
        List<Integer> offsets=new ArrayList<Integer>();
        for(int k=0;offsets.size()<count;k++) {
            if(k==0)offsets.add(Integer.valueOf(0));
            else {offsets.add(Integer.valueOf(-k));if(offsets.size()<count)offsets.add(Integer.valueOf(k));}
        }
        List<Double> wavelengths=new ArrayList<Double>();
        for(Integer offset:offsets) {
            double frequency=f0+offset.intValue()*spacingHz;
            wavelengths.add(Double.valueOf(c/frequency*1e9));
        }
        Collections.sort(wavelengths,new Comparator<Double>() {
            @Override public int compare(Double a,Double b) {
                double aa=physicalModel.attenuationDbPerKm(a.doubleValue());
                double ab=physicalModel.attenuationDbPerKm(b.doubleValue());
                int x=Double.compare(aa,ab);if(x!=0)return x;
                x=Double.compare(Math.abs(a.doubleValue()-centerNm),Math.abs(b.doubleValue()-centerNm));
                if(x!=0)return x;
                return Double.compare(a.doubleValue(),b.doubleValue());
            }
        });
        return wavelengths;
    }

    private static double round12(double value) {
        return Math.round(value*1.0e12)/1.0e12;
    }
}
