package me.j17e4eo.mythof5.rift.config;

import me.j17e4eo.mythof5.Mythof5;
import org.bukkit.boss.BarColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class RiftConfigLoader {
    private final Mythof5 plugin;
    private final File rootFolder;
    private final File riftsFolder;
    private RiftConfig config;
    private final Map<String, RiftTheme> themes = new HashMap<>();

    public RiftConfigLoader(Mythof5 plugin) {
        this.plugin = plugin;
        this.rootFolder = new File(plugin.getDataFolder().getParentFile(), "Rift");
        this.riftsFolder = new File(rootFolder, "rifts");
    }

    public void load() throws IOException {
        ensureDefaultResources();
        File configFile = new File(rootFolder, "config.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile);
        int chunkRadius = Math.max(1, yaml.getInt("chunk-radius", 1));
        int collapseSeconds = Math.max(5, yaml.getInt("collapse-seconds", 60));
        int cooldownSeconds = Math.max(30, yaml.getInt("cooldown-seconds", 900));
        int maxActive = Math.max(1, yaml.getInt("max-active", 3));
        int awakeningSeconds = Math.max(5, yaml.getInt("awakening-seconds", 12));
        int evictionSeconds = Math.max(10, yaml.getInt("eviction-seconds", 45));
        Material activationBlock = Material.matchMaterial(yaml.getString("activation-block", "LODSTONE"));
        if (activationBlock == null) {
            activationBlock = Material.LODESTONE;
        }
        Sound activationSound = parseSound(yaml.getString("sounds.activation", "ENTITY_ILLUSIONER_CAST_SPELL"), Sound.ENTITY_ILLUSIONER_CAST_SPELL);
        Sound collapseSound = parseSound(yaml.getString("sounds.collapse", "BLOCK_BEACON_DEACTIVATE"), Sound.BLOCK_BEACON_DEACTIVATE);
        ConfigurationSection protectSection = yaml.getConfigurationSection("protect");
        boolean protectBlocks = protectSection == null || protectSection.getBoolean("break", true);
        boolean protectPlacement = protectSection == null || protectSection.getBoolean("place", true);
        boolean protectPvp = protectSection == null || protectSection.getBoolean("pvp", true);
        boolean protectFall = protectSection == null || protectSection.getBoolean("fall", true);
        boolean suppressSpawns = yaml.getBoolean("suppress-natural-spawns", true);
        ConfigurationSection compassSection = yaml.getConfigurationSection("compass");
        String compassName = compassSection != null ? compassSection.getString("display-name", "§d균열 나침반") : "§d균열 나침반";
        List<String> compassLore = compassSection != null ? compassSection.getStringList("lore") : List.of("§7가장 가까운 균열을 가리킵니다.");
        int compassUpdateSeconds = compassSection != null ? Math.max(1, compassSection.getInt("update-seconds", 5)) : 5;
        DayOfWeek resetDay = parseDayOfWeek(yaml.getString("weekly-reset-day", "MONDAY"));
        this.config = new RiftConfig(chunkRadius, collapseSeconds, cooldownSeconds, maxActive, awakeningSeconds, evictionSeconds,
                activationBlock, activationSound, collapseSound, protectBlocks, protectPlacement, protectPvp, protectFall,
                suppressSpawns, compassName, compassLore, compassUpdateSeconds, resetDay);

        themes.clear();
        File[] biomeFolders = riftsFolder.listFiles(File::isDirectory);
        if (biomeFolders == null) {
            return;
        }
        for (File folder : biomeFolders) {
            RiftTheme theme = loadTheme(folder, awakeningSeconds);
            if (theme != null) {
                themes.put(theme.getKey(), theme);
            }
        }
    }

    private void ensureDefaultResources() throws IOException {
        if (!rootFolder.exists() && !rootFolder.mkdirs()) {
            throw new IOException("Unable to create rift root folder " + rootFolder);
        }
        copyIfMissing("config.yml", new File(rootFolder, "config.yml"));
        if (!riftsFolder.exists() && !riftsFolder.mkdirs()) {
            throw new IOException("Unable to create rifts folder " + riftsFolder);
        }
        File plainsFolder = new File(riftsFolder, "plains");
        if (!plainsFolder.exists() && plainsFolder.mkdirs()) {
            copyIfMissing("rifts/plains/mobs.yml", new File(plainsFolder, "mobs.yml"));
            copyIfMissing("rifts/plains/phases.yml", new File(plainsFolder, "phases.yml"));
            copyIfMissing("rifts/plains/boss.yml", new File(plainsFolder, "boss.yml"));
            copyIfMissing("rifts/plains/rewards.yml", new File(plainsFolder, "rewards.yml"));
            copyIfMissing("rifts/plains/palette.yml", new File(plainsFolder, "palette.yml"));
        }
    }

    private void copyIfMissing(String resourcePath, File file) throws IOException {
        if (file.exists()) {
            return;
        }
        try (InputStream inputStream = plugin.getResource("rift/" + resourcePath)) {
            if (inputStream == null) {
                return;
            }
            Files.createDirectories(file.toPath().getParent());
            try (FileOutputStream outputStream = new FileOutputStream(file)) {
                inputStream.transferTo(outputStream);
            }
        }
    }

    private Sound parseSound(String name, Sound fallback) {
        if (name == null) {
            return fallback;
        }
        try {
            return Sound.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private DayOfWeek parseDayOfWeek(String day) {
        try {
            return DayOfWeek.valueOf(day.toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            return DayOfWeek.MONDAY;
        }
    }

    private RiftTheme loadTheme(File folder, int defaultAwakening) {
        String key = folder.getName().toLowerCase(Locale.ROOT);
        File mobsFile = new File(folder, "mobs.yml");
        File phasesFile = new File(folder, "phases.yml");
        File bossFile = new File(folder, "boss.yml");
        File rewardsFile = new File(folder, "rewards.yml");
        File paletteFile = new File(folder, "palette.yml");
        if (!mobsFile.exists() || !phasesFile.exists() || !bossFile.exists() || !rewardsFile.exists()) {
            plugin.getLogger().warning("Skipping rift theme " + key + " because required files are missing");
            return null;
        }
        YamlConfiguration mobs = YamlConfiguration.loadConfiguration(mobsFile);
        String displayName = mobs.getString("display-name", key);
        BarColor barColor = parseBarColor(mobs.getString("bar-color", "PURPLE"));
        double danger = mobs.getDouble("danger", 1.0D);
        int spawnRadius = Math.max(6, mobs.getInt("spawn-radius", 12));
        int awakening = Math.max(5, mobs.getInt("awakening-seconds", defaultAwakening));
        Map<String, String> palette = new HashMap<>();
        if (paletteFile.exists()) {
            YamlConfiguration paletteYaml = YamlConfiguration.loadConfiguration(paletteFile);
            for (String paletteKey : paletteYaml.getKeys(false)) {
                palette.put(paletteKey, Objects.toString(paletteYaml.get(paletteKey), ""));
            }
        }
        YamlConfiguration phases = YamlConfiguration.loadConfiguration(phasesFile);
        RiftPhase phaseOne = parsePhase(phases, "phase1", spawnRadius);
        RiftPhase phaseTwo = parsePhase(phases, "phase2", spawnRadius);
        YamlConfiguration boss = YamlConfiguration.loadConfiguration(bossFile);
        RiftBossConfig bossConfig = RiftBossConfig.fromSection(boss);
        RiftRewardTable rewardTable = parseRewards(rewardsFile);
        RiftFlavor flavor = parseFlavor(mobs.getConfigurationSection("messages"));
        return new RiftTheme(key, displayName, barColor, danger, spawnRadius, awakening, phaseOne, phaseTwo, bossConfig, rewardTable, palette, flavor);
    }

    private RiftPhase parsePhase(YamlConfiguration yaml, String key, int defaultRadius) {
        ConfigurationSection section = yaml.getConfigurationSection(key);
        if (section == null) {
            return new RiftPhase(key, 60, List.of());
        }
        int duration = Math.max(10, section.getInt("duration", 60));
        List<RiftWave> waves = new ArrayList<>();
        List<?> waveList = section.getList("waves");
        if (waveList != null) {
            for (Object obj : waveList) {
                if (obj instanceof Map<?, ?> map) {
                    RiftWave wave = parseWave(map, defaultRadius);
                    if (wave != null) {
                        waves.add(wave);
                    }
                }
            }
        } else {
            ConfigurationSection waveSection = section.getConfigurationSection("waves");
            if (waveSection != null) {
                for (String child : waveSection.getKeys(false)) {
                    ConfigurationSection childSection = waveSection.getConfigurationSection(child);
                    if (childSection != null) {
                        RiftWave wave = parseWave(childSection.getValues(false), defaultRadius);
                        if (wave != null) {
                            waves.add(wave);
                        }
                    }
                }
            }
        }
        return new RiftPhase(section.getString("name", key), duration, waves);
    }

    private RiftWave parseWave(Map<?, ?> map, int defaultRadius) {
        int delay = asInt(map.get("delay"), 0);
        int crystals = asInt(map.get("mechanic-crystals"), asInt(map.get("mechanics"), 0));
        double radius = asDouble(map.get("radius"), defaultRadius);
        Object mobsObject = map.get("mobs");
        if (!(mobsObject instanceof Map<?, ?> mobsMap)) {
            return new RiftWave(delay, List.of(), crystals);
        }
        List<RiftSpawnEntry> spawns = new ArrayList<>();
        for (Map.Entry<?, ?> entry : mobsMap.entrySet()) {
            String entityName = entry.getKey().toString();
            try {
                org.bukkit.entity.EntityType type = org.bukkit.entity.EntityType.valueOf(entityName.toUpperCase(Locale.ROOT));
                int amount = 1;
                double spawnRadius = radius;
                Object value = entry.getValue();
                if (value instanceof Number number) {
                    amount = number.intValue();
                } else if (value instanceof Map<?, ?> nested) {
                    amount = asInt(nested.get("amount"), asInt(nested.get("count"), 1));
                    if (nested.containsKey("radius")) {
                        spawnRadius = asDouble(nested.get("radius"), radius);
                    }
                }
                spawns.add(new RiftSpawnEntry(type, Math.max(1, amount), spawnRadius));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return new RiftWave(delay, spawns, crystals);
    }

    private RiftRewardTable parseRewards(File file) {
        if (!file.exists()) {
            return RiftRewardTable.empty();
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<RiftRewardEntry> personal = parseRewardList(yaml, "personal");
        List<RiftRewardEntry> party = parseRewardList(yaml, "party");
        List<RiftRewardEntry> weekly = parseRewardList(yaml, "weekly");
        return new RiftRewardTable(personal, party, weekly);
    }

    private RiftFlavor parseFlavor(ConfigurationSection section) {
        if (section == null) {
            return RiftFlavor.empty();
        }
        List<String> activation = readStringList(section, "activation");
        List<String> awakening = readStringList(section, "awakening");
        List<String> phaseOne = readStringList(section, "phase1");
        List<String> phaseTwo = readStringList(section, "phase2");
        List<String> boss = readStringList(section, "boss");
        List<String> collapse = readStringList(section, "collapse");
        List<String> mechanic = readStringList(section, "mechanic");
        return new RiftFlavor(activation, awakening, phaseOne, phaseTwo, boss, collapse, mechanic);
    }

    private List<RiftRewardEntry> parseRewardList(YamlConfiguration yaml, String key) {
        List<RiftRewardEntry> list = new ArrayList<>();
        List<?> rawList = yaml.getList(key);
        if (rawList == null) {
            return list;
        }
        for (Object obj : rawList) {
            if (obj instanceof Map<?, ?> map) {
                ConfigurationSection section = mapToSection(map);
                try {
                    list.add(RiftRewardEntry.fromSection(section));
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Failed to parse reward entry in " + key + ": " + ex.getMessage());
                }
            }
        }
        return list;
    }

    private List<String> readStringList(ConfigurationSection section, String key) {
        if (section == null) {
            return List.of();
        }
        List<String> values = section.getStringList(key);
        if (!values.isEmpty()) {
            return List.copyOf(values);
        }
        String single = section.getString(key);
        if (single == null || single.isBlank()) {
            return List.of();
        }
        return List.of(single);
    }

    private ConfigurationSection mapToSection(Map<?, ?> map) {
        MemoryConfiguration configuration = new MemoryConfiguration();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            configuration.set(entry.getKey().toString(), entry.getValue());
        }
        return configuration;
    }

    private BarColor parseBarColor(String value) {
        try {
            return BarColor.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            return BarColor.PURPLE;
        }
    }

    private int asInt(Object value, int def) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    private double asDouble(Object value, double def) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    public RiftConfig getConfig() {
        return config;
    }

    public Map<String, RiftTheme> getThemes() {
        return Map.copyOf(themes);
    }

    public RiftTheme getTheme(String key) {
        return themes.get(key.toLowerCase(Locale.ROOT));
    }

    public File getRootFolder() {
        return rootFolder;
    }
}
