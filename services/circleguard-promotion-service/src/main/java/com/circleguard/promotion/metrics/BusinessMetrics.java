package com.circleguard.promotion.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class BusinessMetrics {

    private final MeterRegistry registry;

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordStatusPromotion(String status) {
        if (!"CONFIRMED".equals(status) && !"SUSPECT".equals(status)) {
            return;
        }
        Counter.builder("circleguard.business.status.promoted")
                .description("Health status promotions to CONFIRMED or SUSPECT")
                .tag("status", status)
                .register(registry)
                .increment();
    }
}
