package pt.isep.labdsof.reports.adapters.out.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import pt.isep.labdsof.reports.application.TriageMetrics;
import pt.isep.labdsof.reports.domain.NotTriagedReason;

/**
 * The triage counters (spec US-36 D7): {@code farol.triage.fallback{reason}} (architecture.md §9.2) and
 * {@code farol.triage.suggested}; the reason tag has the five values of {@link NotTriagedReason} only.
 */
@Component
public class MicrometerTriageMetrics implements TriageMetrics {

  private final MeterRegistry registry;

  public MicrometerTriageMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void suggested() {
    registry.counter("farol.triage.suggested").increment();
  }

  @Override
  public void fallback(NotTriagedReason reason) {
    registry.counter("farol.triage.fallback", "reason", reason.name()).increment();
  }
}
