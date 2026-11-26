package pt.isep.labdsof.authusers;

import java.time.Clock;
import org.springframework.amqp.rabbit.config.ContainerCustomizer;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import pt.isep.labdsof.authusers.domain.BootstrapAdmin;

// Only the outbox publisher is scheduled, and it exists only when farol.outbox.enabled=true
@EnableScheduling
@Configuration
class AuthUsersConfiguration {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  BootstrapAdmin bootstrapAdmin(@Value("${farol.bootstrap-farol-admin-email:}") String email) {
    return BootstrapAdmin.of(email);
  }

  /** The broker can refuse this user until rabbitmq-setup provisions it. */
  @Bean
  ContainerCustomizer<SimpleMessageListenerContainer> retryRefusedLogin() {
    return container -> container.setPossibleAuthenticationFailureFatal(false);
  }
}
