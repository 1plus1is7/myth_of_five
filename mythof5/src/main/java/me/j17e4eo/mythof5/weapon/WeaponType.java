package me.j17e4eo.mythof5.weapon;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Enumeration describing the supported weapon archetypes that can
 * receive custom behaviour. Each type keeps track of the vanilla
 * materials that should automatically opt-in to the behaviour when
 * the persistent weapon tag is missing.
 */
public enum WeaponType {

    SWORD("sword", EnumSet.of(
            Material.WOODEN_SWORD,
            Material.STONE_SWORD,
            Material.IRON_SWORD,
            Material.GOLDEN_SWORD,
            Material.DIAMOND_SWORD,
            Material.NETHERITE_SWORD
    )),

    AXE("axe", EnumSet.of(
            Material.WOODEN_AXE,
            Material.STONE_AXE,
            Material.IRON_AXE,
            Material.GOLDEN_AXE,
            Material.DIAMOND_AXE,
            Material.NETHERITE_AXE
    )),

    TRIDENT("trident", EnumSet.of(Material.TRIDENT)),

    BOW("bow", EnumSet.of(Material.BOW));

    private final String key;
    private final Set<Material> materials;

    WeaponType(String key, Set<Material> materials) {
        this.key = key;
        this.materials = materials;
    }

    public String getKey() {
        return key;
    }

    public Set<Material> getMaterials() {
        return materials;
    }

    public boolean supports(Material material) {
        return materials.contains(material);
    }

    public static WeaponType detect(Material material) {
        for (WeaponType value : values()) {
            if (value.supports(material)) {
                return value;
            }
        }
        return null;
    }

    public static WeaponType fromKey(String key) {
        if (key == null) {
            return null;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        for (WeaponType value : values()) {
            if (value.key.equals(normalized)) {
                return value;
            }
        }
        return null;
    }
}

