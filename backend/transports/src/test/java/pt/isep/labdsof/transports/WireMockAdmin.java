package pt.isep.labdsof.transports;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/** WireMock's admin API, as the presenter uses it in the D2 demo: failure stubs, scenario reset, the journal. */
public final class WireMockAdmin {

  /** The exact query Transports sends; the recorded stubs answer it and ignore the limit (US-40 D6). */
  public static final String GEO_QUERY =
      "/v2/entities?q=vehicleType==bus&georel=near;maxDistance:2000&geometry=point&coords=41.1724,-8.6841"
          + "&limit=1000";

  private static final HttpClient CLIENT = HttpClient.newHttpClient();
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final String base;

  public WireMockAdmin(String base) {
    this.base = base;
  }

  public String base() {
    return base;
  }

  /** Back to the first recorded snapshot (the scenario state belongs to the WireMock instance). */
  public void resetScenarios() {
    send("POST", "/__admin/scenarios/reset", "");
  }

  public void clearJournal() {
    send("DELETE", "/__admin/requests", null);
  }

  /** A stub that answers every bus query before the recorded ones; returns its id, for {@link #remove}. */
  public String failure(String responseJson) {
    var created = send("POST", "/__admin/mappings", """
        {"priority":1,"request":{"method":"GET","urlPath":"/v2/entities"},"response":%s}""".formatted(responseJson));
    return String.valueOf(JSON.readValue(created, Map.class).get("id"));
  }

  public void remove(String stubId) {
    send("DELETE", "/__admin/mappings/" + stubId, null);
  }

  /** The URLs WireMock received since the journal was cleared, as sent (path and raw query). */
  @SuppressWarnings("unchecked")
  public List<String> requestedUrls() {
    var journal = JSON.readValue(send("GET", "/__admin/requests", null), Map.class);
    return ((List<Map<String, Object>>) journal.get("requests")).stream()
        .map(entry -> String.valueOf(((Map<String, Object>) entry.get("request")).get("url"))).toList();
  }

  private String send(String method, String path, String body) {
    var request = HttpRequest.newBuilder(URI.create(base + path))
        .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
        .header("Content-Type", "application/json").build();
    try {
      var response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 300) {
        throw new IllegalStateException(method + " " + path + " answered " + response.statusCode());
      }
      return response.body();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
