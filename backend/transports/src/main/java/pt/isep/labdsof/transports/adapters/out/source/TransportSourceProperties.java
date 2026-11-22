package pt.isep.labdsof.transports.adapters.out.source;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The bus source (spec D5, D7): where it is ({@code TRANSPORT_SOURCE_URL}, WireMock in Compose), the venue it is asked
 * about and the J1 timeouts (architecture 9.1); how often is {@code farol.transport.source.interval}, which the job
 * reads. Switched on without a URL, or with one that is not an absolute http or https URL, start-up fails: otherwise
 * every read would fail and no read would be recorded.
 */
@ConfigurationProperties("farol.transport.source")
public record TransportSourceProperties(boolean enabled, String url, String coords, int maxDistance,
    Duration connectTimeout, Duration readTimeout) {

  public TransportSourceProperties {
    if (enabled && (url == null || url.isBlank())) {
      throw new IllegalArgumentException("farol.transport.source.url is required");
    }
    if (enabled && !isHttpUrl(url)) {
      throw new IllegalArgumentException("farol.transport.source.url must be an absolute http or https URL");
    }
  }

  private static boolean isHttpUrl(String url) {
    try {
      var uri = new URI(url);
      return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
          && uri.getHost() != null;
    } catch (URISyntaxException e) {
      return false;
    }
  }
}
