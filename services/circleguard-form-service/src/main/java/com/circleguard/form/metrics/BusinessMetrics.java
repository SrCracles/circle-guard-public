package com.circleguard.form.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class BusinessMetrics {

    private final Counter surveysSubmitted;

    public BusinessMetrics(MeterRegistry registry) {
        this.surveysSubmitted = Counter.builder("circleguard.business.surveys.submitted")
                .description("Health survey forms submitted")
                .register(registry);
    }

    public void recordSurveySubmitted() {
        surveysSubmitted.increment();
    }
}
