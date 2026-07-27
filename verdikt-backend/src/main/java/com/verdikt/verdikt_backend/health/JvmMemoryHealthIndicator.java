package com.verdikt.verdikt_backend.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

@Component
public class JvmMemoryHealthIndicator implements HealthIndicator {

    private static final double LOW_MEMORY_THRESHOLD = 0.10;

    @Override
    public Health health() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryBean.getNonHeapMemoryUsage();

        long heapUsed = heap.getUsed();
        long heapMax = heap.getMax();
        long nonHeapUsed = nonHeap.getUsed();

        if (heapMax == -1) {
            heapMax = Runtime.getRuntime().maxMemory();
        }

        double heapFreeRatio = heapMax > 0 ? (double) (heapMax - heapUsed) / heapMax : 1.0;

        Health.Builder builder = Health.up()
                .withDetail("heapUsed", heapUsed)
                .withDetail("heapMax", heapMax)
                .withDetail("heapFreePercent", Math.round(heapFreeRatio * 100))
                .withDetail("nonHeapUsed", nonHeapUsed);

        if (heapFreeRatio < LOW_MEMORY_THRESHOLD) {
            return Health.outOfService()
                    .withDetail("heapUsed", heapUsed)
                    .withDetail("heapMax", heapMax)
                    .withDetail("heapFreePercent", Math.round(heapFreeRatio * 100))
                    .withDetail("reason", "JVM heap memory below " + Math.round(LOW_MEMORY_THRESHOLD * 100) + "% free")
                    .build();
        }

        return builder.build();
    }
}
