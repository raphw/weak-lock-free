package com.blogspot.mydailyjava.weaklockfree;

import com.blogspot.mydailyjava.weaklockfree.AbstractWeakConcurrentMap.LatentKey;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <p>
 * A thread-safe map with weak keys. Entries are based on a key's system hash code and keys are considered
 * equal only by reference equality.
 * </p>
 * This class does not implement the {@link java.util.Map} interface because this implementation is incompatible
 * with the map contract. While iterating over a map's entries, any key that has not passed iteration is referenced non-weakly.
 */
public class WeakConcurrentMap<K, V> extends AbstractWeakConcurrentMap<K, V, LatentKey<K>>{

    /**
     * Latent keys are cached thread-locally to avoid allocations on lookups.
     * This is beneficial as the JIT unfortunately can't reliably replace the {@link LatentKey} allocation
     * with stack allocations, even though the {@link LatentKey} does not escape.
     */
    private static final ThreadLocal<LatentKey<?>> LATENT_KEY_CACHE = new ThreadLocal<LatentKey<?>>() {
        @Override
        protected LatentKey<?> initialValue() {
            return new LatentKey<Object>();
        }
    };

    private static final AtomicLong ID = new AtomicLong();

    private final Thread thread;

    private final boolean reuseKeys;

    /**
     * @param cleanerThread {@code true} if a thread should be started that removes stale entries.
     */
    public WeakConcurrentMap(boolean cleanerThread) {
        this(cleanerThread, isPersistentClassLoader(LatentKey.class.getClassLoader()));
    }

    /**
     * Checks whether the provided {@link ClassLoader} may be unloaded like a web application class loader, for example.
     * <p>
     * If the class loader can't be unloaded, it is safe to use {@link ThreadLocal}s and to reuse the {@link LatentKey}.
     * Otherwise, the use of {@link ThreadLocal}s may lead to class loader leaks as it prevents the class loader this class
     * is loaded by to unload.
     * </p>
     *
     * @param classLoader The class loader to check.
     * @return {@code true} if the provided class loader can be unloaded.
     */
    private static boolean isPersistentClassLoader(ClassLoader classLoader) {
        try {
            return classLoader == null // bootstrap class loader
                    || classLoader == ClassLoader.getSystemClassLoader()
                    || classLoader == ClassLoader.getSystemClassLoader().getParent(); // ext/platfrom class loader;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * @param cleanerThread {@code true} if a thread should be started that removes stale entries.
     * @param reuseKeys     {@code true} if the lookup keys should be reused via a {@link ThreadLocal}.
     *                      Note that setting this to {@code true} may result in class loader leaks.
     *                      See {@link #isPersistentClassLoader(ClassLoader)} for more details.
     */
    public WeakConcurrentMap(boolean cleanerThread, boolean reuseKeys) {
        this(cleanerThread, reuseKeys, new ConcurrentHashMap<WeakKey<K>, V>());
    }

    /**
     * @param cleanerThread {@code true} if a thread should be started that removes stale entries.
     * @param reuseKeys     {@code true} if the lookup keys should be reused via a {@link ThreadLocal}.
     *                      Note that setting this to {@code true} may result in class loader leaks.
     *                      See {@link #isPersistentClassLoader(ClassLoader)} for more details.
     * @param target        ConcurrentMap implementation that this class wraps.
     */
    public WeakConcurrentMap(boolean cleanerThread, boolean reuseKeys, ConcurrentMap<WeakKey<K>, V> target) {
        super(target);
        this.reuseKeys = reuseKeys;
        if (cleanerThread) {
            thread = new Thread(this);
            thread.setName("weak-ref-cleaner-" + ID.getAndIncrement());
            thread.setPriority(Thread.MIN_PRIORITY);
            thread.setDaemon(true);
            thread.start();
        } else {
            thread = null;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected LatentKey<K> getLookupKey(K key) {
        LatentKey<K> latentKey;
        if (reuseKeys) {
            latentKey = (LatentKey<K>) LATENT_KEY_CACHE.get();
        } else {
            latentKey = new LatentKey<K>();
        }
        return latentKey.withValue(key);
    }

    @Override
    protected void resetLookupKey(LatentKey<K> lookupKey) {
        lookupKey.reset();
    }

    /**
     * @return The cleaner thread or {@code null} if no such thread was set.
     */
    public Thread getCleanerThread() {
        return thread;
    }

    /**
     * A {@link WeakConcurrentMap} where stale entries are removed as a side effect of interacting with this map.
     */
    public static class WithInlinedExpunction<K, V> extends WeakConcurrentMap<K, V> {

        public WithInlinedExpunction() {
            super(false);
        }

        @Override
        public V get(K key) {
            expungeStaleEntries();
            return super.get(key);
        }

        @Override
        public boolean containsKey(K key) {
            expungeStaleEntries();
            return super.containsKey(key);
        }

        @Override
        public V put(K key, V value) {
            expungeStaleEntries();
            return super.put(key, value);
        }

        @Override
        public V remove(K key) {
            expungeStaleEntries();
            return super.remove(key);
        }

        @Override
        public Iterator<Map.Entry<K, V>> iterator() {
            expungeStaleEntries();
            return super.iterator();
        }

        @Override
        public int approximateSize() {
            expungeStaleEntries();
            return super.approximateSize();
        }
    }
}