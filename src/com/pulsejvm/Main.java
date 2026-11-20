package com.pulsejvm;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    public static void main(String[] args) throws Exception {
        boolean liveMode = args.length > 0 && "--live".equalsIgnoreCase(args[0]);
        int maxTicks = liveMode ? Integer.MAX_VALUE : 6;

        MacOsCollector collector = new MacOsCollector();
        TelemetryStore store = new TelemetryStore("pulse_telemetry.db");
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        CountDownLatch doneLatch = new CountDownLatch(1);
        AtomicInteger tickCounter = new AtomicInteger(0);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scheduler.shutdownNow();
            try {
                store.close();
            } catch (Exception ignored) {
            }
        }));

        scheduler.scheduleAtFixedRate(() -> {
            try {
                int tick = tickCounter.incrementAndGet();
                List<ProcessSample> top = collector.captureTopProcesses(10);
                double writeMs = store.insertBatch(top);
                int sockets = collector.countActiveNetworkSockets();
                long totalRows = store.getTotalSampleCount();

                System.out.print("\033[H\033[2J");
                System.out.flush();
                System.out.println(SparklineRenderer.CYAN + SparklineRenderer.BOLD +
                    "╔══════════════════════════════════════════════════════════════════════════════════════╗\n" +
                    "║  PulseJVM — Real-Time macOS Process & SQLite Telemetry Profiler                      ║\n" +
                    "╚══════════════════════════════════════════════════════════════════════════════════════╝" +
                    SparklineRenderer.RESET);

                System.out.printf("Tick: %d/%s  |  Active Net Sockets: %d  |  SQLite Batch Write: %.2f ms  |  Indexed Rows: %d%n%n",
                    tick, liveMode ? "∞" : String.valueOf(maxTicks), sockets, writeMs, totalRows);

                System.out.printf("%-7s %-24s %9s %9s %10s  %-6s  %-14s%n",
                    "PID", "PROCESS", "CPU %", "MEM %", "RSS (MB)", "STATE", "CPU SPARKLINE");
                System.out.println("─".repeat(88));

                for (ProcessSample p : top) {
                    List<Double> history = store.getCpuHistory(p.processName(), 12);
                    String spark = SparklineRenderer.renderSparkline(history, 12);
                    System.out.printf("%-7d %-24s %s %8.1f%% %9.1f  %-6s  %s%s%s%n",
                        p.pid(),
                        p.processName(),
                        SparklineRenderer.colorizeCpu(p.cpuPercent()),
                        p.memPercent(),
                        p.rssMb(),
                        p.state(),
                        SparklineRenderer.CYAN,
                        spark,
                        SparklineRenderer.RESET);
                }

                if (tick >= maxTicks) {
                    System.out.println("\n" + SparklineRenderer.BOLD + "SQLite Indexed Anomaly & Resource Baseline Report:" + SparklineRenderer.RESET);
                    System.out.println("─".repeat(88));
                    List<String> anomalies = store.detectAnomalies();
                    if (anomalies.isEmpty()) {
                        System.out.println("No CPU spikes (>20%) or high-RSS processes (>450 MB) detected during window.");
                    } else {
                        anomalies.forEach(System.out::println);
                    }
                    scheduler.shutdown();
                    doneLatch.countDown();
                }
            } catch (Exception e) {
                e.printStackTrace();
                scheduler.shutdown();
                doneLatch.countDown();
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);

        doneLatch.await();
        store.close();
    }
}
