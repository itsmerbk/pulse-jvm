package com.pulsejvm;

import java.util.List;

public class SparklineRenderer {
    private static final char[] BLOCKS = {'▁', '▂', '▃', '▄', '▅', '▆', '▇', '█'};
    public static final String RESET = "\u001B[0m";
    public static final String CYAN = "\u001B[36m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String RED = "\u001B[31m";
    public static final String BOLD = "\u001B[1m";

    public static String renderSparkline(List<Double> values, int width) {
        if (values == null || values.isEmpty()) {
            return " ".repeat(width);
        }
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        if (max <= 0.0) max = 1.0;

        StringBuilder sb = new StringBuilder();
        int pad = Math.max(0, width - values.size());
        sb.append("▁".repeat(pad));

        for (double v : values) {
            int idx = (int) Math.min(BLOCKS.length - 1, Math.round((v / max) * (BLOCKS.length - 1)));
            sb.append(BLOCKS[Math.max(0, idx)]);
        }
        return sb.toString();
    }

    public static String colorizeCpu(double cpu) {
        String formatted = String.format("%6.1f%%", cpu);
        if (cpu >= 35.0) return RED + BOLD + formatted + RESET;
        if (cpu >= 12.0) return YELLOW + formatted + RESET;
        return GREEN + formatted + RESET;
    }
}
