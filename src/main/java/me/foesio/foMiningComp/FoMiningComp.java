package me.foesio.foMiningComp;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import me.foesio.core.FoCoreContext;
import me.foesio.core.FoPluginCore;
import me.foesio.core.command.CommandVisibilityService;
import me.foesio.core.dialog.NativeDialogConfigDefaults;
import me.foesio.core.message.FoMessageMigrations;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.reload.FoReloadRegistry;
import me.foesio.core.reload.FoReloadResult;
import me.foesio.core.sound.FoAdminSounds;
import me.foesio.core.sound.FoEditorSounds;
import me.foesio.core.sound.FoSoundService;
import me.foesio.core.update.UpdateNoticeService;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class FoMiningComp extends JavaPlugin {

    private static final String MODRINTH_PROJECT_ID = "fominingcomp";
    private static final int BSTATS_PLUGIN_ID = 33037;

    private FoCoreContext core;
    private FoMessageService messages;
    private CompetitionManager competitionManager;
    private EditorGui editorGui;
    private UpdateNoticeService updateNotices;
    private CommandVisibilityService commandVisibility;
    private FoSoundService sounds;
    private FoAdminSounds adminSounds;
    private FoEditorSounds editorSounds;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ensureConfigDefaults();

        refreshCoreContext();
        messages = FoMessageService.load(this, messageMigrations());
        competitionManager = new CompetitionManager(this, messages);
        editorGui = new EditorGui(this, messages, competitionManager, editorSounds);
        updateNotices = core.createUpdateNotices(messages, MODRINTH_PROJECT_ID, adminSounds).start();

        competitionManager.loadFromConfig();

        getServer().getPluginManager().registerEvents(competitionManager, this);
        getServer().getPluginManager().registerEvents(editorGui, this);

        registerCommands();
        registerCommandVisibility();
        hookPlaceholderApi();

        getLogger().info("FoMiningComp enabled.");
    }

    @Override
    public void onDisable() {
        if (competitionManager != null) {
            competitionManager.shutdown();
        }
        if (commandVisibility != null) {
            commandVisibility.close();
            commandVisibility = null;
        }
        if (core != null) {
            core.close();
            core = null;
        }
    }

    public FoMessageService messages() {
        return messages;
    }

    public FoCoreContext core() {
        return core;
    }

    public FoEditorSounds editorSounds() {
        return editorSounds;
    }

    public FoAdminSounds adminSounds() {
        return adminSounds;
    }

    public FoSoundService sounds() {
        return sounds;
    }

    public FoReloadResult reloadPluginResources() {
        return FoReloadRegistry.create()
            .addConfig(this)
            .add("config-defaults", this::ensureConfigDefaults)
            .add("core", this::refreshCoreContext)
            .add("sounds", sounds::reload)
            .addMessages(messages)
            .add("competitions", competitionManager::loadFromConfig)
            .reload();
    }

    private void ensureConfigDefaults() {
        YamlConfiguration defaults = loadDefaultConfigResource();
        FileConfiguration config = getConfig();
        boolean changed = false;

        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key) || config.contains(key)) {
                continue;
            }
            config.set(key, defaults.get(key));
            changed = true;
        }

        boolean nativeDialogDefaultsMissing = !config.contains(NativeDialogConfigDefaults.ENABLED_PATH)
            || !config.contains(NativeDialogConfigDefaults.WARN_ON_FALLBACK_PATH);
        NativeDialogConfigDefaults.addDefaults(config);
        changed = nativeDialogDefaultsMissing || changed;

        if (changed) {
            saveConfig();
        }
    }

    private YamlConfiguration loadDefaultConfigResource() {
        InputStream stream = getResource("config.yml");
        if (stream == null) {
            return new YamlConfiguration();
        }

        try (InputStream input = stream; InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException exception) {
            getLogger().warning("Could not read default config.yml: " + exception.getMessage());
            return new YamlConfiguration();
        }
    }

    private void registerCommands() {
        PluginCommand command = getCommand("fominingcomp");
        if (command == null) {
            getLogger().severe("Command fominingcomp is missing in plugin.yml");
            return;
        }

        MainCommand executor = new MainCommand(this, messages, competitionManager, editorGui, updateNotices);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void registerCommandVisibility() {
        if (commandVisibility != null) {
            commandVisibility.close();
        }
        commandVisibility = CommandVisibilityService.builder(this)
            .hideWithout("fominingcomp.admin", "fominingcomp", "miningcomp")
            .register();
    }

    private void refreshCoreContext() {
        if (core != null) {
            core.close();
        }
        core = FoPluginCore.create(this);
        core.warnIfNativeDialogsUnavailable();
        core.metrics(BSTATS_PLUGIN_ID);
        if (sounds == null) {
            sounds = core.createSounds();
            adminSounds = FoAdminSounds.create(sounds);
            editorSounds = FoEditorSounds.create(sounds);
        } else {
            sounds.reload();
        }
    }

    private FoMessageMigrations messageMigrations() {
        return FoMessageMigrations.create()
            .add(this::migrateLegacyConfigMessages)
            .add(this::resaveWrappedMessages)
            .build();
    }

    private boolean migrateLegacyConfigMessages(FileConfiguration messagesConfig) {
        FileConfiguration config = getConfig();
        ConfigurationSection legacy = config.getConfigurationSection("messages");
        if (legacy == null) {
            return false;
        }

        for (String key : legacy.getKeys(true)) {
            if (legacy.isConfigurationSection(key)) {
                continue;
            }
            messagesConfig.set("messages." + key, legacy.get(key));
        }

        config.set("messages", null);
        saveConfig();
        getLogger().info("Migrated legacy config.yml messages to messages.yml.");
        return true;
    }

    private boolean resaveWrappedMessages(FileConfiguration ignored) {
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            return false;
        }

        try {
            List<String> lines = Files.readAllLines(messagesFile.toPath(), StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size() - 1; i++) {
                String current = lines.get(i);
                String next = lines.get(i + 1);
                if (current.matches("^\\s+[A-Za-z0-9_-]+: ['\\\"].*") && !endsWithClosingQuote(current)
                    && next.matches("^\\s{4,}\\S.*['\\\"]\\s*$")) {
                    return true;
                }
            }
        } catch (IOException exception) {
            getLogger().warning("Could not inspect messages.yml wrapping: " + exception.getMessage());
        }
        return false;
    }

    private boolean endsWithClosingQuote(String line) {
        String trimmed = line.trim();
        return trimmed.endsWith("'") || trimmed.endsWith("\"");
    }

    private void hookPlaceholderApi() {
        Plugin placeholderApi = getServer().getPluginManager().getPlugin("PlaceholderAPI");
        if (placeholderApi == null || !placeholderApi.isEnabled()) {
            return;
        }

        if (FoMiningCompPlaceholders.register(this, competitionManager)) {
            getLogger().info("PlaceholderAPI detected. Registered FoMiningComp placeholders.");
        }
    }
}
