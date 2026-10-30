package me.foesio.foMiningComp.api;

import java.util.UUID;

/** One player's current score and competition rank. */
public record LiveStanding(UUID playerId, String playerName, int score, int rank) {
}
