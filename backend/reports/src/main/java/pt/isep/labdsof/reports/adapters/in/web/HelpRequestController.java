package pt.isep.labdsof.reports.adapters.in.web;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.isep.labdsof.reports.application.ClientRequestConflictException;
import pt.isep.labdsof.reports.application.ForbiddenException;
import pt.isep.labdsof.reports.application.HelpRequestService;
import pt.isep.labdsof.reports.application.NotFoundException;
import pt.isep.labdsof.reports.application.TriageService;
import pt.isep.labdsof.reports.domain.Actor;
import pt.isep.labdsof.reports.domain.Category;
import pt.isep.labdsof.reports.domain.HelpRequest;
import pt.isep.labdsof.reports.domain.HelpRequestStatus;
import pt.isep.labdsof.reports.domain.HistoryEntry;
import pt.isep.labdsof.reports.domain.InvalidHelpRequestException;
import pt.isep.labdsof.reports.domain.Location;
import pt.isep.labdsof.reports.domain.NotTriagedReason;
import pt.isep.labdsof.reports.domain.Priority;
import pt.isep.labdsof.reports.domain.RequestConflictException;
import pt.isep.labdsof.reports.domain.TriageState;

/**
 * Help requests, the control room's triage decisions, acknowledge, close and history, and the triage status (API
 * outline §4, contracts/openapi.yaml). The caller is always the token's sub.
 */
@RestController
@RequestMapping("/api/v1/events/{eventId}")
class HelpRequestController {

  private final HelpRequestService service;
  private final TriageService triage;

  HelpRequestController(HelpRequestService service, TriageService triage) {
    this.service = service;
    this.triage = triage;
  }

  record SubmitBody(
      @NotNull UUID clientRequestId,
      @NotNull @Valid LocationBody location,
      @NotBlank String description) {} // length in code points: checked by HelpRequest

  record LocationBody(
      @NotNull @Min(1) Integer mapVersion,
      @NotNull @DecimalMin("0") @DecimalMax("1") Double x,
      @NotNull @DecimalMin("0") @DecimalMax("1") Double y) {}

  /** What the participant sees (US-24). */
  record ParticipantView(UUID id, String description, HelpRequestStatus status, Instant updatedAt) {}

  /** What the control room sees (API outline §4.1). */
  record ControlRoomView(UUID id, UUID eventId, Category category, LocationBody location,
      String description, Priority priority, HelpRequestStatus status, Instant submittedAt,
      Instant updatedAt, UUID defaultTeamId, UUID routedTeamId, TriageView triage) {}

  record TriageView(TriageState state, Category suggestedCategory, Priority suggestedPriority,
      String suggestedReason, NotTriagedReason notTriagedReason) {}

  @PostMapping(value = "/help-requests", consumes = "application/json", produces = "application/json")
  ResponseEntity<ParticipantView> submit(@PathVariable UUID eventId, @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody SubmitBody body) {
    var location = new Location(body.location().mapVersion(), body.location().x(), body.location().y());
    var result = service.submit(eventId, jwt.getSubject(), body.clientRequestId(), body.description(),
        location);
    var request = result.request();
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .location(URI.create("/api/v1/events/" + eventId + "/help-requests/" + request.id()))
        .body(participant(request));
  }

  /** One poll of a live list (US-24, US-26): the items, and the serverTime to send as updatedSince next time. */
  record ListView<T>(List<T> items, Instant serverTime) {}

  /** The control room's list (US-26), or with mine=true the caller's own requests in the participant view (US-24). */
  @GetMapping(value = "/help-requests", produces = "application/json")
  ListView<Record> list(@PathVariable UUID eventId, @RequestParam(required = false) Instant updatedSince,
      @RequestParam(defaultValue = "false") boolean mine, @AuthenticationPrincipal Jwt jwt) {
    if (mine) {
      var own = service.mine(eventId, jwt.getSubject(), updatedSince);
      return new ListView<>(own.items().stream().<Record>map(HelpRequestController::participant).toList(),
          own.serverTime());
    }
    var changes = service.list(eventId, jwt.getSubject(), updatedSince);
    return new ListView<>(changes.items().stream().<Record>map(HelpRequestController::controlRoom).toList(),
        changes.serverTime());
  }

  @GetMapping(value = "/help-requests/{requestId}", produces = "application/json")
  Object get(@PathVariable UUID eventId, @PathVariable UUID requestId, @AuthenticationPrincipal Jwt jwt) {
    var read = service.get(eventId, jwt.getSubject(), requestId);
    return read.controlRoom() ? controlRoom(read.request()) : participant(read.request());
  }

  /**
   * What a member sets (US-35 AC-2, spec D2): either or both. A field left out keeps its setter uncalled; an explicit
   * null is refused (400), as the contract says. Setters, not a record: a record's missing field also reads as null.
   */
  static final class ChangeBody {
    private Category category;
    private Priority priority;

    @JsonSetter(nulls = Nulls.FAIL)
    void setCategory(Category category) {
      this.category = category;
    }

    @JsonSetter(nulls = Nulls.FAIL)
    void setPriority(Priority priority) {
      this.priority = priority;
    }
  }

  /** The control room takes the AI's suggestion as the request's values (US-35 AC-1, spec D1). */
  @PostMapping(value = "/help-requests/{requestId}/accept-suggestion", produces = "application/json")
  ControlRoomView acceptSuggestion(@PathVariable UUID eventId, @PathVariable UUID requestId,
      @AuthenticationPrincipal Jwt jwt) {
    return controlRoom(service.acceptSuggestion(eventId, actor(jwt), requestId));
  }

  /** The control room sets the category, the priority or both (US-35 AC-2, spec D2). */
  @PatchMapping(value = "/help-requests/{requestId}", consumes = "application/json", produces = "application/json")
  ControlRoomView change(@PathVariable UUID eventId, @PathVariable UUID requestId, @AuthenticationPrincipal Jwt jwt,
      @RequestBody ChangeBody body) {
    return controlRoom(service.change(eventId, actor(jwt), requestId, body.category, body.priority));
  }

  /** The control room has seen the request: Submitted becomes Seen (US-28 AC-1, spec D2). */
  @PostMapping(value = "/help-requests/{requestId}/acknowledge", produces = "application/json")
  ControlRoomView acknowledge(@PathVariable UUID eventId, @PathVariable UUID requestId,
      @AuthenticationPrincipal Jwt jwt) {
    return controlRoom(service.acknowledge(eventId, actor(jwt), requestId));
  }

  /** The control room closes the request: it leaves the active list (US-30 AC-1, spec D1). */
  @PostMapping(value = "/help-requests/{requestId}/close", produces = "application/json")
  ControlRoomView close(@PathVariable UUID eventId, @PathVariable UUID requestId, @AuthenticationPrincipal Jwt jwt) {
    return controlRoom(service.close(eventId, actor(jwt), requestId));
  }

  /** Every recorded change of the request, oldest first (US-28 AC-2, spec D4). */
  record HistoryView(List<HistoryEntryView> items) {}

  /** One entry as the contract publishes it, apart from the domain's HistoryEntry (review of #245). */
  record HistoryEntryView(String field, String oldValue, String newValue, String actorUserId, String actorName,
      Instant changedAt) {}

  @GetMapping(value = "/help-requests/{requestId}/history", produces = "application/json")
  HistoryView history(@PathVariable UUID eventId, @PathVariable UUID requestId, @AuthenticationPrincipal Jwt jwt) {
    return new HistoryView(service.history(eventId, jwt.getSubject(), requestId).stream()
        .map(HelpRequestController::entry).toList());
  }

  private static HistoryEntryView entry(HistoryEntry e) {
    return new HistoryEntryView(e.field(), e.oldValue(), e.newValue(), e.actorUserId(), e.actorName(),
        e.changedAt());
  }

  /**
   * Who acts: the token's sub, and its name claim copied into the history (spec US-28 D5). A valid token without a sub
   * names nobody, so it gets the 404 of a non-member, not a server error (G8 review).
   */
  private static Actor actor(Jwt jwt) {
    var subject = jwt.getSubject();
    if (subject == null || subject.isBlank()) {
      throw new NotFoundException();
    }
    return new Actor(subject, jwt.getClaimAsString("name"));
  }

  /** Whether a new request would be sent to the AI, for the web app's banner (US-36 AC-2, spec D4, D5). */
  @GetMapping(value = "/triage-status", produces = "application/json")
  TriageService.Status triageStatus(@PathVariable UUID eventId, @AuthenticationPrincipal Jwt jwt) {
    service.requireTriage(eventId, jwt.getSubject());
    return triage.status();
  }

  /** Input the domain refuses that bean validation let through, such as a description of Unicode spaces. */
  @ExceptionHandler(InvalidHelpRequestException.class)
  ResponseEntity<ProblemDetail> invalid(InvalidHelpRequestException e) {
    return ResponseEntity.badRequest()
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage()));
  }

  @ExceptionHandler(ClientRequestConflictException.class)
  ResponseEntity<ProblemDetail> conflict(ClientRequestConflictException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage()));
  }

  @ExceptionHandler(RequestConflictException.class)
  ResponseEntity<ProblemDetail> requestConflict(RequestConflictException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage()));
  }

  @ExceptionHandler(ForbiddenException.class)
  ResponseEntity<ProblemDetail> forbidden() {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Forbidden"));
  }

  @ExceptionHandler(NotFoundException.class)
  ResponseEntity<ProblemDetail> notFound() {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Not found"));
  }

  private static ParticipantView participant(HelpRequest r) {
    return new ParticipantView(r.id(), r.description(), r.status(), r.updatedAt());
  }

  private static ControlRoomView controlRoom(HelpRequest r) {
    var t = r.triage();
    return new ControlRoomView(r.id(), r.eventId(), r.category(),
        new LocationBody(r.location().mapVersion(), r.location().x(), r.location().y()),
        r.description(), r.priority(), r.status(), r.submittedAt(), r.updatedAt(), r.defaultTeamId(),
        r.routedTeamId(), new TriageView(t.state(), t.suggestedCategory(), t.suggestedPriority(),
            t.suggestedReason(), t.notTriagedReason()));
  }
}
