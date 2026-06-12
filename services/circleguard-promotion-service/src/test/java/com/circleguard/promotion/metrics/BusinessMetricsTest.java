package com.circleguard.promotion.metrics;

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
    void recordStatusPromotion_incrementsCounterForConfirmedAndSuspect() {
        businessMetrics.recordStatusPromotion("CONFIRMED");
        businessMetrics.recordStatusPromotion("SUSPECT");

        assertThat(registry.find("circleguard.business.status.promoted").tags("status", "CONFIRMED").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.find("circleguard.business.status.promoted").tags("status", "SUSPECT").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void recordStatusPromotion_ignoresOtherStatuses() {
        businessMetrics.recordStatusPromotion("ACTIVE");
        businessMetrics.recordStatusPromotion("PROBABLE");

        assertThat(registry.find("circleguard.business.status.promoted").counters()).isEmpty();
    }
}
