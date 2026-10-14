package qkd.control;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import request.RequestForConnection;

/**
 * Registry of QKD sessions that are currently active in one simulation.
 *
 * <p>An identity-based map is used deliberately. SNetS request objects do not
 * define a logical identifier, and the exact request object is propagated from
 * arrival to hold/departure events by the event machine.</p>
 *
 * <p>Refer\u00eancias: [MORI26].</p>
 */
public final class QKDSessionRegistry implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<RequestForConnection, QKDActiveConnection> active;

    public QKDSessionRegistry() {
        this.active = new IdentityHashMap<RequestForConnection, QKDActiveConnection>();
    }

    public void register(RequestForConnection request,
            QKDActiveConnection connection) {
        if (request == null || connection == null) {
            throw new IllegalArgumentException("request/connection cannot be null");
        }
        if (active.containsKey(request)) {
            throw new IllegalStateException(
                    "QKD session already registered for this request");
        }
        active.put(request, connection);
    }

    public QKDActiveConnection get(RequestForConnection request) {
        return active.get(request);
    }

    public boolean contains(RequestForConnection request) {
        return active.containsKey(request);
    }

    public QKDActiveConnection remove(RequestForConnection request) {
        return active.remove(request);
    }

    public int size() {
        return active.size();
    }

    public boolean isEmpty() {
        return active.isEmpty();
    }

    public List<QKDActiveConnection> snapshot() {
        return Collections.unmodifiableList(
                new ArrayList<QKDActiveConnection>(active.values()));
    }
}
