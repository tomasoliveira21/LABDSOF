package pt.isep.labdsof.authusers.application;

import java.util.List;
import java.util.UUID;

public interface MyMemberships {

  enum Status { Draft, Published, Suspended, Closed }

  record Entry(UUID eventId, String eventName, Status eventStatus, String role, List<UUID> teamIds) {}

  List<Entry> findForUser(UUID userId);
}
