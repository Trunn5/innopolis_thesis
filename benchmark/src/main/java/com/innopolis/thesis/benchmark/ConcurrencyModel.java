package com.innopolis.thesis.benchmark;

import java.util.List;

/** One concurrency model: runs {@code workers} request loops until the deadline. */
public interface ConcurrencyModel extends AutoCloseable {

    String name();

    /** @return latency of every completed request, in nanoseconds */
    List<Long> run(int workers, long deadlineNanos, Workload workload) throws Exception;

    @Override
    void close();
}
