package me.j17e4eo.mythof5.relic;

import org.bukkit.entity.Player;

/**
 * Behaviour invoked when a relic is granted or removed.
 */
public interface RelicAbility {
    void onGrant(Player player);

    void onRemove(Player player);
}
