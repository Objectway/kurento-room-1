package org.kurento.room.interfaces;

import org.kurento.client.IceCandidate;
import org.kurento.client.MediaPipeline;
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

    String getName();
    MediaPipeline getPipeline();
    void join(String participantId, String userName, boolean dataChannels, boolean webParticipant) throws RoomException;
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
    void updateFilter(String filterId);
}
