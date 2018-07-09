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
    public void write(ObjectDataOutput out, DistributedParticipant distributedParticipant) throws IOException {
        out.writeUTF(distributedParticipant.getId());
        out.writeUTF(distributedParticipant.getName());
        out.writeObject(distributedParticipant.getRoom().getId());
        out.writeBoolean(distributedParticipant.isDataChannels());
    }

    @Override
    public DistributedParticipant read(ObjectDataInput in) throws IOException {
        final String id = in.readUTF();
        final String name = in.readUTF();
        final KurentoRoomId roomId = in.readObject();
        final boolean dataChannels = in.readBoolean();

        final IRoomManager roomManager = context.getBean(IRoomManager.class);
        final DistributedRoom room = (DistributedRoom) roomManager.getRoomById(roomId);
        final DistributedParticipant distributedParticipant = (DistributedParticipant) context.getBean("distributedParticipant", id, name, room, dataChannels);
        return distributedParticipant;
    }

    @Override
    public void destroy() {
    }
}

