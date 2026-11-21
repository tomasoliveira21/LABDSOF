package pt.isep.labdsof.transports.adapters.in.web;

import jakarta.validation.ConstraintViolationException;
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

/**
 * Problem Details for every error (spec D1, D10, API outline 1.7). The standard MVC exceptions come from the superclass;
 * the details added here are fixed texts, never an exception message that could hold a token or a path value.
 */
@RestControllerAdvice
class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ProblemDetailsAdvice.class);
  private static final int MAX_CAUSE_DEPTH = 10;

  /** Never the violation text: it repeats the value sent. */
  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ProblemDetail> invalidParameters(ConstraintViolationException e) {
    return ResponseEntity.badRequest()
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request parameters."));
  }

  /**
   * A denial is not an error of ours: rethrowing the same exception leaves it unresolved here, so it reaches Spring
   * Security's translation filter and {@link SecurityProblems} answers the {@code 401} or {@code 403} of the
   * contract. Without this the catch-all below would turn every {@code @PreAuthorize} denial into a {@code 500}.
   */
  @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
  ResponseEntity<ProblemDetail> denied(RuntimeException e) {
    throw e;
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
