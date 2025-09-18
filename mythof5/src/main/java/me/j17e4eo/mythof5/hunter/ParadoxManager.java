package me.j17e4eo.mythof5.hunter;

import me.j17e4eo.mythof5.Mythof5;
import me.j17e4eo.mythof5.chronicle.ChronicleEventType;
import me.j17e4eo.mythof5.chronicle.ChronicleManager;
import me.j17e4eo.mythof5.config.Messages;
import me.j17e4eo.mythof5.hunter.data.HunterProfile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Handles paradox dokkaebi summons and reset rituals.
 */
public class ParadoxManager {

    private final Mythof5 plugin;
    private final Messages messages;
    private final ChronicleManager chronicleManager;
    private final HunterManager hunterManager;
    private final long ritualWindowTicks;
    private final double failureScale;
    private final Random random = new Random();
    private ParadoxRitual activeRitual;
    private BukkitTask ritualTask;

    public ParadoxManager(Mythof5 plugin, Messages messages, ChronicleManager chronicleManager,
                          HunterManager hunterManager, long ritualWindowSeconds, double failureScale) {
        this.plugin = plugin;
        this.messages = messages;
        this.chronicleManager = chronicleManager;
        this.hunterManager = hunterManager;
        this.ritualWindowTicks = ritualWindowSeconds * 20L;
        this.failureScale = failureScale;
    }

    public boolean summonParadox(Player summoner, Location location) {
        plugin.broadcast(messages.format("broadcast.hunter_paradox_summon", Map.of(
                "player", summoner.getName(),
                "world", location.getWorld().getName(),
                "x", String.format(Locale.KOREA, "%.1f", location.getX()),
                "y", String.format(Locale.KOREA, "%.1f", location.getY()),
                "z", String.format(Locale.KOREA, "%.1f", location.getZ())
        )));
        return true;
    }

    public synchronized boolean beginRitual(Player player, HunterProfile profile) {
        if (activeRitual != null) {
            player.sendMessage(messages.format("hunter.paradox.busy"));
            return false;
        }
        activeRitual = new ParadoxRitual(player.getUniqueId(), player.getName(), player.getLocation().clone(), Instant.now());
        plugin.broadcast(messages.format("broadcast.hunter_paradox_ritual", Map.of("player", player.getName())));
        chronicleManager.logEvent(ChronicleEventType.HUNTER_RESET,
                messages.format("chronicle.hunter.ritual_started", Map.of("player", player.getName())),
                new ArrayList<>(Bukkit.getOnlinePlayers()));
        if (ritualTask != null) {
            ritualTask.cancel();
        }
        ritualTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> evaluateRitual(profile), ritualWindowTicks);
        return true;
    }

    private void evaluateRitual(HunterProfile profile) {
        ParadoxRitual ritual = this.activeRitual;
        this.activeRitual = null;
        this.ritualTask = null;
        if (ritual == null) {
            return;
        }
        Player player = Bukkit.getPlayer(ritual.hunterId());
        double mythicCount = profile.countMythicArtifacts();
        double successChance = Math.min(0.2, 0.05 + 0.03 * Math.max(0, mythicCount - 3));
        double roll = random.nextDouble();
        if (player != null) {
            player.sendMessage(messages.format("hunter.paradox.ritual_roll", Map.of(
                    "chance", String.format(Locale.KOREA, "%.1f%%", successChance * 100.0),
                    "roll", String.format(Locale.KOREA, "%.2f", roll)
            )));
        }
        if (roll <= successChance) {
            hunterManager.handleParadoxSuccess(ritual, profile);
        } else {
            hunterManager.handleParadoxFailure(ritual, profile, failureScale);
        }
    }

    public synchronized void cancelActive(String reason) {
        if (ritualTask != null) {
            ritualTask.cancel();
            ritualTask = null;
        }
        if (activeRitual != null) {
            Player player = Bukkit.getPlayer(activeRitual.hunterId());
            if (player != null) {
                player.sendMessage(messages.format("hunter.paradox.cancelled", Map.of("reason", reason)));
            }
        }
        activeRitual = null;
    }

    public void shutdown() {
        cancelActive("shutdown");
    }

    public record ParadoxRitual(UUID hunterId, String hunterName, Location origin, Instant startedAt) {}
}
