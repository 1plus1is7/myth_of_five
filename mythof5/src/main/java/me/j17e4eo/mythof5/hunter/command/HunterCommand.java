package me.j17e4eo.mythof5.hunter.command;

import me.j17e4eo.mythof5.Mythof5;
import me.j17e4eo.mythof5.config.Messages;
import me.j17e4eo.mythof5.hunter.HunterManager;
import me.j17e4eo.mythof5.hunter.ParadoxManager;
import me.j17e4eo.mythof5.hunter.data.Artifact;
import me.j17e4eo.mythof5.hunter.data.ArtifactAbility;
import me.j17e4eo.mythof5.hunter.data.ArtifactGrade;
import me.j17e4eo.mythof5.hunter.data.ArtifactOrigin;
import me.j17e4eo.mythof5.hunter.data.ArtifactType;
import me.j17e4eo.mythof5.hunter.data.HunterProfile;
import me.j17e4eo.mythof5.hunter.test.HunterTestHook;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Command interface for the hunter route.
 */
public class HunterCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERMISSION = "myth.admin.hunter";

    private final Mythof5 plugin;
    private final HunterManager hunterManager;
    private final Messages messages;

    public HunterCommand(Mythof5 plugin, HunterManager hunterManager, Messages messages) {
        this.plugin = plugin;
        this.hunterManager = hunterManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "quest" -> handleQuest(sender, label, Arrays.copyOfRange(args, 1, args.length));
            case "status" -> handleStatus(sender, Arrays.copyOfRange(args, 1, args.length));
            case "craft" -> handleCraft(sender, Arrays.copyOfRange(args, 1, args.length));
            case "list" -> handleList(sender, Arrays.copyOfRange(args, 1, args.length));
            case "ability" -> handleAbility(sender, Arrays.copyOfRange(args, 1, args.length));
            case "patch" -> handlePatch(sender, Arrays.copyOfRange(args, 1, args.length));
            case "rebind" -> handleRebind(sender, Arrays.copyOfRange(args, 1, args.length));
            case "paradox" -> handleParadox(sender, Arrays.copyOfRange(args, 1, args.length));
            case "admin" -> handleAdmin(sender, Arrays.copyOfRange(args, 1, args.length));
            case "test" -> handleTest(sender, Arrays.copyOfRange(args, 1, args.length));
            default -> sendUsage(sender, label);
        }
        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        for (String line : messages.formatList("commands.hunter.usage", Map.of("label", label))) {
            sender.sendMessage(line);
        }
    }

    private void handleQuest(CommandSender sender, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("commands.common.player_only"));
            return;
        }
        if (args.length == 0) {
            sendUsage(sender, label);
            return;
        }
        String step = args[0].toLowerCase(Locale.ROOT);
        if (step.equals("accept")) {
            hunterManager.acceptQuest(player);
        } else if (step.equals("complete")) {
            hunterManager.engrave(player);
        } else {
            sendUsage(sender, label);
        }
    }

    private void handleStatus(CommandSender sender, String[] args) {
        HunterProfile profile;
        if (args.length >= 1) {
            if (!sender.hasPermission(ADMIN_PERMISSION)) {
                sender.sendMessage(messages.format("commands.common.no_permission"));
                return;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (target == null || (target.getUniqueId() == null && !target.hasPlayedBefore())) {
                sender.sendMessage(messages.format("commands.common.player_not_online"));
                return;
            }
            profile = hunterManager.findProfile(target.getUniqueId()).orElse(null);
            if (profile == null) {
                sender.sendMessage(messages.format("hunter.error.no_profile"));
                return;
            }
        } else {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(messages.format("commands.common.player_only"));
                return;
            }
            profile = hunterManager.getProfile(player);
        }
        for (String line : hunterManager.describeProfile(profile)) {
            sender.sendMessage(line);
        }
    }

    private void handleList(CommandSender sender, String[] args) {
        handleStatus(sender, args);
    }

    private void handleCraft(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("commands.common.player_only"));
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(messages.format("hunter.usage.craft"));
            return;
        }
        ArtifactType type = ArtifactType.fromKey(args[0]);
        ArtifactOrigin origin = ArtifactOrigin.fromKey(args[1]);
        ArtifactGrade grade = ArtifactGrade.fromKey(args[2]);
        String name = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        hunterManager.craftArtifact(player, type, origin, grade, name, Collections.emptyList(), false, 0);
    }

    private void handleAbility(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("commands.common.player_only"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(messages.format("hunter.usage.ability"));
            return;
        }
        HunterProfile profile = hunterManager.getProfile(player);
        Optional<Artifact> artifact = hunterManager.findArtifact(profile, args[0]);
        if (artifact.isEmpty()) {
            sender.sendMessage(messages.format("hunter.error.artifact_not_found"));
            return;
        }
        ArtifactAbility ability = artifact.get().getAbility(args[1]);
        if (ability == null) {
            sender.sendMessage(messages.format("hunter.error.ability_not_found"));
            return;
        }
        HunterManager.UseResult result = hunterManager.useAbility(player, artifact.get(), ability);
        if (result.integrity() >= 0) {
            player.sendMessage(messages.format("hunter.ability.result", Map.of(
                    "name", artifact.get().getName(),
                    "ability", ability.getName(),
                    "integrity", String.format(Locale.KOREA, "%.1f%%", result.integrity()),
                    "chance", String.format(Locale.KOREA, "%.1f%%", result.chance() * 100.0),
                    "roll", String.format(Locale.KOREA, "%.2f", result.roll()),
                    "release", result.release() ? messages.format("hunter.ability.release") : messages.format("hunter.ability.safe")
            )));
        }
    }

    private void handlePatch(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("commands.common.player_only"));
            return;
        }
        if (args.length < 1) {
            sender.sendMessage(messages.format("hunter.usage.patch"));
            return;
        }
        HunterProfile profile = hunterManager.getProfile(player);
        Optional<Artifact> artifact = hunterManager.findArtifact(profile, args[0]);
        if (artifact.isEmpty()) {
            sender.sendMessage(messages.format("hunter.error.artifact_not_found"));
            return;
        }
        double value = hunterManager.applyDefaultPatch(player, artifact.get());
        player.sendMessage(messages.format("hunter.patch.result", Map.of(
                "name", artifact.get().getName(),
                "integrity", String.format(Locale.KOREA, "%.1f%%", value)
        )));
    }

    private void handleRebind(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.format("commands.common.player_only"));
            return;
        }
        if (args.length < 1) {
            sender.sendMessage(messages.format("hunter.usage.rebind"));
            return;
        }
        HunterProfile profile = hunterManager.getProfile(player);
        Optional<Artifact> artifact = hunterManager.findArtifact(profile, args[0]);
        if (artifact.isEmpty()) {
            sender.sendMessage(messages.format("hunter.error.artifact_not_found"));
            return;
        }
        hunterManager.rebindArtifact(player, artifact.get());
    }

    private void handleParadox(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(messages.format("hunter.usage.paradox"));
            return;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("summon")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(messages.format("commands.common.player_only"));
                return;
            }
            if (!sender.hasPermission(ADMIN_PERMISSION)) {
                sender.sendMessage(messages.format("commands.common.no_permission"));
                return;
            }
            ParadoxManager manager = hunterManager.getParadoxManager();
            if (manager == null) {
                sender.sendMessage(messages.format("hunter.error.no_paradox"));
                return;
            }
            manager.summonParadox(player, player.getLocation());
        } else if (action.equals("offer")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(messages.format("commands.common.player_only"));
                return;
            }
            HunterProfile profile = hunterManager.getProfile(player);
            if (profile.getParadoxProgress() < 5) {
                player.sendMessage(messages.format("hunter.paradox.require_mythic", Map.of(
                        "count", String.valueOf(profile.getParadoxProgress())
                )));
                return;
            }
            ParadoxManager manager = hunterManager.getParadoxManager();
            if (manager == null) {
                player.sendMessage(messages.format("hunter.error.no_paradox"));
                return;
            }
            manager.beginRitual(player, profile);
        } else {
            sender.sendMessage(messages.format("hunter.usage.paradox"));
        }
    }

    private void handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(messages.format("commands.common.no_permission"));
            return;
        }
        if (args.length == 0) {
            sender.sendMessage(messages.format("hunter.usage.admin"));
            return;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "give" -> adminGive(sender, Arrays.copyOfRange(args, 1, args.length));
            case "release" -> adminRelease(sender, Arrays.copyOfRange(args, 1, args.length));
            case "patch" -> adminPatch(sender, Arrays.copyOfRange(args, 1, args.length));
            case "reset" -> adminReset(sender, Arrays.copyOfRange(args, 1, args.length));
            default -> sender.sendMessage(messages.format("hunter.usage.admin"));
        }
    }

    private void adminGive(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(messages.format("hunter.usage.admin_give"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(messages.format("commands.common.player_not_online"));
            return;
        }
        ArtifactType type = ArtifactType.fromKey(args[1]);
        ArtifactOrigin origin = ArtifactOrigin.fromKey(args[2]);
        ArtifactGrade grade = ArtifactGrade.fromKey(args[3]);
        String name = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
        hunterManager.craftArtifact(target, type, origin, grade, name, Collections.emptyList(), false, 0);
        sender.sendMessage(messages.format("hunter.admin.give", Map.of(
                "player", target.getName(),
                "name", name
        )));
    }

    private void adminRelease(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(messages.format("hunter.usage.admin_release"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(messages.format("commands.common.player_not_online"));
            return;
        }
        HunterProfile profile = hunterManager.getProfile(target);
        Optional<Artifact> artifact = hunterManager.findArtifact(profile, args[1]);
        if (artifact.isEmpty()) {
            sender.sendMessage(messages.format("hunter.error.artifact_not_found"));
            return;
        }
        if (hunterManager.forceRelease(target, artifact.get())) {
            sender.sendMessage(messages.format("hunter.admin.force_release", Map.of(
                    "player", target.getName(),
                    "name", artifact.get().getName()
            )));
        }
    }

    private void adminPatch(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(messages.format("hunter.usage.admin_patch"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(messages.format("commands.common.player_not_online"));
            return;
        }
        HunterProfile profile = hunterManager.getProfile(target);
        Optional<Artifact> artifact = hunterManager.findArtifact(profile, args[1]);
        if (artifact.isEmpty()) {
            sender.sendMessage(messages.format("hunter.error.artifact_not_found"));
            return;
        }
        double value;
        try {
            value = Double.parseDouble(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(messages.format("hunter.error.invalid_number"));
            return;
        }
        double result = hunterManager.applySealPatch(target, artifact.get(), value);
        sender.sendMessage(messages.format("hunter.admin.patch", Map.of(
                "player", target.getName(),
                "name", artifact.get().getName(),
                "integrity", String.format(Locale.KOREA, "%.1f%%", result)
        )));
    }

    private void adminReset(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(messages.format("hunter.usage.admin_reset"));
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(messages.format("commands.common.player_not_online"));
            return;
        }
        Optional<HunterProfile> profile = hunterManager.findProfile(target.getUniqueId());
        if (profile.isEmpty()) {
            sender.sendMessage(messages.format("hunter.error.no_profile"));
            return;
        }
        profile.get().setQuestAccepted(false);
        profile.get().setEngraved(false);
        sender.sendMessage(messages.format("hunter.admin.reset", Map.of("player", target.getName())));
        hunterManager.save();
    }

    private void handleTest(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(messages.format("commands.common.no_permission"));
            return;
        }
        if (args.length == 0) {
            sender.sendMessage(messages.format("hunter.usage.test"));
            return;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "create" -> {
                String name = args.length >= 2 ? args[1] : "hook";
                Map<String, Object> params = new HashMap<>();
                if (args.length >= 3) {
                    params.put("note", String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
                }
                HunterTestHook hook = hunterManager.createTestHook(name, params, sender.getName());
                sender.sendMessage(messages.format("hunter.test.created", Map.of("id", hook.getId().toString())));
            }
            case "list" -> {
                List<HunterTestHook> hooks = hunterManager.listTestHooks();
                if (hooks.isEmpty()) {
                    sender.sendMessage(messages.format("hunter.test.empty"));
                } else {
                    for (HunterTestHook hook : hooks) {
                        sender.sendMessage(messages.format("hunter.test.entry", Map.of(
                                "id", hook.getId().toString(),
                                "name", hook.getName(),
                                "creator", hook.getCreatedBy()
                        )));
                    }
                }
            }
            case "remove" -> {
                if (args.length < 2) {
                    sender.sendMessage(messages.format("hunter.usage.test_remove"));
                    return;
                }
                try {
                    UUID id = UUID.fromString(args[1]);
                    hunterManager.removeTestHook(id);
                    sender.sendMessage(messages.format("hunter.test.removed", Map.of("id", id.toString())));
                } catch (IllegalArgumentException ex) {
                    sender.sendMessage(messages.format("hunter.error.invalid_uuid"));
                }
            }
            default -> sender.sendMessage(messages.format("hunter.usage.test"));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("quest", "status", "craft", "list", "ability", "patch", "rebind", "paradox", "admin", "test"), args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("quest")) {
            if (args.length == 2) {
                return filter(List.of("accept", "complete"), args[1]);
            }
        } else if (sub.equals("paradox")) {
            if (args.length == 2) {
                return filter(List.of("summon", "offer"), args[1]);
            }
        } else if (sub.equals("admin")) {
            if (args.length == 2) {
                return filter(List.of("give", "release", "patch", "reset"), args[1]);
            }
        } else if (sub.equals("test")) {
            if (args.length == 2) {
                return filter(List.of("create", "list", "remove"), args[1]);
            }
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String current) {
        if (current == null || current.isEmpty()) {
            return options;
        }
        List<String> filtered = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(current.toLowerCase(Locale.ROOT))) {
                filtered.add(option);
            }
        }
        return filtered;
    }
}
