package org.kurento.room.distributed.serializers;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;
import org.kurento.client.KurentoClient;
import org.kurento.room.api.MutedMediaType;
import org.kurento.room.distributed.model.DistributedRemoteObject;
import org.kurento.room.distributed.model.endpoint.DistributedPublisherEndpoint;
import org.kurento.room.distributed.model.endpoint.DistributedSubscriberEndpoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Created by sturiale on 05/12/16.
 */
@Component
public class DistributedSubscriberEndpointSerializer implements StreamSerializer<DistributedSubscriberEndpoint> {
    private static final int TYPE_ID = 4;

    @Autowired
    private ApplicationContext context;

    @Override
    public int getTypeId() {
        return TYPE_ID;
    }

    @Override
    public void write(ObjectDataOutput out, DistributedSubscriberEndpoint endpoint)
            throws IOException {
        //DistributedMediaEndpoint serialization
        DistributedRemoteObject webEndpointRemoteObj = DistributedRemoteObject.fromKurentoObject(endpoint.getWebEndpoint(),endpoint.getKmsUrl());
        DistributedRemoteObject rtpEndpointRemoteObj = DistributedRemoteObject.fromKurentoObject(endpoint.getRtpEndpoint(),endpoint.getKmsUrl());

        out.writeBoolean(endpoint.isWeb());
        out.writeBoolean(endpoint.isDataChannels());
        out.writeObject(webEndpointRemoteObj);
        out.writeObject(rtpEndpointRemoteObj);
        out.writeUTF(endpoint.getOwner().getRoom().getName());
        out.writeUTF(endpoint.getOwner().getId());
        out.writeUTF(endpoint.getEndpointName());
        out.writeUTF(endpoint.getMuteType().name());
        out.writeUTF(endpoint.getKmsUrl());

        //DistributedSubscriberEndpoint Serialization
        out.writeBoolean(endpoint.isConnectedToPublisher());
        out.writeUTF(endpoint.getOwner().getStreamIdFromPublisher(endpoint.getPublisher()));
    }

    @Override
    public DistributedSubscriberEndpoint read(ObjectDataInput in)
            throws IOException {
        //DistributedMediaEndpoint deserialization
        boolean web = in.readBoolean();
        boolean dataChannels = in.readBoolean();
        DistributedRemoteObject webEndpointRemoteObj = in.readObject();
        DistributedRemoteObject rtpEndpointRemoteObj = in.readObject();
        String roomName = in.readUTF();
        String participantId = in.readUTF();
        String endpointName = in.readUTF();
        MutedMediaType muteType = MutedMediaType.valueOf(in.readUTF());
        String kmsUrl = in.readUTF();

        //DistributedSubscriberEndpoint serialization
        boolean connectedToPublisher = in.readBoolean();
        String streamId = in.readUTF();

        KurentoClient client = KurentoClient.create(kmsUrl);
        return new DistributedSubscriberEndpoint(web,dataChannels,endpointName,kmsUrl,client,webEndpointRemoteObj,rtpEndpointRemoteObj,
                roomName,participantId,muteType,connectedToPublisher,streamId);
    }

    @Override
    public void destroy() {
    }
}

