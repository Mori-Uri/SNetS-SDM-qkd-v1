package qkd.reference;

/** One topology/replication/QTND observation of the MF reference experiment. */
public class ReferenceResultRow {
    public int replication;
    public long seed;
    public String topology;
    public int qtndKm;
    public int qtrxUsage;
    public double normalizedQtrxUsagePct;
    public int requestCount;
    public int classicalAccepted;
    public int classicalBlocked;
    public int quantumBlocked;
    public double totalBlockingRatio;
    public double offeredClassicalGbps;
    public double offeredSkrMbps;
    public double acceptedSkrMbps;
    public int pythonPathMismatchCount;

    public String key(){return replication+"|"+topology+"|"+qtndKm;}
}
