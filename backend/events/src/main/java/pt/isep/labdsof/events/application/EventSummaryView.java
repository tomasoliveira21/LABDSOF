package pt.isep.labdsof.events.application;

import java.util.List;
import java.util.UUID;
import pt.isep.labdsof.events.domain.DateRange;
import pt.isep.labdsof.events.domain.EventStatus;

/**
 * What the FarolAdmin sees of an event (US-07 AC-1): its name, its dates, its organisation, its EventAdmins and
 * its status — and <em>nothing</em> else. No description, no join code, no team, no map and no participant, which
 * is what {@code features.md} §4.1 allows the role and what US-07 AC-2 and US-14 refuse it everywhere else.
 *
 * <p>{@code eventAdmins} is empty for an event whose first EventAdmin has not been named yet, which is the normal
 * state between {@code POST /events} and the name call of US-06, and the state US-08 exists to fix (US-06 D12).
 */
public record EventSummaryView(UUID eventId, String name, String organiserName, DateRange dates,
    EventStatus status, List<UUID> eventAdmins) {

  public EventSummaryView {
    eventAdmins = List.copyOf(eventAdmins);
  }
}
