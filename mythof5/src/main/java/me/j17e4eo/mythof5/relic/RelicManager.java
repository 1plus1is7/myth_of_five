package me.j17e4eo.mythof5.relic;

import me.j17e4eo.mythof5.Mythof5;
import me.j17e4eo.mythof5.balance.BalanceTable;
import me.j17e4eo.mythof5.chronicle.ChronicleEventType;
import me.j17e4eo.mythof5.chronicle.ChronicleManager;
import me.j17e4eo.mythof5.config.Messages;
import me.j17e4eo.mythof5.inherit.skilltree.SkillTreeManager;
import me.j17e4eo.mythof5.meta.MetaEventManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages relic ownership, granting and fusion logic.
 */
public class RelicManager implements Listener {

    private final Mythof5 plugin;
    private final Messages messages;
    private final ChronicleManager chronicleManager;
    private final BalanceTable balanceTable;
    private final SkillTreeManager skillTreeManager;
    private final MetaEventManager metaEventManager;
    private final Map<UUID, Set<RelicType>> relics = new HashMap<>();
    private final List<RelicFusion> fusions = new ArrayList<>();
    private final Map<RelicType, RelicAbility> abilities = new EnumMap<>(RelicType.class);
    private final Map<UUID, Long> gumihoCooldown = new HashMap<>();
    private File dataFile;
    private YamlConfiguration dataConfig;

    public RelicManager(Mythof5 plugin, Messages messages, ChronicleManager chronicleManager,
                        BalanceTable balanceTable, SkillTreeManager skillTreeManager,
                        MetaEventManager metaEventManager) {
        this.plugin = plugin;
        this.messages = messages;
        this.chronicleManager = chronicleManager;
        this.balanceTable = balanceTable;
        this.skillTreeManager = skillTreeManager;
        this.metaEventManager = metaEventManager;
        fusions.add(new RelicFusion(RelicType.TRICK_AND_BIND,
                "환영과 포획이 동시에 발현되는 강력한 설화",
                RelicType.MANGTAE_HALABEOM, RelicType.DUNGAP_MOUSE));
        registerAbilities();
    }

    private void registerAbilities() {
        abilities.put(RelicType.MANGTAE_HALABEOM, new RelicAbility() {
            @Override
            public void onGrant(Player player) {
                applyPermanentEffect(player, PotionEffectType.RESISTANCE, 0);
            }

            @Override
            public void onRemove(Player player) {
                player.removePotionEffect(PotionEffectType.RESISTANCE);
            }
        });
        abilities.put(RelicType.WATER_GOBLIN, new RelicAbility() {
            @Override
            public void onGrant(Player player) {
                applyPermanentEffect(player, PotionEffectType.CONDUIT_POWER, 0);
                applyPermanentEffect(player, PotionEffectType.DOLPHINS_GRACE, 0);
            }

            @Override
            public void onRemove(Player player) {
                player.removePotionEffect(PotionEffectType.CONDUIT_POWER);
                player.removePotionEffect(PotionEffectType.DOLPHINS_GRACE);
            }
        });
        abilities.put(RelicType.DUNGAP_MOUSE, new RelicAbility() {
            @Override
            public void onGrant(Player player) {
                applyPermanentEffect(player, PotionEffectType.SPEED, 0);
            }

            @Override
            public void onRemove(Player player) {
                player.removePotionEffect(PotionEffectType.SPEED);
            }
        });
        abilities.put(RelicType.PACK_ELDER, new RelicAbility() {
            @Override
            public void onGrant(Player player) {
                applyPermanentEffect(player, PotionEffectType.LUCK, 0);
                applyPermanentEffect(player, PotionEffectType.HASTE, 0);
            }

            @Override
            public void onRemove(Player player) {
                player.removePotionEffect(PotionEffectType.LUCK);
                player.removePotionEffect(PotionEffectType.HASTE);
            }
        });
        abilities.put(RelicType.DOOR_GUARD, new RelicAbility() {
            @Override
            public void onGrant(Player player) {
                applyPermanentEffect(player, PotionEffectType.RESISTANCE, 0);
            }

            @Override
            public void onRemove(Player player) {
                player.removePotionEffect(PotionEffectType.RESISTANCE);
            }
        });
        abilities.put(RelicType.GUMIHO_TAIL, new RelicAbility() {
            @Override
            public void onGrant(Player player) {
                applyPermanentEffect(player, PotionEffectType.SPEED, 0);
            }

            @Override
            public void onRemove(Player player) {
                player.removePotionEffect(PotionEffectType.SPEED);
            }
        });
        abilities.put(RelicType.TRICK_AND_BIND, new RelicAbility() {
            @Override
            public void onGrant(Player player) {
                applyPermanentEffect(player, PotionEffectType.INVISIBILITY, 0);
            }

            @Override
            public void onRemove(Player player) {
                player.removePotionEffect(PotionEffectType.INVISIBILITY);
            }
        });
        abilities.put(RelicType.TWILIGHT_CINDERS, new RelicAbility() {
            @Override
            public void onGrant(Player player) {
                applyPermanentEffect(player, PotionEffectType.NIGHT_VISION, 0);
            }

            @Override
            public void onRemove(Player player) {
                player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            }
        });
        abilities.put(RelicType.STORMCALLER_DRUM, new RelicAbility() {
            @Override
            public void onGrant(Player player) {
                applyPermanentEffect(player, PotionEffectType.HERO_OF_THE_VILLAGE, 0);
            }

            @Override
            public void onRemove(Player player) {
                player.removePotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE);
            }
        });
        abilities.put(RelicType.FORGE_HEART, new RelicAbility() {
            @Override
            public void onGrant(Player player) {
                applyPermanentEffect(player, PotionEffectType.FIRE_RESISTANCE, 0);
            }

            @Override
            public void onRemove(Player player) {
                player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
            }
        });
    }

    private void applyPermanentEffect(Player player, PotionEffectType type, int amplifier) {
        PotionEffect effect = new PotionEffect(type, Integer.MAX_VALUE, amplifier, true, false, true);
        player.addPotionEffect(effect);
    }

    public void load() {
        plugin.getDataFolder().mkdirs();
        dataFile = new File(plugin.getDataFolder(), "relics.yml");
        if (!dataFile.exists()) {
            dataConfig = new YamlConfiguration();
            return;
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        relics.clear();
        for (String key : dataConfig.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Invalid player UUID in relics.yml: " + key);
                continue;
            }
            List<String> entries = dataConfig.getStringList(key);
            Set<RelicType> set = new HashSet<>();
            for (String entry : entries) {
                RelicType type = RelicType.fromKey(entry);
                if (type != null) {
                    set.add(type);
                }
            }
            relics.put(uuid, set);
        }
    }

    public void save() {
        if (dataConfig == null) {
            dataConfig = new YamlConfiguration();
        }
        for (Map.Entry<UUID, Set<RelicType>> entry : relics.entrySet()) {
            List<String> serialized = new ArrayList<>();
            for (RelicType type : entry.getValue()) {
                serialized.add(type.getKey());
            }
            dataConfig.set(entry.getKey().toString(), serialized);
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save relics.yml: " + e.getMessage());
        }
    }

    public boolean grantRelic(Player player, RelicType type, boolean announce) {
        Set<RelicType> owned = relics.computeIfAbsent(player.getUniqueId(), key -> new HashSet<>());
        if (!owned.add(type)) {
            return false;
        }
        RelicAbility ability = abilities.get(type);
        if (ability != null) {
            ability.onGrant(player);
        }
        if (announce) {
            player.sendMessage(messages.format("relic.obtain.self", Map.of(
                    "relic", type.getDisplayName(),
                    "effect", type.getEffect(),
                    "rarity", type.getRarity().getDisplayName(),
                    "ability", type.getAbility()
            )));
            plugin.broadcast(messages.format("relic.obtain.broadcast", Map.of(
                    "player", player.getName(),
                    "relic", type.getDisplayName()
            )));
        }
        balanceTable.recordRelicEquipped(type);
        skillTreeManager.addPoints(player.getUniqueId(), 1,
                messages.format("goblin.skilltree.reason.relic", Map.of(
                        "relic", type.getDisplayName()
                )));
        metaEventManager.recordRelicGain(type);
        chronicleManager.logEvent(ChronicleEventType.RELIC_GAIN,
                messages.format("chronicle.relic.obtain", Map.of(
                        "player", player.getName(),
                        "relic", type.getDisplayName()
                )), List.of(player));
        checkFusions(player, owned);
        save();
        return true;
    }

    public boolean removeRelic(Player player, RelicType type) {
        Set<RelicType> owned = relics.get(player.getUniqueId());
        if (owned == null) {
            return false;
        }
        boolean removed = owned.remove(type);
        if (removed) {
            RelicAbility ability = abilities.get(type);
            if (ability != null) {
                ability.onRemove(player);
            }
            save();
        }
        return removed;
    }

    public Set<RelicType> getRelics(UUID uuid) {
        return relics.containsKey(uuid) ? Collections.unmodifiableSet(relics.get(uuid)) : Collections.emptySet();
    }

    public List<RelicFusion> getFusions() {
        return Collections.unmodifiableList(fusions);
    }

    public List<String> describeRelics(UUID uuid) {
        Set<RelicType> owned = relics.get(uuid);
        if (owned == null || owned.isEmpty()) {
            return List.of(messages.format("relic.none"));
        }
        List<String> result = new ArrayList<>();
        for (RelicType type : owned) {
            result.add("• " + type.getDisplayName() + " [" + type.getRarity().getDisplayName() + "] - "
                    + type.getAbility() + " / " + type.getSynergy());
        }
        return result;
    }

    public boolean hasRelic(UUID uuid, RelicType type) {
        Set<RelicType> set = relics.get(uuid);
        return set != null && set.contains(type);
    }

    public void handleJoin(Player player) {
        Set<RelicType> set = relics.get(player.getUniqueId());
        if (set == null) {
            return;
        }
        for (RelicType type : set) {
            RelicAbility ability = abilities.get(type);
            if (ability != null) {
                ability.onGrant(player);
            }
        }
    }

    private void checkFusions(Player player, Set<RelicType> owned) {
        for (RelicFusion fusion : fusions) {
            if (!owned.contains(fusion.getResult()) && fusion.matches(owned)) {
                owned.add(fusion.getResult());
                player.sendMessage(messages.format("relic.fusion.self", Map.of(
                        "relic", fusion.getResult().getDisplayName(),
                        "desc", fusion.getDescription()
                )));
                plugin.broadcast(messages.format("relic.fusion.broadcast", Map.of(
                        "player", player.getName(),
                        "relic", fusion.getResult().getDisplayName()
                )));
                chronicleManager.logEvent(ChronicleEventType.RELIC_FUSION,
                        messages.format("chronicle.relic.fusion", Map.of(
                                "player", player.getName(),
                                "relic", fusion.getResult().getDisplayName()
                        )), List.of(player));
                metaEventManager.recordRelicFusion(fusion.getResult());
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        handleJoin(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        Player player = event.getPlayer();
        if (hasRelic(player.getUniqueId(), RelicType.DUNGAP_MOUSE)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 80, 0, true, true, true));
            player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 12, 0.3, 0.5, 0.3, 0.02);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        double finalHealth = player.getHealth() - event.getFinalDamage();
        if (hasRelic(player.getUniqueId(), RelicType.MANGTAE_HALABEOM)) {
            double max = player.getAttribute(Attribute.MAX_HEALTH).getValue();
            if (finalHealth <= max * 0.3D) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1, true, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 1, true, true, true));
                player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.01);
                player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0F, 0.7F);
            }
        }
        if (hasRelic(player.getUniqueId(), RelicType.FORGE_HEART)) {
            player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0, 0.5, 0), 30, 0.4, 0.4, 0.4, 0.02);
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 80, 0, true, true, true));
        }
        if (hasRelic(player.getUniqueId(), RelicType.GUMIHO_TAIL)) {
            long now = System.currentTimeMillis();
            long next = gumihoCooldown.getOrDefault(player.getUniqueId(), 0L);
            if (now >= next) {
                spawnGumihoIllusion(player);
                gumihoCooldown.put(player.getUniqueId(), now + 5000L);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        Entity victim = event.getEntity();
        if (!(victim instanceof LivingEntity living)) {
            return;
        }
        if (hasRelic(player.getUniqueId(), RelicType.TWILIGHT_CINDERS)) {
            living.setFireTicks(80);
            if (living instanceof Player targetPlayer) {
                targetPlayer.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, true, true, true));
            }
            living.getWorld().spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, living.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.02);
        }
        if (hasRelic(player.getUniqueId(), RelicType.TRICK_AND_BIND)) {
            living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, true, true, true));
            living.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0, true, true, true));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (hasRelic(player.getUniqueId(), RelicType.STORMCALLER_DRUM)) {
            activateStormcaller(player);
        }
        if (event.getClickedBlock() != null && hasRelic(player.getUniqueId(), RelicType.DOOR_GUARD)) {
            String typeName = event.getClickedBlock().getType().name();
            if (typeName.endsWith("DOOR") || typeName.contains("TRAPDOOR")) {
                event.setCancelled(true);
                player.playSound(event.getClickedBlock().getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 1.0F, 0.8F);
                deployDoorBarrier(player, event.getClickedBlock().getLocation());
            }
        }
    }

    private void activateStormcaller(Player player) {
        Location center = player.getLocation();
        player.getWorld().playSound(center, Sound.ITEM_TRIDENT_THUNDER, 1.0F, 1.2F);
        player.getWorld().spawnParticle(Particle.CLOUD, center.add(0, 1, 0), 60, 4.0, 1.0, 4.0, 0.1);
        double radius = 8.0D;
        for (Entity entity : player.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof Player other) {
                if (other.equals(player) || other.getUniqueId().equals(player.getUniqueId())) {
                    other.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1, true, true, true));
                } else {
                    other.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 0, true, true, true));
                }
            }
        }
    }

    private void spawnGumihoIllusion(Player player) {
        Location location = player.getLocation();
        player.getWorld().spawnParticle(Particle.END_ROD, location, 40, 0.7, 0.6, 0.7, 0.03);
        player.playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6F, 1.4F);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 60, 0, true, true, true));
        for (int i = 0; i < 3; i++) {
            Location spawn = location.clone().add((i - 1) * 0.8, 0.1, ThreadLocalRandom.current().nextDouble(-0.4, 0.4));
            ArmorStand stand = player.getWorld().spawn(spawn, ArmorStand.class, armorStand -> {
                armorStand.setSmall(true);
                armorStand.setArms(true);
                armorStand.setGravity(false);
                armorStand.setBasePlate(false);
                armorStand.setInvulnerable(true);
                armorStand.setPersistent(false);
                armorStand.customName(Component.text(player.getName(), NamedTextColor.GOLD));
                armorStand.setCustomNameVisible(true);
                armorStand.setGlowing(true);
                armorStand.getEquipment().setHelmet(createPlayerHead(player));
            });
            Vector velocity = player.getLocation().getDirection().clone()
                    .rotateAroundY((i - 1) * 0.8).multiply(0.4);
            stand.setVelocity(velocity);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (stand.isValid()) {
                    stand.getEquipment().setHelmet(null);
                    stand.remove();
                }
            }, 60L);
        }
    }

    private void deployDoorBarrier(Player player, Location location) {
        Location center = location.clone().add(0.5, 0.5, 0.5);
        player.getWorld().spawnParticle(Particle.END_ROD, center, 50, 1.0, 0.3, 1.0, 0.02);
        player.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 0.8F, 0.9F);
        AreaEffectCloud cloud = player.getWorld().spawn(center, AreaEffectCloud.class);
        cloud.setRadius(3.0F);
        cloud.setDuration(100);
        cloud.setRadiusPerTick(-0.03F);
        cloud.addCustomEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, false, true, true), true);
        cloud.setWaitTime(5);
    }

    private ItemStack createPlayerHead(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);
            skullMeta.displayName(Component.text(player.getName(), NamedTextColor.GOLD));
            head.setItemMeta(skullMeta);
        }
        return head;
    }
}
