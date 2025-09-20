package me.j17e4eo.mythof5.rift;

import me.j17e4eo.mythof5.Mythof5;
import me.j17e4eo.mythof5.rift.config.RiftBossConfig;
import me.j17e4eo.mythof5.rift.config.RiftConfig;
import me.j17e4eo.mythof5.rift.config.RiftPhase;
import me.j17e4eo.mythof5.rift.config.RiftSpawnEntry;
import me.j17e4eo.mythof5.rift.config.RiftTheme;
import me.j17e4eo.mythof5.rift.config.RiftWave;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public final class RiftInstance {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final int WAVE_CALLOUT_LEAD_SECONDS = 5;
    private static final int MAX_TIMELINE_ENTRIES = 40;
    private final Mythof5 plugin;
    private final RiftManager manager;
    private final RiftSite site;
    private final RiftTheme theme;
    private final RiftConfig config;
    private final BossBar bossBar;
    private final Set<UUID> participants = new HashSet<>();
    private final Map<UUID, RiftContribution> contributions = new HashMap<>();
    private final Set<UUID> activeEntities = new HashSet<>();
    private final Set<UUID> mechanicEntities = new HashSet<>();
    private final Set<BukkitTask> scheduledTasks = new CopyOnWriteArraySet<>();
    private final Map<BlockKey, BlockData> paletteRestores = new HashMap<>();
    private final Deque<RiftTimelineEntry> timeline = new ArrayDeque<>();
    private final int collapseSeconds;
    private final int chunkRadius;
    private final int evictionSeconds;
    private RiftInstanceState state = RiftInstanceState.DORMANT;
    private long stateStarted;
    private double scalingFactor = 1.0D;
    private int phaseOneWaveTotal;
    private int phaseOneWaveSpawned;
    private boolean phaseOneSpawnsFinished;
    private int phaseTwoWaveTotal;
    private int phaseTwoWaveSpawned;
    private boolean phaseTwoSpawnsFinished;
    private int enemiesRemaining;
    private LivingEntity bossEntity;
    private boolean rewardsDistributed;
    private boolean paletteApplied;
    private long lastParticipantPresence;

    public RiftInstance(Mythof5 plugin, RiftManager manager, RiftSite site, RiftTheme theme, RiftConfig config) {
        this.plugin = plugin;
        this.manager = manager;
        this.site = site;
        this.theme = theme;
        this.config = config;
        this.collapseSeconds = config.getCollapseSeconds();
        this.chunkRadius = config.getChunkRadius();
        this.evictionSeconds = config.getEvictionSeconds();
        this.bossBar = Bukkit.createBossBar(ChatColor.LIGHT_PURPLE + theme.getDisplayName(), theme.getBarColor(), BarStyle.SOLID);
        this.bossBar.setProgress(0.0D);
        this.bossBar.setVisible(false);
        this.lastParticipantPresence = System.currentTimeMillis();
    }

    public void start(Player activator) {
        if (state != RiftInstanceState.DORMANT) {
            return;
        }
        addParticipant(activator);
        lastParticipantPresence = System.currentTimeMillis();
        this.scalingFactor = computeScalingFactor();
        this.state = RiftInstanceState.AWAKENING;
        this.stateStarted = System.currentTimeMillis();
        this.bossBar.setTitle(ChatColor.LIGHT_PURPLE + theme.getDisplayName());
        this.bossBar.setVisible(true);
        logEvent(RiftInstanceState.AWAKENING, "Activated by " + activator.getName() + " (scaling x" + String.format(Locale.KOREAN, "%.2f", scalingFactor) + ")");
        playActivationEffects();
        applyDecorations();
        broadcastFlavor(theme.getFlavor().activation(), Map.of(
                "player", activator.getName(),
                "theme", theme.getDisplayName()
        ), NamedTextColor.LIGHT_PURPLE);
        broadcastFlavor(theme.getFlavor().awakening(), Map.of(
                "theme", theme.getDisplayName(),
                "remaining", String.valueOf(theme.getAwakeningSeconds())
        ), NamedTextColor.LIGHT_PURPLE);
        broadcast(Component.text("균열이 각성하고 있습니다...", NamedTextColor.LIGHT_PURPLE));
        broadcast(Component.text(String.format(Locale.KOREAN, "난이도 계수 %.2f", scalingFactor), NamedTextColor.GRAY));
    }

    private void playActivationEffects() {
        Location center = site.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        world.playSound(center, config.getActivationSound(), 1.3f, 0.85f);
        world.spawnParticle(Particle.PORTAL, center, 50, 1.5, 1.0, 1.5, 0.01);
    }

    private void applyDecorations() {
        Location center = site.getLocation();
        World world = center.getWorld();
        if (world == null || paletteApplied) {
            return;
        }
        paletteApplied = true;
        paletteRestores.clear();
        Material base = theme.resolvePaletteMaterial("base_stone", Material.COBBLED_DEEPSLATE);
        Material accent = theme.resolvePaletteMaterial("accent", Material.GLOWSTONE);
        Material crystal = theme.resolvePaletteMaterial("crystal", Material.AMETHYST_BLOCK);
        int baseY = center.getBlockY() - 1;
        int[][] pedestals = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}};
        for (int[] offset : pedestals) {
            int x = center.getBlockX() + offset[0];
            int z = center.getBlockZ() + offset[1];
            replaceBlock(world.getBlockAt(x, baseY, z), base);
            replaceBlock(world.getBlockAt(x, baseY + 1, z), accent);
            replaceBlock(world.getBlockAt(x, baseY + 2, z), crystal);
        }
        int radius = Math.min(6, Math.max(3, theme.getSpawnRadius() / 2));
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (Math.abs(x) == radius || Math.abs(z) == radius) {
                    int worldX = center.getBlockX() + x;
                    int worldZ = center.getBlockZ() + z;
                    if (Math.abs(x) <= 1 && Math.abs(z) <= 1) {
                        continue;
                    }
                    Block block = world.getBlockAt(worldX, baseY, worldZ);
                    if (block.getType().isSolid()) {
                        replaceBlock(block, base);
                    }
                }
            }
        }
        logEvent(RiftInstanceState.AWAKENING, "Applied palette for theme " + theme.getKey());
    }

    private void replaceBlock(Block block, Material material) {
        BlockKey key = new BlockKey(block.getX(), block.getY(), block.getZ());
        paletteRestores.putIfAbsent(key, block.getBlockData().clone());
        if (block.getType() != material) {
            block.setType(material, false);
        }
    }

    private void restorePalette() {
        if (!paletteApplied) {
            return;
        }
        World world = site.getLocation().getWorld();
        if (world == null) {
            paletteRestores.clear();
            paletteApplied = false;
            return;
        }
        for (Map.Entry<BlockKey, BlockData> entry : paletteRestores.entrySet()) {
            Block block = world.getBlockAt(entry.getKey().x(), entry.getKey().y(), entry.getKey().z());
            block.setBlockData(entry.getValue(), false);
        }
        paletteRestores.clear();
        paletteApplied = false;
    }

    private double computeScalingFactor() {
        double participantFactor = Math.max(1, participants.size());
        double equipmentScore = participants.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .filter(Player::isOnline)
                .mapToDouble(this::calculateEquipmentScore)
                .average()
                .orElse(1.0D);
        double danger = theme.getDangerLevel();
        return 1.0D + participantFactor * 0.15D + equipmentScore * 0.05D + danger * 0.3D;
    }

    private double calculateEquipmentScore(Player player) {
        double score = 0.0D;
        score += scoreItem(player.getInventory().getHelmet());
        score += scoreItem(player.getInventory().getChestplate());
        score += scoreItem(player.getInventory().getLeggings());
        score += scoreItem(player.getInventory().getBoots());
        score += scoreItem(player.getInventory().getItemInMainHand());
        return score / 5.0D;
    }

    private double scoreItem(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return 0.0D;
        }
        MaterialTier tier = MaterialTier.fromMaterial(stack.getType());
        return tier.getScore();
    }

    public void tick() {
        if (state == RiftInstanceState.DORMANT) {
            return;
        }
        refreshParticipants();
        checkDesertion();
        updateBossBar();
        if (state == RiftInstanceState.AWAKENING) {
            handleAwakening();
        } else if (state == RiftInstanceState.PHASE_ONE || state == RiftInstanceState.PHASE_TWO) {
            handlePhase();
        } else if (state == RiftInstanceState.BOSS) {
            updateBossPhase();
        } else if (state == RiftInstanceState.COLLAPSE) {
            handleCollapse();
        }
        spawnBoundaryParticles();
    }

    private void checkDesertion() {
        if (state == RiftInstanceState.COLLAPSE || state == RiftInstanceState.COOLDOWN) {
            return;
        }
        if (participants.isEmpty()) {
            long idle = System.currentTimeMillis() - lastParticipantPresence;
            if (idle >= evictionSeconds * 1000L) {
                forceCollapse();
            }
        }
    }

    private void handleAwakening() {
        long elapsed = elapsedSeconds();
        long total = theme.getAwakeningSeconds();
        if (elapsed >= total) {
            beginPhaseOne();
        } else {
            double progress = total <= 0 ? 0.0D : (double) elapsed / (double) total;
            bossBar.setProgress(Math.min(1.0D, progress));
        }
    }

    private void handlePhase() {
        RiftPhase current = state == RiftInstanceState.PHASE_ONE ? theme.getPhaseOne() : theme.getPhaseTwo();
        long elapsed = elapsedSeconds();
        double progress = Math.min(1.0D, (double) elapsed / Math.max(1, current.getDurationSeconds()));
        bossBar.setProgress(progress);
        if (elapsed >= current.getDurationSeconds()) {
            if (state == RiftInstanceState.PHASE_ONE) {
                beginPhaseTwo();
            } else if (state == RiftInstanceState.PHASE_TWO) {
                beginBossPhase();
            }
        }
    }

    private void updateBossPhase() {
        if (bossEntity == null || bossEntity.isDead()) {
            onBossDefeated();
            return;
        }
        AttributeInstance maxHealth = bossEntity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            bossBar.setProgress(Math.max(0.0D, bossEntity.getHealth() / maxHealth.getValue()));
        }
    }

    private void handleCollapse() {
        long elapsed = elapsedSeconds();
        double progress = 1.0D - Math.min(1.0D, (double) elapsed / Math.max(1, collapseSeconds));
        bossBar.setProgress(Math.max(0.0D, progress));
        if (!rewardsDistributed) {
            manager.distributeRewards(this);
            rewardsDistributed = true;
        }
        if (elapsed >= collapseSeconds) {
            finishCollapse();
        }
    }

    private void finishCollapse() {
        state = RiftInstanceState.COOLDOWN;
        bossBar.setVisible(false);
        bossBar.removeAll();
        cancelScheduledTasks();
        restorePalette();
        manager.handleCollapseFinished(this);
        logEvent(RiftInstanceState.COOLDOWN, "Collapse finished. Rift cooling down");
    }

    public void forceCollapse() {
        if (state == RiftInstanceState.COLLAPSE || state == RiftInstanceState.COOLDOWN) {
            return;
        }
        state = RiftInstanceState.COLLAPSE;
        stateStarted = System.currentTimeMillis();
        broadcast(Component.text("균열이 강제로 붕괴됩니다.", NamedTextColor.RED));
        logEvent(RiftInstanceState.COLLAPSE, "Collapse forced due to desertion or admin command");
        if (!participants.isEmpty()) {
            rewardsDistributed = false;
            manager.distributeRewards(this);
        }
        rewardsDistributed = true;
        finishCollapse();
    }

    private void beginPhaseOne() {
        state = RiftInstanceState.PHASE_ONE;
        stateStarted = System.currentTimeMillis();
        bossBar.setColor(BarColor.PURPLE);
        bossBar.setTitle(ChatColor.LIGHT_PURPLE + "Phase 1 - " + theme.getDisplayName());
        phaseOneWaveTotal = theme.getPhaseOne().getWaves().size();
        phaseOneWaveSpawned = 0;
        phaseOneSpawnsFinished = phaseOneWaveTotal == 0;
        enemiesRemaining = 0;
        scheduleWaves(theme.getPhaseOne(), RiftInstanceState.PHASE_ONE);
        broadcast(Component.text(theme.getPhaseOne().getName() + " 돌입!", NamedTextColor.AQUA));
        logEvent(RiftInstanceState.PHASE_ONE, "Phase one started with " + phaseOneWaveTotal + " waves");
    }

    private void beginPhaseTwo() {
        state = RiftInstanceState.PHASE_TWO;
        stateStarted = System.currentTimeMillis();
        bossBar.setColor(BarColor.BLUE);
        bossBar.setTitle(ChatColor.AQUA + "Phase 2 - " + theme.getDisplayName());
        phaseTwoWaveTotal = theme.getPhaseTwo().getWaves().size();
        phaseTwoWaveSpawned = 0;
        phaseTwoSpawnsFinished = phaseTwoWaveTotal == 0;
        enemiesRemaining = 0;
        scheduleWaves(theme.getPhaseTwo(), RiftInstanceState.PHASE_TWO);
        broadcast(Component.text(theme.getPhaseTwo().getName() + " 돌입!", NamedTextColor.AQUA));
        logEvent(RiftInstanceState.PHASE_TWO, "Phase two started with " + phaseTwoWaveTotal + " waves");
    }

    private void beginBossPhase() {
        state = RiftInstanceState.BOSS;
        stateStarted = System.currentTimeMillis();
        bossBar.setColor(BarColor.RED);
        bossBar.setTitle(ChatColor.RED + "Boss - " + theme.getDisplayName());
        spawnBoss();
        broadcast(Component.text(theme.getBossConfig().getName() + " 등장!", NamedTextColor.RED));
        broadcastFlavor(theme.getFlavor().boss(), Map.of(
                "boss", theme.getBossConfig().getName(),
                "theme", theme.getDisplayName()
        ), NamedTextColor.RED);
        logEvent(RiftInstanceState.BOSS, "Boss phase begun: " + theme.getBossConfig().getName());
    }

    private void spawnBoss() {
        Location center = site.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        RiftBossConfig bossConfig = theme.getBossConfig();
        if (bossConfig.getType().getEntityClass() == null || !LivingEntity.class.isAssignableFrom(bossConfig.getType().getEntityClass())) {
            plugin.getLogger().warning("Boss entity type " + bossConfig.getType() + " is not a living entity.");
            return;
        }
        LivingEntity entity = (LivingEntity) world.spawnEntity(center, bossConfig.getType());
        entity.customName(Component.text(bossConfig.getName(), NamedTextColor.RED));
        entity.setCustomNameVisible(true);
        double health = bossConfig.getBaseHealth() * scalingFactor;
        AttributeInstance maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(Math.max(1.0D, health));
            entity.setHealth(maxHealth.getValue());
        }
        AttributeInstance damage = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(damage.getBaseValue() * bossConfig.getDamageMultiplier() * scalingFactor);
        }
        for (PotionEffect effect : bossConfig.getPotionEffects()) {
            entity.addPotionEffect(effect);
        }
        entity.getPersistentDataContainer().set(manager.getEntityKey(), PersistentDataType.BYTE, (byte) 1);
        manager.registerEntity(entity, this);
        bossEntity = entity;
    }

    private void scheduleWaves(RiftPhase phase, RiftInstanceState phaseState) {
        int index = 0;
        int total = phaseState == RiftInstanceState.PHASE_ONE ? phaseOneWaveTotal : phaseTwoWaveTotal;
        for (RiftWave wave : phase.getWaves()) {
            final int waveNumber = ++index;
            int delaySeconds = Math.max(0, wave.getDelaySeconds());
            if (delaySeconds <= WAVE_CALLOUT_LEAD_SECONDS) {
                announceWaveIncoming(phaseState, waveNumber, total, wave);
            } else {
                BukkitTask calloutTask = new BukkitRunnable() {
                    @Override
                    public void run() {
                        announceWaveIncoming(phaseState, waveNumber, total, wave);
                    }
                }.runTaskLater(plugin, (delaySeconds - WAVE_CALLOUT_LEAD_SECONDS) * 20L);
                scheduledTasks.add(calloutTask);
            }
            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    spawnWave(wave, phaseState, waveNumber, total);
                }
            }.runTaskLater(plugin, delaySeconds * 20L);
            scheduledTasks.add(task);
        }
    }

    private void announceWaveIncoming(RiftInstanceState phaseState, int waveNumber, int total, RiftWave wave) {
        String summary = describeWave(wave);
        String phaseLabel = phaseState == RiftInstanceState.PHASE_ONE ? theme.getPhaseOne().getName() : theme.getPhaseTwo().getName();
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("wave", String.valueOf(waveNumber));
        placeholders.put("total", String.valueOf(total));
        placeholders.put("summary", summary);
        placeholders.put("phase", phaseLabel);
        placeholders.put("theme", theme.getDisplayName());
        List<String> templates = phaseState == RiftInstanceState.PHASE_ONE ? theme.getFlavor().phaseOneCallouts() : theme.getFlavor().phaseTwoCallouts();
        if (templates.isEmpty()) {
            broadcast(Component.text(String.format(Locale.KOREAN, "%s 웨이브 %d/%d 예고: %s", phaseLabel, waveNumber, total, summary), NamedTextColor.AQUA));
        } else {
            broadcastFlavor(templates, placeholders, NamedTextColor.AQUA);
        }
        logEvent(phaseState, "Wave " + waveNumber + "/" + total + " incoming: " + summary);
    }

    private void spawnWave(RiftWave wave, RiftInstanceState waveState, int waveIndex, int waveTotal) {
        scheduledTasks.removeIf(task -> task.isCancelled());
        Location center = site.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        int spawned = 0;
        List<String> summary = new ArrayList<>();
        for (RiftSpawnEntry entry : wave.getSpawns()) {
            if (entry.getType().getEntityClass() == null || !LivingEntity.class.isAssignableFrom(entry.getType().getEntityClass())) {
                continue;
            }
            for (int i = 0; i < entry.getAmount(); i++) {
                Location spawnLoc = findSpawnLocation(center, entry.getRadius());
                Entity spawnedEntity = world.spawnEntity(spawnLoc, entry.getType());
                if (spawnedEntity instanceof LivingEntity living) {
                    enhanceEntity(living);
                    living.getPersistentDataContainer().set(manager.getEntityKey(), PersistentDataType.BYTE, (byte) 1);
                    manager.registerEntity(living, this);
                    activeEntities.add(living.getUniqueId());
                    spawned++;
                } else {
                    spawnedEntity.remove();
                }
            }
            if (entry.getAmount() > 0) {
                summary.add(formatSpawnLabel(entry));
            }
        }
        enemiesRemaining += spawned;
        if (wave.getMechanicCrystals() > 0) {
            for (int i = 0; i < wave.getMechanicCrystals(); i++) {
                spawnMechanicCrystal(center);
            }
            broadcast(Component.text(wave.getMechanicCrystals() + "개의 균열 수정이 활성화되었습니다!", NamedTextColor.LIGHT_PURPLE));
            broadcastFlavor(theme.getFlavor().mechanic(), Map.of(
                    "count", String.valueOf(wave.getMechanicCrystals()),
                    "wave", String.valueOf(waveIndex),
                    "phase", waveState == RiftInstanceState.PHASE_ONE ? theme.getPhaseOne().getName() : theme.getPhaseTwo().getName()
            ), NamedTextColor.LIGHT_PURPLE);
            logEvent(waveState, wave.getMechanicCrystals() + " mechanic crystals activated");
        }
        String phaseLabel = waveState == RiftInstanceState.PHASE_ONE ? theme.getPhaseOne().getName() : theme.getPhaseTwo().getName();
        String summaryText = summary.isEmpty() ? "휴식" : String.join(", ", summary);
        if (spawned > 0 && !summary.isEmpty()) {
            broadcast(Component.text(String.format(Locale.KOREAN, "%s 웨이브 %d/%d: %s", phaseLabel, waveIndex, waveTotal, summaryText), NamedTextColor.RED));
        }
        logEvent(waveState, "Wave " + waveIndex + "/" + waveTotal + " spawned " + spawned + " mobs: " + summaryText);
        if (waveState == RiftInstanceState.PHASE_ONE) {
            phaseOneWaveSpawned++;
            if (phaseOneWaveSpawned >= phaseOneWaveTotal) {
                phaseOneSpawnsFinished = true;
                checkAdvanceFromPhase(RiftInstanceState.PHASE_ONE);
                logEvent(RiftInstanceState.PHASE_ONE, "Phase one waves completed");
            }
        } else if (waveState == RiftInstanceState.PHASE_TWO) {
            phaseTwoWaveSpawned++;
            if (phaseTwoWaveSpawned >= phaseTwoWaveTotal) {
                phaseTwoSpawnsFinished = true;
                checkAdvanceFromPhase(RiftInstanceState.PHASE_TWO);
                logEvent(RiftInstanceState.PHASE_TWO, "Phase two waves completed");
            }
        }
    }

    private Location findSpawnLocation(Location center, double radius) {
        World world = center.getWorld();
        if (world == null) {
            return center;
        }
        double angle = Math.random() * Math.PI * 2;
        double distance = radius * Math.random();
        double x = center.getX() + Math.cos(angle) * distance;
        double z = center.getZ() + Math.sin(angle) * distance;
        int y = world.getHighestBlockYAt((int) Math.round(x), (int) Math.round(z));
        return new Location(world, x, y + 1.0D, z);
    }

    private String formatSpawnLabel(RiftSpawnEntry entry) {
        String name = entry.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        if (!name.isEmpty()) {
            name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
        return name + " x" + entry.getAmount();
    }

    private void spawnMechanicCrystal(Location center) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Location location = center.clone().add((Math.random() - 0.5) * theme.getSpawnRadius(), 0.0, (Math.random() - 0.5) * theme.getSpawnRadius());
        int highest = world.getHighestBlockYAt(location);
        location.setY(highest + 1.0D);
        ArmorStand stand = world.spawn(location, ArmorStand.class, armorStand -> {
            armorStand.setInvisible(true);
            armorStand.setMarker(true);
            armorStand.customName(Component.text("균열 수정", NamedTextColor.LIGHT_PURPLE));
            armorStand.setCustomNameVisible(true);
            armorStand.getPersistentDataContainer().set(manager.getMechanicKey(), PersistentDataType.BYTE, (byte) 1);
        });
        mechanicEntities.add(stand.getUniqueId());
        manager.registerMechanic(stand.getUniqueId(), this);
    }

    private void enhanceEntity(LivingEntity living) {
        AttributeInstance health = living.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            double newHealth = health.getBaseValue() * scalingFactor;
            health.setBaseValue(newHealth);
            living.setHealth(newHealth);
        }
        AttributeInstance damage = living.getAttribute(Attribute.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(damage.getBaseValue() * (0.8D + scalingFactor * 0.25D));
        }
    }

    private void refreshParticipants() {
        Location center = site.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        Set<UUID> inside = new HashSet<>();
        for (Player player : world.getPlayers()) {
            if (site.isInside(player.getLocation(), chunkRadius)) {
                inside.add(player.getUniqueId());
                if (addParticipant(player)) {
                    scalingFactor = Math.max(scalingFactor, computeScalingFactor());
                }
            }
        }
        if (!inside.isEmpty()) {
            lastParticipantPresence = System.currentTimeMillis();
        }
        participants.removeIf(uuid -> {
            if (!inside.contains(uuid)) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) {
                    bossBar.removePlayer(player);
                }
                logEvent("Participant left area: " + (player != null ? player.getName() : uuid));
                return true;
            }
            return false;
        });
    }

    private void updateBossBar() {
        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                if (!bossBar.getPlayers().contains(player)) {
                    bossBar.addPlayer(player);
                }
                long remaining = 0L;
                if (state == RiftInstanceState.AWAKENING) {
                    remaining = theme.getAwakeningSeconds() - elapsedSeconds();
                } else if (state == RiftInstanceState.PHASE_ONE) {
                    remaining = theme.getPhaseOne().getDurationSeconds() - elapsedSeconds();
                } else if (state == RiftInstanceState.PHASE_TWO) {
                    remaining = theme.getPhaseTwo().getDurationSeconds() - elapsedSeconds();
                } else if (state == RiftInstanceState.COLLAPSE) {
                    remaining = collapseSeconds - elapsedSeconds();
                }
                remaining = Math.max(0L, remaining);
                Component action = Component.text(stateDisplay(), NamedTextColor.GOLD)
                        .append(Component.text(" · ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(formatDuration(remaining), NamedTextColor.AQUA));
                if (state == RiftInstanceState.PHASE_ONE || state == RiftInstanceState.PHASE_TWO) {
                    action = action.append(Component.text(" · 적 " + Math.max(0, enemiesRemaining), NamedTextColor.RED));
                } else if (state == RiftInstanceState.BOSS && bossEntity != null) {
                    action = action.append(Component.text(" · HP " + String.format(Locale.KOREAN, "%.0f", Math.max(0.0D, bossEntity.getHealth())), NamedTextColor.RED));
                }
                if (!mechanicEntities.isEmpty()) {
                    action = action.append(Component.text(" · 기믹 " + mechanicEntities.size(), NamedTextColor.LIGHT_PURPLE));
                }
                player.sendActionBar(action);
            }
        }
    }

    private String stateDisplay() {
        return switch (state) {
            case AWAKENING -> "각성";
            case PHASE_ONE -> "위상 1";
            case PHASE_TWO -> "위상 2";
            case BOSS -> "보스";
            case COLLAPSE -> "붕괴";
            case COOLDOWN -> "재정비";
            default -> "휴면";
        };
    }

    private String formatDuration(long seconds) {
        Duration duration = Duration.ofSeconds(Math.max(0, seconds));
        long minutes = duration.toMinutes();
        long secs = duration.minusMinutes(minutes).getSeconds();
        return String.format("%02d:%02d", minutes, secs);
    }

    private void spawnBoundaryParticles() {
        Location center = site.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        int particles = 20;
        double radius = theme.getSpawnRadius();
        for (int i = 0; i < particles; i++) {
            double angle = (Math.PI * 2 / particles) * i;
            double x = center.getX() + Math.cos(angle) * radius;
            double z = center.getZ() + Math.sin(angle) * radius;
            double y = center.getY() + 0.5;
            world.spawnParticle(Particle.ENCHANT, x, y, z, 2, 0.1, 0.1, 0.1, 0.0);
        }
    }

    private boolean addParticipant(Player player) {
        if (player == null) {
            return false;
        }
        if (participants.add(player.getUniqueId())) {
            bossBar.addPlayer(player);
            contributions.putIfAbsent(player.getUniqueId(), new RiftContribution());
            logEvent("Participant joined: " + player.getName());
            return true;
        }
        return false;
    }

    private long elapsedSeconds() {
        return (System.currentTimeMillis() - stateStarted) / 1000L;
    }

    public void recordDamage(Player player, double amount) {
        contributions.computeIfAbsent(player.getUniqueId(), uuid -> new RiftContribution()).addDamage(amount);
    }

    public void recordSupport(Player player, double amount) {
        contributions.computeIfAbsent(player.getUniqueId(), uuid -> new RiftContribution()).addSupport(amount);
    }

    public void recordMechanic(Player player, Entity mechanic) {
        if (mechanic == null) {
            return;
        }
        if (mechanicEntities.remove(mechanic.getUniqueId())) {
            contributions.computeIfAbsent(player.getUniqueId(), uuid -> new RiftContribution()).addMechanic();
            triggerMechanicEffect(player, mechanic.getLocation());
            player.sendMessage(Component.text("균열 수정이 공명하여 전투를 지원합니다!", NamedTextColor.AQUA));
            broadcast(Component.text(player.getName() + "님이 균열 수정을 공명시켰습니다!", NamedTextColor.LIGHT_PURPLE));
            logEvent("Mechanic crystal resolved by " + player.getName());
        }
    }

    private void triggerMechanicEffect(Player player, Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        double radius = 8.0D;
        double healAmount = 6.0D;
        double radiusSquared = radius * radius;
        for (UUID uuid : participants) {
            Player target = Bukkit.getPlayer(uuid);
            if (target != null && target.getWorld().equals(world) && target.getLocation().distanceSquared(location) <= radiusSquared) {
                AttributeInstance attribute = target.getAttribute(Attribute.MAX_HEALTH);
                double max = attribute != null ? attribute.getValue() : target.getHealth();
                target.setHealth(Math.min(max, target.getHealth() + healAmount));
                target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 6, 1, true, true));
                target.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 15, 1, true, true));
            }
        }
        for (UUID uuid : new HashSet<>(activeEntities)) {
            Entity mob = Bukkit.getEntity(uuid);
            if (mob instanceof LivingEntity living && living.getWorld().equals(world)) {
                if (living.getLocation().distanceSquared(location) <= radiusSquared) {
                    living.damage(6.0D, player);
                }
            }
        }
        if (bossEntity != null && !bossEntity.isDead() && bossEntity.getWorld().equals(world) && bossEntity.getLocation().distanceSquared(location) <= radiusSquared) {
            bossEntity.damage(8.0D, player);
        }
        world.spawnParticle(Particle.END_ROD, location, 40, 0.6, 0.6, 0.6, 0.02);
        world.playSound(location, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, 1.2f);
    }

    public boolean containsEntity(UUID uuid) {
        return activeEntities.contains(uuid) || (bossEntity != null && bossEntity.getUniqueId().equals(uuid)) || mechanicEntities.contains(uuid);
    }

    public void handleEntityDeath(UUID uuid) {
        if (bossEntity != null && bossEntity.getUniqueId().equals(uuid)) {
            onBossDefeated();
            return;
        }
        if (activeEntities.remove(uuid)) {
            enemiesRemaining = Math.max(0, enemiesRemaining - 1);
            checkAdvanceFromPhase(state);
        }
    }

    private void checkAdvanceFromPhase(RiftInstanceState currentState) {
        if (currentState == RiftInstanceState.PHASE_ONE && phaseOneSpawnsFinished && enemiesRemaining <= 0) {
            beginPhaseTwo();
        } else if (currentState == RiftInstanceState.PHASE_TWO && phaseTwoSpawnsFinished && enemiesRemaining <= 0) {
            beginBossPhase();
        }
    }

    private void onBossDefeated() {
        if (state == RiftInstanceState.COLLAPSE || state == RiftInstanceState.COOLDOWN) {
            return;
        }
        state = RiftInstanceState.COLLAPSE;
        stateStarted = System.currentTimeMillis();
        bossBar.setColor(BarColor.GREEN);
        bossBar.setTitle(ChatColor.GREEN + "붕괴 - " + theme.getDisplayName());
        World world = site.getLocation().getWorld();
        if (world != null) {
            world.playSound(site.getLocation(), config.getCollapseSound(), 1.0f, 0.75f);
        }
        broadcastFlavor(theme.getFlavor().collapse(), Map.of(
                "theme", theme.getDisplayName(),
                "boss", theme.getBossConfig().getName()
        ), NamedTextColor.GOLD);
        logEvent(RiftInstanceState.COLLAPSE, "Boss defeated. Collapse initiated");
        manager.handleBossDefeated(this);
    }

    private void cancelScheduledTasks() {
        for (BukkitTask task : scheduledTasks) {
            task.cancel();
        }
        scheduledTasks.clear();
    }

    public boolean isInside(Location location) {
        return site.isInside(location, chunkRadius);
    }

    public RiftInstanceState getState() {
        return state;
    }

    public Set<UUID> getParticipants() {
        return Collections.unmodifiableSet(participants);
    }

    public Map<UUID, RiftContribution> getContributions() {
        return Collections.unmodifiableMap(contributions);
    }

    public List<RiftTimelineEntry> getTimeline() {
        return List.copyOf(timeline);
    }

    public void logExternal(String message) {
        logEvent(message);
    }

    private void logEvent(String message) {
        logEvent(state, message);
    }

    private void logEvent(RiftInstanceState context, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        if (timeline.size() >= MAX_TIMELINE_ENTRIES) {
            timeline.removeFirst();
        }
        timeline.addLast(new RiftTimelineEntry(System.currentTimeMillis(), context, message));
    }

    private void broadcastFlavor(List<String> templates, Map<String, String> placeholders, NamedTextColor fallbackColor) {
        if (templates == null || templates.isEmpty()) {
            return;
        }
        String template = templates.get(ThreadLocalRandom.current().nextInt(templates.size()));
        if (template == null || template.isBlank()) {
            return;
        }
        String resolved = applyPlaceholders(template, placeholders);
        Component component = LEGACY.deserialize(resolved);
        if (fallbackColor != null) {
            component = component.colorIfAbsent(fallbackColor);
        }
        broadcast(component);
    }

    private String applyPlaceholders(String template, Map<String, String> placeholders) {
        String result = template;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return result;
    }

    private String describeWave(RiftWave wave) {
        if (wave.getSpawns().isEmpty()) {
            return "휴식";
        }
        StringJoiner joiner = new StringJoiner(", ");
        for (RiftSpawnEntry entry : wave.getSpawns()) {
            joiner.add(formatSpawnLabel(entry));
        }
        if (wave.getMechanicCrystals() > 0) {
            joiner.add("균열 수정 x" + wave.getMechanicCrystals());
        }
        return joiner.toString();
    }

    public void broadcast(Component component) {
        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(component);
            }
        }
    }

    public List<ContributionSnapshot> getTopContributors(int limit) {
        return contributions.entrySet().stream()
                .map(entry -> new ContributionSnapshot(entry.getKey(), entry.getValue().getDamage(), entry.getValue().getSupport(), entry.getValue().getMechanics()))
                .sorted(Comparator.comparingDouble(ContributionSnapshot::score).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public RiftSite getSite() {
        return site;
    }

    public RiftTheme getTheme() {
        return theme;
    }

    public double getScalingFactor() {
        return scalingFactor;
    }

    public int getEnemiesRemaining() {
        return enemiesRemaining;
    }

    public int getActiveMechanics() {
        return mechanicEntities.size();
    }

    public void shutdown() {
        cancelScheduledTasks();
        bossBar.removeAll();
        if (bossEntity != null) {
            bossEntity.remove();
        }
        restorePalette();
        for (UUID uuid : new HashSet<>(activeEntities)) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) {
                entity.remove();
            }
        }
        activeEntities.clear();
        for (UUID uuid : mechanicEntities) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    public void removeParticipant(UUID uuid) {
        participants.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            bossBar.removePlayer(player);
        }
        logEvent("Participant departed: " + (player != null ? player.getName() : uuid));
    }

    public record ContributionSnapshot(UUID playerId, double damage, double support, int mechanics) {
        public double score() {
            return damage + mechanics * 25.0D + support * 0.75D;
        }
    }

    public record RiftTimelineEntry(long timestamp, RiftInstanceState state, String message) {
    }

    private record BlockKey(int x, int y, int z) {
    }

    private enum MaterialTier {
        WOOD(0.3D),
        STONE(0.5D),
        IRON(0.7D),
        GOLD(0.6D),
        DIAMOND(1.0D),
        NETHERITE(1.2D),
        OTHER(0.4D);

        private final double score;

        MaterialTier(double score) {
            this.score = score;
        }

        public double getScore() {
            return score;
        }

        public static MaterialTier fromMaterial(org.bukkit.Material material) {
            String name = material.name();
            if (name.contains("NETHERITE")) {
                return NETHERITE;
            }
            if (name.contains("DIAMOND")) {
                return DIAMOND;
            }
            if (name.contains("IRON")) {
                return IRON;
            }
            if (name.contains("GOLD")) {
                return GOLD;
            }
            if (name.contains("STONE")) {
                return STONE;
            }
            if (name.contains("WOODEN") || name.contains("LEATHER")) {
                return WOOD;
            }
            return OTHER;
        }
    }
}
