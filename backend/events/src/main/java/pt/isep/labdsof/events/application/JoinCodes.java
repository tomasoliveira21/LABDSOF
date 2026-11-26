package pt.isep.labdsof.events.application;

/**
 * Port: the code the join link and the QR code of a published event carry (US-11 D1). Unguessable, so the link
 * cannot be found by trying; the use case asks for another one if the table already holds it.
 */
public interface JoinCodes {

  String next();
}
