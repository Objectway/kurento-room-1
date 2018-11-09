package org.kurento.room.interfaces;

import org.kurento.client.KurentoClient;
import org.kurento.client.KurentoConnectionListener;

/**
 * Sames as {@link KurentoConnectionListener} but with the additional {@link KurentoClient} parameter.
 */
public interface KurentoClientAwareConnectionListener {
    void connected(final KurentoClient kurentoClient);
    void connectionFailed(final KurentoClient kurentoClient);
    void disconnected(final KurentoClient kurentoClient);
    void reconnected(final KurentoClient kurentoClient, final boolean sameServer);
}
