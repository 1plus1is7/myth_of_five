package me.j17e4eo.mythof5.hunter;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Bridges Bukkit events into the hunter manager lifecycle.
 */
public class HunterListener implements Listener {

    private final HunterManager hunterManager;

    public HunterListener(HunterManager hunterManager) {
        this.hunterManager = hunterManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        hunterManager.handleJoin(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        hunterManager.handleJoin(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        hunterManager.handleDeath(event.getEntity());
    }
}
