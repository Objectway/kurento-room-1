package org.kurento.room.endpoint;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IList;
import com.hazelcast.core.ILock;
import org.kurento.client.*;
import org.kurento.room.TurnKMSCredentials;
import org.kurento.room.api.MutedMediaType;
import org.kurento.room.internal.DistributedParticipant;
import org.kurento.room.distributed.interfaces.IChangeListener;
import org.kurento.room.distributed.interfaces.ICountDownLatchWrapper;
import org.kurento.room.distributed.interfaces.IDistributedNamingService;
import org.kurento.room.distributed.model.DistributedIceCandidate;
import org.kurento.room.distributed.model.DistributedRemoteObject;
import org.kurento.room.exception.RoomException;
import org.kurento.room.interfaces.IMediaEndpoint;
import org.kurento.room.interfaces.IRoom;
import org.kurento.room.interfaces.IRoomManager;
import org.kurento.room.interfaces.ITurnProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import javax.annotation.PostConstruct;
import java.util.concurrent.locks.Lock;

/**
 * Created by sturiale on 06/12/16.
 */
public abstract class DistributedMediaEndpoint implements IMediaEndpoint{
    private static Logger log;

    @Autowired
    private HazelcastInstance hazelcastInstance;

    @Autowired
    private IDistributedNamingService namingService;

    @Autowired
    private ITurnProvider turnProvider;

    private boolean web = false;

    private boolean dataChannels = false;

    private WebRtcEndpoint webEndpoint = null;
    private RtpEndpoint endpoint = null;

    private DistributedParticipant owner;
    private String endpointName;

    private MediaPipeline pipeline = null;
    private ListenerSubscription endpointSubscription = null;

    private IList<DistributedIceCandidate> candidates;

    private MutedMediaType muteType;

    private String kmsUrl;

    private String streamId;

    protected IChangeListener<DistributedMediaEndpoint> listener;

    private ILock mediaEndpointLock;

    @PostConstruct
    public void init() {
        candidates = hazelcastInstance.getList(namingService.getName("icecandidates-" + endpointName));
        mediaEndpointLock = hazelcastInstance.getLock(namingService.getName("lock-mediaendpoint-" + endpointName));
    }

    /**
     * Destroys the hazelcast resources.
     */
    public void destroyHazelcastResources() {
        candidates.destroy();
        mediaEndpointLock.destroy();
    }

    /**
     * Constructor to set the owner, the endpoint's name and the media pipeline.
     *
     * @param web
     * @param dataChannels
     * @param owner
     * @param endpointName
     * @param pipeline
     * @param log
     */
    public DistributedMediaEndpoint(boolean web, boolean dataChannels, DistributedParticipant owner, String endpointName,
                                    MediaPipeline pipeline, Logger log, String kmsUrl, String streamId) {
        if (log == null) {
            DistributedMediaEndpoint.log = LoggerFactory.getLogger(DistributedMediaEndpoint.class);
        } else {
            DistributedMediaEndpoint.log = log;
        }
        this.web = web;
        this.dataChannels = dataChannels;
        this.owner = owner;
        this.setListener(owner);
        this.setEndpointName(endpointName);
        this.setMediaPipeline(pipeline);
        this.kmsUrl = kmsUrl;
        this.streamId = streamId;
    }

    public DistributedMediaEndpoint(boolean web,
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
                                    IRoomManager roomManager,
                                    Logger log) {
        if (log == null) {
            DistributedMediaEndpoint.log = LoggerFactory.getLogger(DistributedMediaEndpoint.class);
        } else {
            DistributedMediaEndpoint.log = log;
        }
        this.web = web;
        this.dataChannels = dataChannels;
        this.endpointName = endpointName;
        this.kmsUrl = kmsUrl;
        this.streamId = streamId;
        this.muteType = muteType;
        IRoom room = roomManager.getRoomByName(roomName);
        this.owner = (DistributedParticipant) room.getParticipant(participantId);
        this.setListener(owner);
        this.pipeline = room.getPipeline();
        try {
            if (webEndpointInfo != null) {
                final Class<KurentoObject> clazz = (Class<KurentoObject>) Class.forName(webEndpointInfo.getClassName());
                this.webEndpoint = (WebRtcEndpoint) kurentoClient.getById(webEndpointInfo.getObjectRef(), clazz);
//                endpointSubscription = registerElemErrListener(webEndpoint);
            }
            if (rtpEndpointInfo != null) {
                final Class<KurentoObject> clazz = (Class<KurentoObject>) Class.forName(rtpEndpointInfo.getClassName());
                this.endpoint = (RtpEndpoint) kurentoClient.getById(rtpEndpointInfo.getObjectRef(), clazz);
//                endpointSubscription = registerElemErrListener(endpoint);
            }

            // We always have a KurentoObject as a result, even if it does not exist in the KMS

        } catch (ClassNotFoundException ex) {
            log.error(ex.toString());
        } catch (Exception e) {
            // Try to invoke this endpoint with objectRef ending in ".MediaPipelinez" to trigger
            //      org.kurento.client.internal.server.ProtocolException: Exception creating Java Class for 'kurento.MediaPipelinez'
            log.error(e.toString());
        }

    }

    public boolean isWeb() {
        return web;
    }

    /**
     * @return the user session that created this endpoint
     */
    public DistributedParticipant getOwner() {
        return owner;
    }

    /**
     * @return the internal endpoint ({@link RtpEndpoint} or {@link WebRtcEndpoint})
     */
    public SdpEndpoint getEndpoint() {
        if (this.isWeb()) {
            return this.webEndpoint;
        } else {
            return this.endpoint;
        }
    }

    public WebRtcEndpoint getWebEndpoint() {
        return webEndpoint;
    }

    public RtpEndpoint getRtpEndpoint() {
        return endpoint;
    }

    /**
     * If this object doesn't have a {@link WebRtcEndpoint}, it is created in a thread-safe way using
     * the internal {@link MediaPipeline}. Otherwise no actions are taken. It also registers an error
     * listener for the endpoint and for any additional media elements.
     *
     * @param endpointLatch latch whose countdown is performed when the asynchronous call to build the
     *                      {@link WebRtcEndpoint} returns
     * @return the existing endpoint, if any
     */
    public SdpEndpoint createEndpoint() {
        Lock lock = getLock();
        lock.lock();
        try {
            SdpEndpoint old = this.getEndpoint();
            if (old == null) {
                internalEndpointInitialization();
            }
//            else {
//                endpointLatch.countDown();
//            }
            if (this.isWeb()) {
                for (DistributedIceCandidate candidate : candidates) {
                    internalAddIceCandidate(candidate);
                }
                candidates.clear();
            }
            return old;

        } finally {
            lock.unlock();
            listener.onChange(this);
        }
    }
    public SdpEndpoint createEndpoint(ICountDownLatchWrapper countDownLatchWrapper) {
       throw new NotImplementedException();
    }

    protected Lock getLock() {
        return mediaEndpointLock;
    }

    /**
     * @return the pipeline
     */
    public MediaPipeline getPipeline() {
        return this.pipeline;
    }

    /**
     * Sets the {@link MediaPipeline} used to create the internal {@link WebRtcEndpoint}.
     *
     * @param pipeline the {@link MediaPipeline}
     */
    public void setMediaPipeline(MediaPipeline pipeline) {
        this.pipeline = pipeline;
    }

    /**
     * @return name of this endpoint (as indicated by the browser)
     */
    public String getEndpointName() {
        return endpointName;
    }

    /**
     * Sets the endpoint's name (as indicated by the browser).
     *
     * @param endpointName the name
     */
    public void setEndpointName(String endpointName) {
        this.endpointName = endpointName;
    }

    /**
     * Unregisters all error listeners created for media elements owned by this instance.
     */
    public void unregisterErrorListeners() {
        Lock lock = getLock();
        lock.lock();
        try {
            unregisterElementErrListener(endpoint, endpointSubscription);
        } finally {
            lock.unlock();
        }

    }

    /**
     * Mute the media stream.
     *
     * @param muteType which type of leg to disconnect (audio, video or both)
     */
    public abstract void mute(MutedMediaType muteType);

    /**
     * Reconnect the muted media leg(s).
     */
    public abstract void unmute();

    public void setMuteType(MutedMediaType muteType) {
        this.muteType = muteType;
    }

    public MutedMediaType getMuteType() {
        return this.muteType;
    }

    protected void resolveCurrentMuteType(MutedMediaType newMuteType) {
        MutedMediaType prev = this.getMuteType();
        if (prev != null) {
            switch (prev) {
                case AUDIO:
                    if (muteType.equals(MutedMediaType.VIDEO)) {
                        this.setMuteType(MutedMediaType.ALL);
                        return;
                    }
                    break;
                case VIDEO:
                    if (muteType.equals(MutedMediaType.AUDIO)) {
                        this.setMuteType(MutedMediaType.ALL);
                        return;
                    }
                    break;
                case ALL:
                    return;
            }
        }
        this.setMuteType(newMuteType);
    }

    /**
     * Creates the endpoint (RTP or WebRTC) and any other additional elements (if needed).
     *
     * @param endpointLatch
     */
    protected void internalEndpointInitialization() {
        if (this.isWeb()) {
            final TurnKMSCredentials credentials = turnProvider.generateKMSCredentials();
            WebRtcEndpoint.Builder builder = new WebRtcEndpoint.Builder(pipeline);
//                .with("stunServerAddress", credentials.getStunUrl())
//                .with("stunServerPort", credentials.getStunPort())
//                .with("turnUrl", credentials.getTurnUrl())
//                .with("maxVideoRecvBandwidth", 600)
//                .with("minVideoRecvBandwidth", 300)
//                .with("maxVideoSendBandwidth", 600)
//                .with("minVideoSendBandwidth", 300);
            if (this.dataChannels) {
                builder.useDataChannels();
            }
            webEndpoint = builder.build();
            webEndpoint.setStunServerAddress(credentials.getStunUrl());
            webEndpoint.setStunServerPort(credentials.getStunPort());
            webEndpoint.setTurnUrl(credentials.getTurnUrl());
            webEndpoint.setMaxVideoRecvBandwidth(0); // 0 is considered unconstrained
            webEndpoint.setMinVideoRecvBandwidth(0); // 0 is considered unconstrained
            webEndpoint.setMaxVideoSendBandwidth(0); // 0 is considered unconstrained
            webEndpoint.setMinVideoSendBandwidth(0); // 0 is considered unconstrained
            log.trace("EP {}: Created a new WebRtcEndpoint", endpointName);
            endpointSubscription = registerElemErrListener(webEndpoint);

//            builder.buildAsync(new Continuation<WebRtcEndpoint>() {
//                @Override
//                public void onSuccess(WebRtcEndpoint result) throws Exception {
//                    webEndpoint = result;
//
//                    webEndpoint.setMaxVideoRecvBandwidth(600);
//                    webEndpoint.setMinVideoRecvBandwidth(300);
//                    webEndpoint.setMaxVideoSendBandwidth(600);
//                    webEndpoint.setMinVideoSendBandwidth(300);
//
//                    endpointLatch.countDown();
//                    log.trace("EP {}: Created a new WebRtcEndpoint", endpointName);
//                    endpointSubscription = registerElemErrListener(webEndpoint);
//                }
//
//                @Override
//                public void onError(Throwable cause) throws Exception {
//                    endpointLatch.countDown();
//                    log.error("EP {}: Failed to create a new WebRtcEndpoint", endpointName, cause);
//                }
//            });
        } else {
            endpoint =  new RtpEndpoint.Builder(pipeline).build();
            log.trace("EP {}: Created a new RtpEndpoint", endpointName);
            endpointSubscription = registerElemErrListener(endpoint);
//            new RtpEndpoint.Builder(pipeline).buildAsync(new Continuation<RtpEndpoint>() {
//                @Override
//                public void onSuccess(RtpEndpoint result) throws Exception {
//                    endpoint = result;
//                    endpointLatch.countDown();
//                    log.trace("EP {}: Created a new RtpEndpoint", endpointName);
//                    endpointSubscription = registerElemErrListener(endpoint);
//                }
//
//                @Override
//                public void onError(Throwable cause) throws Exception {
//                    endpointLatch.countDown();
//                    log.error("EP {}: Failed to create a new RtpEndpoint", endpointName, cause);
//                }
//            });
        }
    }

    /**
     * Add a new {@link IceCandidate} received gathered by the remote peer of this
     * {@link WebRtcEndpoint}.
     *
     * @param candidate the remote candidate
     */
    public void addIceCandidate(IceCandidate candidate) throws RoomException {
        Lock lock = getLock();
        lock.lock();
        try {
            if (!this.isWeb()) {
                throw new RoomException(RoomException.Code.MEDIA_NOT_A_WEB_ENDPOINT_ERROR_CODE, "Operation not supported");
            }
            if (webEndpoint == null) {
                candidates.add((DistributedIceCandidate)candidate);
            } else {
                internalAddIceCandidate((DistributedIceCandidate)candidate);
            }
        }catch (Exception e){
            log.error(e.toString());
        } finally{
            lock.unlock();
        }

    }

    /**
     * Registers a listener for when the {@link MediaElement} triggers an {@link ErrorEvent}. Notifies
     * the owner with the error.
     *
     * @param element the {@link MediaElement}
     * @return {@link ListenerSubscription} that can be used to deregister the listener
     */
    protected ListenerSubscription registerElemErrListener(MediaElement element) {
        return element.addErrorListener(new EventListener<ErrorEvent>() {
            @Override
            public void onEvent(ErrorEvent event) {
                owner.sendMediaError(event);
            }
        });
    }

    /**
     * Unregisters the error listener from the media element using the provided subscription.
     *
     * @param element      the {@link MediaElement}
     * @param subscription the associated {@link ListenerSubscription}
     */
    protected void unregisterElementErrListener(MediaElement element,
                                                final ListenerSubscription subscription) {
        if (element == null || subscription == null) {
            return;
        }
        element.removeErrorListener(subscription);
    }

    /**
     * Orders the internal endpoint ({@link RtpEndpoint} or {@link WebRtcEndpoint}) to process the
     * offer String.
     *
     * @param offer String with the Sdp offer
     * @return the Sdp answer
     * @see SdpEndpoint#processOffer(String)
     */
    protected String processOffer(String offer) throws RoomException {
        if (this.isWeb()) {
            if (webEndpoint == null) {
                throw new RoomException(RoomException.Code.MEDIA_WEBRTC_ENDPOINT_ERROR_CODE,
                        "Can't process offer when WebRtcEndpoint is null (ep: " + endpointName + ")");
            }
            return webEndpoint.processOffer(offer);
        } else {
            if (endpoint == null) {
                throw new RoomException(RoomException.Code.MEDIA_RTP_ENDPOINT_ERROR_CODE,
                        "Can't process offer when RtpEndpoint is null (ep: " + endpointName + ")");
            }
            return endpoint.processOffer(offer);
        }
    }

    /**
     * Orders the internal endpoint ({@link RtpEndpoint} or {@link WebRtcEndpoint}) to generate the
     * offer String that can be used to initiate a connection.
     *
     * @return the Sdp offer
     * @see SdpEndpoint#generateOffer()
     */
    protected String generateOffer() throws RoomException {
        if (this.isWeb()) {
            if (webEndpoint == null) {
                throw new RoomException(RoomException.Code.MEDIA_WEBRTC_ENDPOINT_ERROR_CODE,
                        "Can't generate offer when WebRtcEndpoint is null (ep: " + endpointName + ")");
            }
            return webEndpoint.generateOffer();
        } else {
            if (endpoint == null) {
                throw new RoomException(RoomException.Code.MEDIA_RTP_ENDPOINT_ERROR_CODE,
                        "Can't generate offer when RtpEndpoint is null (ep: " + endpointName + ")");
            }
            return endpoint.generateOffer();
        }
    }

    /**
     * Orders the internal endpoint ({@link RtpEndpoint} or {@link WebRtcEndpoint}) to process the
     * answer String.
     *
     * @param answer String with the Sdp answer from remote
     * @return the updated Sdp offer, based on the received answer
     * @see SdpEndpoint#processAnswer(String)
     */
    protected String processAnswer(String answer) throws RoomException {
        if (this.isWeb()) {
            if (webEndpoint == null) {
                throw new RoomException(RoomException.Code.MEDIA_WEBRTC_ENDPOINT_ERROR_CODE,
                        "Can't process answer when WebRtcEndpoint is null (ep: " + endpointName + ")");
            }
            return webEndpoint.processAnswer(answer);
        } else {
            if (endpoint == null) {
                throw new RoomException(RoomException.Code.MEDIA_RTP_ENDPOINT_ERROR_CODE,
                        "Can't process answer when RtpEndpoint is null (ep: " + endpointName + ")");
            }
            return endpoint.processAnswer(answer);
        }
    }

    /**
     * If supported, it registers a listener for when a new {@link IceCandidate} is gathered by the
     * internal endpoint ({@link WebRtcEndpoint}) and sends it to the remote User Agent as a
     * notification using the messaging capabilities of the {@link Participant}.
     *
     * @throws RoomException if thrown, unable to register the listener
     * @see WebRtcEndpoint#addOnIceCandidateListener(org.kurento.client.EventListener)
     * @see Participant#sendIceCandidate(String, IceCandidate)
     */
    protected void registerOnIceCandidateEventListener() throws RoomException {
        if (!this.isWeb()) {
            return;
        }
        if (webEndpoint == null) {
            throw new RoomException(RoomException.Code.MEDIA_WEBRTC_ENDPOINT_ERROR_CODE,
                    "Can't register event listener for null WebRtcEndpoint (ep: " + endpointName + ")");
        }
        webEndpoint.addOnIceCandidateListener(new EventListener<OnIceCandidateEvent>() {
            @Override
            public void onEvent(OnIceCandidateEvent event) {
                owner.sendIceCandidate(endpointName.substring(0, endpointName.lastIndexOf('_')), endpointName.substring(endpointName.lastIndexOf('_') + 1), event.getCandidate());
            }
        });
    }

    /**
     * If supported, it instructs the internal endpoint to start gathering {@link IceCandidate}s.
     */
    protected void gatherCandidates() throws RoomException {
        if (!this.isWeb()) {
            return;
        }
        if (webEndpoint == null) {
            throw new RoomException(RoomException.Code.MEDIA_WEBRTC_ENDPOINT_ERROR_CODE,
                    "Can't start gathering ICE candidates on null WebRtcEndpoint (ep: " + endpointName + ")");
        }
        webEndpoint.gatherCandidates(new Continuation<Void>() {
            @Override
            public void onSuccess(Void result) throws Exception {
                log.trace("EP {}: Internal endpoint started to gather candidates", endpointName);
            }

            @Override
            public void onError(Throwable cause) throws Exception {
                log.warn("EP {}: Internal endpoint failed to start gathering candidates", endpointName,
                        cause);
            }
        });
    }

    private void internalAddIceCandidate(DistributedIceCandidate candidate) throws RoomException {
        if (webEndpoint == null) {
            throw new RoomException(RoomException.Code.MEDIA_WEBRTC_ENDPOINT_ERROR_CODE,
                    "Can't add existing ICE candidates to null WebRtcEndpoint (ep: " + endpointName + ")");
        }

        // Kurento cannot handle DistributedIceCandidate class remotely, sho
        // we convert the ice candidate to the Kurento class
        this.webEndpoint.addIceCandidate(candidate.toIceCandidate());
//        this.webEndpoint.addIceCandidate(candidate, new Continuation<Void>() {
//            @Override
//            public void onSuccess(Void result) throws Exception {
//                log.trace("Ice candidate added to the internal endpoint");
//            }
//
//            @Override
//            public void onError(Throwable cause) throws Exception {
//                log.warn("EP {}: Failed to add ice candidate to the internal endpoint", endpointName, cause);
//            }
//        });
    }

    public String getKmsUrl() {
        return kmsUrl;
    }

    public void setKmsUrl(String kmsUrl) {
        this.kmsUrl = kmsUrl;
    }

    public boolean isDataChannels() {
        return dataChannels;
    }

    public void setListener(IChangeListener<DistributedMediaEndpoint> listener) {
        this.listener = listener;
    }

    public String getStreamId() {
        return streamId;
    }


}
