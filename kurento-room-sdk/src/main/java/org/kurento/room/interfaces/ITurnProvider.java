package org.kurento.room.interfaces;

import org.kurento.room.TurnClientCredentials;
import org.kurento.room.TurnKMSCredentials;

/**
 * ITurnProvider is responsible for providing dynamic TURN url and credentials.
 */
public interface ITurnProvider {
    /**
     * Given a username, generates the credentials for the TURN server.
     * These kind of credentials are meant to be consumed by the CCAS clients only.
     * @param username
     * @return
     */
    TurnClientCredentials generateClientCredentials(final String username);

    /**
     * Generates the TURN credentials suitable for the KMS (i.e. no domain names, single stun/turn)
     * These kind of credentials are meant to be consumed by the KMS only.
     * @return
     */
    TurnKMSCredentials generateKMSCredentials();
}
