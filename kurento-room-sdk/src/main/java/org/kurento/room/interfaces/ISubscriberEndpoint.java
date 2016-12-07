package org.kurento.room.interfaces;

import org.kurento.room.api.MutedMediaType;


/**
 * Created by sturiale on 07/12/16.
 */
public interface ISubscriberEndpoint extends IMediaEndpoint {
    String subscribe(String sdpOffer, IPublisherEndpoint publisher);

    boolean isConnectedToPublisher();

    void setConnectedToPublisher(boolean connectedToPublisher);

    IPublisherEndpoint getPublisher();

    void setPublisher(IPublisherEndpoint publisher);

    @Override
    void mute(MutedMediaType muteType);

    @Override
    void unmute();
}
