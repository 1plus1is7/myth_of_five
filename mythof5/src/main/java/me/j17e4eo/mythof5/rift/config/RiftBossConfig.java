package me.j17e4eo.mythof5.rift.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

public final class RiftBossConfig {
    private final EntityType type;
    private final String name;
    private final double baseHealth;
    private final double damageMultiplier;
    private final List<PotionEffect> potionEffects;

    public RiftBossConfig(EntityType type, String name, double baseHealth, double damageMultiplier, List<PotionEffect> potionEffects) {
        this.type = type;
        this.name = name;
        this.baseHealth = baseHealth;
        this.damageMultiplier = damageMultiplier;
        this.potionEffects = List.copyOf(potionEffects);
    }

    public EntityType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public double getBaseHealth() {
        return baseHealth;
    }

    public double getDamageMultiplier() {
        return damageMultiplier;
    }

    public List<PotionEffect> getPotionEffects() {
        return potionEffects;
    }

    public static RiftBossConfig fromSection(ConfigurationSection section) {
        EntityType type = EntityType.valueOf(section.getString("type", "ZOMBIE").toUpperCase());
        String name = section.getString("name", "균열 수호자");
        double baseHealth = section.getDouble("health", 200.0D);
        double damageMultiplier = section.getDouble("damage-multiplier", 1.0D);
        List<PotionEffect> effects = new ArrayList<>();
        for (Object obj : section.getMapList("potion-effects")) {
            if (!(obj instanceof java.util.Map<?, ?> map)) {
                continue;
            }
            Object typeObj = map.get("type");
            String effectName = typeObj != null ? typeObj.toString() : "INCREASE_DAMAGE";
            PotionEffectType potionType = PotionEffectType.getByName(effectName.toUpperCase());
            if (potionType == null) {
                continue;
            }
            Object amplifierObj = map.get("amplifier");
            int amplifier = amplifierObj instanceof Number number ? number.intValue() : amplifierObj != null ? Integer.parseInt(amplifierObj.toString()) : 0;
            Object durationObj = map.get("duration");
            int duration = durationObj instanceof Number number ? number.intValue() : durationObj != null ? Integer.parseInt(durationObj.toString()) : 20 * 60 * 5;
            effects.add(new PotionEffect(potionType, duration, amplifier, true, true));
        }
        return new RiftBossConfig(type, name, baseHealth, damageMultiplier, effects);
    }
}
