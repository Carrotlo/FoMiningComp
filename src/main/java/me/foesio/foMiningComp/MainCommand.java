package me.foesio.foMiningComp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.reload.FoReloadResult;
import me.foesio.core.update.UpdateNoticeService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class MainCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERMISSION = "fominingcomp.admin";

    private final FoMiningComp plugin;
    private final FoMessageService messages;
    private final CompetitionManager manager;
    private final EditorGui editorGui;
    private final UpdateNoticeService updateNotices;

    public MainCommand(FoMiningComp plugin, FoMessageService messages, CompetitionManager manager, EditorGui editorGui, UpdateNoticeService updateNotices) {
        this.plugin = plugin;
        this.messages = messages;
        this.manager = manager;
        this.editorGui = editorGui;
        this.updateNotices = updateNotices;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            messages.send(sender, "messages.no-permission", "{prefix}{bad}You do not have permission.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "version" -> {
                messages.send(sender, "messages.version", "{prefix}{theme}Version {version} {muted}| Author: {theme}Carrotio", Map.of("version", plugin.getDescription().getVersion()));
                updateNotices.checkAndSendVersion(sender);
                return true;
            }
            case "reload" -> {
                FoReloadResult result = plugin.reloadPluginResources();
                if (!result.successful()) {
                    messages.sendTemplate(sender, "{prefix}{bad}Reload failed at {theme}{step}{bad}: {muted}{error}", Map.of(
                        "step", result.failedStep(),
                        "error", result.errorMessage()
                    ));
                    return true;
                }
                messages.send(sender, "messages.reload", "{prefix}{good}Configuration reloaded.");
                return true;
            }
            case "editor" -> {
                if (!(sender instanceof Player player)) {
                    messages.send(sender, "messages.player-only", "{prefix}{bad}Only players can use this command.");
                    return true;
                }
                editorGui.openMainEditor(player);
                messages.send(sender, "messages.editor-opened", "{prefix}{good}Opened editor.");
                return true;
            }
            case "start" -> {
                if (args.length < 2) {
                    messages.send(sender, "messages.usage-start", "{prefix}{bad}Usage: {theme}/{label} start <id>", Map.of("label", label));
                    return true;
                }

                String id = args[1];
                if (!manager.startCompetition(id, sender)) {
                    messages.send(sender, "messages.competition-not-found", "{prefix}{bad}Competition {theme}{id}{bad} was not found or is disabled.", Map.of("id", id));
                }
                return true;
            }
            case "stop" -> {
                manager.stopCompetition(sender);
                return true;
            }
            case "setnext" -> {
                if (args.length < 2) {
                    messages.send(sender, "messages.usage-setnext", "{prefix}{bad}Usage: {theme}/{label} setnext <id>", Map.of("label", label));
                    return true;
                }

                String id = args[1];
                if (!manager.queueNext(id)) {
                    messages.send(sender, "messages.competition-not-found", "{prefix}{bad}Competition {theme}{id}{bad} was not found or is disabled.", Map.of("id", id));
                    return true;
                }

                messages.send(sender, "messages.set-next", "{prefix}{good}Queued {theme}{id}{muted} as next competition.", Map.of("id", id));
                return true;
            }
            case "status" -> {
                sendStatus(sender);
                return true;
            }
            case "list" -> {
                sendList(sender);
                return true;
            }
            default -> {
                sendHelp(sender);
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            return List.of();
        }

        if (args.length == 1) {
            return filter(List.of("help", "version", "editor", "reload", "start", "stop", "status", "setnext", "list"), args[0]);
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("setnext"))) {
            return filter(
                manager.competitions().stream().map(CompetitionConfig::id).collect(Collectors.toList()),
                args[1]
            );
        }

        return List.of();
    }

    private void sendHelp(CommandSender sender) {
        for (String key : List.of(
            "help-header",
            "help-help",
            "help-version",
            "help-editor",
            "help-reload",
            "help-start",
            "help-stop",
            "help-setnext",
            "help-status",
            "help-list"
        )) {
            messages.send(sender, "messages." + key, fallback(key));
        }
    }

    private void sendStatus(CommandSender sender) {
        if (manager.hasActiveCompetition()) {
            messages.send(sender, "messages.status-active", "{prefix}{muted}Active: {theme}{name} {muted}| Remaining: {theme}{remaining}", Map.of(
                "name", manager.activeCompetitionDisplayName(),
                "remaining", manager.formatDuration(manager.timeRemainingSeconds())
            ));
        } else {
            messages.send(sender, "messages.status-inactive", "{prefix}{muted}No active competition. Next in: {theme}{time} {muted}({theme}{id}{muted})", Map.of(
                "time", manager.formatDuration(manager.timeUntilNextSeconds()),
                "id", manager.nextCompetitionId()
            ));
        }
    }

    private void sendList(CommandSender sender) {
        List<CompetitionConfig> competitions = new ArrayList<>(manager.competitions());
        competitions.sort(Comparator.comparing(CompetitionConfig::id));

        if (competitions.isEmpty()) {
            messages.send(sender, "messages.list-empty", "{prefix}{bad}No competitions configured.");
            return;
        }

        messages.send(sender, "messages.list-header", "{prefix}{muted}Configured competitions:");
        for (CompetitionConfig competition : competitions) {
            String status = competition.enabled()
                ? messages.render("messages.state-enabled", "{good}enabled")
                : messages.render("messages.state-disabled", "{bad}disabled");
            messages.send(sender, "messages.list-entry", "{prefix}{theme}{id} {muted}- {status} {muted}| materials: {theme}{materials}", Map.of(
                "id", competition.id(),
                "status", status,
                "materials", String.valueOf(competition.trackedMaterials().size())
            ));
        }
    }

    private List<String> filter(List<String> options, String raw) {
        String lowered = raw.toLowerCase(Locale.ROOT);
        return options.stream()
            .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lowered))
            .sorted()
            .toList();
    }

    private String fallback(String key) {
        return switch (key) {
            case "help-header" -> "{prefix}{muted}Commands:";
            case "help-help" -> "{prefix}{theme}/fominingcomp help {muted}- Show this help";
            case "help-version" -> "{prefix}{theme}/fominingcomp version {muted}- Show author and plugin version";
            case "help-editor" -> "{prefix}{theme}/fominingcomp editor {muted}- Open GUI editor";
            case "help-reload" -> "{prefix}{theme}/fominingcomp reload {muted}- Reload config and messages";
            case "help-start" -> "{prefix}{theme}/fominingcomp start <id> {muted}- Start competition now";
            case "help-stop" -> "{prefix}{theme}/fominingcomp stop {muted}- Stop active competition";
            case "help-setnext" -> "{prefix}{theme}/fominingcomp setnext <id> {muted}- Force the next competition";
            case "help-status" -> "{prefix}{theme}/fominingcomp status {muted}- Show live status";
            case "help-list" -> "{prefix}{theme}/fominingcomp list {muted}- List all configured competitions";
            default -> "{prefix}{muted}" + key;
        };
    }
}
