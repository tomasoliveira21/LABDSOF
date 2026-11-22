package pt.isep.labdsof.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.json.JsonMapper;

/** Plain HTTP client for the integration tests: it sends exactly what the test says, headers included. */
public final class ApiClient {

  private static final HttpClient CLIENT = HttpClient.newHttpClient();
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final Set<String> PROBLEM_KEYS = Set.of("type", "title", "status", "detail", "instance");

  private final int port;

  public ApiClient(int port) {
    this.port = port;
  }

  /**
   * @param authorization the whole Authorization header value, or null for none
   * @param body the JSON body, or null for none
   * @param headerNameValuePairs extra headers; a Content-Type here replaces the JSON default
   */
  public HttpResponse<String> send(String method, String path, String authorization, String body,
      String... headerNameValuePairs) {
    var builder = HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1:" + port + path))
        .timeout(Duration.ofSeconds(10));
    var contentType = true;
    for (var i = 0; i < headerNameValuePairs.length; i += 2) {
      builder.header(headerNameValuePairs[i], headerNameValuePairs[i + 1]);
      contentType &= !"Content-Type".equalsIgnoreCase(headerNameValuePairs[i]);
    }
    if (authorization != null) {
      builder.header("Authorization", authorization);
    }
    if (body == null) {
      builder.method(method, HttpRequest.BodyPublishers.noBody());
    } else {
      if (contentType) {
        builder.header("Content-Type", "application/json");
      }
      builder.method(method, HttpRequest.BodyPublishers.ofString(body));
    }
    try {
      return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  public HttpResponse<String> get(String path, String token) {
    return send("GET", path, token == null ? null : "Bearer " + token);
  }

  public HttpResponse<String> send(String method, String path, String authorization) {
    return send(method, path, authorization, null);
  }

  /** The body parsed as a JSON object. */
  public static Map<String, Object> json(HttpResponse<String> response) {
    return JSON.readValue(response.body(), Map.class);
  }

  /**
   * A Problem Details answer: the status, content type problem+json, only the RFC 9457 members the service uses, a
   * title and a detail, and none of the given texts (a token, an e-mail) anywhere in the body or the headers.
   */
  public static void assertProblem(HttpResponse<String> response, int status, String... mustNotContain) {
    assertThat(response.statusCode()).isEqualTo(status);
    assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
        type -> assertThat(type).startsWith("application/problem+json"));
    var problem = json(response);
    assertThat(problem.keySet()).isSubsetOf(PROBLEM_KEYS);
    assertThat(problem.get("status")).isEqualTo(status);
    assertThat(problem.get("title")).asString().isNotBlank();
    assertThat(problem.get("detail")).asString().isNotBlank();
    var everything = response.body() + response.headers().map();
    for (var text : mustNotContain) {
      assertThat(everything).doesNotContain(text);
    }
  }
}
