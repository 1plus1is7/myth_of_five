package me.j17e4eo.mythof5.rift.config;

import org.bukkit.boss.BarColor;

import java.util.Collections;
import java.util.Map;

public final class RiftTheme {
    private final String key;
    private final String displayName;
    private final BarColor barColor;
    private final double dangerLevel;
    private final int spawnRadius;
    private final int awakeningSeconds;
    private final RiftPhase phaseOne;
    private final RiftPhase phaseTwo;
    private final RiftBossConfig bossConfig;
    private final RiftRewardTable rewardTable;
    private final Map<String, String> palette;
    private final RiftFlavor flavor;

    public RiftTheme(String key, String displayName, BarColor barColor, double dangerLevel, int spawnRadius,
                     int awakeningSeconds, RiftPhase phaseOne, RiftPhase phaseTwo, RiftBossConfig bossConfig,
                     RiftRewardTable rewardTable, Map<String, String> palette, RiftFlavor flavor) {
        this.key = key;
        this.displayName = displayName;
        this.barColor = barColor;
        this.dangerLevel = dangerLevel;
        this.spawnRadius = spawnRadius;
        this.awakeningSeconds = awakeningSeconds;
        this.phaseOne = phaseOne;
        this.phaseTwo = phaseTwo;
        this.bossConfig = bossConfig;
        this.rewardTable = rewardTable;
        this.palette = palette == null ? Collections.emptyMap() : Map.copyOf(palette);
        this.flavor = flavor == null ? RiftFlavor.empty() : flavor;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public BarColor getBarColor() {
        return barColor;
    }

    public double getDangerLevel() {
        return dangerLevel;
    }

    public int getSpawnRadius() {
        return spawnRadius;
    }

    public int getAwakeningSeconds() {
        return awakeningSeconds;
    }

    public RiftPhase getPhaseOne() {
        return phaseOne;
    }

    public RiftPhase getPhaseTwo() {
        return phaseTwo;
    }

    public RiftBossConfig getBossConfig() {
        return bossConfig;
    }

    public RiftRewardTable getRewardTable() {
        return rewardTable;
    }

    public Map<String, String> getPalette() {
        return palette;
    }

    public RiftFlavor getFlavor() {
        return flavor;
    }

    public org.bukkit.Material resolvePaletteMaterial(String key, org.bukkit.Material fallback) {
        String value = palette.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        org.bukkit.Material material = org.bukkit.Material.matchMaterial(value, false);
        return material != null ? material : fallback;
    }
}
