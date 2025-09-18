package me.j17e4eo.mythof5.hunter.test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lightweight container for QA-controlled hunter simulations.
 */
public class HunterTestHook {

    private final UUID id;
    private final String name;
    private final Map<String, Object> parameters;
    private final String createdBy;
    private final Instant createdAt;
    private boolean active;

    public HunterTestHook(UUID id, String name, Map<String, Object> parameters, String createdBy, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.parameters = parameters == null ? new HashMap<>() : new HashMap<>(parameters);
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.active = true;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getParameters() {
        return Map.copyOf(parameters);
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id.toString());
        map.put("name", name);
        map.put("params", new HashMap<>(parameters));
        map.put("created_by", createdBy);
        map.put("created_at", createdAt.toString());
        map.put("active", active);
        return map;
    }

    public static HunterTestHook deserialize(Map<?, ?> raw) {
        if (raw == null) {
            return null;
        }
        try {
            Map<String, Object> data = new HashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                data.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            UUID id = UUID.fromString(String.valueOf(data.get("id")));
            String name = String.valueOf(data.getOrDefault("name", "hook"));
            Map<String, Object> params = new HashMap<>();
            Object rawParams = data.get("params");
            if (rawParams instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) {
                        params.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
            }
            String createdBy = String.valueOf(data.getOrDefault("created_by", "system"));
            Instant createdAt = Instant.parse(String.valueOf(data.getOrDefault("created_at", Instant.now().toString())));
            HunterTestHook hook = new HunterTestHook(id, name, params, createdBy, createdAt);
            hook.active = Boolean.parseBoolean(String.valueOf(data.getOrDefault("active", true)));
            return hook;
        } catch (Exception ex) {
            return null;
        }
    }
}
