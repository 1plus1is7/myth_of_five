package me.j17e4eo.mythof5.omens;

import me.j17e4eo.mythof5.Mythof5;
import me.j17e4eo.mythof5.chronicle.ChronicleEventType;
import me.j17e4eo.mythof5.chronicle.ChronicleManager;
import me.j17e4eo.mythof5.config.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;

/**
 * Handles omen stage transitions and world feedback.
 */
public class OmenManager {

    private final Mythof5 plugin;
    private final Messages messages;
    private final ChronicleManager chronicleManager;
    private final Map<OmenStage, String> messageKeys = new EnumMap<>(OmenStage.class);
    private BukkitTask ghostFireTask;
    private OmenStage currentStage;

    public OmenManager(Mythof5 plugin, Messages messages, ChronicleManager chronicleManager) {
        this.plugin = plugin;
        this.messages = messages;
        this.chronicleManager = chronicleManager;
        messageKeys.put(OmenStage.STARSHIFT, "omen.starshift");
        messageKeys.put(OmenStage.GHOST_FIRE, "omen.ghost_fire");
        messageKeys.put(OmenStage.SKYBREAK, "omen.skybreak");
    }

    public void trigger(OmenStage stage, String reason) {
        currentStage = stage;
        broadcast(messages.format(messageKeys.get(stage), Map.of("reason", reason != null ? reason : "")));
        chronicleManager.logEvent(ChronicleEventType.OMEN,
                messages.format("chronicle.omen", Map.of(
                        "stage", stage.name(),
                        "reason", reason != null ? reason : messages.format("omen.unknown_reason")
                )), new ArrayList<>(Bukkit.getOnlinePlayers()));
        applyStage(stage);
    }

    private void applyStage(OmenStage stage) {
        cancelGhostFire();
        switch (stage) {
            case STARSHIFT -> applyStarshift();
            case GHOST_FIRE -> applyGhostFire();
            case SKYBREAK -> applySkybreak();
        }
    }

    private void applyStarshift() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.MUSIC_DISC_OTHERSIDE, 0.5F, 1.2F);
            player.sendActionBar(Component.text(messages.format("omen.starshift.actionbar"), NamedTextColor.LIGHT_PURPLE));
        }
    }

    private void applyGhostFire() {
        ghostFireTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                            player.getLocation().add(0, 1.5, 0), 20, 0.5, 0.8, 0.5, 0.01);
                }
            }
        }.runTaskTimer(plugin, 0L, 40L);
    }

    private void applySkybreak() {
        for (World world : Bukkit.getWorlds()) {
            world.setStorm(true);
            world.setThundering(true);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 0.8F, 0.6F);
            player.sendActionBar(Component.text(messages.format("omen.skybreak.actionbar"), NamedTextColor.RED));
        }
    }

    private void cancelGhostFire() {
        if (ghostFireTask != null) {
            ghostFireTask.cancel();
            ghostFireTask = null;
        }
    }

    private void broadcast(String message) {
        plugin.broadcast(message);
    }


    public void shutdown() {
        cancelGhostFire();
        for (World world : Bukkit.getWorlds()) {
            world.setStorm(false);
            world.setThundering(false);
        }
    }
}
