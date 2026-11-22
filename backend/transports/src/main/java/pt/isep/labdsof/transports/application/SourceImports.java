package pt.isep.labdsof.transports.application;

import pt.isep.labdsof.transports.domain.SourceImport;

/** Port: the record of every read of the bus source (spec D5). */
public interface SourceImports {

  void record(SourceImport sourceImport);
}
