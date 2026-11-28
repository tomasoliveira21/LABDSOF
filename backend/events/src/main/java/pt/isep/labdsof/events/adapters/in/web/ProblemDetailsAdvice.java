package pt.isep.labdsof.events.adapters.in.web;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import pt.isep.labdsof.events.application.EventChangedConcurrentlyException;
import pt.isep.labdsof.events.application.EventNotFoundException;
import pt.isep.labdsof.events.application.EventNotOpenException;
import pt.isep.labdsof.events.application.ForbiddenException;
import pt.isep.labdsof.events.application.NotPlatformAdminException;
import pt.isep.labdsof.events.domain.EventNotDraftException;
import pt.isep.labdsof.events.application.TeamChangedConcurrentlyException;
import pt.isep.labdsof.events.application.TeamNameTakenException;
import pt.isep.labdsof.events.application.TeamNotFoundException;
import pt.isep.labdsof.events.domain.InvalidMapFileException;
import pt.isep.labdsof.events.domain.InvalidEventException;
import pt.isep.labdsof.events.domain.InvalidTeamException;
import pt.isep.labdsof.events.domain.MapFileTooLargeException;

/**
 * Problem Details for every error (spec D1, API outline 1.7). The standard MVC exceptions come from the superclass;
 * the details added here are fixed texts, never an exception message.
 */
@RestControllerAdvice
class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ProblemDetailsAdvice.class);

  /** Not logged here: the AccessAudit already wrote the one refusal line (spec D8). Same body for every cause. */
  @ExceptionHandler(EventNotFoundException.class)
  ResponseEntity<ProblemDetail> eventNotFound(EventNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Event not found."));
  }

  /** A member of the event without the permission the operation needs (US-16 D4). */
  @ExceptionHandler(ForbiddenException.class)
  ResponseEntity<ProblemDetail> forbidden(ForbiddenException e) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "No permission in this event."));
  }

  /** As {@link #eventNotFound}: a team of another event looks exactly like an unknown team (US-16 D4). */
  @ExceptionHandler(TeamNotFoundException.class)
  ResponseEntity<ProblemDetail> teamNotFound(TeamNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Team not found."));
  }

  /** US-16 AC-2: another live team of the event already has the name. */
  @ExceptionHandler(TeamNameTakenException.class)
  ResponseEntity<ProblemDetail> teamNameTaken(TeamNameTakenException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "A team with this name already exists."));
  }

  /** US-16 D3: another request changed the team between this one's read and its write; nothing was published. */
  @ExceptionHandler(TeamChangedConcurrentlyException.class)
  ResponseEntity<ProblemDetail> teamChangedConcurrently(TeamChangedConcurrentlyException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "This team changed meanwhile; read it again."));
  }

  /** US-16 D2: a suspended or closed event does not change. */
  @ExceptionHandler(EventNotOpenException.class)
  ResponseEntity<ProblemDetail> eventNotOpen(EventNotOpenException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "This event is not open to changes."));
  }

  /** US-11 D2: only a draft publishes, and a second publish of the same event lands here too. */
  @ExceptionHandler(EventNotDraftException.class)
  ResponseEntity<ProblemDetail> eventNotDraft(EventNotDraftException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "This event is not a draft."));
  }

  /** US-11 D4: another request changed the event between this one's read and its write; nothing was published. */
  @ExceptionHandler(EventChangedConcurrentlyException.class)
  ResponseEntity<ProblemDetail> eventChangedConcurrently(EventChangedConcurrentlyException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "This event changed meanwhile; read it again."));
  }

  /** A team the caller described wrongly; the reason is the domain's, never shown. */
  @ExceptionHandler(NotPlatformAdminException.class)
  ResponseEntity<ProblemDetail> notPlatformAdmin(NotPlatformAdminException e) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Only the FarolAdmin may do this."));
  }

  @ExceptionHandler(InvalidEventException.class)
  ResponseEntity<ProblemDetail> invalidEvent(InvalidEventException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid event."));
  }

  @ExceptionHandler(InvalidTeamException.class)
  ResponseEntity<ProblemDetail> invalidTeam(InvalidTeamException e) {
    return ResponseEntity.badRequest()
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid team."));
  }

  /** US-19 AC-2: the file is not a PNG, a JPEG or a single-page PDF this service can render. */
  @ExceptionHandler(InvalidMapFileException.class)
  ResponseEntity<ProblemDetail> invalidMapFile(InvalidMapFileException e) {
    return ResponseEntity.badRequest()
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
            "The map must be a PNG, JPEG or single-page PDF."));
  }

  /**
   * US-19 AC-3 and D5: above the 20 MB of ADR-013 L-4. The detail names the limit, which is what the criterion
   * asks for.
   */
  @ExceptionHandler(MapFileTooLargeException.class)
  ResponseEntity<ProblemDetail> mapFileTooLarge(MapFileTooLargeException e) {
    return tooLarge();
  }

  /**
   * The same answer when the multipart limit of the service raises it, which is the normal way: the file is
   * refused as it arrives, so nothing of it is ever read. The superclass already maps this to {@code 413}; what
   * is replaced here is its generic detail, which says nothing about the limit.
   */
  @Override
  protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e,
      HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(tooLarge().getBody());
  }

  private static ResponseEntity<ProblemDetail> tooLarge() {
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE,
            "The map file must be at most 20 MB."));
  }

  /** Never the violation text. */
  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ProblemDetail> invalidParameters(ConstraintViolationException e) {
    return ResponseEntity.badRequest()
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request parameters."));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception e) {
    log.error("Unexpected error answering the request", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error."));
  }
}
