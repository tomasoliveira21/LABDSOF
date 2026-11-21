package pt.isep.labdsof.transports.adapters.out.source;

import java.net.URI;
import java.net.http.HttpClient;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import pt.isep.labdsof.transports.application.SourceReading;
import pt.isep.labdsof.transports.application.TransportSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Porto Digital urban platform (FIWARE NGSI v2, ADR-008), or its simulator: the buses within {@code maxDistance}
 * metres of the venue, with the J1 timeouts. A failure of the source is a reading, never an exception (spec D5).
 */
@Component
class PortoDigitalSource implements TransportSource {

  private final RestClient client;
  private final URI uri;
  private final JsonMapper json;

  PortoDigitalSource(TransportSourceProperties properties, JsonMapper json) {
    var http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    var factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(properties.readTimeout());
    this.client = RestClient.builder().requestFactory(factory).build();
    // Switched off, there may be no URL (the properties refuse an empty one only when the job is on)
    this.uri = properties.enabled() ? uri(properties.url(), properties.coords(), properties.maxDistance()) : null;
    this.json = json;
  }

  /** The NGSI v2 geo-query, written exactly as the recorded stubs match it (infra/wiremock/README.md). */
  static String query(String coords, int maxDistance) {
    return "/v2/entities?q=vehicleType==bus&georel=near;maxDistance:" + maxDistance + "&geometry=point&coords="
        + coords;
  }

  /** Built with {@code URI.create}, so nothing re-encodes the query: the stubs match its text. */
  static URI uri(String url, String coords, int maxDistance) {
    var base = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    return URI.create(base + query(coords, maxDistance));
  }

  @Override
  public SourceReading read() {
    byte[] body;
    try {
      body = client.get().uri(uri).accept(MediaType.APPLICATION_JSON).retrieve().body(byte[].class);
    } catch (RestClientResponseException e) {
      return SourceReading.unavailable("HTTP " + e.getStatusCode().value());
    } catch (RestClientException e) {
      return SourceReading.unavailable(describe(e));
    }
    return parse(body, json);
  }

  /** A list of NGSI entities, each an object with a text id; anything else is an invalid answer. */
  static SourceReading parse(byte[] body, JsonMapper json) {
    if (body == null || body.length == 0) {
      return SourceReading.invalid("empty body");
    }
    try {
      var root = json.readTree(body);
      if (!root.isArray()) {
        return SourceReading.invalid("not a JSON array");
      }
      for (int i = 0; i < root.size(); i++) {
        var entity = root.get(i);
        if (!entity.isObject()) {
          return SourceReading.invalid("entity " + i + " is not an object");
        }
        var id = entity.get("id");
        if (id == null || !id.isString() || id.stringValue().isBlank()) {
          return SourceReading.invalid("entity " + i + " has no id");
        }
      }
      return SourceReading.succeeded(root.size());
    } catch (JacksonException e) {
      return SourceReading.invalid("not JSON: " + describe(e));
    }
  }

  /** The root cause as "Class: message" (the reason is cut to 500 characters when it is recorded). */
  private static String describe(Exception e) {
    var cause = NestedExceptionUtils.getMostSpecificCause(e);
    return cause.getClass().getSimpleName() + ": " + cause.getMessage();
  }
}
