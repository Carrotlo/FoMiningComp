package me.foesio.foMiningComp.api;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired after FoMiningComp has finalized a competition and attempted its normal
 * configured rewards. This includes early endings; inspect {@link #getReason()}.
 * Consumers may add independent bonus rewards, but cannot alter existing payouts.
 */
public final class MiningCompetitionCompletedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String competitionId;
    private final String competitionName;
    private final List<Placement> placements;
    private final CompletionReason reason;

    /** Retained for binary compatibility; use the reason-aware constructor for new code. */
    public MiningCompetitionCompletedEvent(String competitionId, String competitionName, List<Placement> placements) {
        this(competitionId, competitionName, placements, CompletionReason.UNKNOWN);
    }

    public MiningCompetitionCompletedEvent(String competitionId, String competitionName, List<Placement> placements,
                                           CompletionReason reason) {
        this.competitionId = Objects.requireNonNull(competitionId, "competitionId");
        this.competitionName = Objects.requireNonNull(competitionName, "competitionName");
        this.placements = List.copyOf(placements == null ? List.of() : placements);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public String getCompetitionId() {
        return competitionId;
    }

    public String getCompetitionName() {
        return competitionName;
    }

    public List<Placement> getPlacements() {
        return placements;
    }

    public CompletionReason getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public record Placement(UUID playerId, String playerName, int score, int rank) {
        public Placement {
            Objects.requireNonNull(playerId, "playerId");
            playerName = Objects.requireNonNullElse(playerName, "");
            if (rank < 1) {
                throw new IllegalArgumentException("rank must be at least 1");
            }
        }
    }
}
