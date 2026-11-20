package com.pulsejvm;

public record ProcessSample(
    int pid,
    String processName,
    double cpuPercent,
    double memPercent,
    double rssMb,
    String state,
    long capturedAtEpochMs
) {}
