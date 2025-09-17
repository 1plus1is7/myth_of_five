package me.j17e4eo.mythof5.inherit;

import me.j17e4eo.mythof5.Mythof5;
import me.j17e4eo.mythof5.config.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InheritManager {

    private final Mythof5 plugin;
    private final NamespacedKey inheritorFlagKey;
    private final NamespacedKey powerKeyKey;
    private final NamespacedKey transformationFlagKey;
    private final NamespacedKey transformationGlowKey;
    private final NamespacedKey goblinFlameKey;
    private final String powerKeyValue;
    private final boolean announceEnabled;
    private final Messages messages;
    private final List<AttributeBuff> buffs = new ArrayList<>();
    private final Map<UUID, Map<Attribute, Double>> storedBaseValues = new ConcurrentHashMap<>();
    private final Set<UUID> transformedPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<UUID> applyTokens = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<UUID> removeTokens = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final double transformationScaleMultiplier;
    private final double transformationAttackBonus;
    private final NamespacedKey transformationScaleModifierKey;
    private final NamespacedKey transformationAttackModifierKey;
    private final Component goblinFlameDisplayName = Component.text("도깨비불", NamedTextColor.GOLD);
    private static final double ATTRIBUTE_EPSILON = 1.0E-4D;
    private File dataFile;
    private YamlConfiguration dataConfig;
    private UUID inheritorId;
    private String inheritorName;

    public InheritManager(Mythof5 plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
        this.inheritorFlagKey = new NamespacedKey(plugin, "is_inheritor");
        this.powerKeyKey = new NamespacedKey(plugin, "power_key");
        this.transformationFlagKey = new NamespacedKey(plugin, "goblin_transformed");
        this.transformationGlowKey = new NamespacedKey(plugin, "goblin_prev_glow");
        this.goblinFlameKey = new NamespacedKey(plugin, "goblin_flame");
        this.transformationScaleModifierKey = new NamespacedKey(plugin, "goblin_transform_scale");
        this.transformationAttackModifierKey = new NamespacedKey(plugin, "goblin_transform_attack");
        this.powerKeyValue = plugin.getConfig().getString("inherit.power_key", "dokkaebi.core");
        this.announceEnabled = plugin.getConfig().getBoolean("inherit.announce", true);
        double scale = plugin.getConfig().getDouble("inherit.transformation.scale_multiplier", 2.0D);
        if (scale < 1.0D) {
            plugin.getLogger().warning("inherit.transformation.scale_multiplier must be at least 1.0. Using 1.0 instead.");
            scale = 1.0D;
        }
        this.transformationScaleMultiplier = scale;
        this.transformationAttackBonus = plugin.getConfig().getDouble("inherit.transformation.attack_bonus", 6.0D);
        loadBuffs();
    }

    public void load() {
        plugin.getDataFolder().mkdirs();
        dataFile = new File(plugin.getDataFolder(), "inherit.yml");
        if (!dataFile.exists()) {
            dataConfig = new YamlConfiguration();
            return;
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        String uuid = dataConfig.getString("inheritor.uuid");
        if (uuid != null && !uuid.isEmpty()) {
            try {
                inheritorId = UUID.fromString(uuid);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Invalid inheritor UUID in data file: " + uuid);
            }
        }
        inheritorName = dataConfig.getString("inheritor.name");
    }

    public void save() {
        if (dataConfig == null) {
            dataConfig = new YamlConfiguration();
        }
        if (inheritorId == null) {
            dataConfig.set("inheritor", null);
        } else {
            dataConfig.set("inheritor.uuid", inheritorId.toString());
            dataConfig.set("inheritor.name", inheritorName);
        }
        if (dataFile == null) {
            dataFile = new File(plugin.getDataFolder(), "inherit.yml");
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save inherit data: " + e.getMessage());
        }
    }

    public void reapplyToOnlinePlayers() {
        if (inheritorId == null) {
            return;
        }
        Player player = Bukkit.getPlayer(inheritorId);
        if (player != null) {
            applyPower(player);
        }
    }

    public void handlePlayerJoin(Player player) {
        UUID uuid = player.getUniqueId();
        if (inheritorId != null && inheritorId.equals(uuid)) {
            applyPower(player);
            return;
        }

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        boolean hasFlag = pdc.has(inheritorFlagKey, PersistentDataType.BYTE);
        String storedPowerKey = pdc.get(powerKeyKey, PersistentDataType.STRING);
        boolean matchesPowerKey = storedPowerKey != null && storedPowerKey.equals(powerKeyValue);
        boolean hasBuffs = buffs.stream().anyMatch(buff -> hasAttributeBuff(player, buff));
        boolean hasTransformation = pdc.has(transformationFlagKey, PersistentDataType.BYTE) || hasActiveTransformationAttributes(player);
        if (hasFlag || matchesPowerKey || hasBuffs || hasTransformation) {
            removePower(player);
        }
    }

    public void handleDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (inheritorId == null || !player.getUniqueId().equals(inheritorId)) {
            return;
        }
        Player killer = player.getKiller();
        if (killer != null && !killer.getUniqueId().equals(player.getUniqueId())) {
            transferInheritance(killer, player);
            return;
        }
        String name = inheritorName != null ? inheritorName : player.getName();
        clearInheritorInternal(true, name);
    }

    public void setInheritor(Player player) {
        UUID uuid = player.getUniqueId();
        if (inheritorId != null && inheritorId.equals(uuid)) {
            inheritorName = player.getName();
            applyPower(player);
            save();
            return;
        }
        if (inheritorId != null && !inheritorId.equals(uuid)) {
            Player previous = Bukkit.getPlayer(inheritorId);
            if (previous != null) {
                removePower(previous);
                previous.sendMessage(Component.text(messages.format("inherit.previous_replaced"), NamedTextColor.GRAY));
            }
        }
        inheritorId = uuid;
        inheritorName = player.getName();
        applyPower(player);
        save();
    }

    public void grantFromBoss(Player player, String bossName) {
        setInheritor(player);
        if (announceEnabled) {
            plugin.broadcast(messages.format("inherit.broadcast.gain", Map.of(
                    "player", player.getName(),
                    "boss", bossName
            )));
        }
        plugin.getLogger().info(String.format("[Event:MYTH_INHERITED] %s inherited the power by defeating %s.", player.getName(), bossName));
    }

    private void transferInheritance(Player killer, Player victim) {
        setInheritor(killer);
        if (announceEnabled) {
            plugin.broadcast(messages.format("inherit.broadcast.transfer", Map.of(
                    "killer", killer.getName(),
                    "victim", victim.getName()
            )));
        }
        plugin.getLogger().info(String.format("[Event:MYTH_TRANSFERRED] %s claimed the power from %s.", killer.getName(), victim.getName()));
    }

    public void clearInheritor(boolean announce) {
        if (inheritorId == null) {
            return;
        }
        String name = inheritorName;
        clearInheritorInternal(announce, name);
    }

    private void clearInheritorInternal(boolean announce, String targetName) {
        UUID previousId = inheritorId;
        inheritorId = null;
        inheritorName = null;
        Player player = previousId != null ? Bukkit.getPlayer(previousId) : null;
        if (player != null) {
            removePower(player);
        }
        save();
        if (announce && targetName != null) {
            if (announceEnabled) {
                plugin.broadcast(messages.format("inherit.broadcast.loss", Map.of("player", targetName)));
            }
            plugin.getLogger().info(String.format("[Event:MYTH_LOST] %s lost the power.", targetName));
        }
    }

    private void applyPower(Player player) {
        UUID uuid = player.getUniqueId();
        if (!applyTokens.add(uuid)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> applyTokens.remove(uuid));

        for (AttributeBuff buff : buffs) {
            applyBuff(player, uuid, buff);
        }

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(inheritorFlagKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(powerKeyKey, PersistentDataType.STRING, powerKeyValue);

        if (pdc.has(transformationFlagKey, PersistentDataType.BYTE)) {
            transformedPlayers.add(uuid);
            applyTransformationAttributes(player);
            player.setGlowing(true);
        }

        giveGoblinFlame(player);
    }

    private void removePower(Player player) {
        UUID uuid = player.getUniqueId();
        if (!removeTokens.add(uuid)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> removeTokens.remove(uuid));

        for (AttributeBuff buff : buffs) {
            removeBuff(player, uuid, buff);
        }

        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (pdc.has(transformationFlagKey, PersistentDataType.BYTE) || transformedPlayers.contains(uuid)) {
            clearTransformation(player);
        }

        removeGoblinFlame(player);

        pdc.remove(inheritorFlagKey);
        pdc.remove(powerKeyKey);
    }

    public void handleGoblinFlameDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!isInheritor(player.getUniqueId())) {
            return;
        }

        ItemStack dropped = event.getItemDrop().getItemStack();
        if (!isGoblinFlame(dropped)) {
            return;
        }

        event.setCancelled(true);
        event.getItemDrop().remove();
        plugin.getServer().getScheduler().runTask(plugin, player::updateInventory);
        toggleTransformation(player);
    }

    private void giveGoblinFlame(Player player) {
        if (hasGoblinFlame(player)) {
            return;
        }

        ItemStack flame = createGoblinFlame();
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(flame);
        if (overflow.isEmpty()) {
            return;
        }

        for (ItemStack itemStack : overflow.values()) {
            Item drop = player.getWorld().dropItem(player.getLocation(), itemStack);
            drop.setOwner(player.getUniqueId());
            drop.setPickupDelay(0);
        }
    }

    private void removeGoblinFlame(Player player) {
        PlayerInventory inventory = player.getInventory();
        boolean changed = false;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isGoblinFlame(stack)) {
                inventory.setItem(slot, null);
                changed = true;
            }
        }

        ItemStack offHand = inventory.getItemInOffHand();
        if (isGoblinFlame(offHand)) {
            inventory.setItemInOffHand(null);
            changed = true;
        }

        if (changed) {
            player.updateInventory();
        }
    }

    private boolean hasGoblinFlame(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (ItemStack stack : inventory.getContents()) {
            if (isGoblinFlame(stack)) {
                return true;
            }
        }
        return isGoblinFlame(inventory.getItemInOffHand());
    }

    public boolean containsGoblinFlame(ItemStack[] stacks) {
        if (stacks == null) {
            return false;
        }
        for (ItemStack stack : stacks) {
            if (isGoblinFlame(stack)) {
                return true;
            }
        }
        return false;
    }

    private boolean isGoblinFlame(ItemStack stack) {
        if (stack == null || stack.getType() != Material.NETHER_STAR) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte flag = meta.getPersistentDataContainer().get(goblinFlameKey, PersistentDataType.BYTE);
        return flag != null && flag == 1;
    }

    private ItemStack createGoblinFlame() {
        ItemStack stack = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(goblinFlameDisplayName);
            meta.getPersistentDataContainer().set(goblinFlameKey, PersistentDataType.BYTE, (byte) 1);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void loadBuffs() {
        buffs.clear();
        List<String> entries = plugin.getConfig().getStringList("inherit.buffs");
        int index = 0;
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String[] split = entry.split(":");
            if (split.length < 3) {
                plugin.getLogger().warning("Invalid inherit buff format (attribute:operation:amount expected): " + entry);
                continue;
            }
            Attribute attribute = parseAttribute(split[0]);
            if (attribute == null) {
                continue;
            }
            AttributeOperation operation = parseOperation(split[1]);
            if (operation == null) {
                plugin.getLogger().warning("Unknown attribute operation in inherit.buffs: " + split[1]);
                continue;
            }
            double amount;
            try {
                amount = Double.parseDouble(split[2].trim());
            } catch (NumberFormatException ex) {
                plugin.getLogger().warning("Invalid amount for inherit buff " + entry);
                continue;
            }
            String attributeKey = attribute.getKey().getKey().replace('.', '_');
            String keyBase = "inherit_" + attributeKey + "_" + operation.name().toLowerCase(Locale.ROOT) + "_" + index;
            String sanitized = keyBase.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
            NamespacedKey modifierKey = new NamespacedKey(plugin, sanitized);
            buffs.add(new AttributeBuff(attribute, operation, amount, modifierKey));
            index++;
        }
    }

    public void handlePlayerQuit(Player player) {
        // No specific handling required; keep state for reapplication.
    }

    public UUID getInheritorId() {
        return inheritorId;
    }

    public String getInheritorName() {
        return inheritorName;
    }

    public boolean isInheritor(UUID uuid) {
        return uuid != null && uuid.equals(inheritorId);
    }

    public boolean toggleTransformation(Player player) {
        boolean activated;
        if (isTransformed(player)) {
            clearTransformation(player);
            activated = false;
        } else {
            activateTransformation(player);
            activated = true;
        }
        sendTransformationFeedback(player, activated);
        return activated;
    }

    private void sendTransformationFeedback(Player player, boolean activated) {
        String messageKey = activated ? "commands.goblin.activated" : "commands.goblin.deactivated";
        player.sendMessage(Component.text(messages.format(messageKey), NamedTextColor.GOLD));

        World world = player.getWorld();
        Location center = player.getLocation().clone().add(0, 1.0, 0);
        if (activated) {
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, center, 80, 0.4, 0.8, 0.4, 0.02);
            world.spawnParticle(Particle.LARGE_SMOKE, center, 20, 0.2, 0.6, 0.2, 0.0);
            world.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0F, 1.0F);
            world.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.7F, 0.8F);
        } else {
            world.spawnParticle(Particle.ASH, center, 50, 0.4, 0.6, 0.4, 0.01);
            world.spawnParticle(Particle.SMOKE, center, 30, 0.3, 0.5, 0.3, 0.01);
            world.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.8F, 1.2F);
            world.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.5F, 0.6F);
        }
    }

    private boolean isTransformed(Player player) {
        UUID uuid = player.getUniqueId();
        if (transformedPlayers.contains(uuid)) {
            return true;
        }
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        return pdc.has(transformationFlagKey, PersistentDataType.BYTE);
    }

    private void activateTransformation(Player player) {
        UUID uuid = player.getUniqueId();
        if (!transformedPlayers.add(uuid)) {
            return;
        }
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(transformationFlagKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(transformationGlowKey, PersistentDataType.BYTE, player.isGlowing() ? (byte) 1 : (byte) 0);
        applyTransformationAttributes(player);
        player.setGlowing(true);
    }

    private void clearTransformation(Player player) {
        UUID uuid = player.getUniqueId();
        transformedPlayers.remove(uuid);
        removeTransformationAttributes(player);
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        Byte previousGlow = pdc.get(transformationGlowKey, PersistentDataType.BYTE);
        boolean wasGlowing = previousGlow != null && previousGlow == 1;
        player.setGlowing(wasGlowing);
        pdc.remove(transformationFlagKey);
        pdc.remove(transformationGlowKey);
    }

    private void applyBuff(Player player, UUID uuid, AttributeBuff buff) {
        AttributeInstance instance = player.getAttribute(buff.attribute());
        if (instance == null) {
            plugin.getLogger().warning("Player " + player.getName() + " is missing attribute " + buff.attribute().getKey());
            return;
        }

        switch (buff.operation()) {
            case SET_BASE -> {
                Map<Attribute, Double> map = storedBaseValues.computeIfAbsent(uuid, key -> new ConcurrentHashMap<>());
                map.putIfAbsent(buff.attribute(), instance.getBaseValue());
                instance.setBaseValue(buff.amount());
                if (buff.attribute() == Attribute.MAX_HEALTH) {
                    double maxHealth = instance.getValue();
                    if (player.getHealth() > maxHealth) {
                        player.setHealth(maxHealth);
                    }
                }
            }
            case ADD_NUMBER, MULTIPLY_SCALAR_1, ADD_SCALAR -> {
                instance.removeModifier(buff.key());
                instance.addModifier(buff.createModifier());
            }
        }
    }

    private void removeBuff(Player player, UUID uuid, AttributeBuff buff) {
        AttributeInstance instance = player.getAttribute(buff.attribute());
        if (instance == null) {
            return;
        }
        switch (buff.operation()) {
            case SET_BASE -> {
                Map<Attribute, Double> map = storedBaseValues.get(uuid);
                Double previous = map != null ? map.remove(buff.attribute()) : null;
                if (map != null && map.isEmpty()) {
                    storedBaseValues.remove(uuid);
                }
                double target = previous != null ? previous : instance.getDefaultValue();
                instance.setBaseValue(target);
                if (buff.attribute() == Attribute.MAX_HEALTH) {
                    double maxHealth = instance.getValue();
                    if (player.getHealth() > maxHealth) {
                        player.setHealth(maxHealth);
                    }
                }
            }
            case ADD_NUMBER, MULTIPLY_SCALAR_1, ADD_SCALAR -> instance.removeModifier(buff.key());
        }
    }

    private void applyTransformationAttributes(Player player) {
        if (transformationScaleMultiplier > 1.0D) {
            AttributeInstance scale = player.getAttribute(Attribute.SCALE);
            if (scale != null) {
                scale.removeModifier(transformationScaleModifierKey);
                double modifierAmount = transformationScaleMultiplier - 1.0D;
                AttributeModifier modifier = new AttributeModifier(transformationScaleModifierKey, modifierAmount,
                        AttributeModifier.Operation.MULTIPLY_SCALAR_1);
                scale.addModifier(modifier);
            } else {
                plugin.getLogger().warning("Player " + player.getName() + " is missing attribute " + Attribute.SCALE.getKey());
            }
        }

        if (transformationAttackBonus != 0.0D) {
            AttributeInstance attack = player.getAttribute(Attribute.ATTACK_DAMAGE);
            if (attack != null) {
                attack.removeModifier(transformationAttackModifierKey);
                AttributeModifier modifier = new AttributeModifier(transformationAttackModifierKey, transformationAttackBonus,
                        AttributeModifier.Operation.ADD_NUMBER);
                attack.addModifier(modifier);
            } else {
                plugin.getLogger().warning("Player " + player.getName() + " is missing attribute " + Attribute.ATTACK_DAMAGE.getKey());
            }
        }
    }

    private void removeTransformationAttributes(Player player) {
        AttributeInstance scale = player.getAttribute(Attribute.SCALE);
        if (scale != null) {
            scale.removeModifier(transformationScaleModifierKey);
        }
        AttributeInstance attack = player.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attack != null) {
            attack.removeModifier(transformationAttackModifierKey);
        }
    }

    private boolean hasActiveTransformationAttributes(Player player) {
        AttributeInstance scale = player.getAttribute(Attribute.SCALE);
        boolean scaleActive = scale != null && scale.getModifier(transformationScaleModifierKey) != null;
        AttributeInstance attack = player.getAttribute(Attribute.ATTACK_DAMAGE);
        boolean attackActive = attack != null && attack.getModifier(transformationAttackModifierKey) != null;
        return scaleActive || attackActive;
    }

    private boolean hasAttributeBuff(Player player, AttributeBuff buff) {
        AttributeInstance instance = player.getAttribute(buff.attribute());
        if (instance == null) {
            return false;
        }
        return switch (buff.operation()) {
            case SET_BASE -> Math.abs(instance.getBaseValue() - buff.amount()) < ATTRIBUTE_EPSILON;
            case ADD_NUMBER, MULTIPLY_SCALAR_1, ADD_SCALAR -> instance.getModifier(buff.key()) != null;
        };
    }

    private Attribute parseAttribute(String token) {
        if (token == null) {
            return null;
        }
        String sanitized = token.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(':', '_')
                .replace('.', '_');
        if (sanitized.startsWith("MINECRAFT_")) {
            sanitized = sanitized.substring("MINECRAFT_".length());
        }

        try {
            return Attribute.valueOf(sanitized);
        } catch (IllegalArgumentException ignored) {
            if (sanitized.startsWith("GENERIC_")) {
                String withoutPrefix = sanitized.substring("GENERIC_".length());
                try {
                    return Attribute.valueOf(withoutPrefix);
                } catch (IllegalArgumentException ignoredAgain) {
                    // continue to final attempt
                }
            }
            try {
                return Attribute.valueOf("GENERIC_" + sanitized);
            } catch (IllegalArgumentException ignoredAgain) {
                plugin.getLogger().warning("Unknown attribute in inherit.buffs: " + token);
                return null;
            }
        }
    }

    private AttributeOperation parseOperation(String token) {
        if (token == null) {
            return null;
        }
        String normalized = token.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "set", "set_base", "base" -> AttributeOperation.SET_BASE;
            case "add", "add_number" -> AttributeOperation.ADD_NUMBER;
            case "multiply_base", "multiply_scalar_1", "multiply" -> AttributeOperation.MULTIPLY_SCALAR_1;
            case "multiply_total", "multiply_scalar_2", "multiply_total_scalar", "add_scalar" -> AttributeOperation.ADD_SCALAR;
            default -> null;
        };
    }

    private enum AttributeOperation {
        SET_BASE,
        ADD_NUMBER,
        MULTIPLY_SCALAR_1,
        ADD_SCALAR
    }

    private record AttributeBuff(Attribute attribute, AttributeOperation operation, double amount, NamespacedKey key) {
        AttributeModifier createModifier() {
            AttributeModifier.Operation op = switch (operation) {
                case ADD_NUMBER -> AttributeModifier.Operation.ADD_NUMBER;
                case MULTIPLY_SCALAR_1 -> AttributeModifier.Operation.MULTIPLY_SCALAR_1;
                case ADD_SCALAR -> AttributeModifier.Operation.ADD_SCALAR;
                case SET_BASE -> throw new IllegalStateException("SET_BASE operation does not use modifiers");
            };
            return new AttributeModifier(key, amount, op);
        }
    }
}
