package org.kurento.room.distributed.serializers;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;
import org.kurento.client.KurentoClient;
import org.kurento.room.api.MutedMediaType;
import org.kurento.room.distributed.model.endpoint.DistributedPublisherEndpoint;
import org.kurento.room.distributed.model.DistributedRemoteObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Created by sturiale on 05/12/16.
 */
@Component
public class DistributedPublisherEndpointSerializer implements StreamSerializer<DistributedPublisherEndpoint> {
    private static final int TYPE_ID = 3;

    @Autowired
    private ApplicationContext context;

    @Override
    public int getTypeId() {
        return TYPE_ID;
    }

    @Override
    public void write(ObjectDataOutput out, DistributedPublisherEndpoint endpoint)
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

        //DistributedPublisherEndpointSerialization
        DistributedRemoteObject passThruRemoteObj = DistributedRemoteObject.fromKurentoObject(endpoint.getPassThru(),endpoint.getKmsUrl());
        DistributedRemoteObject recEndpointRemoteObj = DistributedRemoteObject.fromKurentoObject(endpoint.getRecorderEndpoint(),endpoint.getKmsUrl());
        out.writeObject(passThruRemoteObj);
        out.writeBoolean(endpoint.isConnected());
        out.writeObject(recEndpointRemoteObj);
        out.writeLong(endpoint.getCallStreamId());

    }

    @Override
    public DistributedPublisherEndpoint read(ObjectDataInput in)
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

        //DistributedPublisherEndpoint serialization
        DistributedRemoteObject passThruRemoteObj = in.readObject();
        boolean connected = in.readBoolean();
        DistributedRemoteObject recEndpointRemoteObj = in.readObject();
        Long callStreamId = in.readLong();

        KurentoClient client = KurentoClient.create(kmsUrl);
        return new DistributedPublisherEndpoint(web,dataChannels,endpointName,kmsUrl,client,webEndpointRemoteObj,rtpEndpointRemoteObj,
                recEndpointRemoteObj,passThruRemoteObj,roomName,participantId,muteType,connected,callStreamId);
    }

    @Override
    public void destroy() {
    }
}

