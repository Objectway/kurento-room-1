package org.kurento.room.distributed;

import org.kurento.client.MediaElement;
import org.kurento.client.MediaPipeline;
import org.kurento.client.MediaType;
import org.kurento.room.RoomManager;
import org.kurento.room.api.KurentoClientProvider;
import org.kurento.room.api.KurentoClientSessionInfo;
import org.kurento.room.api.MutedMediaType;
import org.kurento.room.api.RoomHandler;
import org.kurento.room.api.pojo.UserParticipant;
import org.kurento.room.exception.RoomException;
import org.kurento.room.interfaces.IRoomManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

/**
 * Distributed implementation of IRoomManager using hazelcast.
 */
public class DistributedRoomManager implements IRoomManager {
    private final Logger log = LoggerFactory.getLogger(DistributedRoomManager.class);

    @Autowired
    private RoomHandler roomHandler;

    // Note: This can be null if we don't want to use a KMS!
    @Autowired
    private KurentoClientProvider kcProvider;

    @Override
    public Set<UserParticipant> joinRoom(String userName, String roomName, boolean dataChannels, boolean webParticipant, KurentoClientSessionInfo kcSessionInfo, String participantId) throws RoomException {
        return null;
    }

    @Override
    public Set<UserParticipant> leaveRoom(String participantId) throws RoomException {
        return null;
    }

    @Override
    public String publishMedia(String participantId, String streamId, String streamType, boolean isOffer, String sdp, MediaElement loopbackAlternativeSrc, MediaType loopbackConnectionType, boolean doLoopback, MediaElement... mediaElements) throws RoomException {
        return null;
    }

    @Override
    public String generatePublishOffer(String participantId, String streamId) throws RoomException {
        return null;
    }

    @Override
    public void unpublishMedia(String participantId, String streamId) throws RoomException {

    }

    @Override
    public String subscribe(String remoteName, String streamId, String sdpOffer, String participantId) throws RoomException {
        return null;
    }

    @Override
    public void unsubscribe(String remoteName, String participantId, String streamId) throws RoomException {

    }

    @Override
    public void onIceCandidate(String endpointName, String streamId, String candidate, int sdpMLineIndex, String sdpMid, String participantId) throws RoomException {

    }

    @Override
    public void addMediaElement(String participantId, String streamId, MediaElement element, MediaType type) throws RoomException {

    }

    @Override
    public void removeMediaElement(String participantId, String streamId, MediaElement element) throws RoomException {

    }

    @Override
    public void mutePublishedMedia(MutedMediaType muteType, String participantId, String streamId) throws RoomException {

    }

    @Override
    public void unmutePublishedMedia(String participantId, String streamId) throws RoomException {

    }

    @Override
    public void muteSubscribedMedia(String remoteName, String streamId, MutedMediaType muteType, String participantId) throws RoomException {

    }

    @Override
    public void unmuteSubscribedMedia(String remoteName, String streamId, String participantId) throws RoomException {

    }

    @Override
    public void close() {

    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public Set<String> getRooms() {
        return null;
    }

    @Override
    public Set<UserParticipant> getParticipants(String roomName) throws RoomException {
        return null;
    }

    @Override
    public Set<UserParticipant> getPublishers(String roomName) throws RoomException {
        return null;
    }

    @Override
    public Set<UserParticipant> getSubscribers(String roomName) throws RoomException {
        return null;
    }

    @Override
    public Set<UserParticipant> getPeerPublishers(String participantId) throws RoomException {
        return null;
    }

    @Override
    public Set<UserParticipant> getPeerSubscribers(String participantId) throws RoomException {
        return null;
    }

    @Override
    public boolean isPublisherStreaming(String participantId) throws RoomException {
        return false;
    }

    @Override
    public void createRoom(KurentoClientSessionInfo kcSessionInfo) throws RoomException {

    }

    @Override
    public Set<UserParticipant> closeRoom(String roomName) throws RoomException {
        return null;
    }

    @Override
    public MediaPipeline getPipeline(String participantId) throws RoomException {
        return null;
    }

    @Override
    public String getRoomName(String participantId) throws RoomException {
        return null;
    }

    @Override
    public String getParticipantName(String participantId) throws RoomException {
        return null;
    }

    @Override
    public UserParticipant getParticipantInfo(String participantId) throws RoomException {
        return null;
    }

    @Override
    public void updateFilter(String roomId, String filterId) {

    }
}
