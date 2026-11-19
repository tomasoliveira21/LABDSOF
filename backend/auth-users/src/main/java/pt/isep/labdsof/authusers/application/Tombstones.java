package pt.isep.labdsof.authusers.application;

import java.util.UUID;

public interface Tombstones {

  boolean exists(UUID userId);
}
