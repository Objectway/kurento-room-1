package org.kurento.room.distributed.serializers;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;
import org.kurento.client.KurentoClient;
import org.kurento.room.api.KurentoClientProvider;
import org.kurento.room.DistributedRoomManager;
import org.kurento.room.distributed.model.DistributedRemoteObject;
import org.kurento.room.internal.DistributedRoom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Created by sturiale on 05/12/16.
 */
@Component
public class DistributedRoomSerializer implements StreamSerializer<DistributedRoom> {
    private static final int TYPE_ID = 1;

    @Autowired
    private KurentoClientProvider kmsManager;

    @Autowired
    private ApplicationContext context;

    @Override
    public int getTypeId() {
        return TYPE_ID;
    }

    @Override
    public void write(ObjectDataOutput out, DistributedRoom distributedRoom)
            throws IOException {
        out.writeUTF(distributedRoom.getName());
        out.writeUTF(distributedRoom.getKmsUri());
        out.writeBoolean(distributedRoom.getDestroyKurentoClient());
        out.writeBoolean(distributedRoom.isClosed());
        out.writeObject(DistributedRemoteObject.fromKurentoObject(distributedRoom.getPipeline(), DistributedRemoteObject.MEDIAPIPELINE_CLASSNAME, distributedRoom.getKmsUri()));
        out.writeObject(DistributedRemoteObject.fromKurentoObject(distributedRoom.getComposite(), DistributedRemoteObject.COMPOSITE_CLASSNAME, distributedRoom.getKmsUri()));
        out.writeObject(DistributedRemoteObject.fromKurentoObject(distributedRoom.getHubPort(), DistributedRemoteObject.HUBPORT_CLASSNAME, distributedRoom.getKmsUri()));
        out.writeObject(DistributedRemoteObject.fromKurentoObject(distributedRoom.getRecorderEndpoint(), DistributedRemoteObject.RECORDERENDPOINT_CLASSNAME, distributedRoom.getKmsUri()));
    }

    @Override
    public DistributedRoom read(ObjectDataInput in)
            throws IOException {
        String roomName = in.readUTF();
        String kmsUri = in.readUTF();
        boolean destroyKurentoClient = in.readBoolean();
        boolean closed = in.readBoolean();
        DistributedRemoteObject pipelineInfo = (DistributedRemoteObject) in.readObject();
        DistributedRemoteObject compositeInfo = (DistributedRemoteObject) in.readObject();
        DistributedRemoteObject hubPortInfo = (DistributedRemoteObject) in.readObject();
        DistributedRemoteObject recorderInfo = (DistributedRemoteObject) in.readObject();

        KurentoClient client = kmsManager.getKurentoClient(kmsUri);
        DistributedRoom distributedRoom = (DistributedRoom) context.getBean("distributedRoom", roomName, client, destroyKurentoClient, closed, pipelineInfo, compositeInfo, hubPortInfo, recorderInfo);
        distributedRoom.setListener((DistributedRoomManager) context.getBean("roomManager"));
        return distributedRoom;
    }

    @Override
    public void destroy() {
    }
}

