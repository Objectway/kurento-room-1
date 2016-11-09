package org.kurento.room;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class mantains a map from usernames to the sessionids (websocket), used for sending notifications.
 * The same username can have multiple connections (session ids).
 *
 * TODO: Check this class for thread-safe correctness!!
 */
@Component
public class UsernameSessionIdMapper {
    // Username -> List<SessionId>
    private ConcurrentHashMap<String, List<String>> map = new ConcurrentHashMap<>();

    public void add(final String username, final String sessionId) {
        if (!map.containsKey(username)) {
            final List<String> list = new ArrayList<String>();
            list.add(sessionId);
            map.put(username, list);
        } else {
            map.get(username).add(sessionId);
        }
    }

    public void removeByUsername(final String username) {
        map.remove(username);
    }

    public void removeBySessionId(final String sessionId) {
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            // ArrayList.contains() tests equals(), not object identity
            if (entry.getValue().contains(sessionId)) {
                entry.getValue().remove(sessionId);
                if (entry.getValue().isEmpty()) {
                    map.remove(entry.getKey());
                }
                break;
            }
        }
    }

    public List<String> getSessionIds(final String username) {
        return map.get(username);
    }

    public String getUsername(final String sessionId) {
        // ArrayList.contains() tests equals(), not object identity
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
            if (entry.getValue().contains(sessionId)) {
                return entry.getKey();
            }
        }

        return null;
    }
}
