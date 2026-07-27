package com.verdikt.verdikt_backend.health;

import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Instant;

@Component
public class JvmMemoryHealthCheck {

    private static final double LOW_MEMORY_THRESHOLD = 0.10;

    public HealthStatus check() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        long heapMax = heap.getMax();
        if (heapMax == -1) {
            heapMax = Runtime.getRuntime().maxMemory();
        }
        long heapUsed = heap.getUsed();
        double heapFreeRatio = heapMax > 0 ? (double) (heapMax - heapUsed) / heapMax : 1.0;

        if (heapFreeRatio < LOW_MEMORY_THRESHOLD) {
            return HealthStatus.OUT_OF_SERVICE;
        }
        return HealthStatus.UP;
    }

    public Map<String, Object> details() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryBean.getNonHeapMemoryUsage();
        long heapMax = heap.getMax();
        if (heapMax == -1) {
            heapMax = Runtime.getRuntime().maxMemory();
        }
        long heapUsed = heap.getUsed();
        double heapFreeRatio = heapMax > 0 ? (double) (heapMax - heapUsed) / heapMax : 1.0;

        return Map.of(
                "heapUsed", heapUsed,
                "heapMax", heapMax,
                "heapFreePercent", Math.round(heapFreeRatio * 100),
                "nonHeapUsed", nonHeap.getUsed()
        );
    }
}
