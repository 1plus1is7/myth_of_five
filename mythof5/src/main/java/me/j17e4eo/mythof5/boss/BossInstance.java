package me.j17e4eo.mythof5.boss;

import org.bukkit.ChatColor;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class BossInstance {

    private final int id;
    private final LivingEntity entity;
    private final BossBar bossBar;
    private final String displayName;
    private final EntityType entityType;
    private final double maxHealth;

    public BossInstance(int id, LivingEntity entity, BossBar bossBar, String displayName, EntityType entityType, double maxHealth) {
        this.id = id;
        this.entity = entity;
        this.bossBar = bossBar;
        this.displayName = displayName;
        this.entityType = entityType;
        this.maxHealth = maxHealth;
    }

    public int getId() {
        return id;
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public String getDisplayName() {
        return displayName;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public double getMaxHealth() {
        return maxHealth;
    }

    public void addViewer(Player player) {
        bossBar.addPlayer(player);
    }

    public void removeViewer(Player player) {
        bossBar.removePlayer(player);
    }

    public void updateProgress() {
        if (!entity.isValid()) {
            return;
        }
        double health = Math.max(0.0D, Math.min(entity.getHealth(), maxHealth));
        double progress = maxHealth <= 0 ? 0 : Math.max(0.0D, Math.min(1.0D, health / maxHealth));
        bossBar.setProgress(progress);
        String title = ChatColor.DARK_RED + displayName + ChatColor.GRAY + " " + String.format("%.0f/%.0f", health, maxHealth);
        bossBar.setTitle(title);
    }

    public void remove() {
        bossBar.removeAll();
    }
}
