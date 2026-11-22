package pt.isep.labdsof.reports.adapters.out.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.isep.labdsof.reports.domain.NotTriagedReason;

/** The triage counters (spec US-36 D7). */
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
}
