package org.kurento.room.distributed.interfaces;

import java.util.concurrent.TimeUnit;

/**
 * Created by sturiale on 07/12/16.
 */
public interface ICountDownLatchWrapper {
    boolean await(long var1, TimeUnit var3) throws InterruptedException;

    void countDown();

    int getCount();

    boolean trySetCount(int var1);
}
