package org.kurento.room;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class mantains che map from a username to the sessionid (websocket), used for sending notifications.
 */
@Component
public class UsernameSessionIdMapper {
    // Username -> SessionId
    private ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();

    public void add(final String username, final String sessionId) {
        map.put(username, sessionId);
    }

    public void removeByUsername(final String username) {
        map.remove(username);
    }

    public void removeBySessionId(final String sessionId) {
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (entry.getValue().equals(sessionId)) {
                map.remove(entry.getKey());
                break;
            }
        }
    }

    public String getSessionId(final String username) {
        return map.get(username);
    }

    public String getUsername(final String sessionId) {
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (entry.getValue().equals(sessionId)) {
                return entry.getKey();
            }
        }

        return null;
    }
}
