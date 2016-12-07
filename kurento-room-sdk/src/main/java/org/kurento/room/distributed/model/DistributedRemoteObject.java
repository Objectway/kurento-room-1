package org.kurento.room.distributed.model;

import org.kurento.client.KurentoObject;

import java.io.Serializable;

/**
 * Created by sturiale on 05/12/16.
 */
public class DistributedRemoteObject implements Serializable {

    public static final String MEDIA_PIPELINE_CLASS_NAME  = "org.kurento.client.MediaPipeline";
    private String objectRef;
    private String className;
    private String kmsUri;

    public DistributedRemoteObject() {
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


    public static DistributedRemoteObject fromKurentoObject(KurentoObject object,String kmsUri){
        if(object==null){
            return null;
        }
        DistributedRemoteObject remoteObject = new DistributedRemoteObject();
        remoteObject.setClassName(object.getClass().getName());
        remoteObject.setObjectRef(object.getId());
        remoteObject.setKmsUri(kmsUri);
        return remoteObject;
    }
}
