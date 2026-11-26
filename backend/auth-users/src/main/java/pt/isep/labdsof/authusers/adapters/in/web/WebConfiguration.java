package pt.isep.labdsof.authusers.adapters.in.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pt.isep.labdsof.authusers.adapters.in.web.generated.model.Membership;

@Configuration
class WebConfiguration {

  /** Before Spring Security, so that even a 401 of the security layer carries the correlation id. */
  @Bean
  FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
    var registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
    registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 10);
    return registration;
  }

  @Bean
  JsonMapperBuilderCustomizer omitMissingEventDetails() {
    return builder -> builder.addMixIn(Membership.class, OmitNulls.class);
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  interface OmitNulls {}
}
