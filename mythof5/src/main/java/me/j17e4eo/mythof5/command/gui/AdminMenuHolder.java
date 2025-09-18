package me.j17e4eo.mythof5.command.gui;

import me.j17e4eo.mythof5.inherit.aspect.GoblinAspect;
import me.j17e4eo.mythof5.relic.RelicType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Custom inventory holder carrying metadata about the admin GUI state. Bukkit
 * allows retrieving this holder during inventory events which lets us
 * reconstruct the context of the open menu.
 */
public final class AdminMenuHolder implements InventoryHolder {

    private final MenuType type;
    private final int page;
    private final AdminAction action;
    private final GoblinAspect aspect;
    private final RelicType relicType;
    private Inventory inventory;

    public AdminMenuHolder(MenuType type) {
        this(type, 0, AdminAction.NONE, null, null);
    }

    public AdminMenuHolder(MenuType type, int page) {
        this(type, page, AdminAction.NONE, null, null);
    }

    public AdminMenuHolder(MenuType type, int page, AdminAction action, GoblinAspect aspect, RelicType relicType) {
        this.type = type;
        this.page = page;
        this.action = action;
        this.aspect = aspect;
        this.relicType = relicType;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public MenuType getType() {
        return type;
    }

    public int getPage() {
        return page;
    }

    public AdminAction getAction() {
        return action;
    }

    public GoblinAspect getAspect() {
        return aspect;
    }

    public RelicType getRelicType() {
        return relicType;
    }
}
