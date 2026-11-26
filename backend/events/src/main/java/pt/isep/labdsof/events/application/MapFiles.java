package pt.isep.labdsof.events.application;

/**
 * Port: the map images on the service's volume (API outline D6). The database keeps only the name of each file
 * (US-19 D9).
 */
public interface MapFiles {

  /** Writes the PNG under that name, creating the event's folder; an existing file is replaced. */
  void write(String file, byte[] png);

  /** Deletes the file if it is there, and never fails: it only ever cleans up after a failed upload (D9). */
  void delete(String file);
}
