package org.kurento.room.distributed.model;

import org.kurento.client.IceCandidate;
import org.kurento.client.internal.server.Param;

import java.io.Serializable;

/**
 * Created by sturiale on 06/12/16.
 */
public class DistributedIceCandidate extends IceCandidate implements Serializable {
    public DistributedIceCandidate() {
    }

    public DistributedIceCandidate(@Param("candidate") String candidate, @Param("sdpMid") String sdpMid, @Param("sdpMLineIndex") int sdpMLineIndex) {
        super(candidate, sdpMid, sdpMLineIndex);
    }
}
