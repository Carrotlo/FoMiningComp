package me.foesio.foMiningComp.api;

import java.util.Objects;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired for a valid enabled competition before replacing an active one or changing
 * schedule/state. Cancelling a scheduled start skips that candidate and schedules
 * the next enabled competition after the normal interval.
 */
public final class MiningCompetitionPreStartEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String competitionId;
    private final String competitionName;
    private final StartCause cause;
    private boolean cancelled;

    public MiningCompetitionPreStartEvent(String competitionId, String competitionName, StartCause cause) {
        this.competitionId = Objects.requireNonNull(competitionId, "competitionId");
        this.competitionName = Objects.requireNonNull(competitionName, "competitionName");
        this.cause = Objects.requireNonNull(cause, "cause");
    }

    public String getCompetitionId() {
        return competitionId;
    }

    public String getCompetitionName() {
        return competitionName;
    }

    public StartCause getCause() {
        return cause;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
