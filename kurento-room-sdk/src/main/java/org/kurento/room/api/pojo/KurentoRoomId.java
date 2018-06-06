package org.kurento.room.api.pojo;

import java.io.Serializable;

/**
 * Created by Sebastiano Motta on 05/06/2018.
 */

public class KurentoRoomId implements Serializable {

	private static final long serialVersionUID = 1L;

	private String tenant;
	private String roomName;

	public KurentoRoomId(String tenant, String roomName) {
		this.tenant = tenant;
		this.roomName = roomName;
	}

	public String getTenant() {
		return tenant;
	}

	public String getRoomName() {
		return roomName;
	}

	@Override public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof KurentoRoomId))
			return false;

		KurentoRoomId roomId = (KurentoRoomId) o;

		if (tenant != null ? !tenant.equals(roomId.tenant) : roomId.tenant != null)
			return false;
		return roomName != null ? roomName.equals(roomId.roomName) : roomId.roomName == null;
	}

	@Override public int hashCode() {
		int result = tenant != null ? tenant.hashCode() : 0;
		result = 31 * result + (roomName != null ? roomName.hashCode() : 0);
		return result;
	}

	@Override public String toString() {
		return "[" + roomName + " on tenant " + tenant + "]";
	}
}
