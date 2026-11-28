package pt.isep.labdsof.authusers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

/** One Spring context on the shared containers, reused by every *IT that extends this class. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTest {

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    Infrastructure.properties().forEach((key, value) -> registry.add(key, () -> value));
  }

  @LocalServerPort protected int port;
  @Autowired protected JdbcClient jdbc;

  protected static final Jwks JWKS = Infrastructure.JWKS;

  /** An account with a display name, as {@code GET /me} would have created it (US-18 needs one to list). */
  protected void seedAccount(UUID userId, String displayName) {
    jdbc.sql("""
        INSERT INTO user_account (user_id, email, email_verified, display_name, status, version, created_at,
                                  updated_at)
        VALUES (:id, :email, true, :name, 'Active', 1, now(), now())
        """)
        .param("id", userId).param("email", userId + "@farol.test").param("name", displayName)
        .update();
  }

  /** A membership of an event, as {@code MembershipChanged} will create it from US-06 and US-12 on. */
  protected void seedMembership(UUID eventId, UUID userId, String role) {
    jdbc.sql("""
        INSERT INTO event_membership (event_id, user_id, role, version, created_at, updated_at)
        VALUES (:event, :user, :role, 1, now(), now())
        """)
        .param("event", eventId).param("user", userId).param("role", role)
        .update();
  }

  /** A team of an event in the local copy, as the {@code TeamChanged} consumer of #241 stores it. */
  protected void seedTeam(UUID eventId, UUID teamId, boolean deleted) {
    jdbc.sql("""
        INSERT INTO event_team_copy (team_id, event_id, payload, version)
        VALUES (:team, :event, CAST(:payload AS jsonb), 1)
        ON CONFLICT (team_id) DO UPDATE SET payload = excluded.payload
        """)
        .param("team", teamId).param("event", eventId)
        .param("payload", "{\"teamId\":\"%s\",\"name\":\"Nursing\",\"deleted\":%s,\"version\":1}"
            .formatted(teamId, deleted))
        .update();
  }

  private static final JsonMapper LOG_JSON = JsonMapper.builder().build();

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
