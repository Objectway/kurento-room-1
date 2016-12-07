package org.kurento.room.interfaces;

import org.kurento.client.*;
import org.kurento.room.api.MutedMediaType;
import org.kurento.room.endpoint.PublisherEndpoint;
import org.kurento.room.endpoint.SdpType;
import org.kurento.room.endpoint.SubscriberEndpoint;

import java.util.Enumeration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interface for a Participant
 */
public interface IParticipant {
  void createPublishingEndpoint(String streamId);
  String getId();
  String getName();
  void shapePublisherMedia(MediaElement element, MediaType type, String streamId);
  Filter getFilterElement(String id);
  void addFilterElement(String id, Filter filter);
  void disableFilterelement(String filterID, boolean releaseElement);
  void enableFilterelement(String filterID);
  void removeFilterElement(String id);
  void releaseAllFilters();
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

  boolean isSubscribed();
  Set<String> getConnectedSubscribedEndpoints();
  String preparePublishConnection(String streamId);
  String publishToRoom(String streamId, String streamType, SdpType sdpType, String sdpString, boolean doLoopback, MediaElement loopbackAlternativeSrc, MediaType loopbackConnectionType);
  void unpublishMedia(String streamId);
  String receiveMediaFrom(IParticipant sender, String streamId, String sdpOffer);
  void cancelReceivingAllMedias(String senderName);
  void cancelReceivingMedia(String senderName, String streamId);
  void mutePublishedMedia(MutedMediaType muteType, String streamId);
  void unmutePublishedMedia(String streamId);
  void muteSubscribedMedia(IParticipant sender, String streamId, MutedMediaType muteType);
  void unmuteSubscribedMedia(IParticipant sender, String streamId);
  void close();

  /**
   * Returns a {@link SubscriberEndpoint} for the given username. The endpoint is created if not
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
