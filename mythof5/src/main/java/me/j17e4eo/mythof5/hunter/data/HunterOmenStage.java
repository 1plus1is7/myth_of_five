package me.j17e4eo.mythof5.hunter.data;

/**
 * Tracks hunter-specific omen intensities.
 */
public enum HunterOmenStage {
    CALM,
    LONG,
    MEDIUM,
    LATE;

    public HunterOmenStage next() {
        return switch (this) {
            case CALM -> LONG;
            case LONG -> MEDIUM;
            case MEDIUM -> LATE;
            case LATE -> LATE;
        };
    }
}
