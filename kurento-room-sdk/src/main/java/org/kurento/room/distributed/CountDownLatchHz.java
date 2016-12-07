package org.kurento.room.distributed;

import com.hazelcast.core.ICountDownLatch;
import org.kurento.room.distributed.interfaces.ICountDownLatchWrapper;

import java.util.concurrent.TimeUnit;

/**
 * Created by sturiale on 07/12/16.
 */
public class CountDownLatchHz implements ICountDownLatchWrapper {
    private ICountDownLatch countDownLatch;

    public CountDownLatchHz(ICountDownLatch countDownLatch) {
        this.countDownLatch = countDownLatch;
    }

    @Override
    public boolean await(long var1, TimeUnit var3) throws InterruptedException {
        return countDownLatch.await(var1,var3);
    }

    @Override
    public void countDown() {
        countDownLatch.countDown();
    }

    @Override
    public int getCount() {
        return countDownLatch.getCount();
    }

    @Override
    public boolean trySetCount(int var1) {
        return countDownLatch.trySetCount(var1);
    }
}
