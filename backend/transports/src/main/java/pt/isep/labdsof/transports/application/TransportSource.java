package pt.isep.labdsof.transports.application;

/** Port: the external bus source (ADR-008), read with its timeouts; never throws for a failure of the source. */
public interface TransportSource {

  SourceReading read();
}
