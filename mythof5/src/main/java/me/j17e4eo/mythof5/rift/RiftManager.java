package me.j17e4eo.mythof5.rift;

import me.j17e4eo.mythof5.Mythof5;
import me.j17e4eo.mythof5.rift.config.RiftConfig;
import me.j17e4eo.mythof5.rift.config.RiftConfigLoader;
import me.j17e4eo.mythof5.rift.config.RiftRewardEntry;
import me.j17e4eo.mythof5.rift.config.RiftRewardTable;
import me.j17e4eo.mythof5.rift.config.RiftTheme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public final class RiftManager {
    private final Mythof5 plugin;
    private final RiftConfigLoader configLoader;
    private final RiftPersistence persistence;
    private final Map<String, RiftSite> sites = new ConcurrentHashMap<>();
    private final Map<UUID, RiftInstance> entityLookup = new ConcurrentHashMap<>();
    private final Map<UUID, RiftInstance> mechanicLookup = new ConcurrentHashMap<>();
    private final Set<RiftInstance> activeInstances = ConcurrentHashMap.newKeySet();
    private final NamespacedKey entityKey;
    private final NamespacedKey mechanicKey;
    private final RiftCompassService compassService;
    private final NamespacedKey siteKey;
    private BukkitTask tickTask;

    public RiftManager(Mythof5 plugin) {
        this.plugin = plugin;
        this.configLoader = new RiftConfigLoader(plugin);
        this.persistence = new RiftPersistence(plugin, configLoader.getRootFolder());
        this.entityKey = new NamespacedKey(plugin, "rift_entity");
        this.mechanicKey = new NamespacedKey(plugin, "rift_mechanic");
        this.compassService = new RiftCompassService(plugin, this);
        this.siteKey = new NamespacedKey(plugin, "rift_site");
    }

    public void load() {
        try {
            configLoader.load();
        } catch (Exception ex) {
            plugin.getLogger().severe("Failed to load rift configuration: " + ex.getMessage());
        }
        persistence.loadPlayers();
        sites.clear();
        sites.putAll(persistence.loadSites(configLoader.getThemes()));
        sites.values().forEach(this::applySiteMetadata);
        startTickTask();
    }

    private void startTickTask() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                tickInstances();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        for (RiftInstance instance : new HashSet<>(activeInstances)) {
            instance.forceCollapse();
            instance.shutdown();
        }
        activeInstances.clear();
        entityLookup.clear();
        mechanicLookup.clear();
        persistence.saveSites(sites.values());
        persistence.savePlayers();
        compassService.clear();
    }

    private void tickInstances() {
        for (RiftInstance instance : activeInstances) {
            try {
                instance.tick();
            } catch (Exception ex) {
                plugin.getLogger().severe("Failed to tick rift instance at " + instance.getSite().getId() + ": " + ex.getMessage());
            }
        }
    }

    public RiftConfig getConfig() {
        return configLoader.getConfig();
    }

    public RiftConfigLoader getConfigLoader() {
        return configLoader;
    }

    public NamespacedKey getEntityKey() {
        return entityKey;
    }

    public NamespacedKey getMechanicKey() {
        return mechanicKey;
    }

    public RiftCompassService getCompassService() {
        return compassService;
    }

    public void registerEntity(LivingEntity entity, RiftInstance instance) {
        entityLookup.put(entity.getUniqueId(), instance);
    }

    public void unregisterEntity(Entity entity) {
        if (entity != null) {
            entityLookup.remove(entity.getUniqueId());
            mechanicLookup.remove(entity.getUniqueId());
        }
    }

    public void registerMechanic(UUID entityId, RiftInstance instance) {
        mechanicLookup.put(entityId, instance);
    }

    public Optional<RiftInstance> getInstance(LivingEntity entity) {
        return Optional.ofNullable(entityLookup.get(entity.getUniqueId()));
    }

    public Optional<RiftInstance> getInstance(Entity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        RiftInstance instance = entityLookup.get(entity.getUniqueId());
        if (instance != null) {
            return Optional.of(instance);
        }
        return Optional.ofNullable(mechanicLookup.get(entity.getUniqueId()));
    }

    public Optional<RiftInstance> getInstanceAt(Location location) {
        for (RiftInstance instance : activeInstances) {
            if (instance.isInside(location)) {
                return Optional.of(instance);
            }
        }
        return Optional.empty();
    }

    public boolean isInProtectedArea(Location location) {
        return getInstanceAt(location).isPresent();
    }

    public boolean handleActivation(Player player, Block block, boolean forced) {
        if (block == null || block.getType() != getConfig().getActivationBlock()) {
            return false;
        }
        String siteId = null;
        BlockState state = block.getState();
        if (state instanceof TileState tileState) {
            siteId = tileState.getPersistentDataContainer().get(siteKey, PersistentDataType.STRING);
        }
        RiftSite site = siteId != null ? getSite(siteId) : findSiteByLocation(block.getLocation());
        if (site == null) {
            player.sendMessage(Component.text("이 구조물은 아직 균열 좌표에 등록되지 않았습니다.", NamedTextColor.RED));
            return false;
        }
        return startRift(site, player, forced);
    }

    public boolean startRift(RiftSite site, Player activator, boolean forced) {
        if (site.getActiveInstance().isPresent()) {
            activator.sendMessage(Component.text("이미 진행 중인 균열입니다.", NamedTextColor.RED));
            return false;
        }
        long now = System.currentTimeMillis();
        if (!forced && site.isCoolingDown(now)) {
            long seconds = site.getCooldownRemaining(now) / 1000L;
            activator.sendMessage(Component.text("균열이 재정비 중입니다. " + seconds + "초 후에 다시 시도하세요.", NamedTextColor.RED));
            return false;
        }
        if (!forced && activeInstances.size() >= getConfig().getMaxActiveInstances()) {
            activator.sendMessage(Component.text("현재 허용된 최대 균열 수에 도달했습니다.", NamedTextColor.RED));
            return false;
        }
        RiftInstance instance = new RiftInstance(plugin, this, site, site.getTheme(), getConfig());
        site.setActiveInstance(instance);
        activeInstances.add(instance);
        instance.start(activator);
        broadcast(Component.text(activator.getName(), NamedTextColor.GOLD)
                .append(Component.text("님이 균열 " + site.getId() + "을(를) 각성시켰습니다!", NamedTextColor.LIGHT_PURPLE)));
        return true;
    }

    private void broadcast(Component component) {
        Bukkit.getServer().broadcast(component);
    }

    public void handleBossDefeated(RiftInstance instance) {
        instance.broadcast(Component.text("균열이 붕괴 중입니다. 보상을 획득하세요!", NamedTextColor.GOLD));
        List<RiftInstance.ContributionSnapshot> top = instance.getTopContributors(5);
        if (!top.isEmpty()) {
            instance.broadcast(Component.text("— 기여도 순위 —", NamedTextColor.LIGHT_PURPLE));
            int rank = 1;
            for (RiftInstance.ContributionSnapshot snapshot : top) {
                String name = Optional.ofNullable(Bukkit.getOfflinePlayer(snapshot.playerId()).getName()).orElse("알 수 없음");
                Component line = Component.text(rank + ". ", NamedTextColor.GRAY)
                        .append(Component.text(name, NamedTextColor.GOLD))
                        .append(Component.text(" • ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(String.format(Locale.KOREAN, "%.0f", snapshot.damage()), NamedTextColor.RED))
                        .append(Component.text(" 피해", NamedTextColor.GRAY))
                        .append(Component.text(", ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(String.format(Locale.KOREAN, "%.0f", snapshot.support()), NamedTextColor.AQUA))
                        .append(Component.text(" 지원", NamedTextColor.GRAY))
                        .append(Component.text(", ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(snapshot.mechanics() + " 기믹", NamedTextColor.LIGHT_PURPLE));
                instance.broadcast(line);
                rank++;
            }
        }
    }

    public void handleCollapseFinished(RiftInstance instance) {
        RiftSite site = instance.getSite();
        site.setActiveInstance(null);
        activeInstances.remove(instance);
        long cooldownMillis = getConfig().getCooldownSeconds() * 1000L;
        site.setCooldownUntil(System.currentTimeMillis() + cooldownMillis);
        applySiteMetadata(site);
        persistence.saveSites(sites.values());
    }

    public void recordDamage(Player player, Entity target, double damage) {
        if (player == null || target == null || damage <= 0) {
            return;
        }
        RiftInstance instance = entityLookup.get(target.getUniqueId());
        if (instance != null) {
            instance.recordDamage(player, damage);
        }
    }

    public void recordSupport(Player player, double amount) {
        if (player == null || amount <= 0) {
            return;
        }
        getInstanceAt(player.getLocation()).ifPresent(instance -> instance.recordSupport(player, amount));
    }

    public void recordMechanic(Player player, Entity entity) {
        if (entity == null || player == null) {
            return;
        }
        RiftInstance instance = mechanicLookup.get(entity.getUniqueId());
        if (instance != null) {
            instance.recordMechanic(player, entity);
            mechanicLookup.remove(entity.getUniqueId());
            entity.remove();
        }
    }

    public void handleEntityDeath(LivingEntity entity) {
        RiftInstance instance = entityLookup.remove(entity.getUniqueId());
        if (instance != null) {
            instance.handleEntityDeath(entity.getUniqueId());
        }
    }

    public void handlePlayerQuit(UUID uuid) {
        for (RiftInstance instance : activeInstances) {
            instance.removeParticipant(uuid);
        }
    }

    public boolean shouldCancelSpawn(Location location) {
        if (!getConfig().isSuppressNaturalSpawns()) {
            return false;
        }
        for (RiftInstance instance : activeInstances) {
            if (instance.isInside(location)) {
                return true;
            }
        }
        return false;
    }

    public void distributeRewards(RiftInstance instance) {
        RiftRewardTable table = instance.getTheme().getRewardTable();
        if (table == null) {
            return;
        }
        double totalScore = instance.getContributions().values().stream().mapToDouble(RiftContribution::getScore).sum();
        List<ItemStack> partyItems = new ArrayList<>();
        List<String> partyCommands = new ArrayList<>();
        List<String> partyItemSummaries = new ArrayList<>();
        List<String> partyCommandSummaries = new ArrayList<>();
        for (UUID uuid : instance.getParticipants()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            RiftContribution contribution = instance.getContributions().getOrDefault(uuid, new RiftContribution());
            double personalScore = contribution.getScore();
            int rolls = personalRolls(totalScore, personalScore);
            Set<Material> granted = new HashSet<>();
            List<String> personalLog = new ArrayList<>();
            for (int i = 0; i < rolls; i++) {
                RiftRewardEntry entry = drawEntry(table.getPersonal(), granted);
                if (entry == null) {
                    continue;
                }
                if (entry.isCommand()) {
                    executeCommand(entry.getCommand(), player);
                    personalLog.add("명령:" + entry.getCommand());
                } else {
                    ItemStack item = new ItemStack(entry.getMaterial(), randomAmount(entry));
                    granted.add(entry.getMaterial());
                    Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                    for (ItemStack rest : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), rest);
                    }
                    personalLog.add(entry.getMaterial().name() + " x" + item.getAmount());
                }
            }
            if (persistence.shouldGrantWeeklyBonus(uuid, instance.getTheme().getKey(), getConfig().getWeeklyResetDay())) {
                for (RiftRewardEntry entry : table.getWeekly()) {
                    if (entry.isCommand()) {
                        executeCommand(entry.getCommand(), player);
                        personalLog.add("주간 명령:" + entry.getCommand());
                    } else {
                        ItemStack item = new ItemStack(entry.getMaterial(), randomAmount(entry));
                        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                        for (ItemStack rest : leftover.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), rest);
                        }
                        personalLog.add("주간 " + entry.getMaterial().name() + " x" + item.getAmount());
                    }
                }
                persistence.markWeeklyBonus(uuid, instance.getTheme().getKey(), getConfig().getWeeklyResetDay());
                personalLog.add("주간 보너스 적용");
            }
            String logLine = personalLog.isEmpty() ? "획득 없음" : String.join(", ", personalLog);
            instance.logExternal(player.getName() + " 보상: " + logLine + " (기여도 " + String.format(Locale.KOREAN, "%.0f", personalScore) + ")");
        }
        for (RiftRewardEntry entry : table.getParty()) {
            if (entry.isCommand()) {
                partyCommands.add(entry.getCommand());
                partyCommandSummaries.add(entry.getCommand());
            } else {
                ItemStack item = new ItemStack(entry.getMaterial(), randomAmount(entry));
                partyItems.add(item);
                partyItemSummaries.add(item.getType().name() + " x" + item.getAmount());
            }
        }
        if (!partyItems.isEmpty()) {
            spawnPartyChest(instance.getSite().getLocation(), partyItems);
            instance.logExternal("파티 상자 생성: " + String.join(", ", partyItemSummaries));
        }
        for (String command : partyCommands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("@players", instance.getParticipants().stream()
                    .map(uuid -> {
                        Player player = Bukkit.getPlayer(uuid);
                        return player != null ? player.getName() : "";
                    })
                    .collect(Collectors.joining(","))));
        }
        if (!partyCommandSummaries.isEmpty()) {
            instance.logExternal("파티 명령 보상: " + String.join(", ", partyCommandSummaries));
        }
        persistence.savePlayers();
    }

    private int personalRolls(double totalScore, double personalScore) {
        if (totalScore <= 0.0D) {
            return 1;
        }
        double ratio = personalScore / totalScore;
        int rolls = (int) Math.round(1 + ratio * 3);
        return Math.max(1, Math.min(4, rolls));
    }

    private RiftRewardEntry drawEntry(List<RiftRewardEntry> entries, Set<Material> granted) {
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        double totalWeight = entries.stream().mapToDouble(RiftRewardEntry::getWeight).sum();
        if (totalWeight <= 0.0D) {
            return null;
        }
        double target = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cumulative = 0.0D;
        for (RiftRewardEntry entry : entries) {
            if (!entry.isCommand() && granted.contains(entry.getMaterial())) {
                continue;
            }
            cumulative += entry.getWeight();
            if (cumulative >= target) {
                return entry;
            }
        }
        return entries.get(ThreadLocalRandom.current().nextInt(entries.size()));
    }

    private int randomAmount(RiftRewardEntry entry) {
        if (entry.getMaxAmount() <= entry.getMinAmount()) {
            return entry.getMinAmount();
        }
        return ThreadLocalRandom.current().nextInt(entry.getMinAmount(), entry.getMaxAmount() + 1);
    }

    private void executeCommand(String command, Player player) {
        if (command == null || command.isBlank()) {
            return;
        }
        String parsed = command.replace("@player", player.getName());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
    }

    private void spawnPartyChest(Location location, List<ItemStack> items) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        Location chestLoc = location.clone().toCenterLocation();
        chestLoc.setY(Math.floor(chestLoc.getY()));
        Block block = world.getBlockAt(chestLoc);
        block.setType(Material.CHEST, false);
        BlockState state = block.getState();
        if (state instanceof Chest chest) {
            Inventory inventory = chest.getBlockInventory();
            for (ItemStack item : items) {
                inventory.addItem(item);
            }
            chest.update(true);
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (block.getType() == Material.CHEST) {
                        block.setType(Material.AIR);
                    }
                }
            }.runTaskLater(plugin, 20L * 120L);
        }
    }

    public RiftSite findNearestSite(Location location) {
        return findNearestSite(location, false);
    }

    public RiftSite findNearestSite(Location location, boolean onlyAvailable) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        double best = Double.MAX_VALUE;
        RiftSite bestSite = null;
        long now = System.currentTimeMillis();
        for (RiftSite site : sites.values()) {
            if (!site.getWorldName().equalsIgnoreCase(location.getWorld().getName())) {
                continue;
            }
            if (onlyAvailable) {
                if (site.getActiveInstance().isPresent()) {
                    continue;
                }
                if (site.isCoolingDown(now)) {
                    continue;
                }
            }
            double distance = site.getLocation().distanceSquared(location);
            if (distance < best) {
                best = distance;
                bestSite = site;
            }
        }
        return bestSite;
    }

    public RiftSite findSiteByLocation(Location location) {
        for (RiftSite site : sites.values()) {
            if (site.isInside(location, getConfig().getChunkRadius())) {
                return site;
            }
        }
        return null;
    }

    public RiftSite getSite(String id) {
        if (id == null) {
            return null;
        }
        return sites.get(id.toLowerCase(Locale.ROOT));
    }

    public RiftSite registerSite(Location location, String themeKey) {
        RiftTheme theme = configLoader.getTheme(themeKey);
        if (theme == null) {
            throw new IllegalArgumentException("Unknown theme " + themeKey);
        }
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Location has no world");
        }
        Location baseBlock = location.toBlockLocation();
        double centerX = baseBlock.getBlockX() + 0.5D;
        double centerY = baseBlock.getBlockY() + 1.5D;
        double centerZ = baseBlock.getBlockZ() + 0.5D;
        String id = nextSiteId(themeKey);
        RiftSite site = new RiftSite(id, world, centerX, centerY, centerZ, theme);
        sites.put(id.toLowerCase(Locale.ROOT), site);
        buildTotem(world, baseBlock, theme);
        applySiteMetadata(site);
        persistence.saveSites(sites.values());
        return site;
    }

    private String nextSiteId(String themeKey) {
        String base = themeKey.toLowerCase(Locale.ROOT);
        int index = 1;
        while (sites.containsKey(base + "-" + index)) {
            index++;
        }
        return base + "-" + index;
    }

    public Map<String, RiftSite> getSites() {
        return Collections.unmodifiableMap(sites);
    }

    public void showDebugInfo(CommandSender sender, RiftSite site) {
        if (site == null) {
            sender.sendMessage(Component.text("해당 위치에 균열이 없습니다.", NamedTextColor.RED));
            return;
        }
        sender.sendMessage(Component.text("[균열] " + site.getId(), NamedTextColor.GOLD));
        sender.sendMessage(Component.text("  월드: " + site.getWorldName(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  좌표: " + String.format("%.1f %.1f %.1f", site.getX(), site.getY(), site.getZ()), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  테마: " + site.getThemeKey(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  상태: " + site.getActiveInstance().map(inst -> inst.getState().name()).orElse("휴면"), NamedTextColor.GRAY));
        long remaining = site.getCooldownRemaining(System.currentTimeMillis()) / 1000L;
        if (remaining > 0) {
            sender.sendMessage(Component.text("  쿨다운: " + remaining + "초", NamedTextColor.GRAY));
        }
        site.getActiveInstance().ifPresent(instance -> {
            sender.sendMessage(Component.text("  참여자: " + instance.getParticipants().size(), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  스케일링 계수: " + String.format(Locale.KOREAN, "%.2f", instance.getScalingFactor()), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  남은 적: " + instance.getEnemiesRemaining(), NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  남은 기믹: " + instance.getActiveMechanics(), NamedTextColor.GRAY));
            List<RiftInstance.ContributionSnapshot> top = instance.getTopContributors(5);
            if (!top.isEmpty()) {
                sender.sendMessage(Component.text("  기여도 상위:", NamedTextColor.GRAY));
                int rank = 1;
                for (RiftInstance.ContributionSnapshot snapshot : top) {
                    String name = Optional.ofNullable(Bukkit.getOfflinePlayer(snapshot.playerId()).getName()).orElse("알 수 없음");
                    Component line = Component.text(String.format(Locale.KOREAN, "    %d위 %s", rank, name), NamedTextColor.DARK_AQUA)
                            .append(Component.text(String.format(Locale.KOREAN, " | 피해 %.0f", snapshot.damage()), NamedTextColor.RED))
                            .append(Component.text(String.format(Locale.KOREAN, " | 지원 %.0f", snapshot.support()), NamedTextColor.AQUA))
                            .append(Component.text(" | 기믹 " + snapshot.mechanics(), NamedTextColor.LIGHT_PURPLE));
                    sender.sendMessage(line);
                    rank++;
                }
            }
            List<RiftInstance.RiftTimelineEntry> timeline = instance.getTimeline();
            if (!timeline.isEmpty()) {
                sender.sendMessage(Component.text("  최근 이벤트:", NamedTextColor.GRAY));
                long now = System.currentTimeMillis();
                int start = Math.max(0, timeline.size() - 6);
                for (int i = start; i < timeline.size(); i++) {
                    RiftInstance.RiftTimelineEntry entry = timeline.get(i);
                    long secondsAgo = Math.max(0L, (now - entry.timestamp()) / 1000L);
                    String message = String.format(Locale.KOREAN, "    %ds 전 [%s] %s", secondsAgo, entry.state().name(), entry.message());
                    sender.sendMessage(Component.text(message, NamedTextColor.DARK_AQUA));
                }
            }
        });
    }

    private void applySiteMetadata(RiftSite site) {
        Location location = site.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        Block block = world.getBlockAt(location);
        if (block.getType() != getConfig().getActivationBlock()) {
            block.setType(getConfig().getActivationBlock(), false);
        }
        BlockState state = block.getState();
        if (state instanceof TileState tileState) {
            tileState.getPersistentDataContainer().set(siteKey, PersistentDataType.STRING, site.getId());
            tileState.update(true);
        }
    }

    private void buildTotem(World world, Location baseBlock, RiftTheme theme) {
        Material base = theme.resolvePaletteMaterial("base_stone", Material.CHISELED_STONE_BRICKS);
        Material accent = theme.resolvePaletteMaterial("accent", Material.GLOWSTONE);
        Material crystal = theme.resolvePaletteMaterial("crystal", Material.AMETHYST_BLOCK);
        Block baseBlockState = world.getBlockAt(baseBlock);
        Block middleBlock = baseBlockState.getRelative(0, 1, 0);
        Block topBlock = baseBlockState.getRelative(0, 2, 0);
        baseBlockState.setType(base, false);
        middleBlock.setType(getConfig().getActivationBlock(), false);
        topBlock.setType(crystal, false);
        Block accentBlock = topBlock.getRelative(0, 1, 0);
        accentBlock.setType(accent, false);
    }
}
