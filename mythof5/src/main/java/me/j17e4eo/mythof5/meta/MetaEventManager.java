package me.j17e4eo.mythof5.meta;

import me.j17e4eo.mythof5.Mythof5;
import me.j17e4eo.mythof5.config.Messages;
import me.j17e4eo.mythof5.inherit.aspect.GoblinSkill;
import me.j17e4eo.mythof5.relic.LoreFragmentManager;
import me.j17e4eo.mythof5.relic.LoreFragmentType;
import me.j17e4eo.mythof5.relic.RelicType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Drives meta progression events based on repeated chronicle activity.
 */
public class MetaEventManager {

    private final Mythof5 plugin;
    private final Messages messages;
    private LoreFragmentManager loreFragmentManager;
    private final Deque<String> recentSkills = new ArrayDeque<>();
    private final Map<String, Deque<Long>> fusionHistory = new HashMap<>();
    private long balanceCollapseExpire;
    private long loreFractureExpire;

    public MetaEventManager(Mythof5 plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public void setLoreFragmentManager(LoreFragmentManager loreFragmentManager) {
        this.loreFragmentManager = loreFragmentManager;
    }

    public void recordSkillUse(Player player, GoblinSkill skill) {
        if (skill == null) {
            return;
        }
        recentSkills.addLast(skill.getKey());
        if (recentSkills.size() > 6) {
            recentSkills.removeFirst();
        }
        if (recentSkills.size() < 4) {
            return;
        }
        String key = skill.getKey();
        int matches = 0;
        Iterator<String> iterator = recentSkills.descendingIterator();
        while (iterator.hasNext()) {
            String entry = iterator.next();
            if (!entry.equalsIgnoreCase(key)) {
                break;
            }
            matches++;
            if (matches >= 4) {
                break;
            }
        }
        if (matches >= 4 && System.currentTimeMillis() > balanceCollapseExpire) {
            triggerBalanceCollapse(skill);
        }
    }

    public void recordRelicFusion(RelicType type) {
        if (type == null) {
            return;
        }
        Deque<Long> history = fusionHistory.computeIfAbsent(type.getKey(), key -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        history.addLast(now);
        while (!history.isEmpty() && now - history.peekFirst() > 600_000L) {
            history.removeFirst();
        }
        if (history.size() >= 2 && now > loreFractureExpire) {
            triggerLoreFracture(type);
            history.clear();
        }
    }

    public void recordRelicGain(RelicType type) {
        if (type == null) {
            return;
        }
        Deque<Long> history = fusionHistory.get(type.getKey());
        if (history != null) {
            history.clear();
        }
    }

    public boolean isLoreFractureActive() {
        return System.currentTimeMillis() < loreFractureExpire;
    }

    public boolean isBalanceCollapseActive() {
        return System.currentTimeMillis() < balanceCollapseExpire;
    }

    private void triggerBalanceCollapse(GoblinSkill skill) {
        balanceCollapseExpire = System.currentTimeMillis() + 240_000L;
        Bukkit.broadcast(Component.text(messages.format("events.balance_collapse.start", Map.of(
                "skill", skill.getDisplayName()
        )), NamedTextColor.DARK_RED));
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (entity instanceof Monster monster) {
                    monster.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 0, true, true, true));
                    monster.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 0, true, true, true));
                }
            }
        }
    }

    private void triggerLoreFracture(RelicType type) {
        loreFractureExpire = System.currentTimeMillis() + 300_000L;
        Bukkit.broadcast(Component.text(messages.format("events.lore_fracture.start", Map.of(
                "relic", type.getDisplayName()
        )), NamedTextColor.DARK_PURPLE));
        if (loreFragmentManager == null) {
            return;
        }
        LoreFragmentType[] fragments = LoreFragmentType.values();
        for (Player player : Bukkit.getOnlinePlayers()) {
            LoreFragmentType fragmentType = fragments[ThreadLocalRandom.current().nextInt(fragments.length)];
            ItemStack stack = loreFragmentManager.createFragmentItem(fragmentType);
            player.getWorld().dropItemNaturally(player.getLocation().add(0, 1, 0), stack).setGlowing(true);
        }
    }
}
