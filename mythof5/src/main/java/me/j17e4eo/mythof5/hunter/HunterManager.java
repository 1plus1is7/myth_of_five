package me.j17e4eo.mythof5.hunter;

import me.j17e4eo.mythof5.Mythof5;
import me.j17e4eo.mythof5.chronicle.ChronicleEventType;
import me.j17e4eo.mythof5.chronicle.ChronicleManager;
import me.j17e4eo.mythof5.config.Messages;
import me.j17e4eo.mythof5.hunter.data.Artifact;
import me.j17e4eo.mythof5.hunter.data.ArtifactAbility;
import me.j17e4eo.mythof5.hunter.data.ArtifactGrade;
import me.j17e4eo.mythof5.hunter.data.ArtifactOrigin;
import me.j17e4eo.mythof5.hunter.data.ArtifactState;
import me.j17e4eo.mythof5.hunter.data.ArtifactType;
import me.j17e4eo.mythof5.hunter.data.HunterOmenStage;
import me.j17e4eo.mythof5.hunter.data.HunterProfile;
import me.j17e4eo.mythof5.hunter.data.SealLogEntry;
import me.j17e4eo.mythof5.inherit.AspectManager;
import me.j17e4eo.mythof5.inherit.InheritManager;
import me.j17e4eo.mythof5.hunter.event.HunterReleaseEvent;
import me.j17e4eo.mythof5.hunter.math.SealMath;
import me.j17e4eo.mythof5.hunter.test.HunterTestHook;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Runtime manager for the hunter route system.
 */
public class HunterManager {

    private final Mythof5 plugin;
    private final Messages messages;
    private final ChronicleManager chronicleManager;
    private final SealMath sealMath;
    private final Map<ArtifactGrade, Double> gaugeOverrides = new EnumMap<>(ArtifactGrade.class);
    private final Map<UUID, HunterProfile> profiles = new HashMap<>();
    private final Map<UUID, HunterTestHook> testHooks = new LinkedHashMap<>();
    private final Random random = new Random();
    private final double sealPatchValue;
    private final double deathIntegrityDecay;
    private final double witnessRadius;
    private final long broadcastCooldownMillis;
    private final int longThreshold;
    private final int mediumThreshold;
    private final int lateThreshold;
    private File dataFile;
    private YamlConfiguration dataConfig;
    private long lastBroadcast;
    private ParadoxManager paradoxManager;
    private InheritManager inheritManager;
    private AspectManager aspectManager;

    public HunterManager(Mythof5 plugin, Messages messages, ChronicleManager chronicleManager, SealMath sealMath,
                         double sealPatchValue, double deathIntegrityDecay, double witnessRadius,
                         long broadcastCooldownMillis, int longThreshold, int mediumThreshold, int lateThreshold,
                         Map<ArtifactGrade, Double> gaugeOverrides) {
        this.plugin = plugin;
        this.messages = messages;
        this.chronicleManager = chronicleManager;
        this.sealMath = sealMath;
        this.sealPatchValue = sealPatchValue;
        this.deathIntegrityDecay = deathIntegrityDecay;
        this.witnessRadius = witnessRadius;
        this.broadcastCooldownMillis = broadcastCooldownMillis;
        this.longThreshold = longThreshold;
        this.mediumThreshold = mediumThreshold;
        this.lateThreshold = lateThreshold;
        this.gaugeOverrides.putAll(gaugeOverrides);
    }

    public void setParadoxManager(ParadoxManager paradoxManager) {
        this.paradoxManager = paradoxManager;
    }

    public void setInheritManager(InheritManager inheritManager) {
        this.inheritManager = inheritManager;
    }

    public void setAspectManager(AspectManager aspectManager) {
        this.aspectManager = aspectManager;
    }

    public void load() {
        plugin.getDataFolder().mkdirs();
        dataFile = new File(plugin.getDataFolder(), "hunter.yml");
        if (!dataFile.exists()) {
            dataConfig = new YamlConfiguration();
            return;
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        profiles.clear();
        ConfigurationSection huntersSection = dataConfig.getConfigurationSection("hunters");
        if (huntersSection != null) {
            for (String key : huntersSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    ConfigurationSection section = huntersSection.getConfigurationSection(key);
                    if (section == null) {
                        continue;
                    }
                    Map<String, Object> raw = new HashMap<>();
                    for (String child : section.getKeys(false)) {
                        raw.put(child, section.get(child));
                    }
                    HunterProfile profile = HunterProfile.deserialize(uuid, raw);
                    if (profile != null) {
                        profiles.put(uuid, profile);
                    }
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Invalid hunter UUID: " + key);
                }
            }
        }
        testHooks.clear();
        Object hooksRaw = dataConfig.get("test_hooks");
        if (hooksRaw instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> map) {
                    HunterTestHook hook = HunterTestHook.deserialize(map);
                    if (hook != null) {
                        testHooks.put(hook.getId(), hook);
                    }
                }
            }
        }
    }

    public void save() {
        if (dataConfig == null) {
            dataConfig = new YamlConfiguration();
        }
        dataConfig.set("hunters", null);
        ConfigurationSection huntersSection = dataConfig.createSection("hunters");
        for (Map.Entry<UUID, HunterProfile> entry : profiles.entrySet()) {
            ConfigurationSection section = huntersSection.createSection(entry.getKey().toString());
            Map<String, Object> serialized = entry.getValue().serialize();
            for (Map.Entry<String, Object> line : serialized.entrySet()) {
                section.set(line.getKey(), line.getValue());
            }
        }
        List<Map<String, Object>> hooks = new ArrayList<>();
        for (HunterTestHook hook : testHooks.values()) {
            hooks.add(hook.serialize());
        }
        dataConfig.set("test_hooks", hooks);
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save hunter.yml: " + e.getMessage());
        }
    }

    public HunterProfile getProfile(Player player) {
        return profiles.computeIfAbsent(player.getUniqueId(), uuid -> new HunterProfile(uuid, player.getName()));
    }

    public Optional<HunterProfile> findProfile(UUID uuid) {
        return Optional.ofNullable(profiles.get(uuid));
    }

    public void handleJoin(Player player) {
        HunterProfile profile = getProfile(player);
        profile.setLastKnownName(player.getName());
        profile.pruneOldReleases(sealMath.getFatigueWindowMillis());
        for (Artifact artifact : profile.listArtifacts()) {
            if (artifact.isExpired()) {
                artifact.setState(ArtifactState.DESTROYED);
            }
        }
        if (profile.isEngraved()) {
            player.sendMessage(messages.format("hunter.status.header", Map.of(
                    "integrity", summarizeIntegrity(profile),
                    "artifacts", String.valueOf(profile.listArtifacts().size())
            )));
        }
    }

    private String summarizeIntegrity(HunterProfile profile) {
        double avg = 0.0D;
        int count = 0;
        for (Artifact artifact : profile.listArtifacts()) {
            avg += artifact.getIntegrity();
            count++;
        }
        if (count == 0) {
            return "0";
        }
        return String.format(Locale.KOREA, "%.1f%%", avg / count);
    }

    public boolean acceptQuest(Player player) {
        HunterProfile profile = getProfile(player);
        if (hasGoblinInheritance(player)) {
            player.sendMessage(messages.format("hunter.error.is_inheritor"));
            return false;
        }
        if (profile.isQuestAccepted()) {
            player.sendMessage(messages.format("hunter.quest.already"));
            return false;
        }
        profile.setQuestAccepted(true);
        player.sendMessage(messages.format("hunter.quest.accept"));
        chronicleManager.logEvent(ChronicleEventType.HUNTER_ENGRAVE,
                messages.format("chronicle.hunter.quest", Map.of("player", player.getName())), List.of(player));
        save();
        return true;
    }

    public boolean engrave(Player player) {
        HunterProfile profile = getProfile(player);
        if (!profile.isQuestAccepted()) {
            player.sendMessage(messages.format("hunter.quest.not_ready"));
            return false;
        }
        if (hasGoblinInheritance(player)) {
            player.sendMessage(messages.format("hunter.error.is_inheritor"));
            return false;
        }
        if (profile.isEngraved()) {
            player.sendMessage(messages.format("hunter.quest.already_hunter"));
            return false;
        }
        profile.setEngraved(true);
        player.sendMessage(messages.format("hunter.quest.complete"));
        chronicleManager.logEvent(ChronicleEventType.HUNTER_ENGRAVE,
                messages.format("chronicle.hunter.engraved", Map.of("player", player.getName())), List.of(player));
        plugin.broadcast(messages.format("broadcast.hunter_engraved", Map.of("player", player.getName())));
        save();
        return true;
    }

    public Artifact craftArtifact(Player player, ArtifactType type, ArtifactOrigin origin, ArtifactGrade grade,
                                   String name, List<ArtifactAbility> abilities, boolean borrowed, long expirySeconds) {
        HunterProfile profile = getProfile(player);
        if (!profile.isEngraved()) {
            player.sendMessage(messages.format("hunter.error.not_hunter"));
            return null;
        }
        UUID id = UUID.randomUUID();
        Artifact artifact = new Artifact(id, name, type, origin, grade);
        double gaugeGain = gaugeOverrides.getOrDefault(grade, grade.getDefaultGaugeGain());
        if (abilities == null || abilities.isEmpty()) {
            artifact.addAbility(new ArtifactAbility("surge", messages.format("hunter.default_ability"),
                    messages.format("hunter.default_ability_desc", Map.of("name", name)), gaugeGain, 20, List.of("burst")));
        } else {
            for (ArtifactAbility ability : abilities) {
                artifact.addAbility(ability);
            }
        }
        if (borrowed) {
            artifact.setBorrowedFrom(player.getUniqueId());
        }
        if (expirySeconds > 0) {
            artifact.setExpiryTimestamp(Instant.now().getEpochSecond() + expirySeconds);
        }
        profile.addArtifact(artifact);
        updateProgress(profile);
        chronicleManager.logEvent(ChronicleEventType.HUNTER_ARTIFACT,
                messages.format("chronicle.hunter.artifact", Map.of(
                        "player", player.getName(),
                        "name", name,
                        "origin", origin.name()
                )), List.of(player));
        player.sendMessage(messages.format("hunter.artifact.crafted", Map.of(
                "name", name,
                "grade", grade.name(),
                "type", type.name()
        )));
        save();
        return artifact;
    }

    public UseResult useAbility(Player player, Artifact artifact, ArtifactAbility ability) {
        HunterProfile profile = getProfile(player);
        if (!profile.isEngraved()) {
            player.sendMessage(messages.format("hunter.error.not_hunter"));
            return UseResult.error();
        }
        if (artifact.getState() == ArtifactState.BROKEN) {
            player.sendMessage(messages.format("hunter.error.broken"));
            return UseResult.error();
        }
        double before = artifact.getIntegrity();
        double gain = ability.getGaugeGain();
        double after = Math.min(100.0D, before + gain);
        artifact.setIntegrity(after);
        artifact.setState(sealMath.evaluateState(after, artifact.getState()));
        double fatigueModifier = computeFatigueModifier(profile);
        double chance = sealMath.computeChance(after, fatigueModifier);
        double roll = random.nextDouble();
        boolean release = roll <= chance;
        SealLogEntry entry = new SealLogEntry(Instant.now(), before, release ? 100.0D : after,
                "ABILITY:" + ability.getKey(), roll, release);
        artifact.addHistory(entry);
        if (release) {
            handleRelease(player, profile, artifact, chance, roll, entry);
        }
        save();
        return new UseResult(after, chance, roll, release, fatigueModifier);
    }

    private void handleRelease(Player player, HunterProfile profile, Artifact artifact, double chance, double roll, SealLogEntry entry) {
        artifact.setState(ArtifactState.BROKEN);
        artifact.incrementReleaseCount();
        artifact.setLastRelease(System.currentTimeMillis());
        profile.recordRelease(System.currentTimeMillis());
        updateProgress(profile);
        Collection<Player> witnesses = collectWitnesses(player.getLocation());
        if (witnesses.isEmpty()) {
            witnesses = List.of(player);
        }
        for (Player witness : witnesses) {
            witness.addScoreboardTag("hunter_witness");
        }
        long now = System.currentTimeMillis();
        if (now - lastBroadcast >= broadcastCooldownMillis) {
            plugin.broadcast(messages.format("broadcast.hunter_release", Map.of(
                    "player", player.getName(),
                    "name", artifact.getName(),
                    "world", player.getWorld().getName()
            )));
            lastBroadcast = now;
        }
        if (artifact.isPlayerLore()) {
            chronicleManager.logEvent(ChronicleEventType.HUNTER_BURNED,
                    messages.format("chronicle.hunter.burned", Map.of("player", player.getName())), witnesses);
        } else {
            chronicleManager.logEvent(ChronicleEventType.HUNTER_RELEASE,
                    messages.format("chronicle.hunter.release", Map.of(
                            "player", player.getName(),
                            "artifact", artifact.getName()
                    )), witnesses);
        }
        Bukkit.getPluginManager().callEvent(new HunterReleaseEvent(player, artifact, chance, roll, entry));
    }

    private Collection<Player> collectWitnesses(Location location) {
        List<Player> witnesses = new ArrayList<>();
        World world = location.getWorld();
        if (world == null) {
            return witnesses;
        }
        double squared = witnessRadius * witnessRadius;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(world)) {
                continue;
            }
            if (player.getLocation().distanceSquared(location) <= squared) {
                witnesses.add(player);
            }
        }
        return witnesses;
    }

    private double computeFatigueModifier(HunterProfile profile) {
        profile.pruneOldReleases(sealMath.getFatigueWindowMillis());
        int count = profile.getRecentReleases().size();
        double modifier = 1.1 - 0.1 * count;
        if (count == 0) {
            modifier = 1.15;
        }
        return sealMath.clampFatigue(modifier);
    }

    private void updateProgress(HunterProfile profile) {
        int mythic = profile.countMythicArtifacts();
        profile.setParadoxProgress(mythic);
        HunterOmenStage stage = HunterOmenStage.CALM;
        if (mythic >= lateThreshold) {
            stage = HunterOmenStage.LATE;
        } else if (mythic >= mediumThreshold) {
            stage = HunterOmenStage.MEDIUM;
        } else if (mythic >= longThreshold) {
            stage = HunterOmenStage.LONG;
        }
        profile.setOmenStage(stage);
    }

    public double applySealPatch(Player player, Artifact artifact, double amount) {
        if (artifact.getState() == ArtifactState.BROKEN) {
            player.sendMessage(messages.format("hunter.error.patch_broken"));
            return artifact.getIntegrity();
        }
        double before = artifact.getIntegrity();
        double after = Math.max(0.0D, before - amount);
        artifact.setIntegrity(after);
        artifact.setState(sealMath.evaluateState(after, artifact.getState()));
        artifact.addHistory(new SealLogEntry(Instant.now(), before, after, "PATCH", 0.0D, false));
        save();
        return after;
    }

    public double applyDefaultPatch(Player player, Artifact artifact) {
        return applySealPatch(player, artifact, sealPatchValue);
    }

    public boolean rebindArtifact(Player player, Artifact artifact) {
        if (artifact.getState() != ArtifactState.BROKEN) {
            player.sendMessage(messages.format("hunter.error.not_broken"));
            return false;
        }
        artifact.setState(ArtifactState.SEALED);
        artifact.setIntegrity(0.0D);
        artifact.addHistory(new SealLogEntry(Instant.now(), 100.0D, 0.0D, "REBIND", 0.0D, false));
        player.sendMessage(messages.format("hunter.artifact.resealed", Map.of("name", artifact.getName())));
        save();
        return true;
    }

    public void handleDeath(Player player) {
        HunterProfile profile = profiles.get(player.getUniqueId());
        if (profile == null) {
            return;
        }
        boolean modified = false;
        for (Artifact artifact : profile.listArtifacts()) {
            double before = artifact.getIntegrity();
            if (artifact.isPlayerLore()) {
                artifact.setState(ArtifactState.DESTROYED);
                artifact.setIntegrity(0.0D);
                modified = true;
                continue;
            }
            double after = Math.max(0.0D, before - deathIntegrityDecay);
            artifact.setIntegrity(after);
            artifact.setState(sealMath.evaluateState(after, artifact.getState()));
            artifact.addHistory(new SealLogEntry(Instant.now(), before, after, "DEATH", 0.0D, false));
            if (after != before) {
                modified = true;
            }
        }
        if (modified) {
            player.sendMessage(messages.format("hunter.death.penalty"));
            save();
        }
    }

    public boolean forceRelease(Player target, Artifact artifact) {
        if (artifact.getState() == ArtifactState.BROKEN) {
            return false;
        }
        SealLogEntry entry = new SealLogEntry(Instant.now(), artifact.getIntegrity(), 100.0D, "FORCE", 0.0D, true);
        artifact.addHistory(entry);
        handleRelease(target, getProfile(target), artifact, 1.0D, 0.0D, entry);
        save();
        return true;
    }

    public List<String> describeProfile(HunterProfile profile) {
        List<String> lines = new ArrayList<>();
        lines.add(messages.format("hunter.status.summary", Map.of(
                "name", profile.getLastKnownName(),
                "engraved", profile.isEngraved() ? messages.format("hunter.status.engraved") : messages.format("hunter.status.not_engraved"),
                "omen", profile.getOmenStage().name(),
                "paradox", String.valueOf(profile.getParadoxProgress())
        )));
        for (Artifact artifact : profile.listArtifacts()) {
            lines.add(messages.format("hunter.status.artifact", Map.of(
                    "name", artifact.getName(),
                    "grade", artifact.getGrade().name(),
                    "state", artifact.getState().name(),
                    "integrity", String.format(Locale.KOREA, "%.1f%%", artifact.getIntegrity())
            )));
            for (ArtifactAbility ability : artifact.getAbilities()) {
                lines.add(messages.format("hunter.status.ability", Map.of(
                        "ability", ability.getName(),
                        "key", ability.getKey(),
                        "gauge", String.format(Locale.KOREA, "%.1f", ability.getGaugeGain()),
                        "cooldown", String.valueOf(ability.getCooldownSeconds())
                )));
            }
        }
        return lines;
    }

    public Optional<Artifact> findArtifact(HunterProfile profile, String query) {
        if (profile == null || query == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(profile.findArtifactByName(query));
    }

    public HunterTestHook createTestHook(String name, Map<String, Object> params, String creator) {
        HunterTestHook hook = new HunterTestHook(UUID.randomUUID(), name, params, creator, Instant.now());
        testHooks.put(hook.getId(), hook);
        save();
        return hook;
    }

    public List<HunterTestHook> listTestHooks() {
        return List.copyOf(testHooks.values());
    }

    public void removeTestHook(UUID id) {
        testHooks.remove(id);
        save();
    }

    public ParadoxManager getParadoxManager() {
        return paradoxManager;
    }

    public void handleParadoxSuccess(ParadoxManager.ParadoxRitual ritual, HunterProfile profile) {
        Player player = Bukkit.getPlayer(ritual.hunterId());
        if (player != null) {
            player.sendMessage(messages.format("hunter.paradox.success"));
        }
        plugin.broadcast(messages.format("broadcast.hunter_paradox_success", Map.of("player", ritual.hunterName())));
        chronicleManager.logEvent(ChronicleEventType.HUNTER_RESET,
                messages.format("chronicle.hunter.reset_success", Map.of("player", ritual.hunterName())),
                new ArrayList<>(Bukkit.getOnlinePlayers()));
        // Reset hunter ecosystem but preserve chronicles.
        for (HunterProfile other : profiles.values()) {
            other.setQuestAccepted(false);
            other.setEngraved(false);
            other.setParadoxProgress(0);
            for (Artifact artifact : other.listArtifacts()) {
                artifact.setState(ArtifactState.DESTROYED);
            }
        }
        save();
    }

    public void handleParadoxFailure(ParadoxManager.ParadoxRitual ritual, HunterProfile profile, double failureScale) {
        Player player = Bukkit.getPlayer(ritual.hunterId());
        if (player != null) {
            player.sendMessage(messages.format("hunter.paradox.failure", Map.of("scale", String.format(Locale.KOREA, "%.1f", failureScale))));
        }
        plugin.broadcast(messages.format("broadcast.hunter_paradox_failure", Map.of("player", ritual.hunterName())));
        chronicleManager.logEvent(ChronicleEventType.HUNTER_RESET,
                messages.format("chronicle.hunter.reset_failure", Map.of("player", ritual.hunterName())),
                new ArrayList<>(Bukkit.getOnlinePlayers()));
        profile.setOmenStage(profile.getOmenStage().next());
        save();
    }

    public void shutdown() {
        if (paradoxManager != null) {
            paradoxManager.shutdown();
        }
    }

    private boolean hasGoblinInheritance(Player player) {
        UUID uuid = player.getUniqueId();
        if (inheritManager != null && inheritManager.isInheritor(uuid)) {
            return true;
        }
        return aspectManager != null && aspectManager.isInheritorOfAnyAspect(uuid);
    }

    public record UseResult(double integrity, double chance, double roll, boolean release, double fatigue) {
        public static UseResult error() {
            return new UseResult(-1, 0, 0, false, 1.0);
        }
    }
}
