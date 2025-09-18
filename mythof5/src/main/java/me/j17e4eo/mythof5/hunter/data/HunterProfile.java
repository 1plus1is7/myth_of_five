package me.j17e4eo.mythof5.hunter.data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent record for a hunter player.
 */
public class HunterProfile {

    private final UUID playerId;
    private String lastKnownName;
    private boolean questAccepted;
    private boolean engraved;
    private HunterOmenStage omenStage;
    private int paradoxProgress;
    private final Map<UUID, Artifact> artifacts = new LinkedHashMap<>();
    private final List<Long> recentReleases = new ArrayList<>();

    public HunterProfile(UUID playerId, String lastKnownName) {
        this.playerId = playerId;
        this.lastKnownName = lastKnownName;
        this.omenStage = HunterOmenStage.CALM;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getLastKnownName() {
        return lastKnownName;
    }

    public void setLastKnownName(String lastKnownName) {
        this.lastKnownName = lastKnownName;
    }

    public boolean isQuestAccepted() {
        return questAccepted;
    }

    public void setQuestAccepted(boolean questAccepted) {
        this.questAccepted = questAccepted;
    }

    public boolean isEngraved() {
        return engraved;
    }

    public void setEngraved(boolean engraved) {
        this.engraved = engraved;
    }

    public HunterOmenStage getOmenStage() {
        return omenStage;
    }

    public void setOmenStage(HunterOmenStage omenStage) {
        this.omenStage = omenStage;
    }

    public int getParadoxProgress() {
        return paradoxProgress;
    }

    public void setParadoxProgress(int paradoxProgress) {
        this.paradoxProgress = Math.max(0, paradoxProgress);
    }

    public Map<UUID, Artifact> getArtifacts() {
        return Map.copyOf(artifacts);
    }

    public void addArtifact(Artifact artifact) {
        artifacts.put(artifact.getId(), artifact);
    }

    public Artifact removeArtifact(UUID id) {
        return artifacts.remove(id);
    }

    public Artifact getArtifact(UUID id) {
        return artifacts.get(id);
    }

    public List<Artifact> listArtifacts() {
        return List.copyOf(artifacts.values());
    }

    public List<Long> getRecentReleases() {
        return recentReleases;
    }

    public void recordRelease(long epochMillis) {
        recentReleases.add(epochMillis);
    }

    public Map<String, Object> serialize() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", lastKnownName);
        data.put("quest", questAccepted);
        data.put("engraved", engraved);
        data.put("omen", omenStage.name());
        data.put("paradox", paradoxProgress);
        Map<String, Object> artifactSection = new LinkedHashMap<>();
        for (Map.Entry<UUID, Artifact> entry : artifacts.entrySet()) {
            artifactSection.put(entry.getKey().toString(), entry.getValue().serialize());
        }
        data.put("artifacts", artifactSection);
        data.put("releases", new ArrayList<>(recentReleases));
        return data;
    }

    public static HunterProfile deserialize(UUID id, Map<?, ?> raw) {
        if (raw == null) {
            return null;
        }
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                data.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            String name = String.valueOf(data.getOrDefault("name", "unknown"));
            HunterProfile profile = new HunterProfile(id, name);
            profile.questAccepted = Boolean.parseBoolean(String.valueOf(data.getOrDefault("quest", false)));
            profile.engraved = Boolean.parseBoolean(String.valueOf(data.getOrDefault("engraved", false)));
            profile.omenStage = HunterOmenStage.valueOf(String.valueOf(data.getOrDefault("omen", HunterOmenStage.CALM.name())));
            profile.paradoxProgress = Integer.parseInt(String.valueOf(data.getOrDefault("paradox", 0)));
            Object artifactsRaw = data.get("artifacts");
            if (artifactsRaw instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    try {
                        UUID artifactId = UUID.fromString(String.valueOf(entry.getKey()));
                        if (entry.getValue() instanceof Map<?, ?> value) {
                            Artifact artifact = Artifact.deserialize(artifactId, value);
                            if (artifact != null) {
                                profile.addArtifact(artifact);
                            }
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            Object releases = data.get("releases");
            if (releases instanceof List<?> list) {
                for (Object entry : list) {
                    try {
                        profile.recentReleases.add(Long.parseLong(String.valueOf(entry)));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            return profile;
        } catch (Exception ex) {
            return null;
        }
    }

    public int countMythicArtifacts() {
        int count = 0;
        for (Artifact artifact : artifacts.values()) {
            if (artifact.isMythic()) {
                count++;
            }
        }
        return count;
    }

    public Artifact findArtifactByName(String query) {
        if (query == null) {
            return null;
        }
        String normalized = query.toLowerCase(Locale.ROOT);
        for (Artifact artifact : artifacts.values()) {
            if (artifact.getName().toLowerCase(Locale.ROOT).contains(normalized)) {
                return artifact;
            }
        }
        try {
            UUID id = UUID.fromString(query);
            return artifacts.get(id);
        } catch (IllegalArgumentException ignored) {
        }
        return null;
    }

    public void pruneOldReleases(long thresholdMillis) {
        long now = Instant.now().toEpochMilli();
        recentReleases.removeIf(time -> now - time > thresholdMillis);
    }
}
