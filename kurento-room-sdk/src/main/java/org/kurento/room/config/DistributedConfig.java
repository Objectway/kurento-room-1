package org.kurento.room.config;

import org.kurento.client.KurentoClient;
import org.kurento.room.api.MutedMediaType;
import org.kurento.room.distributed.DistributedParticipant;
import org.kurento.room.distributed.DistributedRoom;
import org.kurento.room.distributed.model.DistributedRemoteObject;
import org.kurento.room.distributed.model.endpoint.DistributedPublisherEndpoint;
import org.kurento.room.distributed.model.endpoint.DistributedSubscriberEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * Created by sturiale on 05/12/16.
 */
@Configuration
public class DistributedConfig {
    @Bean
    @Scope("prototype")
    public DistributedRoom distributedRoom(final String roomName, final KurentoClient kurentoClient, final boolean destroyKurentoClient) {
        return new DistributedRoom(roomName, kurentoClient, destroyKurentoClient);
    }

    @Bean
    @Scope("prototype")
    public DistributedParticipant distributedParticipant(String id, String name, DistributedRoom room, boolean dataChannels, boolean web) {
        return new DistributedParticipant(id, name, room, dataChannels, web);
    }

    @Bean
    @Scope("prototype")
    public DistributedPublisherEndpoint distributedPublisherEndpoint(boolean web,
                                                                     boolean dataChannels,
                                                                     String endpointName,
                                                                     String kmsUrl,
                                                                     KurentoClient kurentoClient,
                                                                     DistributedRemoteObject webEndpointInfo,
                                                                     DistributedRemoteObject rtpEndpointInfo,
                                                                     DistributedRemoteObject recEndpointInfo,
                                                                     DistributedRemoteObject passThrouInfo,
                                                                     String roomName,
                                                                     String participantId,
                                                                     MutedMediaType muteType,
                                                                     boolean connected,
                                                                     Long callStreamId) {
        return new DistributedPublisherEndpoint(web,dataChannels,endpointName,kmsUrl,kurentoClient,webEndpointInfo,
                rtpEndpointInfo,recEndpointInfo,passThrouInfo,roomName,participantId,muteType,connected,callStreamId);
    }

    @Bean
    @Scope("prototype")
    public DistributedSubscriberEndpoint distributedSubscriberEndpoint(boolean web,
                                                                       boolean dataChannels,
                                                                       String endpointName,
                                                                       String kmsUrl,
                                                                       KurentoClient kurentoClient,
                                                                       DistributedRemoteObject webEndpointInfo,
                                                                       DistributedRemoteObject rtpEndpointInfo,
                                                                       String roomName,
                                                                       String participantId,
                                                                       MutedMediaType muteType,
                                                                       boolean connectedToPublisher,
                                                                       String streamId) {
        return new DistributedSubscriberEndpoint(web,dataChannels,endpointName,kmsUrl,kurentoClient,webEndpointInfo,
                rtpEndpointInfo,roomName,participantId,muteType,connectedToPublisher,streamId);
    }
}
