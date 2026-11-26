package pt.isep.labdsof.reports.adapters.out.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import pt.isep.labdsof.reports.application.TriageMetrics;
import pt.isep.labdsof.reports.domain.NotTriagedReason;
import pt.isep.labdsof.reports.domain.TriageState;

/**
 * The triage counters (spec US-36 D7): {@code farol.triage.fallback{reason}} (architecture.md §9.2) and
 * {@code farol.triage.suggested}; the reason tag has the five values of {@link NotTriagedReason} only. And
 * {@code farol.triage.decision{decision}} (spec US-35 D6), whose tag is Accepted or Changed.
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

  @Override
  public void decided(TriageState decision) {
    registry.counter("farol.triage.decision", "decision", decision.name()).increment();
  }
}
