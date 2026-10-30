package me.foesio.foMiningComp.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.bukkit.Material;

/** Immutable, currently loaded competition settings. Changes are persisted by the editor. */
public record CompetitionDefinition(String id, String displayName, Material icon, boolean enabled,
                                    int durationSeconds, Set<Material> trackedMaterials, Set<String> disabledWorlds,
                                    boolean announceStart, boolean announceEnd, boolean bossbarEnabled,
                                    Map<Integer, List<String>> rewardCommands) {
    public CompetitionDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(icon, "icon");
        trackedMaterials = Set.copyOf(trackedMaterials);
        disabledWorlds = Set.copyOf(disabledWorlds);
        Map<Integer, List<String>> copiedRewards = new LinkedHashMap<>();
        rewardCommands.forEach((rank, commands) -> copiedRewards.put(rank, List.copyOf(commands)));
        rewardCommands = Map.copyOf(copiedRewards);
    }
}
