package com.hazelcast;

import java.io.NotSerializableException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Conveys a piece of data between one producer thread and arbitrarily many
 * consumer threads. The producer may at any time call
 * {@link #setSerialized(byte[])} to publish an object in serialized form.
 * After that any consumer may call {@link #getDeserialized()} any number of
 * times and it will retrieve the deserialized object.
 *
 * <h3>Assumptions on the use case</h3>
 * <ol><li>
 * The producer thread has many responsibilities, its time spent at any one
 * task must be minimized.
 * </li><li>
 * Deserialization is expensive, therefore the producer must be relieved from
 * it.
 * </li><li>
 * The usage pattern is read-heavy: there are many more invocations of
 * {@link #getDeserialized()} than of {@link #setSerialized(byte[])}.
 * </li><li>
 * Each returned instance will probably be retained on the heap for a long time.
 * </li></ol>
 *
 * <h3>Characteristics of the implementation</h3>
 * <ol><li>
 * Deserialization is lazy: if no invocation of {@link #getDeserialized()} is
 * made, then no deserialization happens.
 * </li><li>
 * The value deserialized by a consumer is cached and shared with other
 * consumers.
 * </li><li>
 * The invocations of {@link #getDeserialized()} will return at most as many
 * distinct instances as there were invocations of
 * {@link #setSerialized(byte[])}.
 * </li><li>
 * {@link #setSerialized(byte[])} is wait-free: it always completes in a finite
 * number of steps, regardless of any concurrent invocations of
 * {@link #getDeserialized()}.
 * </li><li>
 * {@link #getDeserialized()} is wait-free with respect to
 * {@link #setSerialized(byte[])}: the producer thread may do anything, such
 * as calling {@link #setSerialized(byte[])} at a very high rate or getting
 * indefinitely suspended within an invocation, without affecting the ability
 * of {@link #getDeserialized()} to complete in a finite number of steps.
 * </li><li>
 * {@link #getDeserialized()} is also wait-free against itself (concurrent
 * invocations don't interfere with each other), with one allowed exception:
 * when it observes a new serialized value, it may choose to block some of
 * the other invocations of {@link #getDeserialized()} until it completes.
 * More formally, after the following sequence of events has occurred:
 * <ol><li>
 *   the producer completes its last invocation of {@link #setSerialized(byte[])};
 * </li><li>
 *   a consumer starts an invocation of {@link #getDeserialized()};
 * </li><li>
 *   the invocation completes by returning the object deserialized from the
 *   blob set by that last invocation,
 * </li></ol>
 * all future invocations of {@link #getDeserialized()} are wait-free.
 * </li><li>
 * {@link #getDeserialized()} exhibits (at least)  <em>eventually consistent,
 * monotonic read</em> behavior: once a consumer has observed an object derived
 * from a serialized value S, it will never observe an object derived from a
 * serialized value older than S, nor will it observe the initial {@code null}
 * value. If at any point the producer invokes {@link #setSerialized(byte[])}
 * for the last time and the consumer keeps invoking {@link #getDeserialized()},
 * eventually it will return an object deserialized from that final producer's
 * invocation.
 * </li></ol>
 */
public class ConcurrentDeserializer {
    private static final Object LOCK = new Object();
    private byte[] serializedValue;
    private volatile Object deserializedCachedValue;
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final Lock writeLock = readWriteLock.writeLock();
    private final Lock readLock = readWriteLock.readLock();

    /**
     * Sets a new serialized value. Exclusively called by a single producer
     * thread.
     */
    public void setSerialized(byte[] blob) {
        writeLock.lock();
        try {
            serializedValue = blob;
            deserializedCachedValue = null;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Returns the result of deserializing a blob previously set by the
     * producer thread. Called by arbitrarily many consumer threads. Initially
     * (before the first invocation of {@link #setSerialized(byte[])}) the
     * method returns {@code null}.
     */
    public Object getDeserialized() throws NotSerializableException {
        serializedValueCheck();
        readLock.lock();
        try {
            if (deserializedCachedValue == null) {
                synchronized (LOCK) {
                    if (deserializedCachedValue == null) {
                        deserializedCachedValue = deserialize(serializedValue);
                    }
                }
            }
        } finally {
            readLock.unlock();
        }
        return deserializedCachedValue;
    }

    private void serializedValueCheck() throws NotSerializableException {
        if (serializedValue == null) {
            throw new NotSerializableException();
        }
    }

    // Details of deserialization are out of scope for this assignment.
    // You may use this mock implementation:
    private Object deserialize(byte[] blob) {
        return new String(blob);
    }
}