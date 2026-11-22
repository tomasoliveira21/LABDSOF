package pt.isep.labdsof.reports.adapters.out.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.isep.labdsof.reports.domain.NotTriagedReason;
import pt.isep.labdsof.reports.domain.TriageState;

/** The triage counters (spec US-36 D7, spec US-35 D6). */
class MicrometerTriageMetricsTest {

  @Test
  @DisplayName("US-36 AC-3 the counters have the metric names and the reason tag")
  void counters() {
    var registry = new SimpleMeterRegistry();
    var metrics = new MicrometerTriageMetrics(registry);

    metrics.suggested();
    metrics.fallback(NotTriagedReason.Timeout);
    metrics.fallback(NotTriagedReason.Timeout);
    metrics.fallback(NotTriagedReason.Unavailable);

    assertThat(registry.get("farol.triage.suggested").counter().count()).isEqualTo(1);
    assertThat(registry.get("farol.triage.fallback").tag("reason", "Timeout").counter().count()).isEqualTo(2);
    assertThat(registry.get("farol.triage.fallback").tag("reason", "Unavailable").counter().count()).isEqualTo(1);
    assertThat(registry.find("farol.triage.fallback").tag("reason", "ProviderError").counter()).isNull();
  }

  @Test
  @DisplayName("US-35 AC-1 the decision counter has its name and tag")
  void decisions() {
    var registry = new SimpleMeterRegistry();
    var metrics = new MicrometerTriageMetrics(registry);

    metrics.decided(TriageState.Accepted);
    metrics.decided(TriageState.Accepted);
    metrics.decided(TriageState.Changed);

    assertThat(registry.get("farol.triage.decision").tag("decision", "Accepted").counter().count()).isEqualTo(2);
    assertThat(registry.get("farol.triage.decision").tag("decision", "Changed").counter().count()).isEqualTo(1);
  }
}
