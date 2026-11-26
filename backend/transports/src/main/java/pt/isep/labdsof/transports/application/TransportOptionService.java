package pt.isep.labdsof.transports.application;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.labdsof.transports.domain.BusRoute;
import pt.isep.labdsof.transports.domain.EventAccess;
import pt.isep.labdsof.transports.domain.EventCopy;
import pt.isep.labdsof.transports.domain.EventStatus;
import pt.isep.labdsof.transports.domain.Membership;
import pt.isep.labdsof.transports.domain.NewBusRoute;
import pt.isep.labdsof.transports.domain.Team;

/**
 * Adds an event's external bus routes (spec US-41 D2 to D4, D15). The answers come in the order of D3: 404, 403, 501,
 * 409, so an outsider never learns more than 404.
 */
@Service
public class TransportOptionService {

  private static final Logger log = LoggerFactory.getLogger(TransportOptionService.class);

  private final LocalCopies copies;
  private final TransportOptions options;
  private final Clock clock;

  public TransportOptionService(LocalCopies copies, TransportOptions options, Clock clock) {
    this.copies = copies;
    this.options = options;
    this.clock = clock;
  }

  /**
   * @throws EventNotFoundException, ForbiddenException, EventClosedException, BusRouteTakenException or
   *     BusRouteLimitException
   */
  @Transactional
  public BusRoute addBusRoute(UUID eventId, UUID userId, NewBusRoute route) {
    var event = mayEditTransport(eventId, userId);
    if (event.status() == EventStatus.Closed) {
      throw new EventClosedException();
    }
    var stored = new BusRoute(UUID.randomUUID(), eventId, route.externalRoute(), route.name(), clock.instant(),
        userId);
    switch (options.addBusRoute(stored, NewBusRoute.MAX_PER_EVENT)) {
      case TAKEN -> throw new BusRouteTakenException();
      case FULL -> throw new BusRouteLimitException();
      case ADDED -> log.atInfo()
          .addKeyValue("eventId", eventId.toString())
          .addKeyValue("optionId", stored.optionId().toString())
          .addKeyValue("externalRoute", stored.externalRoute())
          .addKeyValue("userId", userId.toString())
          .log("Bus route added to the event");
    }
    return stored;
  }

  /**
   * A shuttle arrives with US-37: 501, but only for a caller who may change the event's transport.
   *
   * @throws EventNotFoundException, ForbiddenException or ShuttleNotYetException, always
   */
  @Transactional(readOnly = true)
  public void refuseShuttle(UUID eventId, UUID userId) {
    mayEditTransport(eventId, userId);
    throw new ShuttleNotYetException();
  }

  private EventCopy mayEditTransport(UUID eventId, UUID userId) {
    var event = copies.event(eventId);
    Optional<Membership> membership = copies.membership(eventId, userId);
    List<Team> teams = membership
        .map(m -> m.teamIds().stream().map(copies::team).flatMap(Optional::stream).toList())
        .orElse(List.of());
    return switch (EventAccess.canEditTransport(membership, event, teams)) {
      case NOT_FOUND -> throw new EventNotFoundException();
      case FORBIDDEN -> throw new ForbiddenException();
      case ALLOWED -> event.orElseThrow();
    };
  }
}
