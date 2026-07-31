package me.foesio.foMiningComp;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import me.foesio.core.dialog.DialogButton;
import me.foesio.core.dialog.TextDialogRequest;
import me.foesio.core.editor.EditorDialogInputs;
import me.foesio.core.editor.EditorItemFactory;
import me.foesio.core.gui.GuiButtonConfig;
import me.foesio.core.gui.GuiSlots;
import me.foesio.core.gui.GuiTitles;
import me.foesio.core.material.MaterialChooserClick;
import me.foesio.core.material.MaterialChooserHolder;
import me.foesio.core.material.MaterialChooserMenus;
import me.foesio.core.material.MaterialChooserMode;
import me.foesio.core.material.MaterialChooserRequest;
import me.foesio.core.material.MaterialSelections;
import me.foesio.core.material.MaterialTypes;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.message.FoStyle;
import me.foesio.core.number.LargeNumberParser;
import me.foesio.core.selector.TriStateSelectionClick;
import me.foesio.core.selector.TriStateSelectionHolder;
import me.foesio.core.selector.TriStateSelectionMenus;
import me.foesio.core.selector.TriStateSelectionRequest;
import me.foesio.core.selector.TriStateSelectionState;
import me.foesio.core.selector.TriStateSelections;
import me.foesio.core.selector.WorldSelectionEntries;
import me.foesio.core.text.FoText;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class EditorGui implements Listener {

    private static final int DIALOG_BODY_WIDTH = 300;
    private static final int DIALOG_INPUT_WIDTH = 300;
    private static final int DIALOG_BUTTON_WIDTH = 100;
    private static final int MAIN_EDITOR_ROWS = 6;
    private static final int MAIN_EDITOR_SIZE = GuiSlots.sizeForRows(MAIN_EDITOR_ROWS);
    private static final int COMPETITION_EDITOR_ROWS = 4;
    private static final int COMPETITION_EDITOR_SIZE = GuiSlots.sizeForRows(COMPETITION_EDITOR_ROWS);
    private static final GuiButtonConfig BUTTONS = GuiButtonConfig.defaults();

    private final FoMiningComp plugin;
    private final FoMessageService messages;
    private final CompetitionManager manager;

    private final NamespacedKey actionKey;
    private final NamespacedKey competitionKey;
    private final NamespacedKey rankKey;
    private final Map<UUID, String> materialSelectors;
    private final Map<UUID, String> worldSelectors;

    public EditorGui(FoMiningComp plugin, FoMessageService messages, CompetitionManager manager) {
        this.plugin = plugin;
        this.messages = messages;
        this.manager = manager;
        this.actionKey = new NamespacedKey(plugin, "editor_action");
        this.competitionKey = new NamespacedKey(plugin, "editor_competition");
        this.rankKey = new NamespacedKey(plugin, "editor_rank");
        this.materialSelectors = new HashMap<>();
        this.worldSelectors = new HashMap<>();
    }

    public static boolean isEditorInventory(Inventory inventory) {
        if (inventory == null) {
            return false;
        }
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof EditorListHolder || holder instanceof CompetitionEditHolder;
    }

    public void openMainEditor(Player player) {
        EditorListHolder holder = new EditorListHolder();
        Inventory inventory = Bukkit.createInventory(holder, MAIN_EDITOR_SIZE, GuiTitles.format("FoMiningComp Editor"));
        holder.inventory = inventory;

        fillBackground(inventory, MAIN_EDITOR_ROWS);

        List<CompetitionConfig> competitions = new ArrayList<>(manager.competitions());
        competitions.sort(Comparator.comparing(CompetitionConfig::id));

        int slot = 10;
        for (CompetitionConfig competition : competitions) {
            if (slot >= 44) {
                break;
            }
            if (slot % 9 == 8) {
                slot += 2;
            }

            List<String> lore = List.of(
                "&fID: " + competition.id(),
                "&fStatus: " + (competition.enabled() ? FoText.color(FoStyle.GOOD + "enabled") : FoText.color(FoStyle.BAD + "disabled")),
                "&fDuration: " + competition.durationSeconds() + "s",
                "&fTracked blocks: " + competition.trackedMaterials().size(),
                "",
                "&fClick to edit"
            );

            ItemStack button = actionItem(
                competition.icon(),
                FoStyle.THEME + competition.displayName(),
                lore,
                "open_competition",
                competition.id(),
                0
            );
            inventory.setItem(slot, button);
            slot++;
        }

        inventory.setItem(
            48,
            actionItem(
                toggleMaterial(manager.broadcastWhatToMineEnabled()),
                FoStyle.THEME + "Broadcast What To Mine",
                List.of(
                    "&fGlobal setting",
                    "&fCurrent: " + (manager.broadcastWhatToMineEnabled() ? "enabled" : "disabled"),
                    "",
                    "&fClick to toggle."
                ),
                "toggle_broadcast_what_to_mine",
                "",
                0
            )
        );

        inventory.setItem(
            49,
            actionItem(
                Material.LIME_DYE,
                FoStyle.GOOD + "Create Competition",
                List.of("&fCreate a new competition entry.", "&fClick to enter a new ID."),
                "create_competition",
                "",
                0
            )
        );

        player.openInventory(inventory);
    }

    private void openCompetitionEditor(Player player, String id) {
        CompetitionConfig competition = manager.competition(id);
        if (competition == null) {
            messages.send(player, "messages.competition-not-found", "{prefix}{bad}Competition {theme}{id}{bad} was not found or is disabled.", Map.of("id", id));
            return;
        }

        CompetitionEditHolder holder = new CompetitionEditHolder(id);
        Inventory inventory = Bukkit.createInventory(holder, COMPETITION_EDITOR_SIZE, GuiTitles.format("Edit " + competition.id()));
        holder.inventory = inventory;

        fillBackground(inventory, COMPETITION_EDITOR_ROWS);

        inventory.setItem(
            11,
            actionItem(
                Material.NAME_TAG,
                FoStyle.THEME + "Display Name",
                List.of("&fCurrent: " + competition.displayName(), "", "&fClick to edit."),
                "prompt_name",
                competition.id(),
                0
            )
        );

        inventory.setItem(
            12,
            actionItem(
                competition.icon(),
                FoStyle.THEME + "Icon Material",
                List.of("&fCurrent: " + competition.icon().name(), "", "&fClick to edit."),
                "prompt_icon",
                competition.id(),
                0
            )
        );

        inventory.setItem(
            13,
            actionItem(
                Material.IRON_PICKAXE,
                FoStyle.THEME + "Tracked Materials",
                List.of(
                    "&fCurrent:",
                    "&f" + joinMaterials(competition.trackedMaterials()),
                    "",
                    "&fClick to configure blocks."
                ),
                "select_materials",
                competition.id(),
                0
            )
        );

        inventory.setItem(
            15,
            actionItem(
                Material.CLOCK,
                FoStyle.THEME + "Duration",
                List.of("&fCurrent: " + competition.durationSeconds() + " seconds", "", "&fClick to edit."),
                "prompt_duration",
                competition.id(),
                0
            )
        );

        inventory.setItem(
            14,
            actionItem(
                Material.COMPASS,
                FoStyle.THEME + "Disabled Worlds",
                List.of(
                    "&fCurrent:",
                    "&f" + joinWorlds(competition.disabledWorlds()),
                    "",
                    "&fClick to configure disabled worlds."
                ),
                "select_disabled_worlds",
                competition.id(),
                0
            )
        );

        inventory.setItem(
            10,
            actionItem(
                toggleMaterial(competition.enabled()),
                competition.enabled() ? FoStyle.GOOD + "Enabled" : FoStyle.BAD + "Disabled",
                List.of("&fToggle this competition on/off."),
                "toggle_enabled",
                competition.id(),
                0
            )
        );

        inventory.setItem(
            16,
            actionItem(
                toggleMaterial(competition.bossbarEnabled()),
                FoStyle.THEME + "BossBar",
                List.of("&fCurrent: " + (competition.bossbarEnabled() ? "enabled" : "disabled"), "&fClick to toggle."),
                "toggle_bossbar",
                competition.id(),
                0
            )
        );

        inventory.setItem(
            19,
            actionItem(
                toggleMaterial(competition.announceStart()),
                FoStyle.THEME + "Announce Start",
                List.of("&fCurrent: " + (competition.announceStart() ? "enabled" : "disabled"), "&fClick to toggle."),
                "toggle_announce_start",
                competition.id(),
                0
            )
        );

        inventory.setItem(
            20,
            actionItem(
                toggleMaterial(competition.announceEnd()),
                FoStyle.THEME + "Announce End",
                List.of("&fCurrent: " + (competition.announceEnd() ? "enabled" : "disabled"), "&fClick to toggle."),
                "toggle_announce_end",
                competition.id(),
                0
            )
        );

        for (int rank = 1; rank <= 5; rank++) {
            Material rewardMaterial = switch (rank) {
                case 1 -> Material.GOLD_BLOCK;
                case 2 -> Material.IRON_BLOCK;
                case 3 -> Material.COPPER_BLOCK;
                case 4 -> Material.EMERALD_BLOCK;
                default -> Material.LAPIS_BLOCK;
            };

            List<String> commands = competition.rewardCommands(rank);
            List<String> preview = new ArrayList<>();
            preview.add("&fCommands: " + (commands.isEmpty() ? "none" : String.valueOf(commands.size())));
            if (!commands.isEmpty()) {
                for (int i = 0; i < Math.min(3, commands.size()); i++) {
                    preview.add("&f- " + commands.get(i));
                }
                if (commands.size() > 3) {
                    preview.add("&f...");
                }
            }
            preview.add("");
            preview.add("&fUse '|' between commands.");

            inventory.setItem(
                20 + rank,
                actionItem(
                    rewardMaterial,
                    FoStyle.THEME + "Top " + rank + " Reward",
                    preview,
                    "prompt_reward",
                    competition.id(),
                    rank
                )
            );
        }

        inventory.setItem(
            GuiSlots.bottomMiddleSlot(COMPETITION_EDITOR_ROWS),
            actionItem(
                BUTTONS.back(),
                "back_to_list",
                competition.id(),
                0
            )
        );

        player.openInventory(inventory);
    }

    private void openTrackedMaterialsSelector(Player player, String competitionId) {
        CompetitionConfig competition = manager.competition(competitionId);
        if (competition == null) {
            messages.send(player, "messages.competition-not-found", "{prefix}{bad}Competition {theme}{id}{bad} was not found or is disabled.", Map.of("id", competitionId));
            return;
        }

        openTrackedMaterialsSelector(player, competition.id(), trackedMaterialsRequest(competition, 0, ""));
    }

    private void openTrackedMaterialsSelector(Player player, String competitionId, MaterialChooserRequest request) {
        CompetitionConfig competition = manager.competition(competitionId);
        if (competition == null) {
            messages.send(player, "messages.competition-not-found", "{prefix}{bad}Competition {theme}{id}{bad} was not found or is disabled.", Map.of("id", competitionId));
            return;
        }

        materialSelectors.put(player.getUniqueId(), competition.id());
        MaterialChooserMenus.open(player, request.withSelectedMaterials(competition.trackedMaterials()));
    }

    private MaterialChooserRequest trackedMaterialsRequest(CompetitionConfig competition, int page, String filter) {
        return MaterialChooserRequest.builder()
            .title("Tracked Blocks")
            .mode(MaterialChooserMode.MULTI_TOGGLE)
            .availableMaterials(MaterialTypes.allBlocks())
            .selectedMaterials(competition.trackedMaterials())
            .page(page)
            .filter(filter)
            .showBack(true)
            .showSearch(true)
            .buttons(BUTTONS)
            .extraLore(material -> List.of(FoStyle.WHITE + "Counts when broken."))
            .build();
    }

    private void openDisabledWorldSelector(Player player, String competitionId) {
        CompetitionConfig competition = manager.competition(competitionId);
        if (competition == null) {
            messages.send(player, "messages.competition-not-found", "{prefix}{bad}Competition {theme}{id}{bad} was not found or is disabled.", Map.of("id", competitionId));
            return;
        }

        openDisabledWorldSelector(player, competition.id(), disabledWorldsRequest(competition, 0, ""));
    }

    private void openDisabledWorldSelector(Player player, String competitionId, TriStateSelectionRequest request) {
        CompetitionConfig competition = manager.competition(competitionId);
        if (competition == null) {
            messages.send(player, "messages.competition-not-found", "{prefix}{bad}Competition {theme}{id}{bad} was not found or is disabled.", Map.of("id", competitionId));
            return;
        }

        worldSelectors.put(player.getUniqueId(), competition.id());
        TriStateSelectionMenus.open(player, request);
    }

    private TriStateSelectionRequest disabledWorldsRequest(CompetitionConfig competition, int page, String filter) {
        return TriStateSelectionRequest.builder()
            .worldSelection()
            .entries(WorldSelectionEntries.loadedAndConfigured(plugin, competition.disabledWorlds()))
            .states(TriStateSelections.fromEnabledDisabled(List.of(), competition.disabledWorlds()))
            .cycleOrder(List.of(TriStateSelectionState.NEUTRAL, TriStateSelectionState.DISABLED))
            .page(page)
            .filter(filter)
            .showBack(true)
            .showSearch(true)
            .buttons(BUTTONS)
            .neutralLabel("Allowed")
            .disabledLabel("Disabled")
            .clickHint("Click to allow or disable this world.")
            .emptyTitle("No Worlds")
            .emptyLore(List.of(FoStyle.WHITE + "No worlds are loaded or configured."))
            .build();
    }

    private TextDialogRequest selectorSearchRequest(String body, String currentFilter) {
        String filter = currentFilter == null ? "" : currentFilter;
        return new TextDialogRequest(
            "Search",
            List.of(body),
            "Search",
            filter,
            filter.isBlank() ? "type to filter" : filter,
            DialogButton.search("Search", "", DIALOG_BUTTON_WIDTH),
            DialogButton.cancel("Cancel", "", DIALOG_BUTTON_WIDTH),
            DIALOG_BODY_WIDTH,
            DIALOG_INPUT_WIDTH,
            64,
            true,
            true,
            false
        );
    }

    private void beginMaterialSearch(Player player, String competitionId, MaterialChooserRequest request) {
        EditorDialogInputs.openTextFromInventory(
            plugin,
            plugin.core().inventoryCloseSuppressor(),
            plugin.core().dialogService(),
            player,
            selectorSearchRequest("Enter a block filter.", request.filter()),
            filter -> openTrackedMaterialsSelector(player, competitionId, request.withFilter(filter)),
            () -> openTrackedMaterialsSelector(player, competitionId, request)
        );
    }

    private void beginWorldSearch(Player player, String competitionId, TriStateSelectionRequest request) {
        EditorDialogInputs.openTextFromInventory(
            plugin,
            plugin.core().inventoryCloseSuppressor(),
            plugin.core().dialogService(),
            player,
            selectorSearchRequest("Enter a world filter.", request.filter()),
            filter -> openDisabledWorldSelector(player, competitionId, request.withFilter(filter)),
            () -> openDisabledWorldSelector(player, competitionId, request)
        );
    }

    private void handleMaterialSelectorClick(Player player, int rawSlot, MaterialChooserHolder holder) {
        String competitionId = materialSelectors.get(player.getUniqueId());
        if (competitionId == null) {
            player.closeInventory();
            return;
        }

        MaterialChooserClick click = MaterialChooserMenus.handleClick(rawSlot, holder);
        MaterialChooserRequest request = holder.request();
        switch (click.action()) {
            case PREVIOUS_PAGE, NEXT_PAGE, CLEAR_SEARCH -> openTrackedMaterialsSelector(player, competitionId, click.nextRequest());
            case SEARCH -> beginMaterialSearch(player, competitionId, request);
            case BACK -> {
                materialSelectors.remove(player.getUniqueId());
                openCompetitionEditor(player, competitionId);
            }
            case TOGGLE -> {
                Set<Material> selected = MaterialSelections.toggled(request.selectedMaterials(), click.material());
                if (selected.isEmpty()) {
                    messages.send(player, "messages.invalid-value", "{prefix}{bad}Invalid value: {theme}{value}", Map.of("value", "at least one tracked block"));
                    openTrackedMaterialsSelector(player, competitionId, request);
                    return;
                }
                if (manager.setTrackedMaterials(competitionId, selected)) {
                    messages.send(player, "messages.edit-saved", "{prefix}{good}Saved changes for {theme}{id}{muted}.", Map.of("id", competitionId));
                    openTrackedMaterialsSelector(player, competitionId, request.withSelectedMaterials(selected));
                }
            }
            case NONE, SELECT -> {
            }
        }
    }

    private void handleWorldSelectorClick(Player player, int rawSlot, TriStateSelectionHolder holder) {
        String competitionId = worldSelectors.get(player.getUniqueId());
        if (competitionId == null) {
            player.closeInventory();
            return;
        }

        TriStateSelectionClick click = TriStateSelectionMenus.handleClick(rawSlot, holder);
        TriStateSelectionRequest request = holder.request();
        switch (click.action()) {
            case PREVIOUS_PAGE, NEXT_PAGE, CLEAR_SEARCH -> openDisabledWorldSelector(player, competitionId, click.nextRequest());
            case SEARCH -> beginWorldSearch(player, competitionId, request);
            case BACK -> {
                worldSelectors.remove(player.getUniqueId());
                openCompetitionEditor(player, competitionId);
            }
            case TOGGLE -> {
                Set<String> disabledWorlds = TriStateSelections.disabledKeys(click.nextRequest()).stream()
                    .map(world -> world.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toCollection(HashSet::new));
                if (manager.setDisabledWorlds(competitionId, disabledWorlds)) {
                    messages.send(player, "messages.edit-saved", "{prefix}{good}Saved changes for {theme}{id}{muted}.", Map.of("id", competitionId));
                    CompetitionConfig updated = manager.competition(competitionId);
                    openDisabledWorldSelector(player, competitionId, disabledWorldsRequest(updated, click.page(), click.filter()));
                }
            }
            case NONE -> {
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        materialSelectors.remove(uuid);
        worldSelectors.remove(uuid);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        InventoryHolder holder = top.getHolder();
        if (holder instanceof MaterialChooserHolder materialHolder) {
            event.setCancelled(true);
            handleMaterialSelectorClick(player, event.getRawSlot(), materialHolder);
            return;
        }
        if (holder instanceof TriStateSelectionHolder selectionHolder) {
            event.setCancelled(true);
            handleWorldSelectorClick(player, event.getRawSlot(), selectionHolder);
            return;
        }
        if (!(holder instanceof EditorListHolder) && !(holder instanceof CompetitionEditHolder)) {
            return;
        }

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) {
            return;
        }

        PersistentDataContainer data = meta.getPersistentDataContainer();
        String action = data.get(actionKey, PersistentDataType.STRING);
        String competitionId = data.get(competitionKey, PersistentDataType.STRING);
        Integer rewardRank = data.get(rankKey, PersistentDataType.INTEGER);

        if (action == null) {
            return;
        }

        switch (action) {
            case "open_competition" -> openCompetitionEditor(player, competitionId);
            case "create_competition" -> beginPrompt(
                player,
                new PromptState(PromptType.CREATE_COMPETITION, null, 0)
            );
            case "toggle_broadcast_what_to_mine" -> {
                manager.setBroadcastWhatToMineEnabled(!manager.broadcastWhatToMineEnabled());
                String state = manager.broadcastWhatToMineEnabled()
                    ? messages.render("messages.state-enabled", "{good}enabled")
                    : messages.render("messages.state-disabled", "{bad}disabled");
                messages.send(player, "messages.broadcast-what-to-mine-toggled", "{prefix}{good}Broadcast what-to-mine is now {state}", Map.of("state", state));
                openMainEditor(player);
            }
            case "back_to_list" -> openMainEditor(player);
            case "toggle_enabled" -> {
                CompetitionConfig competition = manager.competition(competitionId);
                if (competition != null) {
                    manager.setEnabled(competition.id(), !competition.enabled());
                    messages.send(player, "messages.edit-saved", "{prefix}{good}Saved changes for {theme}{id}{muted}.", Map.of("id", competition.id()));
                    openCompetitionEditor(player, competition.id());
                }
            }
            case "toggle_announce_start" -> {
                CompetitionConfig competition = manager.competition(competitionId);
                if (competition != null) {
                    manager.setAnnounceStart(competition.id(), !competition.announceStart());
                    messages.send(player, "messages.edit-saved", "{prefix}{good}Saved changes for {theme}{id}{muted}.", Map.of("id", competition.id()));
                    openCompetitionEditor(player, competition.id());
                }
            }
            case "toggle_announce_end" -> {
                CompetitionConfig competition = manager.competition(competitionId);
                if (competition != null) {
                    manager.setAnnounceEnd(competition.id(), !competition.announceEnd());
                    messages.send(player, "messages.edit-saved", "{prefix}{good}Saved changes for {theme}{id}{muted}.", Map.of("id", competition.id()));
                    openCompetitionEditor(player, competition.id());
                }
            }
            case "toggle_bossbar" -> {
                CompetitionConfig competition = manager.competition(competitionId);
                if (competition != null) {
                    manager.setBossbarEnabled(competition.id(), !competition.bossbarEnabled());
                    messages.send(player, "messages.edit-saved", "{prefix}{good}Saved changes for {theme}{id}{muted}.", Map.of("id", competition.id()));
                    openCompetitionEditor(player, competition.id());
                }
            }
            case "prompt_name" -> beginPrompt(
                player,
                new PromptState(PromptType.NAME, competitionId, 0)
            );
            case "prompt_icon" -> beginPrompt(
                player,
                new PromptState(PromptType.ICON, competitionId, 0)
            );
            case "select_materials" -> openTrackedMaterialsSelector(player, competitionId);
            case "prompt_duration" -> beginPrompt(
                player,
                new PromptState(PromptType.DURATION, competitionId, 0)
            );
            case "select_disabled_worlds" -> openDisabledWorldSelector(player, competitionId);
            case "prompt_reward" -> beginPrompt(
                player,
                new PromptState(PromptType.REWARD, competitionId, rewardRank == null ? 1 : rewardRank)
            );
            default -> {
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        Inventory top = event.getView().getTopInventory();
        if (!isEditorInventory(top)) {
            return;
        }

        plugin.core().inventoryCloseSuppressor().consumeSuppressedClose(player);
    }

    private void handlePrompt(Player player, PromptState state, String input, boolean reopenOnInvalid) {
        if (input.equalsIgnoreCase("cancel")) {
            messages.send(player, "messages.prompt-cancelled", "{prefix}{bad}Editor prompt cancelled.");
            reopenPromptOrigin(player, state);
            return;
        }

        boolean success;
        switch (state.type()) {
            case CREATE_COMPETITION -> {
                success = manager.createCompetition(input.trim());
                if (success) {
                    messages.send(player, "messages.edit-saved", "{prefix}{good}Saved changes for {theme}{id}{muted}.", Map.of("id", input.trim()));
                    openCompetitionEditor(player, input.trim());
                } else {
                    messages.send(player, "messages.invalid-value", "{prefix}{bad}Invalid value: {theme}{value}", Map.of("value", input));
                    if (reopenOnInvalid) {
                        reopenPromptOrigin(player, state);
                    }
                }
            }
            case NAME -> {
                success = manager.setDisplayName(state.competitionId(), input);
                finishEdit(player, state.competitionId(), success, input, reopenOnInvalid);
            }
            case ICON -> {
                Material material = MaterialTypes.match(input);
                success = material != null && manager.setIcon(state.competitionId(), material);
                finishEdit(player, state.competitionId(), success, input, reopenOnInvalid);
            }
            case DURATION -> {
                int value = parseDurationSeconds(input);
                success = value > 0 && manager.setDurationSeconds(state.competitionId(), value);
                finishEdit(player, state.competitionId(), success, input, reopenOnInvalid);
            }
            case REWARD -> {
                List<String> commands = parseRewardCommands(input);
                success = manager.setRewardCommands(state.competitionId(), state.rank(), commands);
                finishEdit(player, state.competitionId(), success, input, reopenOnInvalid);
            }
        }
    }

    private void finishEdit(Player player, String competitionId, boolean success, String originalInput, boolean reopenOnInvalid) {
        if (success) {
            messages.send(player, "messages.edit-saved", "{prefix}{good}Saved changes for {theme}{id}{muted}.", Map.of("id", competitionId));
            openCompetitionEditor(player, competitionId);
        } else {
            messages.send(player, "messages.invalid-value", "{prefix}{bad}Invalid value: {theme}{value}", Map.of("value", originalInput));
            if (reopenOnInvalid) {
                openCompetitionEditor(player, competitionId);
            }
        }
    }

    private void beginPrompt(Player player, PromptState state) {
        EditorDialogInputs.openTextFromInventory(
            plugin,
            plugin.core().inventoryCloseSuppressor(),
            plugin.core().dialogService(),
            player,
            inputRequest(state),
            input -> handlePrompt(player, state, input, true),
            () -> {
                messages.send(player, "messages.prompt-cancelled", "{prefix}{bad}Editor prompt cancelled.");
                reopenPromptOrigin(player, state);
            }
        );
    }

    private TextDialogRequest inputRequest(PromptState state) {
        CompetitionConfig competition = state.competitionId() == null ? null : manager.competition(state.competitionId());
        return switch (state.type()) {
            case CREATE_COMPETITION -> textRequest(
                "Create Competition",
                "Enter a new competition ID.",
                "Competition ID",
                "",
                "letters, numbers, underscores, and hyphens",
                64
            );
            case NAME -> textRequest(
                "Display Name",
                "Enter the display name shown in messages and menus.",
                "Display Name",
                competition == null ? "" : competition.displayName(),
                "plain text",
                128
            );
            case ICON -> textRequest(
                "Icon Material",
                "Enter a Bukkit material name for the menu icon.",
                "Material",
                competition == null ? "" : competition.icon().name(),
                "Bukkit material, for example DIAMOND_PICKAXE",
                48
            );
            case DURATION -> textRequest(
                "Duration",
                "Enter how long this competition lasts. Use seconds, 12m, 1h, or 1d.",
                "Duration",
                competition == null ? "" : String.valueOf(competition.durationSeconds()),
                "900, 12m, 1h",
                32
            );
            case REWARD -> TextDialogRequest.command(
                List.of("Enter console reward commands for top " + state.rank() + "."),
                competition == null ? "none" : joinRewardCommands(competition.rewardCommands(state.rank())),
                "commands separated with |, or none"
            );
        };
    }

    private TextDialogRequest textRequest(
        String title,
        String body,
        String fieldLabel,
        String currentValue,
        String placeholder,
        int maxLength
    ) {
        return new TextDialogRequest(
            title,
            List.of(body),
            fieldLabel,
            currentValue,
            placeholder,
            DialogButton.save("Save", "", DIALOG_BUTTON_WIDTH),
            DialogButton.cancel("Cancel", "", DIALOG_BUTTON_WIDTH),
            DIALOG_BODY_WIDTH,
            DIALOG_INPUT_WIDTH,
            maxLength,
            true,
            false,
            false
        );
    }

    private void reopenPromptOrigin(Player player, PromptState state) {
        if (state.competitionId() == null || state.type() == PromptType.CREATE_COMPETITION) {
            openMainEditor(player);
            return;
        }
        openCompetitionEditor(player, state.competitionId());
    }

    private void fillBackground(Inventory inventory, int rows) {
        ItemStack pane = EditorItemFactory.filler();

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            int column = slot % 9;
            if (slot < GuiSlots.ROW_SIZE || slot >= GuiSlots.bottomRowStart(rows) || column == 0 || column == 8) {
                inventory.setItem(slot, pane);
            }
        }
    }

    private Material toggleMaterial(boolean enabled) {
        return enabled ? Material.LIME_DYE : Material.RED_DYE;
    }

    private ItemStack actionItem(
        Material material,
        String name,
        List<String> loreLines,
        String action,
        String competitionId,
        int rank
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(FoText.color(name));
            List<String> lore = loreLines.stream()
                .map(line -> FoText.color(line.startsWith("#") || line.startsWith("&") || line.startsWith("§") ? line : FoStyle.WHITE + line))
                .collect(Collectors.toCollection(ArrayList::new));
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return actionItem(item, action, competitionId, rank);
    }

    private ItemStack actionItem(ItemStack item, String action, String competitionId, int rank) {
        ItemStack tagged = item.clone();
        ItemMeta meta = tagged.getItemMeta();
        if (meta != null) {
            PersistentDataContainer data = meta.getPersistentDataContainer();
            data.set(actionKey, PersistentDataType.STRING, action);
            data.set(competitionKey, PersistentDataType.STRING, competitionId == null ? "" : competitionId);
            data.set(rankKey, PersistentDataType.INTEGER, rank);
            tagged.setItemMeta(meta);
        }
        return tagged;
    }

    private String joinMaterials(Set<Material> materials) {
        return materials.stream().map(Material::name).sorted().collect(Collectors.joining(", "));
    }

    private String joinWorlds(Set<String> worlds) {
        if (worlds == null || worlds.isEmpty()) {
            return "none";
        }
        return worlds.stream().sorted().collect(Collectors.joining(", "));
    }

    private String joinRewardCommands(List<String> commands) {
        if (commands == null || commands.isEmpty()) {
            return "none";
        }
        return String.join(" | ", commands);
    }

    private List<String> parseRewardCommands(String raw) {
        if (raw.equalsIgnoreCase("none") || raw.isBlank()) {
            return new ArrayList<>();
        }

        String[] split = raw.split("[|;]");
        List<String> commands = new ArrayList<>();
        for (String command : split) {
            String cleaned = command.trim();
            if (!cleaned.isEmpty()) {
                commands.add(cleaned);
            }
        }
        return commands;
    }

    private int parseDurationSeconds(String raw) {
        String input = raw.trim().toLowerCase(Locale.ROOT);
        if (input.isEmpty()) {
            return -1;
        }

        char suffix = input.charAt(input.length() - 1);
        int multiplier = switch (suffix) {
            case 's' -> 1;
            case 'm' -> 60;
            case 'h' -> 60 * 60;
            case 'd' -> 24 * 60 * 60;
            default -> 0;
        };

        if (multiplier == 0) {
            return LargeNumberParser.parsePositiveInt(input).orElse(-1);
        }

        String amount = input.substring(0, input.length() - 1).trim();
        int value = LargeNumberParser.parsePositiveInt(amount).orElse(-1);
        if (value <= 0) {
            return -1;
        }

        long seconds = (long) value * multiplier;
        return seconds > Integer.MAX_VALUE ? -1 : (int) seconds;
    }

    private static final class EditorListHolder implements InventoryHolder {

        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class CompetitionEditHolder implements InventoryHolder {

        private final String competitionId;
        private Inventory inventory;

        private CompetitionEditHolder(String competitionId) {
            this.competitionId = competitionId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private record PromptState(PromptType type, String competitionId, int rank) {
    }

    private enum PromptType {
        CREATE_COMPETITION,
        NAME,
        ICON,
        DURATION,
        REWARD
    }
}
