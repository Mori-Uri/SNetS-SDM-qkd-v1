package qkd.network;

import java.util.ArrayList;
import java.util.List;

import gprmcsa.routing.Route;
import network.Link;
import request.RequestForConnection;

/**
 * Converts an already-selected classical SNetS route into its logical QKD
 * route plan using the topology segmentation produced in Part 3.
 *
 * <p>Refer\u00eancias: [DIBAJ24] (repasse de chave hop a hop pelos n\u00f3s confi\u00e1veis), [MORI26].</p>
 */
public final class QKDRoutePlanner {

    private final QKDTopologyPlan topologyPlan;

    public QKDRoutePlanner(QKDTopologyPlan topologyPlan) {
        if (topologyPlan == null) {
            throw new IllegalArgumentException("topologyPlan cannot be null");
        }
        if (!topologyPlan.isEnabled()) {
            throw new IllegalArgumentException(
                    "QKDRoutePlanner requires an enabled QKD topology plan");
        }
        this.topologyPlan = topologyPlan;
    }

    public QKDRoutePlan plan(Route classicalRoute) {
        if (classicalRoute == null) {
            throw new IllegalArgumentException("classicalRoute cannot be null");
        }
        if (classicalRoute.getLinkList() == null
                || classicalRoute.getLinkList().isEmpty()) {
            throw new IllegalArgumentException(
                    "classicalRoute must contain at least one physical link");
        }

        List<QKDLinkPlan> routeLinks = new ArrayList<QKDLinkPlan>();

        for (Link link : classicalRoute.getLinkList()) {
            String source = link.getSource().getName();
            String destination = link.getDestination().getName();

            QKDLinkPlan linkPlan = topologyPlan.findLink(source, destination);
            if (linkPlan == null) {
                throw new IllegalStateException(
                        "No precomputed QKD link plan for classical route link "
                        + source + "->" + destination);
            }
            routeLinks.add(linkPlan);
        }

        return new QKDRoutePlan(
                classicalRoute.getSource().getName(),
                classicalRoute.getDestination().getName(),
                routeLinks);
    }

    /**
     * Convenience overload for the protected-request flow. The classical
     * circuit route must already have been selected before this method is used.
     */
    public QKDRoutePlan plan(RequestForConnection request, Route classicalRoute) {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        if (!request.isQkdProtected()) {
            throw new IllegalArgumentException(
                    "QKD route planning is only valid for QKD_PROTECTED requests");
        }
        return plan(classicalRoute);
    }

    public QKDTopologyPlan getTopologyPlan() {
        return topologyPlan;
    }
}
