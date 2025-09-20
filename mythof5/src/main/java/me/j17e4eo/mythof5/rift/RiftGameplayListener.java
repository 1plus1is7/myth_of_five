package me.j17e4eo.mythof5.rift;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class RiftGameplayListener implements Listener {
    private final RiftManager manager;

    public RiftGameplayListener(RiftManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        if (manager.handleActivation(event.getPlayer(), block, event.getPlayer().hasPermission("mythof5.rift.force"))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Player player = null;
        if (damager instanceof Player direct) {
            player = direct;
        } else if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player shooter) {
                player = shooter;
            }
        }
        Entity target = event.getEntity();
        if (target.getPersistentDataContainer().has(manager.getMechanicKey())) {
            if (player != null) {
                event.setCancelled(true);
                manager.recordMechanic(player, target);
            }
            return;
        }
        if (player != null && target instanceof LivingEntity living) {
            manager.recordDamage(player, living, event.getFinalDamage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        manager.handleEntityDeath(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        ProjectileSource source = event.getPotion().getShooter();
        if (!(source instanceof Player player)) {
            return;
        }
        double amount = event.getAffectedEntities().stream()
                .filter(entity -> entity instanceof Player)
                .mapToDouble(event::getIntensity)
                .sum();
        if (amount > 0) {
            manager.recordSupport(player, amount * 5.0D);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAreaEffectCloud(AreaEffectCloudApplyEvent event) {
        ProjectileSource source = event.getEntity().getSource();
        if (!(source instanceof Player player)) {
            return;
        }
        if (!event.getAffectedEntities().isEmpty()) {
            manager.recordSupport(player, event.getAffectedEntities().size() * 3.0D);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        manager.handlePlayerQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        switch (event.getSpawnReason()) {
            case NATURAL, PATROL, CHUNK_GEN, REINFORCEMENTS -> {
                if (manager.shouldCancelSpawn(event.getLocation())) {
                    event.setCancelled(true);
                }
            }
            default -> {
            }
        }
    }
}
