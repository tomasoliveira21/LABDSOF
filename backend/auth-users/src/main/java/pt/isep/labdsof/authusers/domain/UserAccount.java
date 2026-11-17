package pt.isep.labdsof.authusers.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/** A Farol account, created the first time its token reaches {@code GET /me} (ADR-006 decision 6, spec D5). */
public record UserAccount(UUID userId, String email, String displayName, AccountStatus status,
    long version, Instant createdAt, Instant updatedAt) {

  public static final int MAX_DISPLAY_NAME = 100;

  public UserAccount {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(email, "email");
    Objects.requireNonNull(displayName, "displayName");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(createdAt, "createdAt");
    Objects.requireNonNull(updatedAt, "updatedAt");
  }

  public static UserAccount provision(UUID userId, String email, String name, Instant now) {
    var stored = now.truncatedTo(ChronoUnit.MICROS);
    return new UserAccount(userId, email, displayNameFrom(name, email), AccountStatus.Active, 1, stored, stored);
  }

  /** The name claim, trimmed and cut to 100 characters, or the part of the e-mail before {@code @}. */
  public static String displayNameFrom(String name, String email) {
    var candidate = name == null || name.isBlank() ? localPart(email) : name.strip();
    int length = candidate.codePointCount(0, candidate.length());
    return candidate.substring(0, candidate.offsetByCodePoints(0, Math.min(length, MAX_DISPLAY_NAME)));
  }

  /** An e-mail change publishes nothing (UserProfileChanged carries only the display name), so the version stays. */
  public UserAccount withEmail(String newEmail, Instant now) {
    return new UserAccount(userId, newEmail, displayName, status, version, createdAt,
        now.truncatedTo(ChronoUnit.MICROS));
  }

  private static String localPart(String email) {
    int at = email.lastIndexOf('@');
    return at > 0 ? email.substring(0, at) : email;
  }
}
