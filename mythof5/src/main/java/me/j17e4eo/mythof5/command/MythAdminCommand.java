package me.j17e4eo.mythof5.command;

import me.j17e4eo.mythof5.Mythof5;
import me.j17e4eo.mythof5.balance.BalanceTable;
import me.j17e4eo.mythof5.boss.BossInstance;
import me.j17e4eo.mythof5.boss.BossManager;
import me.j17e4eo.mythof5.chronicle.ChronicleManager;
import me.j17e4eo.mythof5.config.Messages;
import me.j17e4eo.mythof5.inherit.AspectManager;
import me.j17e4eo.mythof5.inherit.InheritManager;
import me.j17e4eo.mythof5.inherit.aspect.GoblinAspect;
import me.j17e4eo.mythof5.omens.OmenManager;
import me.j17e4eo.mythof5.omens.OmenStage;
import me.j17e4eo.mythof5.relic.RelicManager;
import me.j17e4eo.mythof5.relic.RelicType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class MythAdminCommand implements CommandExecutor, TabCompleter {

    private final Mythof5 plugin;
    private final BossManager bossManager;
    private final InheritManager inheritManager;
    private final AspectManager aspectManager;
    private final RelicManager relicManager;
    private final ChronicleManager chronicleManager;
    private final OmenManager omenManager;
    private final BalanceTable balanceTable;
    private final Messages messages;

    public MythAdminCommand(Mythof5 plugin, BossManager bossManager, InheritManager inheritManager,
                            AspectManager aspectManager, RelicManager relicManager,
                            ChronicleManager chronicleManager, OmenManager omenManager,
                            BalanceTable balanceTable, Messages messages) {
        this.plugin = plugin;
        this.bossManager = bossManager;
        this.inheritManager = inheritManager;
        this.aspectManager = aspectManager;
        this.relicManager = relicManager;
        this.chronicleManager = chronicleManager;
        this.omenManager = omenManager;
        this.balanceTable = balanceTable;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }
        String root = args[0].toLowerCase(Locale.ROOT);
        if (root.equals("help")) {
            sendUsage(sender, label);
            return true;
        }
        if (root.equals("guide")) {
            handleGuide(sender, label);
            return true;
        }
        if (!root.equals("admin")) {
            sendUsage(sender, label);
            return true;
        }
        if (args.length < 2) {
            sendUsage(sender, label);
            return true;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        String[] tail = Arrays.copyOfRange(args, 2, args.length);
        return switch (sub) {
            case "spawnboss" -> { handleSpawnBoss(sender, label, tail); yield true; }
            case "bosslist" -> { handleBossList(sender); yield true; }
            case "endboss" -> { handleEndBoss(sender, label, tail); yield true; }
            case "inherit" -> { handleInherit(sender, tail); yield true; }
            case "clearinherit" -> { handleClearInherit(sender, tail); yield true; }
            case "relic" -> { handleRelic(sender, tail); yield true; }
            case "chronicle" -> { handleChronicle(sender, tail); yield true; }
            case "omen" -> { handleOmen(sender, tail); yield true; }
            case "balance" -> { handleBalance(sender); yield true; }
            default -> { sendUsage(sender, label); yield true; }
        };
    }

    private void handleSpawnBoss(CommandSender sender, String label, String[] args) {
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
            location = new Location(player.getWorld(), x != null ? x : player.getLocation().getX(),
                    y != null ? y : player.getLocation().getY(), z != null ? z : player.getLocation().getZ());
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
        sender.sendMessage(Component.text(messages.format("commands.myth.spawn_success", Map.of(
                "id", String.valueOf(instance.getId()),
                "location", formatLocation(instance.getEntity().getLocation()),
                "type", entityType.name(),
                "name", instance.getDisplayName()
        )), NamedTextColor.GREEN));
    }

    private void handleBossList(CommandSender sender) {
        bossManager.sendBossList(sender);
    }

    private void handleEndBoss(CommandSender sender, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Component.text(messages.format("commands.myth.end_usage", Map.of("label", label)), NamedTextColor.RED));
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

    private void handleInherit(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text(messages.format("commands.admin.inherit_usage"), NamedTextColor.RED));
            return;
        }
        GoblinAspect aspect = GoblinAspect.fromKey(args[0]);
        if (aspect == null) {
            sender.sendMessage(Component.text(messages.format("goblin.aspect.unknown"), NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text(messages.format("commands.common.player_not_online"), NamedTextColor.RED));
            return;
        }
        aspectManager.setInheritor(aspect, target, true, messages.format("chronicle.inherit.force", Map.of(
                "player", target.getName(),
                "aspect", aspect.getDisplayName()
        )));
        sender.sendMessage(Component.text(messages.format("commands.admin.inherit_success", Map.of(
                "player", target.getName(),
                "aspect", aspect.getDisplayName()
        )), NamedTextColor.GREEN));
    }

    private void handleClearInherit(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Component.text(messages.format("commands.admin.clear_usage"), NamedTextColor.RED));
            return;
        }
        GoblinAspect aspect = GoblinAspect.fromKey(args[0]);
        if (aspect == null) {
            sender.sendMessage(Component.text(messages.format("goblin.aspect.unknown"), NamedTextColor.RED));
            return;
        }
        aspectManager.clearInheritor(aspect, true, aspectManager.getInheritorName(aspect));
        sender.sendMessage(Component.text(messages.format("commands.admin.clear_success", Map.of(
                "aspect", aspect.getDisplayName()
        )), NamedTextColor.GREEN));
    }

    private void handleRelic(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text(messages.format("commands.admin.relic_usage"), NamedTextColor.RED));
            return;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text(messages.format("commands.common.player_not_online"), NamedTextColor.RED));
            return;
        }
        RelicType type = RelicType.fromKey(args[2]);
        if (type == null) {
            sender.sendMessage(Component.text(messages.format("relic.unknown"), NamedTextColor.RED));
            return;
        }
        switch (action) {
            case "give" -> {
                boolean granted = relicManager.grantRelic(target, type, true);
                if (!granted) {
                    sender.sendMessage(Component.text(messages.format("commands.admin.relic_duplicate"), NamedTextColor.YELLOW));
                } else {
                    sender.sendMessage(Component.text(messages.format("commands.admin.relic_grant", Map.of(
                            "player", target.getName(),
                            "relic", type.getDisplayName()
                    )), NamedTextColor.GREEN));
                }
            }
            case "remove" -> {
                boolean removed = relicManager.removeRelic(target, type);
                if (removed) {
                    sender.sendMessage(Component.text(messages.format("commands.admin.relic_removed", Map.of(
                            "player", target.getName(),
                            "relic", type.getDisplayName()
                    )), NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text(messages.format("commands.admin.relic_not_owned"), NamedTextColor.RED));
                }
            }
            default -> sender.sendMessage(Component.text(messages.format("commands.admin.relic_usage"), NamedTextColor.RED));
        }
    }

    private void handleChronicle(CommandSender sender, String[] args) {
        int count = 5;
        if (args.length > 0) {
            try {
                count = Math.max(1, Integer.parseInt(args[0]));
            } catch (NumberFormatException ignored) {
            }
        }
        for (String line : chronicleManager.formatRecent(count)) {
            sender.sendMessage(line);
        }
    }

    private void handleOmen(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(Component.text(messages.format("commands.admin.omen_usage"), NamedTextColor.RED));
            return;
        }
        OmenStage stage;
        try {
            stage = OmenStage.valueOf(args[0].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(Component.text(messages.format("commands.admin.omen_unknown"), NamedTextColor.RED));
            return;
        }
        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : messages.format("omen.unknown_reason");
        omenManager.trigger(stage, reason);
        sender.sendMessage(Component.text(messages.format("commands.admin.omen_triggered", Map.of(
                "stage", stage.name(),
                "reason", reason
        )), NamedTextColor.GOLD));
    }

    private void handleBalance(CommandSender sender) {
        for (String line : balanceTable.format()) {
            sender.sendMessage(line);
        }
    }

    private void handleGuide(CommandSender sender, String label) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(messages.format("commands.common.player_only"), NamedTextColor.RED));
            return;
        }
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        if (!(book.getItemMeta() instanceof BookMeta meta)) {
            sender.sendMessage(Component.text(messages.format("commands.myth.guide.error"), NamedTextColor.RED));
            return;
        }
        meta.setTitle(messages.format("commands.myth.guide.title"));
        meta.setAuthor(messages.format("commands.myth.guide.author"));
        Map<String, String> placeholders = Map.of("command", "/" + label);
        Component[] pages = messages.formatList("commands.myth.guide.pages", placeholders).stream()
                .map(Component::text)
                .toArray(Component[]::new);
        if (pages.length > 0) {
            meta.addPages(pages);
        }
        book.setItemMeta(meta);
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(book);
        if (overflow.isEmpty()) {
            player.sendMessage(Component.text(messages.format("commands.myth.guide.received"), NamedTextColor.GOLD));
            return;
        }
        player.sendMessage(Component.text(messages.format("commands.myth.guide.dropped"), NamedTextColor.YELLOW));
        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    private void sendUsage(CommandSender sender, String label) {
        for (String line : messages.formatList("commands.admin.usage", Map.of("label", "/" + label))) {
            sender.sendMessage(Component.text(line, NamedTextColor.GRAY));
        }
    }

    private String formatLocation(Location location) {
        return String.format("%s(%.1f, %.1f, %.1f)",
                location.getWorld() != null ? location.getWorld().getName() : "unknown",
                location.getX(), location.getY(), location.getZ());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return partial(List.of("admin", "guide", "help"), args[0]);
        }
        if (!args[0].equalsIgnoreCase("admin")) {
            return Collections.emptyList();
        }
        if (args.length == 2) {
            return partial(List.of("spawnboss", "bosslist", "endboss", "inherit", "clearinherit", "relic", "chronicle", "omen", "balance"), args[1]);
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "spawnboss" -> {
                if (args.length == 3) {
                    return Arrays.stream(EntityType.values()).map(Enum::name).collect(Collectors.toList());
                }
            }
            case "inherit", "clearinherit" -> {
                if (args.length == 3) {
                    List<String> options = new ArrayList<>();
                    for (GoblinAspect aspect : GoblinAspect.values()) {
                        options.add(aspect.getKey());
                    }
                    return options;
                }
                if (args.length == 4 && sub.equals("inherit")) {
                    return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
                }
            }
            case "relic" -> {
                if (args.length == 3) {
                    return List.of("give", "remove");
                }
                if (args.length == 4) {
                    return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
                }
                if (args.length == 5) {
                    List<String> keys = new ArrayList<>();
                    for (RelicType type : RelicType.values()) {
                        keys.add(type.getKey());
                    }
                    return keys;
                }
            }
            case "omen" -> {
                if (args.length == 3) {
                    List<String> keys = new ArrayList<>();
                    for (OmenStage stage : OmenStage.values()) {
                        keys.add(stage.name());
                    }
                    return keys;
                }
            }
        }
        return Collections.emptyList();
    }

    private List<String> partial(List<String> values, String token) {
        if (token == null || token.isEmpty()) {
            return values;
        }
        String lower = token.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .collect(Collectors.toList());
    }
}
