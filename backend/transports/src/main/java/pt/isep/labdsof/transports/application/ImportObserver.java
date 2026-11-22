package pt.isep.labdsof.transports.application;

/** Port: told of every recorded run of the import, for its metrics (spec US-40 D10). */
public interface ImportObserver {

  void observed(ImportRun run);
}
