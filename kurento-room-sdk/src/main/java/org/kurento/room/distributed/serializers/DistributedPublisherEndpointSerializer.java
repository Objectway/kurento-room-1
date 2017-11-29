package org.kurento.room.distributed.serializers;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;
import org.kurento.client.KurentoClient;
import org.kurento.room.api.KurentoClientProvider;
import org.kurento.room.api.MutedMediaType;
import org.kurento.room.distributed.model.endpoint.DistributedPublisherEndpoint;
import org.kurento.room.distributed.model.DistributedRemoteObject;
import org.kurento.room.interfaces.IRoomManager;
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
    private KurentoClientProvider kmsManager;

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
        DistributedRemoteObject webEndpointRemoteObj = DistributedRemoteObject.fromKurentoObject(endpoint.getWebEndpoint(), DistributedRemoteObject.WEBRTCENDPOINT_CLASSNAME, endpoint.getKmsUrl());
        DistributedRemoteObject rtpEndpointRemoteObj = DistributedRemoteObject.fromKurentoObject(endpoint.getRtpEndpoint(), DistributedRemoteObject.RTPENDPOINT_CLASSNAME, endpoint.getKmsUrl());

        out.writeBoolean(endpoint.isWeb());
        out.writeBoolean(endpoint.isDataChannels());
        out.writeObject(webEndpointRemoteObj);
        out.writeObject(rtpEndpointRemoteObj);
        out.writeUTF(endpoint.getOwner().getRoom().getName());
        out.writeUTF(endpoint.getOwner().getId());
        out.writeUTF(endpoint.getEndpointName());
        MutedMediaType muteType = endpoint.getMuteType();

        out.writeObject((muteType != null) ? muteType.name() : null);
        out.writeUTF(endpoint.getKmsUrl());
        out.writeUTF(endpoint.getStreamId());

        //DistributedPublisherEndpointSerialization
        DistributedRemoteObject passThruRemoteObj = DistributedRemoteObject.fromKurentoObject(endpoint.getPassThru(), DistributedRemoteObject.PASSTHROUGH_CLASSNAME, endpoint.getKmsUrl());
        DistributedRemoteObject recEndpointRemoteObj = DistributedRemoteObject.fromKurentoObject(endpoint.getRecorderEndpoint(), DistributedRemoteObject.RECORDERENDPOINT_CLASSNAME, endpoint.getKmsUrl());
        out.writeObject(passThruRemoteObj);
        out.writeBoolean(endpoint.isConnected());
        out.writeObject(recEndpointRemoteObj);
        out.writeObject(endpoint.getCallStreamId());

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

        String muteTypeStr = in.readObject();
        MutedMediaType muteType = (muteTypeStr != null) ? MutedMediaType.valueOf(muteTypeStr) : null;
        String kmsUrl = in.readUTF();
        String streamId = in.readUTF();

        //DistributedPublisherEndpoint serialization
        DistributedRemoteObject passThruRemoteObj = in.readObject();
        boolean connected = in.readBoolean();
        DistributedRemoteObject recEndpointRemoteObj = in.readObject();
        Long callStreamId = in.readObject();

        KurentoClient client = kmsManager.getKurentoClient(kmsUrl);

        IRoomManager roomManager = (IRoomManager) context.getBean("roomManager");

        return (DistributedPublisherEndpoint) context.getBean("distributedPublisherEndpoint", web, dataChannels, endpointName, kmsUrl, streamId, client, webEndpointRemoteObj, rtpEndpointRemoteObj,
                recEndpointRemoteObj, passThruRemoteObj, roomName, participantId, muteType, connected, callStreamId, roomManager);
    }

    @Override
    public void destroy() {
    }
}

