package me.j17e4eo.mythof5.rift.config;

import java.util.List;

public final class RiftWave {
    private final int delaySeconds;
    private final List<RiftSpawnEntry> spawns;
    private final int mechanicCrystals;

    public RiftWave(int delaySeconds, List<RiftSpawnEntry> spawns, int mechanicCrystals) {
        this.delaySeconds = delaySeconds;
        this.spawns = List.copyOf(spawns);
        this.mechanicCrystals = mechanicCrystals;
    }

    public int getDelaySeconds() {
        return delaySeconds;
    }

    public List<RiftSpawnEntry> getSpawns() {
        return spawns;
    }

    public int getMechanicCrystals() {
        return mechanicCrystals;
    }
}
