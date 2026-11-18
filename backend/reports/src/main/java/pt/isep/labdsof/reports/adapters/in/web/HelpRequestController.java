package pt.isep.labdsof.reports.adapters.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.isep.labdsof.reports.application.ClientRequestConflictException;
import pt.isep.labdsof.reports.application.HelpRequestService;
import pt.isep.labdsof.reports.application.NotFoundException;
import pt.isep.labdsof.reports.domain.Category;
import pt.isep.labdsof.reports.domain.HelpRequest;
import pt.isep.labdsof.reports.domain.HelpRequestStatus;
import pt.isep.labdsof.reports.domain.InvalidHelpRequestException;
import pt.isep.labdsof.reports.domain.Location;
import pt.isep.labdsof.reports.domain.NotTriagedReason;
import pt.isep.labdsof.reports.domain.Priority;
import pt.isep.labdsof.reports.domain.TriageState;

/** Help requests (API outline §4, contracts/openapi.yaml). The caller is always the token's sub. */
@RestController
@RequestMapping("/api/v1/events/{eventId}/help-requests")
class HelpRequestController {

  private final HelpRequestService service;

  HelpRequestController(HelpRequestService service) {
    this.service = service;
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

  @PostMapping(consumes = "application/json", produces = "application/json")
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

  @GetMapping(value = "/{requestId}", produces = "application/json")
  Object get(@PathVariable UUID eventId, @PathVariable UUID requestId, @AuthenticationPrincipal Jwt jwt) {
    var read = service.get(eventId, jwt.getSubject(), requestId);
    return read.controlRoom() ? controlRoom(read.request()) : participant(read.request());
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
