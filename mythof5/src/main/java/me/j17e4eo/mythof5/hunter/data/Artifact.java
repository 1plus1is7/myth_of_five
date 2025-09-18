package me.j17e4eo.mythof5.hunter.data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a hunter artifact with integrity tracking.
 */
public class Artifact {

    private final UUID id;
    private String name;
    private ArtifactType type;
    private ArtifactOrigin origin;
    private ArtifactGrade grade;
    private double integrity;
    private ArtifactState state;
    private final Map<String, ArtifactAbility> abilities = new LinkedHashMap<>();
    private final List<SealLogEntry> history = new ArrayList<>();
    private UUID borrowedFrom;
    private long expiryTimestamp;
    private int releaseCount;
    private long lastRelease;

    public Artifact(UUID id, String name, ArtifactType type, ArtifactOrigin origin, ArtifactGrade grade) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.origin = origin;
        this.grade = grade;
        this.integrity = 0.0D;
        this.state = ArtifactState.STABLE;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ArtifactType getType() {
        return type;
    }

    public void setType(ArtifactType type) {
        this.type = type;
    }

    public ArtifactOrigin getOrigin() {
        return origin;
    }

    public void setOrigin(ArtifactOrigin origin) {
        this.origin = origin;
    }

    public ArtifactGrade getGrade() {
        return grade;
    }

    public void setGrade(ArtifactGrade grade) {
        this.grade = grade;
    }

    public double getIntegrity() {
        return integrity;
    }

    public void setIntegrity(double integrity) {
        this.integrity = Math.max(0.0D, Math.min(100.0D, integrity));
        if (state == ArtifactState.BROKEN || state == ArtifactState.DESTROYED) {
            return;
        }
        this.state = ArtifactState.fromIntegrity(this.integrity);
    }

    public ArtifactState getState() {
        return state;
    }

    public void setState(ArtifactState state) {
        this.state = state;
    }

    public UUID getBorrowedFrom() {
        return borrowedFrom;
    }

    public void setBorrowedFrom(UUID borrowedFrom) {
        this.borrowedFrom = borrowedFrom;
    }

    public long getExpiryTimestamp() {
        return expiryTimestamp;
    }

    public void setExpiryTimestamp(long expiryTimestamp) {
        this.expiryTimestamp = expiryTimestamp;
    }

    public boolean isExpired() {
        return expiryTimestamp > 0 && Instant.now().getEpochSecond() >= expiryTimestamp;
    }

    public int getReleaseCount() {
        return releaseCount;
    }

    public void incrementReleaseCount() {
        this.releaseCount++;
    }

    public long getLastRelease() {
        return lastRelease;
    }

    public void setLastRelease(long lastRelease) {
        this.lastRelease = lastRelease;
    }

    public void addAbility(ArtifactAbility ability) {
        abilities.put(ability.getKey(), ability);
    }

    public ArtifactAbility getAbility(String key) {
        if (key == null) {
            return null;
        }
        return abilities.get(key.toLowerCase(Locale.ROOT));
    }

    public List<ArtifactAbility> getAbilities() {
        return List.copyOf(abilities.values());
    }

    public void addHistory(SealLogEntry entry) {
        history.add(entry);
        if (history.size() > 50) {
            history.remove(0);
        }
    }

    public List<SealLogEntry> getHistory() {
        return List.copyOf(history);
    }

    public boolean isPlayerLore() {
        return origin == ArtifactOrigin.PLAYER_LORE;
    }

    public boolean isMythic() {
        return origin == ArtifactOrigin.MYTHIC;
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", name);
        map.put("type", type.name());
        map.put("origin", origin.name());
        map.put("grade", grade.name());
        map.put("integrity", integrity);
        map.put("state", state.name());
        map.put("release_count", releaseCount);
        map.put("last_release", lastRelease);
        if (borrowedFrom != null) {
            map.put("borrowed_from", borrowedFrom.toString());
        }
        if (expiryTimestamp > 0) {
            map.put("expiry", expiryTimestamp);
        }
        List<Map<String, Object>> abilityList = new ArrayList<>();
        for (ArtifactAbility ability : abilities.values()) {
            abilityList.add(ability.serialize());
        }
        map.put("abilities", abilityList);
        List<Map<String, Object>> logs = new ArrayList<>();
        for (SealLogEntry entry : history) {
            logs.add(entry.serialize());
        }
        map.put("history", logs);
        return map;
    }

    public static Artifact deserialize(UUID id, Map<?, ?> raw) {
        if (raw == null) {
            return null;
        }
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                data.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            String name = String.valueOf(data.getOrDefault("name", "무명 매개체"));
            ArtifactType type = ArtifactType.fromKey(String.valueOf(data.get("type")));
            ArtifactOrigin origin = ArtifactOrigin.fromKey(String.valueOf(data.get("origin")));
            ArtifactGrade grade = ArtifactGrade.fromKey(String.valueOf(data.get("grade")));
            Artifact artifact = new Artifact(id, name, type, origin, grade);
            if (data.get("integrity") != null) {
                artifact.integrity = Double.parseDouble(String.valueOf(data.get("integrity")));
            }
            String stateRaw = String.valueOf(data.getOrDefault("state", ArtifactState.STABLE.name()));
            try {
                artifact.state = ArtifactState.valueOf(stateRaw);
            } catch (IllegalArgumentException ex) {
                artifact.state = ArtifactState.STABLE;
            }
            artifact.releaseCount = Integer.parseInt(String.valueOf(data.getOrDefault("release_count", 0)));
            artifact.lastRelease = Long.parseLong(String.valueOf(data.getOrDefault("last_release", 0)));
            if (data.get("borrowed_from") != null) {
                artifact.borrowedFrom = UUID.fromString(String.valueOf(data.get("borrowed_from")));
            }
            if (data.get("expiry") != null) {
                artifact.expiryTimestamp = Long.parseLong(String.valueOf(data.get("expiry")));
            }
            Object abilitiesRaw = data.get("abilities");
            if (abilitiesRaw instanceof List<?> list) {
                for (Object entry : list) {
                    if (entry instanceof Map<?, ?> abilityMap) {
                        ArtifactAbility ability = ArtifactAbility.deserialize(abilityMap);
                        if (ability != null) {
                            artifact.addAbility(ability);
                        }
                    }
                }
            }
            Object historyRaw = data.get("history");
            if (historyRaw instanceof List<?> list) {
                for (Object entry : list) {
                    if (entry instanceof Map<?, ?> logMap) {
                        SealLogEntry log = SealLogEntry.deserialize(logMap);
                        if (log != null) {
                            artifact.history.add(log);
                        }
                    }
                }
            }
            return artifact;
        } catch (Exception ex) {
            return null;
        }
    }
}
