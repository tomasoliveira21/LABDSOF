package pt.isep.labdsof.reports.application;

import pt.isep.labdsof.reports.domain.NotTriagedReason;
import pt.isep.labdsof.reports.domain.TriageState;

/** Port: the triage counters (spec US-36 D7); the fallback rate is fallback / (fallback + suggested). */
public interface TriageMetrics {

  void suggested();

  void fallback(NotTriagedReason reason);

  /** A member's first decision on a stored suggestion: Accepted or Changed (spec US-35 D6). */
  void decided(TriageState decision);
}
