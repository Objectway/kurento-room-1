package org.kurento.room.distributed.interfaces;

import java.util.Objects;

/**
 * Created by sturiale on 06/12/16.
 */
public interface IChangeListener<T> {
    void onChange(T object);
}
