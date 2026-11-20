# PulseJVM

**Real-Time macOS Process & SQLite Telemetry Profiler**

PulseJVM is a zero-bloat systems telemetry profiler built in modern Java. It streams native macOS process metrics (`ps`) and network socket counts (`lsof`) on a scheduled executor thread, commits batch time-series snapshots into an indexed SQLite WAL database via JDBC, renders live Unicode CPU sparklines, and detects resource anomalies against historical baselines.

## Architecture

~~~mermaid
flowchart LR
    A[macOS Kernel Streams: ps & lsof] -->|ProcessBuilder Pipe| B[MacOsCollector Parser]
    B -->|ScheduledExecutorService 1Hz| C[TelemetryStore JDBC Batch]
    C -->|WAL Mode + Composite Index| D[(SQLite Time-Series DB)]
    D -->|Historical Window Query| E[SparklineRenderer CLI]
    D -->|Aggregate Baseline Query| F[Anomaly Detector Report]
~~~

## Quickstart (macOS)

~~~bash
git clone https://github.com/itsmerbk/pulse-jvm.git
cd pulse-jvm

# Compile
mkdir -p out
javac -cp "lib/*" -d out src/com/pulsejvm/*.java

# Run 6-tick benchmark & anomaly report
java --enable-native-access=ALL-UNNAMED -cp "out:lib/*" com.pulsejvm.Main

# Run continuous live profiler (Ctrl+C to exit)
java --enable-native-access=ALL-UNNAMED -cp "out:lib/*" com.pulsejvm.Main --live
~~~
