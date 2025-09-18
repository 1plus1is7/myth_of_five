package me.j17e4eo.mythof5.hunter.data;

/**
 * Represents the lifecycle of a sealed artifact.
 */
public enum ArtifactState {
    STABLE,
    CRITICAL,
    BROKEN,
    SEALED,
    DESTROYED;

    public boolean isOperational() {
        return this == STABLE || this == CRITICAL || this == SEALED;
    }

    public static ArtifactState fromIntegrity(double integrity) {
        if (integrity >= 90.0) {
            return CRITICAL;
        }
        if (integrity <= 0.0) {
            return STABLE;
        }
        return STABLE;
    }
}
