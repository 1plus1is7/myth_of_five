package me.j17e4eo.mythof5.inherit;

import me.j17e4eo.mythof5.Mythof5;
import me.j17e4eo.mythof5.chronicle.ChronicleEventType;
import me.j17e4eo.mythof5.chronicle.ChronicleManager;
import me.j17e4eo.mythof5.config.Messages;
import me.j17e4eo.mythof5.inherit.aspect.GoblinAspect;
import me.j17e4eo.mythof5.inherit.aspect.GoblinSkill;
import me.j17e4eo.mythof5.inherit.aspect.GoblinSkillCategory;
import me.j17e4eo.mythof5.inherit.skill.PassiveTrigger;
import me.j17e4eo.mythof5.inherit.skill.SkillEnvironmentTag;
import me.j17e4eo.mythof5.inherit.skill.SkillSpec;
import me.j17e4eo.mythof5.inherit.skill.SkillStatusEffect;
import me.j17e4eo.mythof5.inherit.skilltree.SkillTreeManager;
import me.j17e4eo.mythof5.hunter.HunterManager;
import me.j17e4eo.mythof5.balance.BalanceTable;
import me.j17e4eo.mythof5.omens.OmenManager;
import me.j17e4eo.mythof5.omens.OmenStage;
import me.j17e4eo.mythof5.relic.RelicManager;
import me.j17e4eo.mythof5.meta.MetaEventManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.Location;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the five goblin aspects including inheritance, sharing and skill
 * execution.
 */
public class AspectManager implements Listener {

    private static final double HEALTH_PENALTY_PER_SHARE = 2.0D;

    private final Mythof5 plugin;
    private final Messages messages;
    private final ChronicleManager chronicleManager;
    private final RelicManager relicManager;
    private final OmenManager omenManager;
    private final SkillTreeManager skillTreeManager;
    private final BalanceTable balanceTable;
    private final MetaEventManager metaEventManager;
    private final Map<GoblinAspect, AspectProfile> profiles = new EnumMap<>(GoblinAspect.class);
    private final Map<GoblinAspect, NamespacedKey> healthModifierKeys = new EnumMap<>(GoblinAspect.class);
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> scentTasks = new HashMap<>();
    private final NamespacedKey legendaryWeaponKey;
    private final Set<String> powerBossKeywords;
    private final Material speedTraceEyeItem;
    private final Material speedTraceHornItem;
    private final Map<UUID, EnumSet<TraceToken>> speedProgress = new HashMap<>();
    private final Set<Material> contractMaterials;
    private final List<String> contractKeywords;
    private final Set<Material> flameRitualBlocks;
    private final Material flameCatalyst;
    private final Material forgeCatalyst;
    private final Material forgeFuel;
    private final Set<Material> forgeStations;
    private final int forgeRadius;
    private final Map<UUID, Double> originalMaxHealth = new HashMap<>();
    private final Map<UUID, Set<GoblinAspect>> activeInheritorAspects = new HashMap<>();
    private final Map<UUID, Double> pendingMaxHealthRestores = new HashMap<>();
    private final Map<GoblinAspect, Map<UUID, Long>> passiveCooldowns = new EnumMap<>(GoblinAspect.class);
    private GoblinWeaponManager weaponManager;
    private HunterManager hunterManager;
    private File dataFile;
    private YamlConfiguration dataConfig;

    public AspectManager(Mythof5 plugin, Messages messages, ChronicleManager chronicleManager,
                         RelicManager relicManager, OmenManager omenManager,
                         SkillTreeManager skillTreeManager, BalanceTable balanceTable,
                         MetaEventManager metaEventManager) {
        this.plugin = plugin;
        this.messages = messages;
        this.chronicleManager = chronicleManager;
        this.relicManager = relicManager;
        this.omenManager = omenManager;
        this.skillTreeManager = skillTreeManager;
        this.balanceTable = balanceTable;
        this.metaEventManager = metaEventManager;
        this.legendaryWeaponKey = new NamespacedKey(plugin, "legendary_summon");
        FileConfiguration config = plugin.getConfig();
        powerBossKeywords = new HashSet<>();
        for (String keyword : config.getStringList("goblin.triggers.power.boss_keywords")) {
            if (keyword != null && !keyword.isBlank()) {
                powerBossKeywords.add(keyword.toLowerCase(Locale.ROOT));
            }
        }
        if (powerBossKeywords.isEmpty()) {
            powerBossKeywords.add("태초의 도깨비".toLowerCase(Locale.ROOT));
        }

        List<String> speedItems = config.getStringList("goblin.triggers.speed.trace_items");
        speedTraceEyeItem = resolveMaterial(speedItems, 0, Material.SNOWBALL);
        speedTraceHornItem = resolveMaterial(speedItems, 1, Material.GOAT_HORN);

        contractMaterials = resolveMaterialSet(config.getStringList("goblin.triggers.mischief.contract_items"),
                EnumSet.of(Material.WRITTEN_BOOK, Material.WRITABLE_BOOK));
        contractKeywords = buildKeywordList(config.getStringList("goblin.triggers.mischief.contract_keywords"),
                List.of("계약"));

        flameRitualBlocks = resolveMaterialSet(config.getStringList("goblin.triggers.flame.ritual_blocks"),
                EnumSet.of(Material.CAMPFIRE, Material.SOUL_CAMPFIRE));
        flameCatalyst = resolveMaterial(config.getString("goblin.triggers.flame.catalyst"), Material.BLAZE_POWDER);

        forgeCatalyst = resolveMaterial(config.getString("goblin.triggers.forge.catalyst"), Material.NETHERITE_INGOT);
        forgeFuel = resolveMaterial(config.getString("goblin.triggers.forge.fuel"), Material.BLAZE_ROD);
        forgeStations = resolveMaterialSet(config.getStringList("goblin.triggers.forge.station_blocks"),
                EnumSet.of(Material.SMITHING_TABLE, Material.ANVIL));
        forgeRadius = Math.max(1, config.getInt("goblin.triggers.forge.radius", 4));
        for (GoblinAspect aspect : GoblinAspect.values()) {
            profiles.put(aspect, new AspectProfile());
            healthModifierKeys.put(aspect, new NamespacedKey(plugin, "aspect_penalty_" + aspect.getKey()));
            passiveCooldowns.put(aspect, new HashMap<>());
        }
    }

    public void setWeaponManager(GoblinWeaponManager weaponManager) {
        this.weaponManager = weaponManager;
    }

    public void setHunterManager(HunterManager hunterManager) {
        this.hunterManager = hunterManager;
    }

    public void load() {
        plugin.getDataFolder().mkdirs();
        dataFile = new File(plugin.getDataFolder(), "goblins.yml");
        if (!dataFile.exists()) {
            dataConfig = new YamlConfiguration();
            return;
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        for (GoblinAspect aspect : GoblinAspect.values()) {
            AspectProfile profile = profiles.get(aspect);
            String base = "aspects." + aspect.getKey();
            String uuid = dataConfig.getString(base + ".inheritor.uuid");
            profile.shared.clear();
            if (uuid != null && !uuid.isEmpty()) {
                try {
                    profile.inheritor = UUID.fromString(uuid);
                    profile.name = dataConfig.getString(base + ".inheritor.name");
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Invalid UUID for aspect " + aspect.getKey());
                }
            }
            List<String> sharedList = dataConfig.getStringList(base + ".shared");
            for (String shared : sharedList) {
                try {
                    profile.shared.add(UUID.fromString(shared));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Invalid shared UUID for aspect " + aspect.getKey() + ": " + shared);
                }
            }
        }
        speedProgress.clear();
        ConfigurationSection speedSection = dataConfig.getConfigurationSection("progress.speed");
        if (speedSection != null) {
            for (String key : speedSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    List<String> tokens = speedSection.getStringList(key);
                    EnumSet<TraceToken> set = EnumSet.noneOf(TraceToken.class);
                    for (String token : tokens) {
                        TraceToken traceToken = TraceToken.fromKey(token);
                        if (traceToken != null) {
                            set.add(traceToken);
                        }
                    }
                    if (!set.isEmpty()) {
                        speedProgress.put(uuid, set);
                    }
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Invalid UUID in goblin trace progress: " + key);
                }
            }
        }
    }

    public void save() {
        if (dataConfig == null) {
            dataConfig = new YamlConfiguration();
        }
        for (GoblinAspect aspect : GoblinAspect.values()) {
            AspectProfile profile = profiles.get(aspect);
            String base = "aspects." + aspect.getKey();
            if (profile.inheritor == null) {
                dataConfig.set(base, null);
                continue;
            }
            dataConfig.set(base + ".inheritor.uuid", profile.inheritor.toString());
            dataConfig.set(base + ".inheritor.name", profile.name);
            List<String> shared = new ArrayList<>(profile.shared.size());
            for (UUID uuid : profile.shared) {
                shared.add(uuid.toString());
            }
            dataConfig.set(base + ".shared", shared);
        }
        dataConfig.set("progress.speed", null);
        if (!speedProgress.isEmpty()) {
            for (Map.Entry<UUID, EnumSet<TraceToken>> entry : speedProgress.entrySet()) {
                List<String> values = new ArrayList<>();
                for (TraceToken token : entry.getValue()) {
                    values.add(token.name());
                }
                dataConfig.set("progress.speed." + entry.getKey(), values);
            }
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save goblin aspect data: " + e.getMessage());
        }
    }

    public void handlePlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();
        Double pending = pendingMaxHealthRestores.remove(uuid);
        if (pending != null) {
            AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
            if (attribute != null) {
                attribute.setBaseValue(pending);
                if (player.getHealth() > pending) {
                    player.setHealth(pending);
                }
            }
        }
        for (GoblinAspect aspect : GoblinAspect.values()) {
            AspectProfile profile = profiles.get(aspect);
            if (profile.isInheritor(uuid)) {
                applyInheritorHealth(aspect, player);
                applyPassive(aspect, player, true);
                applySharedEffects(aspect, player);
            } else if (profile.shared.contains(uuid)) {
                applyPassive(aspect, player, false);
            }
        }
        if (weaponManager != null) {
            weaponManager.handleJoin(player);
        }
        cleanupLegendaryWeapons(player.getInventory());
    }

    public void handlePlayerQuit(Player player) {
        cancelScentTask(player.getUniqueId());
        UUID uuid = player.getUniqueId();
        for (Map<UUID, Long> map : passiveCooldowns.values()) {
            map.remove(uuid);
        }
    }

    public void handleDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        boolean killerIsHunter = killer != null && hunterManager != null
                && hunterManager.findProfile(killer.getUniqueId()).map(profile -> profile.isEngraved()).orElse(false);
        boolean victimIsHunter = hunterManager != null
                && hunterManager.findProfile(victim.getUniqueId()).map(profile -> profile.isEngraved()).orElse(false);
        boolean killerIsInheritor = killer != null && isInheritorOfAnyAspect(killer.getUniqueId());
        if (victimIsHunter && killerIsInheritor) {
            balanceTable.recordBattle(false);
        }
        boolean recordedHunterWin = false;
        for (GoblinAspect aspect : GoblinAspect.values()) {
            AspectProfile profile = profiles.get(aspect);
            if (!profile.isInheritor(victim.getUniqueId())) {
                continue;
            }
            if (killerIsHunter && !recordedHunterWin) {
                balanceTable.recordBattle(true);
                recordedHunterWin = true;
            }
            if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
                setInheritor(aspect, killer, true, messages.format("chronicle.inherit.transfer", Map.of(
                        "killer", killer.getName(),
                        "victim", victim.getName(),
                        "aspect", aspect.getDisplayName()
                )));
            } else {
                clearInheritor(aspect, true, victim.getName());
            }
        }
    }

    public void handleBossDefeat(Player killer, String bossName) {
        if (killer == null) {
            return;
        }
        AspectProfile powerProfile = profiles.get(GoblinAspect.POWER);
        if (powerProfile.inheritor == null && bossName != null) {
            String normalized = bossName.toLowerCase(Locale.ROOT);
            boolean matches = false;
            for (String keyword : powerBossKeywords) {
                if (normalized.contains(keyword)) {
                    matches = true;
                    break;
                }
            }
            if (!matches) {
                return;
            }
            setInheritor(GoblinAspect.POWER, killer, true, messages.format("chronicle.inherit.obtain", Map.of(
                    "player", killer.getName(),
                    "aspect", GoblinAspect.POWER.getDisplayName(),
                    "method", "현신 격파"
            )));
            skillTreeManager.addPoints(killer.getUniqueId(), 2,
                    messages.format("goblin.skilltree.reason.boss", Map.of(
                            "boss", bossName
                    )));
        }
    }

    public void handleTracePickup(Player player, ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return;
        }
        AspectProfile profile = profiles.get(GoblinAspect.SPEED);
        UUID uuid = player.getUniqueId();
        if (profile.isInheritor(uuid)) {
            return;
        }
        if (profile.inheritor != null && !profile.inheritor.equals(uuid)) {
            return;
        }
        TraceToken token = traceTokenFromMaterial(itemStack.getType());
        if (token == null) {
            return;
        }
        EnumSet<TraceToken> progress = speedProgress.computeIfAbsent(uuid, key -> EnumSet.noneOf(TraceToken.class));
        if (!progress.add(token)) {
            return;
        }
        save();
        switch (token) {
            case EYE -> player.sendMessage(messages.format("goblin.ritual.speed.collect.eye", Map.of(
                    "item", describeMaterial(speedTraceEyeItem)
            )));
            case HORN -> player.sendMessage(messages.format("goblin.ritual.speed.collect.horn", Map.of(
                    "item", describeMaterial(speedTraceHornItem)
            )));
        }
        if (progress.containsAll(EnumSet.allOf(TraceToken.class))) {
            player.sendMessage(messages.format("goblin.ritual.speed.complete"));
            setInheritor(GoblinAspect.SPEED, player, true, messages.format("chronicle.inherit.obtain", Map.of(
                    "player", player.getName(),
                    "aspect", GoblinAspect.SPEED.getDisplayName(),
                    "method", "흔적 수집"
            )));
        } else {
            player.sendMessage(messages.format("goblin.ritual.speed.progress", Map.of(
                    "current", String.valueOf(progress.size()),
                    "total", String.valueOf(EnumSet.allOf(TraceToken.class).size())
            )));
        }
    }

    public void attemptMischiefContract(Player player) {
        AspectProfile profile = profiles.get(GoblinAspect.MISCHIEF);
        UUID uuid = player.getUniqueId();
        if (profile.isInheritor(uuid)) {
            player.sendMessage(messages.format("goblin.contract.already_holder"));
            return;
        }
        if (profile.inheritor != null) {
            String holder = profile.name != null ? profile.name : messages.format("goblin.list.none");
            player.sendMessage(messages.format("goblin.contract.taken", Map.of("player", holder)));
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir() || !contractMaterials.contains(held.getType())) {
            player.sendMessage(messages.format("goblin.contract.require_item", Map.of(
                    "items", describeMaterials(contractMaterials)
            )));
            return;
        }
        if (!matchesContractKeywords(held)) {
            player.sendMessage(messages.format("goblin.contract.require_keyword", Map.of(
                    "keywords", String.join(", ", contractKeywords)
            )));
            return;
        }
        consumeMainHand(player);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0F, 0.6F);
        player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.05);
        setInheritor(GoblinAspect.MISCHIEF, player, true, messages.format("chronicle.inherit.obtain", Map.of(
                "player", player.getName(),
                "aspect", GoblinAspect.MISCHIEF.getDisplayName(),
                "method", "계약 성립"
        )));
        player.sendMessage(messages.format("goblin.contract.success", Map.of(
                "aspect", GoblinAspect.MISCHIEF.getDisplayName()
        )));
    }

    public void handleEmberRitual(Player player, Block block) {
        if (block == null || !flameRitualBlocks.contains(block.getType())) {
            return;
        }
        AspectProfile profile = profiles.get(GoblinAspect.FLAME);
        UUID uuid = player.getUniqueId();
        if (profile.isInheritor(uuid)) {
            return;
        }
        if (profile.inheritor != null && !profile.inheritor.equals(uuid)) {
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() != flameCatalyst) {
            player.sendMessage(messages.format("goblin.ritual.flame.require_item", Map.of(
                    "item", describeMaterial(flameCatalyst)
            )));
            return;
        }
        igniteCampfire(block);
        consumeMainHand(player);
        player.getWorld().spawnParticle(Particle.FLAME, block.getLocation().add(0.5, 1.2, 0.5), 80, 0.4, 0.5, 0.4, 0.02);
        player.getWorld().playSound(block.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.9F, 1.1F);
        setInheritor(GoblinAspect.FLAME, player, true, messages.format("chronicle.inherit.obtain", Map.of(
                "player", player.getName(),
                "aspect", GoblinAspect.FLAME.getDisplayName(),
                "method", "불씨 의례"
        )));
        player.sendMessage(messages.format("goblin.ritual.flame.success"));
    }

    public void attemptForgeRitual(Player player) {
        AspectProfile profile = profiles.get(GoblinAspect.FORGE);
        UUID uuid = player.getUniqueId();
        if (profile.isInheritor(uuid)) {
            player.sendMessage(messages.format("goblin.ritual.forge.already"));
            return;
        }
        if (profile.inheritor != null && !profile.inheritor.equals(uuid)) {
            player.sendMessage(messages.format("goblin.ritual.forge.taken", Map.of(
                    "player", profile.name != null ? profile.name : messages.format("goblin.list.none")
            )));
            return;
        }
        PlayerInventory inventory = player.getInventory();
        if (!inventory.contains(forgeCatalyst)) {
            player.sendMessage(messages.format("goblin.ritual.forge.require_catalyst", Map.of(
                    "item", describeMaterial(forgeCatalyst)
            )));
            return;
        }
        if (!inventory.contains(forgeFuel)) {
            player.sendMessage(messages.format("goblin.ritual.forge.require_fuel", Map.of(
                    "item", describeMaterial(forgeFuel)
            )));
            return;
        }
        if (!isNearForgeStation(player)) {
            player.sendMessage(messages.format("goblin.ritual.forge.require_station", Map.of(
                    "blocks", describeMaterials(forgeStations)
            )));
            return;
        }
        removeFromInventory(inventory, forgeCatalyst);
        removeFromInventory(inventory, forgeFuel);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0F, 0.8F);
        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0), 60, 0.5, 0.7, 0.5, 0.1);
        setInheritor(GoblinAspect.FORGE, player, true, messages.format("chronicle.inherit.obtain", Map.of(
                "player", player.getName(),
                "aspect", GoblinAspect.FORGE.getDisplayName(),
                "method", "특수 제작 의식"
        )));
        player.sendMessage(messages.format("goblin.ritual.forge.success"));
    }

    public List<String> describeProgress(Player player) {
        List<String> lines = new ArrayList<>();
        lines.add(messages.format("goblin.progress.header"));
        lines.add(describePowerProgress(player));
        lines.add(describeSpeedProgress(player));
        lines.add(describeMischiefProgress(player));
        lines.add(describeFlameProgress(player));
        lines.add(describeForgeProgress(player));
        return lines;
    }

    public void setInheritor(GoblinAspect aspect, Player player, boolean announce, String chronicleText) {
        AspectProfile profile = profiles.get(aspect);
        if (profile.inheritor != null && profile.inheritor.equals(player.getUniqueId())) {
            profile.name = player.getName();
            applyPassive(aspect, player, true);
            if (weaponManager != null) {
                weaponManager.grantWeapon(aspect, player, true);
            }
            return;
        }
        Player previousPlayer = profile.inheritor != null ? Bukkit.getPlayer(profile.inheritor) : null;
        if (previousPlayer != null && previousPlayer.isOnline()) {
            removePassive(aspect, previousPlayer, true);
            if (weaponManager != null) {
                weaponManager.revokeWeapon(aspect, previousPlayer);
            }
        }
        profile.inheritor = player.getUniqueId();
        profile.name = player.getName();
        applyInheritorHealth(aspect, player);
        applyPassive(aspect, player, true);
        applySharedEffects(aspect, player);
        updateHealthPenalty(aspect);
        if (weaponManager != null) {
            weaponManager.grantWeapon(aspect, player, true);
        }
        if (aspect == GoblinAspect.SPEED) {
            speedProgress.remove(player.getUniqueId());
        }
        if (announce) {
            broadcast(messages.format("goblin.inherit.broadcast", Map.of(
                    "player", player.getName(),
                    "aspect", aspect.getDisplayName()
            )));
        }
        if (omenManager != null) {
            omenManager.trigger(OmenStage.STARSHIFT, aspect.getDisplayName() + " 계승");
        }
        if (chronicleText != null) {
            chronicleManager.logEvent(ChronicleEventType.INHERIT, chronicleText, List.of(player));
        }
        save();
    }

    public void clearInheritor(GoblinAspect aspect, boolean announce, String targetName) {
        AspectProfile profile = profiles.get(aspect);
        UUID previous = profile.inheritor;
        profile.inheritor = null;
        profile.name = null;
        if (previous != null) {
            Player prevPlayer = Bukkit.getPlayer(previous);
            if (prevPlayer != null) {
                removePassive(aspect, prevPlayer, true);
                if (weaponManager != null) {
                    weaponManager.revokeWeapon(aspect, prevPlayer);
                }
            }
            removeInheritorHealth(aspect, previous, prevPlayer);
        }
        for (UUID shared : new HashSet<>(profile.shared)) {
            Player target = Bukkit.getPlayer(shared);
            if (target != null) {
                removePassive(aspect, target, false);
                if (weaponManager != null) {
                    weaponManager.revokeWeapon(aspect, target);
                }
            }
        }
        profile.shared.clear();
        updateHealthPenalty(aspect);
        save();
        if (announce && targetName != null) {
            broadcast(messages.format("goblin.inherit.loss", Map.of(
                    "player", targetName,
                    "aspect", aspect.getDisplayName()
            )));
            chronicleManager.logEvent(ChronicleEventType.LOSS,
                    messages.format("chronicle.inherit.loss", Map.of(
                            "player", targetName,
                            "aspect", aspect.getDisplayName()
                    )), Collections.emptyList());
        }
    }

    public boolean sharePower(GoblinAspect aspect, Player target) {
        AspectProfile profile = profiles.get(aspect);
        if (profile.inheritor == null) {
            return false;
        }
        if (profile.shared.contains(target.getUniqueId())) {
            return true;
        }
        profile.shared.add(target.getUniqueId());
        applyPassive(aspect, target, false);
        if (weaponManager != null) {
            weaponManager.grantWeapon(aspect, target, false);
        }
        updateHealthPenalty(aspect);
        save();
        chronicleManager.logEvent(ChronicleEventType.SHARE,
                messages.format("chronicle.inherit.share", Map.of(
                        "player", getInheritorName(aspect),
                        "target", target.getName(),
                        "aspect", aspect.getDisplayName()
                )), List.of(target));
        return true;
    }

    public boolean reclaimPower(GoblinAspect aspect, Player target) {
        AspectProfile profile = profiles.get(aspect);
        if (!profile.shared.remove(target.getUniqueId())) {
            return false;
        }
        removePassive(aspect, target, false);
        if (weaponManager != null) {
            weaponManager.revokeWeapon(aspect, target);
        }
        updateHealthPenalty(aspect);
        save();
        return true;
    }

    public boolean useSkill(Player player, String key) {
        key = key.toLowerCase(Locale.ROOT);
        for (GoblinAspect aspect : GoblinAspect.values()) {
            if (useSkill(aspect, player, key, false)) {
                return true;
            }
        }
        player.sendMessage(messages.format("goblin.skill.unknown"));
        return false;
    }

    public boolean useSkill(GoblinAspect aspect, Player player, String key) {
        return useSkill(aspect, player, key, true);
    }

    private boolean useSkill(GoblinAspect aspect, Player player, String key, boolean notifyMissing) {
        Optional<GoblinSkill> optional = findSkill(aspect, key);
        if (optional.isEmpty()) {
            if (notifyMissing) {
                player.sendMessage(messages.format("goblin.skill.unknown"));
            }
            return false;
        }
        GoblinSkill skill = optional.get();
        AspectProfile profile = profiles.get(aspect);
        boolean inheritor = profile.isInheritor(player.getUniqueId());
        boolean shared = profile.shared.contains(player.getUniqueId());
        if (!inheritor && !shared) {
            if (notifyMissing) {
                player.sendMessage(messages.format("goblin.skill.unknown"));
            }
            return false;
        }
        if (skill.getCategory() == GoblinSkillCategory.PASSIVE) {
            player.sendMessage(messages.format("goblin.skill.passive"));
            return true;
        }
        int cooldown = skill.getCooldownSeconds(inheritor);
        if (!checkCooldown(player.getUniqueId(), aspect, skill, cooldown)) {
            long remaining = getRemainingCooldown(player.getUniqueId(), aspect, skill);
            player.sendMessage(messages.format("goblin.skill.cooldown", Map.of(
                    "time", formatSeconds(remaining)
            )));
            return true;
        }
        executeSkill(aspect, skill, player, inheritor);
        registerCooldown(player.getUniqueId(), aspect, skill, cooldown);
        chronicleManager.logEvent(ChronicleEventType.SKILL,
                messages.format("chronicle.skill.use", Map.of(
                        "player", player.getName(),
                        "skill", skill.getDisplayName(),
                        "aspect", aspect.getDisplayName()
                )), List.of(player));
        return true;
    }

    public Set<GoblinAspect> getAspects(UUID uuid) {
        Set<GoblinAspect> aspects = new HashSet<>();
        for (Map.Entry<GoblinAspect, AspectProfile> entry : profiles.entrySet()) {
            if (entry.getValue().isInheritor(uuid) || entry.getValue().shared.contains(uuid)) {
                aspects.add(entry.getKey());
            }
        }
        return aspects;
    }

    public String getInheritorName(GoblinAspect aspect) {
        AspectProfile profile = profiles.get(aspect);
        return profile.name;
    }

    public Set<UUID> getSharedMembers(GoblinAspect aspect) {
        return Collections.unmodifiableSet(profiles.get(aspect).shared);
    }

    public boolean isInheritor(GoblinAspect aspect, UUID uuid) {
        return profiles.get(aspect).isInheritor(uuid);
    }

    public boolean isShared(GoblinAspect aspect, UUID uuid) {
        return profiles.get(aspect).shared.contains(uuid);
    }

    public Optional<GoblinSkill> findSkill(GoblinAspect aspect, String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return aspect.getSkills().stream().filter(skill -> skill.getKey().equals(normalized)).findFirst();
    }

    public void markWitness(Player player) {
        player.addScoreboardTag("myth_witness");
    }

    private void executeSkill(GoblinAspect aspect, GoblinSkill skill, Player player, boolean inheritor) {
        double effectiveness = inheritor ? 1.0D : skill.getSharedEffectiveness();
        String upgrade = skillTreeManager.getSelectedUpgrade(player.getUniqueId(), skill.getKey());
        switch (skill.getKey()) {
            case "rush_strike" -> performRushStrike(player, skill, effectiveness, upgrade);
            case "pursuit_mark" -> performPursuitMark(player, skill, effectiveness, upgrade);
            case "vision_twist" -> performVisionTwist(player, skill, effectiveness, upgrade);
            case "veil_break" -> performVeilBreak(player, skill, effectiveness, upgrade);
            case "ember_boost" -> performEmberBoost(player, skill, effectiveness, upgrade);
            case "ember_recovery" -> performEmberRecovery(player, skill, effectiveness, upgrade);
            case "weapon_overdrive" -> performWeaponOverdrive(player, skill, effectiveness, upgrade);
            case "legendary_summon" -> performLegendarySummon(player, skill, effectiveness, upgrade);
            default -> player.sendMessage(messages.format("goblin.skill.unknown"));
        }
        balanceTable.recordSkillUsage(aspect, skill, upgrade, inheritor);
        metaEventManager.recordSkillUse(player, skill);
    }

    private void performRushStrike(Player player, GoblinSkill skill, double effectiveness, String upgrade) {
        Vector direction = player.getLocation().getDirection();
        if (direction.lengthSquared() == 0) {
            direction = new Vector(0, 0, 0);
        }
        double rangeBonus = "range".equalsIgnoreCase(upgrade) ? 0.6 : 0.0;
        Vector velocity = direction.normalize().multiply(1.4 * effectiveness + rangeBonus);
        velocity.setY(Math.max(0.35 * effectiveness, 0.25) + ("range".equalsIgnoreCase(upgrade) ? 0.1 : 0.0));
        player.setVelocity(velocity);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2F, 0.75F);
        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, player.getLocation().add(0, 1, 0), 12, 0.6, 0.2, 0.6, 0.01);
        SkillSpec spec = skill.getSpec().orElse(null);
        double baseDamage = spec != null ? spec.getBaseDamage() * effectiveness : 6.0 * effectiveness + 4.0;
        if ("range".equalsIgnoreCase(upgrade)) {
            baseDamage *= 1.15;
        }
        double radius = 2.5 + effectiveness + rangeBonus;
        LocationSnapshot snapshot = LocationSnapshot.of(player);
        final double explosionRadius = radius;
        final double scheduledDamage = baseDamage;
        final double knockEffectiveness = effectiveness;
        final SkillSpec resolvedSpec = spec;
        final Player caster = player;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Collection<Entity> nearby = snapshot.world().getNearbyEntities(snapshot.location(), explosionRadius, explosionRadius, explosionRadius);
            for (Entity entity : nearby) {
                if (entity instanceof LivingEntity living && !living.getUniqueId().equals(caster.getUniqueId())) {
                    living.damage(scheduledDamage, caster);
                    Vector knock = living.getLocation().toVector().subtract(caster.getLocation().toVector()).normalize().multiply(0.5 * knockEffectiveness);
                    living.setVelocity(living.getVelocity().add(knock));
                    applyStatusEffects(living, resolvedSpec, knockEffectiveness, upgrade);
                }
            }
            snapshot.world().spawnParticle(Particle.EXPLOSION, snapshot.location(), 24, 0.6, 0.4, 0.6, 0.05);
            if (resolvedSpec != null) {
                applyEnvironmentEffects(caster, resolvedSpec, upgrade);
            }
        }, 8L);
    }

    private void performPursuitMark(Player player, GoblinSkill skill, double effectiveness, String upgrade) {
        Entity targetEntity = player.getTargetEntity(25);
        if (!(targetEntity instanceof LivingEntity target) || target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(messages.format("goblin.skill.no_target"));
            return;
        }
        SkillSpec spec = skill.getSpec().orElse(null);
        int duration = (int) Math.round(200 * effectiveness + 60);
        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, duration, 0, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) Math.round(60 * effectiveness + 40), 0, false, true, true));
        if ("rupture".equalsIgnoreCase(upgrade)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, duration, 1, false, true, true));
        }
        int speedLevel = effectiveness >= 0.9 ? 2 : 1;
        if ("sprint".equalsIgnoreCase(upgrade)) {
            speedLevel += 1;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, speedLevel, false, true, true));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.8F, 1.4F);
        player.getWorld().spawnParticle(Particle.END_ROD, target.getLocation().add(0, 1, 0), 20, 0.3, 0.7, 0.3, 0.01);
        applyStatusEffects(target, spec, effectiveness, upgrade);
        if (spec != null) {
            applyEnvironmentEffects(player, spec, upgrade);
        }
    }

    private void performVisionTwist(Player player, GoblinSkill skill, double effectiveness, String upgrade) {
        SkillSpec spec = skill.getSpec().orElse(null);
        double radius = 8.0 + 4.0 * effectiveness;
        if ("chill".equalsIgnoreCase(upgrade)) {
            radius += 2.0;
        }
        int blindness = (int) Math.round(80 * effectiveness + 60);
        int confusion = (int) Math.round(140 * effectiveness + 80);
        if ("dread".equalsIgnoreCase(upgrade)) {
            blindness += 40;
        }
        for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), radius, radius, radius)) {
            if (entity instanceof LivingEntity target && !target.getUniqueId().equals(player.getUniqueId())) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindness, 0, false, true, true));
                target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, confusion, 0, false, true, true));
                applyStatusEffects(target, spec, effectiveness, upgrade);
            }
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1.0F, 0.6F);
        player.getWorld().spawnParticle(Particle.WITCH, player.getLocation().add(0, 1, 0), 60, radius / 6, 1.0, radius / 6, 0.02);
        if (spec != null) {
            applyEnvironmentEffects(player, spec, upgrade);
        }
    }

    private void performVeilBreak(Player player, GoblinSkill skill, double effectiveness, String upgrade) {
        SkillSpec spec = skill.getSpec().orElse(null);
        double radius = 12.0 * effectiveness + 6.0;
        int duration = (int) Math.round(120 * effectiveness + 40);
        for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), radius, radius, radius)) {
            if (entity instanceof LivingEntity target && !target.getUniqueId().equals(player.getUniqueId())) {
                if (target.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                    target.removePotionEffect(PotionEffectType.INVISIBILITY);
                }
                target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, duration, 0, false, true, true));
                if ("flare".equalsIgnoreCase(upgrade)) {
                    target.damage(4.0 * effectiveness, player);
                }
                applyStatusEffects(target, spec, effectiveness, upgrade);
                if ("mark".equalsIgnoreCase(upgrade)) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 2, true, true, true));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 1, true, true, true));
                }
            }
        }
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0F, 1.2F);
        player.getWorld().spawnParticle(Particle.INSTANT_EFFECT, player.getLocation().add(0, 1, 0), 40, radius / 6, 0.8, radius / 6, 0.01);
        if (spec != null) {
            applyEnvironmentEffects(player, spec, upgrade);
        }
    }

    private void performEmberBoost(Player player, GoblinSkill skill, double effectiveness, String upgrade) {
        SkillSpec spec = skill.getSpec().orElse(null);
        double radius = 10.0 + 4.0 * effectiveness;
        if ("wildfire".equalsIgnoreCase(upgrade)) {
            radius += 2.0;
        }
        int duration = (int) Math.round(200 * effectiveness + 100);
        int strengthLevel = effectiveness >= 0.95 ? 1 : 0;
        for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), radius, radius, radius)) {
            if (entity instanceof Player target) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, strengthLevel, false, true, true));
                target.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 0, false, true, true));
                target.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, duration, 0, false, true, true));
                applyStatusEffects(target, spec, effectiveness, upgrade);
                if ("ward".equalsIgnoreCase(upgrade)) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, duration, 1, true, true, true));
                }
            }
        }
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.0F, 1.0F);
        player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0, 1, 0), 120, radius / 5, 1.0, radius / 5, 0.05);
        if (spec != null) {
            applyEnvironmentEffects(player, spec, upgrade);
        }
    }

    private void performEmberRecovery(Player player, GoblinSkill skill, double effectiveness, String upgrade) {
        double healAmount = 6.0 * effectiveness + 2.0;
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            player.setHealth(Math.min(maxHealth.getValue(), player.getHealth() + healAmount));
        }
        player.setFireTicks(0);
        player.removePotionEffect(PotionEffectType.POISON);
        player.removePotionEffect(PotionEffectType.WITHER);
        int duration = (int) Math.round(80 * effectiveness + 40);
        int regenLevel = effectiveness >= 0.9 ? 1 : 0;
        if ("purify".equalsIgnoreCase(upgrade)) {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            regenLevel++;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, duration, regenLevel, false, true, true));
        SkillSpec spec = skill.getSpec().orElse(null);
        applyStatusEffects(player, spec, effectiveness, upgrade);
        if ("ember_wall".equalsIgnoreCase(upgrade)) {
            createEmberWall(player.getLocation(), 4.0);
        }
        if (spec != null) {
            applyEnvironmentEffects(player, spec, upgrade);
        }
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_CAMPFIRE_CRACKLE, 1.0F, 1.2F);
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1, 0), 12, 0.4, 0.6, 0.4, 0.02);
    }

    private void performWeaponOverdrive(Player player, GoblinSkill skill, double effectiveness, String upgrade) {
        SkillSpec spec = skill.getSpec().orElse(null);
        int duration = (int) Math.round(200 * effectiveness + 80);
        if ("forge_heat".equalsIgnoreCase(upgrade)) {
            duration += 60;
        }
        int strengthLevel = effectiveness >= 0.95 ? 1 : 0;
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, strengthLevel, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, duration, 0, false, true, true));
        if ("impact".equalsIgnoreCase(upgrade)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 0, true, true, true));
        }
        applyStatusEffects(player, spec, effectiveness, upgrade);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1.0F, 1.0F);
        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1, 0), 50, 0.5, 0.7, 0.5, 0.1);
        if ("impact".equalsIgnoreCase(upgrade)) {
            player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 20, 1.2, 0.2, 1.2, 0.05);
        }
        if (spec != null) {
            applyEnvironmentEffects(player, spec, upgrade);
        }
    }

    private void performLegendarySummon(Player player, GoblinSkill skill, double effectiveness, String upgrade) {
        ItemStack sword = new ItemStack(org.bukkit.Material.NETHERITE_SWORD);
        ItemMeta meta = sword.getItemMeta();
        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.Component.text("전설의 화염검", net.kyori.adventure.text.format.NamedTextColor.GOLD));
            meta.lore(List.of(
                    Component.text("일시적 전설 무기", NamedTextColor.GOLD),
                    Component.text("효율: " + (int) Math.round(effectiveness * 100) + "%", NamedTextColor.GRAY)
            ));
            meta.getPersistentDataContainer().set(legendaryWeaponKey, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            sword.setItemMeta(meta);
        }
        player.getInventory().addItem(sword);
        SkillSpec spec = skill.getSpec().orElse(null);
        int duration = (int) Math.round(200 * effectiveness + 160);
        if ("eternal".equalsIgnoreCase(upgrade)) {
            duration += 80;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, 1, false, true, true));
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.8F, 1.1F);
        player.getWorld().spawnParticle(Particle.LAVA, player.getLocation().add(0, 1, 0), 80, 0.6, 1.0, 0.6, 0.08);
        applyStatusEffects(player, spec, effectiveness, upgrade);
        if (spec != null) {
            applyEnvironmentEffects(player, spec, upgrade);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> removeLegendaryWeapon(player), duration);
        if ("eruption".equalsIgnoreCase(upgrade)) {
            player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 1);
            for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), 4, 4, 4)) {
                if (entity instanceof LivingEntity living && !living.getUniqueId().equals(player.getUniqueId())) {
                    living.damage(8.0 * effectiveness, player);
                }
            }
        }
    }

    private void removeLegendaryWeapon(Player player) {
        PlayerInventory inventory = player.getInventory();
        boolean changed = false;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isLegendaryWeapon(stack)) {
                inventory.setItem(slot, null);
                changed = true;
            }
        }
        if (isLegendaryWeapon(inventory.getItemInOffHand())) {
            inventory.setItemInOffHand(null);
            changed = true;
        }
        if (changed) {
            player.updateInventory();
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6F, 1.4F);
        }
    }

    private boolean isLegendaryWeapon(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(legendaryWeaponKey, org.bukkit.persistence.PersistentDataType.BYTE);
    }

    private boolean isInheritorOfAnyAspect(UUID uuid) {
        for (AspectProfile profile : profiles.values()) {
            if (profile.isInheritor(uuid)) {
                return true;
            }
        }
        return false;
    }

    private void cleanupLegendaryWeapons(PlayerInventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isLegendaryWeapon(stack)) {
                NamespacedKey key = legendaryWeaponKey;
                ItemMeta meta = stack.getItemMeta();
                meta.getPersistentDataContainer().remove(key);
                stack.setItemMeta(meta);
            }
        }
    }

    private boolean checkCooldown(UUID uuid, GoblinAspect aspect, GoblinSkill skill, int cooldown) {
        Map<String, Long> map = cooldowns.get(uuid);
        if (map == null) {
            return true;
        }
        String composite = aspect.getKey() + ":" + skill.getKey();
        Long until = map.get(composite);
        if (until == null) {
            return true;
        }
        return until <= System.currentTimeMillis();
    }

    private void registerCooldown(UUID uuid, GoblinAspect aspect, GoblinSkill skill, int cooldown) {
        cooldowns.computeIfAbsent(uuid, key -> new ConcurrentHashMap<>())
                .put(aspect.getKey() + ":" + skill.getKey(), System.currentTimeMillis() + cooldown * 1000L);
    }

    private long getRemainingCooldown(UUID uuid, GoblinAspect aspect, GoblinSkill skill) {
        Map<String, Long> map = cooldowns.get(uuid);
        if (map == null) {
            return 0;
        }
        Long until = map.get(aspect.getKey() + ":" + skill.getKey());
        if (until == null) {
            return 0;
        }
        long remaining = until - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    private String formatSeconds(long millis) {
        long seconds = (long) Math.ceil(millis / 1000.0);
        return seconds + "s";
    }

    private void applyStatusEffects(LivingEntity target, SkillSpec spec, double effectiveness, String upgrade) {
        if (spec == null) {
            return;
        }
        for (SkillStatusEffect status : spec.getStatuses()) {
            int duration = status.getDurationTicks();
            if (metaEventManager.isBalanceCollapseActive()) {
                duration = (int) Math.round(duration * 1.25D);
            }
            switch (status.getType()) {
                case BLEED -> applyBleed(target, effectiveness, duration, status.getAmplifier(), upgrade);
                case STUN -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 6, true, true, true));
                case BURN -> target.setFireTicks(Math.max(target.getFireTicks(), duration));
                case SLOW -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, status.getAmplifier(), true, true, true));
                case WEAKEN -> target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, duration, status.getAmplifier(), true, true, true));
                case SHIELD -> {
                    if (target instanceof Player player) {
                        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, duration, status.getAmplifier(), true, true, true));
                    }
                }
                default -> { }
            }
        }
    }

    private void applyBleed(LivingEntity target, double effectiveness, int durationTicks, int amplifier, String upgrade) {
        double damage = Math.max(1.0, 1.5 * effectiveness + amplifier);
        if ("bleed".equalsIgnoreCase(upgrade)) {
            damage *= 1.5;
        }
        int repeats = Math.max(1, durationTicks / 20);
        final double damagePerTick = damage;
        new BukkitRunnable() {
            int count = 0;
            @Override
            public void run() {
                if (count++ >= repeats || target.isDead()) {
                    cancel();
                    return;
                }
                target.damage(damagePerTick);
                target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getLocation().add(0, 0.5, 0), 6, 0.2, 0.3, 0.2, 0.02);
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void applyEnvironmentEffects(Player player, SkillSpec spec, String upgrade) {
        if (spec == null) {
            return;
        }
        for (SkillEnvironmentTag tag : spec.getEnvironmentTags()) {
            switch (tag) {
                case BREAK_BLOCKS -> shatterBlocks(player.getLocation(), 3);
                case IGNITE_BLOCKS -> igniteArea(player.getLocation(), 4);
                case FREEZE_LIQUIDS -> freezeLiquids(player.getLocation(), 5);
                case SUMMON_SUPPORT -> spawnSupportBurst(player.getLocation());
                case CLEANSING_FIELD -> cleanseArea(player.getLocation(), 5);
                default -> { }
            }
        }
        if ("wildfire".equalsIgnoreCase(upgrade)) {
            igniteArea(player.getLocation(), 3);
        }
    }

    private void shatterBlocks(Location origin, int radius) {
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = world.getBlockAt(origin.clone().add(x, y, z));
                    if (!block.getType().isAir() && block.getType().getHardness() <= 1.0F) {
                        block.breakNaturally();
                    }
                }
            }
        }
    }

    private void igniteArea(Location origin, int radius) {
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                Block block = world.getBlockAt(origin.clone().add(x, 0, z));
                Block above = block.getRelative(BlockFace.UP);
                if (!block.getType().isAir() && above.getType().isAir()) {
                    above.setType(Material.FIRE);
                }
            }
        }
    }

    private void freezeLiquids(Location origin, int radius) {
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                Block block = world.getBlockAt(origin.clone().add(x, -1, z));
                if (block.getType() == Material.WATER) {
                    block.setType(Material.FROSTED_ICE);
                }
                if (block.getType() == Material.LAVA) {
                    block.setType(Material.OBSIDIAN);
                }
            }
        }
    }

    private void spawnSupportBurst(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.INSTANT_EFFECT, location.add(0, 1, 0), 40, 1.2, 0.4, 1.2, 0.05);
        world.playSound(location, Sound.BLOCK_BEACON_ACTIVATE, 0.8F, 1.2F);
    }

    private void cleanseArea(Location location, int radius) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        for (Entity entity : world.getNearbyEntities(location, radius, radius, radius)) {
            if (entity instanceof Player target) {
                target.setFireTicks(0);
                target.removePotionEffect(PotionEffectType.POISON);
                target.removePotionEffect(PotionEffectType.WITHER);
            }
        }
    }

    private void createEmberWall(Location center, double radius) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        for (int i = 0; i < 16; i++) {
            double angle = (Math.PI * 2 * i) / 16;
            Location point = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            world.spawnParticle(Particle.FLAME, point.add(0, 1, 0), 8, 0.1, 0.3, 0.1, 0.01);
        }
        world.playSound(center, Sound.ITEM_FIRECHARGE_USE, 0.8F, 1.3F);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPassiveDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        double maxHealth = Optional.ofNullable(player.getAttribute(Attribute.MAX_HEALTH))
                .map(AttributeInstance::getValue).orElse(20.0D);
        double postHealth = player.getHealth() - event.getFinalDamage();
        for (GoblinAspect aspect : GoblinAspect.values()) {
            AspectProfile profile = profiles.get(aspect);
            boolean inheritor = profile.isInheritor(uuid);
            boolean shared = profile.shared.contains(uuid);
            if (!inheritor && !shared) {
                continue;
            }
            Optional<GoblinSkill> passiveSkill = aspect.getSkills().stream()
                    .filter(skill -> skill.getCategory() == GoblinSkillCategory.PASSIVE)
                    .findFirst();
            if (passiveSkill.isEmpty()) {
                continue;
            }
            Optional<PassiveTrigger> triggerOptional = passiveSkill.get().getPassiveTrigger();
            if (triggerOptional.isEmpty()) {
                continue;
            }
            PassiveTrigger trigger = triggerOptional.get();
            Map<UUID, Long> map = passiveCooldowns.get(aspect);
            long now = System.currentTimeMillis();
            long next = map.getOrDefault(uuid, 0L);
            if (now < next) {
                continue;
            }
            if (trigger.getHealthThreshold() > 0 && maxHealth > 0) {
                if (postHealth / maxHealth > trigger.getHealthThreshold()) {
                    continue;
                }
            }
            double effectiveness = inheritor ? 1.0D : passiveSkill.get().getSharedEffectiveness();
            applyPassiveTrigger(player, player, trigger, effectiveness);
            map.put(uuid, now + trigger.getCooldownTicks() * 50L);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onScentReaderMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        Player player = event.getPlayer();
        AspectProfile profile = profiles.get(GoblinAspect.SPEED);
        UUID uuid = player.getUniqueId();
        boolean inheritor = profile.isInheritor(uuid);
        boolean shared = profile.shared.contains(uuid);
        if (!inheritor && !shared) {
            return;
        }
        Optional<GoblinSkill> passiveSkill = GoblinAspect.SPEED.getSkills().stream()
                .filter(skill -> skill.getCategory() == GoblinSkillCategory.PASSIVE)
                .findFirst();
        if (passiveSkill.isEmpty()) {
            return;
        }
        Optional<PassiveTrigger> triggerOptional = passiveSkill.get().getPassiveTrigger();
        if (triggerOptional.isEmpty()) {
            return;
        }
        Map<UUID, Long> map = passiveCooldowns.get(GoblinAspect.SPEED);
        long now = System.currentTimeMillis();
        if (now < map.getOrDefault(uuid, 0L)) {
            return;
        }
        double radius = inheritor ? 18.0D : 12.0D;
        LivingEntity closest = null;
        double best = Double.MAX_VALUE;
        for (Player other : player.getWorld().getPlayers()) {
            if (other.equals(player) || other.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            double distanceSq = other.getLocation().distanceSquared(player.getLocation());
            if (distanceSq <= radius * radius && distanceSq < best) {
                closest = other;
                best = distanceSq;
            }
        }
        if (closest == null) {
            return;
        }
        double effectiveness = inheritor ? 1.0D : passiveSkill.get().getSharedEffectiveness();
        applyPassiveTrigger(player, closest, triggerOptional.get(), effectiveness);
        map.put(uuid, now + triggerOptional.get().getCooldownTicks() * 50L);
    }

    private void applyPassiveTrigger(Player owner, LivingEntity target, PassiveTrigger trigger, double effectiveness) {
        if (target == null) {
            return;
        }
        boolean selfTarget = owner != null && owner.getUniqueId().equals(target.getUniqueId());
        for (SkillStatusEffect status : trigger.getStatuses()) {
            int duration = status.getDurationTicks();
            if (selfTarget) {
                switch (status.getType()) {
                    case SHIELD -> owner.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, duration, status.getAmplifier() + 1, true, true, true));
                    case WEAKEN -> owner.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, Math.max(0, status.getAmplifier()), true, true, true));
                    case BLEED -> applyBleed(owner, effectiveness, duration, status.getAmplifier(), "");
                    case SLOW -> owner.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, status.getAmplifier(), true, true, true));
                    default -> { }
                }
            } else {
                switch (status.getType()) {
                    case BLEED -> applyBleed(target, effectiveness, duration, status.getAmplifier(), "");
                    case SLOW -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, Math.max(0, status.getAmplifier()), true, true, true));
                    case WEAKEN -> target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, duration, Math.max(0, status.getAmplifier()), true, true, true));
                    case BURN -> target.setFireTicks(Math.max(target.getFireTicks(), duration));
                    default -> { }
                }
            }
        }
        if (owner != null && !trigger.getMessageKey().isBlank()) {
            owner.sendMessage(messages.format(trigger.getMessageKey()));
        }
        Location effectLocation = target.getLocation().add(0, 1, 0);
        target.getWorld().spawnParticle(Particle.CRIT, effectLocation, 20, 0.3, 0.3, 0.3, 0.05);
        target.getWorld().playSound(effectLocation, Sound.ENTITY_PHANTOM_FLAP, 0.4F, selfTarget ? 1.2F : 0.7F);
    }

    private void applyPassive(GoblinAspect aspect, Player player, boolean inheritor) {
        removePassive(aspect, player, inheritor);
        switch (aspect) {
            case POWER -> applyPowerPassive(player, inheritor ? 1.0D : aspect.getSharedPassiveRatio());
            case SPEED -> applySpeedPassive(player, inheritor ? 1.0D : aspect.getSharedPassiveRatio());
            default -> { /* no passive */ }
        }
        Optional<GoblinSkill> passiveSkill = aspect.getSkills().stream()
                .filter(skill -> skill.getCategory() == GoblinSkillCategory.PASSIVE)
                .findFirst();
        passiveSkill.flatMap(GoblinSkill::getPassiveTrigger).ifPresent(trigger ->
                passiveCooldowns.get(aspect).put(player.getUniqueId(), 0L));
    }

    private void removePassive(GoblinAspect aspect, Player player, boolean inheritor) {
        switch (aspect) {
            case POWER -> player.removePotionEffect(PotionEffectType.RESISTANCE);
            case SPEED -> {
                player.removePotionEffect(PotionEffectType.SPEED);
                cancelScentTask(player.getUniqueId());
            }
            default -> {
                // no-op
            }
        }
    }

    private void applyPowerPassive(Player player, double ratio) {
        int amplifier = ratio >= 0.95 ? 1 : 0;
        int duration = Integer.MAX_VALUE;
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, amplifier, false, false, false));
    }

    private void applySpeedPassive(Player player, double ratio) {
        int amplifier = ratio >= 0.9 ? 1 : 0;
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, amplifier, false, false, false));
        long interval = ratio >= 0.9 ? 100L : 160L;
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                if (player.getGameMode() == GameMode.SPECTATOR) {
                    return;
                }
                int count = 0;
                for (Player nearby : player.getWorld().getPlayers()) {
                    if (nearby.equals(player)) {
                        continue;
                    }
                    if (nearby.getLocation().distanceSquared(player.getLocation()) <= 400) {
                        count++;
                    }
                }
                if (count > 0) {
                    player.sendActionBar(net.kyori.adventure.text.Component.text(
                            messages.format("goblin.passive.scent", Map.of("count", String.valueOf(count)))
                    ));
                }
            }
        }.runTaskTimer(plugin, interval, interval);
        scentTasks.put(player.getUniqueId(), task);
    }

    private void cancelScentTask(UUID uuid) {
        BukkitTask task = scentTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    private void applySharedEffects(GoblinAspect aspect, Player inheritor) {
        // Shared effect placeholder for future expansion. Currently handles nothing beyond passives.
    }

    private void updateHealthPenalty(GoblinAspect aspect) {
        AspectProfile profile = profiles.get(aspect);
        if (profile.inheritor == null) {
            return;
        }
        Player inheritor = Bukkit.getPlayer(profile.inheritor);
        if (inheritor == null) {
            return;
        }
        AttributeInstance attribute = inheritor.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) {
            return;
        }
        NamespacedKey key = healthModifierKeys.get(aspect);
        attribute.removeModifier(key);
        int count = profile.shared.size();
        if (count <= 0) {
            return;
        }
        double penalty = count * HEALTH_PENALTY_PER_SHARE;
        AttributeModifier modifier = new AttributeModifier(key, -penalty, AttributeModifier.Operation.ADD_NUMBER);
        attribute.addModifier(modifier);
        double maxHealth = attribute.getValue();
        if (inheritor.getHealth() > maxHealth) {
            inheritor.setHealth(maxHealth);
        }
    }

    private void applyInheritorHealth(GoblinAspect aspect, Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Set<GoblinAspect> active = activeInheritorAspects.computeIfAbsent(uuid, key -> new HashSet<>());
        if (!active.contains(aspect)) {
            if (active.isEmpty() && !originalMaxHealth.containsKey(uuid)) {
                originalMaxHealth.put(uuid, attribute.getBaseValue());
            }
            active.add(aspect);
        }
        double target = aspect.getBaseMaxHealth();
        if (attribute.getBaseValue() < target - 1.0E-3D) {
            attribute.setBaseValue(target);
        }
        if (player.getHealth() > attribute.getBaseValue()) {
            player.setHealth(attribute.getBaseValue());
        }
        pendingMaxHealthRestores.remove(uuid);
    }

    private void removeInheritorHealth(GoblinAspect aspect, UUID uuid, Player player) {
        Set<GoblinAspect> active = activeInheritorAspects.get(uuid);
        boolean restore = true;
        if (active != null) {
            active.remove(aspect);
            if (active.isEmpty()) {
                activeInheritorAspects.remove(uuid);
            } else {
                restore = false;
            }
        }
        if (!restore) {
            return;
        }
        Double original = originalMaxHealth.remove(uuid);
        if (original == null) {
            return;
        }
        if (player != null) {
            AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
            if (attribute != null) {
                attribute.setBaseValue(original);
                if (player.getHealth() > original) {
                    player.setHealth(original);
                }
            }
        } else {
            pendingMaxHealthRestores.put(uuid, original);
        }
    }

    private String describePowerProgress(Player player) {
        AspectProfile profile = profiles.get(GoblinAspect.POWER);
        if (profile.inheritor == null) {
            return messages.format("goblin.progress.power.available");
        }
        if (profile.isInheritor(player.getUniqueId())) {
            return messages.format("goblin.progress.power.self");
        }
        String holder = profile.name != null ? profile.name : messages.format("goblin.list.none");
        return messages.format("goblin.progress.power.holder", Map.of("player", holder));
    }

    private String describeSpeedProgress(Player player) {
        AspectProfile profile = profiles.get(GoblinAspect.SPEED);
        UUID uuid = player.getUniqueId();
        if (profile.inheritor != null) {
            if (profile.isInheritor(uuid)) {
                return messages.format("goblin.progress.speed.self");
            }
            String holder = profile.name != null ? profile.name : messages.format("goblin.list.none");
            return messages.format("goblin.progress.speed.holder", Map.of("player", holder));
        }
        EnumSet<TraceToken> progress = speedProgress.get(uuid);
        if (progress == null || progress.isEmpty()) {
            return messages.format("goblin.progress.speed.none", Map.of(
                    "eye", describeMaterial(speedTraceEyeItem),
                    "horn", describeMaterial(speedTraceHornItem)
            ));
        }
        boolean hasEye = progress.contains(TraceToken.EYE);
        boolean hasHorn = progress.contains(TraceToken.HORN);
        if (hasEye && hasHorn) {
            return messages.format("goblin.progress.speed.ready");
        }
        if (hasEye) {
            return messages.format("goblin.progress.speed.partial_eye", Map.of(
                    "horn", describeMaterial(speedTraceHornItem)
            ));
        }
        return messages.format("goblin.progress.speed.partial_horn", Map.of(
                "eye", describeMaterial(speedTraceEyeItem)
        ));
    }

    private String describeMischiefProgress(Player player) {
        AspectProfile profile = profiles.get(GoblinAspect.MISCHIEF);
        UUID uuid = player.getUniqueId();
        if (profile.inheritor != null) {
            if (profile.isInheritor(uuid)) {
                return messages.format("goblin.progress.mischief.self");
            }
            String holder = profile.name != null ? profile.name : messages.format("goblin.list.none");
            return messages.format("goblin.progress.mischief.holder", Map.of("player", holder));
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir() || !contractMaterials.contains(held.getType())) {
            return messages.format("goblin.progress.mischief.need_item", Map.of(
                    "items", describeMaterials(contractMaterials)
            ));
        }
        if (!matchesContractKeywords(held)) {
            return messages.format("goblin.progress.mischief.need_keyword", Map.of(
                    "keywords", String.join(", ", contractKeywords)
            ));
        }
        return messages.format("goblin.progress.mischief.ready");
    }

    private String describeFlameProgress(Player player) {
        AspectProfile profile = profiles.get(GoblinAspect.FLAME);
        UUID uuid = player.getUniqueId();
        if (profile.inheritor != null) {
            if (profile.isInheritor(uuid)) {
                return messages.format("goblin.progress.flame.self");
            }
            String holder = profile.name != null ? profile.name : messages.format("goblin.list.none");
            return messages.format("goblin.progress.flame.holder", Map.of("player", holder));
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() != flameCatalyst) {
            return messages.format("goblin.progress.flame.need_item", Map.of(
                    "item", describeMaterial(flameCatalyst)
            ));
        }
        return messages.format("goblin.progress.flame.ready", Map.of(
                "blocks", describeMaterials(flameRitualBlocks)
        ));
    }

    private String describeForgeProgress(Player player) {
        AspectProfile profile = profiles.get(GoblinAspect.FORGE);
        UUID uuid = player.getUniqueId();
        if (profile.inheritor != null) {
            if (profile.isInheritor(uuid)) {
                return messages.format("goblin.progress.forge.self");
            }
            String holder = profile.name != null ? profile.name : messages.format("goblin.list.none");
            return messages.format("goblin.progress.forge.holder", Map.of("player", holder));
        }
        PlayerInventory inventory = player.getInventory();
        if (!inventory.contains(forgeCatalyst)) {
            return messages.format("goblin.progress.forge.need_catalyst", Map.of(
                    "item", describeMaterial(forgeCatalyst)
            ));
        }
        if (!inventory.contains(forgeFuel)) {
            return messages.format("goblin.progress.forge.need_fuel", Map.of(
                    "item", describeMaterial(forgeFuel)
            ));
        }
        if (!isNearForgeStation(player)) {
            return messages.format("goblin.progress.forge.need_station", Map.of(
                    "blocks", describeMaterials(forgeStations)
            ));
        }
        return messages.format("goblin.progress.forge.ready");
    }

    private TraceToken traceTokenFromMaterial(Material material) {
        if (material == null) {
            return null;
        }
        if (material == speedTraceEyeItem) {
            return TraceToken.EYE;
        }
        if (material == speedTraceHornItem) {
            return TraceToken.HORN;
        }
        return null;
    }

    private String describeMaterial(Material material) {
        if (material == null) {
            return "";
        }
        String path = "goblin.material." + material.name();
        String formatted = messages.format(path);
        if (formatted.equals(path)) {
            return material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        }
        return formatted;
    }

    private String describeMaterials(Collection<Material> materials) {
        List<String> names = new ArrayList<>();
        for (Material material : materials) {
            names.add(describeMaterial(material));
        }
        return String.join(", ", names);
    }

    private boolean matchesContractKeywords(ItemStack stack) {
        if (contractKeywords.isEmpty()) {
            return true;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        List<String> candidates = new ArrayList<>();
        if (meta instanceof BookMeta bookMeta) {
            String title = bookMeta.getTitle();
            if (title != null) {
                candidates.add(title);
            }
        }
        if (meta.hasDisplayName()) {
            Component component = meta.displayName();
            if (component != null) {
                candidates.add(PlainTextComponentSerializer.plainText().serialize(component));
            }
        }
        for (String candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            String normalized = candidate.toLowerCase(Locale.ROOT);
            for (String keyword : contractKeywords) {
                if (normalized.contains(keyword)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void consumeMainHand(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack held = inventory.getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            return;
        }
        if (held.getAmount() <= 1) {
            inventory.setItemInMainHand(null);
        } else {
            held.setAmount(held.getAmount() - 1);
        }
    }

    private void igniteCampfire(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Campfire campfire && !campfire.isLit()) {
            campfire.setLit(true);
            block.setBlockData(campfire);
        }
    }

    private boolean isNearForgeStation(Player player) {
        org.bukkit.Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return false;
        }
        int vertical = Math.max(1, Math.min(forgeRadius, 2));
        for (int x = -forgeRadius; x <= forgeRadius; x++) {
            for (int y = -vertical; y <= vertical; y++) {
                for (int z = -forgeRadius; z <= forgeRadius; z++) {
                    Block block = world.getBlockAt(center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z);
                    if (forgeStations.contains(block.getType())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean removeFromInventory(PlayerInventory inventory, Material material) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && stack.getType() == material) {
                if (stack.getAmount() <= 1) {
                    inventory.setItem(slot, null);
                } else {
                    stack.setAmount(stack.getAmount() - 1);
                }
                return true;
            }
        }
        ItemStack offHand = inventory.getItemInOffHand();
        if (offHand != null && offHand.getType() == material) {
            if (offHand.getAmount() <= 1) {
                inventory.setItemInOffHand(null);
            } else {
                offHand.setAmount(offHand.getAmount() - 1);
            }
            return true;
        }
        return false;
    }

    private void broadcast(String message) {
        plugin.broadcast(message);
    }

    private Material resolveMaterial(List<String> entries, int index, Material fallback) {
        if (entries == null || index < 0 || index >= entries.size()) {
            return fallback;
        }
        return resolveMaterial(entries.get(index), fallback);
    }

    private Material resolveMaterial(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        if (material == null) {
            plugin.getLogger().warning("[Config] Unknown material '" + raw + "' - using " + fallback);
            return fallback;
        }
        return material;
    }

    private Set<Material> resolveMaterialSet(List<String> entries, Set<Material> fallback) {
        EnumSet<Material> result = EnumSet.noneOf(Material.class);
        if (entries != null) {
            for (String entry : entries) {
                Material material = resolveMaterial(entry, null);
                if (material != null) {
                    result.add(material);
                }
            }
        }
        if (result.isEmpty()) {
            result.addAll(fallback);
        }
        return result;
    }

    private List<String> buildKeywordList(List<String> entries, List<String> fallback) {
        List<String> list = new ArrayList<>();
        if (entries != null) {
            for (String entry : entries) {
                if (entry != null && !entry.isBlank()) {
                    list.add(entry.toLowerCase(Locale.ROOT));
                }
            }
        }
        if (list.isEmpty()) {
            for (String entry : fallback) {
                list.add(entry.toLowerCase(Locale.ROOT));
            }
        }
        return Collections.unmodifiableList(list);
    }

    private enum TraceToken {
        EYE,
        HORN;

        static TraceToken fromKey(String key) {
            if (key == null) {
                return null;
            }
            try {
                return TraceToken.valueOf(key.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }
    }

    private static final class AspectProfile {
        private UUID inheritor;
        private String name;
        private final Set<UUID> shared = new HashSet<>();

        boolean isInheritor(UUID uuid) {
            return uuid != null && uuid.equals(inheritor);
        }
    }

    private record LocationSnapshot(World world, org.bukkit.Location location) {
        static LocationSnapshot of(Player player) {
            return new LocationSnapshot(player.getWorld(), player.getLocation().clone());
        }
    }
}
