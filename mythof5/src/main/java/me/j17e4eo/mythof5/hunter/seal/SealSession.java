package me.j17e4eo.mythof5.hunter.seal;

import me.j17e4eo.mythof5.config.Messages;
import me.j17e4eo.mythof5.hunter.seal.data.GoblinFlame;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a single seal GUI interaction session.
 */
class SealSession implements InventoryHolder {

    private final UUID playerId;
    private final Inventory inventory;
    private final int inputSlot;
    private final int weaponSlot;
    private final int resultSlot;
    private final int sealSlot;
    private final int unsealSlot;
    private final int upgradeSlot;
    private final int cancelSlot;
    private final int helpSlot;
    private final boolean allowUnseal;
    private final boolean allowUpgrade;
    private final Messages messages;
    private GoblinFlame flame;
    private ItemStack flameStack;

    SealSession(org.bukkit.entity.Player player, int rows, Messages messages, boolean allowUnseal, boolean allowUpgrade) {
        this.playerId = player.getUniqueId();
        this.allowUnseal = allowUnseal;
        this.allowUpgrade = allowUpgrade;
        this.messages = messages;
        int size = Math.max(27, Math.min(36, rows * 9));
        Component title = Component.text(messages.format("seal.gui.title"), NamedTextColor.GOLD);
        this.inventory = Bukkit.createInventory(this, size, title);
        this.inputSlot = 10;
        this.weaponSlot = 13;
        this.resultSlot = 16;
        int base = (size / 9 - 1) * 9;
        this.sealSlot = Math.min(size - 1, base + 1);
        this.unsealSlot = Math.min(size - 1, base + 3);
        this.upgradeSlot = Math.min(size - 1, base + 5);
        this.cancelSlot = Math.min(size - 1, base + 7);
        this.helpSlot = Math.min(size - 1, base + 8);
        fillLayout();
    }

    private void fillLayout() {
        ItemStack filler = createButton(Material.GRAY_STAINED_GLASS_PANE, " ");
        Set<Integer> skip = new HashSet<>();
        skip.add(inputSlot);
        skip.add(weaponSlot);
        skip.add(resultSlot);
        skip.add(sealSlot);
        skip.add(unsealSlot);
        skip.add(upgradeSlot);
        skip.add(cancelSlot);
        skip.add(helpSlot);
        for (int i = 0; i < inventory.getSize(); i++) {
            if (!skip.contains(i)) {
                inventory.setItem(i, filler);
            }
        }
        inventory.setItem(sealSlot, createButton(Material.EMERALD, messages.format("seal.gui.button.seal")));
        Material unsealMaterial = allowUnseal ? Material.ANVIL : Material.BARRIER;
        String unsealLabel = allowUnseal ? messages.format("seal.gui.button.unseal") : messages.format("seal.gui.button.unseal_disabled");
        inventory.setItem(unsealSlot, createButton(unsealMaterial, unsealLabel));
        Material upgradeMaterial = allowUpgrade ? Material.NETHERITE_SCRAP : Material.BARRIER;
        String upgradeLabel = allowUpgrade ? messages.format("seal.gui.button.upgrade") : messages.format("seal.gui.button.upgrade_disabled");
        inventory.setItem(upgradeSlot, createButton(upgradeMaterial, upgradeLabel));
        inventory.setItem(cancelSlot, createButton(Material.REDSTONE, messages.format("seal.gui.button.cancel")));
        inventory.setItem(helpSlot, createButton(Material.BOOK, messages.format("seal.gui.button.help")));
    }

    private ItemStack createButton(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.GOLD));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public int getInputSlot() {
        return inputSlot;
    }

    public int getSealSlot() {
        return sealSlot;
    }

    public int getUnsealSlot() {
        return unsealSlot;
    }

    public int getUpgradeSlot() {
        return upgradeSlot;
    }

    public int getCancelSlot() {
        return cancelSlot;
    }

    public int getHelpSlot() {
        return helpSlot;
    }

    public GoblinFlame getFlame() {
        return flame;
    }

    public void setFlame(ItemStack stack, GoblinFlame flame) {
        this.flame = flame;
        this.flameStack = stack;
        inventory.setItem(inputSlot, stack);
        updatePreviewItem();
    }

    public ItemStack takeFlame() {
        ItemStack item = flameStack;
        this.flameStack = null;
        this.flame = null;
        inventory.setItem(inputSlot, null);
        updatePreviewItem();
        return item;
    }

    public void clearFlameSlot() {
        this.flame = null;
        this.flameStack = null;
        inventory.setItem(inputSlot, null);
        updatePreviewItem();
    }

    public void updateWeaponPreview(ItemStack weapon) {
        ItemStack preview = (weapon == null || weapon.getType() == Material.AIR) ? createButton(Material.BARRIER, messages.format("seal.gui.no_weapon")) : weapon.clone();
        inventory.setItem(weaponSlot, preview);
        updatePreviewItem();
    }

    private void updatePreviewItem() {
        if (flame != null) {
            ItemStack preview = new ItemStack(Material.ENCHANTED_BOOK);
            ItemMeta meta = preview.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text(messages.format("seal.gui.preview.ready", Map.of(
                        "aspect", flame.aspect().getDisplayName(),
                        "tier", String.valueOf(flame.tier())
                )), NamedTextColor.GOLD));
                meta.lore(java.util.List.of(Component.text(messages.format("seal.gui.preview.detail"), NamedTextColor.GRAY)));
                preview.setItemMeta(meta);
            }
            inventory.setItem(resultSlot, preview);
        } else {
            inventory.setItem(resultSlot, createButton(Material.PAPER, messages.format("seal.gui.preview.wait")));
        }
    }
}
