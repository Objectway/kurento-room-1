package org.kurento.room.distributed.serializers;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;
import org.kurento.client.KurentoClient;
import org.kurento.room.distributed.model.DistributedRemoteObject;
import org.kurento.room.distributed.DistributedRoom;
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
        out.writeObject(DistributedRemoteObject.fromKurentoObject(distributedRoom.getPipeline(),distributedRoom.getKmsUri()));
    }

    @Override
    public DistributedRoom read(ObjectDataInput in)
            throws IOException {
        String roomName = in.readUTF();
        String kmsUri = in.readUTF();
        boolean destroyKurentoClient = in.readBoolean();
        boolean closed= in.readBoolean();
        DistributedRemoteObject pipelineInfo = (DistributedRemoteObject) in.readObject();
        KurentoClient client = KurentoClient.create(kmsUri);

        DistributedRoom distributedRoom = (DistributedRoom) context.getBean("distributedRoom",roomName,client,destroyKurentoClient,closed,pipelineInfo);

        return distributedRoom;
    }

    @Override
    public void destroy() {
    }
}

