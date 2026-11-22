package pt.isep.labdsof.reports.adapters.out.ai;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import pt.isep.labdsof.reports.application.TriageModel;
import pt.isep.labdsof.reports.application.TriageModelException;
import pt.isep.labdsof.reports.domain.NotTriagedReason;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The local Ollama (ADR-005, guide K1) with the runtime contract of the local AI decisions (spec US-34 D2): one
 * attempt, connect 2 s, the whole call capped at the response timeout and at the time left before the deadline;
 * the prompt and schema are the evaluated package files (D3). Sends the description only (features.md §5.2 AI-1).
 */
@Component
public class OllamaTriageModel implements TriageModel {

  /** Generation settings of the runtime contract, as the evaluation runner sends them. */
  static final Map<String, Object> OPTIONS = options();

  /** The runner's synthetic warm-up text (run_ollama.py), never a participant's: its reply is not stored. */
  public static final String WARM_UP_TEXT =
      "Teste de aquecimento: uma pessoa pede informações sobre o horário dos concertos.";

  /** A cold model takes about 51 to 58 s to load and read the prompt (#224, #227): the warm-up may take longer. */
  static final Duration WARM_UP_TIMEOUT = Duration.ofSeconds(300);

  private static final String KEEP_ALIVE = "10m";
  private static final Logger log = LoggerFactory.getLogger(OllamaTriageModel.class);

  private final String url;
  private final String model;
  private final Duration responseTimeout;
  private final JsonMapper json;
  private final HttpClient client;
  private final String prompt;
  private final JsonNode schema;

  public OllamaTriageModel(@Value("${farol.ai.url:}") String url, @Value("${farol.ai.model:qwen3:4b}") String model,
      @Value("${farol.ai.connect-timeout:2s}") Duration connectTimeout,
      @Value("${farol.ai.response-timeout:20s}") Duration responseTimeout, JsonMapper json) {
    this.url = url == null ? "" : url.strip().replaceAll("/+$", "");
    this.model = model;
    this.responseTimeout = responseTimeout;
    this.json = json;
    this.client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    this.prompt = resource("triage/system-prompt.txt");
    this.schema = json.readTree(resource("triage/output.schema.json"));
  }

  private static Map<String, Object> options() {
    var options = new LinkedHashMap<String, Object>();
    options.put("temperature", 0);
    options.put("seed", 42);
    options.put("num_ctx", 4096);
    options.put("num_predict", 256);
    return options;
  }

  private static String resource(String path) {
    try (var in = new ClassPathResource(path).getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public boolean configured() {
    return !url.isEmpty();
  }

  @Override
  public Object suggest(String description, Duration budget) throws TriageModelException {
    return output(post(chat(description), budget.compareTo(responseTimeout) < 0 ? budget : responseTimeout));
  }

  /**
   * One unscored call with the synthetic text, so the model is loaded and the prompt read before a participant's
   * request needs it (the cold start would miss the 20 s timeout). A failure is only logged.
   */
  public void warmUp() {
    try {
      output(post(chat(WARM_UP_TEXT), WARM_UP_TIMEOUT));
      log.info("AI model warmed up");
    } catch (TriageModelException | RuntimeException e) {
      log.warn("AI warm-up failed: {}", e.getMessage());
    }
  }

  /** Loads the model or renews its keep-alive without generating (Ollama: a chat with no messages). */
  public void keepLoaded() {
    var body = new LinkedHashMap<String, Object>();
    body.put("model", model);
    body.put("messages", List.of());
    body.put("keep_alive", KEEP_ALIVE);
    try {
      var response = post(body, responseTimeout);
      if (response.statusCode() != 200) {
        log.warn("AI keep-alive answered HTTP {}", response.statusCode());
      }
    } catch (TriageModelException | RuntimeException e) {
      log.warn("AI keep-alive failed: {}", e.getMessage());
    }
  }

  private Map<String, Object> chat(String text) {
    var body = new LinkedHashMap<String, Object>();
    body.put("model", model);
    body.put("stream", false);
    body.put("think", false);
    body.put("keep_alive", KEEP_ALIVE);
    body.put("format", schema);
    body.put("options", OPTIONS);
    // The bytes the evaluation runner sent: json.dumps({"text": text}, ensure_ascii=False) has ", " and ": "
    body.put("messages", List.of(Map.of("role", "system", "content", prompt),
        Map.of("role", "user", "content", "{\"text\": " + json.writeValueAsString(text) + "}")));
    return body;
  }

  private HttpResponse<String> post(Map<String, Object> body, Duration limit) throws TriageModelException {
    HttpRequest request;
    try {
      request = HttpRequest.newBuilder(URI.create(url + "/api/chat"))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
    } catch (IllegalArgumentException e) {
      throw new TriageModelException(NotTriagedReason.ProviderError, "malformed AI address");
    }
    var call = client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    try {
      // The whole call, not each read, is capped (the review of PR #225 found per-read timeouts in urllib)
      return call.get(limit.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      call.cancel(true);
      throw new TriageModelException(NotTriagedReason.Timeout, "no reply within " + limit.toMillis() + " ms");
    } catch (ExecutionException e) {
      throw new TriageModelException(NotTriagedReason.ProviderError, e.getCause().getClass().getSimpleName());
    } catch (InterruptedException e) {
      // Shutting down: not the model's fault, so nothing is stored and the message is not acknowledged
      call.cancel(true);
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while waiting for the AI", e);
    }
  }

  private Object output(HttpResponse<String> response) throws TriageModelException {
    if (response.statusCode() != 200) {
      throw new TriageModelException(NotTriagedReason.ProviderError, "HTTP " + response.statusCode());
    }
    JsonNode content;
    try {
      content = json.readTree(response.body()).path("message").path("content");
    } catch (JacksonException e) {
      throw new TriageModelException(NotTriagedReason.ProviderError, "reply is not JSON");
    }
    if (!content.isString()) {
      throw new TriageModelException(NotTriagedReason.ProviderError, "reply has no message content");
    }
    try {
      return json.readValue(content.asString(), Object.class);
    } catch (JacksonException e) {
      throw new TriageModelException(NotTriagedReason.InvalidOutput, "output is not JSON");
    }
  }
}
