package me.j17e4eo.mythof5.inherit;

import me.j17e4eo.mythof5.Mythof5;
import me.j17e4eo.mythof5.config.Messages;
import me.j17e4eo.mythof5.inherit.aspect.GoblinAspect;
import me.j17e4eo.mythof5.inherit.aspect.GoblinSkill;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Handles the distribution of goblin skill weapons and listens for the
 * interaction shortcuts bound to each aspect weapon.
 */
public class GoblinWeaponManager implements Listener {

    private final Mythof5 plugin;
    private final Messages messages;
    private final AspectManager aspectManager;
    private final NamespacedKey weaponAspectKey;
    private final NamespacedKey weaponRoleKey;
    private final Map<GoblinAspect, GoblinWeaponDefinition> definitions = new EnumMap<>(GoblinAspect.class);

    public GoblinWeaponManager(Mythof5 plugin, Messages messages, AspectManager aspectManager) {
        this.plugin = plugin;
        this.messages = messages;
        this.aspectManager = aspectManager;
        this.weaponAspectKey = new NamespacedKey(plugin, "goblin_weapon_aspect");
        this.weaponRoleKey = new NamespacedKey(plugin, "goblin_weapon_role");
        initializeDefinitions();
    }

    public void grantWeapon(GoblinAspect aspect, Player player, boolean inheritor) {
        GoblinWeaponDefinition definition = definitions.get(aspect);
        if (definition == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack existing = findWeapon(inventory, aspect);
        ItemStack template = createWeapon(definition, inheritor);
        if (existing != null) {
            ItemMeta meta = template.getItemMeta();
            if (meta != null) {
                existing.setItemMeta(meta);
                existing.setAmount(1);
            }
            return;
        }
        Map<Integer, ItemStack> overflow = inventory.addItem(template);
        if (!overflow.isEmpty()) {
            for (ItemStack stack : overflow.values()) {
                Item drop = player.getWorld().dropItem(player.getLocation(), stack);
                drop.setOwner(player.getUniqueId());
                drop.setPickupDelay(0);
            }
        }
    }

    public void revokeWeapon(GoblinAspect aspect, Player player) {
        PlayerInventory inventory = player.getInventory();
        boolean changed = false;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (matchesAspect(inventory.getItem(slot), aspect)) {
                inventory.setItem(slot, null);
                changed = true;
            }
        }
        if (matchesAspect(inventory.getItemInOffHand(), aspect)) {
            inventory.setItemInOffHand(null);
            changed = true;
        }
        if (changed) {
            player.updateInventory();
        }
    }

    public void handleJoin(Player player) {
        sanitizeInventory(player);
        UUID uuid = player.getUniqueId();
        Set<GoblinAspect> owned = aspectManager.getAspects(uuid);
        for (GoblinAspect aspect : owned) {
            boolean inheritor = aspectManager.isInheritor(aspect, uuid);
            grantWeapon(aspect, player, inheritor);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        WeaponAction action = WeaponAction.fromInteract(event.getAction());
        if (action == null) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack stack = player.getInventory().getItemInMainHand();
        GoblinAspect aspect = getAspect(stack);
        if (aspect == null) {
            return;
        }
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) {
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        }
        event.setCancelled(true);
        handleSkill(player, aspect, action);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack stack = player.getInventory().getItemInMainHand();
        GoblinAspect aspect = getAspect(stack);
        if (aspect == null) {
            return;
        }
        event.setCancelled(true);
        handleSkill(player, aspect, WeaponAction.RIGHT_CLICK);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        ItemStack stack = player.getInventory().getItemInMainHand();
        GoblinAspect aspect = getAspect(stack);
        if (aspect == null) {
            return;
        }
        event.setCancelled(true);
        handleSkill(player, aspect, WeaponAction.LEFT_CLICK);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        GoblinAspect aspect = getAspect(event.getItemDrop().getItemStack());
        if (aspect == null) {
            return;
        }
        event.setCancelled(true);
        event.getItemDrop().remove();
        plugin.getServer().getScheduler().runTask(plugin, event.getPlayer()::updateInventory);
        handleSkill(event.getPlayer(), aspect, WeaponAction.DROP);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack main = event.getMainHandItem();
        ItemStack off = event.getOffHandItem();
        GoblinAspect aspect = getAspect(main);
        if (aspect == null) {
            aspect = getAspect(off);
            if (aspect == null) {
                return;
            }
        }
        event.setCancelled(true);
        plugin.getServer().getScheduler().runTask(plugin, player::updateInventory);
        handleSkill(player, aspect, WeaponAction.SWAP_HANDS);
    }

    private void handleSkill(Player player, GoblinAspect aspect, WeaponAction action) {
        UUID uuid = player.getUniqueId();
        if (!aspectManager.isInheritor(aspect, uuid) && !aspectManager.isShared(aspect, uuid)) {
            player.sendMessage(messages.format("goblin.weapon.no_power"));
            revokeWeapon(aspect, player);
            return;
        }
        GoblinWeaponDefinition definition = definitions.get(aspect);
        if (definition == null) {
            player.sendMessage(messages.format("goblin.skill.unassigned"));
            return;
        }
        String key = definition.getSkillKey(action);
        if (key == null) {
            player.sendMessage(messages.format("goblin.skill.unassigned"));
            return;
        }
        aspectManager.useSkill(aspect, player, key);
    }

    private void sanitizeInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        UUID uuid = player.getUniqueId();
        Set<GoblinAspect> owned = aspectManager.getAspects(uuid);
        EnumSet<GoblinAspect> seen = EnumSet.noneOf(GoblinAspect.class);
        boolean changed = false;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            GoblinAspect aspect = getAspect(stack);
            if (aspect == null) {
                continue;
            }
            if (!owned.contains(aspect) || seen.contains(aspect)) {
                inventory.setItem(slot, null);
                changed = true;
                continue;
            }
            seen.add(aspect);
            updateRole(stack, aspect, uuid);
        }
        ItemStack offHand = inventory.getItemInOffHand();
        GoblinAspect offAspect = getAspect(offHand);
        if (offAspect != null) {
            if (!owned.contains(offAspect) || seen.contains(offAspect)) {
                inventory.setItemInOffHand(null);
                changed = true;
            } else {
                updateRole(offHand, offAspect, uuid);
                seen.add(offAspect);
            }
        }
        if (changed) {
            player.updateInventory();
        }
    }

    private void updateRole(ItemStack stack, GoblinAspect aspect, UUID uuid) {
        if (stack == null) {
            return;
        }
        GoblinWeaponDefinition definition = definitions.get(aspect);
        if (definition == null) {
            return;
        }
        boolean inheritor = aspectManager.isInheritor(aspect, uuid);
        ItemStack refreshed = createWeapon(definition, inheritor);
        ItemMeta meta = refreshed.getItemMeta();
        if (meta != null) {
            stack.setItemMeta(meta);
            stack.setAmount(1);
        }
    }

    private ItemStack createWeapon(GoblinWeaponDefinition definition, boolean inheritor) {
        ItemStack stack = new ItemStack(definition.material());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(definition.aspect().getDisplayName() + "의 무기", NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("보유자: " + (inheritor ? "계승자" : "공유"), NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("입력별 스킬", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            for (WeaponSkillInfo info : definition.skillInfos()) {
                String actionLabel = info.actions().isEmpty()
                        ? "상시 발동"
                        : buildActionLabel(info.actions());
                NamedTextColor color = switch (info.skill().getCategory()) {
                    case PASSIVE -> NamedTextColor.GREEN;
                    case UTILITY -> NamedTextColor.AQUA;
                    default -> NamedTextColor.GOLD;
                };
                lore.add(Component.text(actionLabel + " - " + info.skill().getKey() + " (" + info.skill().getDisplayName() + ")",
                                color)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("  " + info.skill().getDescription(), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(weaponAspectKey, PersistentDataType.STRING, definition.aspect().getKey());
            container.set(weaponRoleKey, PersistentDataType.BYTE, inheritor ? (byte) 1 : (byte) 0);
            stack.setItemMeta(meta);
        }
        stack.setAmount(1);
        return stack;
    }

    private String buildActionLabel(EnumSet<WeaponAction> actions) {
        List<String> parts = new ArrayList<>(actions.size());
        for (WeaponAction action : actions) {
            parts.add(action.getDisplay());
        }
        return String.join("/", parts);
    }

    private ItemStack findWeapon(PlayerInventory inventory, GoblinAspect aspect) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (matchesAspect(stack, aspect)) {
                return stack;
            }
        }
        ItemStack offHand = inventory.getItemInOffHand();
        if (matchesAspect(offHand, aspect)) {
            return offHand;
        }
        return null;
    }

    private boolean matchesAspect(ItemStack stack, GoblinAspect aspect) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        return Objects.equals(aspect, getAspect(stack));
    }

    private GoblinAspect getAspect(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        String key = container.get(weaponAspectKey, PersistentDataType.STRING);
        if (key == null) {
            return null;
        }
        return GoblinAspect.fromKey(key);
    }

    private void initializeDefinitions() {
        definitions.clear();
        definitions.put(GoblinAspect.POWER, buildPowerDefinition());
        definitions.put(GoblinAspect.SPEED, buildSpeedDefinition());
        definitions.put(GoblinAspect.MISCHIEF, buildMischiefDefinition());
        definitions.put(GoblinAspect.FLAME, buildFlameDefinition());
        definitions.put(GoblinAspect.FORGE, buildForgeDefinition());
    }

    private GoblinWeaponDefinition buildPowerDefinition() {
        GoblinWeaponDefinition definition = new GoblinWeaponDefinition(GoblinAspect.POWER, Material.NETHERITE_AXE);
        definition.bind(requireSkill(GoblinAspect.POWER, "rush_strike"), WeaponAction.RIGHT_CLICK,
                WeaponAction.LEFT_CLICK, WeaponAction.DROP);
        definition.bind(requireSkill(GoblinAspect.POWER, "stagger_guard"));
        return definition;
    }

    private GoblinWeaponDefinition buildSpeedDefinition() {
        GoblinWeaponDefinition definition = new GoblinWeaponDefinition(GoblinAspect.SPEED, Material.TRIDENT);
        definition.bind(requireSkill(GoblinAspect.SPEED, "pursuit_mark"), WeaponAction.RIGHT_CLICK,
                WeaponAction.LEFT_CLICK, WeaponAction.DROP);
        definition.bind(requireSkill(GoblinAspect.SPEED, "scent_reader"));
        return definition;
    }

    private GoblinWeaponDefinition buildMischiefDefinition() {
        GoblinWeaponDefinition definition = new GoblinWeaponDefinition(GoblinAspect.MISCHIEF, Material.ENDER_EYE);
        definition.bind(requireSkill(GoblinAspect.MISCHIEF, "vision_twist"), WeaponAction.RIGHT_CLICK, WeaponAction.DROP);
        definition.bind(requireSkill(GoblinAspect.MISCHIEF, "veil_break"), WeaponAction.LEFT_CLICK, WeaponAction.SWAP_HANDS);
        return definition;
    }

    private GoblinWeaponDefinition buildFlameDefinition() {
        GoblinWeaponDefinition definition = new GoblinWeaponDefinition(GoblinAspect.FLAME, Material.BLAZE_ROD);
        definition.bind(requireSkill(GoblinAspect.FLAME, "ember_boost"), WeaponAction.RIGHT_CLICK, WeaponAction.DROP);
        definition.bind(requireSkill(GoblinAspect.FLAME, "ember_recovery"), WeaponAction.LEFT_CLICK, WeaponAction.SWAP_HANDS);
        return definition;
    }

    private GoblinWeaponDefinition buildForgeDefinition() {
        GoblinWeaponDefinition definition = new GoblinWeaponDefinition(GoblinAspect.FORGE, Material.NETHERITE_INGOT);
        definition.bind(requireSkill(GoblinAspect.FORGE, "weapon_overdrive"), WeaponAction.RIGHT_CLICK, WeaponAction.DROP);
        definition.bind(requireSkill(GoblinAspect.FORGE, "legendary_summon"), WeaponAction.LEFT_CLICK, WeaponAction.SWAP_HANDS);
        return definition;
    }

    private GoblinSkill requireSkill(GoblinAspect aspect, String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        for (GoblinSkill skill : aspect.getSkills()) {
            if (skill.getKey().equals(normalized)) {
                return skill;
            }
        }
        throw new IllegalStateException("Missing goblin skill " + key + " for aspect " + aspect.getKey());
    }

    private enum WeaponAction {
        RIGHT_CLICK("우클릭"),
        LEFT_CLICK("좌클릭"),
        DROP("버리기"),
        SWAP_HANDS("양손 교체");

        private final String display;

        WeaponAction(String display) {
            this.display = display;
        }

        public String getDisplay() {
            return display;
        }

        public static WeaponAction fromInteract(Action action) {
            return switch (action) {
                case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> RIGHT_CLICK;
                case LEFT_CLICK_AIR, LEFT_CLICK_BLOCK -> LEFT_CLICK;
                default -> null;
            };
        }
    }

    private static final class GoblinWeaponDefinition {
        private final GoblinAspect aspect;
        private final Material material;
        private final Map<WeaponAction, String> actionToKey = new EnumMap<>(WeaponAction.class);
        private final Map<String, WeaponSkillInfo> skills = new LinkedHashMap<>();

        private GoblinWeaponDefinition(GoblinAspect aspect, Material material) {
            this.aspect = aspect;
            this.material = material;
        }

        public GoblinAspect aspect() {
            return aspect;
        }

        public Material material() {
            return material;
        }

        public String getSkillKey(WeaponAction action) {
            return actionToKey.get(action);
        }

        public Collection<WeaponSkillInfo> skillInfos() {
            return skills.values();
        }

        public void bind(GoblinSkill skill, WeaponAction... actions) {
            WeaponSkillInfo info = skills.computeIfAbsent(skill.getKey(), key -> new WeaponSkillInfo(skill));
            info.actions().addAll(Arrays.asList(actions));
            for (WeaponAction action : actions) {
                actionToKey.put(action, skill.getKey());
            }
        }
    }

    private static final class WeaponSkillInfo {
        private final GoblinSkill skill;
        private final EnumSet<WeaponAction> actions = EnumSet.noneOf(WeaponAction.class);

        private WeaponSkillInfo(GoblinSkill skill) {
            this.skill = skill;
        }

        public GoblinSkill skill() {
            return skill;
        }

        public EnumSet<WeaponAction> actions() {
            return actions;
        }
    }
}
