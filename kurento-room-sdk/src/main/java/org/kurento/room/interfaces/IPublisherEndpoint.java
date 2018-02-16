package org.kurento.room.interfaces;

import org.kurento.client.*;
import org.kurento.room.api.MutedMediaType;
import org.kurento.room.endpoint.SdpType;
import org.kurento.room.exception.RoomException;

import java.util.Collection;

/**
 * Created by sturiale on 07/12/16.
 */
public interface IPublisherEndpoint extends IMediaEndpoint {
    /**
     * Starts recording the stream (individual track)
     *
     * @param fileName
     * @param mediaSpecType
     */

    void startRecording(String fileName, MediaProfileSpecType mediaSpecType, Long callStreamId, final Continuation<Void> continuation);

    /**
     * Stops the stream recording (individual track)
     */
    void stopRecording(final Continuation<Void> continuation);


    /**
     * Adds the track to the room's Composite Media Element
     */
    void startGlobalRecording();

    /**
     * Removes the track from the room's Composite Media Element
     */
    void stopGlobalRecording();

    HubPort getHubPort();

    Long getCallStreamId();

    void unregisterErrorListeners();

    Collection<MediaElement> getMediaElements();

    String publish(SdpType sdpType, String sdpString, boolean doLoopback,
                   MediaElement loopbackAlternativeSrc, MediaType loopbackConnectionType);

    String preparePublishConnection();

    void connect(MediaElement sink);

    void connect(MediaElement sink, MediaType type);

    void disconnectFrom(MediaElement sink);

    void disconnectFrom(MediaElement sink, MediaType type);

    String apply(MediaElement shaper) throws RoomException;

    String apply(MediaElement shaper, MediaType type) throws RoomException;

    void revert(MediaElement shaper) throws RoomException;

    void revert(MediaElement shaper, boolean releaseElement) throws
      RoomException;

    void mute(MutedMediaType muteType);

    void unmute();

    PassThrough getPassThru();

    boolean isConnected();

    RecorderEndpoint getRecorderEndpoint();
}
