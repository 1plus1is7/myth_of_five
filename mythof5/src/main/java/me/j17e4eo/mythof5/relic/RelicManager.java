package me.j17e4eo.mythof5.relic;

import me.j17e4eo.mythof5.Mythof5;
import me.j17e4eo.mythof5.chronicle.ChronicleEventType;
import me.j17e4eo.mythof5.chronicle.ChronicleManager;
import me.j17e4eo.mythof5.config.Messages;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages relic ownership, granting and fusion logic.
 */
public class RelicManager {

    private final Mythof5 plugin;
    private final Messages messages;
    private final ChronicleManager chronicleManager;
    private final Map<UUID, Set<RelicType>> relics = new HashMap<>();
    private final List<RelicFusion> fusions = new ArrayList<>();
    private File dataFile;
    private YamlConfiguration dataConfig;

    public RelicManager(Mythof5 plugin, Messages messages, ChronicleManager chronicleManager) {
        this.plugin = plugin;
        this.messages = messages;
        this.chronicleManager = chronicleManager;
        fusions.add(new RelicFusion(RelicType.TRICK_AND_BIND,
                "환영과 포획이 동시에 발현되는 강력한 설화",
                RelicType.MANGTAE_HALABEOM, RelicType.DUNGAP_MOUSE));
    }

    public void load() {
        plugin.getDataFolder().mkdirs();
        dataFile = new File(plugin.getDataFolder(), "relics.yml");
        if (!dataFile.exists()) {
            dataConfig = new YamlConfiguration();
            return;
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        relics.clear();
        for (String key : dataConfig.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Invalid player UUID in relics.yml: " + key);
                continue;
            }
            List<String> entries = dataConfig.getStringList(key);
            Set<RelicType> set = new HashSet<>();
            for (String entry : entries) {
                RelicType type = RelicType.fromKey(entry);
                if (type != null) {
                    set.add(type);
                }
            }
            relics.put(uuid, set);
        }
    }

    public void save() {
        if (dataConfig == null) {
            dataConfig = new YamlConfiguration();
        }
        for (Map.Entry<UUID, Set<RelicType>> entry : relics.entrySet()) {
            List<String> serialized = new ArrayList<>();
            for (RelicType type : entry.getValue()) {
                serialized.add(type.getKey());
            }
            dataConfig.set(entry.getKey().toString(), serialized);
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save relics.yml: " + e.getMessage());
        }
    }

    public boolean grantRelic(Player player, RelicType type, boolean announce) {
        Set<RelicType> owned = relics.computeIfAbsent(player.getUniqueId(), key -> new HashSet<>());
        if (!owned.add(type)) {
            return false;
        }
        if (announce) {
            player.sendMessage(messages.format("relic.obtain.self", Map.of(
                    "relic", type.getDisplayName(),
                    "effect", type.getEffect()
            )));
            plugin.broadcast(messages.format("relic.obtain.broadcast", Map.of(
                    "player", player.getName(),
                    "relic", type.getDisplayName()
            )));
        }
        chronicleManager.logEvent(ChronicleEventType.RELIC_GAIN,
                messages.format("chronicle.relic.obtain", Map.of(
                        "player", player.getName(),
                        "relic", type.getDisplayName()
                )), List.of(player));
        checkFusions(player, owned);
        save();
        return true;
    }

    public boolean removeRelic(Player player, RelicType type) {
        Set<RelicType> owned = relics.get(player.getUniqueId());
        if (owned == null) {
            return false;
        }
        boolean removed = owned.remove(type);
        if (removed) {
            save();
        }
        return removed;
    }

    public Set<RelicType> getRelics(UUID uuid) {
        return relics.containsKey(uuid) ? Collections.unmodifiableSet(relics.get(uuid)) : Collections.emptySet();
    }

    public List<RelicFusion> getFusions() {
        return Collections.unmodifiableList(fusions);
    }

    public List<String> describeRelics(UUID uuid) {
        Set<RelicType> owned = relics.get(uuid);
        if (owned == null || owned.isEmpty()) {
            return List.of(messages.format("relic.none"));
        }
        List<String> result = new ArrayList<>();
        for (RelicType type : owned) {
            result.add("• " + type.getDisplayName() + " - " + type.getEffect());
        }
        return result;
    }

    private void checkFusions(Player player, Set<RelicType> owned) {
        for (RelicFusion fusion : fusions) {
            if (!owned.contains(fusion.getResult()) && fusion.matches(owned)) {
                owned.add(fusion.getResult());
                player.sendMessage(messages.format("relic.fusion.self", Map.of(
                        "relic", fusion.getResult().getDisplayName(),
                        "desc", fusion.getDescription()
                )));
                plugin.broadcast(messages.format("relic.fusion.broadcast", Map.of(
                        "player", player.getName(),
                        "relic", fusion.getResult().getDisplayName()
                )));
                chronicleManager.logEvent(ChronicleEventType.RELIC_FUSION,
                        messages.format("chronicle.relic.fusion", Map.of(
                                "player", player.getName(),
                                "relic", fusion.getResult().getDisplayName()
                        )), List.of(player));
            }
        }
    }
}
