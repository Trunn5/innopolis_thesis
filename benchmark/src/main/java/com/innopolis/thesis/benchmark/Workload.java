package com.innopolis.thesis.benchmark;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * One request: a CPU loop, then a call to a stub external service
 * (fixed delay, at most {@code externalLimit} calls at once).
 */
public final class Workload {

    private static volatile int sink;

    private final int cpuIterations;
    private final long delayMs;
    private final Semaphore external;

    public Workload(int cpuIterations, long delayMs, int externalLimit) {
        this.cpuIterations = cpuIterations;
        this.delayMs = delayMs;
        this.external = new Semaphore(externalLimit, true);
    }

    public void handle() throws InterruptedException {
        for (int i = 0; i < cpuIterations; i++) {
            if ((i & 0xFF) == 0) {
                sink = i; // keeps the JIT from removing the loop
            }
        }
        external.acquire();
        try {
            TimeUnit.MILLISECONDS.sleep(delayMs);
        } finally {
            external.release();
        }
    }
}
