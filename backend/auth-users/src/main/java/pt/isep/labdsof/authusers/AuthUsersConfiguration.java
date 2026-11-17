package pt.isep.labdsof.authusers;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pt.isep.labdsof.authusers.domain.BootstrapAdmin;

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
}
