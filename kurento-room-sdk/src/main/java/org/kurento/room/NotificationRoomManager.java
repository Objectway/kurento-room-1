/*
 * (C) Copyright 2015 Kurento (http://kurento.org/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kurento.room;

import org.kurento.client.MediaElement;
import org.kurento.client.MediaPipeline;
import org.kurento.client.MediaType;
import org.kurento.room.api.*;
import org.kurento.room.api.pojo.KurentoUserId;
import org.kurento.room.api.pojo.ParticipantRequest;
import org.kurento.room.api.pojo.KurentoRoomId;
import org.kurento.room.api.pojo.UserParticipant;
import org.kurento.room.exception.RoomException;
import org.kurento.room.interfaces.IRoomManager;
import org.kurento.room.internal.DefaultKurentoClientSessionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PreDestroy;
import java.util.Set;

/**
 * The Kurento room manager represents an SDK for any developer that wants to implement the Room
 * server-side application. They can build their application on top of the manager's Java API and
 * implement their desired business logic without having to consider room or media-specific details.
 * <p/>
 * It will trigger events which when handled, should notify the client side with the execution
 * result of the requested actions (for client-originated requests).
 *
 * @author <a href="mailto:rvlad@naevatec.com">Radu Tom Vlad</a>
 */
public class NotificationRoomManager {
    private final Logger log = LoggerFactory.getLogger(NotificationRoomManager.class);

    private NotificationRoomHandler notificationRoomHandler;

    @Autowired
    private IRoomManager internalManager;

    /**
     * Provides an instance of the room manager by setting an event handler.
     *
     * @param notificationRoomHandler the room event handler implementation
     * @param kcProvider              enables the manager to obtain Kurento Client instances
     */
    public NotificationRoomManager(NotificationRoomHandler notificationRoomHandler,
                                   KurentoClientProvider kcProvider) {
        super();
        this.notificationRoomHandler = notificationRoomHandler;
    }

    // ----------------- CLIENT-ORIGINATED REQUESTS ------------

    /**
     * Calls
     * {@link RoomManager#joinRoom(String userName, String roomName, boolean dataChannels, * boolean webParticipant, KurentoClientSessionInfo kcSessionInfo, String participantId)}
     * with a {@link DefaultKurentoClientSessionInfo} bean as implementation of the
     * {@link KurentoClientSessionInfo}.
     *
     * @param userId
     * @param request instance of {@link ParticipantRequest} POJO containing the participant's id
     *                and a
     *                request id (optional identifier of the request at the communications level,
     *                included
     *                when responding back to the client)
     * @see RoomManager#joinRoom(String, String, boolean, boolean, KurentoClientSessionInfo, String)
     */
    public void joinRoom(KurentoUserId userId, String roomName, boolean dataChannels, ParticipantRequest request) {
        Set<UserParticipant> existingParticipants = null;
        try {
            KurentoClientSessionInfo kcSessionInfo =
                    new DefaultKurentoClientSessionInfo(request.getParticipantId(), roomName);
            existingParticipants = internalManager
                    .joinRoom(userId, roomName, dataChannels, kcSessionInfo,
                            request.getParticipantId());
        } catch (RoomException e) {
            log.warn("PARTICIPANT [{},{}]: Error joining/creating room {}", userId.getTenant(), userId.getUsername(), roomName, e);
            notificationRoomHandler.onParticipantJoined(request, roomName, userId.getUsername(), null, e);
        }
        if (existingParticipants != null) {
            notificationRoomHandler
                    .onParticipantJoined(request, roomName, userId.getUsername(), existingParticipants, null);
        }
    }

    /**
     * @param request instance of {@link ParticipantRequest} POJO
     * @see RoomManager#leaveRoom(String)
     */
    public void leaveRoom(ParticipantRequest request) {
        String pid = request.getParticipantId();
        Set<UserParticipant> remainingParticipants = null;
        KurentoRoomId roomId = null;
        String userName = null;
        try {
            roomId = internalManager.getRoomId(pid);
            userName = internalManager.getParticipantName(pid);
            remainingParticipants = internalManager.leaveRoom(pid);
        } catch (RoomException e) {
            log.warn("PARTICIPANT {}: Error leaving room {}", userName, roomId, e);
            notificationRoomHandler.onParticipantLeft(request, null, null, e);
        }
        if (remainingParticipants != null) {
            notificationRoomHandler.onParticipantLeft(request, userName, remainingParticipants, null);
        }
    }

    /**
     * @param request instance of {@link ParticipantRequest} POJO
     * @see RoomManager#publishMedia(String, boolean, String, MediaElement, MediaType, boolean, *)
     */
    public void publishMedia(ParticipantRequest request, final String streamId, final String streamType, boolean isOffer, String sdp,
                             MediaElement loopbackAlternativeSrc, MediaType loopbackConnectionType, boolean doLoopback) throws RoomException {
        String pid = request.getParticipantId();
        String userName = null;
        Set<UserParticipant> participants = null;
        String sdpAnswer = null;
        try {
            userName = internalManager.getParticipantName(pid);
            sdpAnswer = internalManager.publishMedia(request.getParticipantId(), streamId, streamType, isOffer, sdp,
                    loopbackAlternativeSrc, loopbackConnectionType, doLoopback);
            participants = internalManager.getParticipants(internalManager.getRoomId(pid));
        } catch (RoomException e) {
            log.warn("PARTICIPANT {}: Error publishing media", userName, e);
            notificationRoomHandler.onPublishMedia(request, null, null, null, null, null, e);

            // Rethrow the exception
            throw e;
        }
        if (sdpAnswer != null) {
            notificationRoomHandler.onPublishMedia(request, userName, streamId, streamType, sdpAnswer, participants, null);
        }
    }


    /**
     * @param request instance of {@link ParticipantRequest} POJO
     * @see RoomManager#unpublishMedia(String)
     */
    public void unpublishMedia(ParticipantRequest request, final String streamId) {
        String pid = request.getParticipantId();
        String userName = null;
        Set<UserParticipant> participants = null;
        boolean unpublished = false;
        try {
            userName = internalManager.getParticipantName(pid);
            internalManager.unpublishMedia(pid, streamId);
            unpublished = true;
            participants = internalManager.getParticipants(internalManager.getRoomId(pid));
        } catch (RoomException e) {
            log.warn("PARTICIPANT {}: Error unpublishing media", userName, e);
            notificationRoomHandler.onUnpublishMedia(request, null, null, null, e);
        }
        if (unpublished) {
            notificationRoomHandler.onUnpublishMedia(request, userName, streamId, participants, null);
        }
    }

    /**
     * @param request instance of {@link ParticipantRequest} POJO
     * @see RoomManager#subscribe(String, String, String)
     */
    public void subscribe(String remoteName, final String streamId, String sdpOffer, ParticipantRequest request) {
        String pid = request.getParticipantId();
        String userName = null;
        String sdpAnswer = null;
        try {
            userName = internalManager.getParticipantName(pid);
            sdpAnswer = internalManager.subscribe(remoteName, streamId, sdpOffer, pid);
        } catch (RoomException e) {
            log.warn("PARTICIPANT {}: Error subscribing to {}", userName, remoteName, e);
            notificationRoomHandler.onSubscribe(request, null, e);
        }
        if (sdpAnswer != null) {
            notificationRoomHandler.onSubscribe(request, sdpAnswer, null);
        }
    }

    /**
     * @param request instance of {@link ParticipantRequest} POJO
     * @see RoomManager#unsubscribe(String, String)
     */
    public void unsubscribe(String remoteName, final String streamId, ParticipantRequest request) {
        String pid = request.getParticipantId();
        String userName = null;
        boolean unsubscribed = false;
        try {
            userName = internalManager.getParticipantName(pid);
            internalManager.unsubscribe(remoteName, pid, streamId);
            unsubscribed = true;
        } catch (RoomException e) {
            log.warn("PARTICIPANT {}: Error unsubscribing from {} streamId {}", userName, remoteName, streamId, e);
            notificationRoomHandler.onUnsubscribe(request, e);
        }
        if (unsubscribed) {
            notificationRoomHandler.onUnsubscribe(request, null);
        }
    }

    /**
     * @see RoomManager#onIceCandidate(String, String, int, String, String)
     */
    public void onIceCandidate(String endpointName, final String streamId, String candidate, int sdpMLineIndex,
                               String sdpMid, ParticipantRequest request) {
        String pid = request.getParticipantId();
        String userName = null;
        try {
            userName = internalManager.getParticipantName(pid);
            internalManager.onIceCandidate(endpointName, streamId, candidate, sdpMLineIndex, sdpMid,
                    request.getParticipantId());
            notificationRoomHandler.onRecvIceCandidate(request, null);
        } catch (RoomException e) {
            log.warn("PARTICIPANT {}: Error receiving ICE " + "candidate (epName={}, candidate={})",
                    userName, endpointName, candidate, e);
            notificationRoomHandler.onRecvIceCandidate(request, e);
        }
    }

    /**
     * Used by clients to send written messages to all other participants in the room.<br/>
     * <strong>Side effects:</strong> The room event handler should acknowledge the client's request
     * by sending an empty message. Should also send notifications to the all participants in the room
     * with the message and its sender.
     *
     * @param message  message contents
     * @param userName name or identifier of the user in the room
     * @param roomName room's name
     * @param request  instance of {@link ParticipantRequest} POJO
     */
    public void sendMessage(String message, String userName, String roomName, ParticipantRequest request) {
    /*
    log.debug("Request [SEND_MESSAGE] message={} ({})", message, request);
    try {
      if (!internalManager.getParticipantName(request.getParticipantId()).equals(userName)) {
        throw new RoomException(Code.USER_NOT_FOUND_ERROR_CODE,
            "Provided username '" + userName + "' differs from the participant's name");
      }
      if (!internalManager.getRoomName(request.getParticipantId()).equals(roomName)) {
        throw new RoomException(Code.ROOM_NOT_FOUND_ERROR_CODE,
            "Provided room name '" + roomName + "' differs from the participant's room");
      }
      notificationRoomHandler.onSendMessage(request, message, userName, roomName,
          internalManager.getParticipants(roomName), null);
    } catch (RoomException e) {
      log.warn("PARTICIPANT {}: Error sending message", userName, e);
      notificationRoomHandler.onSendMessage(request, null, null, null, null, e);
    }*/

        try {
            notificationRoomHandler.onSendMessage(request, message, userName, roomName, null);
        } catch (RoomException e) {
            log.warn("PARTICIPANT {}: Error sending message", userName, e);
            notificationRoomHandler.onSendMessage(request, null, null, null, e);
        }
    }

    // ----------------- APPLICATION-ORIGINATED REQUESTS ------------

    /**
     * @see RoomManager#close()
     */
    @PreDestroy
    public void close() {
        if (!internalManager.isClosed()) {
            internalManager.close();
        }
    }

    /**
     * @see RoomManager#getRooms()
     */
    public Set<KurentoRoomId> getRooms() {
        return internalManager.getRooms();
    }

    /**
     * @see RoomManager#getParticipants(String)
     */
    public Set<UserParticipant> getParticipants(KurentoRoomId roomId) throws RoomException {
        return internalManager.getParticipants(roomId);
    }

    /**
     * @see RoomManager#getPipeline(String)
     */
    public MediaPipeline getPipeline(String participantId) throws RoomException {
        return internalManager.getPipeline(participantId);
    }

    /**
     * Application-originated request to remove a participant from the room. <br/>
     * <strong>Side effects:</strong> The room event handler should notify the user that she has been
     * evicted. Should also send notifications to all other participants about the one that's just
     * been evicted.
     *
     * @see RoomManager#leaveRoom(String)
     */
    public void evictParticipant(String participantId) throws RoomException {
        UserParticipant participant = internalManager.getParticipantInfo(participantId);
        Set<UserParticipant> remainingParticipants = internalManager.leaveRoom(participantId);
        notificationRoomHandler.onParticipantLeft(participant.getUserName(), remainingParticipants);
        notificationRoomHandler.onParticipantEvicted(participant);
    }

    /**
     * @param roomId
     * @see RoomManager#closeRoom(String)
     */
    public void closeRoom(KurentoRoomId roomId) throws RoomException {
        Set<UserParticipant> participants = internalManager.closeRoom(roomId);
        notificationRoomHandler.onRoomClosed(roomId, participants);
    }

    public IRoomManager getRoomManager() {
        return internalManager;
    }
}
