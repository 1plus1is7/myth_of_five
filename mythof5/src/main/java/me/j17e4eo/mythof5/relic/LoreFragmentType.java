package me.j17e4eo.mythof5.relic;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Enumerates the collectible lore fragments used for advanced relic forging.
 */
public enum LoreFragmentType {
    EMBER("ember", "불씨 조각", NamedTextColor.GOLD),
    SHADOW("shadow", "그림자 조각", NamedTextColor.DARK_PURPLE),
    TIDE("tide", "조류 조각", NamedTextColor.AQUA),
    GALE("gale", "질풍 조각", NamedTextColor.GREEN),
    STONE("stone", "바위 조각", NamedTextColor.GRAY);

    private final String key;
    private final String displayName;
    private final NamedTextColor color;

    LoreFragmentType(String key, String displayName, NamedTextColor color) {
        this.key = key;
        this.displayName = displayName;
        this.color = color;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public NamedTextColor getColor() {
        return color;
    }

    public static LoreFragmentType fromKey(String token) {
        if (token == null) {
            return null;
        }
        String normalized = token.trim().toLowerCase();
        for (LoreFragmentType type : values()) {
            if (type.key.equals(normalized) || type.displayName.equalsIgnoreCase(token)) {
                return type;
            }
        }
        return null;
    }
}
