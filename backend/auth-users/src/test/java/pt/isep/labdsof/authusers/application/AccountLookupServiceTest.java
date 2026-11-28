package pt.isep.labdsof.authusers.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.isep.labdsof.authusers.application.InMemoryPorts.Lookup;
import pt.isep.labdsof.authusers.domain.GlobalRole;
import pt.isep.labdsof.authusers.domain.UserAccount;

/** US-06 D7: finding the account to name EventAdmin, with the ports faked (spec §4 F5). */
class AccountLookupServiceTest {

  private static final Instant NOW = Instant.parse("2027-04-30T10:15:30.123456Z");
  private static final String EMAIL = "organiser@fap.test";

  private final InMemoryPorts ports = new InMemoryPorts();
  private final AccountLookupService service =
      new AccountLookupService(ports, ports, ports, Clock.fixed(NOW, ZoneOffset.UTC));
  private final UUID admin = UUID.randomUUID();
  private final UUID organiser = UUID.randomUUID();

  private void farolAdmin() {
    ports.grantBySystem(admin, GlobalRole.FarolAdmin, NOW);
  }

  private void account(String email) {
    var provisioned = UserAccount.provision(organiser, email, true, "Sofia Organiser", NOW);
    ports.accounts.put(organiser, provisioned);
  }

  @Test
  @DisplayName("US-06 AC-1 the FarolAdmin finds the account by its exact e-mail and gets its id and name")
  void theFarolAdminFindsTheAccount() {
    farolAdmin();
    account(EMAIL);

    var found = service.byEmail(admin, EMAIL);

    assertThat(found.userId()).isEqualTo(organiser);
    assertThat(found.displayName()).isEqualTo("Sofia Organiser");
    assertThat(ports.lookups).containsExactly(new Lookup(admin, AccountLookupService.hash(EMAIL), true, NOW));
  }

  @Test
  @DisplayName("US-06 AC-1 the match ignores case, as a verified e-mail is already treated elsewhere")
  void theMatchIgnoresCase() {
    farolAdmin();
    account(EMAIL);

    assertThat(service.byEmail(admin, "Organiser@FAP.Test").userId()).isEqualTo(organiser);
    // The same address in another case is the same line in the trail, because the hash is of the lower case
    assertThat(ports.lookups).singleElement()
        .satisfies(lookup -> assertThat(lookup.emailHash()).isEqualTo(AccountLookupService.hash(EMAIL)));
  }

  @Test
  @DisplayName("US-06 AC-1 an address no account holds is 404, and the attempt is still recorded as a miss")
  void aMissIsRecordedToo() {
    farolAdmin();
    account(EMAIL);

    assertThatThrownBy(() -> service.byEmail(admin, "nobody@fap.test"))
        .isInstanceOf(AccountNotFoundException.class);

    assertThat(ports.lookups)
        .containsExactly(new Lookup(admin, AccountLookupService.hash("nobody@fap.test"), false, NOW));
  }

  @Test
  @DisplayName("US-06 AC-1 a partial address finds nothing: the match is exact, never a directory search")
  void theMatchIsExactNotAPrefix() {
    farolAdmin();
    account(EMAIL);

    assertThatThrownBy(() -> service.byEmail(admin, "organiser")).isInstanceOf(AccountNotFoundException.class);
    assertThatThrownBy(() -> service.byEmail(admin, "@fap.test")).isInstanceOf(AccountNotFoundException.class);
    assertThatThrownBy(() -> service.byEmail(admin, "organiser@fap.test.pt"))
        .isInstanceOf(AccountNotFoundException.class);
  }

  @Test
  @DisplayName("US-06 AC-2 a caller who is not the FarolAdmin learns nothing, and the attempt is recorded")
  void aCallerWhoIsNotTheFarolAdminLearnsNothing() {
    account(EMAIL);
    var user = UUID.randomUUID();

    assertThatThrownBy(() -> service.byEmail(user, EMAIL)).isInstanceOf(NotAFarolAdminException.class)
        .hasMessage("not a FarolAdmin");

    // Recorded as a miss whether the address exists or not, so the refusal leaks nothing through the trail
    assertThat(ports.lookups).containsExactly(new Lookup(user, AccountLookupService.hash(EMAIL), false, NOW));
  }

  @Test
  @DisplayName("US-06 D7 the hash is stable, differs per address and is never the address itself")
  void theHashIsStableAndOpaque() {
    var hash = AccountLookupService.hash(EMAIL);

    assertThat(hash).isEqualTo(AccountLookupService.hash(EMAIL))
        .isEqualTo(AccountLookupService.hash(EMAIL.toUpperCase(java.util.Locale.ROOT)))
        .isNotEqualTo(AccountLookupService.hash("other@fap.test"))
        .doesNotContain("organiser").doesNotContain("fap.test").doesNotContain("@");
    // URL-safe Base64 of SHA-256, so it is one word in a log line
    assertThat(hash).hasSize(43).doesNotContain("=").doesNotContain("+").doesNotContain("/");
  }
}
