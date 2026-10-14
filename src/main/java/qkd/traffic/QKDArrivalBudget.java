package qkd.traffic;

import java.io.Serializable;

/**
 * Optional cap on the number of request arrivals generated in one replication.
 *
 * <p>SNetS stops scheduling new arrivals once {@code requests} arrivals have
 * been generated, but every request generator still has one arrival already
 * scheduled, so a run actually processes about {@code requests + generators}
 * arrivals. For semi-static studies (e.g. the article-base reproduction, in
 * which the offered load is a fixed number of requests) the QKD generators of a
 * mesh share this budget and stop scheduling once the limit is reached, so
 * exactly {@code limit} arrivals are processed. Enabled with
 * {@code simulation.qkdExactRequestCount = true}.</p>
 */
public final class QKDArrivalBudget implements Serializable {
    private static final long serialVersionUID = 1L;
    private int limit = -1;
    private int scheduled;

    /** Sets the maximum number of arrivals (negative = unlimited). */
    public synchronized void setLimit(int limit) {
        this.limit = limit;
    }

    public synchronized int getLimit() {
        return limit;
    }

    /** Returns true when one more arrival may be scheduled, consuming it. */
    public synchronized boolean tryAcquire() {
        if (limit < 0) {
            return true;
        }
        if (scheduled >= limit) {
            return false;
        }
        scheduled++;
        return true;
    }

    public synchronized int getScheduled() {
        return scheduled;
    }
}
