package pt.isep.labdsof.transports.application;

import pt.isep.labdsof.transports.domain.SourceImport;

/** One run of the import (spec US-40 D8): what was recorded, how many late buses were left out, after how many calls. */
public record ImportRun(SourceImport sourceImport, int late, int attempts) {}
