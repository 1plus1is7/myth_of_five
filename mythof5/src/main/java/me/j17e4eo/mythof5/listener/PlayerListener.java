package me.j17e4eo.mythof5.listener;

import me.j17e4eo.mythof5.boss.BossManager;
import me.j17e4eo.mythof5.inherit.InheritManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.util.Vector;

public class PlayerListener implements Listener {

    private final BossManager bossManager;
    private final InheritManager inheritManager;
    private final boolean doubleJumpEnabled;
    private final double doubleJumpVerticalVelocity;
    private final double doubleJumpForwardMultiplier;

    public PlayerListener(BossManager bossManager, InheritManager inheritManager,
                          boolean doubleJumpEnabled, double doubleJumpVerticalVelocity,
                          double doubleJumpForwardMultiplier) {
        this.bossManager = bossManager;
        this.inheritManager = inheritManager;
        this.doubleJumpEnabled = doubleJumpEnabled;
        this.doubleJumpVerticalVelocity = doubleJumpVerticalVelocity;
        this.doubleJumpForwardMultiplier = doubleJumpForwardMultiplier;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        bossManager.handlePlayerJoin(player);
        inheritManager.handlePlayerJoin(player);
        initializeDoubleJump(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        inheritManager.handlePlayerQuit(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        inheritManager.handleDeath(event);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        inheritManager.handleGoblinFlameDrop(event);
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (inheritManager.containsGoblinFlame(event.getInventory().getMatrix())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (inheritManager.containsGoblinFlame(event.getInventory().getMatrix())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        initializeDoubleJump(event.getPlayer());
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        GameMode newMode = event.getNewGameMode();
        Player player = event.getPlayer();
        if (newMode == GameMode.CREATIVE || newMode == GameMode.SPECTATOR) {
            return;
        }

        if (newMode == GameMode.SURVIVAL || newMode == GameMode.ADVENTURE) {
            player.setAllowFlight(doubleJumpEnabled);
            player.setFlying(false);
        } else {
            player.setAllowFlight(false);
            player.setFlying(false);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!doubleJumpEnabled) {
            return;
        }

        Player player = event.getPlayer();
        if (!isDoubleJumpGamemode(player)) {
            return;
        }

        if (player.isOnGround() && !player.isFlying() && !player.isGliding() && !player.isInsideVehicle()) {
            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
            }
        }
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        if (!doubleJumpEnabled || !event.isFlying()) {
            return;
        }

        Player player = event.getPlayer();
        if (!isDoubleJumpGamemode(player) || player.isGliding()) {
            return;
        }

        event.setCancelled(true);
        player.setAllowFlight(false);
        player.setFlying(false);

        Vector lookDirection = player.getLocation().getDirection();
        lookDirection.setY(0);
        Vector velocity = lookDirection.lengthSquared() > 0
                ? lookDirection.normalize().multiply(doubleJumpForwardMultiplier)
                : new Vector(0, 0, 0);
        velocity.setY(doubleJumpVerticalVelocity);

        player.setVelocity(velocity);
        player.setFallDistance(0F);
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.4F, 1.2F);
    }

    public void initializeExistingPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            initializeDoubleJump(player);
        }
    }

    private void initializeDoubleJump(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        if (isDoubleJumpGamemode(player)) {
            player.setAllowFlight(doubleJumpEnabled);
            player.setFlying(false);
        } else {
            player.setAllowFlight(false);
            player.setFlying(false);
        }
    }

    private boolean isDoubleJumpGamemode(Player player) {
        GameMode gameMode = player.getGameMode();
        return gameMode == GameMode.SURVIVAL || gameMode == GameMode.ADVENTURE;
    }
}
