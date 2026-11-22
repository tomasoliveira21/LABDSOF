package pt.isep.labdsof.badges.adapters.in.web;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pt.isep.labdsof.badges.adapters.in.web.generated.api.BadgesApi;
import pt.isep.labdsof.badges.adapters.in.web.generated.model.Badge;
import pt.isep.labdsof.badges.adapters.in.web.generated.model.BadgePreferences;
import pt.isep.labdsof.badges.adapters.in.web.generated.model.BadgeTier;
import pt.isep.labdsof.badges.adapters.in.web.generated.model.BadgeTrack;
import pt.isep.labdsof.badges.adapters.out.persistence.JdbcBadgeProgress;

@RestController
class BadgesController implements BadgesApi {

  private final JdbcBadgeProgress progress;

  BadgesController(JdbcBadgeProgress progress) {
    this.progress = progress;
  }

  @Override
  public ResponseEntity<List<Badge>> listMyBadges() {
    var authentication = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
    var userId = UUID.fromString(authentication.getToken().getSubject());
    var badges = progress.findForAccount(userId).stream()
        .map(badge -> new Badge(BadgeTrack.fromValue(badge.track().name()), badge.eventId(),
            badge.qualifyingCount(), BadgeTier.fromValue(badge.tier().name())))
        .toList();
    return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(badges);
  }

  @Override
  public ResponseEntity<BadgePreferences> getMyBadgePreferences() {
    throw preferencesPending();
  }

  @Override
  public ResponseEntity<BadgePreferences> putMyBadgePreferences(BadgePreferences preferences) {
    throw preferencesPending();
  }

  private static ResponseStatusException preferencesPending() {
    return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Badge preferences are scheduled for US-56.");
  }
}
