package pt.isep.labdsof.transports.adapters.out.audit;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import pt.isep.labdsof.transports.application.AccessAudit.Refusal;

class LoggingAccessAuditTest {

  @Test
  void recordsRefusalWithTheRequestCorrelationId() {
    var user = UUID.randomUUID();
    var event = UUID.randomUUID();
    var at = Instant.parse("2027-05-08T23:14:05Z");
    var logger = (Logger) LoggerFactory.getLogger(LoggingAccessAudit.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);

    try (var ignored = MDC.putCloseable("correlationId", "audit-test-correlation")) {
      new LoggingAccessAudit().refused(user, event, at, Refusal.NOT_A_MEMBER);

      assertThat(appender.list).singleElement().satisfies(entry -> {
        assertThat(entry.getLevel()).isEqualTo(Level.WARN);
        assertThat(entry.getFormattedMessage()).isEqualTo("Refused access to an event (US-14)");
        assertThat(entry.getMDCPropertyMap()).containsEntry("correlationId", "audit-test-correlation");
        assertThat(entry.getKeyValuePairs()).extracting(pair -> Map.entry(pair.key, pair.value))
            .containsExactlyInAnyOrder(Map.entry("userId", user.toString()),
                Map.entry("eventId", event.toString()), Map.entry("at", at.toString()),
                Map.entry("refusal", Refusal.NOT_A_MEMBER.name()));
      });
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }
}
