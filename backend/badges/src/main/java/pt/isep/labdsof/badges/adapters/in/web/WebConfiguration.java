package pt.isep.labdsof.badges.adapters.in.web;

import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class WebConfiguration {

  @Bean
  FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
    var registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
    registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 10);
    return registration;
  }
}
