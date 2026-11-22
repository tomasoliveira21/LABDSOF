package pt.isep.labdsof.transports.adapters.out.metrics;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.github.resilience4j.micrometer.tagged.TaggedRetryMetrics;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pt.isep.labdsof.transports.application.SourceImports;

/**
 * The D2 panel's metrics of architecture 9.2 (spec US-40 D10): the age of the data in use, and Resilience4j's state of
 * the {@code transport-source} circuit breaker and retry. Spring Boot binds every {@link MeterBinder} bean.
 */
@Configuration
class ImportMetricsConfiguration {

  static final String DATA_AGE = "farol.transport.data.age";

  /** Seconds since the last valid import ended, read from source_import at each scrape; {@code NaN} before any. */
  @Bean
  MeterBinder transportDataAge(SourceImports imports, Clock clock) {
    return registry -> Gauge.builder(DATA_AGE, imports, i -> i.lastSucceededAt()
            .map(at -> Duration.between(at, clock.instant()).toMillis() / 1000.0)
            .orElse(Double.NaN))
        .description("Seconds since the last valid import of the transport source")
        .baseUnit("seconds")
        .register(registry);
  }

  @Bean
  MeterBinder transportSourceCircuitBreakerMetrics(CircuitBreakerRegistry breakers) {
    return TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(breakers);
  }

  @Bean
  MeterBinder transportSourceRetryMetrics(RetryRegistry retries) {
    return TaggedRetryMetrics.ofRetryRegistry(retries);
  }
}
