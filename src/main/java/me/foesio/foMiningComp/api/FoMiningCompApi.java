package me.foesio.foMiningComp.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Material;

/** Main-thread-only, live FoMiningComp service. Obtain it through Bukkit's ServicesManager. */
public interface FoMiningCompApi {
    boolean isCompetitionActive();

    /** Returns the active ID, or {@code "none"} while idle. */
    String activeCompetitionId();

    Optional<ActiveCompetitionSnapshot> activeCompetition();

    /** Returns an empty result if nothing is queued or scheduled. An unknown start time is -1. */
    Optional<NextCompetitionSnapshot> nextCompetition();

    List<CompetitionDefinition> competitions();

    /** Returns 0 while idle or when the player has not scored. */
    int playerScore(UUID playerId);

    /** Returns 0 while idle or when the player has not scored. Equal scores share a rank. */
    int playerRank(UUID playerId);

    /** Live standings only; empty while idle. */
    List<LiveStanding> liveStandings();

    /** Starts a configured, enabled competition. A running one is completed as REPLACED first. */
    ActionResult startCompetition(String id);

    /** Ends the active competition and runs its configured rewards. */
    ActionResult stopCompetition();

    /** Selects an enabled next candidate; it starts automatically only when auto-start is enabled. */
    ActionResult queueNext(String id);

    /** Credits eligible blocks from an integration. No score is changed on a non-success result. */
    ActionResult awardScore(UUID playerId, Material material, String worldName, int amount);
}
