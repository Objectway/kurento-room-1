package org.kurento.room.config;

import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.kurento.room.api.MutedMediaType;
import org.kurento.room.distributed.DistributedParticipant;
import org.kurento.room.distributed.DistributedRoom;
import org.kurento.room.distributed.model.DistributedRemoteObject;
import org.kurento.room.distributed.model.endpoint.DistributedPublisherEndpoint;
import org.kurento.room.distributed.model.endpoint.DistributedSubscriberEndpoint;
import org.kurento.room.interfaces.IRoomManager;
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
    public DistributedRoom distributedRoom(final String roomName, final KurentoClient kurentoClient, final boolean destroyKurentoClient, final boolean closed,
                                           final DistributedRemoteObject pipelineInfo,
                                           final DistributedRemoteObject compositeInfo,
                                           final DistributedRemoteObject hubportInfo,
                                           final DistributedRemoteObject recorderInfo) {
        return new DistributedRoom(roomName, kurentoClient, destroyKurentoClient, closed, pipelineInfo, compositeInfo, hubportInfo, recorderInfo);
    }

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
        return new DistributedPublisherEndpoint(web, dataChannels, endpointName, kmsUrl, streamId, kurentoClient, webEndpointInfo,
                rtpEndpointInfo, recEndpointInfo, passThrouInfo, hubportInfo, roomName, participantId, muteType, connected, callStreamId, roomManager);
    }

    @Bean
    @Scope("prototype")
    public DistributedPublisherEndpoint distributedPublisherEndpoint(boolean web, boolean dataChannels, DistributedParticipant owner,
                                                                     String endpointName, MediaPipeline pipeline, String kmsUrl, String streamId) {
        return new DistributedPublisherEndpoint(web, dataChannels, owner, endpointName, pipeline, kmsUrl, streamId);
    }


    @Bean
    @Scope("prototype")
    public DistributedSubscriberEndpoint distributedSubscriberEndpoint(boolean web,
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
        return new DistributedSubscriberEndpoint(web, dataChannels, endpointName, kmsUrl, streamId, kurentoClient, webEndpointInfo,
                rtpEndpointInfo, roomName, participantId, muteType, connectedToPublisher, roomManager);
    }

    @Bean
    @Scope("prototype")
    public DistributedSubscriberEndpoint distributedSubscriberEndpoint(boolean web, DistributedParticipant owner, String endpointName,
                                                                       MediaPipeline pipeline, String kmsUrl, String streamId) {
        return new DistributedSubscriberEndpoint(web, owner, endpointName, pipeline, kmsUrl, streamId);
    }
}
