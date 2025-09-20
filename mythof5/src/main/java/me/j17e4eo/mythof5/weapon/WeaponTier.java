package me.j17e4eo.mythof5.weapon;

import java.util.Locale;

/**
 * Represents the origin of a weapon. Regular (common) weapons are the
 * standard tools while hunter and goblin crafted variants unlock the
 * advanced skills and upgraded numerical values.
 */
public enum WeaponTier {

    COMMON("common"),
    HUNTER("hunter"),
    GOBLIN("goblin");

    private final String key;

    WeaponTier(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public boolean isAdvanced() {
        return this == HUNTER || this == GOBLIN;
    }

    public static WeaponTier fromKey(String key) {
        if (key == null) {
            return null;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        for (WeaponTier value : values()) {
            if (value.key.equals(normalized)) {
                return value;
            }
        }
        return null;
    }
}

