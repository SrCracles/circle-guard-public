package com.circleguard.notification.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessMetricsTest {

    private BusinessMetrics businessMetrics;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        businessMetrics = new BusinessMetrics(registry);
    }

    @Test
    void recordNotificationSent_incrementsCounter() {
        businessMetrics.recordNotificationSent();
        businessMetrics.recordNotificationSent();

        assertThat(registry.find("circleguard.business.notifications.sent").counter().count())
                .isEqualTo(2.0);
    }
}
