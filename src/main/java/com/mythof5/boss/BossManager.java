package com.mythof5.boss;

import com.mythof5.MythPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class BossManager {
    private final MythPlugin plugin;

    /**
     * Stores all active bosses. Each boss has an id, entity and bossbar.
     */
    public static class BossInfo {
        public final LivingEntity entity;
        public final BossBar bar;

        public BossInfo(LivingEntity entity, BossBar bar) {
            this.entity = entity;
            this.bar = bar;
        }
    }

    private final Map<Integer, BossInfo> bosses = new HashMap<>();
    private final AtomicInteger ids = new AtomicInteger();

    public BossManager(MythPlugin plugin) {
        this.plugin = plugin;
    }

    public int spawnBoss(String name, double hp, double armor, World world, double x, double y, double z) {
        LivingEntity entity = (LivingEntity) world.spawnEntity(new org.bukkit.Location(world, x, y, z), EntityType.ZOMBIE);
        entity.setCustomName(name);
        entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(hp);
        entity.setHealth(hp);
        entity.getAttribute(Attribute.GENERIC_ARMOR).setBaseValue(armor);
        entity.setRemoveWhenFarAway(false);

        BossBar bar = Bukkit.createBossBar(name, BarColor.RED, BarStyle.SOLID);
        bar.setProgress(1.0);
        for (Player p : Bukkit.getOnlinePlayers()) {
            bar.addPlayer(p);
        }

        int id = ids.incrementAndGet();
        bosses.put(id, new BossInfo(entity, bar));
        Bukkit.broadcastMessage("[방송] 태초의 도깨비가 나타났다!");
        plugin.getLogger().info("BOSS_SPAWNED id=" + id);
        return id;
    }

    public Map<Integer, BossInfo> getBosses() {
        return bosses;
    }

    public boolean endBoss(int id) {
        BossInfo info = bosses.remove(id);
        if (info != null) {
            info.bar.removeAll();
            info.entity.remove();
            plugin.getLogger().info("BOSS_DEFEATED id=" + id);
            return true;
        }
        return false;
    }

    public Integer getBossId(Entity entity) {
        for (Map.Entry<Integer, BossInfo> entry : bosses.entrySet()) {
            if (entry.getValue().entity.getUniqueId().equals(entity.getUniqueId())) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Update the bossbar progress for the given entity.
     */
    public void updateBossBar(LivingEntity entity) {
        Integer id = getBossId(entity);
        if (id != null) {
            BossInfo info = bosses.get(id);
            double max = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            info.bar.setProgress(Math.max(0, entity.getHealth() / max));
        }
    }

    public void addPlayer(Player player) {
        for (BossInfo info : bosses.values()) {
            info.bar.addPlayer(player);
        }
    }

    public void clearBosses() {
        for (BossInfo info : bosses.values()) {
            info.bar.removeAll();
            info.entity.remove();
        }
        bosses.clear();
    }
}
