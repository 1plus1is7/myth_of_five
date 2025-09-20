package me.j17e4eo.mythof5.rift;

public enum RiftInstanceState {
    DORMANT,
    AWAKENING,
    PHASE_ONE,
    PHASE_TWO,
    BOSS,
    COLLAPSE,
    COOLDOWN;

    public boolean isRunning() {
        return this == AWAKENING || this == PHASE_ONE || this == PHASE_TWO || this == BOSS || this == COLLAPSE;
    }

    public boolean allowsRewards() {
        return this == COLLAPSE || this == COOLDOWN;
    }
}
