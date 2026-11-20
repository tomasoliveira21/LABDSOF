package pt.isep.labdsof.reports;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import org.springframework.amqp.rabbit.config.ContainerCustomizer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import pt.isep.labdsof.reports.adapters.out.persistence.JdbcOutbox;

@Configuration
@EnableScheduling
class ReportsConfiguration {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  /** #148: farol_outbox_oldest_pending_seconds, read at each scrape (every 15 s); the outbox alert fires above 60 s. */
  @Bean
  MeterBinder outboxAge(JdbcOutbox outbox) {
    return registry -> Gauge.builder("farol.outbox.oldest.pending", outbox, JdbcOutbox::oldestPendingSeconds)
        .description("Age of the oldest unpublished outbox row; 0 when none is pending")
        .baseUnit("seconds")
        .register(registry);
  }

  /**
   * Compose starts Reports and rabbitmq-setup together, so the broker may refuse the {@code reports} user
   * until setup has created it: keep retrying, as for missing queues, instead of stopping (spec D9).
   */
  @Bean
  ContainerCustomizer<SimpleMessageListenerContainer> retryRefusedLogin() {
    return container -> container.setPossibleAuthenticationFailureFatal(false);
  }
}
