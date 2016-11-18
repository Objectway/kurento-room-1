/*
 * (C) Copyright 2014 Kurento (http://kurento.org/)
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

package org.kurento.room.internal;

import org.kurento.client.*;
import org.kurento.client.internal.server.KurentoServerException;
import org.kurento.room.api.MutedMediaType;
import org.kurento.room.endpoint.PublisherEndpoint;
import org.kurento.room.endpoint.SdpType;
import org.kurento.room.endpoint.SubscriberEndpoint;
import org.kurento.room.exception.RoomException;
import org.kurento.room.exception.RoomException.Code;
import org.kurento.room.interfaces.IParticipant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * IParticipant (stateful) implementation.
 *
 * @author Ivan Gracia (izanmail@gmail.com)
 * @author Micael Gallego (micael.gallego@gmail.com)
 * @author Radu Tom Vlad (rvlad@naevatec.com)
 * @since 1.0.0
 */
public class Participant implements IParticipant {

    private static final Logger log = LoggerFactory.getLogger(Participant.class);

    private String id;
    private String name;
    private boolean web = false;
    private boolean dataChannels = false;

    private final Room room;
    private final MediaPipeline pipeline;

    // Publisher (streamId)
    private final ConcurrentHashMap<String, PublisherEndpoint> publishers = new ConcurrentHashMap<String, PublisherEndpoint>();
    private final ConcurrentHashMap<String, CountDownLatch> publisherLatches = new ConcurrentHashMap<String, CountDownLatch>();

    // Warning: the mere presence of a stream in publishers does NOT mean that the stream is effectively published
    // However, when the stream is unpublished, the entry is removed both from publishers and publishersStreamingFlags.
    private final ConcurrentHashMap<String, Boolean> publishersStreamingFlags = new ConcurrentHashMap<String, Boolean>();

    // Subscribers
    private final ConcurrentMap<String, SubscriberEndpoint> subscribers = new ConcurrentHashMap<String, SubscriberEndpoint>();
    private final ConcurrentMap<String, Filter> filters = new ConcurrentHashMap<>();

    private volatile boolean closed;

    public Participant(String id, String name, Room room, MediaPipeline pipeline, boolean dataChannels, boolean web) {
        this.id = id;
        this.name = name;
        this.web = web;
        this.dataChannels = dataChannels;
        this.pipeline = pipeline;
        this.room = room;
    }

    @Override
    public void createPublishingEndpoint(final String streamId) {
        final PublisherEndpoint publisherEndpoint = getNewOrExistingPublisher(name, streamId);
        final CountDownLatch publisherLatch = publisherLatches.get(streamId);
        publisherEndpoint.createEndpoint(publisherLatch);

        if (getPublisher(streamId).getEndpoint() == null) {
            throw new RoomException(Code.MEDIA_ENDPOINT_ERROR_CODE, "Unable to create publisher endpoint");
        }
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void shapePublisherMedia(MediaElement element, MediaType type, final String streamId) {
        if (!publishers.contains(streamId)) {
            throw new RoomException(Code.MEDIA_ENDPOINT_ERROR_CODE, "Unable to create publisher endpoint, streamId " + streamId + " not found");
        }
        final PublisherEndpoint publisher = publishers.get(streamId);
        if (type == null) {
            publisher.apply(element);
        } else {
            publisher.apply(element, type);
        }
    }

    @Override
    public synchronized Filter getFilterElement(String id) {
        return filters.get(id);
    }

    @Override
    public synchronized void addFilterElement(String id, Filter filter) {
        filters.put(id, filter);
        // TODO: fix
        //shapePublisherMedia(filter, null);
    }

    @Override
    public synchronized void disableFilterelement(String filterID, boolean releaseElement) {
        Filter filter = getFilterElement(filterID);

        if (filter != null) {
            try {
                // TODO: fix
                // publisher.revert(filter, releaseElement);
            } catch (RoomException e) {
                //Ignore error
            }
        }
    }

    @Override
    public synchronized void enableFilterelement(String filterID) {
        Filter filter = getFilterElement(filterID);

        if (filter != null) {
            try {
                // TODO: fix
                //publisher.apply(filter);
            } catch (RoomException e) {
                // Ignore exception if element is already used
            }
        }
    }

    @Override
    public synchronized void removeFilterElement(String id) {
        Filter filter = getFilterElement(id);

        filters.remove(id);
        if (filter != null) {
            // TODO: fix
            //publisher.revert(filter);
        }
    }

    @Override
    public synchronized void releaseAllFilters() {

        // Check this, mutable array?

        filters.forEach((s, filter) -> removeFilterElement(s));
    }

    @Override
    public PublisherEndpoint getPublisher(final String streamId) {
        final CountDownLatch endPointLatch = publisherLatches.get(streamId);
        try {
            if (!endPointLatch.await(Room.ASYNC_LATCH_TIMEOUT, TimeUnit.SECONDS)) {
                throw new RoomException(
                        Code.MEDIA_ENDPOINT_ERROR_CODE,
                        "Timeout reached while waiting for publisher endpoint to be ready");
            }
        } catch (InterruptedException e) {
            throw new RoomException(
                    Code.MEDIA_ENDPOINT_ERROR_CODE,
                    "Interrupted while waiting for publisher endpoint to be ready: " + e.getMessage());
        }
        return publishers.get(streamId);
    }

    @Override
    public Room getRoom() {
        return this.room;
    }

    @Override
    public MediaPipeline getPipeline() {
        return pipeline;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public boolean isStreaming(final String streamId) {
        final Boolean streaming = publishersStreamingFlags.get(streamId);
        return streaming != null && streaming.booleanValue() == true;
    }

    @Override
    public boolean isAnyStreaming() {
        return this.publishersStreamingFlags.size() > 0;
    }

    @Override
    public boolean isSubscribed() {
        for (SubscriberEndpoint se : subscribers.values()) {
            if (se.isConnectedToPublisher()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Set<String> getConnectedSubscribedEndpoints() {
        Set<String> subscribedToSet = new HashSet<String>();
        for (SubscriberEndpoint se : subscribers.values()) {
            if (se.isConnectedToPublisher()) {
                subscribedToSet.add(se.getEndpointName());
            }
        }
        return subscribedToSet;
    }

    @Override
    public String preparePublishConnection(final String streamId) {
        log.info("USER {}: Request to publish video in room {} by "
                + "initiating connection from server", this.name, this.room.getName());

        String sdpOffer = this.getPublisher(streamId).preparePublishConnection();

        log.trace("USER {}: Publishing SdpOffer is {} for streamId {}", this.name, sdpOffer, streamId);
        log.info("USER {}: Generated Sdp offer for publishing in room {} for streamId {}", this.name,
                this.room.getName(), streamId);
        return sdpOffer;
    }

    @Override
    public String publishToRoom(final String streamId, final String streamType, SdpType sdpType, String sdpString, boolean doLoopback,
                                MediaElement loopbackAlternativeSrc, MediaType loopbackConnectionType) {
        log.info("USER {}: Request to publish video in room {} (sdp type {})", this.name,
                this.room.getName(), sdpType);
        log.trace("USER {}: Publishing Sdp ({}) is {}", this.name, sdpType, sdpString);

        String sdpResponse = this.getPublisher(streamId).publish(sdpType, sdpString, doLoopback,
                loopbackAlternativeSrc, loopbackConnectionType);

        // The publisher is now streaming
        publishersStreamingFlags.put(streamId, true);

        log.trace("USER {}: Publishing Sdp ({}) is {}", this.name, sdpType, sdpResponse);
        log.info("USER {}: Is now publishing video in room {} streamId {} streamType {}", this.name, this.room.getName(), streamId, streamType);

        return sdpResponse;
    }

    @Override
    public void unpublishMedia(final String streamId) {
        log.debug("PARTICIPANT {}: unpublishing media stream from room {}", this.name,
                this.room.getName());
        releasePublisherEndpoint(streamId);

    /*this.publisher = new PublisherEndpoint(web, dataChannels, this, name, pipeline);
    log.debug("PARTICIPANT {}: released publisher endpoint and left it "
        + "initialized (ready for future streaming)", this.name);*/
    }

    @Override
    public String receiveMediaFrom(IParticipant sender, final String streamId, String sdpOffer) {
        final String senderName = sender.getName();

        log.info("USER {}: Request to receive media from {} in room {}", this.name, senderName,
                this.room.getName());
        log.trace("USER {}: SdpOffer for {} is {}", this.name, senderName, sdpOffer);

        if (senderName.equals(this.name)) {
            log.warn("PARTICIPANT {}: trying to configure loopback by subscribing", this.name);
            throw new RoomException(Code.USER_NOT_STREAMING_ERROR_CODE,
                    "Can loopback only when publishing media");
        }

        if (sender.getPublisher(streamId) == null) {
            log.warn("PARTICIPANT {}: Trying to connect to a user without " + "a publishing endpoint",
                    this.name);
            return null;
        }

        log.debug("PARTICIPANT {}: Creating a subscriber endpoint to user {}", this.name, senderName);

        SubscriberEndpoint subscriber = getNewOrExistingSubscriber(senderName, streamId);

        try {
            CountDownLatch subscriberLatch = new CountDownLatch(1);
            SdpEndpoint oldMediaEndpoint = subscriber.createEndpoint(subscriberLatch);
            try {
                if (!subscriberLatch.await(Room.ASYNC_LATCH_TIMEOUT, TimeUnit.SECONDS)) {
                    throw new RoomException(Code.MEDIA_ENDPOINT_ERROR_CODE,
                            "Timeout reached when creating subscriber endpoint");
                }
            } catch (InterruptedException e) {
                throw new RoomException(Code.MEDIA_ENDPOINT_ERROR_CODE,
                        "Interrupted when creating subscriber endpoint: " + e.getMessage());
            }
            if (oldMediaEndpoint != null) {
                log.warn("PARTICIPANT {}: Two threads are trying to create at "
                        + "the same time a subscriber endpoint for user {}", this.name, senderName);
                return null;
            }
            if (subscriber.getEndpoint() == null) {
                throw new RoomException(Code.MEDIA_ENDPOINT_ERROR_CODE,
                        "Unable to create subscriber endpoint");
            }
        } catch (RoomException e) {
            this.subscribers.remove(senderName);
            throw e;
        }

        log.debug("PARTICIPANT {}: Created subscriber endpoint for user {} with streamId {}", this.name, senderName, streamId);
        try {
            String sdpAnswer = subscriber.subscribe(sdpOffer, sender.getPublisher(streamId));
            log.trace("USER {}: Subscribing SdpAnswer is {} with streamId {}", this.name, sdpAnswer, streamId);
            log.info("USER {}: Is now receiving video from {} in room {} with streamId {}", this.name, senderName,
                    this.room.getName(), streamId);
            return sdpAnswer;
        } catch (KurentoServerException e) {
            // TODO Check object status when KurentoClient sets this info in the
            // object
            if (e.getCode() == 40101) {
                log.warn("Publisher endpoint was already released when trying "
                        + "to connect a subscriber endpoint to it", e);
            } else {
                log.error("Exception connecting subscriber endpoint " + "to publisher endpoint", e);
            }
            this.subscribers.remove(senderName);
            releaseSubscriberEndpoint(senderName, subscriber);
        }
        return null;
    }

    @Override
    public void cancelReceivingAllMedias(String senderName) {
        for (String streamId : subscribers.keySet()) {
            cancelReceivingMedia(senderName, streamId);
        }
    }

    @Override
    public void cancelReceivingMedia(String senderName, final String streamId) {
        senderName = senderName + "_" + streamId;
        log.debug("PARTICIPANT {}: cancel receiving media from {}", this.name, senderName);
        SubscriberEndpoint subscriberEndpoint = subscribers.remove(senderName);
        if (subscriberEndpoint == null || subscriberEndpoint.getEndpoint() == null) {
            log.warn("PARTICIPANT {}: Trying to cancel receiving video from user {}. "
                    + "But there is no such subscriber endpoint.", this.name, senderName);
        } else {
            log.debug("PARTICIPANT {}: Cancel subscriber endpoint linked to user {}", this.name,
                    senderName);

            releaseSubscriberEndpoint(senderName, subscriberEndpoint);
        }
    }

    @Override
    public void mutePublishedMedia(MutedMediaType muteType, final String streamId) {
        if (muteType == null) {
            throw new RoomException(Code.MEDIA_MUTE_ERROR_CODE, "Mute type cannot be null");
        }
        this.getPublisher(streamId).mute(muteType);
    }

    @Override
    public void unmutePublishedMedia(final String streamId) {
        if (this.getPublisher(streamId).getMuteType() == null) {
            log.warn("PARTICIPANT {}: Trying to unmute published media. " + "But media is not muted.",
                    this.name);
        } else {
            this.getPublisher(streamId).unmute();
        }
    }

    @Override
    public void muteSubscribedMedia(IParticipant sender, final String streamId, MutedMediaType muteType) {
        if (muteType == null) {
            throw new RoomException(Code.MEDIA_MUTE_ERROR_CODE, "Mute type cannot be null");
        }
        String senderName = sender.getName() + "_" + streamId;
        SubscriberEndpoint subscriberEndpoint = subscribers.get(senderName);
        if (subscriberEndpoint == null || subscriberEndpoint.getEndpoint() == null) {
            log.warn("PARTICIPANT {}: Trying to mute incoming media from user {}. "
                    + "But there is no such subscriber endpoint.", this.name, senderName);
        } else {
            log.debug("PARTICIPANT {}: Mute subscriber endpoint linked to user {}", this.name,
                    senderName);
            subscriberEndpoint.mute(muteType);
        }
    }

    @Override
    public void unmuteSubscribedMedia(IParticipant sender, final String streamId) {
        String senderName = sender.getName() + "_" + streamId;
        SubscriberEndpoint subscriberEndpoint = subscribers.get(senderName);
        if (subscriberEndpoint == null || subscriberEndpoint.getEndpoint() == null) {
            log.warn("PARTICIPANT {}: Trying to unmute incoming media from user {}. "
                    + "But there is no such subscriber endpoint.", this.name, senderName);
        } else {
            if (subscriberEndpoint.getMuteType() == null) {
                log.warn("PARTICIPANT {}: Trying to unmute incoming media from user {}. "
                        + "But media is not muted.", this.name, senderName);
            } else {
                log.debug("PARTICIPANT {}: Unmute subscriber endpoint linked to user {}", this.name,
                        senderName);
                subscriberEndpoint.unmute();
            }
        }
    }

    @Override
    public void close() {
        log.debug("PARTICIPANT {}: Closing user", this.name);
        if (isClosed()) {
            log.warn("PARTICIPANT {}: Already closed", this.name);
            return;
        }
        this.closed = true;
        for (String remoteParticipantName : subscribers.keySet()) {
            SubscriberEndpoint subscriber = this.subscribers.get(remoteParticipantName);
            if (subscriber != null && subscriber.getEndpoint() != null) {
                releaseSubscriberEndpoint(remoteParticipantName, subscriber);
                log.debug("PARTICIPANT {}: Released subscriber endpoint to {}", this.name,
                        remoteParticipantName);
            } else {
                log.warn("PARTICIPANT {}: Trying to close subscriber endpoint to {}. "
                        + "But the endpoint was never instantiated.", this.name, remoteParticipantName);
            }
        }

        for (String streamId : publishers.keySet()) {
            releasePublisherEndpoint(streamId);
        }
    }

    @Override
    public SubscriberEndpoint getNewOrExistingSubscriber(String remoteName, final String streamId) {
        remoteName = remoteName + "_" + streamId;
        SubscriberEndpoint sendingEndpoint = new SubscriberEndpoint(web, this, remoteName, pipeline);
        SubscriberEndpoint existingSendingEndpoint =
                this.subscribers.putIfAbsent(remoteName, sendingEndpoint);
        if (existingSendingEndpoint != null) {
            sendingEndpoint = existingSendingEndpoint;
            log.trace("PARTICIPANT {}: Already exists a subscriber endpoint to user {}", this.name,
                    remoteName);
        } else {
            log.debug("PARTICIPANT {}: New subscriber endpoint to user {}", this.name, remoteName);
        }
        return sendingEndpoint;
    }

    @Override
    public PublisherEndpoint getNewOrExistingPublisher(final String endpointName, final String streamId) {

        PublisherEndpoint publisherEndpoint = new PublisherEndpoint(web, dataChannels, this, endpointName + "_" + streamId, pipeline);
        PublisherEndpoint existingPublisherEndpoint = publishers.putIfAbsent(streamId, publisherEndpoint);

        if (existingPublisherEndpoint != null) {
            publisherEndpoint = existingPublisherEndpoint;
            log.trace("PARTICIPANT {}: Already exists a publish endpoint to user {} with streamId {}", this.name,
                    endpointName, streamId);
        } else {
            log.debug("PARTICIPANT {}: New publisher endpoint to user {} with streamId {}", this.name, endpointName, streamId);

            // The publisher is not streaming yet (only when publishRoom is called)
            publisherLatches.putIfAbsent(streamId, new CountDownLatch(1));
            publishersStreamingFlags.putIfAbsent(streamId, false);

            for (Participant other : (Collection<Participant>)room.getParticipants()) {
                if (!other.getName().equals(this.name)) {
                    for (String otherStreamId : other.publishers.keySet()) {
                        getNewOrExistingSubscriber(other.getName(), otherStreamId);
                    }
                }
            }
        }

        return publisherEndpoint;
    }

    @Override
    public void addIceCandidate(String endpointName, String streamId, IceCandidate iceCandidate) {
        if (this.name.equals(endpointName)) {
            this.getNewOrExistingPublisher(endpointName, streamId).addIceCandidate(iceCandidate);
        } else {
            this.getNewOrExistingSubscriber(endpointName, streamId).addIceCandidate(iceCandidate);
        }
    }

    @Override
    public void sendIceCandidate(String endpointName, final String streamId, IceCandidate candidate) {
        room.sendIceCandidate(id, name, endpointName, streamId, candidate);
    }

    @Override
    public void sendMediaError(ErrorEvent event) {
        String desc =
                event.getType() + ": " + event.getDescription() + "(errCode=" + event.getErrorCode() + ")";
        log.warn("PARTICIPANT {}: Media error encountered: {}", name, desc);
        room.sendMediaError(id, name, desc);
    }

    private void releasePublisherEndpoint(final String streamId) {
        final PublisherEndpoint publisher = publishers.get(streamId);
        if (publisher != null && publisher.getEndpoint() != null) {
            publisher.unregisterErrorListeners();
            for (MediaElement el : publisher.getMediaElements()) {
                releaseElement(name, el);
            }
            releaseElement(name, publisher.getEndpoint());

            publishers.remove(streamId);
            publisherLatches.remove(streamId);
            publishersStreamingFlags.remove(streamId);
        } else {
            log.warn("PARTICIPANT {}: Trying to release publisher endpoint but is null, streamId {}", name, streamId);
        }
    }

    private void releaseSubscriberEndpoint(String senderName, SubscriberEndpoint subscriber) {
        if (subscriber != null) {
            subscriber.unregisterErrorListeners();
            releaseElement(senderName, subscriber.getEndpoint());
        } else {
            log.warn("PARTICIPANT {}: Trying to release subscriber endpoint for '{}' but is null", name,
                    senderName);
        }
    }

    private void releaseElement(final String senderName, final MediaElement element) {
        final String eid = element.getId();
        try {
            element.release(new Continuation<Void>() {
                @Override
                public void onSuccess(Void result) throws Exception {
                    log.debug("PARTICIPANT {}: Released successfully media element #{} for {}",
                            Participant.this.name, eid, senderName);
                }

                @Override
                public void onError(Throwable cause) throws Exception {
                    log.warn("PARTICIPANT {}: Could not release media element #{} for {}",
                            Participant.this.name, eid, senderName, cause);
                }
            });
        } catch (Exception e) {
            log.error("PARTICIPANT {}: Error calling release on elem #{} for {}", name, eid, senderName,
                    e);
        }
    }

    @Override
    public ConcurrentHashMap.KeySetView<String, PublisherEndpoint> getPublisherStreamIds() {
        return publishers.keySet();
    }

    @Override
    public String toString() {
        return "[User: " + name + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (id == null ? 0 : id.hashCode());
        result = prime * result + (name == null ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof Participant)) {
            return false;
        }
        Participant other = (Participant) obj;
        if (id == null) {
            if (other.id != null) {
                return false;
            }
        } else if (!id.equals(other.id)) {
            return false;
        }
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        return true;
    }
}
