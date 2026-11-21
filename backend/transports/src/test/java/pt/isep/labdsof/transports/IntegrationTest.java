package pt.isep.labdsof.transports;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pt.isep.labdsof.transports.application.LocalCopies;
import tools.jackson.databind.json.JsonMapper;

/** One Spring context on the shared containers, reused by every *IT that extends this class. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTest {

  private static final JsonMapper LOG_JSON = JsonMapper.builder().build();

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    Infrastructure.properties().forEach((key, value) -> registry.add(key, () -> value));
  }

  @LocalServerPort protected int port;
  @Autowired protected JdbcClient jdbc;
  @Autowired protected LocalCopies copies;

  /** The JSON log lines written so far (other lines, such as the banner, are skipped). */
  @SuppressWarnings("unchecked")
  protected static List<Map<String, Object>> logLines(CapturedOutput output) {
    var lines = new ArrayList<Map<String, Object>>();
    for (var line : output.getOut().split("\\R")) {
      if (line.startsWith("{")) {
        try {
          lines.add(LOG_JSON.readValue(line, Map.class));
        } catch (RuntimeException e) {
          // not a complete JSON line
        }
      }
    }
    return lines;
  }
}
