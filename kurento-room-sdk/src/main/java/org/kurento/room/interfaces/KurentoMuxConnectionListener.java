package org.kurento.room.interfaces;

import org.kurento.client.KurentoClient;
import org.kurento.client.KurentoConnectionListener;

/**
 * Bridges {@link KurentoConnectionListener} to {@link KurentoClientAwareConnectionListener}.
 */
public class KurentoMuxConnectionListener implements KurentoConnectionListener {
    private final KurentoClientAwareConnectionListener kurentoClientAwareConnectionListener;
    private KurentoClient client;

    public KurentoMuxConnectionListener(final KurentoClientAwareConnectionListener kurentoClientAwareConnectionListener) {
        this.kurentoClientAwareConnectionListener = kurentoClientAwareConnectionListener;
    }

    @Override
    public void connected() {
        kurentoClientAwareConnectionListener.connected(client);
    }

    @Override
    public void connectionFailed() {
        kurentoClientAwareConnectionListener.connectionFailed(client);
    }

    @Override
    public void disconnected() {
        kurentoClientAwareConnectionListener.disconnected(client);
    }

    @Override
    public void reconnected(boolean sameServer) {
        kurentoClientAwareConnectionListener.reconnected(client, sameServer);
    }

    public KurentoClient getClient() {
        return client;
    }

    public void setClient(KurentoClient client) {
        this.client = client;
    }
}
