package pt.isep.labdsof.authusers.application;

import java.util.Objects;
import java.util.UUID;

/** What the token says about the caller; claims the token lacks are null. */
public record Identity(UUID userId, String email, Boolean emailVerified, String name) {

  public Identity {
    Objects.requireNonNull(userId, "userId");
  }
}
