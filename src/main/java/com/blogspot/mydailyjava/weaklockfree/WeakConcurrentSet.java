package com.blogspot.mydailyjava.weaklockfree;

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

    public void add(V value) {
        target.put(value, Boolean.TRUE);
    }

    public boolean contains(V value) {
        return target.containsKey(value);
    }

    public boolean remove(V value) {
        return target.remove(value);
    }

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
     * @return The cleaner thread or {@code null} if no such thread was set.
     */
    public Thread getCleanerThread() {
        return target.getCleanerThread();
    }
}
