package me.j17e4eo.mythof5.config;

import me.j17e4eo.mythof5.Mythof5;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Messages {

    private final Mythof5 plugin;
    private final File file;
    private YamlConfiguration configuration;
    private final Set<String> missingKeys = new HashSet<>();

    public Messages(Mythof5 plugin) {
        this.plugin = plugin;
        plugin.saveResource("messages.yml", false);
        this.file = new File(plugin.getDataFolder(), "messages.yml");
        reload();
    }

    public void reload() {
        this.configuration = YamlConfiguration.loadConfiguration(file);
    }

    public String format(String path) {
        return format(path, Collections.emptyMap());
    }

    public String format(String path, Map<String, String> placeholders) {
        String message = configuration.getString(path);
        if (message == null) {
            warnMissing(path);
            message = path;
        }
        return apply(placeholders, message);
    }

    public List<String> formatList(String path, Map<String, String> placeholders) {
        List<String> list;
        if (configuration.isList(path)) {
            list = configuration.getStringList(path);
        } else {
            String single = configuration.getString(path);
            if (single == null) {
                warnMissing(path);
                return List.of(path);
            }
            list = List.of(single);
        }
        List<String> result = new ArrayList<>(list.size());
        for (String line : list) {
            result.add(apply(placeholders, line));
        }
        return result;
    }

    private String apply(Map<String, String> placeholders, String text) {
        if (placeholders == null || placeholders.isEmpty()) {
            return text;
        }
        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private void warnMissing(String path) {
        if (missingKeys.add(path)) {
            plugin.getLogger().warning("Missing message key: " + path);
        }
    }

    public void saveIfModified() {
        try {
            configuration.save(file);
        } catch (IOException ignored) {
            // Message file should not normally be saved at runtime, ignore.
        }
    }
}
