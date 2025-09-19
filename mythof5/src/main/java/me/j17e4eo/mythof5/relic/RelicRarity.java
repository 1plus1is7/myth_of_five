package me.j17e4eo.mythof5.relic;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Rarity tier for relics.
 */
public enum RelicRarity {
    COMMON("일반", NamedTextColor.WHITE),
    RARE("희귀", NamedTextColor.BLUE),
    LEGENDARY("전설", NamedTextColor.GOLD),
    MYTHIC("신화", NamedTextColor.LIGHT_PURPLE);

    private final String displayName;
    private final NamedTextColor color;

    RelicRarity(String displayName, NamedTextColor color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public NamedTextColor getColor() {
        return color;
    }
}
