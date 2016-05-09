package com.blogspot.mydailyjava.weaklockfree;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

public class WeakConcurrentSetTest {

    @Test
    public void testLocalExpunction() throws Exception {
        final WeakConcurrentSet<Object> set = new WeakConcurrentSet<Object>(WeakConcurrentSet.Cleaner.INLINE);
        assertThat(set.getCleanerThread(), nullValue(Thread.class));
        new SetTestCase(set) {
            @Override
            protected void triggerClean() {
                ((WeakConcurrentMap.WithInlinedExpunction<?, ?>) set.target).expungeStaleEntries();
            }
        }.doTest();
    }

    @Test
    public void testExternalThread() throws Exception {
        WeakConcurrentSet<Object> set = new WeakConcurrentSet<Object>(WeakConcurrentSet.Cleaner.MANUAL);
        assertThat(set.getCleanerThread(), nullValue(Thread.class));
        Thread thread = new Thread(set);
        thread.start();
        new SetTestCase(set).doTest();
        thread.interrupt();
        Thread.sleep(200L);
        assertThat(thread.isAlive(), is(false));
    }

    @Test
    public void testInternalThread() throws Exception {
        WeakConcurrentSet<Object> set = new WeakConcurrentSet<Object>(WeakConcurrentSet.Cleaner.THREAD);
        assertThat(set.getCleanerThread(), not(nullValue(Thread.class)));
        new SetTestCase(set).doTest();
        set.getCleanerThread().interrupt();
        Thread.sleep(200L);
        assertThat(set.getCleanerThread().isAlive(), is(false));
    }

    private class SetTestCase {

        private final WeakConcurrentSet<Object> set;

        public SetTestCase(WeakConcurrentSet<Object> set) {
            this.set = set;
        }

        void doTest() throws Exception {
            Object value1 = new Object(), value2 = new Object(), value3 = new Object(), value4 = new Object();
            set.add(value1);
            set.add(value2);
            set.add(value3);
            set.add(value4);
            assertThat(set.contains(value1), is(true));
            assertThat(set.contains(value2), is(true));
            assertThat(set.contains(value3), is(true));
            assertThat(set.contains(value4), is(true));
            Set<Object> values = new HashSet<Object>(Arrays.asList(value1, value2, value3, value4));
            for (Object value : set) {
                assertThat(values.remove(value), is(true));
            }
            assertThat(values.isEmpty(), is(true));
            value1 = value2 = null; // Make eligible for GC
            System.gc();
            Thread.sleep(200L);
            triggerClean();
            assertThat(set.contains(value3), is(true));
            assertThat(set.contains(value4), is(true));
            assertThat(set.target.target.size(), is(2));
            assertThat(set.remove(value3), is(true));
            assertThat(set.contains(value3), is(false));
            assertThat(set.contains(value4), is(true));
            assertThat(set.target.target.size(), is(1));
            set.clear();
            assertThat(set.contains(value3), is(false));
            assertThat(set.contains(value4), is(false));
            assertThat(set.target.target.size(), is(0));
            assertThat(set.iterator().hasNext(), is(false));
        }

        protected void triggerClean() {
        }
    }
}
