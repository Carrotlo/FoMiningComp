# FoMiningComp

[![Modrinth](https://img.shields.io/badge/Download-Modrinth-1bd96a?logo=modrinth&logoColor=white)](https://modrinth.com/plugin/fominingcomp) [![Versions](https://img.shields.io/badge/Supported_versions-Modrinth-1bd96a)](https://modrinth.com/plugin/fominingcomp/versions) [![Discord](https://img.shields.io/badge/Support-Discord-5865f2?logo=discord&logoColor=white)](https://discord.gg/kEF77QCfu7)

Automatic mining competitions with live rankings and configurable rewards.

*Developed by Carrotio.*

## Features

- Runs scheduled competitions where players race to mine configured blocks.
- Tracks live scores, rankings, and top finishers.
- Executes configurable commands for ranked rewards.
- Provides controls to start, stop, queue, inspect, and edit competitions.
- Exposes live competition and leaderboard placeholders.

## Download and dependencies

Download the latest compatible version from [Modrinth](https://modrinth.com/plugin/fominingcomp). Check the [versions page](https://modrinth.com/plugin/fominingcomp/versions) for supported Minecraft versions and server platforms.

- **Required:** None
- **Optional integrations:** `PlaceholderAPI`

## Support

For help or issue reports, join the [Carrotio Discord server](https://discord.gg/kEF77QCfu7).

<details>
<summary><strong>Commands and permissions</strong></summary>

### Commands

- `/fominingcomp <help|version|reload|editor|start|stop|setnext|status|list>` — Main command for FoMiningComp Aliases: `/miningcomp`.

### Permissions

- `fominingcomp.admin` — Access to all FoMiningComp admin commands Default: `op`.

</details>

<details>
<summary><strong>PlaceholderAPI</strong></summary>

Requires PlaceholderAPI.

- `%fominingcomp_time_remaining%` — Formatted time left in the active competition.
- `%fominingcomp_time_until_next%` — Formatted time until the next competition.
- `%fominingcomp_active_id% / %fominingcomp_active_name%` — Active competition identifier or display name.
- `%fominingcomp_next_id%` — Next competition identifier.
- `%fominingcomp_status%` — `running` or `idle`.
- `%fominingcomp_player_score% / %fominingcomp_player_rank%` — Player's live score or rank.
- `%fominingcomp_top_<1-5>_player% / %fominingcomp_top_<1-5>_score%` — Top-five player name or score.

</details>

<details>
<summary><strong>Configuration</strong></summary>

- `config.yml` — Main behavior and feature settings.
- `messages.yml` — Player-facing messages and interface text.
- `sounds.yml` — Configurable interaction sounds.

Main config areas: `settings`.

</details>

<details>
<summary><strong>Developer API</strong></summary>

The public types are in `me.foesio.foMiningComp.api`. This checkout uses Java 21,
`plugin.yml`, and Bukkit's service registry. Compile against the **FoMiningComp
jar** as a provided dependency; do not shade a second copy of its API into your
plugin. The Maven coordinates below come from this project's `pom.xml`; install
the built jar locally first if you do not have a repository containing it:

```sh
mvn install:install-file -Dfile=/path/to/FoMiningComp-2.0.jar -DgroupId=me.foesio -DartifactId=FoMiningComp -Dversion=2.0 -Dpackaging=jar
```

```xml
<dependency>
  <groupId>me.foesio</groupId>
  <artifactId>FoMiningComp</artifactId>
  <version>2.0</version>
  <scope>provided</scope>
</dependency>
```

For an optional `plugin.yml` consumer, use `softdepend: [FoMiningComp]` and keep
all direct FoMiningComp class references in a separate integration class that
you load only after checking `getPlugin("FoMiningComp")` is enabled. Use `depend`
instead if your plugin cannot run without FoMiningComp. Reacquire the service
after a disable/enable; a cached service is invalid after disable.

```java
// Put this in a separate class loaded only when FoMiningComp is enabled.
public final class MiningIntegration {
    public static ActionResult award(Player player) {
        FoMiningCompApi api = Bukkit.getServicesManager().load(FoMiningCompApi.class);
        if (api == null) return ActionResult.PROVIDER_DISABLED;
        api.activeCompetition().ifPresent(active ->
            Bukkit.getLogger().info("Mining competition: " + active.displayName()));
        return api.awardScore(player.getUniqueId(), Material.DIAMOND_ORE,
            player.getWorld().getName(), 1);
    }
}
```

### Service contract

All service methods must run on the Bukkit main server thread. Off-thread calls
throw `IllegalStateException`. Read methods also throw after the provider is
disabled; action methods return `PROVIDER_DISABLED`. The service is registered
after competition data and commands load, and unregistered during disable.
These methods do not apply a player's `fominingcomp.admin` permission: only
trusted server plugins can call them, and the caller decides who may invoke its
own controls.

- `activeCompetition()` is empty while idle; otherwise it contains the live ID,
  display name, and Unix-millisecond start/end timestamps. The older
  `isCompetitionActive()` and `activeCompetitionId()` methods remain available;
  the latter returns `"none"` while idle.
- `nextCompetition()` is empty if no candidate is known. It returns a queued or
  scheduled ID; `startsAtMillis == -1` means no automatic start time is known,
  including while a competition is active or auto-start is disabled.
- `competitions()` returns immutable snapshots of the currently loaded
  definitions. Nested material/world sets and reward-command lists are copied.
  Editor changes persist to YAML; an existing snapshot never changes.
- `playerScore()` and `playerRank()` return zero while idle or before a player
  scores. Equal scores share a live rank. `liveStandings()` is a descending,
  immutable snapshot of current scored players; tie ordering is unspecified.
  Final reward placements use sequential positions. Scores and live timers are
  memory-only and are not restored after restart.
- `startCompetition(id)` accepts an enabled definition. If another competition
  runs, it completes first with reason `REPLACED`. `stopCompetition()` completes
  the active one with reason `API_STOP`; configured reward commands still run.
  `queueNext(id)` selects an enabled definition; auto-start must be enabled for
  it to start automatically. These methods return
  `ActionResult`, including `NOT_FOUND`, `DISABLED`, `NO_ACTIVE_COMPETITION`,
  `CANCELLED`, `BUSY`, and `INVALID_INPUT`. A cancelled idle start may advance
  the normal automatic schedule as described below.
- `awardScore(playerId, material, worldName, amount)` credits a positive amount
  only while active, when the material is tracked and the named world is
  not disabled. It accepts an offline player UUID and trusts the calling plugin
  to report a real mining action. Invalid, ineligible, and overflowing requests
  do not change the score. It uses the same scoring rules as normal block breaks.

### Events

Register a Bukkit listener normally; the provider's `plugin.yml` classloader
must be available when the listener class loads. Events are dispatched from the
same server thread as the command or scheduler transition. They are synchronous,
non-async Bukkit events; do not invoke the service from an async worker. This
plugin uses Bukkit's standard scheduler and does not promise Folia region-thread
compatibility.

```java
@EventHandler
public void onMiningEnd(MiningCompetitionCompletedEvent event) {
    if (event.getReason() != CompletionReason.TIME_EXPIRED) return;
    for (MiningCompetitionCompletedEvent.Placement placement : event.getPlacements()) {
        // Give an independent bonus using placement.playerId(), score(), rank().
    }
}
```

- `MiningCompetitionPreStartEvent` fires only for a known, enabled definition,
  before an active competition is replaced or new state is set. Cancelling it
  leaves an active competition untouched. If the rejected candidate was due to
  start while idle with auto-start enabled, it is skipped and the next enabled
  competition is scheduled after the normal interval. A separate candidate
  already scheduled by an admin is left in place. With auto-start disabled, no
  automatic start follows.
  The event provides ID, display name, and `StartCause` (`SCHEDULED`, `COMMAND`,
  `API`). No start or completion event is emitted for a cancelled start.
- `MiningCompetitionStartedEvent` fires after the new competition becomes active
  and its bossbar is created, before start broadcasts and sounds. It contains
  the ID, name, cause, and Unix-millisecond start/end timestamps. It cannot be
  cancelled.
- `MiningCompetitionCompletedEvent` fires after final placements are calculated
  and configured reward commands have been attempted, before scheduling the next
  competition. It also fires for early ends: `MANUAL_STOP`, `API_STOP`,
  `REPLACED`, `DISABLED`, and `RELOADED`. `TIME_EXPIRED` marks a natural finish.
  Placements are immutable and the event cannot change existing payouts. The
  three-argument constructor remains for binary compatibility and sets reason
  `UNKNOWN`; FoMiningComp itself always supplies a specific reason. Plugin
  disable only shuts down the ticker and bossbar; it does not finalize the
  active competition or emit this event.

### Complete public API method index

Signatures use short type names; import Fo types from this plugin's `api` package and Bukkit types from Bukkit.

#### `FoMiningCompApi`

```java
boolean isCompetitionActive();
String activeCompetitionId();
Optional<ActiveCompetitionSnapshot> activeCompetition();
Optional<NextCompetitionSnapshot> nextCompetition();
List<CompetitionDefinition> competitions();
int playerScore(UUID);
int playerRank(UUID);
List<LiveStanding> liveStandings();
ActionResult startCompetition(String);
ActionResult stopCompetition();
ActionResult queueNext(String);
ActionResult awardScore(UUID, Material, String, int);
```

**Other public contract types:** `ActionResult`, `ActiveCompetitionSnapshot`, `CompetitionDefinition`, `CompletionReason`, `LiveStanding`, `NextCompetitionSnapshot`, `StartCause`.

### Complete Bukkit event index

- `MiningCompetitionCompletedEvent`.
- `MiningCompetitionPreStartEvent`.
- `MiningCompetitionStartedEvent`.

</details>
