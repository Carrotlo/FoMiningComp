package me.foesio.foMiningComp;

import me.foesio.core.placeholder.FoPlaceholders;
import org.bukkit.OfflinePlayer;

public final class FoMiningCompPlaceholders {

    private static final int TOP_PLACEHOLDER_LIMIT = 5;

    private FoMiningCompPlaceholders() {
    }

    public static boolean register(FoMiningComp plugin, CompetitionManager manager) {
        FoPlaceholders placeholders = plugin.core().placeholders("fominingcomp")
            .author("Carrotio")
            .logRegistration(false)
            .global("time_remaining", () -> manager.formatDuration(manager.timeRemainingSeconds()))
            .global("time_until_next", () -> manager.formatDuration(manager.timeUntilNextSeconds()))
            .global("active_id", manager::activeCompetitionId)
            .global("active_name", manager::activeCompetitionDisplayName)
            .global("next_id", manager::nextCompetitionId)
            .global("status", () -> manager.hasActiveCompetition() ? "running" : "idle")
            .offline("player_score", player -> String.valueOf(playerScore(manager, player)))
            .offline("player_rank", player -> String.valueOf(playerRank(manager, player)));

        for (int rank = 1; rank <= TOP_PLACEHOLDER_LIMIT; rank++) {
            int currentRank = rank;
            placeholders
                .global("top_" + currentRank + "_player", () -> topPlayer(manager, currentRank))
                .global("top_" + currentRank + "_score", () -> topScore(manager, currentRank));
        }

        return placeholders.registerIfAvailable();
    }

    private static int playerScore(CompetitionManager manager, OfflinePlayer player) {
        return player == null ? 0 : manager.playerScore(player.getUniqueId());
    }

    private static int playerRank(CompetitionManager manager, OfflinePlayer player) {
        return player == null ? 0 : manager.playerRank(player.getUniqueId());
    }

    private static String topPlayer(CompetitionManager manager, int rank) {
        CompetitionManager.ResultEntry entry = manager.topEntry(rank);
        return entry == null ? "" : entry.name();
    }

    private static String topScore(CompetitionManager manager, int rank) {
        CompetitionManager.ResultEntry entry = manager.topEntry(rank);
        return entry == null ? "" : String.valueOf(entry.score());
    }
}
