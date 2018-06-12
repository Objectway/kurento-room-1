package org.kurento.room;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import org.kurento.client.KurentoClient;
import org.kurento.client.MediaElement;
import org.kurento.client.MediaPipeline;
import org.kurento.client.MediaType;
import org.kurento.room.api.KurentoClientProvider;
import org.kurento.room.api.KurentoClientSessionInfo;
import org.kurento.room.api.MutedMediaType;
import org.kurento.room.api.pojo.KurentoUserId;
import org.kurento.room.api.pojo.KurentoRoomId;
import org.kurento.room.api.pojo.UserParticipant;
import org.kurento.room.distributed.interfaces.IChangeListener;
import org.kurento.room.distributed.interfaces.IDistributedNamingService;
import org.kurento.room.distributed.model.DistributedIceCandidate;
import org.kurento.room.endpoint.DistributedMediaEndpoint;
import org.kurento.room.endpoint.SdpType;
import org.kurento.room.exception.RoomException;
import org.kurento.room.interfaces.IParticipant;
import org.kurento.room.interfaces.IRoom;
import org.kurento.room.interfaces.IRoomManager;
import org.kurento.room.internal.DistributedParticipant;
import org.kurento.room.internal.DistributedRoom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Distributed implementation of IRoomManager using hazelcast.
 */
@Component
public class DistributedRoomManager implements IRoomManager, IChangeListener<DistributedRoom> {
    private final Logger log = LoggerFactory.getLogger(DistributedRoomManager.class);

    // Note: This can be null if we don't want to use a KMS!
    @Autowired(required = false)
    private KurentoClientProvider kcProvider;

    @Autowired
    private HazelcastInstance hazelcastInstance;

    @Autowired
    private IDistributedNamingService distributedNamingService;

    @Autowired
    private ApplicationContext context;

    private IMap<KurentoRoomId, DistributedRoom> rooms;

    @PostConstruct
    public void init() {
        rooms = hazelcastInstance.getMap(distributedNamingService.getName("rooms"));
    }

    /**
     * Destroys the hazelcast resources.
     */
    public void destroyHazelcastResources() {
        rooms.destroy();
    }

    @Override
    public Set<UserParticipant> joinRoom(KurentoUserId userId, String roomName, boolean dataChannels, KurentoClientSessionInfo kcSessionInfo, String participantId) throws RoomException {
        log.debug("Request [JOIN_ROOM] userId={}, room={}, " + "kcSessionInfo.room={} ({})",
                userId, roomName,
                kcSessionInfo != null ? kcSessionInfo.getRoomName() : null, participantId);
        KurentoRoomId roomId = new KurentoRoomId(userId.getTenant(), roomName);
        IRoom room = rooms.get(roomId);
        if (room == null && kcSessionInfo != null) {
            this.createRoom(kcSessionInfo, roomId);
            room = rooms.get(roomId);
        }

        if (room == null) {
            log.warn("Room '{}' not found");
            throw new RoomException(RoomException.Code.ROOM_NOT_FOUND_ERROR_CODE,
                    "Room '" + roomId + "' was not found, must be created before '" + userId
                            + "' can join");
        }
        if (room.isClosed()) {
            log.warn("'{}' is trying to join room '{}' but it is closing", userId, roomId);
            throw new RoomException(RoomException.Code.ROOM_CLOSED_ERROR_CODE,
                    "'" + userId + "' is trying to join room '" + roomId + "' but it is closing");
        }

        Set<UserParticipant> existingParticipants = this.getParticipants(roomId);
        room.join(participantId, userId.getUsername(), dataChannels);
        return existingParticipants;
    }

    @Override
    public Set<UserParticipant> leaveRoom(String participantId) throws RoomException {
        log.debug("Request [LEAVE_ROOM] ({})", participantId);
        DistributedParticipant participant = getParticipant(participantId);
        IRoom room = participant.getRoom();
        KurentoRoomId roomId = room.getId();
        if (room.isClosed()) {
            log.warn("'{}' is trying to leave from room '{}' but it is closing", participant.getName(), roomId);
            throw new RoomException(RoomException.Code.ROOM_CLOSED_ERROR_CODE,
                    "'" + participant.getName() + "' is trying to leave from room '" + roomId
                            + "' but it is closing");
        }
        room.leave(participantId);
        Set<UserParticipant> remainingParticipants;
        try {
            remainingParticipants = this.getParticipants(roomId);
        } catch (RoomException e) {
            log.debug("Possible collision when closing the room '{}' (not found)");
            remainingParticipants = Collections.emptySet();
        }
        if (remainingParticipants.isEmpty()) {
            log.debug("No more participants in room '{}', removing it and closing it", roomId);
            room.close();

            // Warning: We must call destroyHazelcastResources AFTER we
            // remove the element from the map, otherwise the destroyed resources
            // will be recreated (most likely the .remove() causes a deserialization)
            rooms.remove(roomId);
            ((DistributedRoom)room).destroyHazelcastResources();
            log.warn("Room '{}' removed and closed", roomId);
        }
        return remainingParticipants;
    }

    @Override
    public String publishMedia(String participantId, String streamId, String streamType, boolean isOffer, String sdp, MediaElement loopbackAlternativeSrc, MediaType loopbackConnectionType, boolean doLoopback) throws RoomException {
        if (kcProvider == null) {
            throw new RoomException(RoomException.Code.MEDIA_GENERIC_ERROR_CODE, "Cannot publish media without a KMS provider!");
        }

        log.debug("Request [PUBLISH_MEDIA] isOffer={} sdp={} "
                        + "loopbackAltSrc={} lpbkConnType={} doLoopback={} ({})", isOffer, sdp,
                loopbackAlternativeSrc == null, loopbackConnectionType, doLoopback,
                participantId);

        SdpType sdpType = isOffer ? SdpType.OFFER : SdpType.ANSWER;
        IParticipant participant = getParticipant(participantId);
        String name = participant.getName();
        IRoom room = participant.getRoom();

        participant.createPublishingEndpoint(streamId);

        String sdpResponse = participant.publishToRoom(streamId, streamType, sdpType, sdp, doLoopback,
                loopbackAlternativeSrc, loopbackConnectionType);
        if (sdpResponse == null) {
            throw new RoomException(RoomException.Code.MEDIA_SDP_ERROR_CODE,
                    "Error generating SDP response for publishing user " + name);
        }

        room.newPublisher(participant, streamId);
        return sdpResponse;
    }

    public String publishMedia(String participantId, final String streamId, final String streamType, String sdp, boolean doLoopback) throws RoomException {
        return publishMedia(participantId, streamId, streamType, true, sdp, null, null, doLoopback);
    }

    public String publishMedia(String participantId, final String streamId, final String streamType, boolean isOffer, String sdp, boolean doLoopback) throws RoomException {
        return publishMedia(participantId, streamId, streamType, isOffer, sdp, null, null, doLoopback);
    }

    @Override
    public String generatePublishOffer(String participantId, String streamId) throws RoomException {
        log.debug("Request [GET_PUBLISH_SDP_OFFER] ({})", participantId);

        IParticipant participant = getParticipant(participantId);
        String name = participant.getName();
        IRoom room = participant.getRoom();

        participant.createPublishingEndpoint(streamId);

        String sdpOffer = participant.preparePublishConnection(streamId);
        if (sdpOffer == null) {
            throw new RoomException(RoomException.Code.MEDIA_SDP_ERROR_CODE,
                    "Error generating SDP offer for publishing user " + name);
        }

        room.newPublisher(participant, streamId);
        return sdpOffer;
    }

    @Override
    public void unpublishMedia(String participantId, String streamId) throws RoomException {
        log.debug("Request [UNPUBLISH_MEDIA] ({})", participantId);
        IParticipant participant = getParticipant(participantId);

        if (!participant.isStreaming(streamId)) {
            throw new RoomException(RoomException.Code.USER_NOT_STREAMING_ERROR_CODE, "Participant '"
                    + participant.getName() + "' is not streaming media");
        }
        IRoom room = participant.getRoom();
        participant.unpublishMedia(streamId);
        room.cancelPublisher(participant, streamId);
    }

    @Override
    public String subscribe(String remoteName, String streamId, String sdpOffer, String participantId) throws RoomException {
        log.debug("Request [SUBSCRIBE] remoteParticipant={} sdpOffer={} ({})", remoteName, sdpOffer,
                participantId);
        IParticipant participant = getParticipant(participantId);
        String name = participant.getName();

        IRoom room = participant.getRoom();
        IParticipant senderParticipant = room.getParticipantByName(remoteName);
        boolean isAnyStreaming;

        if (senderParticipant != null) {
            isAnyStreaming = senderParticipant.isAnyStreaming();
            log.info("Request subscribe remoteName = {}, streamId = {}, participantId = {}, remoteStreaming = {}", remoteName, streamId, participantId, isAnyStreaming);
        } else {
            log.warn("PARTICIPANT {}: Requesting to recv media from user {} "
                    + "in room {} but user could not be found", name, remoteName, room.getId());
            throw new RoomException(RoomException.Code.USER_NOT_FOUND_ERROR_CODE,
                    "User '" + remoteName + " not found in room '" + room.getId() + "'");
        }
        if (!senderParticipant.isStreaming(streamId)) {
            log.warn("PARTICIPANT {}: Requesting to recv media from user {} "
                    + "in room {} but user is not streaming media", name, remoteName, room.getId());
            throw new RoomException(RoomException.Code.USER_NOT_STREAMING_ERROR_CODE,
                    "User '" + remoteName + " not streaming media in room '" + room.getId() + "'");
        }

        String sdpAnswer = participant.receiveMediaFrom(senderParticipant, streamId, sdpOffer);
        if (sdpAnswer == null) {
            throw new RoomException(RoomException.Code.MEDIA_SDP_ERROR_CODE,
                    "Unable to generate SDP answer when subscribing '" + name + "' to '" + remoteName + "'");
        }
        return sdpAnswer;
    }

    @Override
    public void unsubscribe(String remoteName, String participantId, String streamId) throws RoomException {
        log.debug("Request [UNSUBSCRIBE] remoteParticipant={} ({})", remoteName, participantId);
        IParticipant participant = getParticipant(participantId);
        String name = participant.getName();
        IRoom room = participant.getRoom();
        IParticipant senderParticipant = room.getParticipantByName(remoteName);
        if (senderParticipant == null) {
            log.warn("PARTICIPANT {}: Requesting to unsubscribe from user {} with streamId {} "
                    + "in room {} but user could not be found", name, remoteName, streamId, room.getId());
            throw new RoomException(RoomException.Code.USER_NOT_FOUND_ERROR_CODE, "User " + remoteName
                    + " not found in room " + room.getId());
        }
        participant.cancelReceivingMedia(remoteName, streamId);
    }

    @Override
    public void onIceCandidate(String endpointName, String streamId, String candidate, int sdpMLineIndex, String sdpMid, String participantId) throws RoomException {
        log.debug("Request [ICE_CANDIDATE] endpoint={} candidate={} " + "sdpMLineIdx={} sdpMid={} ({})",
                endpointName, candidate, sdpMLineIndex, sdpMid, participantId);
        IParticipant participant = getParticipant(participantId);
        participant.addIceCandidate(endpointName, streamId, new DistributedIceCandidate(candidate, sdpMid, sdpMLineIndex));
    }

    @Override
    public void mutePublishedMedia(MutedMediaType muteType, String participantId, String streamId) throws RoomException {
        log.debug("Request [MUTE_PUBLISHED] muteType={} ({})", muteType, participantId);
        IParticipant participant = getParticipant(participantId);
        String name = participant.getName();
        if (participant.isClosed()) {
            throw new RoomException(RoomException.Code.USER_CLOSED_ERROR_CODE,
                    "Participant '" + name + "' has been closed");
        }
        if (!participant.isStreaming(streamId)) {
            throw new RoomException(RoomException.Code.USER_NOT_STREAMING_ERROR_CODE, "Participant '" + name
                    + "' is not streaming media");
        }
        participant.mutePublishedMedia(muteType, streamId);
    }

    @Override
    public void unmutePublishedMedia(String participantId, String streamId) throws RoomException {
        log.debug("Request [UNMUTE_PUBLISHED] muteType={} ({})", participantId);
        IParticipant participant = getParticipant(participantId);
        String name = participant.getName();
        if (participant.isClosed()) {
            throw new RoomException(RoomException.Code.USER_CLOSED_ERROR_CODE,
                    "Participant '" + name + "' has been closed");
        }
        if (!participant.isStreaming(streamId)) {
            throw new RoomException(RoomException.Code.USER_NOT_STREAMING_ERROR_CODE, "Participant '" + name
                    + "' is not streaming media");
        }
        participant.unmutePublishedMedia(streamId);
    }

    @Override
    public void muteSubscribedMedia(String remoteName, String streamId, MutedMediaType muteType, String participantId) throws RoomException {
        IParticipant participant = getParticipant(participantId);
        String name = participant.getName();
        IRoom room = participant.getRoom();

        remoteName = DistributedMediaEndpoint.toEndpointName(room.getTenant(), remoteName, streamId);
        log.debug("Request [MUTE_SUBSCRIBED] remoteParticipant={} muteType={} ({})", remoteName,
                muteType, participantId);

        IParticipant senderParticipant = room.getParticipantByName(remoteName);
        if (senderParticipant == null) {
            log.warn("PARTICIPANT {}: Requesting to mute streaming from {} "
                    + "in room {} but user could not be found", name, remoteName, room.getId());
            throw new RoomException(RoomException.Code.USER_NOT_FOUND_ERROR_CODE,
                    "User " + remoteName + " not found in room " + room.getId());
        }

        if (!senderParticipant.isStreaming(streamId)) {
            log.warn("PARTICIPANT {}: Requesting to mute streaming from {} "
                    + "in room {} but user is not streaming media", name, remoteName, room.getId());
            throw new RoomException(RoomException.Code.USER_NOT_STREAMING_ERROR_CODE,
                    "User '" + remoteName + " not streaming media in room '" + room.getId() + "'");
        }
        participant.muteSubscribedMedia(senderParticipant, streamId, muteType);
    }

    @Override
    public void unmuteSubscribedMedia(String remoteName, String streamId, String participantId) throws RoomException {
        IParticipant participant = getParticipant(participantId);
        String name = participant.getName();
        IRoom room = participant.getRoom();
        remoteName = DistributedMediaEndpoint.toEndpointName(room.getTenant(), remoteName, streamId);
        log.debug("Request [UNMUTE_SUBSCRIBED] remoteParticipant={} ({})", remoteName, participantId);

        IParticipant senderParticipant = room.getParticipantByName(remoteName);
        if (senderParticipant == null) {
            log.warn("PARTICIPANT {}: Requesting to unmute streaming from {} "
                    + "in room {} but user could not be found", name, remoteName, room.getId());
            throw new RoomException(RoomException.Code.USER_NOT_FOUND_ERROR_CODE,
                    "User " + remoteName + " not found in room " + room.getId());
        }
        if (!senderParticipant.isStreaming(streamId)) {
            log.warn("PARTICIPANT {}: Requesting to unmute streaming from {} "
                    + "in room {} but user is not streaming media", name, remoteName, room.getId());
            throw new RoomException(RoomException.Code.USER_NOT_STREAMING_ERROR_CODE,
                    "User '" + remoteName + " not streaming media in room '" + room.getId() + "'");
        }
        participant.unmuteSubscribedMedia(senderParticipant, streamId);
    }

    @Override
    @PreDestroy
    public void close() {
//        closed = true;
//        log.info("Closing all rooms");
//        for (String roomName : rooms.keySet()) {
//            try {
//                closeRoom(roomName);
//            } catch (Exception e) {
//                log.warn("Error closing room '{}'", roomName, e);
//            }
//        }
    }

    @Override
    public boolean isClosed() {
//        return closed;
        return false;
    }

    @Override
    public HashSet<KurentoRoomId> getRooms() {
        return new HashSet<>(rooms.keySet());
    }

    @Override
    public Set<UserParticipant> getParticipants(KurentoRoomId roomId) throws RoomException {
        IRoom room = rooms.get(roomId);
        if (room == null) {
            throw new RoomException(RoomException.Code.ROOM_NOT_FOUND_ERROR_CODE, "Room '" + roomId + "' not found");
        }
        Collection<? extends IParticipant> participants = room.getParticipants();
        Set<UserParticipant> userParts = new HashSet<UserParticipant>();
        for (IParticipant p : participants) {
            if (!p.isClosed()) {
                userParts.add(new UserParticipant(p.getId(), p.getName(), p.getRoom().getTenant(), p.isAnyStreaming()));
            }
        }
        return userParts;
    }

    @Override
    public Set<UserParticipant> getPublishers(KurentoRoomId roomId) throws RoomException {
        IRoom r = rooms.get(roomId);
        if (r == null) {
            throw new RoomException(RoomException.Code.ROOM_NOT_FOUND_ERROR_CODE, "Room '" + roomId + "' not found");
        }
        Collection<? extends IParticipant> participants = r.getParticipants();
        Set<UserParticipant> userParts = new HashSet<UserParticipant>();
        for (IParticipant p : participants) {
            if (!p.isClosed() && p.isAnyStreaming()) {
                userParts.add(new UserParticipant(p.getId(), p.getName(), p.getRoom().getTenant(), true));
            }
        }
        return userParts;
    }

    @Override
    public Set<UserParticipant> getSubscribers(KurentoRoomId roomId) throws RoomException {
        IRoom r = rooms.get(roomId);
        if (r == null) {
            throw new RoomException(RoomException.Code.ROOM_NOT_FOUND_ERROR_CODE, "Room '" + roomId + "' not found");
        }
        Collection<? extends IParticipant> participants = r.getParticipants();
        Set<UserParticipant> userParts = new HashSet<UserParticipant>();
        for (IParticipant p : participants) {
            if (!p.isClosed() && p.isSubscribed()) {
                userParts.add(new UserParticipant(p.getId(), p.getName(), p.getRoom().getTenant(), p.isAnyStreaming()));
            }
        }
        return userParts;
    }

    @Override
    public Set<UserParticipant> getPeerPublishers(String participantId) throws RoomException {
        IParticipant participant = getParticipant(participantId);
        if (participant == null) {
            throw new RoomException(RoomException.Code.USER_NOT_FOUND_ERROR_CODE,
                    "No participant with id '" + participantId + "' was found");
        }
        Set<String> subscribedEndpoints = participant.getConnectedSubscribedEndpoints();
        IRoom room = participant.getRoom();
        Set<UserParticipant> userParts = new HashSet<UserParticipant>();
        for (String epName : subscribedEndpoints) {
            IParticipant p = room.getParticipantByName(epName);
            userParts.add(new UserParticipant(p.getId(), p.getName(), room.getTenant()));
        }
        return userParts;
    }

    @Override
    public Set<UserParticipant> getPeerSubscribers(String participantId) throws RoomException {
        IParticipant participant = getParticipant(participantId);
        if (participant == null) {
            throw new RoomException(RoomException.Code.USER_NOT_FOUND_ERROR_CODE,
                    "No participant with id '" + participantId + "' was found");
        }
        if (!participant.isAnyStreaming()) {
            throw new RoomException(RoomException.Code.USER_NOT_STREAMING_ERROR_CODE, "Participant with id '"
                    + participantId + "' is not a publisher yet");
        }
        Set<UserParticipant> userParts = new HashSet<UserParticipant>();
        IRoom room = participant.getRoom();
        String endpointName = participant.getName();
        for (IParticipant p : room.getParticipants()) {
            if (p.equals(participant)) {
                continue;
            }
            Set<String> subscribedEndpoints = p.getConnectedSubscribedEndpoints();
            if (subscribedEndpoints.contains(endpointName)) {
                userParts.add(new UserParticipant(p.getId(), p.getName(), room.getTenant()));
            }
        }
        return userParts;
    }

    @Override
    public boolean isPublisherStreaming(String participantId) throws RoomException {
        IParticipant participant = getParticipant(participantId);
        if (participant == null) {
            throw new RoomException(RoomException.Code.USER_NOT_FOUND_ERROR_CODE,
                    "No participant with id '" + participantId + "' was found");
        }
        if (participant.isClosed()) {
            throw new RoomException(RoomException.Code.USER_CLOSED_ERROR_CODE,
                    "Participant '" + participant.getName() + "' has been closed");
        }
        return participant.isAnyStreaming();
    }

    private void createRoom(KurentoClientSessionInfo kcSessionInfo, KurentoRoomId roomId) throws RoomException {
        String roomName = kcSessionInfo.getRoomName();
        // TODO: This check does not make sense. Ask on Kurento mailing list.
//        DistributedRoom room = rooms.get(kcSessionInfo);
//        if (room != null) {
//            throw new RoomException(RoomException.Code.ROOM_CANNOT_BE_CREATED_ERROR_CODE,
//                    "Room '" + roomName + "' already exists");
//        }

        // We may not have a kcProvider object!
        KurentoClient kurentoClient = kcProvider != null ? kcProvider.getKurentoClient(kcSessionInfo) : null;
        DistributedRoom room = (DistributedRoom) context.getBean("distributedRoom", roomId, kurentoClient, kcProvider != null ? kcProvider.destroyWhenUnused() : true);
        room.setListener(this);
        DistributedRoom oldRoom = rooms.putIfAbsent(roomId, room);

        if (oldRoom != null) {
            log.warn("Room '{}' has just been created by another thread", roomName);
            return;
            // throw new RoomException(
            // Code.ROOM_CANNOT_BE_CREATED_ERROR_CODE,
            // "Room '"
            // + roomName
            // + "' already exists (has just been created by another thread)");
        }
        String kcName = "[NAME NOT AVAILABLE]";
        if (kurentoClient != null && kurentoClient.getServerManager() != null) {
            kcName = kurentoClient.getServerManager().getName();
        }
        log.warn("No room '{}' exists yet. Created one " + "using KurentoClient '{}'.", roomName, kcName);
    }

    @Override
    public Set<UserParticipant> closeRoom(KurentoRoomId roomId) throws RoomException {
        DistributedRoom room = rooms.get(roomId);
        if (room == null) {
            throw new RoomException(RoomException.Code.ROOM_NOT_FOUND_ERROR_CODE, "Room '" + roomId + "' not found");
        }
        if (room.isClosed()) {
            throw new RoomException(RoomException.Code.ROOM_CLOSED_ERROR_CODE,
                    "Room '" + roomId + "' already closed");
        }
        Set<UserParticipant> participants = this.getParticipants(roomId);
        // copy the ids as they will be removed from the map
        Set<String> pids = new HashSet<String>(room.getParticipantIds());
        for (String pid : pids) {
            try {
                room.leave(pid);
            } catch (RoomException e) {
                log.warn("Error evicting participant with id '{}' from room '{}'", pid, roomId, e);
            }
        }

        room.close();

        // Warning: We must call destroyHazelcastResources AFTER we
        // remove the element from the map, otherwise the destroyed resources
        // will be recreated (most likely the .remove() causes a deserialization)
        rooms.remove(roomId);
        room.destroyHazelcastResources();

        log.warn("Room '{}' removed and closed", roomId);
        return participants;
    }

    @Override
    public MediaPipeline getPipeline(String participantId) throws RoomException {
        IParticipant participant = getParticipant(participantId);
        if (participant == null) {
            throw new RoomException(RoomException.Code.USER_NOT_FOUND_ERROR_CODE,
                    "No participant with id '" + participantId + "' was found");
        }
        return participant.getPipeline();
    }

    @Override
    public KurentoRoomId getRoomId(String participantId) throws RoomException {
        IParticipant participant = getParticipant(participantId);
        return participant.getRoom().getId();
    }

    @Override
    public String getParticipantName(String participantId) throws RoomException {
        IParticipant participant = getParticipant(participantId);
        return participant.getName();
    }

    @Override
    public UserParticipant getParticipantInfo(String participantId) throws RoomException {
        IParticipant participant = getParticipant(participantId);
        return new UserParticipant(participantId, participant.getName(), participant.getRoom().getTenant());
    }


    @Override
    public void onChange(DistributedRoom room) {
        rooms.set(room.getId(), room);
    }

    // ------------------ HELPERS ------------------------------------------
    public DistributedParticipant getParticipant(String pid) throws RoomException {
        for (DistributedRoom r : rooms.values()) {
            if (!r.isClosed()) {
                if (r.getParticipantIds().contains(pid) && r.getParticipant(pid) != null) {
                    return (DistributedParticipant) r.getParticipant(pid);
                }
            }
        }
        throw new RoomException(RoomException.Code.USER_NOT_FOUND_ERROR_CODE, "No participant with id '" + pid + "' was found");
    }

    @Override
    public IRoom getRoomById(KurentoRoomId roomId) {
        return rooms.get(roomId);
    }
}
