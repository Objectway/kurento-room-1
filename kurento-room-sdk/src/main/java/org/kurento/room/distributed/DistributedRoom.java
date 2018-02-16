package org.kurento.room.distributed;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ILock;
import com.hazelcast.core.IMap;
import com.hazelcast.mapreduce.aggregation.Aggregations;
import com.hazelcast.mapreduce.aggregation.Supplier;
import org.kurento.client.*;
import org.kurento.room.api.RoomHandler;
import org.kurento.room.distributed.interfaces.IChangeListener;
import org.kurento.room.distributed.interfaces.IDistributedNamingService;
import org.kurento.room.distributed.model.DistributedRemoteObject;
import org.kurento.room.exception.RoomException;
import org.kurento.room.interfaces.IParticipant;
import org.kurento.room.interfaces.IRoom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Set;

/**
 * Created by sturiale on 02/12/16.
 */
@Component
@Scope("prototype")
public class DistributedRoom implements IRoom, IChangeListener<DistributedParticipant> {
    private final static Logger log = LoggerFactory.getLogger(DistributedRoom.class);

    @Autowired
    private RoomHandler roomHandler;

    @Autowired
    private HazelcastInstance hazelcastInstance;

    @Autowired
    private IDistributedNamingService distributedNamingService;

    @Autowired
    private ApplicationContext context;

    private final String name;
    private KurentoClient kurentoClient;
    private volatile boolean pipelineReleased = false;
    private boolean destroyKurentoClient;
    private MediaPipeline pipeline;
    //    private CountDownLatch pipelineLatch = new CountDownLatch(1);
    private String kmsUri;
    private volatile boolean closed = false;
    private IMap<String, DistributedParticipant> participants;
    private IChangeListener<DistributedRoom> listener;

    private ILock pipelineReleaseLock;
    private ILock pipelineCreateLock;
    private ILock roomLock;

    // Composite and recorder endpoint used for registrations
    private Composite compositeElement = null;
    private HubPort compositeRecorderPort = null;
    private RecorderEndpoint recorderEndpoint = null;

    @PostConstruct
    public void init() {
        participants = hazelcastInstance.getMap(distributedNamingService.getName("participants-" + name));
        pipelineCreateLock = hazelcastInstance.getLock(distributedNamingService.getName("pipelineCreateLock-" + name));
        pipelineReleaseLock = hazelcastInstance.getLock(distributedNamingService.getName("pipelineReleaseLock-" + name));
        roomLock = hazelcastInstance.getLock(distributedNamingService.getName("lock-room-" + name));
    }

    /**
     * Destroys the hazelcast resources.
     */
    public void destroyHazelcastResources() {
        participants.destroy();
        pipelineCreateLock.destroy();
        pipelineReleaseLock.destroy();
        roomLock.destroy();
    }

    public DistributedRoom(String roomName, KurentoClient kurentoClient,
                           boolean destroyKurentoClient) {
        this.name = roomName;
        this.kurentoClient = kurentoClient;
        this.destroyKurentoClient = destroyKurentoClient;
        this.kmsUri = ReflectionUtils.getKmsUri(kurentoClient);
        // log.debug("New DistributedRoom instance, named '{}'", roomName);
    }

    public DistributedRoom(String roomName, KurentoClient kurentoClient,
                           boolean destroyKurentoClient, boolean closed,
                           DistributedRemoteObject pipelineInfo,
                           DistributedRemoteObject compositeInfo,
                           DistributedRemoteObject hubportInfo,
                           DistributedRemoteObject recorderInfo) {
        this(roomName, kurentoClient, destroyKurentoClient);
        this.closed = closed;
        this.pipeline = DistributedRemoteObject.retrieveFromInfo(pipelineInfo, kurentoClient);
        this.compositeElement = DistributedRemoteObject.retrieveFromInfo(compositeInfo, kurentoClient);
        this.compositeRecorderPort = DistributedRemoteObject.retrieveFromInfo(hubportInfo, kurentoClient);
        this.recorderEndpoint = DistributedRemoteObject.retrieveFromInfo(recorderInfo, kurentoClient);
        // log.debug("New DistributedRoom deserialized instance, named '{}'", roomName);
    }

    @Override
    public HubPort allocateHubPort() {
        return new HubPort.Builder(compositeElement).build();
    }

    @Override
    public void startGlobalRecording(final String pathName) {
        // We reuse the pipeline lock
        pipelineCreateLock.lock();

        try {
            if (recorderEndpoint != null) {
                return;
            }

            log.info("ROOM {}: Creating Composite node for recording", name);

            // Create the elements needed for global recording
            compositeElement = new Composite.Builder(pipeline).build();
            compositeRecorderPort = new HubPort.Builder(compositeElement).build();
            recorderEndpoint = new RecorderEndpoint.Builder(pipeline, pathName).stopOnEndOfStream().build();
            compositeRecorderPort.connect(recorderEndpoint);

            // Start the record
            recorderEndpoint.record();
        } catch (Exception e) {
            log.error("Unable to create Composite node for room '{}'", name, e);
        } finally {
            pipelineCreateLock.unlock();
            listener.onChange(this);
        }
    }

    @Override
    public void stopGlobalRecording() {
        // We reuse the pipeline lock
        pipelineCreateLock.lock();

        try {
            if (recorderEndpoint == null) {
                return;
            }

            recorderEndpoint.stop();
        } finally {
            pipelineCreateLock.unlock();
            listener.onChange(this);
        }
    }

    @Override
     public String getName() {
        return name;
    }

    @Override
    public MediaPipeline getPipeline() {
        // The pipeline creation process is not triggered when we do not have
        // a KMS provider, so if we wait on pipelineLatch we would loop indefinitely
        if (kurentoClient == null) {
            return null;
        }

//        try {
//            pipelineLatch.await(DistributedRoom.ASYNC_LATCH_TIMEOUT, TimeUnit.SECONDS);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
        return this.pipeline;
    }

    @Override
    public void join(String participantId, String userName, boolean dataChannels, boolean webParticipant) throws RoomException {
        log.info("KMS: Using kmsUri {} for {}", kmsUri, name);
        roomLock.lock();

        try {
            checkClosed();

            if (userName == null || userName.isEmpty()) {
                throw new RoomException(RoomException.Code.GENERIC_ERROR_CODE, "Empty user name is not allowed");
            }
            for (IParticipant p : participants.values()) {
                if (p.getName().equals(userName)) {
                    throw new RoomException(RoomException.Code.EXISTING_USER_IN_ROOM_ERROR_CODE,
                            "User '" + userName + "' already exists in room '" + name + "'");
                }
            }

            // We create a pipeline only if we have a KMS provider!
            if (kurentoClient != null) {
                createPipeline();
            }
            // Note: The IAtomicLong contained in DistributedParticipant starts at 0 pre default
            participants.set(participantId, (DistributedParticipant) context.getBean("distributedParticipant", participantId, userName, this,
                    dataChannels, webParticipant));
//            participants.put(participantId, new DistributedParticipant(participantId, userName, this,
//                    dataChannels, webParticipant));

            log.info("ROOM {}: Added participant {}", name, userName);
        } finally {
            roomLock.unlock();
        }
    }

    @Override
    public void newPublisher(IParticipant participant, String streamId) {
        registerPublisher(participant.getId());

        // pre-load endpoints to recv video from the new publisher
        for (IParticipant participant1 : participants.values()) {
            if (participant.equals(participant1)) {
                continue;
            }
            participant1.getNewOrExistingSubscriber(participant.getName(), streamId);
        }

        log.debug("ROOM {}: Virtually subscribed other participants {} to new publisher {}", name,
                participants.values(), participant.getName());
    }

    @Override
    public void cancelPublisher(IParticipant participant, String streamId) {
        deregisterPublisher(participant.getId());

        // cancel recv video from this publisher
        for (IParticipant subscriber : participants.values()) {
            if (participant.equals(subscriber)) {
                continue;
            }
            subscriber.cancelReceivingMedia(participant.getName(), streamId);
        }

        log.debug("ROOM {}: Unsubscribed other participants {} from the publisher {}", name,
                participants.values(), participant.getName());
    }

    @Override
    public void leave(String participantId) throws RoomException {
        checkClosed();

        IParticipant participant = participants.get(participantId);
        if (participant == null) {
            throw new RoomException(RoomException.Code.USER_NOT_FOUND_ERROR_CODE,
                    "User #" + participantId + " not found in room '" + name + "'");
        }
//        participant.releaseAllFilters();

        log.info("PARTICIPANT {}: Leaving room {}", participant.getName(), this.name);
        Enumeration<String> publisherStreamIds = participant.getPublisherStreamIds();
        while (publisherStreamIds.hasMoreElements()) {
            String streamId = publisherStreamIds.nextElement();
            if (participant.isStreaming(streamId)) {
                this.deregisterPublisher(participant.getId());
            }
        }

        // We can't invoke methods on partipant after removing it from hazelcast.So we moved removeParticipant after participant.close()
        participant.close();
        this.removeParticipant(participant);
    }

    @Override
    public Collection<? extends IParticipant> getParticipants() {
        checkClosed();

        return participants.values();
    }

    @Override
    public Set<String> getParticipantIds() {
        checkClosed();

        return participants.keySet();
    }

    @Override
    public IParticipant getParticipant(String participantId) {
        checkClosed();

        return participants.get(participantId);
    }

    @Override
    public IParticipant getParticipantByName(String userName) {
        checkClosed();

        for (IParticipant p : participants.values()) {
            if (p.getName().equals(userName)) {
                return p;
            }
        }

        return null;
    }

    @Override
    public void close() {
        if (!closed) {
            for (DistributedParticipant user : participants.values()) {
                user.close();
                user.destroyHazelcastResources();
            }

            participants.clear();

            // The pipeline is created only when we have a suitable KMS provider!
            if (kurentoClient != null) {
                closePipeline();
            }

            log.debug("Room {} closed", this.name);
            if (destroyKurentoClient && kurentoClient != null) {
                kurentoClient.destroy();
            }

            this.closed = true;
        } else {
            log.warn("Closing an already closed room '{}'", this.name);
        }
    }

    @Override
    public void sendIceCandidate(String participantId, String participantName, String endpointName, String streamId, IceCandidate candidate) {
        this.roomHandler.onIceCandidate(name, participantId, participantName, endpointName, streamId, candidate);
    }

    @Override
    public void sendMediaError(String participantId, String participantName, String description) {
        this.roomHandler.onMediaElementError(name, participantId, participantName, description);
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    private void checkClosed() {
        if (closed) {
            throw new RoomException(RoomException.Code.ROOM_CLOSED_ERROR_CODE, "The room '" + name + "' is closed");
        }
    }

    private void removeParticipant(IParticipant participant) {
        checkClosed();

        // Warning: We must call destroyHazelcastResources AFTER we
        // remove the element from the map, otherwise the destroyed resources
        // will be recreated (most likely the .remove() causes a deserialization)
        participants.remove(participant.getId());
        ((DistributedParticipant)participant).destroyHazelcastResources();

        log.debug("ROOM {}: Cancel receiving media from user '{}' for other users", this.name,
                participant.getName());

        for (IParticipant other : participants.values()) {
            other.cancelReceivingAllMedias(participant.getName());
        }
    }

    @Override
    public int getActivePublishers() {
        // For each participant
        final Supplier<String, DistributedParticipant, Boolean> supplier = Supplier.fromPredicate(
                // If it has been registered at least once
                // WARNING: This is DIFFERENT from checking for the ACTUAL number of streams in the corresponding participant!
                entry -> entry.getValue().getRegisterCount().get() > 0
        );

        return participants.aggregate(supplier, Aggregations.count()).intValue();
    }

    @Override
    public void registerPublisher(String participantId) {
        participants.get(participantId).getRegisterCount().incrementAndGet();
    }

    @Override
    public void deregisterPublisher(String participantId) {
        participants.get(participantId).getRegisterCount().decrementAndGet();
    }

    private void createPipeline() {
        pipelineCreateLock.lock();

        try {
            if (pipeline != null) {
                return;
            }

            log.info("ROOM {}: Creating MediaPipeline", name);
            try {
                // This method must not be called when we do not have a KMS provider!
                if (kurentoClient == null) {
                    throw new Exception("Cannot create a media pipeline without a KMS!");
                }

                // Create the pipeline
                pipeline = kurentoClient.createMediaPipeline();

//                kurentoClient.createMediaPipeline(new Continuation<MediaPipeline>() {
//                    @Override
//                    public void onSuccess(MediaPipeline result) throws Exception {
//                        pipeline = result;
//                        pipelineLatch.countDown();
//                        log.debug("ROOM {}: Created MediaPipeline", name);
//                    }
//
//                    @Override
//                    public void onError(Throwable cause) throws Exception {
//                        pipelineLatch.countDown();
//                        log.error("ROOM {}: Failed to create MediaPipeline", name, cause);
//                    }
//                });
            } catch (Exception e) {
                log.error("Unable to create media pipeline for room '{}'", name, e);
//                pipelineLatch.countDown();
            }
            if (getPipeline() == null) {
                throw new RoomException(RoomException.Code.ROOM_CANNOT_BE_CREATED_ERROR_CODE, "Unable to create media pipeline for room '" + name + "'");
            }

            pipeline.addErrorListener(new EventListener<ErrorEvent>() {
                @Override
                public void onEvent(ErrorEvent event) {
                    final String desc = event.getType() + ": " + event.getDescription() + "(errCode=" + event.getErrorCode() + ")";
                    log.warn("ROOM {}: Pipeline error encountered: {}", name, desc);
                    roomHandler.onPipelineError(name, (Collection<IParticipant>) getParticipants(), desc);
                }
            });
        } finally {
            pipelineCreateLock.unlock();
            listener.onChange(this);
        }
    }

    private void closePipeline() {
        pipelineReleaseLock.lock();
        try {
            if (pipeline == null || pipelineReleased) {
                return;
            }
            getPipeline().release();
            pipelineReleased = true;
//            getPipeline().release(new Continuation<Void>() {
//
//                @Override
//                public void onSuccess(Void result) throws Exception {
//                    log.debug("ROOM {}: Released Pipeline", DistributedRoom.this.name);
//                    pipelineReleased = true;
//                }
//
//                @Override
//                public void onError(Throwable cause) throws Exception {
//                    log.warn("ROOM {}: Could not successfully release Pipeline", DistributedRoom.this.name, cause);
//                    pipelineReleased = true;
//                }
//            });
        } catch (Exception e) {
            pipelineReleased = false;
        } finally {
            pipelineReleaseLock.unlock();
            listener.onChange(this);
        }
    }

    @Override
    public void updateFilter(String filterId) {
        throw new NotImplementedException();
    }

    @Override
    public String getKmsUri() {
        return kmsUri;
    }

    public boolean getDestroyKurentoClient() {
        return destroyKurentoClient;
    }

    public void setListener(IChangeListener<DistributedRoom> listener) {
        this.listener = listener;
    }

    @Override
    public void onChange(DistributedParticipant participant) {
        participants.set(participant.getId(), participant);
    }

    @Override
    public Composite getComposite() { return compositeElement; }

    @Override
    public HubPort getHubPort() { return compositeRecorderPort; }

    @Override
    public RecorderEndpoint getRecorderEndpoint() { return recorderEndpoint; }
}
