package com.blogspot.mydailyjava.weaklockfree;

import java.lang.ref.ReferenceQueue;

public class WeakConcurrentSet<V> implements Runnable {

    final WeakConcurrentMap<V, Boolean> target;

    public WeakConcurrentSet(Cleaner cleaner) {
        switch (cleaner) {
            case INLINE:
                target = new WeakConcurrentMap.WithInlinedExpunction<V, Boolean>();
                break;
            case THREAD:
            case MANUAL:
                target = new WeakConcurrentMap<V, Boolean>(cleaner == Cleaner.THREAD);
                break;
            default:
                throw new AssertionError();
        }
    }

    /**
     * @param value The value to add to the set.
     * @return {@code true} if the value was added to the set and was not contained before.
     */
    public boolean add(V value) {
        return target.put(value, Boolean.TRUE) == null; // is null or Boolean.TRUE
    }

    /**
     * @param value The value to check if it is contained in the set.
     * @return {@code true} if the set contains the value.
     */
    public boolean contains(V value) {
        return target.containsKey(value);
    }

    /**
     * @param value The value to remove from the set.
     * @return {@code true} if the value is contained in the set.
     */
    public boolean remove(V value) {
        return target.remove(value);
    }

    /**
     * Clears the set.
     */
    public void clear() {
        target.clear();
    }

    @Override
    public void run() {
        target.run();
    }

    /**
     * Determines the cleaning format. A reference is removed either by an explicitly started cleaner thread
     * associated with this instance ({@link Cleaner#THREAD}), as a result of interacting with this thread local
     * from any thread ({@link Cleaner#INLINE} or manually by submitting the detached thread local to a thread
     * ({@link Cleaner#MANUAL}).
     */
    public enum Cleaner {
        THREAD, INLINE, MANUAL
    }

    /**
     * @return The reference queue that backs the weak data structure.
     */
    public ReferenceQueue<V> getReferenceQueue() {
        return target;
    }

    /**
     * @return The cleaner thread or {@code null} if no such thread was set.
     */
    public Thread getCleanerThread() {
        return target.getCleanerThread();
    }
}
