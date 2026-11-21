package pt.isep.labdsof.badges.adapters.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.isep.labdsof.badges.domain.BadgeProgress;
import pt.isep.labdsof.badges.domain.BadgeProgress.Tier;
import pt.isep.labdsof.badges.domain.BadgeProgress.Track;

@Repository
public class JdbcBadgeProgress {

  private final JdbcClient jdbc;

  public JdbcBadgeProgress(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public List<BadgeProgress> findForAccount(UUID userId) {
    return jdbc.sql("""
        SELECT track, event_id, qualifying_count, tier
        FROM badge_progress
        WHERE user_id = :userId
        ORDER BY track, event_id
        """)
        .param("userId", userId)
        .query((row, number) -> new BadgeProgress(
            Track.valueOf(row.getString("track")), row.getObject("event_id", UUID.class),
            row.getInt("qualifying_count"), Tier.valueOf(row.getString("tier"))))
        .list();
  }
}
