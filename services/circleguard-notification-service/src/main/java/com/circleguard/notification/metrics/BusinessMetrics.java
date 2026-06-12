package com.circleguard.notification.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class BusinessMetrics {

    private final Counter notificationsSent;

    public BusinessMetrics(MeterRegistry registry) {
        this.notificationsSent = Counter.builder("circleguard.business.notifications.sent")
                .description("Multi-channel notification alerts dispatched")
                .register(registry);
    }

    public void recordNotificationSent() {
        notificationsSent.increment();
    }
}
