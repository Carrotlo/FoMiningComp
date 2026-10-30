package me.foesio.foMiningComp.api;

import java.util.Objects;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired after the competition becomes active, before start broadcasts and sounds. */
public final class MiningCompetitionStartedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String competitionId;
    private final String competitionName;
    private final StartCause cause;
    private final long startedAtMillis;
    private final long endsAtMillis;

    public MiningCompetitionStartedEvent(String competitionId, String competitionName, StartCause cause,
                                         long startedAtMillis, long endsAtMillis) {
        this.competitionId = Objects.requireNonNull(competitionId, "competitionId");
        this.competitionName = Objects.requireNonNull(competitionName, "competitionName");
        this.cause = Objects.requireNonNull(cause, "cause");
        this.startedAtMillis = startedAtMillis;
        this.endsAtMillis = endsAtMillis;
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

    public long getStartedAtMillis() {
        return startedAtMillis;
    }

    public long getEndsAtMillis() {
        return endsAtMillis;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
