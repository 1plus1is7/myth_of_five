package me.j17e4eo.mythof5.weapon;

import me.j17e4eo.mythof5.Mythof5;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles the bespoke weapon abilities for common and advanced (hunter / goblin)
 * weapons. The manager is responsible for tagging weapons, tracking cooldowns
 * and combo states and spawning the visual feedback.
 */
public class WeaponManager implements Listener {

    private static final long COMBO_RESET_MILLIS = 1500L;
    private static final long PARRY_WINDOW_MILLIS_COMMON = 500L;
    private static final long PARRY_WINDOW_MILLIS_ADVANCED = 750L;

    private final Mythof5 plugin;
    private final NamespacedKey weaponTypeKey;
    private final NamespacedKey weaponTierKey;
    private final NamespacedKey arrowEffectKey;
    private final NamespacedKey arrowTierKey;
    private final NamespacedKey arrowOwnerKey;

    private final Map<UUID, ComboState> swordCombos = new HashMap<>();
    private final Map<UUID, Long> parryWindows = new HashMap<>();
    private final Map<UUID, Long> empoweredStrikes = new HashMap<>();
    private final Map<UUID, Long> enhancedTridentThrow = new HashMap<>();
    private final Map<UUID, Long> enhancedArrow = new HashMap<>();
    private final Map<UUID, Long> ropeArrow = new HashMap<>();
    private final Map<UUID, CooldownMap> cooldowns = new HashMap<>();
    private final Map<UUID, BukkitTask> trackingTasks = new HashMap<>();
    private final Map<UUID, DashState> dashingPlayers = new HashMap<>();

    public WeaponManager(Mythof5 plugin) {
        this.plugin = plugin;
        NamespacedKey type = NamespacedKey.fromString("myserver:weaponType", plugin);
        NamespacedKey tier = NamespacedKey.fromString("myserver:weaponTier", plugin);
        this.weaponTypeKey = Objects.requireNonNullElseGet(type, () -> new NamespacedKey(plugin, "weapon_type"));
        this.weaponTierKey = Objects.requireNonNullElseGet(tier, () -> new NamespacedKey(plugin, "weapon_tier"));
        this.arrowEffectKey = new NamespacedKey(plugin, "weapon_arrow_effect");
        this.arrowTierKey = new NamespacedKey(plugin, "weapon_arrow_tier");
        this.arrowOwnerKey = new NamespacedKey(plugin, "weapon_arrow_owner");
    }

    // region Public API helpers

    public WeaponProfile resolveWeapon(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            meta = Bukkit.getItemFactory().getItemMeta(stack.getType());
            if (meta == null) {
                return null;
            }
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        WeaponType type = WeaponType.fromKey(container.get(weaponTypeKey, PersistentDataType.STRING));
        if (type == null) {
            type = WeaponType.detect(stack.getType());
            if (type == null) {
                return null;
            }
            container.set(weaponTypeKey, PersistentDataType.STRING, type.getKey());
        }
        WeaponTier tier = WeaponTier.fromKey(container.get(weaponTierKey, PersistentDataType.STRING));
        if (tier == null) {
            tier = WeaponTier.COMMON;
            container.set(weaponTierKey, PersistentDataType.STRING, tier.getKey());
        }
        stack.setItemMeta(meta);
        return new WeaponProfile(type, tier);
    }

    // endregion

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ItemStack main = player.getInventory().getItemInMainHand();
        resolveWeapon(main);
        ItemStack off = player.getInventory().getItemInOffHand();
        resolveWeapon(off);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onMeleeAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        ItemStack main = player.getInventory().getItemInMainHand();
        WeaponProfile profile = resolveWeapon(main);
        if (profile == null) {
            return;
        }
        switch (profile.type()) {
            case SWORD -> handleSwordAttack(player, victim, event, profile);
            case AXE -> handleAxeAttack(player, victim, event, profile);
            case TRIDENT -> handleTridentMelee(player, victim, event, profile);
            case BOW -> handleBowMelee(player, victim, event, profile);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamaged(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Long window = parryWindows.get(uuid);
        if (window == null || window < System.currentTimeMillis()) {
            return;
        }
        WeaponProfile profile = resolveWeapon(player.getInventory().getItemInMainHand());
        if (profile == null || profile.type() != WeaponType.SWORD) {
            return;
        }
        parryWindows.remove(uuid);
        event.setCancelled(true);
        performSwordCounter(player, event.getDamager(), profile);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack main = player.getInventory().getItemInMainHand();
        WeaponProfile profile = resolveWeapon(main);
        if (profile == null) {
            return;
        }
        Action action = event.getAction();
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        if (!left && !right) {
            return;
        }
        if (right) {
            if (player.isSneaking()) {
                handleDualWield(player, profile);
            } else {
                handleRightClick(player, profile);
            }
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            event.setCancelled(true);
        } else if (left && profile.type() == WeaponType.BOW) {
            // The bow receives a special tap-fire on left click.
            handleBowTapFire(player, profile);
            event.setUseItemInHand(Event.Result.DENY);
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        WeaponProfile profile = resolveWeapon(event.getBow());
        if (profile == null) {
            return;
        }
        if (!(event.getProjectile() instanceof Projectile projectile)) {
            return;
        }
        applyBowProjectileAttributes(player, projectile, event.getForce(), profile);
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player player)) {
            return;
        }
        ItemStack stack = player.getInventory().getItemInMainHand();
        WeaponProfile profile = resolveWeapon(stack);
        if (profile == null) {
            return;
        }
        if (projectile instanceof Trident trident) {
            handleTridentLaunch(player, trident, profile);
        } else if (projectile instanceof Arrow arrow) {
            handleArrowLaunch(player, arrow, profile);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player player)) {
            return;
        }
        if (projectile instanceof Arrow arrow) {
            handleArrowHit(player, arrow, event);
        } else if (projectile instanceof Trident trident) {
            handleTridentHit(player, trident, event);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        swordCombos.remove(uuid);
        parryWindows.remove(uuid);
        empoweredStrikes.remove(uuid);
        enhancedTridentThrow.remove(uuid);
        enhancedArrow.remove(uuid);
        ropeArrow.remove(uuid);
        CooldownMap map = cooldowns.remove(uuid);
        if (map != null) {
            map.clear();
        }
        DashState dash = dashingPlayers.remove(uuid);
        if (dash != null) {
            dash.cancel();
        }
    }

    // region Sword logic

    private void handleSwordAttack(Player player, LivingEntity victim, EntityDamageByEntityEvent event, WeaponProfile profile) {
        if (victim == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        ComboState state = swordCombos.computeIfAbsent(uuid, k -> new ComboState());
        if (state.expireAt < now) {
            state.reset();
        }
        state.registerHit();
        state.expireAt = now + COMBO_RESET_MILLIS;

        double baseDamage = profile.tier().isAdvanced() ? 6.0D : 5.0D;
        event.setDamage(baseDamage);

        boolean empowered = consumeEmpoweredStrike(uuid);
        if (empowered) {
            event.setDamage(event.getDamage() + (profile.tier().isAdvanced() ? 6.0D : 4.0D));
            emitEmpoweredSlash(player);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, profile.tier().isAdvanced() ? 60 : 40,
                    profile.tier().isAdvanced() ? 1 : 0, true, true, true));
        }

        if (state.comboIndex == 3) {
            performSwordWave(player, victim, profile);
            state.reset();
        } else {
            spawnSwordComboParticles(player, state.comboIndex);
        }
    }

    private void performSwordWave(Player player, LivingEntity victim, WeaponProfile profile) {
        World world = player.getWorld();
        Location waveOrigin = victim.getLocation().clone();
        boolean advanced = profile.tier().isAdvanced();
        double radius = advanced ? 5.0D : 3.5D;
        double damage = advanced ? 7.0D : 5.0D;
        if (world.hasStorm() && world.getHighestBlockYAt(waveOrigin) <= waveOrigin.getBlockY() + 2) {
            // rainy weather empowers the wave slightly.
            damage += 1.5D;
        }
        Location particleOrigin = waveOrigin.clone().add(0.0D, 1.0D, 0.0D);
        world.spawnParticle(Particle.SWEEP_ATTACK, particleOrigin, 12, 0.6D, 0.4D, 0.6D, 0.0D);
        world.playSound(waveOrigin, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, advanced ? 0.9F : 1.1F);
        for (LivingEntity target : victim.getLocation().getNearbyLivingEntities(radius)) {
            if (target.equals(player) || target.equals(victim) || !canDamage(player, target)) {
                continue;
            }
            Vector push = target.getLocation().toVector().subtract(victim.getLocation().toVector()).normalize().multiply(0.6D);
            push.setY(0.35D);
            target.damage(damage, player);
            target.setVelocity(target.getVelocity().add(push));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, advanced ? 60 : 40, advanced ? 1 : 0, true, true, true));
            if (advanced) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0, true, true, true));
            }
        }
        spawnLingeringBladeStorm(player, waveOrigin, radius, advanced);
    }

    private void spawnSwordComboParticles(Player player, int comboIndex) {
        double yOffset = 1.0D + (comboIndex * 0.15D);
        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0.0D, yOffset, 0.0D), 10, 0.2D, 0.2D, 0.2D, 0.05D);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.5F, 1.0F + (comboIndex * 0.1F));
    }

    private void performSwordCounter(Player player, Entity attacker, WeaponProfile profile) {
        long cooldown = profile.tier().isAdvanced() ? 3000L : 4500L;
        setCooldown(player.getUniqueId(), "sword_parry", cooldown);
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0F, 1.2F);
        world.spawnParticle(Particle.SONIC_BOOM, player.getEyeLocation(), 1);
        if (attacker instanceof LivingEntity living) {
            double damage = profile.tier().isAdvanced() ? 6.0D : 4.0D;
            living.damage(damage, player);
            living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, profile.tier().isAdvanced() ? 60 : 40, 1, true, true, true));
            Vector knock = living.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(0.9D);
            knock.setY(0.4D);
            living.setVelocity(living.getVelocity().add(knock));
        }
        applyCounterShockwave(player, profile);
    }

    private void handleRightClick(Player player, WeaponProfile profile) {
        switch (profile.type()) {
            case SWORD -> handleSwordParry(player, profile);
            case AXE -> handleAxeGuardBreak(player, profile);
            case TRIDENT -> {
                // default trident right click behaviour stays untouched
                // unless an enhanced throw is queued by the dual wield.
            }
            case BOW -> {
                // handled by EntityShootBowEvent
            }
        }
    }

    private void handleSwordParry(Player player, WeaponProfile profile) {
        UUID uuid = player.getUniqueId();
        if (isOnCooldown(uuid, "sword_parry")) {
            notifyCooldown(player, "패링", getRemaining(uuid, "sword_parry"));
            return;
        }
        long window = profile.tier().isAdvanced() ? PARRY_WINDOW_MILLIS_ADVANCED : PARRY_WINDOW_MILLIS_COMMON;
        parryWindows.put(uuid, System.currentTimeMillis() + window);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7F, 1.4F);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getEyeLocation(), 20, 0.2D, 0.2D, 0.2D, 0.01D);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                profile.tier().isAdvanced() ? 80 : 60, profile.tier().isAdvanced() ? 1 : 0, true, true, true));
        player.setCooldown(player.getInventory().getItemInMainHand().getType(), 10);
    }

    private boolean consumeEmpoweredStrike(UUID uuid) {
        Long expire = empoweredStrikes.get(uuid);
        if (expire == null) {
            return false;
        }
        if (expire < System.currentTimeMillis()) {
            empoweredStrikes.remove(uuid);
            return false;
        }
        empoweredStrikes.remove(uuid);
        return true;
    }

    private void emitEmpoweredSlash(Player player) {
        Location loc = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(0.8D));
        player.getWorld().spawnParticle(Particle.EFFECT, loc, 40, 0.2D, 0.2D, 0.2D, 0.0D);
        player.getWorld().playSound(loc, Sound.ITEM_TRIDENT_THUNDER, 0.6F, 1.4F);
    }

    private void spawnLingeringBladeStorm(Player player, Location origin, double radius, boolean advanced) {
        Location center = origin.clone();
        new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || ticks >= (advanced ? 60 : 40)) {
                    cancel();
                    return;
                }
                World world = center.getWorld();
                double swirl = Math.max(1.5D, radius * 0.6D);
                for (int i = 0; i < (advanced ? 3 : 2); i++) {
                    double angle = (ticks / 6.0D) + (i * (Math.PI * 2) / (advanced ? 3.0D : 2.0D));
                    double x = Math.cos(angle) * swirl;
                    double z = Math.sin(angle) * swirl;
                    Location particle = center.clone().add(x, 0.25D + (0.08D * i), z);
                    world.spawnParticle(advanced ? Particle.ELECTRIC_SPARK : Particle.CRIT, particle, 2, 0.05D, 0.05D, 0.05D, 0.0D);
                }
                for (LivingEntity entity : center.getNearbyLivingEntities(radius)) {
                    if (!canDamage(player, entity)) {
                        continue;
                    }
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, advanced ? 2 : 1, true, true, true));
                    if (advanced) {
                        entity.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 20, 0, true, true, true));
                    }
                }
                ticks += 5;
            }
        }.runTaskTimer(plugin, 5L, 5L);
    }

    private void spawnSwordDashTrail(Player player, boolean advanced) {
        new BukkitRunnable() {
            private int iterations = 0;

            @Override
            public void run() {
                if (!player.isOnline() || iterations > 8) {
                    cancel();
                    return;
                }
                Location snapshot = player.getLocation().clone();
                World world = snapshot.getWorld();
                world.spawnParticle(Particle.END_ROD, snapshot.add(0.0D, 1.0D, 0.0D), advanced ? 12 : 6, 0.3D, 0.2D, 0.3D, 0.01D);
                if (advanced) {
                    world.spawnParticle(Particle.SWEEP_ATTACK, snapshot, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
                iterations++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void applyCounterShockwave(Player player, WeaponProfile profile) {
        World world = player.getWorld();
        Location center = player.getLocation();
        double radius = profile.tier().isAdvanced() ? 3.5D : 2.5D;
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
        world.playSound(center, Sound.ITEM_SHIELD_BREAK, 0.5F, profile.tier().isAdvanced() ? 1.0F : 1.2F);
        for (LivingEntity entity : center.getNearbyLivingEntities(radius)) {
            if (!canDamage(player, entity) || entity.equals(player)) {
                continue;
            }
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, profile.tier().isAdvanced() ? 80 : 60,
                    profile.tier().isAdvanced() ? 2 : 1, true, true, true));
            if (profile.tier().isAdvanced()) {
                entity.damage(2.0D, player);
                entity.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 40, 0, true, true, true));
            }
        }
    }

    private void handleDualWield(Player player, WeaponProfile profile) {
        switch (profile.type()) {
            case SWORD -> handleSwordTwoHand(player, profile);
            case AXE -> handleAxeTwoHand(player, profile);
            case TRIDENT -> handleTridentTwoHand(player, profile);
            case BOW -> handleBowTwoHand(player, profile);
        }
    }

    private void handleSwordTwoHand(Player player, WeaponProfile profile) {
        UUID uuid = player.getUniqueId();
        if (profile.tier().isAdvanced()) {
            if (isOnCooldown(uuid, "sword_wave_dash")) {
                notifyCooldown(player, "파동베기", getRemaining(uuid, "sword_wave_dash"));
                return;
            }
            setCooldown(uuid, "sword_wave_dash", 12000L);
            performSwordDash(player, profile);
        } else {
            if (isOnCooldown(uuid, "sword_empower")) {
                notifyCooldown(player, "강화 일격", getRemaining(uuid, "sword_empower"));
                return;
            }
            setCooldown(uuid, "sword_empower", 8000L);
            empoweredStrikes.put(uuid, System.currentTimeMillis() + 6000L);
            player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0.0D, 1.0D, 0.0D), 45, 0.8D, 0.6D, 0.8D, 0.0D);
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8F, 1.2F);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0, true, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 0, true, true, true));
        }
    }

    private void performSwordDash(Player player, WeaponProfile profile) {
        Vector direction = player.getLocation().getDirection().normalize();
        direction.setY(0.15D);
        player.setVelocity(direction.multiply(1.2D));
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0F, 1.35F);
        world.spawnParticle(Particle.CLOUD, player.getLocation(), 20, 0.5D, 0.1D, 0.5D, 0.0D);
        spawnSwordDashTrail(player, profile.tier().isAdvanced());
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Location center = player.getLocation();
            world.spawnParticle(Particle.SWEEP_ATTACK, center, 30, 1.0D, 0.4D, 1.0D, 0.0D);
            world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6F, 1.6F);
            double radius = profile.tier().isAdvanced() ? 6.5D : 5.0D;
            double damage = profile.tier().isAdvanced() ? 10.0D : 8.0D;
            for (LivingEntity target : player.getLocation().getNearbyLivingEntities(radius)) {
                if (!canDamage(player, target)) {
                    continue;
                }
                target.damage(damage, player);
                Vector push = target.getLocation().toVector().subtract(center.toVector()).normalize().multiply(1.1D);
                push.setY(0.5D);
                target.setVelocity(target.getVelocity().add(push));
            }
            if (profile.tier().isAdvanced()) {
                spawnLingeringBladeStorm(player, center, radius - 1.0D, true);
            }
        }, 8L);
    }

    // endregion

    // region Axe logic

    private void handleAxeAttack(Player player, LivingEntity victim, EntityDamageByEntityEvent event, WeaponProfile profile) {
        if (victim == null) {
            return;
        }
        double multiplier = profile.tier().isAdvanced() ? 1.9D : 1.6D;
        event.setDamage(event.getDamage() * multiplier);
        player.setCooldown(player.getInventory().getItemInMainHand().getType(), profile.tier().isAdvanced() ? 18 : 22);
        spawnAxeImpact(player, victim);
        if (victim instanceof Player target && target.isBlocking()) {
            double chance = profile.tier().isAdvanced() ? 0.75D : 0.5D;
            if (Math.random() <= chance) {
                target.setCooldown(Material.SHIELD, profile.tier().isAdvanced() ? 140 : 100);
                target.getWorld().playSound(target.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.0F, 0.8F);
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0));
            }
        }
    }

    private void spawnAxeImpact(Player player, LivingEntity victim) {
        victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0.0D, 1.0D, 0.0D), 25, 0.4D, 0.4D, 0.4D, 0.0D);
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 0.6F, 0.6F);
    }

    private void handleAxeGuardBreak(Player player, WeaponProfile profile) {
        UUID uuid = player.getUniqueId();
        if (isOnCooldown(uuid, "axe_shatter")) {
            notifyCooldown(player, "방패 깨기", getRemaining(uuid, "axe_shatter"));
            return;
        }
        LivingEntity target = rayTraceLiving(player, 4.0D);
        if (target == null) {
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6F, 0.6F);
            return;
        }
        setCooldown(uuid, "axe_shatter", profile.tier().isAdvanced() ? 6000L : 8000L);
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0F, 0.6F);
        world.spawnParticle(Particle.CRIT, target.getLocation().add(0.0D, 1.2D, 0.0D), 30, 0.3D, 0.3D, 0.3D, 0.0D);
        Vector knock = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(profile.tier().isAdvanced() ? 1.2D : 0.8D);
        knock.setY(0.4D);
        target.setVelocity(target.getVelocity().add(knock));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, profile.tier().isAdvanced() ? 80 : 60, 1));
        target.damage(profile.tier().isAdvanced() ? 4.0D : 2.0D, player);
        if (target instanceof Player targetPlayer) {
            targetPlayer.setCooldown(Material.SHIELD, profile.tier().isAdvanced() ? 200 : 160);
            degradeShieldDurability(targetPlayer, profile.tier());
        }
    }

    private void degradeShieldDurability(Player target, WeaponTier tier) {
        ItemStack shield = target.getInventory().getItemInOffHand();
        boolean offHand = true;
        if (shield == null || shield.getType() != Material.SHIELD) {
            shield = target.getInventory().getItemInMainHand();
            offHand = false;
        }
        if (shield == null || shield.getType() != Material.SHIELD) {
            return;
        }
        ItemMeta meta = shield.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return;
        }
        int extra = tier.isAdvanced() ? 70 : 45;
        int max = shield.getType().getMaxDurability() - 1;
        damageable.setDamage(Math.min(max, damageable.getDamage() + extra));
        shield.setItemMeta(meta);
        if (offHand) {
            target.getInventory().setItemInOffHand(shield);
        } else {
            target.getInventory().setItemInMainHand(shield);
        }
        target.getWorld().spawnParticle(Particle.CRIT, target.getEyeLocation(), 5, 0.1D, 0.2D, 0.1D, 0.0D);
    }

    private void handleAxeTwoHand(Player player, WeaponProfile profile) {
        UUID uuid = player.getUniqueId();
        if (profile.tier().isAdvanced()) {
            if (isOnCooldown(uuid, "axe_quake")) {
                notifyCooldown(player, "땅울림", getRemaining(uuid, "axe_quake"));
                return;
            }
            setCooldown(uuid, "axe_quake", 13000L);
            performAxeQuake(player, profile);
        } else {
            if (isOnCooldown(uuid, "axe_frenzy")) {
                notifyCooldown(player, "강화", getRemaining(uuid, "axe_frenzy"));
                return;
            }
            setCooldown(uuid, "axe_frenzy", 15000L);
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 0));
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_ANGRY, 0.8F, 0.6F);
            player.getWorld().spawnParticle(Particle.ASH, player.getLocation(), 40, 0.4D, 0.2D, 0.4D, 0.0D);
        }
    }

    private void performAxeQuake(Player player, WeaponProfile profile) {
        Location loc = player.getLocation();
        World world = player.getWorld();
        world.playSound(loc, Sound.ENTITY_WARDEN_EMERGE, 1.0F, 0.5F);
        Block base = loc.getBlock();
        BlockData data = base.getBlockData();
        world.spawnParticle(Particle.BLOCK_CRUMBLE, loc, 80, 1.2D, 0.4D, 1.2D, data);
        double radius = 5.0D;
        double damage = profile.tier().isAdvanced() ? 7.0D : 5.0D;
        spawnQuakeRing(world, loc, data, radius, profile.tier().isAdvanced());
        summonQuakeDebris(world, loc, data, profile.tier().isAdvanced());
        for (LivingEntity target : player.getLocation().getNearbyLivingEntities(radius)) {
            if (!canDamage(player, target)) {
                continue;
            }
            target.damage(damage, player);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1));
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0));
            target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE,
                    profile.tier().isAdvanced() ? 80 : 60, profile.tier().isAdvanced() ? 1 : 0, true, true, true));
            if (profile.tier().isAdvanced()) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0, true, true, true));
            }
            Vector push = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(0.8D);
            push.setY(0.6D);
            target.setVelocity(target.getVelocity().add(push));
        }
    }

    private void spawnQuakeRing(World world, Location center, BlockData data, double radius, boolean advanced) {
        double step = Math.PI / 10.0D;
        for (double angle = 0.0D; angle < Math.PI * 2; angle += step) {
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location particle = center.clone().add(x, 0.2D, z);
            world.spawnParticle(Particle.BLOCK_CRUMBLE, particle, advanced ? 6 : 3, 0.1D, 0.1D, 0.1D, data);
            if (advanced) {
                world.spawnParticle(Particle.ASH, particle, 1, 0.0D, 0.05D, 0.0D, 0.0D);
            }
        }
    }

    private void summonQuakeDebris(World world, Location center, BlockData data, boolean advanced) {
        if (data.getMaterial().isAir()) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int pieces = advanced ? 6 : 4;
        for (int i = 0; i < pieces; i++) {
            double offsetX = random.nextDouble(-1.2D, 1.2D);
            double offsetZ = random.nextDouble(-1.2D, 1.2D);
            Location spawn = center.clone().add(offsetX, 1.0D, offsetZ);
            FallingBlock falling = world.spawnFallingBlock(spawn, data);
            falling.setDropItem(false);
            falling.setHurtEntities(false);
            falling.setVelocity(new Vector(offsetX * 0.15D, 0.6D + random.nextDouble(0.3D), offsetZ * 0.15D));
        }
    }

    // endregion

    // region Trident logic

    private void handleTridentMelee(Player player, LivingEntity victim, EntityDamageByEntityEvent event, WeaponProfile profile) {
        if (victim == null) {
            return;
        }
        double base = profile.tier().isAdvanced() ? 6.5D : 5.5D;
        if (isMoist(player)) {
            base += 1.0D;
        }
        event.setDamage(base);
        player.setCooldown(Material.TRIDENT, profile.tier().isAdvanced() ? 8 : 10);
        victim.getWorld().spawnParticle(Particle.SPLASH, victim.getLocation().add(0.0D, 1.0D, 0.0D), 15, 0.2D, 0.2D, 0.2D, 0.0D);
        victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_TRIDENT_HIT, 1.0F, 1.0F);
        if (isMoist(player)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                    profile.tier().isAdvanced() ? 80 : 60, 1, true, true, true));
        }
        if (profile.tier().isAdvanced()) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0, true, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 60, 1, true, true, true));
        }
    }

    private void handleTridentLaunch(Player player, Trident trident, WeaponProfile profile) {
        UUID uuid = player.getUniqueId();
        PersistentDataContainer container = trident.getPersistentDataContainer();
        container.set(arrowTierKey, PersistentDataType.BYTE, (byte) profile.tier().ordinal());
        if (consumePending(enhancedTridentThrow, uuid)) {
            trident.setVelocity(trident.getVelocity().multiply(profile.tier().isAdvanced() ? 1.8D : 1.4D));
            trident.getWorld().spawnParticle(Particle.UNDERWATER, trident.getLocation(), 30, 0.2D, 0.2D, 0.2D, 0.1D);
            trident.getWorld().playSound(trident.getLocation(), Sound.ITEM_TRIDENT_THROW, 1.0F, 1.3F);
        }
        if (profile.tier().isAdvanced()) {
            trident.setPierceLevel(1);
            trident.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, trident.getLocation(), 10, 0.1D, 0.1D, 0.1D, 0.0D);
        } else {
            trident.setPierceLevel(0);
        }
    }

    private void handleTridentTwoHand(Player player, WeaponProfile profile) {
        UUID uuid = player.getUniqueId();
        if (profile.tier().isAdvanced()) {
            if (isOnCooldown(uuid, "trident_dash")) {
                notifyCooldown(player, "꿰뚫기", getRemaining(uuid, "trident_dash"));
                return;
            }
            setCooldown(uuid, "trident_dash", 12000L);
            startDash(player, profile);
        } else {
            if (isOnCooldown(uuid, "trident_charge")) {
                notifyCooldown(player, "강화 투척", getRemaining(uuid, "trident_charge"));
                return;
            }
            setCooldown(uuid, "trident_charge", 9000L);
            enhancedTridentThrow.put(uuid, System.currentTimeMillis() + 6000L);
            player.getWorld().spawnParticle(Particle.BUBBLE, player.getLocation().add(0.0D, 1.0D, 0.0D), 25, 0.4D, 0.3D, 0.4D, 0.0D);
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 0.8F, 1.3F);
        }
    }

    private void handleTridentHit(Player player, Trident trident, ProjectileHitEvent event) {
        boolean watery = trident.isInWater();
        WeaponTier tier = WeaponTier.values()[trident.getPersistentDataContainer()
                .getOrDefault(arrowTierKey, PersistentDataType.BYTE, (byte) 0)];
        if (!watery) {
            player.getWorld().spawnParticle(Particle.CLOUD, trident.getLocation(), 8, 0.1D, 0.1D, 0.1D, 0.01D);
        }
        if (trident.getPierceLevel() > 0 && event.getHitEntity() instanceof LivingEntity hit) {
            hit.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
            if (tier.isAdvanced()) {
                hit.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0, true, true, true));
            }
        }
        spawnTridentShockwave(player, trident.getLocation(), tier, watery);
    }

    private void startDash(Player player, WeaponProfile profile) {
        UUID uuid = player.getUniqueId();
        DashState previous = dashingPlayers.remove(uuid);
        if (previous != null) {
            previous.cancel();
        }
        DashState state = new DashState(player, profile.tier(), profile.tier().isAdvanced() ? 1.8D : 1.4D);
        dashingPlayers.put(uuid, state);
        state.start();
    }

    private void spawnTridentShockwave(Player player, Location center, WeaponTier tier, boolean watery) {
        World world = center.getWorld();
        Particle particle = watery ? Particle.SPLASH : Particle.CLOUD;
        world.spawnParticle(particle, center, watery ? 40 : 20, 0.4D, 0.2D, 0.4D, watery ? 0.05D : 0.02D);
        world.playSound(center, watery ? Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE : Sound.ITEM_TRIDENT_RETURN, 0.8F,
                watery ? 1.3F : 0.9F);
        double radius = watery ? (tier.isAdvanced() ? 5.0D : 3.5D) : (tier.isAdvanced() ? 3.5D : 2.5D);
        for (LivingEntity entity : center.getNearbyLivingEntities(radius)) {
            if (!canDamage(player, entity)) {
                continue;
            }
            entity.damage(tier.isAdvanced() ? 3.0D : 1.5D, player);
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, watery ? 100 : 60, tier.isAdvanced() ? 2 : 1,
                    true, true, true));
            if (tier.isAdvanced()) {
                entity.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0, true, true, true));
            }
        }
    }

    private boolean isMoist(Player player) {
        Location loc = player.getLocation();
        Block block = loc.getBlock();
        return block.getType() == Material.WATER || block.isLiquid() || player.getWorld().hasStorm();
    }

    // endregion

    // region Bow logic

    private void handleBowMelee(Player player, LivingEntity victim, EntityDamageByEntityEvent event, WeaponProfile profile) {
        if (victim == null) {
            return;
        }
        event.setDamage(profile.tier().isAdvanced() ? 4.0D : 3.0D);
        victim.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0));
        player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, victim.getLocation().add(0.0D, 1.0D, 0.0D), 15, 0.2D, 0.2D, 0.2D, 0.0D);
    }

    private void handleBowTapFire(Player player, WeaponProfile profile) {
        if (isOnCooldown(player.getUniqueId(), "bow_tap")) {
            notifyCooldown(player, "신속 사격", getRemaining(player.getUniqueId(), "bow_tap"));
            return;
        }
        setCooldown(player.getUniqueId(), "bow_tap", profile.tier().isAdvanced() ? 2000L : 3000L);
        Arrow arrow = player.launchProjectile(Arrow.class, player.getLocation().getDirection().normalize().multiply(profile.tier().isAdvanced() ? 2.2D : 1.8D));
        arrow.setCritical(true);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.getPersistentDataContainer().set(arrowEffectKey, PersistentDataType.STRING, "tap");
        arrow.getPersistentDataContainer().set(arrowTierKey, PersistentDataType.BYTE, (byte) profile.tier().ordinal());
        arrow.getPersistentDataContainer().set(arrowOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.8F, 1.8F);
        player.swingMainHand();
    }

    private void applyBowProjectileAttributes(Player player, Projectile projectile, float force, WeaponProfile profile) {
        PersistentDataContainer container = projectile.getPersistentDataContainer();
        container.set(arrowOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        container.set(arrowTierKey, PersistentDataType.BYTE, (byte) profile.tier().ordinal());
        String effect;
        if (force < 0.35F) {
            effect = "explosion";
        } else if (force < 0.75F) {
            effect = "tracking";
        } else {
            effect = "willow";
        }
        container.set(arrowEffectKey, PersistentDataType.STRING, effect);
        if (consumePending(enhancedArrow, player.getUniqueId())) {
            projectile.setVelocity(projectile.getVelocity().multiply(profile.tier().isAdvanced() ? 1.8D : 1.4D));
            projectile.getWorld().spawnParticle(Particle.ENCHANTED_HIT, projectile.getLocation(), 20, 0.2D, 0.2D, 0.2D, 0.01D);
        }
        if (consumePending(ropeArrow, player.getUniqueId())) {
            container.set(arrowEffectKey, PersistentDataType.STRING, "rope");
        }
    }

    private void handleArrowLaunch(Player player, Arrow arrow, WeaponProfile profile) {
        String effect = arrow.getPersistentDataContainer().get(arrowEffectKey, PersistentDataType.STRING);
        if ("tracking".equals(effect)) {
            startTrackingArrow(player, arrow, profile.tier());
        }
    }

    private void handleArrowHit(Player player, Arrow arrow, ProjectileHitEvent event) {
        PersistentDataContainer container = arrow.getPersistentDataContainer();
        String effect = container.get(arrowEffectKey, PersistentDataType.STRING);
        if (effect == null) {
            return;
        }
        WeaponTier tier = WeaponTier.values()[container.getOrDefault(arrowTierKey, PersistentDataType.BYTE, (byte) 0)];
        switch (effect) {
            case "explosion" -> explodeArrow(player, arrow, tier);
            case "tracking" -> impactShock(player, arrow, tier);
            case "willow" -> igniteArrow(player, arrow, tier, event.getHitEntity());
            case "rope" -> ropeArrow(player, arrow, tier, event);
            case "tap" -> impactShock(player, arrow, tier);
        }
        BukkitTask task = trackingTasks.remove(arrow.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    private void explodeArrow(Player player, Arrow arrow, WeaponTier tier) {
        float power = tier.isAdvanced() ? 2.5F : 1.8F;
        arrow.getWorld().createExplosion(arrow.getLocation(), power, tier.isAdvanced(), false, player);
    }

    private void impactShock(Player player, Arrow arrow, WeaponTier tier) {
        double radius = tier.isAdvanced() ? 4.0D : 3.0D;
        double damage = tier.isAdvanced() ? 5.0D : 3.5D;
        arrow.getWorld().spawnParticle(Particle.DUST, arrow.getLocation(), 20, 0.3D, 0.3D, 0.3D, new Particle.DustOptions(Color.LIME, 1.0F));
        for (LivingEntity entity : arrow.getLocation().getNearbyLivingEntities(radius)) {
            if (!canDamage(player, entity)) {
                continue;
            }
            entity.damage(damage, player);
            if (tier.isAdvanced()) {
                entity.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, true, true, true));
                entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20, 0, true, true, true));
            }
        }
    }

    private void igniteArrow(Player player, Arrow arrow, WeaponTier tier, Entity hit) {
        World world = arrow.getWorld();
        world.spawnParticle(Particle.FLAME, arrow.getLocation(), 40, 0.3D, 0.3D, 0.3D, 0.01D);
        world.playSound(arrow.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.0F, 1.2F);
        if (hit instanceof LivingEntity living) {
            int duration = tier.isAdvanced() ? 140 : 100;
            if (world.hasStorm()) {
                duration = Math.max(40, duration - 40);
            }
            living.setFireTicks(duration);
            if (tier.isAdvanced()) {
                living.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0, true, true, true));
            }
        } else {
            Block block = arrow.getLocation().getBlock();
            if (block.getType().isFlammable() && !world.hasStorm()) {
                block.getRelative(0, 1, 0).setType(Material.FIRE);
            }
        }
        if (tier.isAdvanced()) {
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, arrow.getLocation(), 30, 0.4D, 0.3D, 0.4D, 0.01D);
            for (LivingEntity entity : world.getNearbyLivingEntities(arrow.getLocation(), 2.5D)) {
                if (hit != null && entity.equals(hit)) {
                    continue;
                }
                if (!canDamage(player, entity)) {
                    continue;
                }
                entity.setFireTicks(60);
            }
        }
    }

    private void ropeArrow(Player player, Arrow arrow, WeaponTier tier, ProjectileHitEvent event) {
        Location hitLocation = event.getHitBlock() != null ? event.getHitBlock().getLocation().add(0.5D, 1.0D, 0.5D)
                : (event.getHitEntity() != null ? event.getHitEntity().getLocation() : arrow.getLocation());
        Vector pull = hitLocation.toVector().subtract(player.getLocation().toVector()).normalize();
        double power = tier.isAdvanced() ? 1.4D : 1.0D;
        player.setVelocity(pull.multiply(power));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0F, 1.2F);
        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0.0D, 1.0D, 0.0D), 20, 0.2D, 0.2D, 0.2D, 0.01D);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, tier.isAdvanced() ? 80 : 40, 0, true, true, true));
        if (tier.isAdvanced() && event.getHitEntity() instanceof LivingEntity living) {
            Vector reverse = player.getLocation().toVector().subtract(living.getLocation().toVector()).normalize().multiply(0.7D);
            reverse.setY(0.4D);
            living.setVelocity(living.getVelocity().add(reverse));
            living.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 20, 0, true, true, true));
        }
        if (tier.isAdvanced()) {
            player.getWorld().spawnParticle(Particle.FALLING_SPORE_BLOSSOM, hitLocation, 12, 0.2D, 0.2D, 0.2D, 0.0D);
        }
    }

    private void handleBowTwoHand(Player player, WeaponProfile profile) {
        UUID uuid = player.getUniqueId();
        if (profile.tier().isAdvanced()) {
            if (isOnCooldown(uuid, "bow_rope")) {
                notifyCooldown(player, "밧줄 화살", getRemaining(uuid, "bow_rope"));
                return;
            }
            setCooldown(uuid, "bow_rope", 10000L);
            ropeArrow.put(uuid, System.currentTimeMillis() + 8000L);
            player.getWorld().spawnParticle(Particle.INSTANT_EFFECT, player.getLocation().add(0.0D, 1.0D, 0.0D), 30, 0.3D, 0.3D, 0.3D, 0.01D);
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 0.8F, 0.9F);
        } else {
            if (isOnCooldown(uuid, "bow_enhanced")) {
                notifyCooldown(player, "강화 화살", getRemaining(uuid, "bow_enhanced"));
                return;
            }
            setCooldown(uuid, "bow_enhanced", 7000L);
            enhancedArrow.put(uuid, System.currentTimeMillis() + 8000L);
            player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0.0D, 1.0D, 0.0D), 35, 0.5D, 0.3D, 0.5D, 0.0D);
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_LOADING_MIDDLE, 0.8F, 1.3F);
        }
    }

    // endregion

    private void startTrackingArrow(Player player, Arrow arrow, WeaponTier tier) {
        BukkitRunnable runnable = new BukkitRunnable() {
            private int cycles = 0;

            @Override
            public void run() {
                if (arrow.isDead() || arrow.isOnGround()) {
                    cancelAndForget();
                    return;
                }
                cycles++;
                arrow.getWorld().spawnParticle(Particle.CRIT, arrow.getLocation(), 2, 0.0D, 0.0D, 0.0D, 0.0D);
                LivingEntity target = findNearestTarget(player, arrow.getLocation(), 12.0D);
                if (target != null) {
                    Vector direction = target.getEyeLocation().toVector().subtract(arrow.getLocation().toVector()).normalize();
                    arrow.setVelocity(direction.multiply(tier.isAdvanced() ? 2.0D : 1.6D));
                    arrow.setCritical(true);
                    if (tier.isAdvanced() && cycles % 5 == 0) {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, true, true, true));
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 0, true, true, true));
                        arrow.getWorld().playSound(arrow.getLocation(), Sound.ENTITY_DROWNED_SHOOT, 0.6F, 1.5F);
                    }
                } else if (cycles > 80) {
                    cancelAndForget();
                }
            }

            private void cancelAndForget() {
                cancel();
                trackingTasks.remove(arrow.getUniqueId());
            }
        };
        BukkitTask task = runnable.runTaskTimer(plugin, 2L, 2L);
        trackingTasks.put(arrow.getUniqueId(), task);
    }

    // region Utility helpers

    private LivingEntity rayTraceLiving(Player player, double maxDistance) {
        RayTraceResult result = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getLocation().getDirection(), maxDistance,
                entity -> entity instanceof LivingEntity && !entity.equals(player));
        if (result != null && result.getHitEntity() instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    private LivingEntity findNearestTarget(Player owner, Location origin, double radius) {
        double closest = Double.MAX_VALUE;
        LivingEntity selected = null;
        for (Entity entity : origin.getWorld().getNearbyEntities(origin, radius, radius, radius, e -> e instanceof LivingEntity && !e.equals(owner))) {
            LivingEntity living = (LivingEntity) entity;
            if (!canDamage(owner, living)) {
                continue;
            }
            double distance = living.getLocation().distanceSquared(origin);
            if (distance < closest) {
                closest = distance;
                selected = living;
            }
        }
        return selected;
    }

    private boolean canDamage(Player owner, LivingEntity target) {
        if (target instanceof Player playerTarget && playerTarget.equals(owner)) {
            return false;
        }
        if (!target.isValid() || target.isDead()) {
            return false;
        }
        return true;
    }

    private boolean isOnCooldown(UUID uuid, String key) {
        CooldownMap map = cooldowns.get(uuid);
        return map != null && map.isOnCooldown(key);
    }

    private long getRemaining(UUID uuid, String key) {
        CooldownMap map = cooldowns.get(uuid);
        return map == null ? 0L : map.getRemaining(key);
    }

    private void setCooldown(UUID uuid, String key, long durationMillis) {
        cooldowns.computeIfAbsent(uuid, id -> new CooldownMap()).setCooldown(key, durationMillis);
    }

    private void notifyCooldown(Player player, String ability, long remaining) {
        double seconds = Math.ceil(remaining / 100.0D) / 10.0D;
        String text = String.format(Locale.KOREA, "%s 재사용 대기중: %.1f초", ability, seconds);
        player.sendActionBar(Component.text(text, NamedTextColor.RED));
    }

    private boolean consumePending(Map<UUID, Long> map, UUID uuid) {
        Long expire = map.get(uuid);
        if (expire == null) {
            return false;
        }
        if (expire < System.currentTimeMillis()) {
            map.remove(uuid);
            return false;
        }
        map.remove(uuid);
        return true;
    }

    // endregion

    private static final class ComboState {
        private int comboIndex = 0;
        private long expireAt = 0L;

        void registerHit() {
            comboIndex++;
            if (comboIndex > 3) {
                comboIndex = 3;
            }
        }

        void reset() {
            comboIndex = 0;
            expireAt = 0L;
        }
    }

    private static final class CooldownMap {
        private final Map<String, Long> cooldowns = new HashMap<>();

        boolean isOnCooldown(String key) {
            Long expire = cooldowns.get(key);
            return expire != null && expire > System.currentTimeMillis();
        }

        long getRemaining(String key) {
            Long expire = cooldowns.get(key);
            return expire == null ? 0L : Math.max(0L, expire - System.currentTimeMillis());
        }

        void setCooldown(String key, long durationMillis) {
            cooldowns.put(key, System.currentTimeMillis() + durationMillis);
        }

        void clear() {
            cooldowns.clear();
        }
    }

    private final class DashState implements Runnable {

        private final Player player;
        private final WeaponTier tier;
        private final double speed;
        private final Set<UUID> hit = new HashSet<>();
        private BukkitTask task;
        private int ticks;

        private DashState(Player player, WeaponTier tier, double speed) {
            this.player = player;
            this.tier = tier;
            this.speed = speed;
        }

        void start() {
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 1.0F, 1.4F);
            Vector velocity = player.getLocation().getDirection().normalize().multiply(speed);
            velocity.setY(0.2D);
            player.setVelocity(velocity);
            player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, tier.isAdvanced() ? 120 : 80, 1, true, true, true));
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this, 0L, 1L);
        }

        @Override
        public void run() {
            if (!player.isOnline()) {
                cancel();
                return;
            }
            ticks++;
            if (ticks > 10) {
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RETURN, 0.6F, 1.4F);
                cancel();
                return;
            }
            Location loc = player.getLocation();
            player.getWorld().spawnParticle(Particle.SPLASH, loc, 10, 0.2D, 0.1D, 0.2D, 0.0D);
            player.getWorld().spawnParticle(Particle.BUBBLE_POP, loc, tier.isAdvanced() ? 6 : 3, 0.3D, 0.1D, 0.3D, 0.01D);
            for (LivingEntity entity : player.getLocation().getNearbyLivingEntities(2.5D)) {
                if (!canDamage(player, entity) || hit.contains(entity.getUniqueId())) {
                    continue;
                }
                hit.add(entity.getUniqueId());
                entity.damage(tier.isAdvanced() ? 7.0D : 6.0D, player);
                Vector push = entity.getLocation().toVector().subtract(loc.toVector()).normalize().multiply(1.4D);
                push.setY(0.3D);
                entity.setVelocity(entity.getVelocity().add(push));
                entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, tier.isAdvanced() ? 80 : 60,
                        tier.isAdvanced() ? 2 : 1, true, true, true));
                if (tier.isAdvanced()) {
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0, true, true, true));
                }
            }
        }

        void cancel() {
            if (task != null) {
                task.cancel();
            }
            dashingPlayers.remove(player.getUniqueId());
            if (player.isOnline()) {
                spawnTridentShockwave(player, player.getLocation(), tier, player.getLocation().getBlock().isLiquid());
            }
        }
    }
}

