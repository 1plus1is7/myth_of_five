package me.j17e4eo.mythof5.command;

import me.j17e4eo.mythof5.config.Messages;
import me.j17e4eo.mythof5.inherit.AspectManager;
import me.j17e4eo.mythof5.inherit.aspect.GoblinAspect;
import me.j17e4eo.mythof5.inherit.aspect.GoblinSkill;
import me.j17e4eo.mythof5.inherit.aspect.GoblinSkillCategory;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class GoblinCommand implements CommandExecutor, TabCompleter {

    private final AspectManager aspectManager;
    private final Messages messages;

    public GoblinCommand(AspectManager aspectManager, Messages messages) {
        this.aspectManager = aspectManager;
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
            case "help" -> {
                sendUsage(sender, label);
                return true;
            }
            case "skill" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(messages.format("commands.common.player_only"));
                    return true;
                }
                if (args.length < 2) {
                    showSkills(sender, args);
                    return true;
                }
                aspectManager.useSkill(player, args[1]);
                return true;
            }
            case "skills" -> {
                showSkills(sender, args);
                return true;
            }
            case "info" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(messages.format("commands.common.player_only"));
                    return true;
                }
                showInfo(player);
                return true;
            }
            case "progress" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(messages.format("commands.common.player_only"));
                    return true;
                }
                showProgress(player);
                return true;
            }
            case "share" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(messages.format("commands.common.player_only"));
                    return true;
                }
                if (args.length < 3) {
                    sendUsage(sender, label);
                    return true;
                }
                handleShare(player, args[1], args[2], true);
                return true;
            }
            case "reclaim" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(messages.format("commands.common.player_only"));
                    return true;
                }
                if (args.length < 3) {
                    sendUsage(sender, label);
                    return true;
                }
                handleShare(player, args[1], args[2], false);
                return true;
            }
            case "contract" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(messages.format("commands.common.player_only"));
                    return true;
                }
                aspectManager.attemptMischiefContract(player);
                return true;
            }
            case "forge" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(messages.format("commands.common.player_only"));
                    return true;
                }
                aspectManager.attemptForgeRitual(player);
                return true;
            }
            case "list" -> {
                listAspects(sender);
                return true;
            }
            default -> {
                sendUsage(sender, label);
                return true;
            }
        }
    }

    private void sendUsage(CommandSender sender, String label) {
        for (String line : messages.formatList("goblin.usage", Map.of("label", "/" + label))) {
            sender.sendMessage(line);
        }
    }

    private void showSkills(CommandSender sender, String[] args) {
        List<GoblinAspect> aspects = new ArrayList<>();
        if (args.length >= 2) {
            GoblinAspect aspect = GoblinAspect.fromKey(args[1]);
            if (aspect == null) {
                sender.sendMessage(messages.format("goblin.aspect.unknown"));
                return;
            }
            aspects.add(aspect);
        } else if (sender instanceof Player player) {
            Set<GoblinAspect> owned = aspectManager.getAspects(player.getUniqueId());
            if (owned.isEmpty()) {
                aspects.addAll(List.of(GoblinAspect.values()));
            } else {
                aspects.addAll(owned);
            }
        } else {
            aspects.addAll(List.of(GoblinAspect.values()));
        }
        sender.sendMessage(messages.format("goblin.skills.header"));
        for (GoblinAspect aspect : aspects) {
            sender.sendMessage(messages.format("goblin.skills.aspect", Map.of(
                    "aspect", aspect.getDisplayName(),
                    "key", aspect.getKey()
            )));
            for (GoblinSkill skill : aspect.getSkills()) {
                String key;
                switch (skill.getCategory()) {
                    case PASSIVE -> key = "goblin.skills.entry.passive";
                    case UTILITY -> key = "goblin.skills.entry.utility";
                    default -> key = "goblin.skills.entry.active";
                }
                sender.sendMessage(messages.format(key, Map.of(
                        "code", skill.getKey(),
                        "name", skill.getDisplayName(),
                        "cooldown", skill.getCategory() == GoblinSkillCategory.PASSIVE
                                ? "-"
                                : String.valueOf(skill.getBaseCooldownSeconds()),
                        "desc", skill.getDescription()
                )));
            }
        }
    }

    private void showInfo(Player player) {
        Set<GoblinAspect> aspects = aspectManager.getAspects(player.getUniqueId());
        if (aspects.isEmpty()) {
            player.sendMessage(messages.format("goblin.info.none"));
            return;
        }
        player.sendMessage(messages.format("goblin.info.header"));
        for (GoblinAspect aspect : aspects) {
            player.sendMessage(messages.format("goblin.info.aspect", Map.of(
                    "aspect", aspect.getDisplayName(),
                    "personality", aspect.getPersonality(),
                    "acquire", aspect.getAcquisition()
            )));
            for (GoblinSkill skill : aspect.getSkills()) {
                String key = skill.getCategory() == GoblinSkillCategory.PASSIVE
                        ? "goblin.info.skill.passive"
                        : "goblin.info.skill.active";
                player.sendMessage(messages.format(key, Map.of(
                        "name", skill.getDisplayName(),
                        "code", skill.getKey(),
                        "desc", skill.getDescription()
                )));
            }
        }
    }

    private void showProgress(Player player) {
        for (String line : aspectManager.describeProgress(player)) {
            player.sendMessage(line);
        }
    }

    private void handleShare(Player player, String aspectToken, String targetName, boolean share) {
        GoblinAspect aspect = GoblinAspect.fromKey(aspectToken);
        if (aspect == null) {
            player.sendMessage(messages.format("goblin.aspect.unknown"));
            return;
        }
        if (!aspectManager.isInheritor(aspect, player.getUniqueId())) {
            player.sendMessage(messages.format("goblin.aspect.not_holder"));
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            player.sendMessage(messages.format("commands.common.player_not_online"));
            return;
        }
        if (share) {
            if (aspectManager.sharePower(aspect, target)) {
                player.sendMessage(messages.format("goblin.share.success", Map.of(
                        "player", target.getName(),
                        "aspect", aspect.getDisplayName()
                )));
                target.sendMessage(messages.format("goblin.share.received", Map.of(
                        "aspect", aspect.getDisplayName(),
                        "player", player.getName()
                )));
            } else {
                player.sendMessage(messages.format("goblin.share.duplicate"));
            }
        } else {
            if (aspectManager.reclaimPower(aspect, target)) {
                player.sendMessage(messages.format("goblin.reclaim.success", Map.of(
                        "player", target.getName(),
                        "aspect", aspect.getDisplayName()
                )));
                target.sendMessage(messages.format("goblin.reclaim.notice", Map.of(
                        "aspect", aspect.getDisplayName()
                )));
            } else {
                player.sendMessage(messages.format("goblin.reclaim.missing"));
            }
        }
    }

    private void listAspects(CommandSender sender) {
        for (GoblinAspect aspect : GoblinAspect.values()) {
            String inheritor = aspectManager.getInheritorName(aspect);
            if (inheritor == null) {
                inheritor = messages.format("goblin.list.none");
            }
            sender.sendMessage(messages.format("goblin.list.entry", Map.of(
                    "aspect", aspect.getDisplayName(),
                    "holder", inheritor
            )));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(List.of("help", "info", "skill", "skills", "progress", "share", "reclaim", "contract", "forge", "list"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "skill" -> {
                    if (sender instanceof Player player) {
                        Set<String> keys = new HashSet<>();
                        for (GoblinAspect aspect : aspectManager.getAspects(player.getUniqueId())) {
                            for (GoblinSkill skill : aspect.getSkills()) {
                                if (skill.getCategory() != GoblinSkillCategory.PASSIVE) {
                                    keys.add(skill.getKey());
                                }
                            }
                        }
                        completions.addAll(keys);
                    }
                }
                case "skills" -> {
                    for (GoblinAspect aspect : GoblinAspect.values()) {
                        completions.add(aspect.getKey());
                    }
                }
                case "share", "reclaim" -> {
                    for (GoblinAspect aspect : GoblinAspect.values()) {
                        completions.add(aspect.getKey());
                    }
                }
            }
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("share") || args[0].equalsIgnoreCase("reclaim"))) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        }
        String current = args[args.length - 1].toLowerCase(Locale.ROOT);
        return completions.stream().filter(entry -> entry.toLowerCase(Locale.ROOT).startsWith(current)).toList();
    }
}
