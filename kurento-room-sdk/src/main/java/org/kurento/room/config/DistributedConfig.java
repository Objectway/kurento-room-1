package org.kurento.room.config;

import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.kurento.room.api.MutedMediaType;
import org.kurento.room.api.pojo.KurentoRoomId;
import org.kurento.room.internal.DistributedParticipant;
import org.kurento.room.internal.DistributedRoom;
import org.kurento.room.distributed.model.DistributedRemoteObject;
import org.kurento.room.endpoint.DistributedPublisherEndpoint;
import org.kurento.room.endpoint.DistributedSubscriberEndpoint;
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
    public DistributedRoom distributedRoom(final KurentoRoomId roomId, final KurentoClient kurentoClient, final boolean destroyKurentoClient, final boolean closed,
                                           final DistributedRemoteObject pipelineInfo,
                                           final DistributedRemoteObject compositeInfo,
                                           final DistributedRemoteObject hubportInfo,
                                           final DistributedRemoteObject recorderInfo) {
        return new DistributedRoom(roomId, kurentoClient, destroyKurentoClient, closed, pipelineInfo, compositeInfo, hubportInfo, recorderInfo);
    }

    @Bean
    @Scope("prototype")
    public DistributedRoom distributedRoom(final KurentoRoomId roomId, final KurentoClient kurentoClient, final boolean destroyKurentoClient) {
        return new DistributedRoom(roomId, kurentoClient, destroyKurentoClient);
    }

    @Bean
    @Scope("prototype")
    public DistributedParticipant distributedParticipant(String id, String name, DistributedRoom room, boolean dataChannels) {
        return new DistributedParticipant(id, name, room, dataChannels);
    }

    @Bean
    @Scope("prototype")
    public DistributedPublisherEndpoint distributedPublisherEndpoint(boolean dataChannels,
                                                                     String endpointName,
                                                                     String kmsUrl,
                                                                     String streamId,
                                                                     KurentoClient kurentoClient,
                                                                     DistributedRemoteObject webEndpointInfo,
                                                                     DistributedRemoteObject recEndpointInfo,
                                                                     DistributedRemoteObject passThrouInfo,
                                                                     DistributedRemoteObject hubportInfo,
                                                                     KurentoRoomId roomId,
                                                                     String participantId,
                                                                     MutedMediaType muteType,
                                                                     boolean connected,
                                                                     Long callStreamId,
                                                                     IRoomManager roomManager) {
        return new DistributedPublisherEndpoint(dataChannels, endpointName, kmsUrl, streamId, kurentoClient, webEndpointInfo,
                recEndpointInfo, passThrouInfo, hubportInfo, roomId, participantId, muteType, connected, callStreamId, roomManager);
    }

    @Bean
    @Scope("prototype")
    public DistributedPublisherEndpoint distributedPublisherEndpoint(boolean dataChannels, DistributedParticipant owner,
                                                                     String endpointName, MediaPipeline pipeline, String kmsUrl, String streamId) {
        return new DistributedPublisherEndpoint(dataChannels, owner, endpointName, pipeline, kmsUrl, streamId);
    }


    @Bean
    @Scope("prototype")
    public DistributedSubscriberEndpoint distributedSubscriberEndpoint(boolean dataChannels,
                                                                       String endpointName,
                                                                       String kmsUrl,
                                                                       String streamId,
                                                                       KurentoClient kurentoClient,
                                                                       DistributedRemoteObject webEndpointInfo,
                                                                       KurentoRoomId roomId,
                                                                       String participantId,
                                                                       MutedMediaType muteType,
                                                                       boolean connectedToPublisher,
                                                                       IRoomManager roomManager) {
        return new DistributedSubscriberEndpoint(dataChannels, endpointName, kmsUrl, streamId, kurentoClient, webEndpointInfo,
                roomId, participantId, muteType, connectedToPublisher, roomManager);
    }

    @Bean
    @Scope("prototype")
    public DistributedSubscriberEndpoint distributedSubscriberEndpoint(DistributedParticipant owner, String endpointName,
                                                                       MediaPipeline pipeline, String kmsUrl, String streamId) {
        return new DistributedSubscriberEndpoint(owner, endpointName, pipeline, kmsUrl, streamId);
    }
}
