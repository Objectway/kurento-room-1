package org.kurento.room.distributed.serializers;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.StreamSerializer;
import org.kurento.room.api.pojo.KurentoRoomId;
import org.kurento.room.interfaces.IRoomManager;
import org.kurento.room.internal.DistributedParticipant;
import org.kurento.room.internal.DistributedRoom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Created by sturiale on 05/12/16.
 */
@Component
public class DistributedParticipantSerializer implements StreamSerializer<DistributedParticipant> {
    private static final int TYPE_ID = 2;

    @Autowired
    private ApplicationContext context;

    @Override
    public int getTypeId() {
        return TYPE_ID;
    }

    @Override
    public void write(ObjectDataOutput out, DistributedParticipant distributedParticipant)
            throws IOException {
        out.writeUTF(distributedParticipant.getId());
        out.writeUTF(distributedParticipant.getName());
        out.writeObject(distributedParticipant.getRoom().getId());
        out.writeBoolean(distributedParticipant.isDataChannels());
    }

    @Override
    public DistributedParticipant read(ObjectDataInput in)
            throws IOException {
        String id = in.readUTF();
        String name = in.readUTF();
        IRoomManager roomManager = (IRoomManager) context.getBean("roomManager");
        KurentoRoomId roomId = in.readObject();
        DistributedRoom room = (DistributedRoom) roomManager.getRoomById(roomId);
        boolean dataChannels = in.readBoolean();
        DistributedParticipant distributedParticipant = (DistributedParticipant) context.getBean("distributedParticipant", id, name, room, dataChannels);

        return distributedParticipant;
    }

    @Override
    public void destroy() {
    }
}

