package pt.isep.labdsof.authusers.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class BootstrapAdminTest {

  private final BootstrapAdmin bootstrap = BootstrapAdmin.of("daniel@farol.test");

  @Test
  @DisplayName("191 AC-6 the bootstrap e-mail with email_verified true grants FarolAdmin")
  void verifiedBootstrapEmailGrants() {
    assertThat(bootstrap.grants("daniel@farol.test", true)).isTrue();
  }

  @Test
  @DisplayName("191 AC-6 the bootstrap e-mail matches ignoring case and surrounding spaces")
  void matchesIgnoringCaseAndSpaces() {
    assertThat(bootstrap.grants("  Daniel@FAROL.test ", true)).isTrue();
    assertThat(BootstrapAdmin.of("  DANIEL@farol.test\t").grants("daniel@farol.test", true)).isTrue();
  }

  @ParameterizedTest(name = "email_verified {0}")
  @NullSource
  @ValueSource(booleans = false)
  @DisplayName("191 AC-6 an unverified bootstrap e-mail, or one without email_verified, grants nothing")
  void unverifiedGrantsNothing(Boolean verified) {
    assertThat(bootstrap.grants("daniel@farol.test", verified)).isFalse();
  }

  @ParameterizedTest(name = "setting [{0}]")
  @NullAndEmptySource
  @ValueSource(strings = "   ")
  @DisplayName("191 AC-6 an empty or blank bootstrap setting grants nobody")
  void emptySettingGrantsNobody(String setting) {
    assertThat(BootstrapAdmin.of(setting).grants("daniel@farol.test", true)).isFalse();
    assertThat(BootstrapAdmin.of(setting).email()).isEmpty();
  }

  @Test
  @DisplayName("191 AC-6 another e-mail grants nothing")
  void anotherEmailGrantsNothing() {
    assertThat(bootstrap.grants("other@farol.test", true)).isFalse();
  }
}
