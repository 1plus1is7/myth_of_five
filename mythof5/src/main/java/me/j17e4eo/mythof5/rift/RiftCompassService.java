package me.j17e4eo.mythof5.rift;

import me.j17e4eo.mythof5.Mythof5;
import me.j17e4eo.mythof5.rift.config.RiftConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class RiftCompassService {
    private final Mythof5 plugin;
    private final RiftManager manager;
    private final NamespacedKey compassKey;
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();

    public RiftCompassService(Mythof5 plugin, RiftManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.compassKey = new NamespacedKey(plugin, "rift_compass");
    }

    public void clear() {
        for (BukkitTask task : tasks.values()) {
            task.cancel();
        }
        tasks.clear();
    }

    public void giveCompass(Player player, boolean showCoordinates) {
        giveCompass(player, showCoordinates, true);
    }

    public void giveCompass(Player player, boolean showCoordinates, boolean inform) {
        RiftSite target = manager.findNearestSite(player.getLocation());
        ItemStack compass = buildCompass(target);
        PlayerInventory inventory = player.getInventory();
        inventory.addItem(compass);
        if (target != null) {
            player.setCompassTarget(target.getLocation());
            if (inform) {
                player.sendMessage(describeSite(target, player.getLocation(), showCoordinates));
            }
        } else if (inform) {
            player.sendMessage(Component.text("탐지 가능한 균열이 없습니다.", NamedTextColor.RED));
        }
        startTracking(player.getUniqueId(), showCoordinates);
    }

    public Component describeSite(RiftSite site, Location viewer, boolean includeCoords) {
        if (site == null) {
            return Component.text("균열 정보를 찾을 수 없습니다.", NamedTextColor.RED);
        }
        return buildStatusComponent(site, viewer, includeCoords);
    }

    private ItemStack buildCompass(RiftSite target) {
        RiftConfig config = manager.getConfig();
        LegacyComponentSerializer serializer = LegacyComponentSerializer.legacySection();
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(serializer.deserialize(colorize(config.getCompassDisplayName())));
        List<String> lore = config.getCompassLore();
        if (lore != null && !lore.isEmpty()) {
            meta.lore(lore.stream()
                    .map(line -> line.replace("{seconds}", String.valueOf(config.getCompassUpdateSeconds())))
                    .map(this::colorize)
                    .map(serializer::deserialize)
                    .toList());
        }
        meta.getPersistentDataContainer().set(compassKey, PersistentDataType.BYTE, (byte) 1);
        if (meta instanceof CompassMeta compassMeta && target != null) {
            Location loc = target.getLocation();
            compassMeta.setLodestone(loc);
            compassMeta.setLodestoneTracked(false);
        }
        item.setItemMeta(meta);
        return item;
    }

    private void startTracking(UUID playerId, boolean showCoordinates) {
        tasks.computeIfPresent(playerId, (uuid, task) -> {
            task.cancel();
            return null;
        });
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                Player player = plugin.getServer().getPlayer(playerId);
                if (player == null || !player.isOnline()) {
                    cancel();
                    tasks.remove(playerId);
                    return;
                }
                RiftSite nearest = manager.findNearestSite(player.getLocation());
                if (nearest == null) {
                    return;
                }
                player.setCompassTarget(nearest.getLocation());
                updateHeldCompass(player, nearest);
                player.sendActionBar(buildActionBar(player, nearest, showCoordinates));
            }
        }.runTaskTimer(plugin, 0L, manager.getConfig().getCompassUpdateSeconds() * 20L);
        tasks.put(playerId, task);
    }

    private void updateHeldCompass(Player player, RiftSite target) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isRiftCompass(item)) {
            item = player.getInventory().getItemInOffHand();
        }
        if (!isRiftCompass(item)) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof CompassMeta compassMeta) {
            Location loc = target.getLocation();
            compassMeta.setLodestone(loc);
            compassMeta.setLodestoneTracked(false);
            item.setItemMeta(compassMeta);
        }
    }

    private boolean isRiftCompass(ItemStack stack) {
        if (stack == null || stack.getType() != Material.COMPASS) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(compassKey, PersistentDataType.BYTE);
    }

    private String colorize(String input) {
        if (input == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    private Component buildStatusComponent(RiftSite site, Location viewer, boolean includeCoords) {
        Location loc = site.getLocation();
        double distance = 0.0D;
        if (viewer != null && viewer.getWorld() != null && viewer.getWorld().equals(loc.getWorld())) {
            distance = viewer.distance(loc);
        }
        long now = System.currentTimeMillis();
        NamedTextColor stateColor;
        String stateText;
        Optional<RiftInstance> active = site.getActiveInstance();
        if (active.isPresent()) {
            stateColor = NamedTextColor.LIGHT_PURPLE;
            stateText = translateState(active.get().getState());
        } else if (site.isCoolingDown(now)) {
            stateColor = NamedTextColor.GRAY;
            stateText = "쿨다운 " + site.getCooldownRemaining(now) / 1000L + "s";
        } else {
            stateColor = NamedTextColor.GREEN;
            stateText = "대기중";
        }
        TextComponent.Builder builder = Component.text();
        builder.append(Component.text("균열 ", NamedTextColor.AQUA));
        builder.append(Component.text(site.getId(), NamedTextColor.GOLD));
        builder.append(Component.text(" [", NamedTextColor.DARK_GRAY));
        builder.append(Component.text(site.getTheme().getDisplayName(), NamedTextColor.LIGHT_PURPLE));
        builder.append(Component.text("]", NamedTextColor.DARK_GRAY));
        builder.append(Component.text(String.format(Locale.KOREAN, " • %.0fm", distance), NamedTextColor.GRAY));
        builder.append(Component.text(" • ", NamedTextColor.DARK_GRAY));
        builder.append(Component.text(stateText, stateColor));
        if (includeCoords) {
            builder.append(Component.text(String.format(Locale.KOREAN, " • (%.0f, %.0f, %.0f)", loc.getX(), loc.getY(), loc.getZ()), NamedTextColor.GRAY));
        }
        return builder.build();
    }

    private Component buildActionBar(Player player, RiftSite site, boolean includeCoords) {
        Location viewer = player.getLocation();
        Location loc = site.getLocation();
        double distance = 0.0D;
        if (viewer.getWorld() != null && viewer.getWorld().equals(loc.getWorld())) {
            distance = viewer.distance(loc);
        }
        long now = System.currentTimeMillis();
        NamedTextColor stateColor;
        String stateText;
        Optional<RiftInstance> active = site.getActiveInstance();
        if (active.isPresent()) {
            stateColor = NamedTextColor.LIGHT_PURPLE;
            stateText = translateState(active.get().getState());
        } else if (site.isCoolingDown(now)) {
            stateColor = NamedTextColor.GRAY;
            stateText = "쿨다운 " + site.getCooldownRemaining(now) / 1000L + "s";
        } else {
            stateColor = NamedTextColor.GREEN;
            stateText = "대기중";
        }
        TextComponent.Builder builder = Component.text();
        builder.append(Component.text(site.getId(), NamedTextColor.GOLD));
        builder.append(Component.text(String.format(Locale.KOREAN, " • %.0fm", distance), NamedTextColor.GRAY));
        builder.append(Component.text(" • ", NamedTextColor.DARK_GRAY));
        builder.append(Component.text(stateText, stateColor));
        if (includeCoords) {
            builder.append(Component.text(String.format(Locale.KOREAN, " • (%.0f, %.0f, %.0f)", loc.getX(), loc.getY(), loc.getZ()), NamedTextColor.GRAY));
        }
        return builder.build();
    }

    private String translateState(RiftInstanceState state) {
        return switch (state) {
            case AWAKENING -> "각성";
            case PHASE_ONE -> "위상 1";
            case PHASE_TWO -> "위상 2";
            case BOSS -> "보스";
            case COLLAPSE -> "붕괴";
            case COOLDOWN -> "재정비";
            default -> "휴면";
        };
    }
}
