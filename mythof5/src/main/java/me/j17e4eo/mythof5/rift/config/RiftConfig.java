package me.j17e4eo.mythof5.rift.config;

import org.bukkit.Material;
import org.bukkit.Sound;

import java.time.DayOfWeek;
import java.util.List;

public final class RiftConfig {
    private final int chunkRadius;
    private final int collapseSeconds;
    private final int cooldownSeconds;
    private final int maxActiveInstances;
    private final int awakeningSeconds;
    private final int evictionSeconds;
    private final Material activationBlock;
    private final Sound activationSound;
    private final Sound collapseSound;
    private final boolean protectBlocks;
    private final boolean protectPlacement;
    private final boolean protectPvp;
    private final boolean protectFall;
    private final boolean suppressNaturalSpawns;
    private final String compassDisplayName;
    private final List<String> compassLore;
    private final int compassUpdateSeconds;
    private final DayOfWeek weeklyResetDay;

    public RiftConfig(int chunkRadius, int collapseSeconds, int cooldownSeconds, int maxActiveInstances,
                      int awakeningSeconds, int evictionSeconds, Material activationBlock, Sound activationSound, Sound collapseSound,
                      boolean protectBlocks, boolean protectPlacement, boolean protectPvp, boolean protectFall,
                      boolean suppressNaturalSpawns, String compassDisplayName, List<String> compassLore,
                      int compassUpdateSeconds, DayOfWeek weeklyResetDay) {
        this.chunkRadius = chunkRadius;
        this.collapseSeconds = collapseSeconds;
        this.cooldownSeconds = cooldownSeconds;
        this.maxActiveInstances = maxActiveInstances;
        this.awakeningSeconds = awakeningSeconds;
        this.evictionSeconds = evictionSeconds;
        this.activationBlock = activationBlock;
        this.activationSound = activationSound;
        this.collapseSound = collapseSound;
        this.protectBlocks = protectBlocks;
        this.protectPlacement = protectPlacement;
        this.protectPvp = protectPvp;
        this.protectFall = protectFall;
        this.suppressNaturalSpawns = suppressNaturalSpawns;
        this.compassDisplayName = compassDisplayName;
        this.compassLore = List.copyOf(compassLore);
        this.compassUpdateSeconds = compassUpdateSeconds;
        this.weeklyResetDay = weeklyResetDay;
    }

    public int getChunkRadius() {
        return chunkRadius;
    }

    public int getCollapseSeconds() {
        return collapseSeconds;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public int getMaxActiveInstances() {
        return maxActiveInstances;
    }

    public int getAwakeningSeconds() {
        return awakeningSeconds;
    }

    public int getEvictionSeconds() {
        return evictionSeconds;
    }

    public Material getActivationBlock() {
        return activationBlock;
    }

    public Sound getActivationSound() {
        return activationSound;
    }

    public Sound getCollapseSound() {
        return collapseSound;
    }

    public boolean isProtectBlocks() {
        return protectBlocks;
    }

    public boolean isProtectPlacement() {
        return protectPlacement;
    }

    public boolean isProtectPvp() {
        return protectPvp;
    }

    public boolean isProtectFall() {
        return protectFall;
    }

    public boolean isSuppressNaturalSpawns() {
        return suppressNaturalSpawns;
    }

    public String getCompassDisplayName() {
        return compassDisplayName;
    }

    public List<String> getCompassLore() {
        return compassLore;
    }

    public int getCompassUpdateSeconds() {
        return compassUpdateSeconds;
    }

    public DayOfWeek getWeeklyResetDay() {
        return weeklyResetDay;
    }
}
