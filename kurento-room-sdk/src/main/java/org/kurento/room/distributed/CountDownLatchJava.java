package org.kurento.room.distributed;

import org.kurento.room.distributed.interfaces.ICountDownLatchWrapper;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Created by sturiale on 07/12/16.
 */
public class CountDownLatchJava implements ICountDownLatchWrapper {

    private CountDownLatch countDownLatch;

    public CountDownLatchJava(CountDownLatch countDownLatch) {
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
        return (int)countDownLatch.getCount();
    }

    @Override
    public boolean trySetCount(int var1) {
        throw new NotImplementedException();
    }


}
