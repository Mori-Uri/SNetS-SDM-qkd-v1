package qkd.reference;

import java.util.ArrayList;
import java.util.List;

/** One request of the article-base reconstruction trace. */
public class ReferenceRequest {
    public final int requestId;
    public final int source;
    public final int destination;
    public final double classicalRateGbps;
    public final int updatePeriodUs;
    public final double skrDemandMbps;
    public final List<Integer> expectedPythonPath;

    public ReferenceRequest(int requestId, int source, int destination,
            double classicalRateGbps, int updatePeriodUs,
            double skrDemandMbps, List<Integer> expectedPythonPath) {
        this.requestId=requestId; this.source=source; this.destination=destination;
        this.classicalRateGbps=classicalRateGbps;
        this.updatePeriodUs=updatePeriodUs; this.skrDemandMbps=skrDemandMbps;
        this.expectedPythonPath=expectedPythonPath == null
                ? null : new ArrayList<Integer>(expectedPythonPath);
    }
}
