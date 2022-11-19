package com.hazelcast;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;

import java.io.NotSerializableException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConcurrentDeserializerTest {

    private static ConcurrentDeserializer concurrentDeserializer;
    private static AtomicReference<String> testValue;

    @BeforeAll
    public static void beforeAll() {
        concurrentDeserializer = new ConcurrentDeserializer();
        testValue = new AtomicReference<>();
    }

    @RepeatedTest(5)
    public void testProducerConsumer() throws InterruptedException {
        int numberOfThreads = 10;
        ExecutorService service = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        producerProcess(service, latch);
        consumerProcess(numberOfThreads-1, service, latch);
        latch.await();
    }

    private void producerProcess(ExecutorService service, CountDownLatch latch) {
        service.execute(() -> {
            testValue.set(String.valueOf(Math.random() * 100));
            concurrentDeserializer.setSerialized(testValue.get().getBytes());
            latch.countDown();
        });
    }

    private void consumerProcess(int numberOfThreads, ExecutorService service, CountDownLatch latch) {
        for (int i = 0; i < numberOfThreads; i++) {
            service.execute(() -> {
                try {
                    Thread.sleep((int) (Math.random() * 1_000));
                    Object deserialized = concurrentDeserializer.getDeserialized();
                    assertEquals(testValue.get(), deserialized);
                } catch (InterruptedException | NotSerializableException e) {
                    throw new RuntimeException(e);
                }
                latch.countDown();
            });
        }
    }
}
