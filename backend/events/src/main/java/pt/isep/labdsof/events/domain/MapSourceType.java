package pt.isep.labdsof.events.domain;

import java.util.Arrays;

/**
 * What the uploaded map file is, decided by its first bytes and never by its name or its {@code Content-Type}
 * (US-19 D1, ADR-007 decision 2, assessment M-11). Only these three are accepted; SVG never is, because it can
 * carry scripts. The codes are the ones {@code venue_map_version_source_type} and the contract use.
 */
public enum MapSourceType {

  PNG("image/png", 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A),
  JPEG("image/jpeg", 0xFF, 0xD8, 0xFF),
  PDF("application/pdf", '%', 'P', 'D', 'F', '-');

  private final String contentType;
  private final byte[] signature;

  MapSourceType(String contentType, int... signature) {
    this.contentType = contentType;
    this.signature = new byte[signature.length];
    for (var i = 0; i < signature.length; i++) {
      this.signature[i] = (byte) signature[i];
    }
  }

  public String contentType() {
    return contentType;
  }

  public boolean isPdf() {
    return this == PDF;
  }

  /**
   * The type of this content, or {@link InvalidMapFileException} for anything else — an SVG, an archive, a text
   * file, a PNG renamed to {@code .jpg}, an empty file, or a file shorter than the signature it claims.
   */
  public static MapSourceType ofContent(byte[] content) {
    if (content == null) {
      throw new InvalidMapFileException("no file");
    }
    return Arrays.stream(values()).filter(type -> type.matches(content)).findFirst()
        .orElseThrow(() -> new InvalidMapFileException("unsupported file type"));
  }

  private boolean matches(byte[] content) {
    if (content.length < signature.length) {
      return false;
    }
    return Arrays.equals(content, 0, signature.length, signature, 0, signature.length);
  }
}
