package me.j17e4eo.mythof5.hunter.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Describes an active ability bound to an artifact.
 */
public class ArtifactAbility {

    private final String key;
    private final String name;
    private final String description;
    private final double gaugeGain;
    private final int cooldownSeconds;
    private final List<String> tags;

    public ArtifactAbility(String key, String name, String description, double gaugeGain,
                           int cooldownSeconds, List<String> tags) {
        this.key = key.toLowerCase(Locale.ROOT);
        this.name = name;
        this.description = description;
        this.gaugeGain = gaugeGain;
        this.cooldownSeconds = cooldownSeconds;
        this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
    }

    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public double getGaugeGain() {
        return gaugeGain;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public List<String> getTags() {
        return List.copyOf(tags);
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("key", key);
        map.put("name", name);
        map.put("description", description);
        map.put("gauge", gaugeGain);
        map.put("cooldown", cooldownSeconds);
        map.put("tags", new ArrayList<>(tags));
        return map;
    }

    public static ArtifactAbility deserialize(Map<?, ?> raw) {
        if (raw == null) {
            return null;
        }
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                map.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            String key = String.valueOf(map.get("key"));
            String name = String.valueOf(map.getOrDefault("name", key));
            String desc = String.valueOf(map.getOrDefault("description", ""));
            double gauge = Double.parseDouble(String.valueOf(map.getOrDefault("gauge", 7.0)));
            int cooldown = Integer.parseInt(String.valueOf(map.getOrDefault("cooldown", 20)));
            List<String> tags = new ArrayList<>();
            Object rawTags = map.get("tags");
            if (rawTags instanceof List<?> list) {
                for (Object entry : list) {
                    if (entry != null) {
                        tags.add(entry.toString());
                    }
                }
            }
            return new ArtifactAbility(key, name, desc, gauge, cooldown, tags);
        } catch (Exception ex) {
            return null;
        }
    }
}
