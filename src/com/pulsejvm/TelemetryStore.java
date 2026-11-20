package com.pulsejvm;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TelemetryStore implements AutoCloseable {
    private final Connection conn;

    public TelemetryStore(String dbFile) throws SQLException {
        this.conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
        initSchema();
    }

    private void initSchema() throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL;");
            st.execute("PRAGMA synchronous=NORMAL;");
            st.execute("""
                CREATE TABLE IF NOT EXISTS process_samples (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    captured_at INTEGER NOT NULL,
                    pid INTEGER NOT NULL,
                    process_name TEXT NOT NULL,
                    cpu_percent REAL NOT NULL,
                    mem_percent REAL NOT NULL,
                    rss_mb REAL NOT NULL,
                    state TEXT NOT NULL
                )
            """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_proc_time ON process_samples(process_name, captured_at DESC);");
            st.execute("CREATE INDEX IF NOT EXISTS idx_cpu_desc ON process_samples(cpu_percent DESC);");
        }
    }

    public synchronized double insertBatch(List<ProcessSample> samples) throws SQLException {
        long startNs = System.nanoTime();
        String sql = """
            INSERT INTO process_samples (captured_at, pid, process_name, cpu_percent, mem_percent, rss_mb, state)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        conn.setAutoCommit(false);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (ProcessSample s : samples) {
                ps.setLong(1, s.capturedAtEpochMs());
                ps.setInt(2, s.pid());
                ps.setString(3, s.processName());
                ps.setDouble(4, s.cpuPercent());
                ps.setDouble(5, s.memPercent());
                ps.setDouble(6, s.rssMb());
                ps.setString(7, s.state());
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
        return (System.nanoTime() - startNs) / 1_000_000.0;
    }

    public synchronized List<Double> getCpuHistory(String processName, int limit) throws SQLException {
        String sql = """
            SELECT cpu_percent FROM process_samples
            WHERE process_name = ?
            ORDER BY captured_at DESC
            LIMIT ?
        """;
        List<Double> values = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, processName);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    values.add(rs.getDouble("cpu_percent"));
                }
            }
        }
        Collections.reverse(values);
        return values;
    }

    public synchronized List<String> detectAnomalies() throws SQLException {
        String sql = """
            SELECT
                process_name,
                ROUND(AVG(cpu_percent), 2) AS avg_cpu,
                ROUND(MAX(cpu_percent), 2) AS peak_cpu,
                ROUND(MAX(rss_mb), 1) AS peak_ram_mb,
                COUNT(*) AS samples
            FROM process_samples
            GROUP BY process_name
            HAVING peak_cpu > 20.0 OR peak_ram_mb > 450.0
            ORDER BY peak_cpu DESC, peak_ram_mb DESC
            LIMIT 6
        """;
        List<String> alerts = new ArrayList<>();
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String name = rs.getString("process_name");
                double avgCpu = rs.getDouble("avg_cpu");
                double peakCpu = rs.getDouble("peak_cpu");
                double peakRam = rs.getDouble("peak_ram_mb");
                String tag = peakCpu > 20.0 ? "CPU_SPIKE" : "HIGH_RSS_MEM";
                alerts.add(String.format("%-24s | %-12s | Avg CPU: %5.1f%% | Peak CPU: %5.1f%% | Peak RAM: %6.1f MB",
                    name, tag, avgCpu, peakCpu, peakRam));
            }
        }
        return alerts;
    }

    public synchronized long getTotalSampleCount() throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM process_samples")) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    @Override
    public void close() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }
}
