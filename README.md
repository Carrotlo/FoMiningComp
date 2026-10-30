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

Compile against the matching jar (`provided` in Maven, `compileOnly` in Gradle); install it locally if needed. Declare `FoMiningComp` in your plugin metadata (`softdepend` for an optional integration).

```xml
<dependency>
  <groupId>me.foesio</groupId>
  <artifactId>FoMiningComp</artifactId>
  <version>2.0</version>
  <scope>provided</scope>
</dependency>
```

```java
import me.foesio.foMiningComp.api.FoMiningCompApi;
import me.foesio.foMiningComp.api.MiningCompetitionCompletedEvent;

FoMiningCompApi api = Bukkit.getServicesManager().load(FoMiningCompApi.class);
if (api != null) api.activeCompetition();

@EventHandler
public void onMiningCompetitionCompletedEvent(MiningCompetitionCompletedEvent event) {
    Bukkit.getLogger().info(event.getClass().getSimpleName());
}
```

**API methods** (overloads grouped):

- `FoMiningCompApi`: `isCompetitionActive`, `activeCompetitionId`, `activeCompetition`, `nextCompetition`, `competitions`, `playerScore`, `playerRank`, `liveStandings`, `startCompetition`, `stopCompetition`, `queueNext`, `awardScore`.

**Other API types:** `ActionResult`, `ActiveCompetitionSnapshot`, `CompetitionDefinition`, `CompletionReason`, `LiveStanding`, `NextCompetitionSnapshot`, `StartCause`.

**Events:** `MiningCompetitionCompletedEvent`, `MiningCompetitionPreStartEvent`, `MiningCompetitionStartedEvent`.

[Browse API source](src/main/java/me/foesio/foMiningComp/api) for signatures and event details.

</details>
