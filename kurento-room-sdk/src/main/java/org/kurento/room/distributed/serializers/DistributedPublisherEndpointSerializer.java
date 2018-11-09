package org.kurento.room.distributed.serializers;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;
import org.kurento.client.KurentoClient;
import org.kurento.room.api.KurentoClientProvider;
import org.kurento.room.api.MutedMediaType;
import org.kurento.room.api.pojo.KurentoRoomId;
import org.kurento.room.endpoint.DistributedPublisherEndpoint;
import org.kurento.room.distributed.model.DistributedRemoteObject;
import org.kurento.room.interfaces.IRoomManager;
import org.kurento.room.interfaces.KurentoMuxConnectionListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
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
    public void write(ObjectDataOutput out, DistributedPublisherEndpoint endpoint) throws IOException {
        // DistributedMediaEndpoint serialization
        final DistributedRemoteObject webEndpointRemoteObj = DistributedRemoteObject.fromKurentoObject(endpoint.getEndpoint(), DistributedRemoteObject.WEBRTCENDPOINT_CLASSNAME, endpoint.getKmsUrl());
        out.writeBoolean(endpoint.isDataChannels());
        out.writeObject(webEndpointRemoteObj);
        out.writeObject(endpoint.getOwner().getRoom().getId());
        out.writeUTF(endpoint.getOwner().getId());
        out.writeUTF(endpoint.getEndpointName());
        final MutedMediaType muteType = endpoint.getMuteType();
        out.writeObject((muteType != null) ? muteType.name() : null);
        out.writeUTF(endpoint.getKmsUrl());
        out.writeUTF(endpoint.getStreamId());

        // DistributedPublisherEndpointSerialization
        final DistributedRemoteObject passThruRemoteObj = DistributedRemoteObject.fromKurentoObject(endpoint.getPassThru(), DistributedRemoteObject.PASSTHROUGH_CLASSNAME, endpoint.getKmsUrl());
        final DistributedRemoteObject recEndpointRemoteObj = DistributedRemoteObject.fromKurentoObject(endpoint.getRecorderEndpoint(), DistributedRemoteObject.RECORDERENDPOINT_CLASSNAME, endpoint.getKmsUrl());
        final DistributedRemoteObject hubportRemoteObj = DistributedRemoteObject.fromKurentoObject(endpoint.getHubPort(), DistributedRemoteObject.HUBPORT_CLASSNAME, endpoint.getKmsUrl());
        out.writeObject(passThruRemoteObj);
        out.writeBoolean(endpoint.isConnected());
        out.writeObject(recEndpointRemoteObj);
        out.writeObject(endpoint.getCallStreamId());
        out.writeObject(hubportRemoteObj);
    }

    @Override
    public DistributedPublisherEndpoint read(ObjectDataInput in) throws IOException {
        // DistributedMediaEndpoint deserialization
        final boolean dataChannels = in.readBoolean();
        final DistributedRemoteObject webEndpointRemoteObj = in.readObject();
        final KurentoRoomId roomId = in.readObject();
        final String participantId = in.readUTF();
        final String endpointName = in.readUTF();

        final String muteTypeStr = in.readObject();
        final MutedMediaType muteType = (muteTypeStr != null) ? MutedMediaType.valueOf(muteTypeStr) : null;
        final String kmsUrl = in.readUTF();
        final String streamId = in.readUTF();

        // DistributedPublisherEndpoint serialization
        final DistributedRemoteObject passThruRemoteObj = in.readObject();
        final boolean connected = in.readBoolean();
        final DistributedRemoteObject recEndpointRemoteObj = in.readObject();
        final Long callStreamId = in.readObject();
        final DistributedRemoteObject hubportRemoteObj = in.readObject();

        final IRoomManager roomManager = context.getBean(IRoomManager.class);
        final KurentoMuxConnectionListener muxListener = new KurentoMuxConnectionListener(roomManager);
        final KurentoClient client = kmsManager.getKurentoClient(kmsUrl, muxListener);
        muxListener.setClient(client);
        return (DistributedPublisherEndpoint) context.getBean("distributedPublisherEndpoint", dataChannels, endpointName, kmsUrl, streamId, client, webEndpointRemoteObj,
                recEndpointRemoteObj, passThruRemoteObj, hubportRemoteObj, roomId, participantId, muteType, connected, callStreamId, roomManager);
    }

    @Override
    public void destroy() {
    }
}

