package com.innopolis.thesis.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Blocking request loop on a Java executor: platform or virtual threads. */
public final class ExecutorModel implements ConcurrencyModel {

    private final String name;
    private final ExecutorService executor;

    private ExecutorModel(String name, ExecutorService executor) {
        this.name = name;
        this.executor = executor;
    }

    public static ExecutorModel platform(int poolSize) {
        return new ExecutorModel("platform", Executors.newFixedThreadPool(poolSize));
    }

    public static ExecutorModel virtual() {
        return new ExecutorModel("virtual", Executors.newVirtualThreadPerTaskExecutor());
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public List<Long> run(int workers, long deadlineNanos, Workload workload) throws Exception {
        List<Callable<List<Long>>> loops = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            loops.add(() -> {
                List<Long> latencies = new ArrayList<>();
                while (System.nanoTime() < deadlineNanos) {
                    long start = System.nanoTime();
                    workload.handle();
                    latencies.add(System.nanoTime() - start);
                }
                return latencies;
            });
        }
        List<Long> all = new ArrayList<>();
        for (Future<List<Long>> result : executor.invokeAll(loops)) {
            all.addAll(result.get());
        }
        return all;
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
