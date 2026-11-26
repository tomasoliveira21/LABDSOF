package pt.isep.labdsof.transports;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The messages the consumer tests publish, written as the producers publish them (API outline 7.1 and 7.4), so
 * {@code ConsumedContractIT} can check them against contracts/events.
 */
public final class Fixtures {

  private Fixtures() {}

  public static String envelope(UUID messageId, String type, String producer, UUID eventId, String payload) {
    return """
        {"messageId":"%s","type":"%s","schemaVersion":1,"occurredAt":"2027-05-08T23:14:05Z",\
        "producer":"%s","eventId":%s,"actorUserId":"","payload":%s}"""
        .formatted(messageId, type, producer, eventId == null ? "null" : "\"" + eventId + "\"", payload);
  }

  /** {@code status} null leaves the field out (spec D3: read as Active). */
  public static String membershipPayload(UUID userId, String role, String status, long version, UUID... teams) {
    var statusField = status == null ? "" : "\"status\":\"" + status + "\",";
    return "{\"userId\":\"%s\",\"role\":\"%s\",%s\"teamIds\":[%s],\"version\":%d}"
        .formatted(userId, role, statusField, quoted(teams), version);
  }

  public static String eventPayload(String status, long version) {
    return """
        {"name":"Queima das Fitas 2027","organiserName":"FAP","dates":{"start":"2027-05-02","end":"2027-05-09"},\
        "status":"%s","version":%d}""".formatted(status, version);
  }

  /** Each permission carries {@code reportTypes: []} (only Reports.Review may carry any, Events #192 D6). */
  public static String teamPayload(UUID teamId, boolean deleted, long version, String... permissions) {
    var list = Arrays.stream(permissions).map(p -> "{\"permission\":\"" + p + "\",\"reportTypes\":[]}")
        .collect(Collectors.joining(","));
    return """
        {"teamId":"%s","name":"Logistics","permissions":[%s],"handledCategories":["Infrastructure"],\
        "deleted":%s,"version":%d}""".formatted(teamId, list, deleted, version);
  }

  public static String membershipChanged(UUID messageId, UUID eventId, String payload) {
    return envelope(messageId, "MembershipChanged", "auth-users", eventId, payload);
  }

  public static String eventChanged(UUID messageId, UUID eventId, String payload) {
    return envelope(messageId, "EventChanged", "events", eventId, payload);
  }

  public static String teamChanged(UUID messageId, UUID eventId, String payload) {
    return envelope(messageId, "TeamChanged", "events", eventId, payload);
  }

  private static String quoted(UUID... ids) {
    return Arrays.stream(ids).map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
  }
}
