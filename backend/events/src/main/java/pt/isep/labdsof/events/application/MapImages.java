package pt.isep.labdsof.events.application;

import pt.isep.labdsof.events.domain.MapSourceType;

/**
 * Port: the one place that decodes an uploaded file (US-19 D2, ADR-007 decision 2). Everything that comes out of
 * it is a PNG of at most 4096 px on its long side, re-encoded, so no metadata and no active content of the source
 * survives.
 */
public interface MapImages {

  /**
   * The PNG of this upload. Throws
   * {@link pt.isep.labdsof.events.domain.InvalidMapFileException} for a file that is not the map it claims to be:
   * an image above the pixel limit of D3, a PDF that is not a single unencrypted page, a truncated or broken file,
   * or a render that does not finish in time (D4).
   */
  byte[] toPng(byte[] source, MapSourceType type);
}
