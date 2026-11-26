package pt.isep.labdsof.transports.application;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.labdsof.transports.domain.BusView;
import pt.isep.labdsof.transports.domain.EventAccess;
import pt.isep.labdsof.transports.domain.Venue;

/** What a member sees of the event's buses (spec US-41 D5 to D8): computed on each request, nothing stored. */
@Service
public class BusObservationService {

  private final LocalCopies copies;
  private final TransportOptions options;
  private final BusData buses;
  private final SourceImports imports;
  private final Clock clock;
  private final Venue venue;
  private final Duration freshness;

  public BusObservationService(LocalCopies copies, TransportOptions options, BusData buses, SourceImports imports,
      Clock clock, @Value("${farol.transport.source.coords}") String coords,
      @Value("${farol.transport.source.freshness}") Duration freshness) {
    this.copies = copies;
    this.options = options;
    this.buses = buses;
    this.imports = imports;
    this.clock = clock;
    this.venue = Venue.parse(coords);
    this.freshness = freshness;
  }

  /** @throws EventNotFoundException when the caller may not read the event (D5, AC-3) */
  @Transactional(readOnly = true)
  public BusView view(UUID eventId, UUID userId) {
    if (!EventAccess.canRead(copies.membership(eventId, userId), copies.event(eventId))) {
      throw new EventNotFoundException();
    }
    return BusView.of(venue, options.busRoutes(eventId), buses.inUse(), imports.lastSucceededAt(), clock.instant(),
        freshness);
  }
}
