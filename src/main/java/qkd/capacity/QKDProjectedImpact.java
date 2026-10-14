package qkd.capacity;

import java.io.Serializable;

public final class QKDProjectedImpact implements Serializable {
    private static final long serialVersionUID = 1L;
    private final boolean admissible; private final int checkedChannels;
    private final QKDSharedChannel limitingChannel;
    private final double previousCapacityBps, projectedCapacityBps, usedBps, projectedMaximumQber;
    public QKDProjectedImpact(boolean admissible,int checked,QKDSharedChannel c,double prev,double proj,double used,double qber){
        this.admissible=admissible;this.checkedChannels=checked;this.limitingChannel=c;this.previousCapacityBps=prev;this.projectedCapacityBps=proj;this.usedBps=used;this.projectedMaximumQber=qber;
    }
    public boolean isAdmissible(){return admissible;} public int getCheckedChannels(){return checkedChannels;}
    public QKDSharedChannel getLimitingChannel(){return limitingChannel;}
    public double getPreviousCapacityBps(){return previousCapacityBps;} public double getProjectedCapacityBps(){return projectedCapacityBps;}
    public double getUsedBps(){return usedBps;} public double getProjectedMaximumQber(){return projectedMaximumQber;}
    @Override public String toString(){return "QKDProjectedImpact{admissible="+admissible+", checkedChannels="+checkedChannels+", used_Mbps="+(usedBps/1e6)+", projectedCapacity_Mbps="+(projectedCapacityBps/1e6)+", projectedMaxQBER="+projectedMaximumQber+'}';}
}
