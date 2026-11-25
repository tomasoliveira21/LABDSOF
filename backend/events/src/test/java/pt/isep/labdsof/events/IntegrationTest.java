package pt.isep.labdsof.events;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pt.isep.labdsof.events.application.LocalCopies;
import pt.isep.labdsof.events.domain.Membership;
import tools.jackson.databind.json.JsonMapper;

/** One Spring context on the shared containers, reused by every *IT that extends this class. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTest {

  private static final JsonMapper LOG_JSON = JsonMapper.builder().build();

  /** Stands in for the volume of the deployment (US-19 D9): one directory for the whole context. */
  protected static final Path MAP_DIRECTORY = tempDirectory();

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    Infrastructure.properties().forEach((key, value) -> registry.add(key, () -> value));
    registry.add("farol.map.directory", MAP_DIRECTORY::toString);
  }

  private static Path tempDirectory() {
    try {
      return Files.createTempDirectory("farol-maps");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @LocalServerPort protected int port;
  @Autowired protected JdbcClient jdbc;
  @Autowired protected LocalCopies copies;

  protected static final Jwks JWKS = Infrastructure.JWKS;

  protected void member(UUID eventId, UUID userId, Membership.Role role, Membership.Status status, long version) {
    member(eventId, userId, role, status, version, Set.of());
  }

  /** A member who is in teams, which is what the permissions of a team grant them (US-19 D6). */
  protected void member(UUID eventId, UUID userId, Membership.Role role, Membership.Status status, long version,
      Set<UUID> teamIds) {
    copies.upsertIfNewer(new Membership(eventId, userId, role, status, teamIds, version));
  }

  /** Inserts an event with fixed fields; the caller supplies the status and what the checks tie to it. */
  protected UUID insertEvent(String status, String previousStatus, String joinCode) {
    var id = UUID.randomUUID();
    jdbc.sql("""
        INSERT INTO event (event_id, name, organiser_name, start_date, end_date, description, status,
                           previous_status, join_code, version, created_at, created_by, updated_at, updated_by)
        VALUES (:id, 'Queima das Fitas 2027', 'FAP', DATE '2027-05-02', DATE '2027-05-09', 'Semana academica',
                :status, :previous, :code, 1, now(), :by, now(), :by)
        """)
        .param("id", id).param("status", status).param("previous", previousStatus).param("code", joinCode)
        .param("by", UUID.randomUUID()).update();
    return id;
  }

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
