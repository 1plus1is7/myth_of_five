package me.j17e4eo.mythof5.boss;

import me.j17e4eo.mythof5.Mythof5;
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
    private final Map<Integer, BossInstance> activeBosses = new HashMap<>();
    private final Map<UUID, BossInstance> bossesByEntity = new HashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(1);
    private final double defaultHp;
    private final double defaultArmor;
    private final String broadcastName;

    public BossManager(Mythof5 plugin, InheritManager inheritManager) {
        this.plugin = plugin;
        this.inheritManager = inheritManager;
        this.defaultHp = plugin.getConfig().getDouble("boss.hp_default", 10000D);
        this.defaultArmor = plugin.getConfig().getDouble("boss.armor_default", 50D);
        this.broadcastName = plugin.getConfig().getString("boss.name", "태초의 도깨비");
    }

    public BossInstance spawnBoss(String typeName, Double hp, Double armor, Location location) {
        double bossHp = hp == null ? defaultHp : hp;
        double bossArmor = armor == null ? defaultArmor : armor;

        LivingEntity entity = (LivingEntity) location.getWorld().spawnEntity(location, EntityType.ZOMBIE);
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

        String actualName = resolveDisplayName(typeName);
        entity.customName(Component.text(actualName, NamedTextColor.DARK_RED));
        entity.setCustomNameVisible(true);

        BossBar bossBar = Bukkit.createBossBar(actualName, BarColor.RED, BarStyle.SEGMENTED_10);
        bossBar.setVisible(true);
        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
        }

        int bossId = idCounter.getAndIncrement();
        BossInstance instance = new BossInstance(bossId, entity, bossBar, actualName, broadcastName, actualMax);
        activeBosses.put(bossId, instance);
        bossesByEntity.put(entity.getUniqueId(), instance);
        instance.updateProgress();

        plugin.broadcast("[방송] " + broadcastName + "가 나타났다!");
        plugin.getLogger().info(String.format("[Event:BOSS_SPAWNED] Boss #%d spawned at %s, world=%s, hp=%.1f, armor=%.1f", bossId,
                stringifyLocation(location), location.getWorld().getName(), bossHp, bossArmor));
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
        plugin.getLogger().info(String.format("[Event:BOSS_DEFEATED] Boss #%d defeated.", instance.getId()));
        if (killer != null) {
            if (inheritManager.isAnnouncementsEnabled()) {
                plugin.broadcast("[방송] " + killer.getName() + "가 " + broadcastName + "를 쓰러뜨리고 힘을 계승했다!");
            }
            inheritManager.setInheritor(killer);
            plugin.getLogger().info(String.format("[Event:MYTH_INHERITED] %s inherited the power.", killer.getName()));
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
            sender.sendMessage(Component.text("활성화된 도깨비 보스가 없습니다.", NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text("==== Boss List ====", NamedTextColor.GOLD));
        for (BossInstance instance : activeBosses.values()) {
            LivingEntity entity = instance.getEntity();
            Location loc = entity.getLocation();
            double health = entity.getHealth();
            sender.sendMessage(Component.text(String.format("#%d %s - HP %.1f, 위치 %s", instance.getId(), instance.getBroadcastName(), health, stringifyLocation(loc)), NamedTextColor.GREEN));
        }
    }

    public Optional<BossInstance> findById(int id) {
        return Optional.ofNullable(activeBosses.get(id));
    }

    private String resolveDisplayName(String typeName) {
        if (typeName == null || typeName.isBlank()) {
            return broadcastName;
        }
        if (typeName.equalsIgnoreCase("도깨비")) {
            return broadcastName;
        }
        return typeName;
    }

    private String stringifyLocation(Location location) {
        World world = location.getWorld();
        return String.format("%s(%.1f, %.1f, %.1f)", world != null ? world.getName() : "unknown", location.getX(), location.getY(), location.getZ());
    }
}
