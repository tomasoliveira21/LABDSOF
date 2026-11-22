package pt.isep.labdsof.reports.application;

import pt.isep.labdsof.reports.domain.NotTriagedReason;

/** Port: the triage counters (spec US-36 D7); the fallback rate is fallback / (fallback + suggested). */
public interface TriageMetrics {

  void suggested();

  void fallback(NotTriagedReason reason);
}
