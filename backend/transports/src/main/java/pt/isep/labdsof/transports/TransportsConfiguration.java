package pt.isep.labdsof.transports;

import java.time.Clock;
import org.springframework.amqp.rabbit.config.ContainerCustomizer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@Configuration
class TransportsConfiguration {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  /**
   * Compose starts Transports and rabbitmq-setup together, so the broker may refuse the {@code transports} user
   * until setup has created it: keep retrying, as for missing queues, instead of stopping (spec D3, #214 D9).
   */
  @Bean
  ContainerCustomizer<SimpleMessageListenerContainer> retryRefusedLogin() {
    return container -> container.setPossibleAuthenticationFailureFatal(false);
  }
}
