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

import org.kurento.client.*;
import org.kurento.room.api.KurentoClientProvider;
import org.kurento.room.api.KurentoClientSessionInfo;
import org.kurento.room.api.MutedMediaType;
import org.kurento.room.api.RoomHandler;
import org.kurento.room.api.pojo.UserParticipant;
import org.kurento.room.endpoint.SdpType;
import org.kurento.room.exception.RoomException;
import org.kurento.room.exception.RoomException.Code;
import org.kurento.room.interfaces.IParticipant;
import org.kurento.room.interfaces.IRoomManager;
import org.kurento.room.internal.Participant;
import org.kurento.room.internal.Room;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * IRoomManager stateful implementation.
 *
 * @author <a href="mailto:rvlad@naevatec.com">Radu Tom Vlad</a>
 */
@Component
public class RoomManager implements IRoomManager {
  private final Logger log = LoggerFactory.getLogger(RoomManager.class);

  private RoomHandler roomHandler;

  // Note: This can be null if we don't want to use a KMS!
  private KurentoClientProvider kcProvider;

  private final ConcurrentMap<String, Room> rooms = new ConcurrentHashMap<String, Room>();

  private volatile boolean closed = false;

  /**
   * Provides an instance of the room manager by setting a room handler and the
   * {@link KurentoClient} provider.
   *
   * @param roomHandler the room handler implementation
   * @param kcProvider  enables the manager to obtain Kurento Client instances
   */
  public RoomManager(RoomHandler roomHandler, KurentoClientProvider kcProvider) {
    super();
    this.roomHandler = roomHandler;
    this.kcProvider = kcProvider;
  }

  @Override
  public Set<UserParticipant> joinRoom(String userName, String roomName, boolean dataChannels,
      boolean webParticipant, KurentoClientSessionInfo kcSessionInfo, String participantId)
      throws RoomException {
    log.debug("Request [JOIN_ROOM] user={}, room={}, web={} " + "kcSessionInfo.room={} ({})",
        userName, roomName, webParticipant,
        kcSessionInfo != null ? kcSessionInfo.getRoomName() : null, participantId);
    Room room = rooms.get(roomName);
    if (room == null && kcSessionInfo != null) {
      createRoom(kcSessionInfo);
    }
    room = rooms.get(roomName);
    if (room == null) {
      log.warn("Room '{}' not found");
      throw new RoomException(Code.ROOM_NOT_FOUND_ERROR_CODE,
          "Room '" + roomName + "' was not found, must be created before '" + userName
              + "' can join");
    }
    if (room.isClosed()) {
      log.warn("'{}' is trying to join room '{}' but it is closing", userName, roomName);
      throw new RoomException(Code.ROOM_CLOSED_ERROR_CODE,
          "'" + userName + "' is trying to join room '" + roomName + "' but it is closing");
    }
    Set<UserParticipant> existingParticipants = getParticipants(roomName);
    room.join(participantId, userName, dataChannels, webParticipant);
    return existingParticipants;
  }

  @Override
  public Set<UserParticipant> leaveRoom(String participantId) throws RoomException {
    log.debug("Request [LEAVE_ROOM] ({})", participantId);
    Participant participant = getParticipant(participantId);
    Room room = participant.getRoom();
    String roomName = room.getName();
    if (room.isClosed()) {
      log.warn("'{}' is trying to leave from room '{}' but it is closing", participant.getName(),
          roomName);
      throw new RoomException(Code.ROOM_CLOSED_ERROR_CODE,
          "'" + participant.getName() + "' is trying to leave from room '" + roomName
              + "' but it is closing");
    }
    room.leave(participantId);
    Set<UserParticipant> remainingParticipants = null;
    try {
      remainingParticipants = getParticipants(roomName);
    } catch (RoomException e) {
      log.debug("Possible collision when closing the room '{}' (not found)");
      remainingParticipants = Collections.emptySet();
    }
    if (remainingParticipants.isEmpty()) {
      log.debug("No more participants in room '{}', removing it and closing it", roomName);
      room.close();
      rooms.remove(roomName);
      log.warn("Room '{}' removed and closed", roomName);
    }
    return remainingParticipants;
  }

  @Override
  public String publishMedia(String participantId, final String streamId, final String streamType, boolean isOffer, String sdp,
      MediaElement loopbackAlternativeSrc, MediaType loopbackConnectionType, boolean doLoopback,
      MediaElement... mediaElements) throws RoomException {
    // We can't publish anything without a KMS provider!
    if (kcProvider == null) {
      throw new RoomException(Code.MEDIA_GENERIC_ERROR_CODE, "Cannot publish media without a KMS provider!");
    }

    log.debug("Request [PUBLISH_MEDIA] isOffer={} sdp={} "
            + "loopbackAltSrc={} lpbkConnType={} doLoopback={} mediaElements={} ({})", isOffer, sdp,
        loopbackAlternativeSrc == null, loopbackConnectionType, doLoopback, mediaElements,
        participantId);

    SdpType sdpType = isOffer ? SdpType.OFFER : SdpType.ANSWER;
    Participant participant = getParticipant(participantId);
    String name = participant.getName();
    Room room = participant.getRoom();

    participant.createPublishingEndpoint(streamId);

    for (MediaElement elem : mediaElements) {
      participant.getPublisher(streamId).apply(elem);
    }

    String sdpResponse = participant.publishToRoom(streamId, streamType, sdpType, sdp, doLoopback,
        loopbackAlternativeSrc, loopbackConnectionType);
    if (sdpResponse == null) {
      throw new RoomException(Code.MEDIA_SDP_ERROR_CODE,
          "Error generating SDP response for publishing user " + name);
    }

    room.newPublisher(participant, streamId);
    return sdpResponse;
  }

  public String publishMedia(String participantId, final String streamId, final String streamType, String sdp, boolean doLoopback,
      MediaElement... mediaElements) throws RoomException {
    return publishMedia(participantId, streamId, streamType, true, sdp, null, null, doLoopback, mediaElements);
  }

  public String publishMedia(String participantId, final String streamId, final String streamType, boolean isOffer, String sdp, boolean doLoopback,
      MediaElement... mediaElements) throws RoomException {
    return publishMedia(participantId, streamId, streamType, isOffer, sdp, null, null, doLoopback, mediaElements);
  }

  @Override
  public String generatePublishOffer(String participantId, final String streamId) throws RoomException {
    log.debug("Request [GET_PUBLISH_SDP_OFFER] ({})", participantId);

    Participant participant = getParticipant(participantId);
    String name = participant.getName();
    Room room = participant.getRoom();

    participant.createPublishingEndpoint(streamId);

    String sdpOffer = participant.preparePublishConnection(streamId);
    if (sdpOffer == null) {
      throw new RoomException(Code.MEDIA_SDP_ERROR_CODE,
          "Error generating SDP offer for publishing user " + name);
    }

    room.newPublisher(participant, streamId);
    return sdpOffer;
  }

  @Override
  public void unpublishMedia(String participantId, final String streamId) throws RoomException {
    log.debug("Request [UNPUBLISH_MEDIA] ({})", participantId);
    Participant participant = getParticipant(participantId);

    if (!participant.isStreaming(streamId)) {
      throw new RoomException(Code.USER_NOT_STREAMING_ERROR_CODE, "Participant '"
          + participant.getName() + "' is not streaming media");
    }
    Room room = participant.getRoom();
    participant.unpublishMedia(streamId);
    room.cancelPublisher(participant, streamId);
  }

  @Override
  public String subscribe(String remoteName, final String streamId, String sdpOffer, String participantId)
      throws RoomException {
    log.debug("Request [SUBSCRIBE] remoteParticipant={} sdpOffer={} ({})", remoteName, sdpOffer,
        participantId);
    Participant participant = getParticipant(participantId);
    String name = participant.getName();

    Room room = participant.getRoom();
    Participant senderParticipant = room.getParticipantByName(remoteName);
    log.info("Request subscribe remoteName = {}, streamId = {}, participantId = {}, remoteStreaming = {}", remoteName, streamId, participantId, senderParticipant.isAnyStreaming());
    if (senderParticipant == null) {
      log.warn("PARTICIPANT {}: Requesting to recv media from user {} "
          + "in room {} but user could not be found", name, remoteName, room.getName());
      throw new RoomException(Code.USER_NOT_FOUND_ERROR_CODE,
          "User '" + remoteName + " not found in room '" + room.getName() + "'");
    }
    if (!senderParticipant.isStreaming(streamId)) {
      log.warn("PARTICIPANT {}: Requesting to recv media from user {} "
          + "in room {} but user is not streaming media", name, remoteName, room.getName());
      throw new RoomException(Code.USER_NOT_STREAMING_ERROR_CODE,
          "User '" + remoteName + " not streaming media in room '" + room.getName() + "'");
    }

    String sdpAnswer = participant.receiveMediaFrom(senderParticipant, streamId, sdpOffer);
    if (sdpAnswer == null) {
      throw new RoomException(Code.MEDIA_SDP_ERROR_CODE,
          "Unable to generate SDP answer when subscribing '" + name + "' to '" + remoteName + "'");
    }
    return sdpAnswer;
  }

  @Override
  public void unsubscribe(String remoteName, String participantId, final String streamId) throws RoomException {
    log.debug("Request [UNSUBSCRIBE] remoteParticipant={} ({})", remoteName, participantId);
    Participant participant = getParticipant(participantId);
    String name = participant.getName();
    Room room = participant.getRoom();
    Participant senderParticipant = room.getParticipantByName(remoteName);
    if (senderParticipant == null) {
      log.warn("PARTICIPANT {}: Requesting to unsubscribe from user {} with streamId {} "
          + "in room {} but user could not be found", name, remoteName, streamId, room.getName());
      throw new RoomException(Code.USER_NOT_FOUND_ERROR_CODE, "User " + remoteName
          + " not found in room " + room.getName());
    }
    participant.cancelReceivingMedia(remoteName, streamId);
  }

  @Override
  public void onIceCandidate(String endpointName, final String streamId, String candidate, int sdpMLineIndex,
      String sdpMid, String participantId) throws RoomException {
    log.debug("Request [ICE_CANDIDATE] endpoint={} candidate={} " + "sdpMLineIdx={} sdpMid={} ({})",
        endpointName, candidate, sdpMLineIndex, sdpMid, participantId);
    Participant participant = getParticipant(participantId);
    participant.addIceCandidate(endpointName, streamId, new IceCandidate(candidate, sdpMid, sdpMLineIndex));
  }

  public void addMediaElement(String participantId, final String streamId, MediaElement element) throws RoomException {
    addMediaElement(participantId, streamId, element, null);
  }

  @Override
  public void addMediaElement(String participantId, final String streamId, MediaElement element, MediaType type)
      throws RoomException {
    log.debug("Add media element {} (connection type: {}) to participant {}", element.getId(), type,
        participantId);
    Participant participant = getParticipant(participantId);
    String name = participant.getName();
    if (participant.isClosed()) {
      throw new RoomException(Code.USER_CLOSED_ERROR_CODE,
          "Participant '" + name + "' has been closed");
    }
    participant.shapePublisherMedia(element, type, streamId);
  }

  @Override
  public void removeMediaElement(String participantId, final String streamId, MediaElement element) throws RoomException {
    log.debug("Remove media element {} from participant {}", element.getId(), participantId);
    Participant participant = getParticipant(participantId);
    String name = participant.getName();
    if (participant.isClosed()) {
      throw new RoomException(Code.USER_CLOSED_ERROR_CODE,
          "Participant '" + name + "' has been closed");
    }
    participant.getPublisher(streamId).revert(element);
  }

  @Override
  public void mutePublishedMedia(MutedMediaType muteType, String participantId, final String streamId)
      throws RoomException {
    log.debug("Request [MUTE_PUBLISHED] muteType={} ({})", muteType, participantId);
    Participant participant = getParticipant(participantId);
    String name = participant.getName();
    if (participant.isClosed()) {
      throw new RoomException(Code.USER_CLOSED_ERROR_CODE,
          "Participant '" + name + "' has been closed");
    }
    if (!participant.isStreaming(streamId)) {
      throw new RoomException(Code.USER_NOT_STREAMING_ERROR_CODE, "Participant '" + name
          + "' is not streaming media");
    }
    participant.mutePublishedMedia(muteType, streamId);
  }

  @Override
  public void unmutePublishedMedia(String participantId, final String streamId) throws RoomException {
    log.debug("Request [UNMUTE_PUBLISHED] muteType={} ({})", participantId);
    Participant participant = getParticipant(participantId);
    String name = participant.getName();
    if (participant.isClosed()) {
      throw new RoomException(Code.USER_CLOSED_ERROR_CODE,
          "Participant '" + name + "' has been closed");
    }
    if (!participant.isStreaming(streamId)) {
      throw new RoomException(Code.USER_NOT_STREAMING_ERROR_CODE, "Participant '" + name
          + "' is not streaming media");
    }
    participant.unmutePublishedMedia(streamId);
  }

  @Override
  public void muteSubscribedMedia(String remoteName, final String streamId, MutedMediaType muteType, String participantId)
      throws RoomException {
    remoteName = remoteName + "_" + streamId;
    log.debug("Request [MUTE_SUBSCRIBED] remoteParticipant={} muteType={} ({})", remoteName,
        muteType, participantId);
    Participant participant = getParticipant(participantId);
    String name = participant.getName();
    Room room = participant.getRoom();
    Participant senderParticipant = room.getParticipantByName(remoteName);
    if (senderParticipant == null) {
      log.warn("PARTICIPANT {}: Requesting to mute streaming from {} "
          + "in room {} but user could not be found", name, remoteName, room.getName());
      throw new RoomException(Code.USER_NOT_FOUND_ERROR_CODE,
          "User " + remoteName + " not found in room " + room.getName());
    }

    if (!senderParticipant.isStreaming(streamId)) {
      log.warn("PARTICIPANT {}: Requesting to mute streaming from {} "
          + "in room {} but user is not streaming media", name, remoteName, room.getName());
      throw new RoomException(Code.USER_NOT_STREAMING_ERROR_CODE,
          "User '" + remoteName + " not streaming media in room '" + room.getName() + "'");
    }
    participant.muteSubscribedMedia(senderParticipant, streamId, muteType);
  }

  @Override
  public void unmuteSubscribedMedia(String remoteName, final String streamId, String participantId) throws RoomException {
    remoteName = remoteName + "_" + streamId;
    log.debug("Request [UNMUTE_SUBSCRIBED] remoteParticipant={} ({})", remoteName, participantId);
    Participant participant = getParticipant(participantId);
    String name = participant.getName();
    Room room = participant.getRoom();
    Participant senderParticipant = room.getParticipantByName(remoteName);
    if (senderParticipant == null) {
      log.warn("PARTICIPANT {}: Requesting to unmute streaming from {} "
          + "in room {} but user could not be found", name, remoteName, room.getName());
      throw new RoomException(Code.USER_NOT_FOUND_ERROR_CODE,
          "User " + remoteName + " not found in room " + room.getName());
    }
    if (!senderParticipant.isStreaming(streamId)) {
      log.warn("PARTICIPANT {}: Requesting to unmute streaming from {} "
          + "in room {} but user is not streaming media", name, remoteName, room.getName());
      throw new RoomException(Code.USER_NOT_STREAMING_ERROR_CODE,
          "User '" + remoteName + " not streaming media in room '" + room.getName() + "'");
    }
    participant.unmuteSubscribedMedia(senderParticipant, streamId);
  }

  // ----------------- ADMIN (DIRECT or SERVER-SIDE) REQUESTS ------------

  @Override
  @PreDestroy
  public void close() {
    closed = true;
    log.info("Closing all rooms");
    for (String roomName : rooms.keySet()) {
      try {
        closeRoom(roomName);
      } catch (Exception e) {
        log.warn("Error closing room '{}'", roomName, e);
      }
    }
  }

  @Override
  public boolean isClosed() {
    return closed;
  }

  @Override
  public Set<String> getRooms() {
    return new HashSet<String>(rooms.keySet());
  }

  @Override
  public Set<UserParticipant> getParticipants(String roomName) throws RoomException {
    Room room = rooms.get(roomName);
    if (room == null) {
      throw new RoomException(Code.ROOM_NOT_FOUND_ERROR_CODE, "Room '" + roomName + "' not found");
    }
    Collection<? extends IParticipant> participants = room.getParticipants();
    Set<UserParticipant> userParts = new HashSet<UserParticipant>();
    for (IParticipant p : participants) {
      if (!p.isClosed()) {
        userParts.add(new UserParticipant(p.getId(), p.getName(), p.isAnyStreaming()));
      }
    }
    return userParts;
  }

  @Override
  public Set<UserParticipant> getPublishers(String roomName) throws RoomException {
    Room r = rooms.get(roomName);
    if (r == null) {
      throw new RoomException(Code.ROOM_NOT_FOUND_ERROR_CODE, "Room '" + roomName + "' not found");
    }
    Collection<? extends IParticipant> participants = r.getParticipants();
    Set<UserParticipant> userParts = new HashSet<UserParticipant>();
    for (IParticipant p : participants) {
      if (!p.isClosed() && p.isAnyStreaming()) {
        userParts.add(new UserParticipant(p.getId(), p.getName(), true));
      }
    }
    return userParts;
  }

  @Override
  public Set<UserParticipant> getSubscribers(String roomName) throws RoomException {
    Room r = rooms.get(roomName);
    if (r == null) {
      throw new RoomException(Code.ROOM_NOT_FOUND_ERROR_CODE, "Room '" + roomName + "' not found");
    }
    Collection<? extends IParticipant> participants = r.getParticipants();
    Set<UserParticipant> userParts = new HashSet<UserParticipant>();
    for (IParticipant p : participants) {
      if (!p.isClosed() && p.isSubscribed()) {
        userParts.add(new UserParticipant(p.getId(), p.getName(), p.isAnyStreaming()));
      }
    }
    return userParts;
  }

  @Override
  public Set<UserParticipant> getPeerPublishers(String participantId) throws RoomException {
    Participant participant = getParticipant(participantId);
    if (participant == null) {
      throw new RoomException(Code.USER_NOT_FOUND_ERROR_CODE,
          "No participant with id '" + participantId + "' was found");
    }
    Set<String> subscribedEndpoints = participant.getConnectedSubscribedEndpoints();
    Room room = participant.getRoom();
    Set<UserParticipant> userParts = new HashSet<UserParticipant>();
    for (String epName : subscribedEndpoints) {
      Participant p = room.getParticipantByName(epName);
      userParts.add(new UserParticipant(p.getId(), p.getName()));
    }
    return userParts;
  }

  @Override
  public Set<UserParticipant> getPeerSubscribers(String participantId) throws RoomException {
    Participant participant = getParticipant(participantId);
    if (participant == null) {
      throw new RoomException(Code.USER_NOT_FOUND_ERROR_CODE,
          "No participant with id '" + participantId + "' was found");
    }
    if (!participant.isAnyStreaming()) {
      throw new RoomException(Code.USER_NOT_STREAMING_ERROR_CODE, "Participant with id '"
          + participantId + "' is not a publisher yet");
    }
    Set<UserParticipant> userParts = new HashSet<UserParticipant>();
    Room room = participant.getRoom();
    String endpointName = participant.getName();
    for (IParticipant p : room.getParticipants()) {
      if (p.equals(participant)) {
        continue;
      }
      Set<String> subscribedEndpoints = p.getConnectedSubscribedEndpoints();
      if (subscribedEndpoints.contains(endpointName)) {
        userParts.add(new UserParticipant(p.getId(), p.getName()));
      }
    }
    return userParts;
  }

  @Override
  public boolean isPublisherStreaming(String participantId) throws RoomException {
    Participant participant = getParticipant(participantId);
    if (participant == null) {
      throw new RoomException(Code.USER_NOT_FOUND_ERROR_CODE,
          "No participant with id '" + participantId + "' was found");
    }
    if (participant.isClosed()) {
      throw new RoomException(Code.USER_CLOSED_ERROR_CODE,
          "Participant '" + participant.getName() + "' has been closed");
    }
    return participant.isAnyStreaming();
  }

  @Override
  public void createRoom(KurentoClientSessionInfo kcSessionInfo) throws RoomException {
    String roomName = kcSessionInfo.getRoomName();
    Room room = rooms.get(kcSessionInfo);
    if (room != null) {
      throw new RoomException(Code.ROOM_CANNOT_BE_CREATED_ERROR_CODE,
          "Room '" + roomName + "' already exists");
    }

    // We may not have a kcProvider object!
    KurentoClient kurentoClient = kcProvider != null ? kcProvider.getKurentoClient(kcSessionInfo) : null;
    room = new Room(roomName, kurentoClient, roomHandler, kcProvider != null ? kcProvider.destroyWhenUnused() : true);

    Room oldRoom = rooms.putIfAbsent(roomName, room);
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
    log.warn("No room '{}' exists yet. Created one " + "using KurentoClient '{}'.", roomName,
        kcName);
  }

  @Override
  public Set<UserParticipant> closeRoom(String roomName) throws RoomException {
    Room room = rooms.get(roomName);
    if (room == null) {
      throw new RoomException(Code.ROOM_NOT_FOUND_ERROR_CODE, "Room '" + roomName + "' not found");
    }
    if (room.isClosed()) {
      throw new RoomException(Code.ROOM_CLOSED_ERROR_CODE,
          "Room '" + roomName + "' already closed");
    }
    Set<UserParticipant> participants = getParticipants(roomName);
    // copy the ids as they will be removed from the map
    Set<String> pids = new HashSet<String>(room.getParticipantIds());
    for (String pid : pids) {
      try {
        room.leave(pid);
      } catch (RoomException e) {
        log.warn("Error evicting participant with id '{}' from room '{}'", pid, roomName, e);
      }
    }
    room.close();
    rooms.remove(roomName);
    log.warn("Room '{}' removed and closed", roomName);
    return participants;
  }

  @Override
  public MediaPipeline getPipeline(String participantId) throws RoomException {
    Participant participant = getParticipant(participantId);
    if (participant == null) {
      throw new RoomException(Code.USER_NOT_FOUND_ERROR_CODE,
          "No participant with id '" + participantId + "' was found");
    }
    return participant.getPipeline();
  }

  @Override
  public String getRoomName(String participantId) throws RoomException {
    Participant participant = getParticipant(participantId);
    return participant.getRoom().getName();
  }

  @Override
  public String getParticipantName(String participantId) throws RoomException {
    Participant participant = getParticipant(participantId);
    return participant.getName();
  }

  @Override
  public UserParticipant getParticipantInfo(String participantId) throws RoomException {
    Participant participant = getParticipant(participantId);
    return new UserParticipant(participantId, participant.getName());
  }

  // ------------------ HELPERS ------------------------------------------

  private Participant getParticipant(String pid) throws RoomException {
    for (Room r : rooms.values()) {
      if (!r.isClosed()) {
        if (r.getParticipantIds().contains(pid) && r.getParticipant(pid) != null) {
          return r.getParticipant(pid);
        }
      }
    }
    throw new RoomException(Code.USER_NOT_FOUND_ERROR_CODE,
        "No participant with id '" + pid + "' was found");
  }

  @Override
  public void updateFilter(String roomId, String filterId) {
    Room room = rooms.get(roomId);

    room.updateFilter(filterId);
  }
}
