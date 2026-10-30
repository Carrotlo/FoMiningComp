package me.foesio.foMiningComp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import me.foesio.foMiningComp.api.ActionResult;
import me.foesio.foMiningComp.api.CompletionReason;
import me.foesio.foMiningComp.api.CompetitionDefinition;
import me.foesio.foMiningComp.api.MiningCompetitionCompletedEvent;
import me.foesio.foMiningComp.api.MiningCompetitionPreStartEvent;
import me.foesio.foMiningComp.api.LiveStanding;
import me.foesio.foMiningComp.api.StartCause;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

class CompetitionApiBehaviorTest {

    @Test
    void cancelledScheduledStartSkipsCandidateAndSchedulesNext() throws Exception {
        CompetitionManager manager = manager();
        definitions(manager).put("alpha", new CompetitionConfig("alpha"));
        definitions(manager).put("beta", new CompetitionConfig("beta"));
        set(manager, "autoStart", true);
        set(manager, "intervalBetweenCompetitionsSeconds", 30);
        set(manager, "scheduledNextCompetitionId", "alpha");
        set(manager, "nextStartAtMillis", System.currentTimeMillis() - 1L);

        List<Event> events = new ArrayList<>();
        try (AutoCloseable server = installServer(events, event -> {
            if (event instanceof MiningCompetitionPreStartEvent preStart) {
                preStart.setCancelled(true);
            }
        })) {
            Method tick = CompetitionManager.class.getDeclaredMethod("tick");
            tick.setAccessible(true);
            tick.invoke(manager);
        }

        assertFalse(manager.hasActiveCompetition());
        assertEquals("beta", manager.nextSnapshot().id());
        assertTrue(manager.nextSnapshot().startsAtMillis() > System.currentTimeMillis());
        assertEquals(1, events.size());
    }

    @Test
    void cancelledManualStartDoesNotReplaceRunningCompetition() throws Exception {
        CompetitionManager manager = manager();
        CompetitionConfig active = new CompetitionConfig("alpha");
        definitions(manager).put("alpha", active);
        definitions(manager).put("beta", new CompetitionConfig("beta"));
        set(manager, "activeCompetition", activeInstance(active));

        List<Event> events = new ArrayList<>();
        try (AutoCloseable server = installServer(events, event -> {
            if (event instanceof MiningCompetitionPreStartEvent preStart) {
                preStart.setCancelled(true);
            }
        })) {
            assertEquals(ActionResult.CANCELLED,
                manager.requestStartCompetition("beta", null, StartCause.COMMAND));
        }

        assertEquals("alpha", manager.activeSnapshot().id());
        assertEquals(1, events.size());
    }

    @Test
    void cancelledManualStartKeepsDifferentScheduledCandidate() throws Exception {
        CompetitionManager manager = manager();
        definitions(manager).put("alpha", new CompetitionConfig("alpha"));
        definitions(manager).put("beta", new CompetitionConfig("beta"));
        set(manager, "autoStart", true);
        set(manager, "scheduledNextCompetitionId", "beta");
        set(manager, "nextStartAtMillis", System.currentTimeMillis() + 60_000L);

        try (AutoCloseable server = installServer(new ArrayList<>(), event -> {
            if (event instanceof MiningCompetitionPreStartEvent preStart) {
                preStart.setCancelled(true);
            }
        })) {
            assertEquals(ActionResult.CANCELLED,
                manager.requestStartCompetition("alpha", null, StartCause.COMMAND));
        }
        assertEquals("beta", manager.nextSnapshot().id());
    }

    @Test
    void scoreAwardsRejectIneligibleAndOverflowWithoutMutation() throws Exception {
        CompetitionManager manager = manager();
        UUID playerId = UUID.randomUUID();
        assertEquals(ActionResult.NO_ACTIVE_COMPETITION,
            manager.awardScore(playerId, Material.DIAMOND_ORE, "world", 1));
        CompetitionConfig config = new CompetitionConfig("ore");
        config.setTrackedMaterials(EnumSet.of(Material.DIAMOND_ORE));
        config.setDisabledWorlds(Set.of("nether"));
        Object active = activeInstance(config);
        set(manager, "activeCompetition", active);

        assertEquals(ActionResult.SUCCESS, manager.awardScore(playerId, Material.DIAMOND_ORE, "world", 2));
        assertEquals(ActionResult.INELIGIBLE, manager.awardScore(playerId, Material.STONE, "world", 1));
        assertEquals(ActionResult.INELIGIBLE, manager.awardScore(playerId, Material.DIAMOND_ORE, "NETHER", 1));
        assertEquals(ActionResult.INVALID_INPUT, manager.awardScore(playerId, Material.DIAMOND_ORE, "world", 0));
        assertEquals(2, manager.playerScore(playerId));

        Field scoresField = active.getClass().getDeclaredField("scores");
        scoresField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, Integer> scores = (Map<UUID, Integer>) scoresField.get(active);
        scores.put(playerId, Integer.MAX_VALUE);
        assertEquals(ActionResult.OVERFLOW, manager.awardScore(playerId, Material.DIAMOND_ORE, "world", 1));
        assertEquals(Integer.MAX_VALUE, manager.playerScore(playerId));
    }

    @Test
    void completionListenerCanStartNextWithoutOldEndOverwritingSchedule() throws Exception {
        CompetitionManager manager = manager();
        CompetitionConfig alpha = quiet("alpha");
        CompetitionConfig beta = quiet("beta");
        definitions(manager).put("alpha", alpha);
        definitions(manager).put("beta", beta);
        set(manager, "activeCompetition", activeInstance(alpha));
        set(manager, "autoStart", true);

        List<Event> events = new ArrayList<>();
        try (AutoCloseable server = installServer(events, event -> {
            if (event instanceof MiningCompetitionCompletedEvent) {
                assertEquals(ActionResult.SUCCESS,
                    manager.requestStartCompetition("beta", null, StartCause.API));
            }
        })) {
            assertEquals(ActionResult.SUCCESS,
                manager.requestStopCompetition(CompletionReason.API_STOP, null));
        }

        assertEquals("beta", manager.activeSnapshot().id());
        assertEquals(null, manager.nextSnapshot());
        assertTrue(events.getFirst() instanceof MiningCompetitionCompletedEvent);
        assertEquals(CompletionReason.API_STOP,
            ((MiningCompetitionCompletedEvent) events.getFirst()).getReason());
    }

    @Test
    void replacementKeepsPreviouslyQueuedChoice() throws Exception {
        CompetitionManager manager = manager();
        CompetitionConfig alpha = quiet("alpha");
        CompetitionConfig beta = quiet("beta");
        CompetitionConfig gamma = quiet("gamma");
        definitions(manager).put("alpha", alpha);
        definitions(manager).put("beta", beta);
        definitions(manager).put("gamma", gamma);
        set(manager, "activeCompetition", activeInstance(alpha));
        set(manager, "autoStart", true);
        set(manager, "queuedNextCompetitionId", "gamma");

        List<Event> events = new ArrayList<>();
        try (AutoCloseable server = installServer(events, event -> {})) {
            assertEquals(ActionResult.SUCCESS,
                manager.requestStartCompetition("beta", null, StartCause.API));
        }

        assertEquals("beta", manager.activeSnapshot().id());
        assertEquals("gamma", manager.nextSnapshot().id());
        assertEquals(CompletionReason.REPLACED,
            ((MiningCompetitionCompletedEvent) events.get(1)).getReason());
        assertEquals(3, events.size());
    }

    @Test
    void startingQueuedCandidateConsumesItBeforeNextRotation() throws Exception {
        CompetitionManager manager = manager();
        definitions(manager).put("alpha", quiet("alpha"));
        definitions(manager).put("beta", quiet("beta"));
        set(manager, "autoStart", true);
        assertEquals(ActionResult.SUCCESS, manager.requestQueueNext("beta"));

        try (AutoCloseable server = installServer(new ArrayList<>(), event -> {})) {
            assertEquals(ActionResult.SUCCESS,
                manager.requestStartCompetition("beta", null, StartCause.SCHEDULED));
            assertEquals(null, manager.nextSnapshot());
            assertEquals(ActionResult.SUCCESS,
                manager.requestStopCompetition(CompletionReason.API_STOP, null));
        }

        assertEquals("alpha", manager.nextSnapshot().id());
    }

    @Test
    void competitionDefinitionCopiesMutableSettingsAndRewards() {
        Set<Material> materials = EnumSet.of(Material.STONE);
        Set<String> worlds = new HashSet<>(Set.of("nether"));
        List<String> commands = new ArrayList<>(List.of("eco give %player% 10"));
        Map<Integer, List<String>> rewards = new HashMap<>();
        rewards.put(1, commands);
        CompetitionDefinition definition = new CompetitionDefinition("stone", "Stone", Material.STONE,
            true, 60, materials, worlds, true, true, false, rewards);

        materials.add(Material.DIAMOND_ORE);
        worlds.clear();
        commands.clear();
        rewards.clear();
        assertEquals(Set.of(Material.STONE), definition.trackedMaterials());
        assertEquals(Set.of("nether"), definition.disabledWorlds());
        assertEquals(List.of("eco give %player% 10"), definition.rewardCommands().get(1));
    }

    @Test
    void liveStandingsUseSharedRanksForEqualScores() throws Exception {
        CompetitionManager manager = manager();
        Object active = activeInstance(quiet("stone"));
        set(manager, "activeCompetition", active);
        Field scoresField = active.getClass().getDeclaredField("scores");
        scoresField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, Integer> scores = (Map<UUID, Integer>) scoresField.get(active);
        scores.put(UUID.randomUUID(), 5);
        scores.put(UUID.randomUUID(), 5);
        scores.put(UUID.randomUUID(), 3);

        List<LiveStanding> standings;
        try (AutoCloseable server = installServer(new ArrayList<>(), event -> {})) {
            standings = manager.liveStandings();
        }
        assertEquals(List.of(1, 1, 3), standings.stream().map(LiveStanding::rank).toList());
    }

    private CompetitionManager manager() {
        return new CompetitionManager(null, null);
    }

    private CompetitionConfig quiet(String id) {
        CompetitionConfig config = new CompetitionConfig(id);
        config.setBossbarEnabled(false);
        config.setAnnounceStart(false);
        return config;
    }

    private AutoCloseable installServer(List<Event> events, Consumer<Event> onEvent) throws Exception {
        PluginManager pluginManager = (PluginManager) Proxy.newProxyInstance(
            PluginManager.class.getClassLoader(), new Class<?>[]{PluginManager.class},
            (proxy, method, args) -> {
                if (method.getName().equals("callEvent")) {
                    Event event = (Event) args[0];
                    events.add(event);
                    onEvent.accept(event);
                }
                return null;
            });
        Server server = (Server) Proxy.newProxyInstance(
            Server.class.getClassLoader(), new Class<?>[]{Server.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getPluginManager" -> pluginManager;
                case "getOnlinePlayers" -> List.of();
                case "getOfflinePlayer" -> Proxy.newProxyInstance(
                    OfflinePlayer.class.getClassLoader(), new Class<?>[]{OfflinePlayer.class},
                    (offline, offlineMethod, offlineArgs) ->
                        offlineMethod.getName().equals("getName") ? "Miner" : null);
                default -> null;
            });
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        Object previous = serverField.get(null);
        serverField.set(null, server);
        return () -> serverField.set(null, previous);
    }

    private Object activeInstance(CompetitionConfig config) throws Exception {
        Class<?> type = Class.forName("me.foesio.foMiningComp.CompetitionManager$ActiveCompetition");
        Constructor<?> constructor = type.getDeclaredConstructor(CompetitionConfig.class, long.class, long.class);
        constructor.setAccessible(true);
        return constructor.newInstance(config, 1L, Long.MAX_VALUE);
    }

    @SuppressWarnings("unchecked")
    private Map<String, CompetitionConfig> definitions(CompetitionManager manager) throws Exception {
        Field field = CompetitionManager.class.getDeclaredField("competitions");
        field.setAccessible(true);
        return (Map<String, CompetitionConfig>) field.get(manager);
    }

    private void set(CompetitionManager manager, String name, Object value) throws Exception {
        Field field = CompetitionManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(manager, value);
    }
}
