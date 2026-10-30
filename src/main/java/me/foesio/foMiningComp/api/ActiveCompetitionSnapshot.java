package me.foesio.foMiningComp.api;

/** Live competition state; timestamps are Unix milliseconds. */
public record ActiveCompetitionSnapshot(String id, String displayName, long startedAtMillis, long endsAtMillis) {
}
