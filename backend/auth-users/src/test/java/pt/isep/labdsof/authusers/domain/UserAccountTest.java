package pt.isep.labdsof.authusers.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UserAccountTest {

  private static final UUID USER = UUID.fromString("11111111-2222-3333-4444-555555555555");
  private static final Instant NOW = Instant.parse("2026-11-17T10:00:00.123456789Z");

  @Test
  @DisplayName("191 AC-5 a missing name falls back to the part of the e-mail before @")
  void missingNameFallsBackToEmailLocalPart() {
    assertThat(UserAccount.displayNameFrom(null, "ana.silva@farol.test")).isEqualTo("ana.silva");
  }

  @ParameterizedTest(name = "name [{0}]")
  @ValueSource(strings = {"", "   ", " \t"})
  @DisplayName("191 AC-5 a blank name falls back to the part of the e-mail before @")
  void blankNameFallsBackToEmailLocalPart(String name) {
    assertThat(UserAccount.displayNameFrom(name, "ana.silva@farol.test")).isEqualTo("ana.silva");
  }

  @Test
  @DisplayName("191 AC-5 the name claim is trimmed")
  void nameIsTrimmed() {
    assertThat(UserAccount.displayNameFrom("  Ana Silva  ", "x@farol.test")).isEqualTo("Ana Silva");
  }

  @Test
  @DisplayName("191 AC-5 a name longer than 100 characters is cut to 100, one of 100 is kept")
  void longNameIsCutToHundred() {
    var hundred = "a".repeat(100);
    assertThat(UserAccount.displayNameFrom(hundred, "x@farol.test")).isEqualTo(hundred);
    assertThat(UserAccount.displayNameFrom(hundred + "b", "x@farol.test")).isEqualTo(hundred);
    assertThat(UserAccount.displayNameFrom("b".repeat(500), "x@farol.test")).isEqualTo("b".repeat(100));
    assertThat(UserAccount.displayNameFrom("a".repeat(99), "x@farol.test")).isEqualTo("a".repeat(99));
  }

  @Test
  @DisplayName("191 AC-5 the cut counts characters, not UTF-16 units, so an emoji is never split")
  void cutCountsCodePoints() {
    var emoji = "😀"; // one character, two UTF-16 units
    var name = emoji.repeat(101);
    var shown = UserAccount.displayNameFrom(name, "x@farol.test");
    assertThat(shown).isEqualTo(emoji.repeat(100));
    assertThat(shown.codePointCount(0, shown.length())).isEqualTo(100);
    assertThat(UserAccount.displayNameFrom(emoji.repeat(100), "x@farol.test")).isEqualTo(emoji.repeat(100));
  }

  @Test
  @DisplayName("191 AC-5 an e-mail with nothing before @ gives the whole e-mail as name")
  void emailWithoutLocalPartGivesWholeEmail() {
    assertThat(UserAccount.displayNameFrom(null, "@farol.test")).isEqualTo("@farol.test");
    assertThat(UserAccount.displayNameFrom(null, "no-at-sign")).isEqualTo("no-at-sign");
    assertThat(UserAccount.displayNameFrom(null, "a@b")).isEqualTo("a");
  }

  @Test
  @DisplayName("191 AC-5 a new account is Active at version 1 with the token's id and e-mail")
  void newAccountIsActiveAtVersionOne() {
    var account = UserAccount.provision(USER, "ana@farol.test", "Ana", NOW);

    assertThat(account.userId()).isEqualTo(USER);
    assertThat(account.email()).isEqualTo("ana@farol.test");
    assertThat(account.displayName()).isEqualTo("Ana");
    assertThat(account.status()).isEqualTo(AccountStatus.Active);
    assertThat(account.version()).isEqualTo(1);
  }

  @Test
  @DisplayName("191 AC-5 a new e-mail keeps the name, status, version and creation time")
  void newEmailKeepsTheRest() {
    var original = new UserAccount(USER, "old@farol.test", "Ana", AccountStatus.Active, 7,
        Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-02T00:00:00Z"));

    var changed = original.withEmail("new@farol.test", NOW);

    assertThat(changed.email()).isEqualTo("new@farol.test");
    assertThat(changed.displayName()).isEqualTo("Ana");
    assertThat(changed.status()).isEqualTo(AccountStatus.Active);
    assertThat(changed.version()).isEqualTo(7);
    assertThat(changed.userId()).isEqualTo(USER);
    assertThat(changed.createdAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    assertThat(changed.updatedAt()).isEqualTo(Instant.parse("2026-11-17T10:00:00.123456Z"));
  }

  @Test
  @DisplayName("191 AC-5 times are kept to the microsecond, as the database stores them")
  void timesAreKeptToTheMicrosecond() {
    var account = UserAccount.provision(USER, "ana@farol.test", "Ana", NOW);

    assertThat(account.createdAt()).isEqualTo(Instant.parse("2026-11-17T10:00:00.123456Z"));
    assertThat(account.updatedAt()).isEqualTo(account.createdAt());
    assertThat(Duration.between(account.createdAt(), NOW)).isEqualTo(Duration.ofNanos(789));
  }
}
