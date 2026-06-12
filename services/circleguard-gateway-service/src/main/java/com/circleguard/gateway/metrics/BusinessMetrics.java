package com.circleguard.gateway.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class BusinessMetrics {

    private final MeterRegistry registry;

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordCampusAccess(String decision) {
        if (!"GREEN".equals(decision) && !"RED".equals(decision)) {
            return;
        }
        Counter.builder("circleguard.business.campus.access")
                .description("Campus QR access validations")
                .tag("decision", decision)
                .register(registry)
                .increment();
    }
}
