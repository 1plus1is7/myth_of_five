package me.j17e4eo.mythof5.listener;

import me.j17e4eo.mythof5.config.Messages;
import me.j17e4eo.mythof5.squad.SquadManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class SquadListener implements Listener {

    private final SquadManager squadManager;
    private final boolean friendlyFireAllowed;
    private final Messages messages;

    public SquadListener(SquadManager squadManager, boolean friendlyFireAllowed, Messages messages) {
        this.squadManager = squadManager;
        this.friendlyFireAllowed = friendlyFireAllowed;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFriendlyFire(EntityDamageByEntityEvent event) {
        if (friendlyFireAllowed) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null) {
            return;
        }
        if (!squadManager.isSameSquad(attacker.getUniqueId(), victim.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        attacker.sendMessage(Component.text(messages.format("squad.friendly_fire_blocked"), NamedTextColor.RED));
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
