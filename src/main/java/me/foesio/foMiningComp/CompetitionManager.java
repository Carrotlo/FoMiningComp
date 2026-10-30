package me.foesio.foMiningComp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;
import me.foesio.core.command.CommandPlaceholders;
import me.foesio.core.dialog.DialogIcons;
import me.foesio.core.material.MaterialTypes;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.text.FoText;
import me.foesio.foMiningComp.api.ActionResult;
import me.foesio.foMiningComp.api.ActiveCompetitionSnapshot;
import me.foesio.foMiningComp.api.CompetitionDefinition;
import me.foesio.foMiningComp.api.CompletionReason;
import me.foesio.foMiningComp.api.LiveStanding;
import me.foesio.foMiningComp.api.MiningCompetitionCompletedEvent;
import me.foesio.foMiningComp.api.MiningCompetitionPreStartEvent;
import me.foesio.foMiningComp.api.MiningCompetitionStartedEvent;
import me.foesio.foMiningComp.api.NextCompetitionSnapshot;
import me.foesio.foMiningComp.api.StartCause;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

public final class CompetitionManager implements Listener {

    private static final String COMPETITIONS_FOLDER = "competitions";

    private final FoMiningComp plugin;
    private final FoMessageService messages;
    private final Map<String, CompetitionConfig> competitions;

    private ActiveCompetition activeCompetition;
    private String lastCompetitionId;
    private String queuedNextCompetitionId;
    private String scheduledNextCompetitionId;
    private long nextStartAtMillis;

    private boolean autoStart;
    private boolean broadcastWhatToMineEnabled;
    private int intervalBetweenCompetitionsSeconds;
    private int initialDelaySeconds;

    private BukkitTask tickerTask;
    private BossBar bossBar;
    private final Map<UUID, net.kyori.adventure.bossbar.BossBar> componentBossBars;

    private List<ResultEntry> lastResults;
    private boolean startRequestInProgress;

    public CompetitionManager(FoMiningComp plugin, FoMessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
        this.competitions = new LinkedHashMap<>();
        this.componentBossBars = new HashMap<>();
        this.nextStartAtMillis = -1L;
        this.lastResults = new ArrayList<>();
    }

    public void loadFromConfig() {
        if (activeCompetition != null) {
            endActiveCompetition(CompletionReason.RELOADED, null);
        }

        competitions.clear();
        FileConfiguration config = plugin.getConfig();

        autoStart = config.getBoolean("settings.auto-start", true);
        initialDelaySeconds = Math.max(0, config.getInt("settings.initial-delay-seconds", 15));
        intervalBetweenCompetitionsSeconds = Math.max(0, config.getInt("settings.interval-between-competitions-seconds", 300));
        broadcastWhatToMineEnabled = config.getBoolean("settings.broadcast-what-to-mine-enabled", true);

        File folder = competitionFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create competitions folder: " + folder.getAbsolutePath());
        }

        migrateLegacyCompetitionsIfNeeded(folder, config);
        ensureDefaultCompetitions(folder);
        loadCompetitionFiles(folder);

        scheduledNextCompetitionId = null;
        queuedNextCompetitionId = null;
        nextStartAtMillis = -1L;

        if (tickerTask == null) {
            tickerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        }

        if (autoStart) {
            scheduledNextCompetitionId = chooseNextCompetitionId();
            if (scheduledNextCompetitionId != null) {
                nextStartAtMillis = System.currentTimeMillis() + (initialDelaySeconds * 1000L);
            }
        }
    }

    public void shutdown() {
        destroyBossBar();
        if (tickerTask != null) {
            tickerTask.cancel();
            tickerTask = null;
        }
    }

    public List<CompetitionConfig> competitions() {
        return new ArrayList<>(competitions.values());
    }

    public CompetitionConfig competition(String id) {
        if (id == null) {
            return null;
        }
        return competitions.get(id.toLowerCase(Locale.ROOT));
    }

    public boolean createCompetition(String id) {
        if (id == null || !id.matches("[a-zA-Z0-9_-]{2,32}")) {
            return false;
        }

        String key = id.toLowerCase(Locale.ROOT);
        if (competitions.containsKey(key)) {
            return false;
        }

        CompetitionConfig created = new CompetitionConfig(id);
        competitions.put(key, created);
        saveCompetitionFile(created);

        if (scheduledNextCompetitionId == null) {
            scheduledNextCompetitionId = chooseNextCompetitionId();
            if (scheduledNextCompetitionId != null) {
                nextStartAtMillis = autoStart
                    ? System.currentTimeMillis() + (initialDelaySeconds * 1000L) : -1L;
            }
        }

        return true;
    }

    public boolean startCompetition(String id, CommandSender sender) {
        return requestStartCompetition(id, sender, StartCause.COMMAND) == ActionResult.SUCCESS;
    }

    public ActionResult requestStartCompetition(String id, CommandSender sender, StartCause cause) {
        if (id == null || id.isBlank() || cause == null) {
            return ActionResult.INVALID_INPUT;
        }
        if (startRequestInProgress) {
            return ActionResult.BUSY;
        }
        CompetitionConfig config = competition(id);
        if (config == null) {
            return ActionResult.NOT_FOUND;
        }
        if (!config.enabled()) {
            return ActionResult.DISABLED;
        }

        startRequestInProgress = true;
        try {
            MiningCompetitionPreStartEvent preStart = new MiningCompetitionPreStartEvent(
                config.id(), config.displayName(), cause);
            Bukkit.getPluginManager().callEvent(preStart);
            if (preStart.isCancelled()) {
                if (activeCompetition == null && autoStart
                    && (cause == StartCause.SCHEDULED || scheduledNextCompetitionId == null
                        || scheduledNextCompetitionId.equalsIgnoreCase(config.id()))) {
                    skipCompetition(config.id());
                }
                return ActionResult.CANCELLED;
            }
            if (competition(config.id()) != config || !config.enabled()) {
                return ActionResult.DISABLED;
            }

            if (activeCompetition != null) {
                endActiveCompetition(CompletionReason.REPLACED, null);
            }

            long now = System.currentTimeMillis();
            activeCompetition = new ActiveCompetition(config, now, now + (config.durationSeconds() * 1000L));

            lastCompetitionId = config.id();
            if (queuedNextCompetitionId != null && queuedNextCompetitionId.equalsIgnoreCase(config.id())) {
                queuedNextCompetitionId = null;
            }
            scheduledNextCompetitionId = null;
            nextStartAtMillis = -1L;

            createBossBarIfNeeded(config);
            ActiveCompetition started = activeCompetition;
            Bukkit.getPluginManager().callEvent(new MiningCompetitionStartedEvent(
                config.id(), config.displayName(), cause, now, activeCompetition.endsAtMillis));
            if (activeCompetition != started) {
                return ActionResult.SUCCESS;
            }

            if (config.announceStart()) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("id", config.id());
                placeholders.put("name", config.displayName());
                placeholders.put("duration", formatDuration(config.durationSeconds()));
                broadcast("messages.broadcast-start", "{prefix}{theme}Competition {name}{muted} started. Time: {theme}{duration}", placeholders);
            }

            if (broadcastWhatToMineEnabled) {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("id", config.id());
                placeholders.put("name", config.displayName());
                placeholders.put("materials", formatMaterials(config.trackedMaterials()));
                broadcast("messages.broadcast-what-to-mine", "{prefix}{muted}Mine these blocks: {theme}{materials}", placeholders);
            }

            if (sender != null) {
                messages.send(sender, "messages.competition-started", "{prefix}{good}Started competition {theme}{name}{muted}.", Map.of("name", config.displayName()));
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                plugin.sounds().play(player, "competition.start");
            }
            return ActionResult.SUCCESS;
        } finally {
            startRequestInProgress = false;
        }
    }

    public boolean stopCompetition(CommandSender sender) {
        return requestStopCompetition(CompletionReason.MANUAL_STOP, sender) == ActionResult.SUCCESS;
    }

    public ActionResult requestStopCompetition(CompletionReason reason, CommandSender sender) {
        if (reason == null) {
            return ActionResult.INVALID_INPUT;
        }
        return endActiveCompetition(reason, sender) ? ActionResult.SUCCESS : ActionResult.NO_ACTIVE_COMPETITION;
    }

    public boolean queueNext(String id) {
        return requestQueueNext(id) == ActionResult.SUCCESS;
    }

    public ActionResult requestQueueNext(String id) {
        if (id == null || id.isBlank()) {
            return ActionResult.INVALID_INPUT;
        }
        CompetitionConfig config = competition(id);
        if (config == null) {
            return ActionResult.NOT_FOUND;
        }
        if (!config.enabled()) {
            return ActionResult.DISABLED;
        }

        queuedNextCompetitionId = config.id();
        if (activeCompetition == null) {
            scheduledNextCompetitionId = queuedNextCompetitionId;
            nextStartAtMillis = autoStart
                ? System.currentTimeMillis() + (intervalBetweenCompetitionsSeconds * 1000L) : -1L;
        }
        return ActionResult.SUCCESS;
    }

    public boolean hasActiveCompetition() {
        return activeCompetition != null;
    }

    public String activeCompetitionId() {
        return activeCompetition == null ? "none" : activeCompetition.config.id();
    }

    public String activeCompetitionDisplayName() {
        return activeCompetition == null ? "None" : activeCompetition.config.displayName();
    }

    public long timeRemainingSeconds() {
        if (activeCompetition == null) {
            return 0;
        }
        long remainingMillis = activeCompetition.endsAtMillis - System.currentTimeMillis();
        return Math.max(0L, remainingMillis / 1000L);
    }

    public long timeUntilNextSeconds() {
        if (activeCompetition != null || nextStartAtMillis <= 0L) {
            return 0L;
        }
        long remainingMillis = nextStartAtMillis - System.currentTimeMillis();
        return Math.max(0L, remainingMillis / 1000L);
    }

    public String nextCompetitionId() {
        return scheduledNextCompetitionId == null ? "none" : scheduledNextCompetitionId;
    }

    public int playerScore(UUID uuid) {
        if (activeCompetition == null) {
            return 0;
        }
        return activeCompetition.scores.getOrDefault(uuid, 0);
    }

    public int playerRank(UUID uuid) {
        if (activeCompetition == null) {
            return 0;
        }

        int playerScore = activeCompetition.scores.getOrDefault(uuid, 0);
        if (playerScore <= 0) {
            return 0;
        }

        int rank = 1;
        for (int score : activeCompetition.scores.values()) {
            if (score > playerScore) {
                rank++;
            }
        }
        return rank;
    }

    public ResultEntry topEntry(int rank) {
        if (rank < 1) {
            return null;
        }

        List<ResultEntry> source = activeCompetition == null ? lastResults : buildResults(activeCompetition.scores);
        if (rank > source.size()) {
            return null;
        }

        return source.get(rank - 1);
    }

    public ActiveCompetitionSnapshot activeSnapshot() {
        ActiveCompetition running = activeCompetition;
        return running == null ? null : new ActiveCompetitionSnapshot(
            running.config.id(), running.config.displayName(), running.startsAtMillis, running.endsAtMillis);
    }

    public NextCompetitionSnapshot nextSnapshot() {
        if (queuedNextCompetitionId != null && (activeCompetition != null || scheduledNextCompetitionId == null)) {
            return new NextCompetitionSnapshot(queuedNextCompetitionId, -1L);
        }
        if (scheduledNextCompetitionId == null) {
            return null;
        }
        return new NextCompetitionSnapshot(scheduledNextCompetitionId,
            autoStart && activeCompetition == null ? nextStartAtMillis : -1L);
    }

    public List<CompetitionDefinition> competitionDefinitions() {
        return competitions.values().stream()
            .map(config -> new CompetitionDefinition(config.id(), config.displayName(), config.icon(),
                config.enabled(), config.durationSeconds(), config.trackedMaterials(), config.disabledWorlds(),
                config.announceStart(), config.announceEnd(), config.bossbarEnabled(), rewardSnapshot(config)))
            .toList();
    }

    private Map<Integer, List<String>> rewardSnapshot(CompetitionConfig config) {
        Map<Integer, List<String>> commands = new LinkedHashMap<>();
        for (int rank = 1; rank <= 5; rank++) {
            commands.put(rank, config.rewardCommands(rank));
        }
        return commands;
    }

    public List<LiveStanding> liveStandings() {
        ActiveCompetition running = activeCompetition;
        if (running == null) {
            return List.of();
        }
        List<ResultEntry> results = buildResults(running.scores);
        List<LiveStanding> standings = new ArrayList<>(results.size());
        int previousScore = -1;
        int sharedRank = 0;
        for (int i = 0; i < results.size(); i++) {
            ResultEntry entry = results.get(i);
            if (entry.score() != previousScore) {
                sharedRank = i + 1;
                previousScore = entry.score();
            }
            standings.add(new LiveStanding(entry.uuid(), entry.name(), entry.score(), sharedRank));
        }
        return List.copyOf(standings);
    }

    public ActionResult awardScore(UUID playerId, Material material, String worldName, int amount) {
        if (playerId == null || material == null || worldName == null || worldName.isBlank() || amount <= 0) {
            return ActionResult.INVALID_INPUT;
        }
        ActiveCompetition running = activeCompetition;
        if (running == null) {
            return ActionResult.NO_ACTIVE_COMPETITION;
        }
        if (!running.config.trackedMaterials().contains(material)
            || running.config.disabledWorlds().contains(worldName.trim().toLowerCase(Locale.ROOT))) {
            return ActionResult.INELIGIBLE;
        }
        int previous = running.scores.getOrDefault(playerId, 0);
        if (previous > Integer.MAX_VALUE - amount) {
            return ActionResult.OVERFLOW;
        }
        running.scores.put(playerId, previous + amount);
        return ActionResult.SUCCESS;
    }

    public String formatDuration(long seconds) {
        long clamped = Math.max(0L, seconds);
        long h = clamped / 3600L;
        long m = (clamped % 3600L) / 60L;
        long s = clamped % 60L;

        if (h > 0L) {
            return String.format("%02dh %02dm %02ds", h, m, s);
        }
        return String.format("%02dm %02ds", m, s);
    }

    public boolean broadcastWhatToMineEnabled() {
        return broadcastWhatToMineEnabled;
    }

    public void setBroadcastWhatToMineEnabled(boolean enabled) {
        broadcastWhatToMineEnabled = enabled;
        plugin.getConfig().set("settings.broadcast-what-to-mine-enabled", enabled);
        plugin.saveConfig();
    }

    public boolean setDisplayName(String id, String displayName) {
        CompetitionConfig config = competition(id);
        if (config == null) {
            return false;
        }
        config.setDisplayName(displayName);
        saveCompetitionFile(config);
        return true;
    }

    public boolean setIcon(String id, Material icon) {
        CompetitionConfig config = competition(id);
        if (config == null) {
            return false;
        }
        config.setIcon(icon);
        saveCompetitionFile(config);
        return true;
    }

    public boolean setTrackedMaterials(String id, Set<Material> materials) {
        CompetitionConfig config = competition(id);
        if (config == null || materials.isEmpty()) {
            return false;
        }
        config.setTrackedMaterials(materials);
        saveCompetitionFile(config);
        return true;
    }

    public boolean setDisabledWorlds(String id, Set<String> worlds) {
        CompetitionConfig config = competition(id);
        if (config == null) {
            return false;
        }
        config.setDisabledWorlds(worlds);
        saveCompetitionFile(config);
        return true;
    }

    public boolean setDurationSeconds(String id, int seconds) {
        CompetitionConfig config = competition(id);
        if (config == null || seconds <= 0) {
            return false;
        }
        config.setDurationSeconds(seconds);
        saveCompetitionFile(config);
        return true;
    }

    public boolean setEnabled(String id, boolean enabled) {
        CompetitionConfig config = competition(id);
        if (config == null) {
            return false;
        }

        config.setEnabled(enabled);
        saveCompetitionFile(config);

        if (!enabled && activeCompetition != null && activeCompetition.config.id().equalsIgnoreCase(config.id())) {
            endActiveCompetition(CompletionReason.DISABLED, null);
        }
        if (!enabled && scheduledNextCompetitionId != null
            && scheduledNextCompetitionId.equalsIgnoreCase(config.id())) {
            skipCompetition(config.id());
        }

        return true;
    }

    public boolean setAnnounceStart(String id, boolean enabled) {
        CompetitionConfig config = competition(id);
        if (config == null) {
            return false;
        }
        config.setAnnounceStart(enabled);
        saveCompetitionFile(config);
        return true;
    }

    public boolean setAnnounceEnd(String id, boolean enabled) {
        CompetitionConfig config = competition(id);
        if (config == null) {
            return false;
        }
        config.setAnnounceEnd(enabled);
        saveCompetitionFile(config);
        return true;
    }

    public boolean setBossbarEnabled(String id, boolean enabled) {
        CompetitionConfig config = competition(id);
        if (config == null) {
            return false;
        }
        config.setBossbarEnabled(enabled);
        saveCompetitionFile(config);

        if (activeCompetition != null && activeCompetition.config.id().equalsIgnoreCase(config.id())) {
            destroyBossBar();
            createBossBarIfNeeded(activeCompetition.config);
        }

        return true;
    }

    public boolean setRewardCommands(String id, int rank, List<String> commands) {
        CompetitionConfig config = competition(id);
        if (config == null || rank < 1 || rank > 5) {
            return false;
        }
        config.setRewardCommands(rank, commands);
        saveCompetitionFile(config);
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        ActiveCompetition running = activeCompetition;
        if (running == null) {
            return;
        }

        if (!running.config.trackedMaterials().contains(event.getBlock().getType())) {
            return;
        }

        String worldName = event.getBlock().getWorld().getName().toLowerCase(Locale.ROOT);
        if (running.config.disabledWorlds().contains(worldName)) {
            return;
        }

        awardScore(event.getPlayer().getUniqueId(), event.getBlock().getType(), event.getBlock().getWorld().getName(), 1);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (bossBar != null) {
            if (!showComponentBossBar(event.getPlayer())) {
                bossBar.addPlayer(event.getPlayer());
            }
            updateBossBar();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (bossBar != null) {
            bossBar.removePlayer(event.getPlayer());
        }
        net.kyori.adventure.bossbar.BossBar componentBossBar = componentBossBars.remove(event.getPlayer().getUniqueId());
        if (componentBossBar != null && event.getPlayer() instanceof Audience audience) {
            audience.hideBossBar(componentBossBar);
        }
    }

    private void tick() {
        if (activeCompetition != null) {
            updateBossBar();
            if (System.currentTimeMillis() >= activeCompetition.endsAtMillis) {
                endActiveCompetition(CompletionReason.TIME_EXPIRED, null);
            }
            return;
        }

        if (!autoStart) {
            return;
        }

        if (scheduledNextCompetitionId == null) {
            scheduledNextCompetitionId = chooseNextCompetitionId();
        }

        if (scheduledNextCompetitionId == null) {
            return;
        }

        if (nextStartAtMillis <= 0L) {
            nextStartAtMillis = System.currentTimeMillis() + (intervalBetweenCompetitionsSeconds * 1000L);
        }

        if (System.currentTimeMillis() >= nextStartAtMillis) {
            String candidate = scheduledNextCompetitionId;
            ActionResult result = requestStartCompetition(candidate, null, StartCause.SCHEDULED);
            if (result == ActionResult.NOT_FOUND || result == ActionResult.DISABLED) {
                skipCompetition(candidate);
            }
        }
    }

    private void skipCompetition(String id) {
        lastCompetitionId = id;
        if (queuedNextCompetitionId != null && queuedNextCompetitionId.equalsIgnoreCase(id)) {
            queuedNextCompetitionId = null;
        }
        scheduledNextCompetitionId = null;
        nextStartAtMillis = -1L;
        if (autoStart) {
            scheduledNextCompetitionId = chooseNextCompetitionId();
            if (scheduledNextCompetitionId != null) {
                nextStartAtMillis = System.currentTimeMillis() + (intervalBetweenCompetitionsSeconds * 1000L);
            }
        }
    }

    private CompetitionConfig parseCompetition(String id, ConfigurationSection configFile) {
        CompetitionConfig config = new CompetitionConfig(id);

        config.setDisplayName(configFile.getString("display-name", id));
        config.setIcon(parseMaterial(configFile.getString("icon"), Material.DIAMOND_PICKAXE, id + ".icon"));

        Set<Material> materials = EnumSet.noneOf(Material.class);
        for (String raw : configFile.getStringList("tracked-materials")) {
            Material parsed = parseMaterial(raw, null, id + ".tracked-materials");
            if (parsed != null) {
                materials.add(parsed);
            }
        }
        if (materials.isEmpty()) {
            materials.add(Material.STONE);
        }
        config.setTrackedMaterials(materials);

        Set<String> disabledWorlds = new HashSet<>();
        for (String world : configFile.getStringList("disabled-worlds")) {
            if (world == null || world.isBlank()) {
                continue;
            }
            disabledWorlds.add(world.trim().toLowerCase(Locale.ROOT));
        }
        config.setDisabledWorlds(disabledWorlds);

        config.setDurationSeconds(Math.max(1, configFile.getInt("duration-seconds", 900)));
        config.setEnabled(configFile.getBoolean("enabled", true));
        config.setBossbarEnabled(configFile.getBoolean("bossbar-enabled", true));
        config.setAnnounceStart(configFile.getBoolean("broadcast.start", true));
        config.setAnnounceEnd(configFile.getBoolean("broadcast.end", true));

        ConfigurationSection rewards = configFile.getConfigurationSection("rewards");
        for (int rank = 1; rank <= 5; rank++) {
            config.setRewardCommands(rank, readRewardCommands(rewards, rank));
        }

        return config;
    }

    private boolean endActiveCompetition(CompletionReason reason, CommandSender sender) {
        if (activeCompetition == null) {
            if (sender != null) {
                plugin.adminSounds().updateError(sender);
                messages.send(sender, "messages.no-active-competition", "{prefix}{bad}There is no active competition.");
            }
            return false;
        }

        ActiveCompetition finished = activeCompetition;
        activeCompetition = null;

        destroyBossBar();

        List<ResultEntry> results = buildResults(finished.scores);
        lastResults = results;

        if (finished.config.announceEnd() && reason == CompletionReason.TIME_EXPIRED) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("id", finished.config.id());
            placeholders.put("name", finished.config.displayName());
            placeholders.put("winner", results.isEmpty() ? "None" : results.getFirst().name());
            broadcast("messages.broadcast-end", "{prefix}{theme}Competition {name}{muted} ended. Winner: {theme}{winner}", placeholders);

            for (int i = 0; i < Math.min(5, results.size()); i++) {
                ResultEntry entry = results.get(i);
                broadcast(
                    "messages.leaderboard-entry",
                    "{prefix}{muted}#{rank} {theme}{player} {muted}- {theme}{score}",
                    Map.of(
                        "rank", String.valueOf(entry.rank()),
                        "player", entry.name(),
                        "score", String.valueOf(entry.score())
                    )
                );
            }
        }

        executeRewards(finished.config, results);
        publishCompletedEvent(finished.config, results, reason);
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            plugin.sounds().play(player, "competition.end");
        }
        for (ResultEntry result : results) {
            org.bukkit.entity.Player winner = Bukkit.getPlayer(result.uuid());
            if (winner != null) {
                plugin.sounds().play(winner, "competition.reward");
            }
        }

        if (autoStart && activeCompetition == null && reason != CompletionReason.REPLACED
            && reason != CompletionReason.RELOADED) {
            scheduledNextCompetitionId = chooseNextCompetitionId();
            if (scheduledNextCompetitionId != null) {
                nextStartAtMillis = System.currentTimeMillis() + (intervalBetweenCompetitionsSeconds * 1000L);
            }
        }

        if (sender != null) {
            messages.send(sender, "messages.competition-stopped", "{prefix}{bad}Stopped the active competition.");
        }

        return true;
    }

    private List<ResultEntry> buildResults(Map<UUID, Integer> scores) {
        if (scores.isEmpty()) {
            return List.of();
        }

        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort(Map.Entry.<UUID, Integer>comparingByValue().reversed());

        List<ResultEntry> result = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<UUID, Integer> entry : sorted) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entry.getKey());
            String name = offlinePlayer.getName() == null ? entry.getKey().toString().substring(0, 8) : offlinePlayer.getName();
            result.add(new ResultEntry(entry.getKey(), name, entry.getValue(), rank));
            rank++;
        }
        return result;
    }

    private void executeRewards(CompetitionConfig config, List<ResultEntry> results) {
        for (int rank = 1; rank <= 5; rank++) {
            if (rank > results.size()) {
                continue;
            }

            List<String> commands = cleanRewardList(config.rewardCommands(rank));
            if (commands.isEmpty()) {
                continue;
            }

            ResultEntry entry = results.get(rank - 1);
            for (String command : commands) {
                String parsed = CommandPlaceholders.apply(command, Map.of(
                    "player", entry.name(),
                    "score", String.valueOf(entry.score()),
                    "rank", String.valueOf(rank),
                    "competition_id", config.id(),
                    "competition_name", FoText.color(config.displayName())
                ));
                try {
                    if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed)) {
                        plugin.getLogger().warning("Competition reward command was unknown or rejected: " + parsed);
                    }
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(Level.WARNING,
                        "Competition reward command failed for competition '" + config.id()
                            + "', rank " + rank + ".",
                        exception);
                }
            }
        }
    }

    private void publishCompletedEvent(CompetitionConfig config, List<ResultEntry> results, CompletionReason reason) {
        List<MiningCompetitionCompletedEvent.Placement> placements = results.stream()
            .map(result -> new MiningCompetitionCompletedEvent.Placement(
                result.uuid(), result.name(), result.score(), result.rank()))
            .toList();
        Bukkit.getPluginManager().callEvent(new MiningCompetitionCompletedEvent(
            config.id(), config.displayName(), placements, reason));
    }

    private List<String> cleanRewardList(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }

        return raw.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .filter(line -> !line.equalsIgnoreCase("none"))
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<String> readRewardCommands(ConfigurationSection rewards, int rank) {
        if (rewards == null) {
            return new ArrayList<>();
        }

        Object raw = rewards.get("top-" + rank);
        if (raw instanceof List<?> list) {
            List<String> commands = new ArrayList<>();
            for (Object value : list) {
                commands.addAll(splitRewardCommands(value));
            }
            return cleanRewardList(commands);
        }

        if (raw instanceof String) {
            return cleanRewardList(splitRewardCommands(raw));
        }

        return cleanRewardList(rewards.getStringList("top-" + rank));
    }

    private List<String> splitRewardCommands(Object raw) {
        if (raw == null) {
            return new ArrayList<>();
        }

        String input = String.valueOf(raw);
        if (input.isBlank()) {
            return new ArrayList<>();
        }

        String[] pieces = input.split("[|;]");
        List<String> commands = new ArrayList<>();
        for (String piece : pieces) {
            String command = piece.trim();
            if (!command.isEmpty()) {
                commands.add(command);
            }
        }
        return commands;
    }

    private boolean needsRewardSeparatorMigration(ConfigurationSection rewards) {
        if (rewards == null) {
            return false;
        }

        for (int rank = 1; rank <= 5; rank++) {
            Object raw = rewards.get("top-" + rank);
            if (raw instanceof List<?> list) {
                for (Object value : list) {
                    if (containsRewardSeparator(value)) {
                        return true;
                    }
                }
                continue;
            }

            if (containsRewardSeparator(raw)) {
                return true;
            }
        }

        return false;
    }

    private boolean containsRewardSeparator(Object raw) {
        if (raw == null) {
            return false;
        }

        String input = String.valueOf(raw);
        return input.indexOf('|') >= 0 || input.indexOf(';') >= 0;
    }

    private void createBossBarIfNeeded(CompetitionConfig config) {
        if (!config.bossbarEnabled()) {
            return;
        }

        bossBar = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SEGMENTED_10);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!showComponentBossBar(player)) {
                bossBar.addPlayer(player);
            }
        }
        updateBossBar();
    }

    private void updateBossBar() {
        if (bossBar == null || activeCompetition == null) {
            return;
        }

        long remaining = timeRemainingSeconds();
        double progress = activeCompetition.config.durationSeconds() <= 0
            ? 0.0D
            : (double) remaining / activeCompetition.config.durationSeconds();

        bossBar.setProgress(Math.max(0.0D, Math.min(1.0D, progress)));

        String titleTemplate = plugin.getConfig().getString(
            "settings.bossbar-title",
            "{theme}FoMiningComp {muted}| {theme}{name} {muted}- {theme}{time}"
        );

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("name", activeCompetition.config.displayName());
        placeholders.put("time", formatDuration(remaining));
        bossBar.setTitle(messages.renderTemplate(titleTemplate, placeholders));

        float componentProgress = (float) Math.max(0.0D, Math.min(1.0D, progress));
        for (Map.Entry<UUID, net.kyori.adventure.bossbar.BossBar> entry : componentBossBars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            try {
                entry.getValue()
                    .progress(componentProgress)
                    .name(DialogIcons.render(
                        player,
                        messages.renderTemplateForViewer(player, titleTemplate, placeholders)
                    ));
            } catch (RuntimeException | LinkageError exception) {
                plugin.getLogger().fine("Could not update component bossbar for " + player.getName()
                    + ": " + exception.getMessage());
            }
        }
    }

    private void destroyBossBar() {
        for (Map.Entry<UUID, net.kyori.adventure.bossbar.BossBar> entry : componentBossBars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player instanceof Audience audience) {
                audience.hideBossBar(entry.getValue());
            }
        }
        componentBossBars.clear();
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
    }

    private boolean showComponentBossBar(Player player) {
        try {
            if (!(player instanceof Audience audience)) {
                return false;
            }
            net.kyori.adventure.bossbar.BossBar componentBossBar =
                net.kyori.adventure.bossbar.BossBar.bossBar(
                    net.kyori.adventure.text.Component.empty(),
                    1.0F,
                    net.kyori.adventure.bossbar.BossBar.Color.GREEN,
                    net.kyori.adventure.bossbar.BossBar.Overlay.NOTCHED_10
                );
            componentBossBars.put(player.getUniqueId(), componentBossBar);
            audience.showBossBar(componentBossBar);
            return true;
        } catch (RuntimeException | LinkageError exception) {
            componentBossBars.remove(player.getUniqueId());
            plugin.getLogger().fine("Component bossbars are unavailable: " + exception.getMessage());
            return false;
        }
    }

    private String chooseNextCompetitionId() {
        List<CompetitionConfig> enabledCompetitions = competitions.values().stream()
            .filter(CompetitionConfig::enabled)
            .toList();

        if (enabledCompetitions.isEmpty()) {
            return null;
        }

        if (queuedNextCompetitionId != null) {
            CompetitionConfig queued = competition(queuedNextCompetitionId);
            queuedNextCompetitionId = null;
            if (queued != null && queued.enabled()) {
                return queued.id();
            }
        }

        if (lastCompetitionId == null) {
            return enabledCompetitions.getFirst().id();
        }

        int currentIndex = -1;
        for (int i = 0; i < enabledCompetitions.size(); i++) {
            if (enabledCompetitions.get(i).id().equalsIgnoreCase(lastCompetitionId)) {
                currentIndex = i;
                break;
            }
        }

        int nextIndex = currentIndex < 0 ? 0 : (currentIndex + 1) % enabledCompetitions.size();
        return enabledCompetitions.get(nextIndex).id();
    }

    private Material parseMaterial(String raw, Material fallback, String context) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        Material parsed = MaterialTypes.match(raw);
        if (parsed != null) {
            return parsed;
        }

        String fallbackText = fallback == null ? "skipping value" : "using " + fallback.name();
        plugin.getLogger().warning(
            "Unknown material '" + raw.trim() + "' in " + context + "; " + fallbackText
                + ". Check this material exists on the current server version."
        );
        return fallback;
    }

    private String formatMaterials(Set<Material> materials) {
        return materials.stream()
            .sorted((left, right) -> MaterialTypes.displayName(left).compareToIgnoreCase(MaterialTypes.displayName(right)))
            .map(this::formatMaterial)
            .collect(Collectors.joining(", "));
    }

    private String formatMaterial(Material material) {
        String label = MaterialTypes.displayName(material);
        String sprite = DialogIcons.token(material);
        return sprite.isBlank() ? label : sprite + " " + label;
    }

    private void broadcast(String path, String fallback, Map<String, String> placeholders) {
        Bukkit.getConsoleSender().sendMessage(messages.render(path, fallback, placeholders));
        for (Player player : Bukkit.getOnlinePlayers()) {
            messages.send(player, path, fallback, placeholders);
        }
    }

    private File competitionFolder() {
        return new File(plugin.getDataFolder(), COMPETITIONS_FOLDER);
    }

    private void loadCompetitionFiles(File folder) {
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return;
        }

        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - 4);
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            boolean migratedRewardSeparators = needsRewardSeparatorMigration(yaml.getConfigurationSection("rewards"));
            CompetitionConfig competition = parseCompetition(id, yaml);
            competitions.put(id.toLowerCase(Locale.ROOT), competition);
            if (migratedRewardSeparators) {
                saveCompetitionFile(competition);
                plugin.getLogger().info("Migrated reward command separators in " + file.getName() + ".");
            }
        }
    }

    private void ensureDefaultCompetitions(File folder) {
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null && files.length > 0) {
            return;
        }

        CompetitionConfig stoneRush = new CompetitionConfig("stone_rush");
        stoneRush.setDisplayName("#03fc88Stone Rush");
        stoneRush.setIcon(Material.DIAMOND_PICKAXE);
        stoneRush.setDurationSeconds(900);
        stoneRush.setTrackedMaterials(EnumSet.of(Material.STONE, Material.DEEPSLATE, Material.COBBLESTONE));
        stoneRush.setRewardCommands(1, List.of("eco give %player% 1000", "say #1 in %competition_id% was %player% with %score% blocks"));
        stoneRush.setRewardCommands(2, List.of("eco give %player% 700"));
        stoneRush.setRewardCommands(3, List.of("eco give %player% 400"));

        CompetitionConfig oreHunt = new CompetitionConfig("ore_hunt");
        oreHunt.setDisplayName("#03fc88Ore Hunt");
        oreHunt.setIcon(Material.IRON_ORE);
        oreHunt.setDurationSeconds(1200);
        oreHunt.setTrackedMaterials(
            EnumSet.of(
                Material.COAL_ORE,
                Material.IRON_ORE,
                Material.COPPER_ORE,
                Material.GOLD_ORE,
                Material.DIAMOND_ORE,
                Material.EMERALD_ORE,
                Material.DEEPSLATE_COAL_ORE,
                Material.DEEPSLATE_IRON_ORE,
                Material.DEEPSLATE_COPPER_ORE,
                Material.DEEPSLATE_GOLD_ORE,
                Material.DEEPSLATE_DIAMOND_ORE,
                Material.DEEPSLATE_EMERALD_ORE
            )
        );
        oreHunt.setRewardCommands(1, List.of("eco give %player% 2500"));
        oreHunt.setRewardCommands(2, List.of("eco give %player% 1750"));
        oreHunt.setRewardCommands(3, List.of("eco give %player% 1000"));
        oreHunt.setRewardCommands(4, List.of("eco give %player% 500"));
        oreHunt.setRewardCommands(5, List.of("eco give %player% 250"));

        saveCompetitionFile(stoneRush);
        saveCompetitionFile(oreHunt);
    }

    private void migrateLegacyCompetitionsIfNeeded(File folder, FileConfiguration mainConfig) {
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null && files.length > 0) {
            return;
        }

        ConfigurationSection legacy = mainConfig.getConfigurationSection("competitions");
        if (legacy == null || legacy.getKeys(false).isEmpty()) {
            return;
        }

        for (String id : legacy.getKeys(false)) {
            ConfigurationSection section = legacy.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            CompetitionConfig parsed = parseCompetition(id, section);
            saveCompetitionFile(parsed);
        }
    }

    private void saveCompetitionFile(CompetitionConfig competition) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", competition.enabled());
        yaml.set("display-name", competition.displayName());
        yaml.set("icon", competition.icon().name());
        yaml.set("duration-seconds", competition.durationSeconds());
        yaml.set("bossbar-enabled", competition.bossbarEnabled());
        yaml.set("broadcast.start", competition.announceStart());
        yaml.set("broadcast.end", competition.announceEnd());

        List<String> materials = competition.trackedMaterials().stream()
            .map(Material::name)
            .sorted()
            .toList();
        yaml.set("tracked-materials", materials);
        yaml.set("disabled-worlds", competition.disabledWorlds().stream().sorted().toList());

        for (int rank = 1; rank <= 5; rank++) {
            yaml.set("rewards.top-" + rank, competition.rewardCommands(rank));
        }

        File target = new File(competitionFolder(), competition.id().toLowerCase(Locale.ROOT) + ".yml");
        try {
            yaml.save(target);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save competition file " + target.getName() + ": " + exception.getMessage());
        }
    }

    public record ResultEntry(UUID uuid, String name, int score, int rank) {
    }

    private static final class ActiveCompetition {

        private final CompetitionConfig config;
        private final long startsAtMillis;
        private final long endsAtMillis;
        private final Map<UUID, Integer> scores;

        private ActiveCompetition(CompetitionConfig config, long startsAtMillis, long endsAtMillis) {
            this.config = config;
            this.startsAtMillis = startsAtMillis;
            this.endsAtMillis = endsAtMillis;
            this.scores = new HashMap<>();
        }
    }
}
