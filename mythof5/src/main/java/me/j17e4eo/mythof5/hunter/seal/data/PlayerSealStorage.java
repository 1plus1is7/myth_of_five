package me.j17e4eo.mythof5.hunter.seal.data;

import me.j17e4eo.mythof5.Mythof5;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * File-backed storage for player seal profiles.
 */
public class PlayerSealStorage {

    private final Mythof5 plugin;
    private final File directory;
    private final Map<UUID, PlayerSealProfile> profiles = new HashMap<>();

    public PlayerSealStorage(Mythof5 plugin) {
        this.plugin = plugin;
        this.directory = new File(plugin.getDataFolder(), "seals");
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    public PlayerSealProfile getProfile(UUID playerId) {
        return profiles.computeIfAbsent(playerId, this::loadProfile);
    }

    public void unload(UUID playerId) {
        PlayerSealProfile profile = profiles.remove(playerId);
        if (profile != null) {
            saveProfile(profile);
        }
    }

    public void saveAll() {
        for (PlayerSealProfile profile : profiles.values()) {
            saveProfile(profile);
        }
    }

    private PlayerSealProfile loadProfile(UUID playerId) {
        File file = new File(directory, playerId + ".yml");
        if (!file.exists()) {
            return new PlayerSealProfile(playerId);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        Map<String, Object> raw = config.getValues(false);
        return PlayerSealProfile.deserialize(playerId, raw);
    }

    public void saveProfile(PlayerSealProfile profile) {
        File file = new File(directory, profile.getPlayerId() + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        Map<String, Object> serialized = profile.serialize();
        for (Map.Entry<String, Object> entry : serialized.entrySet()) {
            config.set(entry.getKey(), entry.getValue());
        }
        try {
            File temp = File.createTempFile("seal", ".yml", directory);
            config.save(temp);
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save seal profile for " + profile.getPlayerId() + ": " + e.getMessage());
        }
    }
}
