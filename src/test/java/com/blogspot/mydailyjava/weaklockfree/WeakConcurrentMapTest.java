package com.blogspot.mydailyjava.weaklockfree;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

public class WeakConcurrentMapTest {

    @Test
    public void testLocalExpunction() throws Exception {
        final WeakConcurrentMap.WithInlinedExpunction<Object, Object> map = new WeakConcurrentMap.WithInlinedExpunction<Object, Object>();
        assertThat(map.getCleanerThread(), nullValue(Thread.class));
        new MapTestCase(map) {
            @Override
            protected void triggerClean() {
                map.expungeStaleEntries();
            }
        }.doTest();
    }

    @Test
    public void testExternalThread() throws Exception {
        WeakConcurrentMap<Object, Object> map = new WeakConcurrentMap<Object, Object>(false);
        assertThat(map.getCleanerThread(), nullValue(Thread.class));
        Thread thread = new Thread(map);
        thread.start();
        new MapTestCase(map).doTest();
        thread.interrupt();
        Thread.sleep(200L);
        assertThat(thread.isAlive(), is(false));
    }

    @Test
    public void testInternalThread() throws Exception {
        WeakConcurrentMap<Object, Object> map = new WeakConcurrentMap<Object, Object>(true);
        assertThat(map.getCleanerThread(), not(nullValue(Thread.class)));
        new MapTestCase(map).doTest();
        map.getCleanerThread().interrupt();
        Thread.sleep(200L);
        assertThat(map.getCleanerThread().isAlive(), is(false));
    }

    private class MapTestCase {

        private final WeakConcurrentMap<Object, Object> map;

        public MapTestCase(WeakConcurrentMap<Object, Object> map) {
            this.map = map;
        }

        void doTest() throws Exception {
            Object
                    key1 = new Object(), value1 = new Object(),
                    key2 = new Object(), value2 = new Object(),
                    key3 = new Object(), value3 = new Object(),
                    key4 = new Object(), value4 = new Object();
            map.put(key1, value1);
            map.put(key2, value2);
            map.put(key3, value3);
            map.put(key4, value4);
            assertThat(map.get(key1), is(value1));
            assertThat(map.get(key2), is(value2));
            assertThat(map.get(key3), is(value3));
            assertThat(map.get(key4), is(value4));
            key1 = key2 = null; // Make eligible for GC
            System.gc();
            Thread.sleep(200L);
            triggerClean();
            assertThat(map.get(key3), is(value3));
            assertThat(map.get(key4), is(value4));
            assertThat(map.approximateSize(), is(2));
            assertThat(map.target.size(), is(2));
            assertThat(map.remove(key3), is(value3));
            assertThat(map.get(key3), nullValue());
            assertThat(map.get(key4), is(value4));
            assertThat(map.approximateSize(), is(1));
            assertThat(map.target.size(), is(1));
            map.clear();
            assertThat(map.get(key3), nullValue());
            assertThat(map.get(key4), nullValue());
            assertThat(map.approximateSize(), is(0));
            assertThat(map.target.size(), is(0));
        }

        protected void triggerClean() {
        }
    }
}
