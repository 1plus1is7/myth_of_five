package me.j17e4eo.mythof5.hunter.seal.command;

import me.j17e4eo.mythof5.config.Messages;
import me.j17e4eo.mythof5.hunter.seal.SealManager;
import me.j17e4eo.mythof5.hunter.seal.data.PlayerSealProfile;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Handles the /seal command entrypoint including admin tools.
 */
public class SealCommand implements CommandExecutor, TabCompleter {

    private final SealManager sealManager;
    private final Messages messages;

    public SealCommand(SealManager sealManager, Messages messages) {
        this.sealManager = sealManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(messages.format("commands.common.player_only"));
                return true;
            }
            sealManager.openSealGui(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> handleStatus(sender, Arrays.copyOfRange(args, 1, args.length));
            case "admin" -> handleAdmin(sender, Arrays.copyOfRange(args, 1, args.length));
            default -> sender.sendMessage(messages.format("commands.seal.usage", Map.of("label", label)));
        }
        return true;
    }

    private void handleStatus(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("commands.common.player_only"));
            return;
        }
        List<String> lines = sealManager.describeProfile(player);
        for (String line : lines) {
            sender.sendMessage(line);
        }
    }

    private void handleAdmin(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(messages.format("commands.seal.admin.usage"));
            return;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "inspect" -> handleInspect(sender, Arrays.copyOfRange(args, 1, args.length));
            case "unseal" -> handleForceUnseal(sender, Arrays.copyOfRange(args, 1, args.length));
            default -> sender.sendMessage(messages.format("commands.seal.admin.usage"));
        }
    }

    private void handleInspect(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(messages.format("commands.seal.admin.inspect_usage"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target.getUniqueId() == null && !target.hasPlayedBefore()) {
            sender.sendMessage(messages.format("commands.common.player_not_online"));
            return;
        }
        UUID uuid = target.getUniqueId();
        PlayerSealProfile profile = sealManager.getProfile(uuid);
        String name = target.getName() != null ? target.getName() : uuid.toString();
        for (String line : sealManager.describeProfile(profile, name)) {
            sender.sendMessage(line);
        }
    }

    private void handleForceUnseal(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(messages.format("commands.seal.admin.unseal_usage"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(messages.format("commands.common.player_not_online"));
            return;
        }
        boolean success = sealManager.forceUnseal(target, true);
        if (success) {
            sender.sendMessage(messages.format("commands.seal.admin.unseal_success", Map.of("player", target.getName())));
            target.sendMessage(messages.format("commands.seal.admin.unsealed_notice"));
        } else {
            sender.sendMessage(messages.format("commands.seal.admin.unseal_fail", Map.of("player", target.getName())));
        }
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            list.add("status");
            list.add("admin");
            return partial(list, args[0]);
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("admin")) {
            if (args.length == 2) {
                return partial(List.of("inspect", "unseal"), args[1]);
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("inspect")) {
                return Collections.emptyList();
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("unseal")) {
                return null;
            }
        }
        return Collections.emptyList();
    }

    private List<String> partial(List<String> values, String token) {
        if (token == null || token.isEmpty()) {
            return values;
        }
        List<String> result = new ArrayList<>();
        String lower = token.toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(value);
            }
        }
        return result;
    }
}
