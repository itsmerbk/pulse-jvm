package com.pulsejvm;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MacOsCollector {

    public List<ProcessSample> captureTopProcesses(int limit) {
        List<ProcessSample> samples = new ArrayList<>();
        long now = System.currentTimeMillis();

        try {
            ProcessBuilder pb = new ProcessBuilder("ps", "-axo", "pid=,pcpu=,pmem=,rss=,state=,comm=");
            Process proc = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) continue;

                    String[] parts = trimmed.split("\\s+", 6);
                    if (parts.length < 6) continue;

                    try {
                        int pid = Integer.parseInt(parts[0]);
                        double cpu = Double.parseDouble(parts[1]);
                        double mem = Double.parseDouble(parts[2]);
                        long rssKb = Long.parseLong(parts[3]);
                        String state = parts[4];
                        String cleanName = sanitizeProcessName(parts[5]);

                        samples.add(new ProcessSample(
                            pid,
                            cleanName,
                            cpu,
                            mem,
                            Math.round((rssKb / 1024.0) * 10.0) / 10.0,
                            state,
                            now
                        ));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            proc.waitFor();
        } catch (Exception e) {
            System.err.println("Collector error: " + e.getMessage());
        }

        samples.sort(Comparator.comparingDouble(ProcessSample::cpuPercent)
            .thenComparingDouble(ProcessSample::rssMb)
            .reversed());

        return samples.subList(0, Math.min(limit, samples.size()));
    }

    public int countActiveNetworkSockets() {
        int count = 0;
        try {
            ProcessBuilder pb = new ProcessBuilder("lsof", "-i", "-n", "-P");
            Process proc = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                while (reader.readLine() != null) {
                    count++;
                }
            }
            proc.waitFor();
        } catch (Exception ignored) {
        }
        return Math.max(0, count - 1);
    }

    private String sanitizeProcessName(String rawPath) {
        String name = rawPath;
        int appIdx = name.indexOf(".app/");
        if (appIdx > 0) {
            String upToApp = name.substring(0, appIdx);
            int slash = upToApp.lastIndexOf('/');
            name = (slash >= 0 ? upToApp.substring(slash + 1) : upToApp) + ".app";
        } else {
            int lastSlash = name.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < name.length() - 1) {
                name = name.substring(lastSlash + 1);
            }
        }
        return name.length() > 24 ? name.substring(0, 21) + "..." : name;
    }
}
