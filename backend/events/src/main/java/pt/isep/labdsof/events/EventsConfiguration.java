package pt.isep.labdsof.events;

import java.time.Clock;
import org.springframework.amqp.rabbit.config.ContainerCustomizer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class EventsConfiguration {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  /**
   * Compose starts Events and rabbitmq-setup together, so the broker may refuse the {@code events} user
   * until setup has created it: keep retrying, as for missing queues, instead of stopping (spec D2).
   */
  @Bean
  ContainerCustomizer<SimpleMessageListenerContainer> retryRefusedLogin() {
    return container -> container.setPossibleAuthenticationFailureFatal(false);
  }
}
