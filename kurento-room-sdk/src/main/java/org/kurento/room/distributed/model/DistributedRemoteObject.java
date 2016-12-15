package org.kurento.room.distributed.model;

import org.kurento.client.KurentoObject;

import java.io.Serializable;

/**
 * Created by sturiale on 05/12/16.
 */
public class DistributedRemoteObject implements Serializable {
    private static final long serialVersionUID = 2L;

    public static final String MEDIAPIPELINE_CLASSNAME  = "org.kurento.client.MediaPipeline";
    public static final String WEBRTCENDPOINT_CLASSNAME  = "org.kurento.client.WebRtcEndpoint";
    public static final String RTPENDPOINT_CLASSNAME  = "org.kurento.client.RtpEndpoint";
    public static final String PASSTHROUGH_CLASSNAME  = "org.kurento.client.PassThrough";
    public static final String RECORDERENDPOINT_CLASSNAME  = "org.kurento.client.RecorderEndpoint";

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
