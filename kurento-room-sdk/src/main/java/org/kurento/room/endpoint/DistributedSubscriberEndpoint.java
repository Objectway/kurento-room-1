package org.kurento.room.endpoint;

import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.kurento.client.MediaType;
import org.kurento.room.api.MutedMediaType;
import org.kurento.room.distributed.model.DistributedRemoteObject;
import org.kurento.room.exception.RoomException;
import org.kurento.room.interfaces.IPublisherEndpoint;
import org.kurento.room.interfaces.IRoomManager;
import org.kurento.room.interfaces.ISubscriberEndpoint;
import org.kurento.room.internal.DistributedParticipant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.Lock;

/**
 * Created by sturiale on 06/12/16.
 */
@Component
@Scope("prototype")
public class DistributedSubscriberEndpoint extends DistributedMediaEndpoint implements ISubscriberEndpoint {
    private final static Logger log = LoggerFactory.getLogger(DistributedSubscriberEndpoint.class);

    @Autowired
    private IRoomManager roomManager;

    private boolean connectedToPublisher = false;

    private DistributedPublisherEndpoint publisher = null;

    public DistributedSubscriberEndpoint(boolean web, DistributedParticipant owner, String endpointName,
                                         MediaPipeline pipeline, String kmsUrl, String streamId) {
        super(web, false, owner, endpointName, pipeline, log, kmsUrl, streamId);
    }

    public DistributedSubscriberEndpoint(boolean web,
                                         boolean dataChannels,
                                         String endpointName,
                                         String kmsUrl,
                                         String streamId,
                                         KurentoClient kurentoClient,
                                         DistributedRemoteObject webEndpointInfo,
                                         DistributedRemoteObject rtpEndpointInfo,
                                         String roomName,
                                         String participantId,
                                         MutedMediaType muteType,
                                         boolean connectedToPublisher,
                                         IRoomManager roomManager) {
        super(web, dataChannels, endpointName, kmsUrl, streamId, kurentoClient, webEndpointInfo, rtpEndpointInfo, roomName, participantId, muteType, roomManager, log);
        this.connectedToPublisher = connectedToPublisher;
        this.publisher = (DistributedPublisherEndpoint) roomManager.getRoomByName(roomName).getParticipant(participantId).getPublisher(streamId);
    }

    @Override
    public String subscribe(String sdpOffer, IPublisherEndpoint publisher) {
        Lock lock = getLock();
        lock.lock();
        try {
            registerOnIceCandidateEventListener();
            String sdpAnswer = processOffer(sdpOffer);
            gatherCandidates();
            publisher.connect(this.getEndpoint());
            setConnectedToPublisher(true);
            setPublisher(publisher);
            return sdpAnswer;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isConnectedToPublisher() {
        return connectedToPublisher;
    }

    @Override
    public void setConnectedToPublisher(boolean connectedToPublisher) {
        this.connectedToPublisher = connectedToPublisher;
    }

    @Override
    public DistributedPublisherEndpoint getPublisher() {
        return publisher;
    }

    @Override
    public void setPublisher(IPublisherEndpoint publisher) {
        this.publisher = (DistributedPublisherEndpoint) publisher;
    }

    @Override
    public void mute(MutedMediaType muteType) {
        Lock lock = getLock();
        lock.lock();
        try {
            if (this.publisher == null) {
                throw new RoomException(RoomException.Code.MEDIA_MUTE_ERROR_CODE, "Publisher endpoint not found");
            }
            switch (muteType) {
                case ALL:
                    this.publisher.disconnectFrom(this.getEndpoint());
                    break;
                case AUDIO:
                    this.publisher.disconnectFrom(this.getEndpoint(), MediaType.AUDIO);
                    break;
                case VIDEO:
                    this.publisher.disconnectFrom(this.getEndpoint(), MediaType.VIDEO);
                    break;
            }
            resolveCurrentMuteType(muteType);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void unmute() {
        Lock lock = getLock();
        lock.lock();
        try {
            this.publisher.connect(this.getEndpoint());
            setMuteType(null);
        } finally {
            lock.unlock();
        }
    }
}
