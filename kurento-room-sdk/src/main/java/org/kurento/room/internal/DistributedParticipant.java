package org.kurento.room.internal;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IAtomicLong;
import com.hazelcast.core.IMap;
import org.kurento.client.*;
import org.kurento.client.internal.server.KurentoServerException;
import org.kurento.room.api.MutedMediaType;
import org.kurento.room.distributed.DistributedNamingService;
import org.kurento.room.distributed.interfaces.IChangeListener;
import org.kurento.room.endpoint.DistributedMediaEndpoint;
import org.kurento.room.endpoint.DistributedPublisherEndpoint;
import org.kurento.room.endpoint.DistributedSubscriberEndpoint;
import org.kurento.room.endpoint.SdpType;
import org.kurento.room.exception.RoomException;
import org.kurento.room.interfaces.IParticipant;
import org.kurento.room.interfaces.IPublisherEndpoint;
import org.kurento.room.interfaces.IRoom;
import org.kurento.room.interfaces.ISubscriberEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;

import static org.kurento.room.endpoint.DistributedMediaEndpoint.SEPARATOR;

/**
 * Created by sturiale on 05/12/16.
 */
@Component
@Scope("prototype")
public class DistributedParticipant implements IParticipant, IChangeListener<DistributedMediaEndpoint> {
    private static final Logger log = LoggerFactory.getLogger(DistributedParticipant.class);

    @Autowired
    private HazelcastInstance hazelcastInstance;

    @Autowired
    private DistributedNamingService distributedNamingService;

    @Autowired
    private ApplicationContext context;

    private String id;
    private String name;
    private boolean web = false;
    private boolean dataChannels = false;
    private final DistributedRoom room;
    private volatile boolean closed;
    private IAtomicLong registerCount;
    private IMap<String, DistributedPublisherEndpoint> publishers;
    private IMap<String, DistributedSubscriberEndpoint> subscribers;

    // Listeners
    private IChangeListener<DistributedParticipant> listener;

    //    private IMap<String, ICountDownLatch> publisherLatches;
    private IMap<String, Boolean> publishersStreamingFlags;

    @PostConstruct
    public void init() {
        publishers = hazelcastInstance.getMap(distributedNamingService.getName("participant-publishers-" + name + "-" + room.getTenant() + SEPARATOR + room.getName()));
        subscribers = hazelcastInstance.getMap(distributedNamingService.getName("participant-subscribers-" + name + "-" + room.getTenant() + SEPARATOR + room.getName()));
        registerCount = hazelcastInstance.getAtomicLong(distributedNamingService.getName("participant-register-count-" + name + "-" + room.getTenant() + SEPARATOR + room.getName()));
//        publisherLatches = hazelcastInstance.getMap(distributedNamingService.getName("publisherLatches-" + name + "-" + room.getName()));
        publishersStreamingFlags = hazelcastInstance.getMap(distributedNamingService.getName("publishersStreamingFlags-" + name + "-" + room.getTenant() + SEPARATOR + room.getName()));
    }

    /**
     * Destroys the hazelcast resources.
     */
    public void destroyHazelcastResources() {
        publishers.destroy();
        subscribers.destroy();
        registerCount.destroy();
        publishersStreamingFlags.destroy();
    }

    public DistributedParticipant(String id, String name, DistributedRoom room, boolean dataChannels, boolean web) {
        this.id = id;
        this.name = name;
        this.web = web;
        this.dataChannels = dataChannels;
        this.room = room;
        setListener(room);
    }

    @Override
    public void createPublishingEndpoint(String streamId) {
        final IPublisherEndpoint publisherEndpoint = getNewOrExistingPublisher(name, streamId);
//        final ICountDownLatch publisherLatch = publisherLatches.get(streamId);
        publisherEndpoint.createEndpoint(/*new CountDownLatchHz(publisherLatch)*/);
        if (publisherEndpoint.getEndpoint() == null) {
            throw new RoomException(RoomException.Code.MEDIA_ENDPOINT_ERROR_CODE, "Unable to create publisher endpoint");
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
    public IPublisherEndpoint getPublisher(String streamId) {
//        final ICountDownLatch endPointLatch = publisherLatches.get(streamId);
//        try {
//            if (!endPointLatch.await(DistributedRoom.ASYNC_LATCH_TIMEOUT, TimeUnit.SECONDS)) {
//                throw new RoomException(
//                        RoomException.Code.MEDIA_ENDPOINT_ERROR_CODE,
//                        "Timeout reached while waiting for publisher endpoint to be ready");
//            }
//        } catch (InterruptedException e) {
//            throw new RoomException(
//                    RoomException.Code.MEDIA_ENDPOINT_ERROR_CODE,
//                    "Interrupted while waiting for publisher endpoint to be ready: " + e.getMessage());
//        }
        return publishers.get(streamId);
    }

    @Override
    public IRoom getRoom() {
        return this.room;
    }

    @Override
    public MediaPipeline getPipeline() {
        return this.room.getPipeline();
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public boolean isStreaming(String streamId) {
        final Boolean streaming = publishersStreamingFlags.get(streamId);
        return streaming != null && streaming.booleanValue() == true;
    }

    @Override
    public boolean isAnyStreaming() {
        return this.publishersStreamingFlags.size() > 0;
    }

    @Override
    public boolean isSubscribed() {
        for (DistributedSubscriberEndpoint se : subscribers.values()) {
            if (se.isConnectedToPublisher()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Set<String> getConnectedSubscribedEndpoints() {
        Set<String> subscribedToSet = new HashSet<String>();
        for (DistributedSubscriberEndpoint se : subscribers.values()) {
            if (se.isConnectedToPublisher()) {
                subscribedToSet.add(se.getEndpointName());
            }
        }
        return subscribedToSet;
    }

    @Override
    public String preparePublishConnection(String streamId) {
        log.info("USER {}: Request to publish video in room {} by "
                + "initiating connection from server", this.name, this.room.getId());

        String sdpOffer = this.getPublisher(streamId).preparePublishConnection();

        log.trace("USER {}: Publishing SdpOffer is {} for streamId {}", this.name, sdpOffer, streamId);
        log.info("USER {}: Generated Sdp offer for publishing in room {} for streamId {}", this.name,
                this.room.getId(), streamId);
        return sdpOffer;
    }

    @Override
    public String publishToRoom(String streamId, String streamType, SdpType sdpType, String sdpString, boolean doLoopback, MediaElement loopbackAlternativeSrc, MediaType loopbackConnectionType) {
        log.info("USER {}: Request to publish video in room {} (sdp type {})", this.name,
                this.room.getId(), sdpType);
        log.trace("USER {}: Publishing Sdp ({}) is {}", this.name, sdpType, sdpString);

        String sdpResponse = this.getPublisher(streamId).publish(sdpType, sdpString, doLoopback,
                loopbackAlternativeSrc, loopbackConnectionType);

        // The publisher is now streaming
        publishersStreamingFlags.set(streamId, true);

        log.trace("USER {}: Publishing Sdp ({}) is {}", this.name, sdpType, sdpResponse);
        log.info("USER {}: Is now publishing video in room {} streamId {} streamType {}", this.name, this.room.getId(), streamId, streamType);

        return sdpResponse;
    }

    @Override
    public void unpublishMedia(String streamId) {
        log.debug("PARTICIPANT {}: unpublishing media stream from room {}", this.name,
                this.room.getId());
        releasePublisherEndpoint(streamId);
    }

    @Override
    public String receiveMediaFrom(IParticipant sender, String streamId, String sdpOffer) {
        final String senderName = sender.getName();

        log.info("USER {}: Request to receive media from {} in room {}", this.name, senderName,
                this.room.getId());
        log.trace("USER {}: SdpOffer for {} is {}", this.name, senderName, sdpOffer);

        if (senderName.equals(this.name)) {
            log.warn("PARTICIPANT {}: trying to configure loopback by subscribing", this.name);
            throw new RoomException(RoomException.Code.USER_NOT_STREAMING_ERROR_CODE,
                    "Can loopback only when publishing media");
        }

        if (sender.getPublisher(streamId) == null) {
            log.warn("PARTICIPANT {}: Trying to connect to a user without " + "a publishing endpoint",
                    this.name);
            return null;
        }

        log.debug("PARTICIPANT {}: Creating a subscriber endpoint to user {}", this.name, senderName);

        DistributedSubscriberEndpoint subscriber = (DistributedSubscriberEndpoint) getNewOrExistingSubscriber(senderName, streamId);

        try {
            //CountDownLatch subscriberLatch = new CountDownLatch(1);
            SdpEndpoint oldMediaEndpoint = subscriber.createEndpoint(); // new CountDownLatchJava(subscriberLatch));
            //
            //            try {
            //                if (!subscriberLatch.await(DistributedRoom.ASYNC_LATCH_TIMEOUT, TimeUnit.SECONDS)) {
            //                    throw new RoomException(RoomException.Code.MEDIA_ENDPOINT_ERROR_CODE,
            //                            "Timeout reached when creating subscriber endpoint");
            //                }
            //            } catch (InterruptedException e) {
            //                throw new RoomException(RoomException.Code.MEDIA_ENDPOINT_ERROR_CODE,
            //                        "Interrupted when creating subscriber endpoint: " + e.getMessage());
            //            }
            if (oldMediaEndpoint != null) {
                log.warn("PARTICIPANT {}: Two threads are trying to create at "
                        + "the same time a subscriber endpoint for user {}", this.name, senderName);
                return null;
            }
            if (subscriber.getEndpoint() == null) {
                throw new RoomException(RoomException.Code.MEDIA_ENDPOINT_ERROR_CODE,
                        "Unable to create subscriber endpoint");
            }
        } catch (RoomException e) {
            // Warning: We must call destroyHazelcastResources AFTER we
            // remove the element from the map, otherwise the destroyed resources
            // will be recreated (most likely the .remove() causes a deserialization)
            subscribers.remove(senderName);
            subscriber.destroyHazelcastResources();
            throw e;
        }

        log.debug("PARTICIPANT {}: Created subscriber endpoint for user {} with streamId {}", this.name, senderName, streamId);
        try {
            String sdpAnswer = subscriber.subscribe(sdpOffer, sender.getPublisher(streamId));
            log.trace("USER {}: Subscribing SdpAnswer is {} with streamId {}", this.name, sdpAnswer, streamId);
            log.info("USER {}: Is now receiving video from {} in room {} with streamId {}", this.name, senderName,
                    this.room.getId(), streamId);
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

            // Warning: We must call destroyHazelcastResources AFTER we
            // remove the element from the map, otherwise the destroyed resources
            // will be recreated (most likely the .remove() causes a deserialization)
            subscribers.remove(senderName);
            subscriber.destroyHazelcastResources();
            releaseSubscriberEndpoint(senderName, subscriber);
        }
        return null;
    }

    @Override
    public void cancelReceivingAllMedias(String senderName) {
        for (String compositeName : subscribers.keySet()) {
            cancelReceivingMedia(senderName, DistributedMediaEndpoint.stripSenderName(compositeName));
        }
    }

    @Override
    public void cancelReceivingMedia(String senderName, String streamId) {
        log.debug("PARTICIPANT {}: cancel receiving media from {}", this.name, senderName);
        DistributedSubscriberEndpoint subscriberEndpoint = subscribers.remove(DistributedMediaEndpoint.toEndpointName(getRoom().getTenant(), senderName, streamId));
        if (subscriberEndpoint == null || subscriberEndpoint.getEndpoint() == null) {
            log.warn("PARTICIPANT {}: Trying to cancel receiving video from user {}. "
                    + "But there is no such subscriber endpoint.", this.name, senderName);
        } else {
            log.debug("PARTICIPANT {}: Cancel subscriber endpoint linked to user {}", this.name,
                    senderName);

            releaseSubscriberEndpoint(senderName, subscriberEndpoint);
            subscriberEndpoint.destroyHazelcastResources();
        }
    }

    @Override
    public void mutePublishedMedia(MutedMediaType muteType, String streamId) {
        if (muteType == null) {
            throw new RoomException(RoomException.Code.MEDIA_MUTE_ERROR_CODE, "Mute type cannot be null");
        }
        this.getPublisher(streamId).mute(muteType);
    }

    @Override
    public void unmutePublishedMedia(String streamId) {
        if (this.getPublisher(streamId).getMuteType() == null) {
            log.warn("PARTICIPANT {}: Trying to unmute published media. " + "But media is not muted.",
                    this.name);
        } else {
            this.getPublisher(streamId).unmute();
        }
    }

    @Override
    public void muteSubscribedMedia(IParticipant sender, String streamId, MutedMediaType muteType) {
        if (muteType == null) {
            throw new RoomException(RoomException.Code.MEDIA_MUTE_ERROR_CODE, "Mute type cannot be null");
        }
        String senderName = DistributedMediaEndpoint.toEndpointName(sender.getRoom().getTenant(), sender.getName(), streamId);
        DistributedSubscriberEndpoint subscriberEndpoint = subscribers.get(senderName);
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
    public void unmuteSubscribedMedia(IParticipant sender, String streamId) {
        String senderName = DistributedMediaEndpoint.toEndpointName(sender.getRoom().getTenant(), sender.getName(), streamId);
        DistributedSubscriberEndpoint subscriberEndpoint = subscribers.get(senderName);
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
//        try {
        for (String compositeName : subscribers.keySet()) {
            final String remoteParticipantName = DistributedMediaEndpoint.stripStreamId(compositeName);
            DistributedSubscriberEndpoint subscriber = subscribers.get(compositeName);
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
  /*      } catch (Exception e) {
            log.error(e.toString());
            e.printStackTrace();
        }*/
    }

    @Override
    public ISubscriberEndpoint getNewOrExistingSubscriber(String remoteName, String streamId) {

//        DistributedSubscriberEndpoint sendingEndpoint = new DistributedSubscriberEndpoint(web, this, remoteName, this.room.getPipeline(), this.room.getKmsUri());

        String endpointName = room.getTenant() + SEPARATOR + remoteName + SEPARATOR + streamId;

        DistributedSubscriberEndpoint sendingEndpoint = (DistributedSubscriberEndpoint) context.getBean("distributedSubscriberEndpoint", web, this, endpointName, this.room.getPipeline(), this.room.getKmsUri(), streamId);
        DistributedSubscriberEndpoint existingSendingEndpoint =
                this.subscribers.putIfAbsent(endpointName, sendingEndpoint);

//        log.debug("Subscribers {}: new key {}", this.name, remoteName);

        if (existingSendingEndpoint != null) {
            sendingEndpoint = existingSendingEndpoint;
            log.trace("PARTICIPANT {}: Already exists a subscriber endpoint to user {}", this.name,
                    endpointName);
        } else {
            log.debug("PARTICIPANT {}: New subscriber endpoint to user {}", this.name, endpointName);
        }

        return sendingEndpoint;
    }

    @Override
    public IPublisherEndpoint getNewOrExistingPublisher(String originalEndpointName, String streamId) {
        String endpointName = room.getTenant() + SEPARATOR + originalEndpointName + SEPARATOR + streamId;

        DistributedPublisherEndpoint publisherEndpoint = (DistributedPublisherEndpoint) context
                .getBean("distributedPublisherEndpoint", web, dataChannels, this, endpointName, room.getPipeline(), room.getKmsUri(), streamId);

        DistributedPublisherEndpoint existingPublisherEndpoint = publishers.putIfAbsent(streamId, publisherEndpoint);

        if (existingPublisherEndpoint != null) {
            publisherEndpoint = existingPublisherEndpoint;
            log.trace("PARTICIPANT {}: Already exists a publish endpoint to user {} with streamId {}", this.name,
                    endpointName, streamId);
        } else {
            log.debug("PARTICIPANT {}: New publisher endpoint to user {} with streamId {}", this.name, endpointName, streamId);

            // The publisher is not streaming yet (only when publishRoom is called)
//            CountDownLatch c = new CountDownLatch(1);
//            ICountDownLatch countDownLatch = hazelcastInstance.getCountDownLatch(distributedNamingService.getName("publisherLatch" + streamId + "-" + name + "-" + room.getName()));
//            publisherLatches.putIfAbsent(streamId, countDownLatch);
            publishersStreamingFlags.putIfAbsent(streamId, false);

            for (DistributedParticipant other : (Collection<DistributedParticipant>) room.getParticipants()) {
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
    public void sendIceCandidate(String endpointName, String streamId, IceCandidate candidate) {
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
        final DistributedPublisherEndpoint publisher = publishers.get(streamId);
        if (publisher != null && publisher.getEndpoint() != null) {
            publisher.unregisterErrorListeners();
//            for (MediaElement el : publisher.getMediaElements()) {
//                releaseElement(name, el);
//            }
            releaseElement(name, publisher.getEndpoint());

            // Warning: We must call destroyHazelcastResources AFTER we
            // remove the element from the map, otherwise the destroyed resources
            // will be recreated (most likely the .remove() causes a deserialization)
            publishers.remove(streamId);
            publisher.destroyHazelcastResources();
//            publisherLatches.remove(streamId);
            publishersStreamingFlags.remove(streamId);
        } else {
            log.warn("PARTICIPANT {}: Trying to release publisher endpoint but is null, streamId {}", name, streamId);
        }
    }

    private void releaseSubscriberEndpoint(String senderName, ISubscriberEndpoint subscriber) {
        if (subscriber != null) {
            subscriber.unregisterErrorListeners();
            releaseElement(senderName, subscriber.getEndpoint());
            senderName = DistributedMediaEndpoint.toEndpointName(getRoom().getTenant(), senderName, ((DistributedSubscriberEndpoint) subscriber).getStreamId());

            // Warning: We must call destroyHazelcastResources AFTER we
            // remove the element from the map, otherwise the destroyed resources
            // will be recreated (most likely the .remove() causes a deserialization)
            subscribers.remove(senderName);
            ((DistributedSubscriberEndpoint) subscriber).destroyHazelcastResources();
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
                            DistributedParticipant.this.name, eid, senderName);
                }

                @Override
                public void onError(Throwable cause) throws Exception {
                    log.warn("PARTICIPANT {}: Could not release media element #{} for {}",
                            DistributedParticipant.this.name, eid, senderName, cause);
                }
            });
        } catch (Exception e) {
            log.error("PARTICIPANT {}: Error calling release on elem #{} for {}", name, eid, senderName,
                    e);
        }
    }

    @Override
    public Enumeration<String> getPublisherStreamIds() {
        return Collections.enumeration(publishers.keySet());
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
        if (!(obj instanceof IParticipant)) {
            return false;
        }
        DistributedParticipant other = (DistributedParticipant) obj;
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

    public boolean isWeb() {
        return web;
    }

    public void setWeb(boolean web) {
        this.web = web;
    }

    public boolean isDataChannels() {
        return dataChannels;
    }

    public void setDataChannels(boolean dataChannels) {
        this.dataChannels = dataChannels;
    }

    public void setListener(IChangeListener<DistributedParticipant> listener) {
        this.listener = listener;
    }


    public IAtomicLong getRegisterCount() {
        return registerCount;
    }

    @Override
    public void onChange(DistributedMediaEndpoint endpoint) {
        if (endpoint instanceof DistributedPublisherEndpoint) {
            DistributedPublisherEndpoint publisherEndpoint = (DistributedPublisherEndpoint) endpoint;
            publishers.set(endpoint.getStreamId(), publisherEndpoint);
        } else if (endpoint instanceof DistributedSubscriberEndpoint) {
            DistributedSubscriberEndpoint subscriberEndpoint = (DistributedSubscriberEndpoint) endpoint;
            subscribers.set(endpoint.getEndpointName(), subscriberEndpoint);
        }
    }
}
