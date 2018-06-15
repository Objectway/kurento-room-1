package org.kurento.room.interfaces;

import org.kurento.client.*;
import org.kurento.room.endpoint.SdpType;

import java.util.Enumeration;

/**
 * Interface for a Participant
 */
public interface IParticipant {
  void createPublishingEndpoint(String streamId);
  String getId();
  String getName();
  IPublisherEndpoint getPublisher(String streamId);
  IRoom getRoom();
  MediaPipeline getPipeline();
  boolean isClosed();
  boolean isStreaming(String streamId);

  /**
   * Returns TRUE if the participant is streaming at least one stream
   *
   * @return
   */
  boolean isAnyStreaming();

  String publishToRoom(String streamId, String streamType, SdpType sdpType, String sdpString, boolean doLoopback, MediaElement loopbackAlternativeSrc, MediaType loopbackConnectionType);
  void unpublishMedia(String streamId);
  String receiveMediaFrom(IParticipant sender, String streamId, String sdpOffer);
  void cancelReceivingAllMedias(String senderName);
  void cancelReceivingMedia(String senderName, String streamId);
  void close();

  /**
   * Returns a {@link ISubscriberEndpoint} for the given username. The endpoint is created if not
   * found.
   *
   * @param remoteName name of another user
   * @return the endpoint instance
   */
  ISubscriberEndpoint getNewOrExistingSubscriber(String remoteName, String streamId);
  IPublisherEndpoint getNewOrExistingPublisher(String endpointName, String streamId);
  void addIceCandidate(String endpointName, String streamId, IceCandidate iceCandidate);
  void sendIceCandidate(String endpointName, String streamId, IceCandidate candidate);
  void sendMediaError(ErrorEvent event);
  Enumeration<String> getPublisherStreamIds();
}
