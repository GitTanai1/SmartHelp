package com.smarthelp.service;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/** Small, low-cardinality operational metrics for agent lifecycle behavior. */
@Component
public class AgentRunMetrics {

    private final MeterRegistry meterRegistry;

    public AgentRunMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void started() {
        meterRegistry.counter("smarthelp.agent.runs.started").increment();
    }

    public void finished(String status, LocalDateTime createdAt) {
        meterRegistry.counter("smarthelp.agent.runs.finished", "status", status).increment();
        if (createdAt != null) {
            Timer.builder("smarthelp.agent.run.duration")
                    .description("Elapsed duration of a completed agent run")
                    .tag("status", status)
                    .register(meterRegistry)
                    .record(Duration.between(createdAt, LocalDateTime.now()));
        }
    }

    public void approvalRequested() {
        meterRegistry.counter("smarthelp.agent.approvals.requested").increment();
    }

    public void approvalDecided(String decision) {
        meterRegistry.counter("smarthelp.agent.approvals.decided", "decision", decision).increment();
    }
}
