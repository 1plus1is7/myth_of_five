package me.j17e4eo.mythof5.command;

import me.j17e4eo.mythof5.config.Messages;
import me.j17e4eo.mythof5.squad.SquadManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class SquadCommand implements CommandExecutor, TabCompleter {

    private final SquadManager squadManager;
    private final Messages messages;

    public SquadCommand(SquadManager squadManager, Messages messages) {
        this.squadManager = squadManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(messages.format("commands.common.player_only"), NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            sendUsage(player, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create":
                handleCreate(player, label, args);
                return true;
            case "invite":
                handleInvite(player, label, args);
                return true;
            case "accept":
                handleAccept(player, label, args);
                return true;
            case "leave":
                handleLeave(player);
                return true;
            case "disband":
                handleDisband(player);
                return true;
            case "status":
                handleStatus(player);
                return true;
            default:
                sendUsage(player, label);
                return true;
        }
    }

    private void handleCreate(Player player, String label, String[] args) {
        if (!hasPermission(player, "myth.user.squad.create")) {
            player.sendMessage(Component.text(messages.format("commands.common.no_permission"), NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text(messages.format("commands.squad.create_usage", java.util.Map.of("label", label)), NamedTextColor.RED));
            return;
        }
        String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (name.isEmpty()) {
            player.sendMessage(Component.text(messages.format("commands.squad.name_required"), NamedTextColor.RED));
            return;
        }
        squadManager.createSquad(player, name);
    }

    private void handleInvite(Player player, String label, String[] args) {
        if (!hasPermission(player, "myth.user.squad.invite")) {
            player.sendMessage(Component.text(messages.format("commands.common.no_permission"), NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text(messages.format("commands.squad.invite_usage", java.util.Map.of("label", label)), NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage(Component.text(messages.format("commands.squad.player_not_online"), NamedTextColor.RED));
            return;
        }
        if (target.equals(player)) {
            player.sendMessage(Component.text(messages.format("commands.squad.cannot_invite_self"), NamedTextColor.RED));
            return;
        }
        squadManager.invite(player, target);
    }

    private void handleAccept(Player player, String label, String[] args) {
        if (!hasPermission(player, "myth.user.squad.accept")) {
            player.sendMessage(Component.text(messages.format("commands.common.no_permission"), NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text(messages.format("commands.squad.accept_usage", java.util.Map.of("label", label)), NamedTextColor.RED));
            return;
        }
        String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        squadManager.accept(player, name);
    }

    private void handleLeave(Player player) {
        if (!hasPermission(player, "myth.user.squad.leave")) {
            player.sendMessage(Component.text(messages.format("commands.common.no_permission"), NamedTextColor.RED));
            return;
        }
        squadManager.leave(player);
    }

    private void handleDisband(Player player) {
        if (!hasPermission(player, "myth.user.squad.disband")) {
            player.sendMessage(Component.text(messages.format("commands.common.no_permission"), NamedTextColor.RED));
            return;
        }
        squadManager.disband(player);
    }

    private void handleStatus(Player player) {
        if (!hasPermission(player, "myth.user.squad.status")) {
            player.sendMessage(Component.text(messages.format("commands.common.no_permission"), NamedTextColor.RED));
            return;
        }
        squadManager.sendStatus(player);
    }

    private boolean hasPermission(Player player, String permission) {
        return player.hasPermission("myth.user.squad.*") || player.hasPermission(permission);
    }

    private void sendUsage(Player player, String label) {
        player.sendMessage(Component.text(messages.format("commands.common.usage_header"), NamedTextColor.GOLD));
        for (String line : messages.formatList("commands.squad.usage", java.util.Map.of("label", label))) {
            player.sendMessage(Component.text("/" + line, NamedTextColor.GRAY));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return Arrays.asList("create", "invite", "accept", "leave", "disband", "status");
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "invite":
                if (args.length == 2) {
                    return Bukkit.getOnlinePlayers().stream()
                            .filter(p -> !p.equals(player))
                            .map(Player::getName)
                            .collect(Collectors.toList());
                }
                return Collections.emptyList();
            case "accept":
                if (args.length == 2) {
                    Set<String> invites = squadManager.getInvites(player.getUniqueId());
                    return invites.stream().collect(Collectors.toList());
                }
                return Collections.emptyList();
            default:
                return Collections.emptyList();
        }
    }
}
