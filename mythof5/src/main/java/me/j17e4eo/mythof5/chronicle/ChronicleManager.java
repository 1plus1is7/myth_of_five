package me.j17e4eo.mythof5.chronicle;

import me.j17e4eo.mythof5.Mythof5;
import me.j17e4eo.mythof5.config.Messages;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Stores server chronicles in an old-Korean narrative style.
 */
public class ChronicleManager {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")
            .withLocale(Locale.KOREA).withZone(ZoneId.systemDefault());

    private final Mythof5 plugin;
    private final Messages messages;
    private final List<ChronicleRecord> records = new ArrayList<>();
    private File file;
    private YamlConfiguration config;

    public ChronicleManager(Mythof5 plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public void load() {
        plugin.getDataFolder().mkdirs();
        file = new File(plugin.getDataFolder(), "chronicle.yml");
        if (!file.exists()) {
            config = new YamlConfiguration();
            return;
        }
        config = YamlConfiguration.loadConfiguration(file);
        records.clear();
        List<Map<?, ?>> section = config.getMapList("entries");
        for (Map<?, ?> raw : section) {
            try {
                String typeRaw = Objects.toString(raw.get("type"), null);
                String timeRaw = Objects.toString(raw.get("time"), null);
                String text = Objects.toString(raw.get("text"), "");
                if (typeRaw == null || timeRaw == null) {
                    continue;
                }
                ChronicleEventType type = ChronicleEventType.valueOf(typeRaw);
                Instant instant = Instant.parse(timeRaw);
                List<String> witnesses = new ArrayList<>();
                Object witnessRaw = raw.get("witnesses");
                if (witnessRaw instanceof List<?> list) {
                    for (Object entry : list) {
                        if (entry != null) {
                            witnesses.add(entry.toString());
                        }
                    }
                }
                records.add(new ChronicleRecord(type, instant, text, witnesses));
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to load chronicle entry: " + ex.getMessage());
            }
        }
    }

    public void save() {
        if (config == null) {
            config = new YamlConfiguration();
        }
        List<Map<String, Object>> serialized = new ArrayList<>();
        for (ChronicleRecord record : records) {
            serialized.add(Map.of(
                    "type", record.type().name(),
                    "time", record.timestamp().toString(),
                    "text", record.text(),
                    "witnesses", record.witnesses()
            ));
        }
        config.set("entries", serialized);
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save chronicle: " + e.getMessage());
        }
    }

    public synchronized void logEvent(ChronicleEventType type, String text, Collection<Player> witnesses) {
        if (text == null || text.isBlank()) {
            text = messages.format("chronicle.default." + type.name().toLowerCase(Locale.ROOT));
        }
        List<String> witnessNames = new ArrayList<>();
        if (witnesses != null) {
            for (Player player : witnesses) {
                witnessNames.add(player.getName());
                player.addScoreboardTag("myth_witness");
            }
        }
        ChronicleRecord record = new ChronicleRecord(type, Instant.now(), text, witnessNames);
        records.add(record);
        plugin.getLogger().info("[Chronicle] " + text);
        save();
    }

    public List<String> formatRecent(int limit) {
        if (records.isEmpty()) {
            return List.of(messages.format("chronicle.empty"));
        }
        int start = Math.max(0, records.size() - limit);
        List<String> formatted = new ArrayList<>();
        for (int i = records.size() - 1; i >= start; i--) {
            ChronicleRecord record = records.get(i);
            formatted.add(FORMATTER.format(record.timestamp()) + " · " + record.text());
        }
        return formatted;
    }

    public List<ChronicleRecord> getRecords() {
        return Collections.unmodifiableList(records);
    }

    public record ChronicleRecord(ChronicleEventType type, Instant timestamp, String text, List<String> witnesses) {}
}
