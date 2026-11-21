package pt.isep.labdsof.events.domain;

import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A team of one event (US-16), with the help categories it handles and the permissions US-17 gives it. Soft
 * deleted: a deleted team keeps its row, its permissions and a higher version, so TeamChanged can carry
 * {@code deleted: true} (spec #192 D4, US-16 D5). Every change makes a new value with the next version, because
 * the consumers only apply a copy whose version is strictly newer.
 */
public record Team(UUID teamId, UUID eventId, String name, List<TeamPermission> permissions,
    Set<HelpCategory> handledCategories, boolean deleted, long version) {

  /** The database's team_name check: 1 to 100 characters once trimmed. */
  public static final int MAX_NAME_LENGTH = 100;

  public Team {
    Objects.requireNonNull(teamId, "teamId");
    Objects.requireNonNull(eventId, "eventId");
    name = trimmedName(name);
    permissions = sortedPermissions(permissions);
    handledCategories = immutable(handledCategories);
    if (version < 1) {
      throw new IllegalArgumentException("version must be at least 1");
    }
  }

  /** A new team: no members, no permissions, no categories (AC-1), version 1. */
  public static Team created(UUID teamId, UUID eventId, String name) {
    return new Team(teamId, eventId, name, List.of(), Set.of(), false, 1);
  }

  /** The same team renamed and with the categories it handles (US-16, US-17), one version later. */
  public Team renamed(String newName, Set<HelpCategory> categories) {
    return new Team(teamId, eventId, newName, permissions, categories, deleted, version + 1);
  }

  /** The same team deleted, keeping the permissions it had so the consumers see what it granted (D5). */
  public Team asDeleted() {
    return new Team(teamId, eventId, name, permissions, handledCategories, true, version + 1);
  }

  /** A deleted team grants nothing, whatever its permission rows still say. */
  public boolean grants(Permission permission) {
    return !deleted && permissions.stream().anyMatch(p -> p.permission() == permission);
  }

  /** US-16 D4: a member of this team may read the event's teams. */
  public boolean grantsTeamReading() {
    return !deleted && permissions.stream().anyMatch(p -> p.permission().readsTeams());
  }

  /** Names are compared ignoring case and surrounding blanks, as team_name_per_event does (D3). */
  public boolean isNamed(String other) {
    return name.equalsIgnoreCase(trimmedName(other));
  }

  /**
   * The categories of a request body: a repeated value is the caller's mistake, never deduplicated (D7), and so
   * is a null item, which the contract's array does not forbid by itself.
   */
  public static Set<HelpCategory> categoriesOf(List<HelpCategory> categories) {
    Objects.requireNonNull(categories, "categories");
    var set = EnumSet.noneOf(HelpCategory.class);
    for (var category : categories) {
      if (category == null) {
        throw new InvalidTeamException("a handled category is missing");
      }
      if (!set.add(category)) {
        throw new InvalidTeamException("a handled category is repeated");
      }
    }
    return set;
  }

  private static String trimmedName(String name) {
    var trimmed = Objects.requireNonNull(name, "name").trim();
    if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
      throw new InvalidTeamException("a team name has 1 to 100 characters");
    }
    return trimmed;
  }

  private static List<TeamPermission> sortedPermissions(List<TeamPermission> permissions) {
    Objects.requireNonNull(permissions, "permissions");
    return permissions.stream().sorted(Comparator.comparing(TeamPermission::permission)).toList();
  }

  private static Set<HelpCategory> immutable(Set<HelpCategory> categories) {
    Objects.requireNonNull(categories, "handledCategories");
    return categories.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(categories));
  }
}
