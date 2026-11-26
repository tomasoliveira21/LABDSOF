package pt.isep.labdsof.transports.adapters.in.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pt.isep.labdsof.transports.adapters.in.web.generated.model.BusObservation;
import pt.isep.labdsof.transports.adapters.in.web.generated.model.BusObservations;
import pt.isep.labdsof.transports.adapters.in.web.generated.model.BusRouteSummary;
import pt.isep.labdsof.transports.adapters.in.web.generated.model.TransportOption;

@Configuration
class WebConfiguration {

  /** Before Spring Security, so that even a 401 of the security layer carries the correlation id. */
  @Bean
  FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
    var registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
    registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 10);
    return registration;
  }

  /**
   * The contract's optional fields are absent, never {@code null} (spec US-41 D6: {@code dataAsOf} before the first
   * valid import, a route's {@code name}, a bus's {@code fleetVehicleId}), as Auth &amp; Users does for its models.
   */
  @Bean
  JsonMapperBuilderCustomizer omitMissingOptionalFields() {
    return builder -> builder.addMixIn(BusObservations.class, OmitNulls.class)
        .addMixIn(BusObservation.class, OmitNulls.class)
        .addMixIn(BusRouteSummary.class, OmitNulls.class)
        .addMixIn(TransportOption.class, OmitNulls.class);
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  interface OmitNulls {}
}
