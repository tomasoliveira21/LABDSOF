package pt.isep.labdsof.authusers.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/** A Farol account, created the first time its token reaches {@code GET /me} (ADR-006 decision 6, spec D5). */
public record UserAccount(UUID userId, String email, boolean emailVerified, String displayName, AccountStatus status,
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

  public static UserAccount provision(UUID userId, String email, boolean emailVerified, String name, Instant now) {
    var stored = now.truncatedTo(ChronoUnit.MICROS);
    return new UserAccount(userId, email, emailVerified, displayNameFrom(name, email), AccountStatus.Active, 1, stored,
        stored);
  }

  /**
   * The {@code name} claim, trimmed and cut to 100 characters, or the part of the e-mail before {@code @}. Never
   * blank, because {@code display_name} is published in {@code UserProfileChanged} and its column refuses a blank
   * value; and never with a NUL, which PostgreSQL text columns reject (it would make every {@code GET /me} a 500).
   */
  public static String displayNameFrom(String name, String email) {
    var fromName = withoutNul(name);
    var candidate = fromName.isEmpty() ? localPart(email) : fromName;
    int length = candidate.codePointCount(0, candidate.length());
    return candidate.substring(0, candidate.offsetByCodePoints(0, Math.min(length, MAX_DISPLAY_NAME)));
  }

  /** An e-mail change publishes nothing (UserProfileChanged carries only the display name), so the version stays. */
  public UserAccount withEmail(String newEmail, boolean verified, Instant now) {
    return new UserAccount(userId, newEmail, verified, displayName, status, version, createdAt,
        now.truncatedTo(ChronoUnit.MICROS));
  }

  /**
   * The part before the last {@code @}, or the whole e-mail when there is nothing before it ({@code @farol.test}).
   * Never blank for an e-mail that is not blank itself, which {@code MeService} refuses before this is reached: the
   * e-mail is stripped first, so anything left before the {@code @} starts with a character that is not a space.
   */
  private static String localPart(String email) {
    var cleaned = withoutNul(email);
    int at = cleaned.lastIndexOf('@');
    return at > 0 ? withoutNul(cleaned.substring(0, at)) : cleaned;
  }

  private static String withoutNul(String value) {
    return value == null ? "" : value.replace("\0", "").strip();
  }
}
