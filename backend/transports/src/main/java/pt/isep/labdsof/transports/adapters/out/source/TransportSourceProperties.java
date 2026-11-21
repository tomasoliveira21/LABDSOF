package pt.isep.labdsof.transports.adapters.out.source;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The bus source (spec D5, D7): where it is ({@code TRANSPORT_SOURCE_URL}, WireMock in Compose), the venue it is asked
 * about, how often, and the J1 timeouts (architecture 9.1). Switched on without a URL, start-up fails.
 */
@ConfigurationProperties("farol.transport.source")
public record TransportSourceProperties(boolean enabled, String url, String coords, int maxDistance,
    Duration interval, Duration connectTimeout, Duration readTimeout) {

  public TransportSourceProperties {
    if (enabled && (url == null || url.isBlank())) {
      throw new IllegalArgumentException("farol.transport.source.url is required");
    }
  }
}
