package me.j17e4eo.mythof5.rift;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class RiftCommand implements CommandExecutor, TabCompleter {
    private final RiftManager manager;

    public RiftCommand(RiftManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("/rift locate|compass|debug|force|register", NamedTextColor.YELLOW));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "locate" -> handleLocate(sender, args);
            case "compass" -> handleCompass(sender);
            case "force" -> handleForce(sender, args);
            case "debug" -> handleDebug(sender, args);
            case "register" -> handleRegister(sender, args);
            default -> {
                sender.sendMessage(Component.text("알 수 없는 하위 명령입니다.", NamedTextColor.RED));
                yield true;
            }
        };
    }

    private boolean handleLocate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("이 명령어는 플레이어만 사용할 수 있습니다.", NamedTextColor.RED));
            return true;
        }
        boolean availableOnly = args.length > 1 && args[1].equalsIgnoreCase("available");
        RiftSite site = manager.findNearestSite(player.getLocation(), availableOnly);
        if (site == null) {
            sender.sendMessage(Component.text("근처에서 균열을 찾지 못했습니다.", NamedTextColor.RED));
            return true;
        }
        boolean showCoords = sender.hasPermission("mythof5.rift.admin");
        sender.sendMessage(manager.getCompassService().describeSite(site, player.getLocation(), showCoords));
        manager.getCompassService().giveCompass(player, showCoords, false);
        return true;
    }

    private boolean handleCompass(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("이 명령어는 플레이어만 사용할 수 있습니다.", NamedTextColor.RED));
            return true;
        }
        boolean showCoords = sender.hasPermission("mythof5.rift.admin");
        RiftSite site = manager.findNearestSite(player.getLocation());
        if (site == null) {
            sender.sendMessage(Component.text("탐지 가능한 균열이 없습니다.", NamedTextColor.RED));
            return true;
        }
        sender.sendMessage(manager.getCompassService().describeSite(site, player.getLocation(), showCoords));
        manager.getCompassService().giveCompass(player, showCoords, false);
        return true;
    }

    private boolean handleForce(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("이 명령어는 플레이어만 사용할 수 있습니다.", NamedTextColor.RED));
            return true;
        }
        if (!sender.hasPermission("mythof5.rift.force")) {
            sender.sendMessage(Component.text("권한이 없습니다.", NamedTextColor.RED));
            return true;
        }
        RiftSite site;
        if (args.length > 1) {
            site = manager.getSite(args[1]);
            if (site == null) {
                sender.sendMessage(Component.text("알 수 없는 균열 ID 입니다.", NamedTextColor.RED));
                return true;
            }
        } else {
            site = manager.findNearestSite(player.getLocation());
            if (site == null) {
                sender.sendMessage(Component.text("근처에 균열이 없습니다.", NamedTextColor.RED));
                return true;
            }
        }
        if (manager.startRift(site, player, true)) {
            sender.sendMessage(Component.text("강제로 균열을 각성시켰습니다.", NamedTextColor.GREEN));
        }
        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        RiftSite site;
        if (args.length > 1) {
            site = manager.getSite(args[1]);
        } else if (sender instanceof Player player) {
            site = manager.findSiteByLocation(player.getLocation());
        } else {
            sender.sendMessage(Component.text("콘솔 사용 시에는 ID를 지정해야 합니다.", NamedTextColor.RED));
            return true;
        }
        manager.showDebugInfo(sender, site);
        return true;
    }

    private boolean handleRegister(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("이 명령어는 플레이어만 사용할 수 있습니다.", NamedTextColor.RED));
            return true;
        }
        if (!sender.hasPermission("mythof5.rift.admin")) {
            sender.sendMessage(Component.text("권한이 없습니다.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("/rift register <테마>", NamedTextColor.RED));
            return true;
        }
        try {
            RiftSite site = manager.registerSite(player.getLocation(), args[1]);
            sender.sendMessage(Component.text("균열이 등록되었습니다: " + site.getId(), NamedTextColor.GREEN));
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(Component.text(ex.getMessage(), NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("locate");
            options.add("compass");
            if (sender.hasPermission("mythof5.rift.force")) {
                options.add("force");
            }
            if (sender.hasPermission("mythof5.rift.admin")) {
                options.add("register");
                options.add("debug");
            }
            return options.stream().filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("locate")) {
                return List.of("available");
            }
            if (args[0].equalsIgnoreCase("force") || args[0].equalsIgnoreCase("debug")) {
                return new ArrayList<>(manager.getSites().keySet());
            }
            if (args[0].equalsIgnoreCase("register")) {
                return new ArrayList<>(manager.getConfigLoader().getThemes().keySet());
            }
        }
        return List.of();
    }
}
