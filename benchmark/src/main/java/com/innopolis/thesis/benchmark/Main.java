package com.innopolis.thesis.benchmark;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Main {

    public static void main(String[] args) throws Exception {
        Map<String, String> opts = parseArgs(args);
        String modelName = opts.getOrDefault("model", "platform");
        int workers = Integer.parseInt(opts.getOrDefault("workers", "64"));
        int poolSize = Integer.parseInt(opts.getOrDefault("pool-size", "8"));
        int durationSec = Integer.parseInt(opts.getOrDefault("duration-sec", "5"));
        int cpuIterations = Integer.parseInt(opts.getOrDefault("cpu-iterations", "1000"));
        int delayMs = Integer.parseInt(opts.getOrDefault("delay-ms", "20"));
        int externalLimit = Integer.parseInt(opts.getOrDefault("external-limit", "32"));

        Workload workload = new Workload(cpuIterations, delayMs, externalLimit);
        try (ConcurrencyModel model = switch (modelName) {
            case "platform" -> ExecutorModel.platform(poolSize);
            case "virtual" -> ExecutorModel.virtual();
            default -> throw new IllegalArgumentException("Unknown model: " + modelName + ". Use platform or virtual.");
        }) {
            long deadline = System.nanoTime() + durationSec * 1_000_000_000L;
            List<Long> latencies = model.run(workers, deadline, workload);
            Collections.sort(latencies);
            System.out.printf(Locale.ROOT,
                    "model=%s workers=%d requests=%d throughput=%.1f req/s p50=%.2f ms p99=%.2f ms%n",
                    model.name(), workers, latencies.size(), latencies.size() / (double) durationSec,
                    percentileMs(latencies, 0.50), percentileMs(latencies, 0.99));
        }
    }

    private static double percentileMs(List<Long> sorted, double p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        return sorted.get((int) Math.ceil(p * sorted.size()) - 1) / 1_000_000.0;
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> opts = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            opts.put(args[i].replaceFirst("^--", ""), args[i + 1]);
        }
        return opts;
    }
}
