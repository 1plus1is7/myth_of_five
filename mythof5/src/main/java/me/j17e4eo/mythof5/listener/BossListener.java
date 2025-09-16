package me.j17e4eo.mythof5.listener;

import me.j17e4eo.mythof5.Mythof5;
import me.j17e4eo.mythof5.boss.BossManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;

public class BossListener implements Listener {

    private final Mythof5 plugin;
    private final BossManager bossManager;

    public BossListener(Mythof5 plugin, BossManager bossManager) {
        this.plugin = plugin;
        this.bossManager = bossManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossDamaged(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> bossManager.updateProgress(living));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossHeal(EntityRegainHealthEvent event) {
        LivingEntity entity = (LivingEntity) event.getEntity();
        plugin.getServer().getScheduler().runTask(plugin, () -> bossManager.updateProgress(entity));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBossDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        bossManager.handleBossDeath(entity, killer);
    }
}
