package me.j17e4eo.mythof5.command;

import me.j17e4eo.mythof5.config.Messages;
import me.j17e4eo.mythof5.relic.RelicFusion;
import me.j17e4eo.mythof5.relic.RelicManager;
import me.j17e4eo.mythof5.relic.RelicType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RelicCommand implements CommandExecutor, TabCompleter {

    private final RelicManager relicManager;
    private final Messages messages;

    public RelicCommand(RelicManager relicManager, Messages messages) {
        this.relicManager = relicManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(messages.format("commands.common.player_only"));
                    return true;
                }
                showRelics(player);
                return true;
            }
            case "fusions" -> {
                showFusions(sender);
                return true;
            }
            default -> {
                sendUsage(sender, label);
                return true;
            }
        }
    }

    private void sendUsage(CommandSender sender, String label) {
        for (String line : messages.formatList("relic.usage", Map.of("label", "/" + label))) {
            sender.sendMessage(line);
        }
    }

    private void showRelics(Player player) {
        for (String line : relicManager.describeRelics(player.getUniqueId())) {
            player.sendMessage(line);
        }
    }

    private void showFusions(CommandSender sender) {
        List<RelicFusion> fusions = relicManager.getFusions();
        if (fusions.isEmpty()) {
            sender.sendMessage(messages.format("relic.fusion.none"));
            return;
        }
        sender.sendMessage(messages.format("relic.fusion.header"));
        for (RelicFusion fusion : fusions) {
            String ingredients = fusion.getIngredients().stream()
                    .map(RelicType::getDisplayName)
                    .reduce((a, b) -> a + " + " + b)
                    .orElse("");
            sender.sendMessage(messages.format("relic.fusion.entry", Map.of(
                    "result", fusion.getResult().getDisplayName(),
                    "ingredients", ingredients,
                    "desc", fusion.getDescription()
            )));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(List.of("list", "fusions"));
        }
        String current = args[args.length - 1].toLowerCase(Locale.ROOT);
        return completions.stream().filter(entry -> entry.toLowerCase(Locale.ROOT).startsWith(current)).toList();
    }
}
