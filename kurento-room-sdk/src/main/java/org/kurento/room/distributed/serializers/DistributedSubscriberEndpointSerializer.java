package org.kurento.room.distributed.serializers;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;
import org.kurento.client.KurentoClient;
import org.kurento.room.api.KurentoClientProvider;
import org.kurento.room.api.MutedMediaType;
import org.kurento.room.api.pojo.KurentoRoomId;
import org.kurento.room.distributed.model.DistributedRemoteObject;
import org.kurento.room.endpoint.DistributedSubscriberEndpoint;
import org.kurento.room.interfaces.IRoomManager;
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
    private KurentoClientProvider kmsManager;

    @Autowired
    private ApplicationContext context;

    @Override
    public int getTypeId() {
        return TYPE_ID;
    }

    @Override
    public void write(ObjectDataOutput out, DistributedSubscriberEndpoint endpoint) throws IOException {
        // DistributedMediaEndpoint serialization
        final  DistributedRemoteObject webEndpointRemoteObj = DistributedRemoteObject.fromKurentoObject(endpoint.getEndpoint(), DistributedRemoteObject.WEBRTCENDPOINT_CLASSNAME, endpoint.getKmsUrl());
        out.writeBoolean(endpoint.isDataChannels());
        out.writeObject(webEndpointRemoteObj);
        out.writeObject(endpoint.getOwner().getRoom().getId());
        out.writeUTF(endpoint.getOwner().getId());
        out.writeUTF(endpoint.getEndpointName());
        final MutedMediaType muteType = endpoint.getMuteType();
        out.writeObject((muteType != null) ? muteType.name() : null);
        out.writeUTF(endpoint.getKmsUrl());
        out.writeUTF(endpoint.getStreamId());

        // DistributedSubscriberEndpoint Serialization
        out.writeBoolean(endpoint.isConnectedToPublisher());
    }

    @Override
    public DistributedSubscriberEndpoint read(ObjectDataInput in) throws IOException {
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

        // DistributedSubscriberEndpoint serialization
        final boolean connectedToPublisher = in.readBoolean();
        final KurentoClient client = kmsManager.getKurentoClient(kmsUrl);
        final IRoomManager roomManager = context.getBean(IRoomManager.class);
        return (DistributedSubscriberEndpoint) context.getBean("distributedSubscriberEndpoint", dataChannels, endpointName, kmsUrl, streamId, client, webEndpointRemoteObj,
                roomId, participantId, muteType, connectedToPublisher, roomManager);
    }

    @Override
    public void destroy() {
    }
}

