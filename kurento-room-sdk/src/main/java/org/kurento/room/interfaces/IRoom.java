package org.kurento.room.interfaces;

import org.kurento.client.*;
import org.kurento.room.api.pojo.KurentoRoomId;
import org.kurento.room.exception.RoomException;

import java.util.Collection;
import java.util.Set;

/**
 * Interface for a Room
 */
public interface IRoom {
    /**
     * Returns the URI of the KMS assigned to the room
     * @return
     */
    String getKmsUri();

    /**
     * Allocates a HubPort (used for composite recording)
     * @return
     */
    HubPort allocateHubPort();

    /**
     * Begins the global recording, if not already started
     * @param pathName
     */
    void startGlobalRecording(final String pathName);

    /**
     * Stops the global recording.
     * Note: don't call startGlobalRecording once stopGlobalRecording has been called.
     */
    void stopGlobalRecording();

    Composite getComposite();
    HubPort getHubPort();
    RecorderEndpoint getRecorderEndpoint();

    String getName();
    MediaPipeline getPipeline();
    void join(String participantId, String userName, boolean dataChannels) throws RoomException;
    void newPublisher(IParticipant participant, String streamId);
    void cancelPublisher(IParticipant participant, String streamId);
    void leave(String participantId) throws RoomException;
    Collection<? extends IParticipant> getParticipants();
    Set<String> getParticipantIds();
    IParticipant getParticipant(String participantId);
    IParticipant getParticipantByName(String userName);
    void close();
    void sendIceCandidate(String participantId, String participantName, String endpointName, String streamId, IceCandidate candidate);
    void sendMediaError(String participantId, String participantName, String description);
    boolean isClosed();
    int getActivePublishers();
    void registerPublisher(String participantId);
    void deregisterPublisher(String participantId);

    KurentoRoomId getId();

    String getTenant();
}
