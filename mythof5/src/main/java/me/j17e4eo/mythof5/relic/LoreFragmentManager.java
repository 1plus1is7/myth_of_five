package me.j17e4eo.mythof5.relic;

import me.j17e4eo.mythof5.Mythof5;
import me.j17e4eo.mythof5.config.Messages;
import me.j17e4eo.mythof5.meta.MetaEventManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Controls the lore fragment drop table and forging logic.
 */
public class LoreFragmentManager implements Listener {

    private final Mythof5 plugin;
    private final Messages messages;
    private final RelicManager relicManager;
    private MetaEventManager metaEventManager;
    private final Random random = new Random();
    private final NamespacedKey fragmentKey;
    private final NamespacedKey fragmentTypeKey;
    private final double dropChance;
    private final List<LoreFragmentRecipe> recipes = new ArrayList<>();

    public LoreFragmentManager(Mythof5 plugin, Messages messages, RelicManager relicManager) {
        this.plugin = plugin;
        this.messages = messages;
        this.relicManager = relicManager;
        this.fragmentKey = new NamespacedKey(plugin, "lore_fragment");
        this.fragmentTypeKey = new NamespacedKey(plugin, "lore_fragment_type");
        this.dropChance = Math.max(0.0005D, plugin.getConfig().getDouble("relic.fragments.drop_chance", 0.01D));
        registerRecipes();
    }

    public void setMetaEventManager(MetaEventManager metaEventManager) {
        this.metaEventManager = metaEventManager;
    }

    private void registerRecipes() {
        recipes.add(new LoreFragmentRecipe(EnumSet.of(LoreFragmentType.EMBER, LoreFragmentType.SHADOW),
                RelicType.TWILIGHT_CINDERS, "불씨와 그림자가 뒤섞인 이계의 칼날"));
        recipes.add(new LoreFragmentRecipe(EnumSet.of(LoreFragmentType.TIDE, LoreFragmentType.GALE),
                RelicType.STORMCALLER_DRUM, "조류와 바람을 다루는 폭풍 북"));
        recipes.add(new LoreFragmentRecipe(EnumSet.of(LoreFragmentType.STONE, LoreFragmentType.EMBER),
                RelicType.FORGE_HEART, "대장장이 도깨비의 숨결이 깃든 심장"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null || entity instanceof Player) {
            return;
        }
        double effectiveChance = dropChance;
        if (metaEventManager != null && metaEventManager.isLoreFractureActive()) {
            effectiveChance *= 3.0D;
        }
        if (effectiveChance > 1.0D) {
            effectiveChance = 1.0D;
        }
        if (random.nextDouble() > effectiveChance) {
            return;
        }
        LoreFragmentType type = LoreFragmentType.values()[random.nextInt(LoreFragmentType.values().length)];
        ItemStack fragment = createFragmentItem(type);
        entity.getWorld().dropItemNaturally(entity.getLocation().add(0, 0.5, 0), fragment).setGlowing(true);
        killer.sendMessage(messages.format("relic.fragment.drop", Map.of(
                "type", type.getDisplayName())));
    }

    public ItemStack createFragmentItem(LoreFragmentType type) {
        ItemStack stack = new ItemStack(Material.PRISMARINE_SHARD);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(type.getDisplayName(), type.getColor()));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(messages.format("relic.fragment.lore"), NamedTextColor.GRAY));
            meta.lore(lore);
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(fragmentKey, PersistentDataType.BYTE, (byte) 1);
            container.set(fragmentTypeKey, PersistentDataType.STRING, type.getKey());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public boolean isFragment(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return false;
        }
        ItemMeta meta = itemStack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(fragmentKey, PersistentDataType.BYTE);
    }

    public Optional<LoreFragmentType> getFragmentType(ItemStack stack) {
        if (!isFragment(stack)) {
            return Optional.empty();
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        String raw = meta.getPersistentDataContainer().get(fragmentTypeKey, PersistentDataType.STRING);
        return Optional.ofNullable(LoreFragmentType.fromKey(raw));
    }

    public boolean attemptForge(Player player) {
        Map<LoreFragmentType, Integer> counts = player.getInventory().getContents() == null ? Map.of() : countFragments(player);
        for (LoreFragmentRecipe recipe : recipes) {
            if (hasIngredients(counts, recipe.getIngredients())) {
                consumeFragments(player, recipe.getIngredients());
                relicManager.grantRelic(player, recipe.getResult(), true);
                broadcastForgeAnnouncement(player, recipe.getResult());
                player.sendMessage(messages.format("relic.fragment.forge_success", Map.of(
                        "relic", recipe.getResult().getDisplayName(),
                        "desc", recipe.getDescription()
                )));
                return true;
            }
        }
        player.sendMessage(messages.format("relic.fragment.forge_fail"));
        return false;
    }

    private Map<LoreFragmentType, Integer> countFragments(Player player) {
        Map<LoreFragmentType, Integer> counts = new EnumMap<>(LoreFragmentType.class);
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null) {
                continue;
            }
            getFragmentType(stack).ifPresent(type ->
                    counts.merge(type, stack.getAmount(), Integer::sum));
        }
        return counts;
    }

    private boolean hasIngredients(Map<LoreFragmentType, Integer> counts, Set<LoreFragmentType> required) {
        for (LoreFragmentType type : required) {
            if (counts.getOrDefault(type, 0) <= 0) {
                return false;
            }
        }
        return true;
    }

    private void consumeFragments(Player player, Set<LoreFragmentType> ingredients) {
        for (LoreFragmentType type : ingredients) {
            int remaining = 1;
            ItemStack[] contents = player.getInventory().getContents();
            for (int i = 0; i < contents.length && remaining > 0; i++) {
                ItemStack stack = contents[i];
                if (stack == null) {
                    continue;
                }
                if (getFragmentType(stack).orElse(null) == type) {
                    int take = Math.min(remaining, stack.getAmount());
                    stack.setAmount(stack.getAmount() - take);
                    if (stack.getAmount() <= 0) {
                        contents[i] = null;
                    }
                    remaining -= take;
                }
            }
            player.getInventory().setContents(contents);
        }
        player.updateInventory();
    }

    public void broadcastForgeAnnouncement(Player player, RelicType relic) {
        String message = messages.format("relic.fragment.broadcast", Map.of(
                "player", player.getName(),
                "relic", relic.getDisplayName()
        ));
        Bukkit.broadcast(Component.text(message, NamedTextColor.GOLD));
    }
}
