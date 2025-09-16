package me.j17e4eo.mythof5.listener;

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

    public SquadListener(SquadManager squadManager, boolean friendlyFireAllowed) {
        this.squadManager = squadManager;
        this.friendlyFireAllowed = friendlyFireAllowed;
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
        attacker.sendMessage(Component.text("부대원에게 피해를 줄 수 없습니다.", NamedTextColor.RED));
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
