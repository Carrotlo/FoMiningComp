package me.foesio.foMiningComp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;

public final class CompetitionConfig {

    private final String id;
    private String displayName;
    private Material icon;
    private Set<Material> trackedMaterials;
    private Set<String> disabledWorlds;
    private int durationSeconds;
    private boolean enabled;
    private boolean announceStart;
    private boolean announceEnd;
    private boolean bossbarEnabled;
    private final Map<Integer, List<String>> rewardsByRank;

    public CompetitionConfig(String id) {
        this.id = id;
        this.displayName = id;
        this.icon = Material.DIAMOND_PICKAXE;
        this.trackedMaterials = EnumSet.of(Material.STONE);
        this.disabledWorlds = new HashSet<>();
        this.durationSeconds = 900;
        this.enabled = true;
        this.announceStart = true;
        this.announceEnd = true;
        this.bossbarEnabled = true;
        this.rewardsByRank = new HashMap<>();
        for (int rank = 1; rank <= 5; rank++) {
            this.rewardsByRank.put(rank, new ArrayList<>());
        }
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Material icon() {
        return icon;
    }

    public void setIcon(Material icon) {
        this.icon = icon;
    }

    public Set<Material> trackedMaterials() {
        return trackedMaterials;
    }

    public void setTrackedMaterials(Set<Material> trackedMaterials) {
        this.trackedMaterials = trackedMaterials;
    }

    public Set<String> disabledWorlds() {
        return disabledWorlds;
    }

    public void setDisabledWorlds(Set<String> disabledWorlds) {
        this.disabledWorlds = disabledWorlds;
    }

    public int durationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean announceStart() {
        return announceStart;
    }

    public void setAnnounceStart(boolean announceStart) {
        this.announceStart = announceStart;
    }

    public boolean announceEnd() {
        return announceEnd;
    }

    public void setAnnounceEnd(boolean announceEnd) {
        this.announceEnd = announceEnd;
    }

    public boolean bossbarEnabled() {
        return bossbarEnabled;
    }

    public void setBossbarEnabled(boolean bossbarEnabled) {
        this.bossbarEnabled = bossbarEnabled;
    }

    public List<String> rewardCommands(int rank) {
        return rewardsByRank.getOrDefault(rank, Collections.emptyList());
    }

    public void setRewardCommands(int rank, List<String> commands) {
        rewardsByRank.put(rank, commands);
    }
}
