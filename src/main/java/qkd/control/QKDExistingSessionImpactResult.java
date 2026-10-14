package qkd.control;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of testing one new classical circuit candidate against all currently
 * active QKD sessions.
 */
public final class QKDExistingSessionImpactResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final List<QKDSessionImpact> impacts;
    private final int affectedSessions;
    private final int violatedSessions;

    public QKDExistingSessionImpactResult(List<QKDSessionImpact> impacts) {
        if (impacts == null) {
            throw new IllegalArgumentException("impacts cannot be null");
        }
        this.impacts = Collections.unmodifiableList(
                new ArrayList<QKDSessionImpact>(impacts));

        int affected = 0;
        int violated = 0;
        for (QKDSessionImpact impact : impacts) {
            if (impact == null) {
                throw new IllegalArgumentException("impacts cannot contain null");
            }
            if (impact.isAffected()) {
                affected++;
            }
            if (!impact.remainsViable()) {
                violated++;
            }
        }
        this.affectedSessions = affected;
        this.violatedSessions = violated;
    }

    public List<QKDSessionImpact> getImpacts() {
        return impacts;
    }

    public int getSessionsEvaluated() {
        return impacts.size();
    }

    public int getAffectedSessions() {
        return affectedSessions;
    }

    public int getViolatedSessions() {
        return violatedSessions;
    }

    public boolean isAdmissible() {
        return violatedSessions == 0;
    }

    public QKDSessionImpact getFirstViolation() {
        for (QKDSessionImpact impact : impacts) {
            if (!impact.remainsViable()) {
                return impact;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "QKDExistingSessionImpactResult{evaluated=" + getSessionsEvaluated()
                + ", affected=" + affectedSessions
                + ", violated=" + violatedSessions
                + ", admissible=" + isAdmissible()
                + '}';
    }
}
