package org.kurento.room.distributed.model;

import org.kurento.client.KurentoClient;
import org.kurento.client.KurentoObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

/**
 * Created by sturiale on 05/12/16.
 */
public class DistributedRemoteObject implements Serializable {
    private static final Logger logger = LoggerFactory.getLogger(DistributedRemoteObject.class);

    private static final long serialVersionUID = 2L;

    public static final String MEDIAPIPELINE_CLASSNAME  = "org.kurento.client.MediaPipeline";
    public static final String WEBRTCENDPOINT_CLASSNAME  = "org.kurento.client.WebRtcEndpoint";
    public static final String RTPENDPOINT_CLASSNAME  = "org.kurento.client.RtpEndpoint";
    public static final String PASSTHROUGH_CLASSNAME  = "org.kurento.client.PassThrough";
    public static final String RECORDERENDPOINT_CLASSNAME  = "org.kurento.client.RecorderEndpoint";
    public static final String COMPOSITE_CLASSNAME = "org.kurento.client.Composite";
    public static final String HUBPORT_CLASSNAME = "org.kurento.client.HubPort";

    private String objectRef;
    private String className;
    private String kmsUri;

    public DistributedRemoteObject() {
    }

    /**
     * Builds a DistributedRemoteObject from a KurentoObject.
     * Note: KurentoObject could be a com.sun.proxy.$Proxy object, therefore we need to pass the className explicitly from the extern!
     *
     * @param object
     * @param className One of the *_CONSTANTS defined here.
     * @param kmsUri
     * @return
     */
    public static DistributedRemoteObject fromKurentoObject(final KurentoObject object, final String className, final String kmsUri) {
        if (object == null) {
            return null;
        }

        final DistributedRemoteObject remoteObject = new DistributedRemoteObject();
        remoteObject.setClassName(className);
        remoteObject.setObjectRef(object.getId());
        remoteObject.setKmsUri(kmsUri);

        return remoteObject;
    }

    /**
     * Reconstructs a remote KurentoObject from the info
     * @param info
     * @param kurentoClient
     * @param <T>
     * @return
     */
    public static <T extends KurentoObject> T retrieveFromInfo(final DistributedRemoteObject info, final KurentoClient kurentoClient) {
        if (info != null) {
            try {
                final Class<KurentoObject> clazz = (Class<KurentoObject>) Class.forName(info.getClassName());

                // We always have a KurentoObject as a result, even if it does not exist in the KMS
                return (T)kurentoClient.getById(info.getObjectRef(), clazz);
            } catch (ClassNotFoundException ex) {
                logger.error(ex.toString());
            } catch (Exception e) {
                // Try to invoke this endpoint with objectRef ending in ".MediaPipelinez" to trigger
                //      org.kurento.client.internal.server.ProtocolException: Exception creating Java Class for 'kurento.MediaPipelinez'
                logger.error(e.toString());
            }
        }

        return null;
    }

    public String getObjectRef() {
        return objectRef;
    }

    public void setObjectRef(String objectRef) {
        this.objectRef = objectRef;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getKmsUri() {
        return kmsUri;
    }

    public void setKmsUri(String kmsUri) {
        this.kmsUri = kmsUri;
    }
}
