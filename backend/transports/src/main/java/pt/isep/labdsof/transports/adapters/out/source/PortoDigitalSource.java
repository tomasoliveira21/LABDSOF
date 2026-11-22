package pt.isep.labdsof.transports.adapters.out.source;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import pt.isep.labdsof.transports.application.SourceReading;
import pt.isep.labdsof.transports.application.TransportSource;
import pt.isep.labdsof.transports.domain.BusReading;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The Porto Digital urban platform (FIWARE NGSI v2, ADR-008), or its simulator: the buses within {@code maxDistance}
 * metres of the venue, with the J1 timeouts. A failure of the source is a reading, never an exception (spec #193 D5).
 * It only reads (spec US-40 D7): each entity becomes a {@link BusReading} with whatever could be read, and the rules
 * of {@code BusImport} decide what is broken.
 */
@Component
class PortoDigitalSource implements TransportSource {

  private final RestClient client;
  private final URI uri;
  private final JsonMapper json;
  private final CircuitBreaker breaker;
  private final Retry retry;

  /** {@code client} is Boot's builder, which measures every call ({@code http.client.requests}, spec D12). */
  PortoDigitalSource(TransportSourceProperties properties, JsonMapper json, RestClient.Builder client,
      CircuitBreakerRegistry breakers, RetryRegistry retries) {
    var http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    var factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(properties.readTimeout());
    this.client = client.requestFactory(factory).build();
    // Switched off, there may be no URL (the properties refuse an empty one only when the job is on)
    this.uri = properties.enabled() ? uri(properties.url(), properties.coords(), properties.maxDistance()) : null;
    this.json = json;
    this.breaker = breakers.circuitBreaker(RESILIENCE);
    this.retry = retries.retry(RESILIENCE);
  }

  /** The retry and the circuit breaker of the source, configured in application.yaml (US-40 D5, architecture 9.1). */
  static final String RESILIENCE = "transport-source";

  private static final Pattern ROUTE = Pattern.compile("^stcp:route:(.+)$");

  /**
   * The NGSI v2 geo-query, written exactly as the recorded stubs match it (infra/wiremock/README.md), asking for up to
   * 1000 buses: without a limit the real API sends 20 (US-40 D6); the stubs ignore it.
   */
  static String query(String coords, int maxDistance) {
    return "/v2/entities?q=vehicleType==bus&georel=near;maxDistance:" + maxDistance + "&geometry=point&coords="
        + coords + "&limit=1000";
  }

  /** Built with {@code URI.create}, so nothing re-encodes the query: the stubs match its text. */
  static URI uri(String url, String coords, int maxDistance) {
    var base = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    return URI.create(base + query(coords, maxDistance));
  }

  /**
   * One run (US-40 D5): the retry wraps the circuit breaker, so every attempt counts for the circuit and nothing is
   * tried while it is open. The answer is read outside both: an invalid answer is neither retried nor counted.
   */
  @Override
  public SourceReading read() {
    var attempts = new AtomicInteger();
    var lastFailure = new AtomicReference<String>();
    Supplier<byte[]> call = () -> {
      attempts.incrementAndGet();
      try {
        return fetch();
      } catch (SourceFailure failure) {
        lastFailure.set(failure.getMessage());
        throw failure;
      }
    };
    try {
      var body = Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(breaker, call)).get();
      return parse(body, json, attempts.get());
    } catch (CallNotPermittedException e) {
      // Opened by this run's failed calls, or already open: the reason is the last failure, if this run had one
      return SourceReading.unavailable(lastFailure.get() == null ? "circuit open" : lastFailure.get(), attempts.get());
    } catch (SourceFailure failure) {
      return SourceReading.unavailable(failure.getMessage(), attempts.get());
    }
  }

  private byte[] fetch() {
    try {
      return client.get().uri(uri).accept(MediaType.APPLICATION_JSON).retrieve().body(byte[].class);
    } catch (RestClientResponseException e) {
      var status = e.getStatusCode().value();
      throw new SourceFailure("HTTP " + status, status >= 500 || status == 429);
    } catch (RestClientException e) {
      throw new SourceFailure(describe(e), true);
    }
  }

  static SourceReading parse(byte[] body, JsonMapper json) {
    return parse(body, json, 1);
  }

  /** A list of NGSI entities, one reading each; an answer that is not a list of objects is invalid. */
  static SourceReading parse(byte[] body, JsonMapper json, int attempts) {
    if (body == null || body.length == 0) {
      return SourceReading.invalid("empty body", attempts);
    }
    try {
      var root = json.readTree(body);
      if (!root.isArray()) {
        return SourceReading.invalid("not a JSON array", attempts);
      }
      var readings = new ArrayList<BusReading>();
      for (int i = 0; i < root.size(); i++) {
        var entity = root.get(i);
        if (!entity.isObject()) {
          return SourceReading.invalid("entity " + i + " is not an object", attempts);
        }
        readings.add(reading(i, entity));
      }
      return SourceReading.answered(readings, attempts);
    } catch (JacksonException e) {
      return SourceReading.invalid("not JSON: " + describe(e), attempts);
    }
  }

  /** The attributes of a Vehicle entity (US-40 D7); a missing or unreadable one is null. */
  private static BusReading reading(int position, JsonNode entity) {
    Double longitude = null;
    Double latitude = null;
    var location = value(entity, "location");
    var coordinates = location == null ? null : location.get("coordinates");
    if (location != null && "Point".equals(text(location.get("type"))) && coordinates != null
        && coordinates.isArray() && coordinates.size() >= 2 && coordinates.get(0).isNumber()
        && coordinates.get(1).isNumber()) {
      longitude = coordinates.get(0).doubleValue();
      latitude = coordinates.get(1).doubleValue();
    }
    return new BusReading(position, text(entity.get("id")), text(value(entity, "dataProvider")),
        text(value(entity, "fleetVehicleId")), route(value(entity, "annotations")), longitude, latitude,
        instant(text(value(entity, "observationDateTime"))));
  }

  /** The {@code value} of an NGSI attribute, or null. */
  private static JsonNode value(JsonNode entity, String attribute) {
    var node = entity.get(attribute);
    return node == null ? null : node.get("value");
  }

  /** Text, or null when missing, not text, or holding a NUL character, which PostgreSQL cannot store (AC-6). */
  private static String text(JsonNode node) {
    return node != null && node.isString() && !node.stringValue().contains("\0") ? node.stringValue() : null;
  }

  /** The route of the first {@code stcp:route:<route>} annotation, or null. */
  private static String route(JsonNode annotations) {
    if (annotations == null || !annotations.isArray()) {
      return null;
    }
    for (var annotation : annotations) {
      var matcher = ROUTE.matcher(Objects.requireNonNullElse(text(annotation), ""));
      if (matcher.matches()) {
        return matcher.group(1);
      }
    }
    return null;
  }

  /** ISO-8601 with an offset, with or without a fraction of a second; null when missing or unreadable. */
  private static Instant instant(String value) {
    try {
      return value == null ? null : OffsetDateTime.parse(value).toInstant();
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  /**
   * The root cause as "Class: message", or "Class" alone when it has no message (an unresolved host has none); the
   * reason is cut to 500 characters when it is recorded.
   */
  static String describe(Exception e) {
    var cause = NestedExceptionUtils.getMostSpecificCause(e);
    var name = cause.getClass().getSimpleName();
    return cause.getMessage() == null ? name : name + ": " + cause.getMessage();
  }
}
