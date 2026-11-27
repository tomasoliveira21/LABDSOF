package pt.isep.labdsof.transports.domain;

/** A request for a bus route that the rules of spec US-41 D2 refuse (400); the message is the Problem's detail. */
public class InvalidBusRouteException extends RuntimeException {

  public InvalidBusRouteException(String detail) {
    super(detail);
  }
}
