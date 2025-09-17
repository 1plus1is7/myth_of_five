package me.j17e4eo.mythof5.command;

import me.j17e4eo.mythof5.Mythof5;
import me.j17e4eo.mythof5.boss.BossInstance;
import me.j17e4eo.mythof5.boss.BossManager;
import me.j17e4eo.mythof5.config.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class MythAdminCommand implements CommandExecutor, TabCompleter {

    private final Mythof5 plugin;
    private final BossManager bossManager;
    private final Messages messages;

    public MythAdminCommand(Mythof5 plugin, BossManager bossManager, Messages messages) {
        this.plugin = plugin;
        this.bossManager = bossManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }
        if (!args[0].equalsIgnoreCase("admin")) {
            sendUsage(sender, label);
            return true;
        }
        if (args.length < 2) {
            sendUsage(sender, label);
            return true;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "spawnboss":
                handleSpawnBoss(sender, label, Arrays.copyOfRange(args, 2, args.length));
                return true;
            case "bosslist":
                handleBossList(sender);
                return true;
            case "endboss":
                handleEndBoss(sender, label, Arrays.copyOfRange(args, 2, args.length));
                return true;
            default:
                sendUsage(sender, label);
                return true;
        }
    }

    private void handleSpawnBoss(CommandSender sender, String label, String[] args) {
        if (!hasPermission(sender, "myth.admin.spawnboss")) {
            sender.sendMessage(Component.text(messages.format("commands.common.no_permission"), NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            sendUsage(sender, label);
            return;
        }
        EntityType entityType;
        try {
            entityType = EntityType.valueOf(args[0].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(Component.text(messages.format("commands.myth.invalid_entity"), NamedTextColor.RED));
            return;
        }
        if (!entityType.isAlive()) {
            sender.sendMessage(Component.text(messages.format("commands.myth.entity_not_living"), NamedTextColor.RED));
            return;
        }
        String name = args[1];
        Double hp = null;
        Double armor = null;
        World world = null;
        Double x = null;
        Double y = null;
        Double z = null;
        int index = 2;
        try {
            if (args.length > index) {
                hp = Double.parseDouble(args[index++]);
                if (hp <= 0) {
                    sender.sendMessage(Component.text(messages.format("commands.myth.invalid_health"), NamedTextColor.RED));
                    return;
                }
            }
            if (args.length > index) {
                armor = Double.parseDouble(args[index++]);
            }
        } catch (NumberFormatException ex) {
            sender.sendMessage(Component.text(messages.format("commands.myth.invalid_numbers"), NamedTextColor.RED));
            return;
        }
        if (args.length > index) {
            world = Bukkit.getWorld(args[index++]);
            if (world == null) {
                sender.sendMessage(Component.text(messages.format("commands.myth.world_not_found"), NamedTextColor.RED));
                return;
            }
        }
        if (args.length > index) {
            if (args.length - index < 3) {
                sender.sendMessage(Component.text(messages.format("commands.myth.coords_not_three"), NamedTextColor.RED));
                return;
            }
            try {
                x = Double.parseDouble(args[index++]);
                y = Double.parseDouble(args[index++]);
                z = Double.parseDouble(args[index]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(Component.text(messages.format("commands.myth.invalid_coords"), NamedTextColor.RED));
                return;
            }
        }
        Location location;
        if (world != null && x != null && y != null && z != null) {
            location = new Location(world, x, y, z);
        } else if (world != null) {
            sender.sendMessage(Component.text(messages.format("commands.myth.must_specify_location"), NamedTextColor.RED));
            return;
        } else if (x != null || y != null || z != null) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text(messages.format("commands.myth.console_requires_location"), NamedTextColor.RED));
                return;
            }
            location = new Location(player.getWorld(), x != null ? x : player.getLocation().getX(), y != null ? y : player.getLocation().getY(), z != null ? z : player.getLocation().getZ());
        } else if (sender instanceof Player player) {
            location = player.getLocation();
        } else {
            sender.sendMessage(Component.text(messages.format("commands.myth.console_requires_location"), NamedTextColor.RED));
            return;
        }
        String resolvedName = name == null || name.isBlank() ? plugin.getConfig().getString("boss.name", "태초의 도깨비") : name;
        if (bossManager.hasActiveBossWithName(resolvedName)) {
            sender.sendMessage(Component.text(messages.format("commands.myth.duplicate_boss_name"), NamedTextColor.RED));
            return;
        }
        BossInstance instance = bossManager.spawnBoss(entityType, resolvedName, hp, armor, location);
        sender.sendMessage(Component.text(messages.format("commands.myth.spawn_success", java.util.Map.of(
                "id", String.valueOf(instance.getId()),
                "location", formatLocation(instance.getEntity().getLocation()),
                "type", entityType.name(),
                "name", instance.getDisplayName()
        )), NamedTextColor.GREEN));
    }

    private void handleBossList(CommandSender sender) {
        if (!hasPermission(sender, "myth.admin.bosslist")) {
            sender.sendMessage(Component.text(messages.format("commands.common.no_permission"), NamedTextColor.RED));
            return;
        }
        bossManager.sendBossList(sender);
    }

    private void handleEndBoss(CommandSender sender, String label, String[] args) {
        if (!hasPermission(sender, "myth.admin.endboss")) {
            sender.sendMessage(Component.text(messages.format("commands.common.no_permission"), NamedTextColor.RED));
            return;
        }
        if (args.length < 1) {
            sender.sendMessage(Component.text(messages.format("commands.myth.end_usage", java.util.Map.of("label", label)), NamedTextColor.RED));
            return;
        }
        int id;
        try {
            id = Integer.parseInt(args[0]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Component.text(messages.format("commands.myth.invalid_boss_id"), NamedTextColor.RED));
            return;
        }
        boolean result = bossManager.endBoss(id);
        if (result) {
            sender.sendMessage(Component.text(messages.format("commands.myth.end_success"), NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text(messages.format("commands.myth.end_not_found"), NamedTextColor.RED));
        }
    }

    private boolean hasPermission(CommandSender sender, String permission) {
        return sender.hasPermission("myth.admin.*") || sender.hasPermission(permission);
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(Component.text(messages.format("commands.common.usage_header"), NamedTextColor.GOLD));
        for (String line : messages.formatList("commands.myth.usage", java.util.Map.of("label", label))) {
            sender.sendMessage(Component.text("/" + line, NamedTextColor.GRAY));
        }
    }

    private String formatLocation(Location location) {
        return String.format("%s(%.1f, %.1f, %.1f)", location.getWorld() != null ? location.getWorld().getName() : "unknown", location.getX(), location.getY(), location.getZ());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Collections.singletonList("admin");
        }
        if (!args[0].equalsIgnoreCase("admin")) {
            return Collections.emptyList();
        }
        if (args.length == 2) {
            return Arrays.asList("spawnboss", "bosslist", "endboss");
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "spawnboss":
                if (args.length == 3) {
                    return Arrays.stream(EntityType.values())
                            .filter(EntityType::isAlive)
                            .map(type -> type.name().toLowerCase(Locale.ROOT))
                            .collect(Collectors.toList());
                }
                if (args.length == 4) {
                    return Collections.singletonList(plugin.getConfig().getString("boss.name", "도깨비"));
                }
                if (args.length == 5) {
                    return Collections.singletonList(String.valueOf(plugin.getConfig().getDouble("boss.hp_default", 10000D)));
                }
                if (args.length == 6) {
                    return Collections.singletonList(String.valueOf(plugin.getConfig().getDouble("boss.armor_default", 50D)));
                }
                if (args.length == 7) {
                    return Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList());
                }
                if (args.length >= 8 && args.length <= 10 && sender instanceof Player player) {
                    Location loc = player.getLocation();
                    if (args.length == 8) {
                        return Collections.singletonList(String.format(Locale.ROOT, "%.1f", loc.getX()));
                    }
                    if (args.length == 9) {
                        return Collections.singletonList(String.format(Locale.ROOT, "%.1f", loc.getY()));
                    }
                    if (args.length == 10) {
                        return Collections.singletonList(String.format(Locale.ROOT, "%.1f", loc.getZ()));
                    }
                }
                return Collections.emptyList();
            case "endboss":
                if (args.length == 3) {
                    List<String> ids = new ArrayList<>();
                    for (BossInstance instance : bossManager.getActiveBosses()) {
                        ids.add(String.valueOf(instance.getId()));
                    }
                    return ids;
                }
                return Collections.emptyList();
            default:
                return Collections.emptyList();
        }
    }
}
