package org.kurento.room.internal;

import org.kurento.client.KurentoClient;
import org.kurento.jsonrpc.client.AbstractJsonRpcClientWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.net.URI;

/**
 * Created by sturiale on 05/12/16.
 */
public class ReflectionUtils {
    private final static Logger logger = LoggerFactory.getLogger(ReflectionUtils.class);
    public static String getKmsUri(KurentoClient client) {
        try {
            Field field = client.getClass().getDeclaredField("client");
            field.setAccessible(true);
            AbstractJsonRpcClientWebSocket jsonRpcClient = (AbstractJsonRpcClientWebSocket)field.get(client);
            return ReflectionUtils.getKmsUri(jsonRpcClient).toString();
        } catch (Exception e) {
            logger.error("Error during obtain kurento internal kmsUri",e);
        }
        return null;
    }

    public static URI getKmsUri(AbstractJsonRpcClientWebSocket rpcClient) {
        try {
            Field field = rpcClient.getClass().getSuperclass().getDeclaredField("uri");
            field.setAccessible(true);
            URI uri = (URI)field.get(rpcClient);
            return uri;
        } catch (Exception e) {
            logger.error("Error during obtain kurento internal kmsUriString",e);
        }
        return null;
    }

}
