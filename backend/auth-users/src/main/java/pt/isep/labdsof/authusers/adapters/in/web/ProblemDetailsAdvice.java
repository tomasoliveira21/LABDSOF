package pt.isep.labdsof.authusers.adapters.in.web;

import jakarta.validation.ConstraintViolationException;
import java.beans.PropertyEditorSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import pt.isep.labdsof.authusers.adapters.in.web.generated.model.EventRole;
import pt.isep.labdsof.authusers.application.AccountDisabledException;
import pt.isep.labdsof.authusers.application.AccountUnavailableException;
import pt.isep.labdsof.authusers.application.MissingEmailException;

/**
 * Problem Details for every error (spec D9, API outline 1.7). The standard MVC exceptions come from the superclass;
 * the details added here are fixed texts, never an exception message that could hold an e-mail or a token.
 */
@RestControllerAdvice
class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ProblemDetailsAdvice.class);

  /**
   * The contract's {@code ?role=EventAdmin} is the constant {@code EVENT_ADMIN} of the generated enum. A property
   * editor, not a converter: after a failed converter Spring falls back to {@code Enum.valueOf}, which would let
   * {@code ?role=EVENT_ADMIN} through. Any other value is a 400.
   */
  @InitBinder
  void bindEventRole(WebDataBinder binder) {
    binder.registerCustomEditor(EventRole.class, new PropertyEditorSupport() {
      @Override
      public void setAsText(String text) {
        setValue(EventRole.fromValue(text));
      }
    });
  }

  @ExceptionHandler({MissingEmailException.class, AccountUnavailableException.class})
  ResponseEntity<ProblemDetail> unauthorized(RuntimeException e) {
    log.info("Request refused with 401: {}", e.getMessage());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
            "The token does not identify an available account."));
  }

  @ExceptionHandler(AccountDisabledException.class)
  ResponseEntity<ProblemDetail> disabled(AccountDisabledException e) {
    log.info("Request refused with 403: {}", e.getMessage());
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "The account is disabled."));
  }

  /** Never the violation text: for {@code GET /accounts} it would hold the e-mail sent. */
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
