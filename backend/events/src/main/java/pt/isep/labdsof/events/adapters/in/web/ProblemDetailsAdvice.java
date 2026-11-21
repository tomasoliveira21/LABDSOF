package pt.isep.labdsof.events.adapters.in.web;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import pt.isep.labdsof.events.application.EventNotFoundException;
import pt.isep.labdsof.events.application.EventNotOpenException;
import pt.isep.labdsof.events.application.ForbiddenException;
import pt.isep.labdsof.events.application.TeamNameTakenException;
import pt.isep.labdsof.events.application.TeamNotFoundException;
import pt.isep.labdsof.events.domain.InvalidTeamException;

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

  /** US-16 D2: a suspended or closed event does not change. */
  @ExceptionHandler(EventNotOpenException.class)
  ResponseEntity<ProblemDetail> eventNotOpen(EventNotOpenException e) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "This event is not open to changes."));
  }

  /** A team the caller described wrongly; the reason is the domain's, never shown. */
  @ExceptionHandler(InvalidTeamException.class)
  ResponseEntity<ProblemDetail> invalidTeam(InvalidTeamException e) {
    return ResponseEntity.badRequest()
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid team."));
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
