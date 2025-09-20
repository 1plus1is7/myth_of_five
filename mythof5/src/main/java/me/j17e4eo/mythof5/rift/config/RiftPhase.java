package me.j17e4eo.mythof5.rift.config;

import java.util.List;

public final class RiftPhase {
    private final String name;
    private final int durationSeconds;
    private final List<RiftWave> waves;

    public RiftPhase(String name, int durationSeconds, List<RiftWave> waves) {
        this.name = name;
        this.durationSeconds = durationSeconds;
        this.waves = List.copyOf(waves);
    }

    public String getName() {
        return name;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public List<RiftWave> getWaves() {
        return waves;
    }
}
