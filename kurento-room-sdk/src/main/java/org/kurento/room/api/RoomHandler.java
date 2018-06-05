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

package org.kurento.room.api;

import org.kurento.client.IceCandidate;
import org.kurento.room.api.pojo.RoomId;
import org.kurento.room.interfaces.IParticipant;

import java.util.Collection;

/**
 * Handler for events triggered from media objects.
 *
 * @author <a href="mailto:rvlad@naevatec.com">Radu Tom Vlad</a>
 */
public interface RoomHandler {

  /**
   * Called when a new {@link IceCandidate} is gathered for the local WebRTC endpoint. The user
   * should receive a notification with all the provided information so that the candidate is added
   * to the remote WebRTC peer.
   *  @param roomId
   *          name of the room
   * @param participantId
   *          identifier of the participant
   * @param participantName
 *          name of the participant
   * @param endpoint
*          String the identifier of the local WebRTC endpoint (created in the server)
   * @param candidate
*          the gathered {@link IceCandidate}
   */
  void onIceCandidate(RoomId roomId, String participantId, String participantName, String endpoint, final String streamId, IceCandidate candidate);

  /**
   * Called as a result of an error intercepted on a media element of a participant. The participant
   * should be notified.
   *  @param roomId
   *          name of the room
   * @param participantId
   *          identifier of the participant
   * @param participantName
 *          name of the participant
   * @param errorDescription
   */
  void onMediaElementError(RoomId roomId, String participantId, String participantName, String errorDescription);

  /**
   * Called as a result of an error intercepted on the media pipeline. The affected participants
   * should be notified.
   *  @param roomId
   *          the room where the error occurred
   * @param participants
   *          the participants
   * @param errorDescription
   */
  void onPipelineError(RoomId roomId, Collection<IParticipant> participants, String errorDescription);
}
