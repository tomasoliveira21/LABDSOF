package pt.isep.labdsof.transports.adapters.out.source;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Which failures the {@code transport-source} retry tries again (spec US-40 D5). It reads the circuit breaker's state,
 * so it is set here, on top of the J1 values of application.yaml: the Resilience4j starter applies a
 * {@link RetryConfigCustomizer} after the properties.
 */
@Configuration
class SourceResilienceConfiguration {

  @Bean
  RetryConfigCustomizer transportSourceRetry(CircuitBreakerRegistry breakers) {
    return RetryConfigCustomizer.of(PortoDigitalSource.RESILIENCE, retry -> retry.retryOnException(
        new RetryableSourceFailure(() -> breakers.circuitBreaker(PortoDigitalSource.RESILIENCE))));
  }
}
