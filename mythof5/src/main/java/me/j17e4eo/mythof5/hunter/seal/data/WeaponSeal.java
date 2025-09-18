package me.j17e4eo.mythof5.hunter.seal.data;

import me.j17e4eo.mythof5.inherit.aspect.GoblinAspect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent description of a sealed weapon.
 */
public class WeaponSeal {

    private final UUID weaponId;
    private final UUID ownerId;
    private final GoblinAspect aspect;
    private final int powerTier;
    private final long createdAt;
    private final int charges;
    private final String originalName;
    private final List<String> originalLore;

    public WeaponSeal(UUID weaponId, UUID ownerId, GoblinAspect aspect, int powerTier,
                      long createdAt, int charges, Component originalName, List<Component> originalLore) {
        this.weaponId = weaponId;
        this.ownerId = ownerId;
        this.aspect = aspect;
        this.powerTier = powerTier;
        this.createdAt = createdAt;
        this.charges = charges;
        this.originalName = originalName != null ? GsonComponentSerializer.gson().serialize(originalName) : null;
        if (originalLore == null || originalLore.isEmpty()) {
            this.originalLore = List.of();
        } else {
            List<String> lore = new ArrayList<>(originalLore.size());
            for (Component component : originalLore) {
                lore.add(GsonComponentSerializer.gson().serialize(component));
            }
            this.originalLore = List.copyOf(lore);
        }
    }

    private WeaponSeal(UUID weaponId, UUID ownerId, GoblinAspect aspect, int powerTier,
                       long createdAt, int charges, String originalName, List<String> originalLore) {
        this.weaponId = weaponId;
        this.ownerId = ownerId;
        this.aspect = aspect;
        this.powerTier = powerTier;
        this.createdAt = createdAt;
        this.charges = charges;
        this.originalName = originalName;
        this.originalLore = originalLore == null ? List.of() : List.copyOf(originalLore);
    }

    public UUID getWeaponId() {
        return weaponId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public GoblinAspect getAspect() {
        return aspect;
    }

    public int getPowerTier() {
        return powerTier;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public int getCharges() {
        return charges;
    }

    public Component getOriginalName() {
        return originalName == null ? null : GsonComponentSerializer.gson().deserialize(originalName);
    }

    public List<Component> getOriginalLore() {
        if (originalLore.isEmpty()) {
            return List.of();
        }
        List<Component> components = new ArrayList<>(originalLore.size());
        for (String entry : originalLore) {
            components.add(GsonComponentSerializer.gson().deserialize(entry));
        }
        return components;
    }

    public Map<String, Object> serialize() {
        return Map.of(
                "weapon", weaponId.toString(),
                "owner", ownerId.toString(),
                "aspect", aspect.name(),
                "tier", powerTier,
                "created", createdAt,
                "charges", charges,
                "name", originalName,
                "lore", originalLore
        );
    }

    public static WeaponSeal deserialize(Map<?, ?> raw) {
        try {
            UUID weapon = UUID.fromString(String.valueOf(raw.get("weapon")));
            UUID owner = UUID.fromString(String.valueOf(raw.get("owner")));
            GoblinAspect aspect = GoblinAspect.valueOf(String.valueOf(raw.get("aspect")));
            Object tierRaw = raw.get("tier");
            int tier = tierRaw != null ? Integer.parseInt(String.valueOf(tierRaw)) : 1;
            Object createdRaw = raw.get("created");
            long created = createdRaw != null ? Long.parseLong(String.valueOf(createdRaw)) : Instant.now().toEpochMilli();
            Object chargesRaw = raw.get("charges");
            int charges = chargesRaw != null ? Integer.parseInt(String.valueOf(chargesRaw)) : 0;
            Object nameRaw = raw.get("name");
            String name = nameRaw != null && !String.valueOf(nameRaw).isBlank() ? String.valueOf(nameRaw) : null;
            Object loreRaw = raw.get("lore");
            List<String> lore = new ArrayList<>();
            if (loreRaw instanceof List<?> list) {
                for (Object entry : list) {
                    if (entry != null) {
                        lore.add(String.valueOf(entry));
                    }
                }
            }
            return new WeaponSeal(weapon, owner, aspect, tier, created, charges, name, lore);
        } catch (Exception ex) {
            return null;
        }
    }

    public String describe() {
        return String.format(Locale.KOREA, "%s Tier %d (%s)", aspect.getDisplayName(), powerTier, weaponId);
    }
}
