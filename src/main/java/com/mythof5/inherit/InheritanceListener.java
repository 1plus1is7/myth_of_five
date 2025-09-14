package com.mythof5.inherit;

import com.mythof5.MythPlugin;
import com.mythof5.boss.BossManager;
import com.mythof5.squad.SquadManager;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class InheritanceListener implements Listener {
    private final BossManager bossManager;
    private final SquadManager squadManager;
    private final MythPlugin plugin = MythPlugin.getInstance();
    private final String powerKey;
    private UUID inheritor;
    private final List<PotionEffect> effects = new ArrayList<>();

    public InheritanceListener(BossManager bossManager, SquadManager squadManager) {
        this.bossManager = bossManager;
        this.squadManager = squadManager;
        FileConfiguration cfg = plugin.getConfiguration();
        powerKey = cfg.getString("inherit.power_key");
        if (cfg.contains("inherit.current")) {
            inheritor = UUID.fromString(cfg.getString("inherit.current"));
        }
        loadEffects();
        if (inheritor != null) {
            Player p = Bukkit.getPlayer(inheritor);
            if (p != null && p.isOnline()) {
                applyEffects(p);
            }
        }
    }

    private void loadEffects() {
        FileConfiguration cfg = plugin.getConfiguration();
        for (String s : cfg.getStringList("inherit.buffs")) {
            String[] split = s.split(":");
            PotionEffectType type = PotionEffectType.getByName(split[0].toUpperCase());
            int level = Integer.parseInt(split[1]);
            effects.add(new PotionEffect(type, Integer.MAX_VALUE, level - 1));
        }
    }

    private void applyEffects(Player player) {
        for (PotionEffect effect : effects) {
            player.addPotionEffect(effect);
        }
        player.addScoreboardTag("myth_inheritor");
        player.getPersistentDataContainer().set(new NamespacedKey(plugin, "powerKey"), PersistentDataType.STRING, powerKey);
    }

    private void clearEffects(Player player) {
        for (PotionEffect effect : effects) {
            player.removePotionEffect(effect.getType());
        }
        player.removeScoreboardTag("myth_inheritor");
        player.getPersistentDataContainer().remove(new NamespacedKey(plugin, "powerKey"));
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        Integer id = bossManager.getBossId(event.getEntity());
        if (id != null) {
            Player killer = event.getEntity().getKiller();
            bossManager.endBoss(id);
            if (killer != null) {
                inheritor = killer.getUniqueId();
                applyEffects(killer);
                plugin.getConfiguration().set("inherit.current", inheritor.toString());
                plugin.saveConfig();
                plugin.getLogger().info("MYTH_INHERITED player=" + killer.getName());
                if (plugin.getConfiguration().getBoolean("inherit.announce", true)) {
                    Bukkit.broadcastMessage("[방송] " + killer.getName() + "가 태초의 도깨비를 쓰러뜨리고 힘을 계승했다!");
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (inheritor != null && player.getUniqueId().equals(inheritor)) {
            clearEffects(player);
            inheritor = null;
            plugin.getConfiguration().set("inherit.current", null);
            plugin.saveConfig();
            plugin.getLogger().info("MYTH_LOST player=" + player.getName());
            if (plugin.getConfiguration().getBoolean("inherit.announce", true)) {
                Bukkit.broadcastMessage("[방송] " + player.getName() + "가 쓰러져 도깨비의 힘이 사라졌다.");
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        bossManager.addPlayer(player);
        if (inheritor != null && player.getUniqueId().equals(inheritor)) {
            applyEffects(player);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();
        if (damager instanceof Player && victim instanceof Player) {
            Player p1 = (Player) damager;
            Player p2 = (Player) victim;
            if (!MythPlugin.getInstance().getConfiguration().getBoolean("squad.friendly_fire")
                    && squadManager.sameSquad(p1.getUniqueId(), p2.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBossDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.LivingEntity) {
            Integer id = bossManager.getBossId(event.getEntity());
            if (id != null) {
                bossManager.updateBossBar((org.bukkit.entity.LivingEntity) event.getEntity());
            }
        }
    }
}
