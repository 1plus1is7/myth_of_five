package me.j17e4eo.mythof5.rift;

import me.j17e4eo.mythof5.rift.config.RiftTheme;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class RiftSite {
    private final String id;
    private final UUID worldId;
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final String themeKey;
    private RiftTheme theme;
    private long cooldownUntil;
    private RiftInstance activeInstance;

    public RiftSite(String id, World world, double x, double y, double z, RiftTheme theme) {
        this(id, world.getUID(), world.getName(), x, y, z, theme.getKey(), theme, 0L);
    }

    public RiftSite(String id, UUID worldId, String worldName, double x, double y, double z,
                    String themeKey, RiftTheme theme, long cooldownUntil) {
        this.id = id;
        this.worldId = worldId;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.themeKey = themeKey;
        this.theme = theme;
        this.cooldownUntil = cooldownUntil;
    }

    public String getId() {
        return id;
    }

    public UUID getWorldId() {
        return worldId;
    }

    public String getWorldName() {
        return worldName;
    }

    public Location getLocation() {
        World world = Bukkit.getWorld(worldId);
        if (world == null) {
            world = Bukkit.getWorld(worldName);
        }
        if (world == null) {
            throw new IllegalStateException("World for rift site " + id + " is not loaded");
        }
        return new Location(world, x, y, z);
    }

    public int getChunkX() {
        return (int) Math.floor(x) >> 4;
    }

    public int getChunkZ() {
        return (int) Math.floor(z) >> 4;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public boolean isInside(Location location, int chunkRadius) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!Objects.equals(location.getWorld().getUID(), worldId) && !Objects.equals(location.getWorld().getName(), worldName)) {
            return false;
        }
        Chunk chunk = location.getChunk();
        return Math.abs(chunk.getX() - getChunkX()) <= chunkRadius && Math.abs(chunk.getZ() - getChunkZ()) <= chunkRadius;
    }

    public boolean isCoolingDown(long now) {
        return now < cooldownUntil;
    }

    public long getCooldownRemaining(long now) {
        return Math.max(0L, cooldownUntil - now);
    }

    public void setCooldownUntil(long cooldownUntil) {
        this.cooldownUntil = cooldownUntil;
    }

    public long getCooldownUntil() {
        return cooldownUntil;
    }

    public RiftTheme getTheme() {
        return theme;
    }

    public void setTheme(RiftTheme theme) {
        this.theme = theme;
    }

    public String getThemeKey() {
        return themeKey;
    }

    public Optional<RiftInstance> getActiveInstance() {
        return Optional.ofNullable(activeInstance);
    }

    public void setActiveInstance(RiftInstance activeInstance) {
        this.activeInstance = activeInstance;
    }
}
