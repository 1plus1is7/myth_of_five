package me.j17e4eo.mythof5.hunter.seal.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent seal information for a player.
 */
public class PlayerSealProfile {

    private final UUID playerId;
    private final Map<UUID, WeaponSeal> seals = new LinkedHashMap<>();
    private final LinkedHashMap<Long, String> recentLogs = new LinkedHashMap<>();

    public PlayerSealProfile(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Map<UUID, WeaponSeal> getSeals() {
        return Collections.unmodifiableMap(seals);
    }

    public void putSeal(WeaponSeal seal) {
        seals.put(seal.getWeaponId(), seal);
    }

    public WeaponSeal removeSeal(UUID weaponId) {
        return seals.remove(weaponId);
    }

    public WeaponSeal getSeal(UUID weaponId) {
        return seals.get(weaponId);
    }

    public List<Map.Entry<Long, String>> getRecentLogs() {
        return List.copyOf(recentLogs.entrySet());
    }

    public void log(long timestamp, String message) {
        recentLogs.put(timestamp, message);
        while (recentLogs.size() > 20) {
            Long first = recentLogs.keySet().iterator().next();
            recentLogs.remove(first);
        }
    }

    public Map<String, Object> serialize() {
        Map<String, Object> data = new LinkedHashMap<>();
        Map<String, Object> sealed = new LinkedHashMap<>();
        for (WeaponSeal seal : seals.values()) {
            sealed.put(seal.getWeaponId().toString(), seal.serialize());
        }
        data.put("seals", sealed);
        Map<String, Object> logs = new LinkedHashMap<>();
        for (Map.Entry<Long, String> entry : recentLogs.entrySet()) {
            logs.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        data.put("logs", logs);
        return data;
    }

    public static PlayerSealProfile deserialize(UUID playerId, Map<?, ?> raw) {
        PlayerSealProfile profile = new PlayerSealProfile(playerId);
        if (raw == null) {
            return profile;
        }
        Object sealsRaw = raw.get("seals");
        if (sealsRaw instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> nested)) {
                    continue;
                }
                WeaponSeal seal = WeaponSeal.deserialize(nested);
                if (seal != null) {
                    profile.putSeal(seal);
                }
            }
        }
        Object logsRaw = raw.get("logs");
        if (logsRaw instanceof Map<?, ?> logMap) {
            for (Map.Entry<?, ?> entry : logMap.entrySet()) {
                try {
                    long time = Long.parseLong(String.valueOf(entry.getKey()));
                    String message = String.valueOf(entry.getValue());
                    profile.recentLogs.put(time, message);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return profile;
    }
}
