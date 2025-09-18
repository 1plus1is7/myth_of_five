package me.j17e4eo.mythof5.hunter.data;

import java.util.Locale;

/**
 * Identifies the source myth for an artifact.
 */
public enum ArtifactOrigin {
    MYTHIC,
    LEGEND,
    PLAYER_LORE;

    public static ArtifactOrigin fromKey(String key) {
        if (key == null) {
            return LEGEND;
        }
        try {
            return ArtifactOrigin.valueOf(key.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return LEGEND;
        }
    }
}
