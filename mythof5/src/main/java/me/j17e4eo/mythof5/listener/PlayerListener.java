package me.j17e4eo.mythof5.listener;

import me.j17e4eo.mythof5.boss.BossManager;
import me.j17e4eo.mythof5.inherit.InheritManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final BossManager bossManager;
    private final InheritManager inheritManager;

    public PlayerListener(BossManager bossManager, InheritManager inheritManager) {
        this.bossManager = bossManager;
        this.inheritManager = inheritManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        bossManager.handlePlayerJoin(player);
        inheritManager.handlePlayerJoin(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        inheritManager.handlePlayerQuit(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        inheritManager.handleDeath(event);
    }
}
