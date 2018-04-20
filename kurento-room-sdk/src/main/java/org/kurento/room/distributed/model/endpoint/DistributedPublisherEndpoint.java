package org.kurento.room.distributed.model.endpoint;

import org.kurento.client.*;
import org.kurento.room.api.MutedMediaType;
import org.kurento.room.distributed.DistributedParticipant;
import org.kurento.room.distributed.model.DistributedRemoteObject;
import org.kurento.room.endpoint.SdpType;
import org.kurento.room.exception.RoomException;
import org.kurento.room.interfaces.IPublisherEndpoint;
import org.kurento.room.interfaces.IRoomManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.Collection;
import java.util.concurrent.locks.Lock;

/**
 * Created by sturiale on 06/12/16.
 */
@Component
@Scope("prototype")
public class DistributedPublisherEndpoint extends DistributedMediaEndpoint implements IPublisherEndpoint {
    private final static Logger log = LoggerFactory.getLogger(DistributedPublisherEndpoint.class);

    private PassThrough passThru = null;
    private ListenerSubscription passThruSubscription = null;
    private boolean connected = false;

    // HubPort used to global track recording
    private HubPort hubPort = null;

    // Recorder endpoint used for local track recording
    private RecorderEndpoint recorderEndpoint = null;
    private Long callStreamId = null;

    public DistributedPublisherEndpoint(boolean web, boolean dataChannels, DistributedParticipant owner,
                                        String endpointName, MediaPipeline pipeline, String kmsUrl, String streamId) {
        super(web, dataChannels, owner, endpointName, pipeline, log, kmsUrl, streamId);
    }


    public DistributedPublisherEndpoint(boolean web,
                                        boolean dataChannels,
                                        String endpointName,
                                        String kmsUrl,
                                        String streamId,
                                        KurentoClient kurentoClient,
                                        DistributedRemoteObject webEndpointInfo,
                                        DistributedRemoteObject rtpEndpointInfo,
                                        DistributedRemoteObject recEndpointInfo,
                                        DistributedRemoteObject passThrouInfo,
                                        DistributedRemoteObject hubportInfo,
                                        String roomName,
                                        String participantId,
                                        MutedMediaType muteType,
                                        boolean connected,
                                        Long callStreamId,
                                        IRoomManager roomManager) {
        super(web, dataChannels, endpointName, kmsUrl, streamId, kurentoClient, webEndpointInfo, rtpEndpointInfo, roomName, participantId, muteType, roomManager, log);
        this.connected = connected;
        this.callStreamId = callStreamId;
        this.recorderEndpoint = DistributedRemoteObject.retrieveFromInfo(recEndpointInfo, kurentoClient);
        this.passThru = DistributedRemoteObject.retrieveFromInfo(passThrouInfo, kurentoClient);
        this.hubPort = DistributedRemoteObject.retrieveFromInfo(hubportInfo, kurentoClient);
    }

    @Override
    public HubPort getHubPort() {
        return hubPort;
    }

    @Override
    public void addTrackToGlobalRecording() {
        if (hubPort == null) {
            log.debug("Adding participant {} stream {} to global recording...", getOwner().getName(), getStreamId());
            hubPort = this.getOwner().getRoom().allocateHubPort();
            internalSinkConnect(passThru, hubPort);

            // Signal the participant that this endpoint data has changed
            listener.onChange(this);
        }
    }

    @Override
    public void removeTrackFromGlobalRecording() {
        if (hubPort != null) {
            log.debug("Removing participant {} stream {} from global recording...", getOwner().getName(), getStreamId());
            internalSinkDisconnect(passThru, hubPort);
            hubPort = null;

            // Signal the participant that this endpoint data has changed
            listener.onChange(this);
        }
    }

    @Override
    public void startRecording(final String fileName, final MediaProfileSpecType mediaSpecType, final Long callStreamId, final Continuation<Void> continuation) {
        if (recorderEndpoint == null) {
            // Add the endpoint to the pipeline
            this.callStreamId = callStreamId;

            recorderEndpoint = new RecorderEndpoint.Builder(getPipeline(), fileName).withMediaProfile(mediaSpecType).build();
            internalSinkConnect(getWebEndpoint(), recorderEndpoint);

            // Start the recording
            recorderEndpoint.record(continuation);

            // Signal the participant that this endpoint data has changed
            listener.onChange(this);
        }
    }

    @Override
    public void stopRecording(final Continuation<Void> continuation) {
        if (recorderEndpoint != null) {
            recorderEndpoint.stop(continuation);

            // Remove the node from the pipeline
            internalSinkDisconnect(getWebEndpoint(), recorderEndpoint);

            recorderEndpoint = null;
            callStreamId = null;

            // Signal the participant that this endpoint data has changed
            listener.onChange(this);
        }
    }

    @Override
    public Long getCallStreamId() {
        return callStreamId;
    }

    @Override
    protected void internalEndpointInitialization() {
        super.internalEndpointInitialization();
        passThru = new PassThrough.Builder(getPipeline()).build();
        passThru.setMinOutputBitrate(0); // 0 is considered unconstrained
        passThru.setMaxOutputBitrate(0); // 0 is considered unconstrained
        passThruSubscription = registerElemErrListener(passThru);
    }

    @Override
    public synchronized void unregisterErrorListeners() {
        super.unregisterErrorListeners();
        unregisterElementErrListener(passThru, passThruSubscription);
    }

    @Override
    public Collection<MediaElement> getMediaElements() {
        throw new NotImplementedException();
    }

    /**
     * Initializes this media endpoint for publishing media and processes the SDP offer or answer. If
     * the internal endpoint is an {@link WebRtcEndpoint}, it first registers an event listener for
     * the ICE candidates and instructs the endpoint to start gathering the candidates. If required,
     * it connects to itself (after applying the intermediate media elements and the
     * {@link PassThrough}) to allow loopback of the media stream.
     *
     * @param sdpType                indicates the type of the sdpString (offer or answer)
     * @param sdpString              offer or answer from the remote peer
     * @param doLoopback             loopback flag
     * @param loopbackAlternativeSrc alternative loopback source
     * @param loopbackConnectionType how to connect the loopback source
     * @return the SDP response (the answer if processing an offer SDP, otherwise is the updated offer
     * generated previously by this endpoint)
     */
    @Override
    public String publish(SdpType sdpType, String sdpString, boolean doLoopback,
                          MediaElement loopbackAlternativeSrc, MediaType loopbackConnectionType) {
        Lock lock = getLock();
        lock.lock();
        try {
            registerOnIceCandidateEventListener();
            if (doLoopback) {
                if (loopbackAlternativeSrc == null) {
                    connect(this.getEndpoint(), loopbackConnectionType);
                } else {
                    connectAltLoopbackSrc(loopbackAlternativeSrc, loopbackConnectionType);
                }
            } else {
                innerConnect();
            }
            String sdpResponse = null;
            switch (sdpType) {
                case ANSWER:
                    sdpResponse = processAnswer(sdpString);
                    break;
                case OFFER:
                    sdpResponse = processOffer(sdpString);
                    break;
                default:
                    throw new RoomException(RoomException.Code.MEDIA_SDP_ERROR_CODE, "Sdp type not supported: " + sdpType);
            }
            gatherCandidates();
            return sdpResponse;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String preparePublishConnection() {
        Lock lock = getLock();
        lock.lock();
        try {
            return generateOffer();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void connect(MediaElement sink) {
        Lock lock = getLock();
        lock.lock();
        try {
            if (!connected) {
                innerConnect();
            }
            internalSinkConnect(passThru, sink);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void connect(MediaElement sink, MediaType type) {
        Lock lock = getLock();
        lock.lock();
        try {
            if (!connected) {
                innerConnect();
            }
            internalSinkConnect(passThru, sink, type);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void disconnectFrom(MediaElement sink) {
        Lock lock = getLock();
        lock.lock();
        try {
            internalSinkDisconnect(passThru, sink);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void disconnectFrom(MediaElement sink, MediaType type) {
        Lock lock = getLock();
        lock.lock();
        try {
            internalSinkDisconnect(passThru, sink, type);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String apply(MediaElement shaper) throws RoomException {
        throw new NotImplementedException();
    }

    @Override
    public String apply(MediaElement shaper, MediaType type) throws RoomException {
        throw new NotImplementedException();
    }

    @Override
    public void revert(MediaElement shaper) throws RoomException {
        throw new NotImplementedException();
    }

    @Override
    public void revert(MediaElement shaper, boolean ùElement) throws RoomException {
        throw new NotImplementedException();
    }

    @Override
    public void mute(MutedMediaType muteType) {
        Lock lock = getLock();
        lock.lock();
        try {
            MediaElement sink = passThru;
            log.debug("Will mute connection of WebRTC and PassThrough (no other elems)");
            switch (muteType) {
                case ALL:
                    internalSinkDisconnect(this.getEndpoint(), sink);
                    break;
                case AUDIO:
                    internalSinkDisconnect(this.getEndpoint(), sink, MediaType.AUDIO);
                    break;
                case VIDEO:
                    internalSinkDisconnect(this.getEndpoint(), sink, MediaType.VIDEO);
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
            MediaElement sink = passThru;
            log.debug("Will unmute connection of WebRTC and PassThrough (no other elems)");
            internalSinkConnect(this.getEndpoint(), sink);
            setMuteType(null);
        } finally {
            lock.unlock();
        }
    }

    private void connectAltLoopbackSrc(MediaElement loopbackAlternativeSrc,
                                       MediaType loopbackConnectionType) {
        if (!connected) {
            innerConnect();
        }
        internalSinkConnect(loopbackAlternativeSrc, this.getEndpoint(), loopbackConnectionType);
    }

    private void innerConnect() {
        if (this.getEndpoint() == null) {
            throw new RoomException(RoomException.Code.MEDIA_ENDPOINT_ERROR_CODE,
                    "Can't connect null endpoint (ep: " + getEndpointName() + ")");
        }
        MediaElement current = this.getEndpoint();
        internalSinkConnect(current, passThru);
        connected = true;
    }

    private void internalSinkConnect(final MediaElement source, final MediaElement sink) {
        source.connect(sink, new Continuation<Void>() {
            @Override
            public void onSuccess(Void result) throws Exception {
                log.debug("EP {}: Elements have been connected (source {} -> sink {})", getEndpointName(),
                        source.getId(), sink.getId());
            }

            @Override
            public void onError(Throwable cause) throws Exception {
                log.warn("EP {}: Failed to connect media elements (source {} -> sink {})",
                        getEndpointName(), source.getId(), sink.getId(), cause);
            }
        });
    }

    /**
     * Same as {@link #internalSinkConnect(MediaElement, MediaElement)}, but can specify the type of
     * the media that will be streamed.
     *
     * @param source
     * @param sink
     * @param type   if null, {@link #internalSinkConnect(MediaElement, MediaElement)} will be used
     *               instead
     * @see #internalSinkConnect(MediaElement, MediaElement)
     */
    private void internalSinkConnect(final MediaElement source, final MediaElement sink,
                                     final MediaType type) {
        if (type == null) {
            internalSinkConnect(source, sink);
        } else {
            source.connect(sink, type, new Continuation<Void>() {
                @Override
                public void onSuccess(Void result) throws Exception {
                    log.debug("EP {}: {} media elements have been connected (source {} -> sink {})",
                            getEndpointName(), type, source.getId(), sink.getId());
                }

                @Override
                public void onError(Throwable cause) throws Exception {
                    log.warn("EP {}: Failed to connect {} media elements (source {} -> sink {})",
                            getEndpointName(), type, source.getId(), sink.getId(), cause);
                }
            });
        }
    }

    private void internalSinkDisconnect(final MediaElement source, final MediaElement sink) {
        source.disconnect(sink, new Continuation<Void>() {
            @Override
            public void onSuccess(Void result) throws Exception {
                log.debug("EP {}: Elements have been disconnected (source {} -> sink {})",
                        getEndpointName(), source.getId(), sink.getId());
            }

            @Override
            public void onError(Throwable cause) throws Exception {
                log.warn("EP {}: Failed to disconnect media elements (source {} -> sink {})",
                        getEndpointName(), source.getId(), sink.getId(), cause);
            }
        });
    }

    /**
     * Same as {@link #internalSinkDisconnect(MediaElement, MediaElement)}, but can specify the type
     * of the media that will be disconnected.
     *
     * @param source
     * @param sink
     * @param type   if null, {@link #internalSinkConnect(MediaElement, MediaElement)} will be used
     *               instead
     * @see #internalSinkConnect(MediaElement, MediaElement)
     */
    private void internalSinkDisconnect(final MediaElement source, final MediaElement sink,
                                        final MediaType type) {
        if (type == null) {
            internalSinkDisconnect(source, sink);
        } else {
            source.disconnect(sink, type, new Continuation<Void>() {
                @Override
                public void onSuccess(Void result) throws Exception {
                    log.debug("EP {}: {} media elements have been disconnected (source {} -> sink {})",
                            getEndpointName(), type, source.getId(), sink.getId());
                }

                @Override
                public void onError(Throwable cause) throws Exception {
                    log.warn("EP {}: Failed to disconnect {} media elements (source {} -> sink {})",
                            getEndpointName(), type, source.getId(), sink.getId(), cause);
                }
            });
        }
    }

    @Override
    public PassThrough getPassThru() {
        return passThru;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public RecorderEndpoint getRecorderEndpoint() {
        return recorderEndpoint;
    }
}
