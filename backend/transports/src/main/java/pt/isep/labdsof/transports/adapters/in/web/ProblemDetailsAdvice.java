package pt.isep.labdsof.transports.adapters.in.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import pt.isep.labdsof.transports.application.BusRouteLimitException;
import pt.isep.labdsof.transports.application.BusRouteTakenException;
import pt.isep.labdsof.transports.application.EventClosedException;
import pt.isep.labdsof.transports.application.EventNotFoundException;
import pt.isep.labdsof.transports.application.ForbiddenException;
import pt.isep.labdsof.transports.application.ShuttleNotYetException;
import pt.isep.labdsof.transports.domain.InvalidBusRouteException;
import pt.isep.labdsof.transports.domain.NewBusRoute;

/**
 * Problem Details for every error (spec D1, D10, API outline 1.7). The standard MVC exceptions, invalid bodies and path
 * values included, come from the superclass; the details added here are fixed texts, never an exception message that
 * could hold a token or a path value. No handler for {@code ConstraintViolationException}: the contract has no
 * constrained path or query parameter, so nothing raises it (a story that adds one adds the handler and its test).
 */
@RestControllerAdvice
class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ProblemDetailsAdvice.class);
  private static final int MAX_CAUSE_DEPTH = 10;

  /**
   * A denial is not an error of ours: rethrowing the same exception leaves it unresolved here, so it reaches Spring
   * Security's translation filter and {@link SecurityProblems} answers the {@code 401} or {@code 403} of the
   * contract. Without this the catch-all below would turn every {@code @PreAuthorize} denial into a {@code 500}.
   */
  @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
  ResponseEntity<ProblemDetail> denied(RuntimeException e) {
    throw e;
  }

  /** US-41 AC-3: the same answer for an unknown event, a suspended one and a user who is not a member. */
  @ExceptionHandler(EventNotFoundException.class)
  ResponseEntity<ProblemDetail> eventNotFound() {
    return problem(HttpStatus.NOT_FOUND, "Event not found.");
  }

  @ExceptionHandler(ForbiddenException.class)
  ResponseEntity<ProblemDetail> forbidden() {
    return problem(HttpStatus.FORBIDDEN, "No permission in this event.");
  }

  /** US-41 D2: the message is one of the fixed texts of {@code NewBusRoute}, never the request's content. */
  @ExceptionHandler(InvalidBusRouteException.class)
  ResponseEntity<ProblemDetail> invalidBusRoute(InvalidBusRouteException e) {
    return problem(HttpStatus.BAD_REQUEST, e.getMessage());
  }

  @ExceptionHandler(ShuttleNotYetException.class)
  ResponseEntity<ProblemDetail> shuttleNotYet() {
    return problem(HttpStatus.NOT_IMPLEMENTED, "This operation arrives with US-37.");
  }

  @ExceptionHandler(EventClosedException.class)
  ResponseEntity<ProblemDetail> eventClosed() {
    return problem(HttpStatus.CONFLICT, "This event is closed.");
  }

  @ExceptionHandler(BusRouteTakenException.class)
  ResponseEntity<ProblemDetail> busRouteTaken() {
    return problem(HttpStatus.CONFLICT, "This route is already on the event.");
  }

  @ExceptionHandler(BusRouteLimitException.class)
  ResponseEntity<ProblemDetail> busRouteLimit() {
    return problem(HttpStatus.CONFLICT, "This event already has " + NewBusRoute.MAX_PER_EVENT + " routes.");
  }

  private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
    return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception e) throws Exception {
    if (clientIsGone(e)) {
      // The client closed the connection: there is nobody to answer, and writing a body would fail again
      log.debug("The client closed the connection before the response was written");
      throw e;
    }
    log.error("Unexpected error answering the request", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error."));
  }

  /** By name, so neither Tomcat nor the servlet async internals become a compile dependency of this class. */
  private static boolean clientIsGone(Throwable thrown) {
    var current = thrown;
    for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
      var name = current.getClass().getSimpleName();
      if ("ClientAbortException".equals(name) || "AsyncRequestNotUsableException".equals(name)) {
        return true;
      }
      current = current.getCause() == current ? null : current.getCause();
    }
    return false;
  }
}
