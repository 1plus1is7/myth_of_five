package me.j17e4eo.mythof5.rift.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

public final class RiftSpawnEntry {
    private final EntityType type;
    private final int amount;
    private final double radius;

    public RiftSpawnEntry(EntityType type, int amount, double radius) {
        this.type = type;
        this.amount = amount;
        this.radius = radius;
    }

    public EntityType getType() {
        return type;
    }

    public int getAmount() {
        return amount;
    }

    public double getRadius() {
        return radius;
    }

    public static RiftSpawnEntry fromSection(String key, ConfigurationSection section) {
        EntityType type = EntityType.valueOf(key.toUpperCase());
        int amount = Math.max(1, section.getInt("amount", section.getInt("count", 1)));
        double radius = Math.max(1.0D, section.getDouble("radius", 6.0D));
        return new RiftSpawnEntry(type, amount, radius);
    }
}
