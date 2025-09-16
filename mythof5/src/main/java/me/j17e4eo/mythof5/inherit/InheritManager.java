package me.j17e4eo.mythof5.inherit;

import me.j17e4eo.mythof5.Mythof5;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InheritManager {

    private final Mythof5 plugin;
    private final NamespacedKey inheritorFlagKey;
    private final NamespacedKey powerKeyKey;
    private final String powerKeyValue;
    private final boolean announceEnabled;
    private final List<PowerBuff> buffs = new ArrayList<>();
    private final Set<UUID> applyTokens = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<UUID> removeTokens = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private File dataFile;
    private YamlConfiguration dataConfig;
    private UUID inheritorId;
    private String inheritorName;

    public InheritManager(Mythof5 plugin) {
        this.plugin = plugin;
        this.inheritorFlagKey = new NamespacedKey(plugin, "is_inheritor");
        this.powerKeyKey = new NamespacedKey(plugin, "power_key");
        this.powerKeyValue = plugin.getConfig().getString("inherit.power_key", "dokkaebi.core");
        this.announceEnabled = plugin.getConfig().getBoolean("inherit.announce", true);
        loadBuffs();
    }

    public void load() {
        plugin.getDataFolder().mkdirs();
        dataFile = new File(plugin.getDataFolder(), "inherit.yml");
        if (!dataFile.exists()) {
            dataConfig = new YamlConfiguration();
            return;
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        String uuid = dataConfig.getString("inheritor.uuid");
        if (uuid != null && !uuid.isEmpty()) {
            try {
                inheritorId = UUID.fromString(uuid);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Invalid inheritor UUID in data file: " + uuid);
            }
        }
        inheritorName = dataConfig.getString("inheritor.name");
    }

    public void save() {
        if (dataConfig == null) {
            dataConfig = new YamlConfiguration();
        }
        if (inheritorId == null) {
            dataConfig.set("inheritor", null);
        } else {
            dataConfig.set("inheritor.uuid", inheritorId.toString());
            dataConfig.set("inheritor.name", inheritorName);
        }
        if (dataFile == null) {
            dataFile = new File(plugin.getDataFolder(), "inherit.yml");
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save inherit data: " + e.getMessage());
        }
    }

    public void reapplyToOnlinePlayers() {
        if (inheritorId == null) {
            return;
        }
        Player player = Bukkit.getPlayer(inheritorId);
        if (player != null) {
            applyPower(player);
        }
    }

    public void handlePlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();
        if (inheritorId != null && inheritorId.equals(uuid)) {
            applyPower(player);
            return;
        }

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        boolean hasFlag = pdc.has(inheritorFlagKey, PersistentDataType.BYTE);
        String storedPowerKey = pdc.get(powerKeyKey, PersistentDataType.STRING);
        boolean matchesPowerKey = storedPowerKey != null && storedPowerKey.equals(powerKeyValue);
        boolean hasBuffs = buffs.stream().anyMatch(buff -> player.hasPotionEffect(buff.type()));
        if (hasFlag || matchesPowerKey || hasBuffs) {
            removePower(player);
        }
    }

    public void handleDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (inheritorId == null || !player.getUniqueId().equals(inheritorId)) {
            return;
        }
        Player killer = player.getKiller();
        if (killer == null) {
            return;
        }
        String name = inheritorName != null ? inheritorName : player.getName();
        clearInheritorInternal(true, name);
    }

    public void setInheritor(Player player) {
        UUID uuid = player.getUniqueId();
        if (inheritorId != null && inheritorId.equals(uuid)) {
            inheritorName = player.getName();
            applyPower(player);
            save();
            return;
        }
        if (inheritorId != null && !inheritorId.equals(uuid)) {
            Player previous = Bukkit.getPlayer(inheritorId);
            if (previous != null) {
                removePower(previous);
                previous.sendMessage(Component.text("도깨비의 힘이 다른 계승자에게 이전되었습니다.", NamedTextColor.GRAY));
            }
        }
        inheritorId = uuid;
        inheritorName = player.getName();
        applyPower(player);
        save();
    }

    public void clearInheritor(boolean announce) {
        if (inheritorId == null) {
            return;
        }
        String name = inheritorName;
        clearInheritorInternal(announce, name);
    }

    private void clearInheritorInternal(boolean announce, String targetName) {
        UUID previousId = inheritorId;
        inheritorId = null;
        inheritorName = null;
        Player player = previousId != null ? Bukkit.getPlayer(previousId) : null;
        if (player != null) {
            removePower(player);
        }
        save();
        if (announce && targetName != null) {
            if (announceEnabled) {
                plugin.broadcast("[방송] " + targetName + "가 쓰러져 도깨비의 힘이 사라졌다.");
            }
            plugin.getLogger().info(String.format("[Event:MYTH_LOST] %s lost the power.", targetName));
        }
    }

    private void applyPower(Player player) {
        UUID uuid = player.getUniqueId();
        if (!applyTokens.add(uuid)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> applyTokens.remove(uuid));

        for (PowerBuff buff : buffs) {
            PotionEffect effect = new PotionEffect(buff.type(), Integer.MAX_VALUE, buff.amplifier(), false, true, true);
            player.addPotionEffect(effect);
        }
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(inheritorFlagKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(powerKeyKey, PersistentDataType.STRING, powerKeyValue);
    }

    private void removePower(Player player) {
        UUID uuid = player.getUniqueId();
        if (!removeTokens.add(uuid)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> removeTokens.remove(uuid));

        for (PowerBuff buff : buffs) {
            player.removePotionEffect(buff.type());
        }
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.remove(inheritorFlagKey);
        pdc.remove(powerKeyKey);
    }

    private void loadBuffs() {
        buffs.clear();
        List<String> entries = plugin.getConfig().getStringList("inherit.buffs");
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String[] split = entry.split(":");
            PotionEffectType type = PotionEffectType.getByName(split[0].trim().toUpperCase(Locale.ROOT));
            if (type == null) {
                plugin.getLogger().warning("Unknown potion effect type in inherit.buffs: " + split[0]);
                continue;
            }
            int level = 1;
            if (split.length > 1) {
                try {
                    level = Integer.parseInt(split[1].trim());
                } catch (NumberFormatException ex) {
                    plugin.getLogger().warning("Invalid amplifier for inherit buff " + entry);
                }
            }
            int amplifier = Math.max(0, level - 1);
            buffs.add(new PowerBuff(type, amplifier));
        }
    }

    public void handlePlayerQuit(Player player) {
        // No specific handling required; keep state for reapplication.
    }

    public UUID getInheritorId() {
        return inheritorId;
    }

    public String getInheritorName() {
        return inheritorName;
    }

    public boolean isAnnouncementsEnabled() {
        return announceEnabled;
    }

    private record PowerBuff(PotionEffectType type, int amplifier) {
    }
}
