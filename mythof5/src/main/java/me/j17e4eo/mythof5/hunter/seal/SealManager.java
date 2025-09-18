package me.j17e4eo.mythof5.hunter.seal;

import me.j17e4eo.mythof5.Mythof5;
import me.j17e4eo.mythof5.config.Messages;
import me.j17e4eo.mythof5.hunter.HunterManager;
import me.j17e4eo.mythof5.hunter.data.HunterProfile;
import me.j17e4eo.mythof5.hunter.seal.data.GoblinFlame;
import me.j17e4eo.mythof5.hunter.seal.data.PlayerSealProfile;
import me.j17e4eo.mythof5.hunter.seal.data.PlayerSealStorage;
import me.j17e4eo.mythof5.hunter.seal.data.WeaponSeal;
import me.j17e4eo.mythof5.inherit.AspectManager;
import me.j17e4eo.mythof5.inherit.aspect.GoblinAspect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Coordinates the hunter weapon seal system including goblin flame drops,
 * storage and GUI interactions.
 */
public class SealManager implements Listener {

    private final Mythof5 plugin;
    private final Messages messages;
    private final HunterManager hunterManager;
    private final AspectManager aspectManager;
    private final PlayerSealStorage storage;
    private final NamespacedKey flameKey;
    private final NamespacedKey flameAspectKey;
    private final NamespacedKey flameTierKey;
    private final NamespacedKey flameOwnerKey;
    private final NamespacedKey flameTimeKey;
    private final NamespacedKey weaponSealKey;
    private final NamespacedKey weaponAspectKey;
    private final NamespacedKey weaponTierKey;
    private final NamespacedKey weaponOwnerKey;
    private final Map<UUID, SealSession> sessions = new HashMap<>();
    private final Map<UUID, ActiveSeal> activeSeals = new HashMap<>();
    private final Set<Material> weaponWhitelist;
    private final Random random = new Random();
    private final boolean enabled;
    private final boolean allowUnseal;
    private final boolean allowUpgrade;
    private final boolean autoPopup;
    private final int guiRows;
    private final int dropAmount;
    private final boolean dropAlways;
    private final boolean dropAnnounce;
    private final long protectMillis;
    private final int defaultTier;
    private final Map<Integer, ForceTierSettings> forceTiers = new HashMap<>();
    private final Map<Integer, SpeedTierSettings> speedTiers = new HashMap<>();
    private final Map<Integer, MischiefTierSettings> mischiefTiers = new HashMap<>();
    private final Map<Integer, FlameTierSettings> flameTiers = new HashMap<>();
    private final Map<Integer, ForgeTierSettings> forgeTiers = new HashMap<>();
    private BukkitTask upkeepTask;

    public SealManager(Mythof5 plugin, Messages messages, HunterManager hunterManager, AspectManager aspectManager) {
        this.plugin = plugin;
        this.messages = messages;
        this.hunterManager = hunterManager;
        this.aspectManager = aspectManager;
        this.storage = new PlayerSealStorage(plugin);
        FileConfiguration config = plugin.getConfig();
        this.enabled = config.getBoolean("seal.enabled", true);
        this.allowUnseal = config.getBoolean("seal.allow_unseal", true);
        this.allowUpgrade = config.getBoolean("seal.allow_upgrade", false);
        this.autoPopup = config.getBoolean("seal.auto_popup", false);
        this.guiRows = Math.max(3, Math.min(4, config.getInt("seal.gui_rows", 3)));
        this.dropAmount = Math.max(1, config.getInt("drop.flame.amount", 1));
        this.dropAlways = config.getBoolean("drop.flame.always", true);
        this.dropAnnounce = config.getBoolean("drop.flame.announce", true);
        this.protectMillis = Math.max(0, config.getLong("drop.flame.protect_seconds", 8) * 1000L);
        this.defaultTier = Math.max(1, config.getInt("drop.flame.default_tier", 1));
        this.flameKey = new NamespacedKey(plugin, "hunter_flame");
        this.flameAspectKey = new NamespacedKey(plugin, "hunter_flame_aspect");
        this.flameTierKey = new NamespacedKey(plugin, "hunter_flame_tier");
        this.flameOwnerKey = new NamespacedKey(plugin, "hunter_flame_owner");
        this.flameTimeKey = new NamespacedKey(plugin, "hunter_flame_time");
        this.weaponSealKey = new NamespacedKey(plugin, "weapon_seal_id");
        this.weaponAspectKey = new NamespacedKey(plugin, "weapon_seal_aspect");
        this.weaponTierKey = new NamespacedKey(plugin, "weapon_seal_tier");
        this.weaponOwnerKey = new NamespacedKey(plugin, "weapon_seal_owner");
        this.weaponWhitelist = loadWeaponWhitelist(config.getStringList("seal.weapon_whitelist"));
        loadAspectSettings(config.getConfigurationSection("aspect"));
        startUpkeepTask();
    }

    public void shutdown() {
        storage.saveAll();
        if (upkeepTask != null) {
            upkeepTask.cancel();
            upkeepTask = null;
        }
        sessions.clear();
        activeSeals.clear();
    }

    private void startUpkeepTask() {
        upkeepTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickActiveEffects, 40L, 40L);
    }

    private void tickActiveEffects() {
        for (Map.Entry<UUID, ActiveSeal> entry : new HashMap<>(activeSeals).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                activeSeals.remove(entry.getKey());
                continue;
            }
            ActiveSeal active = entry.getValue();
            if (active.aspect == GoblinAspect.SPEED) {
                SpeedTierSettings settings = lookup(speedTiers, active.tier);
                if (settings != null) {
                    PotionEffect effect = new PotionEffect(PotionEffectType.SPEED,
                            Math.max(40, settings.durationTicks), Math.max(0, settings.speedAmplifier), true, false, true);
                    player.addPotionEffect(effect);
                }
            }
            if (active.aspect == GoblinAspect.FLAME) {
                FlameTierSettings settings = lookup(flameTiers, active.tier);
                if (settings != null && settings.glowSeconds > 0) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,
                            settings.glowSeconds * 20, 0, true, false, true));
                }
            }
        }
    }

    private Set<Material> loadWeaponWhitelist(List<String> entries) {
        if (entries == null || entries.isEmpty()) {
            return EnumSet.of(Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD,
                    Material.DIAMOND_SWORD, Material.NETHERITE_SWORD, Material.TRIDENT, Material.BOW,
                    Material.CROSSBOW,
                    Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE,
                    Material.DIAMOND_AXE, Material.NETHERITE_AXE);
        }
        Set<Material> set = EnumSet.noneOf(Material.class);
        for (String token : entries) {
            if (token == null || token.isBlank()) {
                continue;
            }
            try {
                Material material = Material.valueOf(token.trim().toUpperCase(Locale.ROOT));
                set.add(material);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Unknown material in seal.weapon_whitelist: " + token);
            }
        }
        if (set.isEmpty()) {
            return EnumSet.of(Material.DIAMOND_SWORD, Material.NETHERITE_SWORD, Material.TRIDENT,
                    Material.NETHERITE_AXE, Material.DIAMOND_AXE, Material.BOW, Material.CROSSBOW);
        }
        return set;
    }

    private void loadAspectSettings(ConfigurationSection base) {
        if (base == null) {
            return;
        }
        ConfigurationSection force = base.getConfigurationSection("force.tier");
        if (force != null) {
            for (String key : force.getKeys(false)) {
                try {
                    int tier = Integer.parseInt(key);
                    double multiplier = force.getDouble(key + ".damage_multiplier", 1.0D);
                    double knockback = force.getDouble(key + ".knockback", 0.0D);
                    forceTiers.put(tier, new ForceTierSettings(multiplier, knockback));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        ConfigurationSection speed = base.getConfigurationSection("speed.tier");
        if (speed != null) {
            for (String key : speed.getKeys(false)) {
                try {
                    int tier = Integer.parseInt(key);
                    int amplifier = speed.getInt(key + ".speed_level", 0);
                    int duration = speed.getInt(key + ".speed_duration", 80);
                    double haste = speed.getDouble(key + ".attack_speed", 0.0D);
                    speedTiers.put(tier, new SpeedTierSettings(amplifier, duration, haste));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        ConfigurationSection mischief = base.getConfigurationSection("mischief.tier");
        if (mischief != null) {
            for (String key : mischief.getKeys(false)) {
                try {
                    int tier = Integer.parseInt(key);
                    double chance = mischief.getDouble(key + ".confusion_chance", 0.2D);
                    int duration = mischief.getInt(key + ".confusion_seconds", 4);
                    int vanish = mischief.getInt(key + ".vanish_seconds", 2);
                    mischiefTiers.put(tier, new MischiefTierSettings(chance, duration, vanish));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        ConfigurationSection flame = base.getConfigurationSection("flame.tier");
        if (flame != null) {
            for (String key : flame.getKeys(false)) {
                try {
                    int tier = Integer.parseInt(key);
                    int fire = flame.getInt(key + ".fire_seconds", 4);
                    double chance = flame.getDouble(key + ".ignite_chance", 1.0D);
                    double bonus = flame.getDouble(key + ".bonus_damage", 0.0D);
                    int glow = flame.getInt(key + ".glow_seconds", 0);
                    flameTiers.put(tier, new FlameTierSettings(fire, chance, bonus, glow));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        ConfigurationSection forge = base.getConfigurationSection("forge.tier");
        if (forge != null) {
            for (String key : forge.getKeys(false)) {
                try {
                    int tier = Integer.parseInt(key);
                    double reduction = forge.getDouble(key + ".durability_reduction", 0.5D);
                    double critChance = forge.getDouble(key + ".crit_chance", 0.1D);
                    double critBonus = forge.getDouble(key + ".crit_multiplier", 1.5D);
                    forgeTiers.put(tier, new ForgeTierSettings(reduction, critChance, critBonus));
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    public void handleJoin(Player player) {
        storage.getProfile(player.getUniqueId());
        refreshActiveSeal(player);
    }

    public void handleQuit(Player player) {
        closeSession(player.getUniqueId(), true);
        storage.unload(player.getUniqueId());
        activeSeals.remove(player.getUniqueId());
    }

    public void openSealGui(Player player) {
        if (!enabled) {
            player.sendMessage(messages.format("error.seal.disabled"));
            return;
        }
        SealSession session = new SealSession(player, guiRows, messages, allowUnseal, allowUpgrade);
        session.updateWeaponPreview(player.getInventory().getItemInMainHand());
        sessions.put(player.getUniqueId(), session);
        player.openInventory(session.getInventory());
    }

    public PlayerSealProfile getProfile(Player player) {
        return storage.getProfile(player.getUniqueId());
    }

    public PlayerSealProfile getProfile(UUID playerId) {
        return storage.getProfile(playerId);
    }

    public List<String> describeProfile(PlayerSealProfile profile, String displayName) {
        if (profile == null) {
            return List.of(messages.format("seal.admin.inspect.empty"));
        }
        List<String> lines = new ArrayList<>();
        lines.add(messages.format("seal.admin.inspect.header", Map.of(
                "player", displayName,
                "count", String.valueOf(profile.getSeals().size())
        )));
        if (profile.getSeals().isEmpty()) {
            lines.add(messages.format("seal.admin.inspect.empty"));
        } else {
            for (WeaponSeal seal : profile.getSeals().values()) {
                lines.add(messages.format("seal.admin.inspect.entry", Map.of(
                        "weapon", seal.getWeaponId().toString(),
                        "aspect", seal.getAspect().getDisplayName(),
                        "tier", String.valueOf(seal.getPowerTier()),
                        "created", Instant.ofEpochMilli(seal.getCreatedAt()).toString()
                )));
            }
        }
        return lines;
    }

    public List<String> describeProfile(Player player) {
        return describeProfile(getProfile(player), player.getName());
    }

    public boolean forceUnseal(Player player, boolean refund) {
        if (player == null) {
            return false;
        }
        ItemStack weapon = player.getInventory().getItemInMainHand();
        WeaponSeal seal = readWeaponSeal(player, weapon);
        if (seal == null) {
            return false;
        }
        removeSeal(player, weapon, seal, refund);
        refreshActiveSeal(player);
        return true;
    }

    public void dropFlame(Player killer, Player victim) {
        if (!dropAlways || killer == null || victim == null) {
            return;
        }
        if (!isHunter(killer)) {
            return;
        }
        GoblinAspect aspect = determinePrimaryAspect(victim.getUniqueId());
        if (aspect == null) {
            aspect = GoblinAspect.POWER;
        }
        World world = victim.getWorld();
        Location location = victim.getLocation().add(0, 1, 0);
        for (int i = 0; i < dropAmount; i++) {
            ItemStack flameItem = createFlameItem(aspect, defaultTier, victim.getName(), killer.getUniqueId());
            Item entity = world.dropItemNaturally(location, flameItem);
            entity.setGlowing(true);
            entity.setUnlimitedLifetime(true);
        }
        if (dropAnnounce) {
            String message = messages.format("death.drop.flame.broadcast", Map.of(
                    "killer", killer.getName(),
                    "victim", victim.getName(),
                    "aspect", aspect.getDisplayName()
            ));
            plugin.broadcast(message);
        } else {
            killer.sendMessage(messages.format("death.drop.flame.self", Map.of(
                    "victim", victim.getName(),
                    "aspect", aspect.getDisplayName()
            )));
        }
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, location, 20, 0.4, 0.5, 0.4, 0.01);
        world.playSound(location, Sound.ITEM_FIRECHARGE_USE, 1.2f, 1.0f);
    }

    private boolean isHunter(Player player) {
        HunterProfile profile = hunterManager.getProfile(player);
        return profile != null && profile.isEngraved();
    }

    private GoblinAspect determinePrimaryAspect(UUID victim) {
        Set<GoblinAspect> owned = aspectManager.getAspects(victim);
        if (owned.isEmpty()) {
            return null;
        }
        for (GoblinAspect aspect : GoblinAspect.values()) {
            if (aspectManager.isInheritor(aspect, victim)) {
                return aspect;
            }
        }
        return owned.iterator().next();
    }

    private ItemStack createFlameItem(GoblinAspect aspect, int tier, String originName, UUID owner) {
        ItemStack stack = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(messages.format("seal.flame.name", Map.of(
                    "aspect", aspect.getDisplayName(),
                    "tier", String.valueOf(tier)
            )), NamedTextColor.GOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(messages.format("seal.flame.lore", Map.of(
                    "origin", originName,
                    "aspect", aspect.getDisplayName(),
                    "tier", String.valueOf(tier)
            )), NamedTextColor.GRAY));
            meta.lore(lore);
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(flameKey, PersistentDataType.BYTE, (byte) 1);
            container.set(flameAspectKey, PersistentDataType.STRING, aspect.name());
            container.set(flameTierKey, PersistentDataType.INTEGER, tier);
            container.set(flameOwnerKey, PersistentDataType.STRING, owner.toString());
            container.set(flameTimeKey, PersistentDataType.LONG, System.currentTimeMillis());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public boolean isGoblinFlame(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(flameKey, PersistentDataType.BYTE);
    }

    private GoblinFlame readFlame(ItemStack stack) {
        if (!isGoblinFlame(stack)) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        String aspectKey = container.get(flameAspectKey, PersistentDataType.STRING);
        Integer tier = container.get(flameTierKey, PersistentDataType.INTEGER);
        GoblinAspect aspect = null;
        if (aspectKey != null) {
            try {
                aspect = GoblinAspect.valueOf(aspectKey);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (aspect == null) {
            aspect = GoblinAspect.POWER;
        }
        int resolvedTier = tier != null ? Math.max(1, tier) : defaultTier;
        String origin = meta.hasDisplayName()
                ? PlainTextComponentSerializer.plainText().serialize(meta.displayName())
                : "";
        return new GoblinFlame(aspect, resolvedTier, origin);
    }

    public WeaponSeal readWeaponSeal(Player player, ItemStack stack) {
        if (stack == null) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        String id = container.get(weaponSealKey, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        UUID weaponId;
        try {
            weaponId = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        PlayerSealProfile profile = storage.getProfile(player.getUniqueId());
        return profile.getSeal(weaponId);
    }

    private void refreshActiveSeal(Player player) {
        WeaponSeal seal = readWeaponSeal(player, player.getInventory().getItemInMainHand());
        if (seal == null) {
            ActiveSeal previous = activeSeals.remove(player.getUniqueId());
            if (previous != null) {
                clearAttributes(player, previous);
            }
            return;
        }
        ActiveSeal current = activeSeals.get(player.getUniqueId());
        if (current != null && current.weaponId.equals(seal.getWeaponId()) && current.tier == seal.getPowerTier()) {
            return;
        }
        if (current != null) {
            clearAttributes(player, current);
        }
        applyAttributes(player, seal);
        activeSeals.put(player.getUniqueId(), new ActiveSeal(seal.getWeaponId(), seal.getAspect(), seal.getPowerTier()));
    }

    private void applyAttributes(Player player, WeaponSeal seal) {
        if (seal.getAspect() == GoblinAspect.POWER) {
            ForceTierSettings settings = lookup(forceTiers, seal.getPowerTier());
            if (settings != null && settings.attackBonus() > 0) {
                org.bukkit.attribute.AttributeInstance instance = player.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
                if (instance != null) {
                    instance.addTransientModifier(settings.createModifier(plugin));
                }
            }
        }
        if (seal.getAspect() == GoblinAspect.SPEED) {
            SpeedTierSettings settings = lookup(speedTiers, seal.getPowerTier());
            if (settings != null && settings.hasAttackSpeedBonus()) {
                org.bukkit.attribute.AttributeInstance instance = player.getAttribute(org.bukkit.attribute.Attribute.ATTACK_SPEED);
                if (instance != null) {
                    instance.addTransientModifier(settings.createModifier(plugin));
                }
            }
        }
    }

    private void clearAttributes(Player player, ActiveSeal seal) {
        if (seal.aspect == GoblinAspect.POWER) {
            org.bukkit.attribute.AttributeInstance instance = player.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
            if (instance != null) {
                Set<org.bukkit.attribute.AttributeModifier> toRemove = new HashSet<>(instance.getModifiers());
                for (org.bukkit.attribute.AttributeModifier modifier : toRemove) {
                    if (modifier.getName().startsWith("hunter_force_")) {
                        instance.removeModifier(modifier);
                    }
                }
            }
        }
        if (seal.aspect == GoblinAspect.SPEED) {
            org.bukkit.attribute.AttributeInstance instance = player.getAttribute(org.bukkit.attribute.Attribute.ATTACK_SPEED);
            if (instance != null) {
                Set<org.bukkit.attribute.AttributeModifier> toRemove = new HashSet<>(instance.getModifiers());
                for (org.bukkit.attribute.AttributeModifier modifier : toRemove) {
                    if (modifier.getName().startsWith("hunter_speed_")) {
                        instance.removeModifier(modifier);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        handleJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        handleQuit(event.getPlayer());
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> refreshActiveSeal(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        if (!isGoblinFlame(stack)) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        String ownerRaw = container.get(flameOwnerKey, PersistentDataType.STRING);
        Long time = container.get(flameTimeKey, PersistentDataType.LONG);
        if (ownerRaw != null && time != null) {
            try {
                UUID owner = UUID.fromString(ownerRaw);
                if (!owner.equals(player.getUniqueId())) {
                    long elapsed = System.currentTimeMillis() - time;
                    if (elapsed < protectMillis) {
                        event.setCancelled(true);
                        player.sendMessage(messages.format("error.flame.protected"));
                        return;
                    }
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        GoblinFlame flame = readFlame(stack);
        player.sendMessage(messages.format("seal.flame.pickup", Map.of(
                "aspect", flame.aspect().getDisplayName(),
                "tier", String.valueOf(flame.tier())
        )));
        if (autoPopup) {
            Bukkit.getScheduler().runTask(plugin, () -> openSealGui(player));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!isGoblinFlame(event.getItemDrop().getItemStack())) {
            return;
        }
        Item item = event.getItemDrop();
        item.setPickupDelay(0);
        item.setUnlimitedLifetime(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inventory = event.getInventory();
        InventoryHolder holder = inventory.getHolder();
        if (!(holder instanceof SealSession session)) {
            return;
        }
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        if (!player.getUniqueId().equals(session.getPlayerId())) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == session.getInputSlot()) {
            handleInputClick(event, session);
            return;
        }
        if (slot == session.getSealSlot()) {
            handleSeal(player, session);
            return;
        }
        if (slot == session.getUnsealSlot()) {
            handleUnseal(player, session);
            return;
        }
        if (slot == session.getUpgradeSlot()) {
            handleUpgrade(player, session);
            return;
        }
        if (slot == session.getCancelSlot()) {
            player.closeInventory();
        }
        if (slot == session.getHelpSlot()) {
            player.sendMessage(messages.format("seal.gui.help"));
        }
    }

    private void handleInputClick(InventoryClickEvent event, SealSession session) {
        ItemStack cursor = event.getCursor();
        if (cursor != null && cursor.getType() != Material.AIR) {
            if (!isGoblinFlame(cursor)) {
                return;
            }
            ItemStack single = cursor.clone();
            single.setAmount(1);
            GoblinFlame flame = readFlame(single);
            session.setFlame(single, flame);
            cursor.setAmount(cursor.getAmount() - 1);
            event.getView().setCursor(cursor.getAmount() <= 0 ? null : cursor);
        } else {
            ItemStack flameItem = session.takeFlame();
            if (flameItem != null) {
                event.getView().setCursor(flameItem);
            }
        }
    }

    private void handleSeal(Player player, SealSession session) {
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon == null || weapon.getType() == Material.AIR) {
            player.sendMessage(messages.format("seal.require.weapon"));
            return;
        }
        if (!weaponWhitelist.contains(weapon.getType())) {
            player.sendMessage(messages.format("error.weapon.not_allowed"));
            return;
        }
        GoblinFlame flame = session.getFlame();
        if (flame == null) {
            player.sendMessage(messages.format("seal.require.flame"));
            return;
        }
        if (readWeaponSeal(player, weapon) != null) {
            player.sendMessage(messages.format("error.already_sealed"));
            return;
        }
        session.clearFlameSlot();
        applySeal(player, weapon, flame);
        player.sendMessage(messages.format("seal.success", Map.of(
                "aspect", flame.aspect().getDisplayName(),
                "tier", String.valueOf(flame.tier())
        )));
        refreshActiveSeal(player);
        session.updateWeaponPreview(weapon);
    }

    private void applySeal(Player player, ItemStack weapon, GoblinFlame flame) {
        UUID weaponId = UUID.randomUUID();
        ItemMeta meta = weapon.getItemMeta();
        Component originalName = meta != null ? meta.displayName() : null;
        List<Component> originalLore = meta != null && meta.lore() != null ? meta.lore() : Collections.emptyList();
        if (meta == null) {
            meta = Bukkit.getItemFactory().getItemMeta(weapon.getType());
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(weaponSealKey, PersistentDataType.STRING, weaponId.toString());
        container.set(weaponAspectKey, PersistentDataType.STRING, flame.aspect().name());
        container.set(weaponTierKey, PersistentDataType.INTEGER, flame.tier());
        container.set(weaponOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        meta.displayName(Component.text(messages.format("seal.name_format", Map.of(
                "weapon", weapon.getType().name(),
                "aspect", flame.aspect().getDisplayName(),
                "tier", String.valueOf(flame.tier())
        )), NamedTextColor.GOLD));
        List<String> loreTemplates = plugin.getConfig().getStringList("seal.lore_lines");
        List<Component> lore = new ArrayList<>();
        for (String line : loreTemplates) {
            lore.add(Component.text(line.replace("{aspect}", flame.aspect().getDisplayName())
                    .replace("{tier}", String.valueOf(flame.tier()))
                    .replace("{weapon}", weapon.getType().name()), NamedTextColor.GRAY));
        }
        meta.lore(lore);
        weapon.setItemMeta(meta);
        PlayerSealProfile profile = storage.getProfile(player.getUniqueId());
        profile.putSeal(new WeaponSeal(weaponId, player.getUniqueId(), flame.aspect(), flame.tier(),
                Instant.now().toEpochMilli(), 0, originalName, originalLore));
        profile.log(System.currentTimeMillis(), "seal:" + weaponId);
    }

    private void handleUnseal(Player player, SealSession session) {
        if (!allowUnseal) {
            player.sendMessage(messages.format("error.not_allowed"));
            return;
        }
        ItemStack weapon = player.getInventory().getItemInMainHand();
        WeaponSeal seal = readWeaponSeal(player, weapon);
        if (seal == null) {
            player.sendMessage(messages.format("error.not_sealed"));
            return;
        }
        removeSeal(player, weapon, seal, true);
        player.sendMessage(messages.format("seal.unseal.success", Map.of(
                "aspect", seal.getAspect().getDisplayName(),
                "tier", String.valueOf(seal.getPowerTier())
        )));
        session.updateWeaponPreview(weapon);
        refreshActiveSeal(player);
    }

    private void removeSeal(Player player, ItemStack weapon, WeaponSeal seal, boolean refund) {
        ItemMeta meta = weapon.getItemMeta();
        if (meta != null) {
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.remove(weaponSealKey);
            container.remove(weaponAspectKey);
            container.remove(weaponTierKey);
            container.remove(weaponOwnerKey);
            meta.displayName(seal.getOriginalName());
            meta.lore(seal.getOriginalLore());
            weapon.setItemMeta(meta);
        }
        PlayerSealProfile profile = storage.getProfile(player.getUniqueId());
        profile.removeSeal(seal.getWeaponId());
        profile.log(System.currentTimeMillis(), "unseal:" + seal.getWeaponId());
        if (refund) {
            ItemStack flame = createFlameItem(seal.getAspect(), seal.getPowerTier(), player.getName(), player.getUniqueId());
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(flame);
            if (!overflow.isEmpty()) {
                player.getWorld().dropItemNaturally(player.getLocation(), flame);
            }
        }
    }

    private void handleUpgrade(Player player, SealSession session) {
        if (!allowUpgrade) {
            player.sendMessage(messages.format("error.not_allowed"));
            return;
        }
        ItemStack weapon = player.getInventory().getItemInMainHand();
        WeaponSeal seal = readWeaponSeal(player, weapon);
        if (seal == null) {
            player.sendMessage(messages.format("error.not_sealed"));
            return;
        }
        int maxTier = plugin.getConfig().getInt("upgrade.max_tier", 5);
        if (seal.getPowerTier() >= maxTier) {
            player.sendMessage(messages.format("seal.upgrade.max_tier"));
            return;
        }
        int cost = Math.max(1, plugin.getConfig().getInt("upgrade.cost.same_flames", 3));
        if (!consumeFlames(player, seal.getAspect(), cost)) {
            player.sendMessage(messages.format("seal.require.flame"));
            return;
        }
        double chance = plugin.getConfig().getDouble("upgrade.success_chance", 1.0D);
        if (random.nextDouble() > chance) {
            player.sendMessage(messages.format("seal.upgrade.fail"));
            return;
        }
        removeSeal(player, weapon, seal, false);
        applySeal(player, weapon, new GoblinFlame(seal.getAspect(), seal.getPowerTier() + 1, player.getName()));
        player.sendMessage(messages.format("seal.upgrade.success", Map.of(
                "tier", String.valueOf(seal.getPowerTier() + 1)
        )));
        session.updateWeaponPreview(weapon);
        refreshActiveSeal(player);
    }

    private boolean consumeFlames(Player player, GoblinAspect aspect, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (!isGoblinFlame(stack)) {
                continue;
            }
            GoblinFlame flame = readFlame(stack);
            if (flame == null || flame.aspect() != aspect) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) {
                contents[i] = null;
            }
            remaining -= take;
        }
        player.getInventory().setContents(contents);
        return remaining <= 0;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        ItemStack weapon = player.getInventory().getItem(EquipmentSlot.HAND);
        WeaponSeal seal = readWeaponSeal(player, weapon);
        if (seal == null) {
            return;
        }
        switch (seal.getAspect()) {
            case POWER -> applyForceEffect(player, event, seal);
            case SPEED -> applySpeedStrike(player, event, seal);
            case MISCHIEF -> applyMischiefEffect(player, event, seal);
            case FLAME -> applyFlameEffect(player, event, seal);
            case FORGE -> applyForgeEffect(player, event, seal);
        }
    }

    private void applyForceEffect(Player player, EntityDamageByEntityEvent event, WeaponSeal seal) {
        ForceTierSettings settings = lookup(forceTiers, seal.getPowerTier());
        if (settings != null) {
            event.setDamage(event.getDamage() * settings.damageMultiplier);
            if (settings.knockbackBonus > 0 && event.getEntity() instanceof LivingEntity target) {
                Vector vector = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
                target.setVelocity(target.getVelocity().add(vector.multiply(settings.knockbackBonus)));
            }
        }
    }

    private void applySpeedStrike(Player player, EntityDamageByEntityEvent event, WeaponSeal seal) {
        SpeedTierSettings settings = lookup(speedTiers, seal.getPowerTier());
        if (settings != null && settings.attackSpeedBonus > 0) {
            event.setDamage(event.getDamage() * (1.0D + settings.attackSpeedBonus));
        }
    }

    private void applyMischiefEffect(Player player, EntityDamageByEntityEvent event, WeaponSeal seal) {
        MischiefTierSettings settings = lookup(mischiefTiers, seal.getPowerTier());
        if (settings == null) {
            return;
        }
        if (event.getEntity() instanceof LivingEntity target && random.nextDouble() <= settings.confusionChance) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, settings.confusionSeconds * 20, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, settings.vanishSeconds * 20, 0, true, false, true));
        }
    }

    private void applyFlameEffect(Player player, EntityDamageByEntityEvent event, WeaponSeal seal) {
        FlameTierSettings settings = lookup(flameTiers, seal.getPowerTier());
        if (settings == null) {
            return;
        }
        if (event.getEntity() instanceof LivingEntity target) {
            if (random.nextDouble() <= settings.igniteChance) {
                target.setFireTicks(Math.max(target.getFireTicks(), settings.fireSeconds * 20));
            }
            if (settings.bonusDamage > 0) {
                event.setDamage(event.getDamage() + settings.bonusDamage);
            }
        }
    }

    private void applyForgeEffect(Player player, EntityDamageByEntityEvent event, WeaponSeal seal) {
        ForgeTierSettings settings = lookup(forgeTiers, seal.getPowerTier());
        if (settings == null) {
            return;
        }
        if (random.nextDouble() <= settings.critChance) {
            event.setDamage(event.getDamage() * settings.critMultiplier);
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        WeaponSeal seal = readWeaponSeal(player, event.getItem());
        if (seal == null || seal.getAspect() != GoblinAspect.FORGE) {
            return;
        }
        ForgeTierSettings settings = lookup(forgeTiers, seal.getPowerTier());
        if (settings == null) {
            return;
        }
        if (random.nextDouble() <= settings.durabilityReduction) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof SealSession session)) {
            return;
        }
        Player player = (Player) event.getPlayer();
        closeSession(player.getUniqueId(), true);
    }

    private void closeSession(UUID playerId, boolean refund) {
        SealSession session = sessions.remove(playerId);
        if (session == null) {
            return;
        }
        ItemStack flame = session.takeFlame();
        if (refund && flame != null) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(flame);
                if (!overflow.isEmpty()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), flame);
                }
            }
        }
    }

    private <T> T lookup(Map<Integer, T> map, int tier) {
        if (map.isEmpty()) {
            return null;
        }
        if (map.containsKey(tier)) {
            return map.get(tier);
        }
        int best = -1;
        for (Integer key : map.keySet()) {
            if (key <= tier && key > best) {
                best = key;
            }
        }
        if (best != -1) {
            return map.get(best);
        }
        int min = map.keySet().stream().min(Integer::compareTo).orElse(1);
        return map.get(min);
    }

    private record ForceTierSettings(double damageMultiplier, double knockbackBonus) {
        private double attackBonus() {
            return Math.max(0.0D, damageMultiplier - 1.0D);
        }

        private org.bukkit.attribute.AttributeModifier createModifier(Mythof5 plugin) {
            return new org.bukkit.attribute.AttributeModifier("hunter_force_" + plugin.getName(), attackBonus(),
                    org.bukkit.attribute.AttributeModifier.Operation.ADD_SCALAR);
        }
    }

    private record SpeedTierSettings(int speedAmplifier, int durationTicks, double attackSpeedBonus) {
        private org.bukkit.attribute.AttributeModifier createModifier(Mythof5 plugin) {
            return new org.bukkit.attribute.AttributeModifier("hunter_speed_" + plugin.getName(), attackSpeedBonus,
                    org.bukkit.attribute.AttributeModifier.Operation.ADD_SCALAR);
        }

        private boolean hasAttackSpeedBonus() {
            return attackSpeedBonus > 0;
        }
    }

    private record MischiefTierSettings(double confusionChance, int confusionSeconds, int vanishSeconds) {
    }

    private record FlameTierSettings(int fireSeconds, double igniteChance, double bonusDamage, int glowSeconds) {
    }

    private record ForgeTierSettings(double durabilityReduction, double critChance, double critMultiplier) {
    }

    private record ActiveSeal(UUID weaponId, GoblinAspect aspect, int tier) {
    }
}
