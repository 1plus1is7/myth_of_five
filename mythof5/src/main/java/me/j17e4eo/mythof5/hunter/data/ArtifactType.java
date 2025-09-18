package me.j17e4eo.mythof5.hunter.data;

import java.util.Locale;

/**
 * High-level classification for artifacts.
 */
public enum ArtifactType {
    WEAPON,
    ACCESSORY,
    TOOL,
    BORROWED;

    public static ArtifactType fromKey(String key) {
        if (key == null) {
            return TOOL;
        }
        try {
            return ArtifactType.valueOf(key.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return TOOL;
        }
    }
}
