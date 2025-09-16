package me.j17e4eo.mythof5.command;

import me.j17e4eo.mythof5.Mythof5;
import me.j17e4eo.mythof5.boss.BossInstance;
import me.j17e4eo.mythof5.boss.BossManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
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

    public MythAdminCommand(Mythof5 plugin, BossManager bossManager) {
        this.plugin = plugin;
        this.bossManager = bossManager;
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
                handleSpawnBoss(sender, Arrays.copyOfRange(args, 2, args.length));
                return true;
            case "bosslist":
                handleBossList(sender);
                return true;
            case "endboss":
                handleEndBoss(sender, Arrays.copyOfRange(args, 2, args.length));
                return true;
            default:
                sendUsage(sender, label);
                return true;
        }
    }

    private void handleSpawnBoss(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "myth.admin.spawnboss")) {
            sender.sendMessage(Component.text("권한이 없습니다.", NamedTextColor.RED));
            return;
        }
        if (args.length < 1) {
            sender.sendMessage(Component.text("사용법: /myth admin spawnboss <이름> [hp] [armor] [world] [x y z]", NamedTextColor.RED));
            return;
        }
        String typeName = args[0];
        Double hp = null;
        Double armor = null;
        World world = null;
        Double x = null;
        Double y = null;
        Double z = null;
        int index = 1;
        try {
            if (args.length > index) {
                hp = Double.parseDouble(args[index++]);
            }
            if (args.length > index) {
                armor = Double.parseDouble(args[index++]);
            }
        } catch (NumberFormatException ex) {
            sender.sendMessage(Component.text("hp 또는 armor 값이 올바르지 않습니다.", NamedTextColor.RED));
            return;
        }
        if (args.length > index) {
            world = Bukkit.getWorld(args[index++]);
            if (world == null) {
                sender.sendMessage(Component.text("해당 월드를 찾을 수 없습니다.", NamedTextColor.RED));
                return;
            }
        }
        if (args.length > index) {
            if (args.length - index < 3) {
                sender.sendMessage(Component.text("좌표는 x y z 형태로 입력해야 합니다.", NamedTextColor.RED));
                return;
            }
            try {
                x = Double.parseDouble(args[index++]);
                y = Double.parseDouble(args[index++]);
                z = Double.parseDouble(args[index]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(Component.text("좌표가 올바르지 않습니다.", NamedTextColor.RED));
                return;
            }
        }
        Location location;
        if (world != null && x != null && y != null && z != null) {
            location = new Location(world, x, y, z);
        } else if (world != null) {
            sender.sendMessage(Component.text("좌표 없이 월드만 지정할 수 없습니다.", NamedTextColor.RED));
            return;
        } else if (x != null || y != null || z != null) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("콘솔에서는 월드와 좌표를 모두 지정해야 합니다.", NamedTextColor.RED));
                return;
            }
            location = new Location(player.getWorld(), x != null ? x : player.getLocation().getX(), y != null ? y : player.getLocation().getY(), z != null ? z : player.getLocation().getZ());
        } else if (sender instanceof Player player) {
            location = player.getLocation();
        } else {
            sender.sendMessage(Component.text("월드와 좌표를 지정해야 합니다.", NamedTextColor.RED));
            return;
        }
        BossInstance instance = bossManager.spawnBoss(typeName, hp, armor, location);
        sender.sendMessage(Component.text(String.format("도깨비 보스 #%d 소환 완료 (%s)", instance.getId(), formatLocation(instance.getEntity().getLocation())), NamedTextColor.GREEN));
    }

    private void handleBossList(CommandSender sender) {
        if (!hasPermission(sender, "myth.admin.bosslist")) {
            sender.sendMessage(Component.text("권한이 없습니다.", NamedTextColor.RED));
            return;
        }
        bossManager.sendBossList(sender);
    }

    private void handleEndBoss(CommandSender sender, String[] args) {
        if (!hasPermission(sender, "myth.admin.endboss")) {
            sender.sendMessage(Component.text("권한이 없습니다.", NamedTextColor.RED));
            return;
        }
        if (args.length < 1) {
            sender.sendMessage(Component.text("사용법: /myth admin endboss <bossId>", NamedTextColor.RED));
            return;
        }
        int id;
        try {
            id = Integer.parseInt(args[0]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Component.text("bossId는 숫자여야 합니다.", NamedTextColor.RED));
            return;
        }
        boolean result = bossManager.endBoss(id);
        if (result) {
            sender.sendMessage(Component.text("보스를 종료했습니다.", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("해당 ID의 보스를 찾을 수 없습니다.", NamedTextColor.RED));
        }
    }

    private boolean hasPermission(CommandSender sender, String permission) {
        return sender.hasPermission("myth.admin.*") || sender.hasPermission(permission);
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(Component.text("사용법:", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/" + label + " admin spawnboss <이름> [hp] [armor] [world] [x y z]", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/" + label + " admin bosslist", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/" + label + " admin endboss <bossId>", NamedTextColor.GRAY));
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
                    return Collections.singletonList(plugin.getConfig().getString("boss.name", "도깨비"));
                }
                if (args.length == 6) {
                    return Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList());
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
