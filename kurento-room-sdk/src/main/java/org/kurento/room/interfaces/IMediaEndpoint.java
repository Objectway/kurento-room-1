package org.kurento.room.interfaces;

import org.kurento.client.IceCandidate;
import org.kurento.client.MediaPipeline;
import org.kurento.client.SdpEndpoint;
import org.kurento.room.api.MutedMediaType;
import org.kurento.room.distributed.interfaces.ICountDownLatchWrapper;
import org.kurento.room.exception.RoomException;

/**
 * Created by sturiale on 07/12/16.
 */
public interface IMediaEndpoint {
    boolean isWeb();

    IParticipant getOwner();

    SdpEndpoint getEndpoint();

    SdpEndpoint createEndpoint(ICountDownLatchWrapper endpointLatch);

    SdpEndpoint createEndpoint();

    MediaPipeline getPipeline();

    void setMediaPipeline(MediaPipeline pipeline);

    String getEndpointName();

    void setEndpointName(String endpointName);

    void unregisterErrorListeners();

    /**
     * Mute the media stream.
     *
     * @param muteType
     *          which type of leg to disconnect (audio, video or both)
     */
    void mute(MutedMediaType muteType);

    /**
     * Reconnect the muted media leg(s).
     */
    void unmute();

    void setMuteType(MutedMediaType muteType);

    MutedMediaType getMuteType();

    void addIceCandidate(IceCandidate candidate) throws RoomException;
}
