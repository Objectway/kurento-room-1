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
import org.kurento.room.api.RoomHandler;
import org.kurento.room.exception.RoomException;
import org.kurento.room.exception.RoomException.Code;
import org.kurento.room.interfaces.IParticipant;
import org.kurento.room.interfaces.IRoom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Enumeration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IRoom (stateful) implementation.
 *
 * @author Ivan Gracia (izanmail@gmail.com)
 * @author Micael Gallego (micael.gallego@gmail.com)
 * @author Radu Tom Vlad (rvlad@naevatec.com)
 * @since 1.0.0
 */
public class Room implements IRoom {
    public static final int ASYNC_LATCH_TIMEOUT = 30;

    private final static Logger log = LoggerFactory.getLogger(Room.class);

    private final ConcurrentMap<String, Participant> participants = new ConcurrentHashMap<String, Participant>();
    private final String name;

    private MediaPipeline pipeline;
    private CountDownLatch pipelineLatch = new CountDownLatch(1);

    // kurentoClient may be null if we do not want to use a KMS!
    private KurentoClient kurentoClient;

    private RoomHandler roomHandler;

    private volatile boolean closed = false;

    // ParticipantId -> Number of times he's been registered
    private ConcurrentHashMap<String, AtomicInteger> activePublishersRegisterCount = new ConcurrentHashMap<String, AtomicInteger>();

    private Object pipelineCreateLock = new Object();
    private Object pipelineReleaseLock = new Object();
    private volatile boolean pipelineReleased = false;
    private boolean destroyKurentoClient;

    private final ConcurrentHashMap<String, String> filterStates = new ConcurrentHashMap<>();

    public Room(String roomName, KurentoClient kurentoClient, RoomHandler roomHandler,
                boolean destroyKurentoClient) {
        this.name = roomName;
        this.kurentoClient = kurentoClient;
        this.destroyKurentoClient = destroyKurentoClient;
        this.roomHandler = roomHandler;
        log.debug("New ROOM instance, named '{}'", roomName);
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

        try {
            pipelineLatch.await(Room.ASYNC_LATCH_TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return this.pipeline;
    }

    @Override
    public synchronized void join(String participantId, String userName, boolean dataChannels,
                                  boolean webParticipant) throws RoomException {

        checkClosed();

        if (userName == null || userName.isEmpty()) {
            throw new RoomException(Code.GENERIC_ERROR_CODE, "Empty user name is not allowed");
        }
        for (Participant p : participants.values()) {
            if (p.getName().equals(userName)) {
                throw new RoomException(Code.EXISTING_USER_IN_ROOM_ERROR_CODE,
                        "User '" + userName + "' already exists in room '" + name + "'");
            }
        }

        // We create a pipeline only if we have a KMS provider!
        if (kurentoClient != null) {
            createPipeline();
        }

        participants.put(participantId, new Participant(participantId, userName, this, getPipeline(),
                dataChannels, webParticipant));
        activePublishersRegisterCount.put(participantId, new AtomicInteger(0));

        filterStates.forEach((filterId, state) -> {
            log.info("Adding filter {}", filterId);
            // TODO: Fix
            //roomHandler.updateFilter(name, participant, filterId, state);
        });

        log.info("ROOM {}: Added participant {}", name, userName);
    }

    @Override
    public void newPublisher(IParticipant participant, final String streamId) {
        registerPublisher(participant.getId());

        // pre-load endpoints to recv video from the new publisher
        for (Participant participant1 : participants.values()) {
            if (participant.equals(participant1)) {
                continue;
            }
            participant1.getNewOrExistingSubscriber(participant.getName(), streamId);
        }

        log.debug("ROOM {}: Virtually subscribed other participants {} to new publisher {}", name,
                participants.values(), participant.getName());
    }

    @Override
    public void cancelPublisher(IParticipant participant, final String streamId) {
        deregisterPublisher(participant.getId());

        // cancel recv video from this publisher
        for (Participant subscriber : participants.values()) {
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

        Participant participant = participants.get(participantId);
        if (participant == null) {
            throw new RoomException(Code.USER_NOT_FOUND_ERROR_CODE,
                    "User #" + participantId + " not found in room '" + name + "'");
        }
        participant.releaseAllFilters();

        log.info("PARTICIPANT {}: Leaving room {}", participant.getName(), this.name);
        Enumeration<String> publisherStreamIds = participant.getPublisherStreamIds();
        while (publisherStreamIds.hasMoreElements()) {
            String streamId = publisherStreamIds.nextElement();
            if (participant.isStreaming(streamId)) {
                this.deregisterPublisher(participant.getId());
            }
        }
        this.removeParticipant(participant);
        participant.close();
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
    public Participant getParticipant(String participantId) {

        checkClosed();

        return participants.get(participantId);
    }

    @Override
    public Participant getParticipantByName(String userName) {

        checkClosed();

        for (Participant p : participants.values()) {
            if (p.getName().equals(userName)) {
                return p;
            }
        }

        return null;
    }

    @Override
    public void close() {
        if (!closed) {

            for (Participant user : participants.values()) {
                user.close();
            }

            participants.clear();
            activePublishersRegisterCount.clear();

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
    public void sendIceCandidate(String participantId, String participantName, String endpointName, final String streamId, IceCandidate candidate) {
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
            throw new RoomException(Code.ROOM_CLOSED_ERROR_CODE, "The room '" + name + "' is closed");
        }
    }

    private void removeParticipant(Participant participant) {

        checkClosed();

        participants.remove(participant.getId());
        activePublishersRegisterCount.remove(participant.getId());
        log.debug("ROOM {}: Cancel receiving media from user '{}' for other users", this.name,
                participant.getName());
        for (Participant other : participants.values()) {
            other.cancelReceivingAllMedias(participant.getName());
        }
    }

    @Override
    public int getActivePublishers() {
        int result = 0;

        // For each participant
        for (String participantId : activePublishersRegisterCount.keySet()) {
            // If it has been registered at least once
            // WARNING: This is DIFFERENT from checking for the ACTUAL number of streams in the corresponding participant!
            if (activePublishersRegisterCount.get(participantId).get() > 0) {
                // then he is active
                result++;
            }
        }

        return result;
    }

    @Override
    public void registerPublisher(final String participantId) {
        this.activePublishersRegisterCount.get(participantId).incrementAndGet();
    }

    @Override
    public void deregisterPublisher(final String participantId) {
        this.activePublishersRegisterCount.get(participantId).decrementAndGet();
    }

    private void createPipeline() {
        synchronized (pipelineCreateLock) {
            if (pipeline != null) {
                return;
            }
            log.info("ROOM {}: Creating MediaPipeline", name);
            try {
                // This method must not be called when we do not have a KMS provider!
                if (kurentoClient == null) {
                    throw new Exception("Cannot create a media pipeline without a KMS!");
                }
                kurentoClient.createMediaPipeline(new Continuation<MediaPipeline>() {
                    @Override
                    public void onSuccess(MediaPipeline result) throws Exception {
                        pipeline = result;
                        pipelineLatch.countDown();
                        log.debug("ROOM {}: Created MediaPipeline", name);
                    }

                    @Override
                    public void onError(Throwable cause) throws Exception {
                        pipelineLatch.countDown();
                        log.error("ROOM {}: Failed to create MediaPipeline", name, cause);
                    }
                });
            } catch (Exception e) {
                log.error("Unable to create media pipeline for room '{}'", name, e);
                pipelineLatch.countDown();
            }
            if (getPipeline() == null) {
                throw new RoomException(Code.ROOM_CANNOT_BE_CREATED_ERROR_CODE,
                        "Unable to create media pipeline for room '" + name + "'");
            }

            pipeline.addErrorListener(new EventListener<ErrorEvent>() {
                @Override
                public void onEvent(ErrorEvent event) {
                    String desc =
                            event.getType() + ": " + event.getDescription() + "(errCode=" + event.getErrorCode()
                                    + ")";
                    log.warn("ROOM {}: Pipeline error encountered: {}", name, desc);
                    roomHandler.onPipelineError(name, (Collection<IParticipant>) getParticipants(), desc);
                }
            });
        }
    }

    private void closePipeline() {
        synchronized (pipelineReleaseLock) {
            if (pipeline == null || pipelineReleased) {
                return;
            }
            getPipeline().release(new Continuation<Void>() {

                @Override
                public void onSuccess(Void result) throws Exception {
                    log.debug("ROOM {}: Released Pipeline", Room.this.name);
                    pipelineReleased = true;
                }

                @Override
                public void onError(Throwable cause) throws Exception {
                    log.warn("ROOM {}: Could not successfully release Pipeline", Room.this.name, cause);
                    pipelineReleased = true;
                }
            });
        }
    }

    @Override
    public synchronized void updateFilter(String filterId) {
        String state = filterStates.get(filterId);
        String newState = roomHandler.getNextFilterState(filterId, state);

        filterStates.put(filterId, newState);

        for (Participant participant : participants.values()) {
            roomHandler.updateFilter(getName(), participant, filterId, newState);
        }
    }
}
