package pt.isep.labdsof.events.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** The type of an upload, from its content only (US-19 D1). */
class MapSourceTypeTest {

  private static byte[] bytes(String hexOrText) {
    return hexOrText.startsWith("hex:")
        ? HexFormat.of().parseHex(hexOrText.substring(4))
        : hexOrText.getBytes(StandardCharsets.UTF_8);
  }

  @ParameterizedTest(name = "{1}")
  @CsvSource({
      "hex:89504E470D0A1A0A0000, PNG",
      "hex:FFD8FFE000104A464946, JPEG",
      "%PDF-1.7 trailer, PDF",
  })
  @DisplayName("US-19 AC-1 only PNG, JPEG and PDF signatures are accepted")
  void signaturesAreAccepted(String content, MapSourceType expected) {
    assertThat(MapSourceType.ofContent(bytes(content))).isEqualTo(expected);
    assertThat(expected.isPdf()).isEqualTo(expected == MapSourceType.PDF);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(strings = {
      "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>", // never, whatever it draws (ADR-007 decision 2)
      "hex:504B0304", // a ZIP, which an SVGZ or an Office file is
      "hex:4D5A9000", // a Windows executable
      "hex:89504E47", // a PNG signature cut short
      "hex:FFD8", // a JPEG signature cut short
      "%PDF", // the PDF signature without its dash
      "Queima das Fitas", // a text file
      "", // an empty file
  })
  @DisplayName("US-19 AC-2 only PNG, JPEG and PDF signatures are accepted")
  void everythingElseIsRefused(String content) {
    assertThatThrownBy(() -> MapSourceType.ofContent(bytes(content)))
        .isInstanceOf(InvalidMapFileException.class).hasMessage("unsupported file type");
  }

  @Test
  @DisplayName("US-19 AC-2 no file at all is refused")
  void noFileIsRefused() {
    assertThatThrownBy(() -> MapSourceType.ofContent(null))
        .isInstanceOf(InvalidMapFileException.class).hasMessage("no file");
  }

  @Test
  @DisplayName("US-19 AC-1 a file exactly as long as its signature is accepted")
  void aFileAsLongAsItsSignatureIsAccepted() {
    assertThat(MapSourceType.ofContent(bytes("hex:89504E470D0A1A0A"))).isEqualTo(MapSourceType.PNG);
    assertThat(MapSourceType.ofContent(bytes("hex:FFD8FF"))).isEqualTo(MapSourceType.JPEG);
    assertThat(MapSourceType.ofContent(bytes("%PDF-"))).isEqualTo(MapSourceType.PDF);
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({"PNG, image/png", "JPEG, image/jpeg", "PDF, application/pdf"})
  @DisplayName("US-19 AC-1 the content types are the ones the table and the contract use")
  void contentTypesMatchTheTable(MapSourceType type, String contentType) {
    assertThat(type.contentType()).isEqualTo(contentType);
  }
}
