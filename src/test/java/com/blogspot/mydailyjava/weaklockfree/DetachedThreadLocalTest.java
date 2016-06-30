package com.blogspot.mydailyjava.weaklockfree;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

public class DetachedThreadLocalTest {

    @Test
    public void testLocalExpunction() throws Exception {
        final DetachedThreadLocal<Object> threadLocal = new DetachedThreadLocal<Object>(DetachedThreadLocal.Cleaner.INLINE);
        assertThat(threadLocal.getBackingMap().getCleanerThread(), nullValue(Thread.class));
        new ThreadLocalTestCase(threadLocal) {
            @Override
            protected void triggerClean() {
                ((WeakConcurrentMap.WithInlinedExpunction<?, ?>) threadLocal.map).expungeStaleEntries();
            }
        }.doTest();
    }

    @Test
    public void testExternalThread() throws Exception {
        DetachedThreadLocal<Object> threadLocal = new DetachedThreadLocal<Object>(DetachedThreadLocal.Cleaner.MANUAL);
        assertThat(threadLocal.getBackingMap().getCleanerThread(), nullValue(Thread.class));
        Thread thread = new Thread(threadLocal);
        thread.start();
        new ThreadLocalTestCase(threadLocal).doTest();
        thread.interrupt();
        Thread.sleep(200L);
        assertThat(thread.isAlive(), is(false));
    }

    @Test
    public void testInternalThread() throws Exception {
        DetachedThreadLocal<Object> threadLocal = new DetachedThreadLocal<Object>(DetachedThreadLocal.Cleaner.THREAD);
        assertThat(threadLocal.getBackingMap().getCleanerThread(), not(nullValue(Thread.class)));
        new ThreadLocalTestCase(threadLocal).doTest();
        threadLocal.getBackingMap().getCleanerThread().interrupt();
        Thread.sleep(200L);
        assertThat(threadLocal.getBackingMap().getCleanerThread().isAlive(), is(false));
    }

    private class ThreadLocalTestCase {

        private final DetachedThreadLocal<Object> threadLocal;

        public ThreadLocalTestCase(DetachedThreadLocal<Object> threadLocal) {
            this.threadLocal = threadLocal;
        }

        void doTest() throws Exception {
            int size = 100;
            List<Thread> threads = new ArrayList<Thread>(size);
            for (int i = 0; i < size; i++) {
                Thread thread = new Thread(new ThreadLocalInteraction(threadLocal));
                threads.add(thread);
                thread.start();
            }
            for (Thread thread : threads) {
                thread.join();
            }
            assertThat(threadLocal.map.target.size(), is(size));
            threads.clear();
            System.gc();
            Thread.sleep(1500L);
            triggerClean();
            assertThat(threadLocal.map.target.size(), is(0));
        }

        void triggerClean() {
        }
    }

    private static class ThreadLocalInteraction implements Runnable {

        private final DetachedThreadLocal<Object> threadLocal;

        ThreadLocalInteraction(DetachedThreadLocal<Object> threadLocal) {
            this.threadLocal = threadLocal;
        }

        @Override
        public void run() {
            Object value = new Object();
            threadLocal.set(value);
            assertThat(threadLocal.get(), is(value));
        }
    }
}
