package me.foesio.foMiningComp;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import me.foesio.foMiningComp.api.ActionResult;
import me.foesio.foMiningComp.api.ActiveCompetitionSnapshot;
import me.foesio.foMiningComp.api.CompetitionDefinition;
import me.foesio.foMiningComp.api.CompletionReason;
import me.foesio.foMiningComp.api.FoMiningCompApi;
import me.foesio.foMiningComp.api.LiveStanding;
import me.foesio.foMiningComp.api.NextCompetitionSnapshot;
import me.foesio.foMiningComp.api.StartCause;
import org.bukkit.Bukkit;
import org.bukkit.Material;

/** Internal adapter; only FoMiningCompApi is the public service contract. */
final class FoMiningCompApiImpl implements FoMiningCompApi {
    private final FoMiningComp plugin;
    private final CompetitionManager competitions;

    FoMiningCompApiImpl(FoMiningComp plugin, CompetitionManager competitions) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.competitions = Objects.requireNonNull(competitions, "competitions");
    }

    private void requireServerThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("FoMiningCompApi must be called on the server thread");
        }
    }

    private void requireAvailable() {
        requireServerThread();
        if (!plugin.isEnabled()) {
            throw new IllegalStateException("FoMiningComp is disabled; reacquire its service when enabled");
        }
    }

    private boolean actionsAvailable() {
        requireServerThread();
        return plugin.isEnabled();
    }

    @Override
    public boolean isCompetitionActive() {
        requireAvailable();
        return competitions.hasActiveCompetition();
    }

    @Override
    public String activeCompetitionId() {
        requireAvailable();
        return competitions.activeCompetitionId();
    }

    @Override
    public Optional<ActiveCompetitionSnapshot> activeCompetition() {
        requireAvailable();
        return Optional.ofNullable(competitions.activeSnapshot());
    }

    @Override
    public Optional<NextCompetitionSnapshot> nextCompetition() {
        requireAvailable();
        return Optional.ofNullable(competitions.nextSnapshot());
    }

    @Override
    public List<CompetitionDefinition> competitions() {
        requireAvailable();
        return competitions.competitionDefinitions();
    }

    @Override
    public int playerScore(UUID playerId) {
        requireAvailable();
        return competitions.playerScore(Objects.requireNonNull(playerId, "playerId"));
    }

    @Override
    public int playerRank(UUID playerId) {
        requireAvailable();
        return competitions.playerRank(Objects.requireNonNull(playerId, "playerId"));
    }

    @Override
    public List<LiveStanding> liveStandings() {
        requireAvailable();
        return competitions.liveStandings();
    }

    @Override
    public ActionResult startCompetition(String id) {
        return actionsAvailable() ? competitions.requestStartCompetition(id, null, StartCause.API)
            : ActionResult.PROVIDER_DISABLED;
    }

    @Override
    public ActionResult stopCompetition() {
        return actionsAvailable() ? competitions.requestStopCompetition(CompletionReason.API_STOP, null)
            : ActionResult.PROVIDER_DISABLED;
    }

    @Override
    public ActionResult queueNext(String id) {
        return actionsAvailable() ? competitions.requestQueueNext(id) : ActionResult.PROVIDER_DISABLED;
    }

    @Override
    public ActionResult awardScore(UUID playerId, Material material, String worldName, int amount) {
        return actionsAvailable() ? competitions.awardScore(playerId, material, worldName, amount)
            : ActionResult.PROVIDER_DISABLED;
    }
}
