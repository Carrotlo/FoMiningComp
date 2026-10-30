package me.foesio.foMiningComp.api;

/** Next queued or scheduled competition; -1 means no automatic start time is known. */
public record NextCompetitionSnapshot(String id, long startsAtMillis) {
}
