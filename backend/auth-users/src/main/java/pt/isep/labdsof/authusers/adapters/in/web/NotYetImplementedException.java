package pt.isep.labdsof.authusers.adapters.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** An operation of the contract whose story is still to come (spec D2): 501 Problem Details naming the story. */
class NotYetImplementedException extends ErrorResponseException {

  NotYetImplementedException(String story) {
    super(HttpStatus.NOT_IMPLEMENTED,
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_IMPLEMENTED, "This operation arrives with " + story + "."),
        null);
  }
}
