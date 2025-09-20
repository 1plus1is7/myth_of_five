package me.j17e4eo.mythof5.rift;

import me.j17e4eo.mythof5.Mythof5;
import me.j17e4eo.mythof5.rift.config.RiftTheme;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class RiftPersistence {
    private final Mythof5 plugin;
    private final File sitesFile;
    private final File playersFile;
    private final Map<UUID, RiftPlayerRecord> playerRecords = new HashMap<>();

    public RiftPersistence(Mythof5 plugin, File rootFolder) {
        this.plugin = plugin;
        this.sitesFile = new File(rootFolder, "sites.yml");
        this.playersFile = new File(rootFolder, "players.yml");
    }

    public Map<String, RiftSite> loadSites(Map<String, RiftTheme> themes) {
        Map<String, RiftSite> sites = new HashMap<>();
        if (!sitesFile.exists()) {
            return sites;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(sitesFile);
        ConfigurationSection section = yaml.getConfigurationSection("sites");
        if (section == null) {
            return sites;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection node = section.getConfigurationSection(id);
            if (node == null) {
                continue;
            }
            String themeKey = node.getString("theme");
            if (themeKey == null) {
                continue;
            }
            RiftTheme theme = themes.get(themeKey.toLowerCase(Locale.ROOT));
            if (theme == null) {
                plugin.getLogger().warning("Unknown rift theme " + themeKey + " for site " + id);
                continue;
            }
            String worldName = node.getString("world", "world");
            UUID worldUuid = null;
            if (node.isString("world-uuid")) {
                try {
                    worldUuid = UUID.fromString(node.getString("world-uuid"));
                } catch (IllegalArgumentException ignored) {
                }
            }
            World world = null;
            if (worldUuid != null) {
                world = Bukkit.getWorld(worldUuid);
            }
            if (world == null) {
                world = Bukkit.getWorld(worldName);
            }
            double x = node.getDouble("x");
            double y = node.getDouble("y");
            double z = node.getDouble("z");
            long cooldownUntil = node.getLong("cooldown-until", 0L);
            if (world != null) {
                RiftSite site = new RiftSite(id, world, x, y, z, theme);
                site.setCooldownUntil(cooldownUntil);
                sites.put(id.toLowerCase(Locale.ROOT), site);
            } else if (worldUuid != null) {
                RiftSite site = new RiftSite(id, worldUuid, worldName, x, y, z, themeKey, theme, cooldownUntil);
                sites.put(id.toLowerCase(Locale.ROOT), site);
            } else {
                plugin.getLogger().warning("Unable to resolve world for rift site " + id);
            }
        }
        return sites;
    }

    public void saveSites(Iterable<RiftSite> sites) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (RiftSite site : sites) {
            String path = "sites." + site.getId();
            yaml.set(path + ".theme", site.getThemeKey());
            yaml.set(path + ".world", site.getWorldName());
            yaml.set(path + ".world-uuid", site.getWorldId().toString());
            yaml.set(path + ".x", site.getX());
            yaml.set(path + ".y", site.getY());
            yaml.set(path + ".z", site.getZ());
            yaml.set(path + ".cooldown-until", site.getCooldownUntil());
        }
        try {
            yaml.save(sitesFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save rift sites: " + ex.getMessage());
        }
    }

    public void loadPlayers() {
        playerRecords.clear();
        if (!playersFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(playersFile);
        ConfigurationSection playersSection = yaml.getConfigurationSection("players");
        if (playersSection == null) {
            return;
        }
        for (String id : playersSection.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(id);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            ConfigurationSection node = playersSection.getConfigurationSection(id);
            if (node == null) {
                continue;
            }
            RiftPlayerRecord record = new RiftPlayerRecord();
            ConfigurationSection weeklySection = node.getConfigurationSection("weekly");
            if (weeklySection != null) {
                for (String theme : weeklySection.getKeys(false)) {
                    record.getWeeklyClears().put(theme, weeklySection.getString(theme));
                }
            }
            playerRecords.put(uuid, record);
        }
    }

    public void savePlayers() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, RiftPlayerRecord> entry : playerRecords.entrySet()) {
            String path = "players." + entry.getKey();
            RiftPlayerRecord record = entry.getValue();
            for (Map.Entry<String, String> weekly : record.getWeeklyClears().entrySet()) {
                yaml.set(path + ".weekly." + weekly.getKey(), weekly.getValue());
            }
        }
        try {
            yaml.save(playersFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save rift player data: " + ex.getMessage());
        }
    }

    public boolean shouldGrantWeeklyBonus(UUID playerId, String themeKey, DayOfWeek resetDay) {
        String weekKey = currentWeekKey(resetDay);
        RiftPlayerRecord record = playerRecords.computeIfAbsent(playerId, uuid -> new RiftPlayerRecord());
        String existing = record.getWeeklyClears().get(themeKey);
        return existing == null || !existing.equals(weekKey);
    }

    public void markWeeklyBonus(UUID playerId, String themeKey, DayOfWeek resetDay) {
        String weekKey = currentWeekKey(resetDay);
        RiftPlayerRecord record = playerRecords.computeIfAbsent(playerId, uuid -> new RiftPlayerRecord());
        record.getWeeklyClears().put(themeKey, weekKey);
    }

    private String currentWeekKey(DayOfWeek resetDay) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDate cursor = today;
        while (cursor.getDayOfWeek() != resetDay) {
            cursor = cursor.minusDays(1);
        }
        int week = cursor.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int year = cursor.get(IsoFields.WEEK_BASED_YEAR);
        return year + "-" + week;
    }

    public RiftPlayerRecord getPlayerRecord(UUID uuid) {
        return playerRecords.computeIfAbsent(uuid, id -> new RiftPlayerRecord());
    }

    public static final class RiftPlayerRecord {
        private final Map<String, String> weeklyClears = new HashMap<>();

        public Map<String, String> getWeeklyClears() {
            return weeklyClears;
        }
    }
}
