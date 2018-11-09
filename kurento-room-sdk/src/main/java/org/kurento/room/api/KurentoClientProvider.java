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

package org.kurento.room.api;

import org.kurento.client.KurentoClient;
import org.kurento.client.KurentoConnectionListener;
import org.kurento.room.exception.RoomException;

/**
 * This service interface was designed so that the room manager could obtain a {@link KurentoClient}
 * instance at any time, without requiring knowledge about the placement of the media server
 * instances. It is left for the developer to provide an implementation for this API.
 *
 * @author <a href="mailto:rvlad@naevatec.com">Radu Tom Vlad</a>
 */
public interface KurentoClientProvider {

  /**
   * Obtains a {@link KurentoClient} instance given the custom session bean. Normally, it'd be
   * called during a room's instantiation.
   *
   * @param sessionInfo custom information object required by the implementors of this interface
   * @param listener an optional connection listener (may be null)
   * @return the {@link KurentoClient} instance
   * @throws RoomException
   *           in case there is an error obtaining a {@link KurentoClient} instance
   */
  KurentoClient getKurentoClient(final KurentoClientSessionInfo sessionInfo, final KurentoConnectionListener listener) throws RoomException;

  /**
   * Returns a KurentoClient linked to the specified Kms.
   * @param kmsUri
   * @param listener an optional connection listener (may be null)
   * @return
   * @throws RoomException
   */
  KurentoClient getKurentoClient(final String kmsUri, final KurentoConnectionListener listener) throws RoomException;

  boolean destroyWhenUnused();
}
