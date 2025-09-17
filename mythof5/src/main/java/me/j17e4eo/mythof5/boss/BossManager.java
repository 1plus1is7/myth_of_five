package me.j17e4eo.mythof5.boss;

import me.j17e4eo.mythof5.Mythof5;
import me.j17e4eo.mythof5.config.Messages;
import me.j17e4eo.mythof5.inherit.InheritManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class BossManager {

    private final Mythof5 plugin;
    private final InheritManager inheritManager;
    private final Messages messages;
    private final Map<Integer, BossInstance> activeBosses = new HashMap<>();
    private final Map<UUID, BossInstance> bossesByEntity = new HashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(1);
    private final double defaultHp;
    private final double defaultArmor;
    private final String defaultName;

    public BossManager(Mythof5 plugin, InheritManager inheritManager, Messages messages) {
        this.plugin = plugin;
        this.inheritManager = inheritManager;
        this.messages = messages;
        this.defaultHp = plugin.getConfig().getDouble("boss.hp_default", 10000D);
        this.defaultArmor = plugin.getConfig().getDouble("boss.armor_default", 50D);
        this.defaultName = plugin.getConfig().getString("boss.name", "태초의 도깨비");
    }

    public BossInstance spawnBoss(EntityType entityType, String name, Double hp, Double armor, Location location) {
        double bossHp = hp == null ? defaultHp : hp;
        double bossArmor = armor == null ? defaultArmor : armor;

        LivingEntity entity = (LivingEntity) location.getWorld().spawnEntity(location, entityType);
        entity.setRemoveWhenFarAway(false);
        entity.setPersistent(true);

        AttributeInstance maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
        double actualMax = bossHp;
        if (maxHealth != null) {
            maxHealth.setBaseValue(bossHp);
            actualMax = maxHealth.getValue();
        }
        entity.setHealth(Math.min(actualMax, bossHp));

        AttributeInstance armorAttr = entity.getAttribute(Attribute.ARMOR);
        if (armorAttr != null) {
            armorAttr.setBaseValue(bossArmor);
        }

        String actualName = resolveDisplayName(name);
        entity.customName(Component.text(actualName, NamedTextColor.DARK_RED));
        entity.setCustomNameVisible(true);

        BossBar bossBar = Bukkit.createBossBar(actualName, BarColor.RED, BarStyle.SEGMENTED_10);
        bossBar.setVisible(true);
        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
        }

        int bossId = idCounter.getAndIncrement();
        BossInstance instance = new BossInstance(bossId, entity, bossBar, actualName, entityType, actualMax);
        activeBosses.put(bossId, instance);
        bossesByEntity.put(entity.getUniqueId(), instance);
        instance.updateProgress();

        plugin.broadcast(messages.format("broadcast.boss_spawn", Map.of("name", actualName)));
        plugin.getLogger().info(String.format("[Event:BOSS_SPAWNED] Boss #%d (%s) spawned at %s, world=%s, hp=%.1f, armor=%.1f", bossId,
                entityType.name(), stringifyLocation(location), location.getWorld().getName(), bossHp, bossArmor));
        return instance;
    }

    public boolean endBoss(int id) {
        BossInstance instance = activeBosses.remove(id);
        if (instance == null) {
            return false;
        }
        bossesByEntity.remove(instance.getEntity().getUniqueId());
        instance.remove();
        if (instance.getEntity().isValid()) {
            instance.getEntity().remove();
        }
        plugin.getLogger().info(String.format("[Event:BOSS_DEFEATED] Boss #%d forcefully removed.", id));
        return true;
    }

    public Collection<BossInstance> getActiveBosses() {
        return Collections.unmodifiableCollection(new ArrayList<>(activeBosses.values()));
    }

    public void handleBossDeath(LivingEntity entity, Player killer) {
        BossInstance instance = bossesByEntity.remove(entity.getUniqueId());
        if (instance == null) {
            return;
        }
        activeBosses.remove(instance.getId());
        instance.remove();
        Location location = entity.getLocation();
        if (killer != null) {
            plugin.getLogger().info(String.format("[Event:BOSS_DEFEATED] Boss #%d defeated by %s at %s", instance.getId(),
                    killer.getName(), stringifyLocation(location)));
            inheritManager.grantFromBoss(killer, instance.getDisplayName());
        } else {
            plugin.getLogger().info(String.format("[Event:BOSS_DEFEATED] Boss #%d died without a killer at %s", instance.getId(),
                    stringifyLocation(location)));
            inheritManager.clearInheritor(true);
        }
    }

    public void updateProgress(LivingEntity entity) {
        BossInstance instance = bossesByEntity.get(entity.getUniqueId());
        if (instance != null) {
            instance.updateProgress();
        }
    }

    public void handlePlayerJoin(Player player) {
        for (BossInstance instance : activeBosses.values()) {
            instance.addViewer(player);
            instance.updateProgress();
        }
    }

    public void initializeBossBars() {
        for (BossInstance instance : activeBosses.values()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                instance.addViewer(player);
            }
            instance.updateProgress();
        }
    }

    public void shutdown() {
        for (BossInstance instance : new ArrayList<>(activeBosses.values())) {
            instance.remove();
            if (instance.getEntity().isValid()) {
                instance.getEntity().remove();
            }
        }
        activeBosses.clear();
        bossesByEntity.clear();
    }

    public void sendBossList(CommandSender sender) {
        if (activeBosses.isEmpty()) {
            sender.sendMessage(Component.text(messages.format("commands.myth.boss_list_empty"), NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text(messages.format("commands.myth.boss_list_header"), NamedTextColor.GOLD));
        for (BossInstance instance : activeBosses.values()) {
            LivingEntity entity = instance.getEntity();
            Location loc = entity.getLocation();
            double health = entity.getHealth();
            String entry = messages.format("commands.myth.boss_list_entry", Map.of(
                    "id", String.valueOf(instance.getId()),
                    "name", instance.getDisplayName(),
                    "type", instance.getEntityType().name(),
                    "health", String.format("%.1f/%.1f", health, instance.getMaxHealth()),
                    "location", stringifyLocation(loc)
            ));
            sender.sendMessage(Component.text(entry, NamedTextColor.GREEN));
        }
    }

    public Optional<BossInstance> findById(int id) {
        return Optional.ofNullable(activeBosses.get(id));
    }

    public boolean hasActiveBossWithName(String name) {
        for (BossInstance instance : activeBosses.values()) {
            if (instance.getDisplayName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private String resolveDisplayName(String providedName) {
        if (providedName == null || providedName.isBlank()) {
            return defaultName;
        }
        return providedName;
    }

    private String stringifyLocation(Location location) {
        World world = location.getWorld();
        return String.format("%s(%.1f, %.1f, %.1f)", world != null ? world.getName() : "unknown", location.getX(), location.getY(), location.getZ());
    }
}
