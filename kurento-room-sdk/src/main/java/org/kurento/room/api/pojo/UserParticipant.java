/*
 * (C) Copyright 2015 Kurento (http://kurento.org/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kurento.room.api.pojo;

/**
 * This POJO holds information about a room participant.
 *
 * @author <a href="mailto:rvlad@naevatec.com">Radu Tom Vlad</a>
 *
 */
public class UserParticipant {
  private String participantId;
  private String userName;
  private boolean streaming = false;
  private String tenant;

  public UserParticipant(String participantId, String userName, String tenant, boolean streaming) {
    super();
    this.participantId = participantId;
    this.userName = userName;
    this.tenant = tenant;
    this.streaming = streaming;
  }

  public UserParticipant(String participantId, String userName, String tenant) {
    super();
    this.participantId = participantId;
    this.userName = userName;
    this.tenant = tenant;
  }

  public String getParticipantId() {
    return participantId;
  }

  public void setParticipantId(String participantId) {
    this.participantId = participantId;
  }

  public String getUserName() {
    return userName;
  }

  public void setUserName(String userName) {
    this.userName = userName;
  }

  public boolean isStreaming() {
    return streaming;
  }

  public void setStreaming(boolean streaming) {
    this.streaming = streaming;
  }

  public String getTenant() {
    return tenant;
  }

  public void setTenant(String tenant) {
    this.tenant = tenant;
  }

  @Override public int hashCode() {
    int result = participantId != null ? participantId.hashCode() : 0;
    result = 31 * result + (userName != null ? userName.hashCode() : 0);
    result = 31 * result + (streaming ? 1 : 0);
    result = 31 * result + (tenant != null ? tenant.hashCode() : 0);
    return result;
  }

  @Override public boolean equals(Object o) {
    if (this == o)
      return true;
    if (!(o instanceof UserParticipant))
      return false;

    UserParticipant that = (UserParticipant) o;

    if (streaming != that.streaming)
      return false;
    if (participantId != null ? !participantId.equals(that.participantId) : that.participantId != null)
      return false;
    if (userName != null ? !userName.equals(that.userName) : that.userName != null)
      return false;
    return tenant != null ? tenant.equals(that.tenant) : that.tenant == null;
  }

  @Override public String toString() {
    final StringBuilder sb = new StringBuilder("UserParticipant{");
    sb.append("participantId='").append(participantId).append('\'');
    sb.append(", userName='").append(userName).append('\'');
    sb.append(", streaming=").append(streaming);
    sb.append(", tenant='").append(tenant).append('\'');
    sb.append('}');
    return sb.toString();
  }
}
