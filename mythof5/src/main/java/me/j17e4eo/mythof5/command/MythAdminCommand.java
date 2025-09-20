package me.j17e4eo.mythof5.command;

import me.j17e4eo.mythof5.Mythof5;
import me.j17e4eo.mythof5.balance.BalanceTable;
import me.j17e4eo.mythof5.boss.BossInstance;
import me.j17e4eo.mythof5.boss.BossManager;
import me.j17e4eo.mythof5.chronicle.ChronicleManager;
import me.j17e4eo.mythof5.config.Messages;
import me.j17e4eo.mythof5.command.gui.AdminGuiManager;
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
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
    private final AdminGuiManager guiManager;
    private final Map<String, AdminExecutor> adminExecutors;

    public MythAdminCommand(Mythof5 plugin, BossManager bossManager, InheritManager inheritManager,
                            AspectManager aspectManager, RelicManager relicManager,
                            ChronicleManager chronicleManager, OmenManager omenManager,
                            BalanceTable balanceTable, Messages messages, AdminGuiManager guiManager) {
        this.plugin = plugin;
        this.bossManager = bossManager;
        this.inheritManager = inheritManager;
        this.aspectManager = aspectManager;
        this.relicManager = relicManager;
        this.chronicleManager = chronicleManager;
        this.omenManager = omenManager;
        this.balanceTable = balanceTable;
        this.messages = messages;
        this.guiManager = guiManager;

        Map<String, AdminExecutor> executors = new LinkedHashMap<>();
        executors.put("boss spawn", this::handleSpawnBoss);
        executors.put("boss list", (sender, label, args) -> handleBossList(sender));
        executors.put("boss end", this::handleEndBoss);
        executors.put("inherit set", this::handleInheritSet);
        executors.put("inherit clear", this::handleClearInherit);
        executors.put("relic give", (sender, label, args) -> handleRelicAction(sender, "give", args));
        executors.put("relic remove", (sender, label, args) -> handleRelicAction(sender, "remove", args));
        executors.put("chronicle", (sender, label, args) -> handleChronicle(sender, args));
        executors.put("omen", (sender, label, args) -> handleOmen(sender, args));
        executors.put("balance", (sender, label, args) -> handleBalance(sender, args));
        this.adminExecutors = Collections.unmodifiableMap(executors);

    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                guiManager.openMainMenu(player);
            } else {
                sendUsage(sender, label);
            }
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
        if (root.equals("admin")) {
            if (args.length == 1) {
                if (sender instanceof Player player) {
                    guiManager.openMainMenu(player);
                } else {
                    sendUsage(sender, label);
                }
                return true;
            }
            String[] remapped = remapLegacyAdmin(args);
            if (remapped == null || !dispatchRoute(sender, label, remapped)) {
                sendUsage(sender, label);
            }
            return true;
        }
        if (!dispatchRoute(sender, label, args)) {
            sendUsage(sender, label);
        }
        return true;
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

    private void handleInheritSet(CommandSender sender, String label, String[] args) {
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

    private void handleClearInherit(CommandSender sender, String label, String[] args) {
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

    private void handleRelicAction(CommandSender sender, String action, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text(messages.format("commands.admin.relic_usage"), NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text(messages.format("commands.common.player_not_online"), NamedTextColor.RED));
            return;
        }
        RelicType type = RelicType.fromKey(args[1]);
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

    private void handleBalance(CommandSender sender, String[] args) {
        if (args.length == 0) {
            for (String line : balanceTable.formatSummary()) {
                sender.sendMessage(line);
            }
            sender.sendMessage(messages.format("commands.admin.balance_hint"));
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("report")) {
            for (String line : balanceTable.buildReport()) {
                sender.sendMessage(line);
            }
            return;
        }
        if (sub.equals("export")) {
            try {
                File file = balanceTable.exportCsv(plugin.getDataFolder());
                sender.sendMessage(messages.format("commands.admin.balance_export", Map.of(
                        "path", file.getName()
                )));
            } catch (IOException e) {
                sender.sendMessage(messages.format("commands.admin.balance_export_fail"));
                plugin.getLogger().warning("Failed to export balance CSV: " + e.getMessage());
            }
            return;
        }
        sender.sendMessage(messages.format("commands.admin.balance_unknown"));
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
            return partial(List.of("boss", "chronicle", "inherit", "relic", "omen", "balance", "guide", "help", "admin"), args[0]);
        }
        String root = args[0].toLowerCase(Locale.ROOT);
        if (root.equals("guide") || root.equals("help")) {
            return Collections.emptyList();
        }
        if (root.equals("admin")) {
            String[] legacyArgs = Arrays.copyOfRange(args, 1, args.length);
            return completeLegacyAdmin(sender, legacyArgs);
        }
        switch (root) {
            case "boss" -> {
                String[] bossArgs = Arrays.copyOfRange(args, 1, args.length);
                return completeBoss(sender, bossArgs);
            }
            case "inherit" -> {
                String[] inheritArgs = Arrays.copyOfRange(args, 1, args.length);
                return completeInherit(sender, inheritArgs);
            }
            case "relic" -> {
                String[] relicArgs = Arrays.copyOfRange(args, 1, args.length);
                return completeRelic(sender, relicArgs);
            }
            case "omen" -> {
                String[] omenArgs = Arrays.copyOfRange(args, 1, args.length);
                return completeOmen(sender, omenArgs);
            }
            case "balance" -> {
                String[] balanceArgs = Arrays.copyOfRange(args, 1, args.length);
                return completeBalance(sender, balanceArgs);
            }
            case "chronicle" -> {
                return Collections.emptyList();
            }
            default -> {
                return Collections.emptyList();
            }
        }
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

    private List<String> completeSpawnBoss(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return partial(Arrays.stream(EntityType.values()).map(Enum::name).collect(Collectors.toList()), args[0]);
        }
        return Collections.emptyList();
    }

    private List<String> completeEndBoss(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> ids = bossManager.getActiveBosses().stream()
                    .map(instance -> String.valueOf(instance.getId()))
                    .collect(Collectors.toList());
            return partial(ids, args[0]);
        }
        return Collections.emptyList();
    }

    private List<String> completeInheritSet(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return partial(Arrays.stream(GoblinAspect.values())
                    .map(GoblinAspect::getKey)
                    .collect(Collectors.toList()), args[0]);
        }
        if (args.length == 2) {
            return partial(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList()), args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> completeClearInherit(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return partial(Arrays.stream(GoblinAspect.values())
                    .map(GoblinAspect::getKey)
                    .collect(Collectors.toList()), args[0]);
        }
        return Collections.emptyList();
    }

    private List<String> completeRelicAction(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return partial(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList()), args[0]);
        }
        if (args.length == 2) {
            return partial(Arrays.stream(RelicType.values())
                    .map(RelicType::getKey)
                    .collect(Collectors.toList()), args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> completeOmen(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return partial(Arrays.stream(OmenStage.values()).map(Enum::name).collect(Collectors.toList()), args[0]);
        }
        return Collections.emptyList();
    }

    private List<String> completeBalance(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return partial(List.of("report", "export"), args[0]);
        }
        return Collections.emptyList();
    }

    private boolean dispatchRoute(CommandSender sender, String label, String[] args) {
        ResolvedRoute route = resolveRoute(args);
        if (route == null) {
            return false;
        }
        route.executor().execute(sender, label, route.tail());
        return true;
    }

    private ResolvedRoute resolveRoute(String[] args) {
        if (args.length == 0) {
            return null;
        }
        int maxDepth = Math.min(2, args.length);
        String[] lowered = new String[maxDepth];
        for (int i = 0; i < maxDepth; i++) {
            lowered[i] = args[i].toLowerCase(Locale.ROOT);
        }
        for (int depth = maxDepth; depth >= 1; depth--) {
            String key = String.join(" ", Arrays.copyOfRange(lowered, 0, depth));
            AdminExecutor executor = adminExecutors.get(key);
            if (executor != null) {
                String[] tail = Arrays.copyOfRange(args, depth, args.length);
                return new ResolvedRoute(executor, tail);
            }
        }
        return null;
    }

    private String[] remapLegacyAdmin(String[] args) {
        if (args.length < 2) {
            return null;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        String[] tail = Arrays.copyOfRange(args, 2, args.length);
        return switch (sub) {
            case "spawnboss" -> merge(new String[]{"boss", "spawn"}, tail);
            case "bosslist" -> merge(new String[]{"boss", "list"}, tail);
            case "endboss" -> merge(new String[]{"boss", "end"}, tail);
            case "inherit" -> merge(new String[]{"inherit", "set"}, tail);
            case "clearinherit" -> merge(new String[]{"inherit", "clear"}, tail);
            case "relic" -> merge(new String[]{"relic"}, tail);
            case "chronicle" -> merge(new String[]{"chronicle"}, tail);
            case "omen" -> merge(new String[]{"omen"}, tail);
            case "balance" -> merge(new String[]{"balance"}, tail);
            default -> null;
        };
    }

    private String[] merge(String[] head, String[] tail) {
        String[] result = new String[head.length + tail.length];
        System.arraycopy(head, 0, result, 0, head.length);
        System.arraycopy(tail, 0, result, head.length, tail.length);
        return result;
    }

    private List<String> completeLegacyAdmin(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return List.of("spawnboss", "bosslist", "endboss", "inherit", "clearinherit", "relic", "chronicle", "omen", "balance");
        }
        if (args.length == 1) {
            return partial(List.of("spawnboss", "bosslist", "endboss", "inherit", "clearinherit", "relic", "chronicle", "omen", "balance"), args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] tail = Arrays.copyOfRange(args, 1, args.length);
        return switch (sub) {
            case "spawnboss" -> completeSpawnBoss(sender, tail);
            case "bosslist" -> Collections.emptyList();
            case "endboss" -> completeEndBoss(sender, tail);
            case "inherit" -> completeInheritSet(sender, tail);
            case "clearinherit" -> completeClearInherit(sender, tail);
            case "relic" -> completeLegacyRelic(sender, tail);
            case "chronicle" -> Collections.emptyList();
            case "omen" -> completeOmen(sender, tail);
            case "balance" -> completeBalance(sender, tail);
            default -> Collections.emptyList();
        };
    }

    private List<String> completeLegacyRelic(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return partial(List.of("give", "remove"), args[0]);
        }
        if (args.length >= 2) {
            String action = args[0].toLowerCase(Locale.ROOT);
            String[] tail = Arrays.copyOfRange(args, 1, args.length);
            if (action.equals("give") || action.equals("remove")) {
                return completeRelicAction(sender, tail);
            }
        }
        return Collections.emptyList();
    }

    private List<String> completeBoss(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return List.of("spawn", "list", "end");
        }
        if (args.length == 1) {
            return partial(List.of("spawn", "list", "end"), args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] tail = Arrays.copyOfRange(args, 1, args.length);
        return switch (sub) {
            case "spawn" -> completeSpawnBoss(sender, tail);
            case "end" -> completeEndBoss(sender, tail);
            default -> Collections.emptyList();
        };
    }

    private List<String> completeInherit(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return List.of("set", "clear");
        }
        if (args.length == 1) {
            return partial(List.of("set", "clear"), args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] tail = Arrays.copyOfRange(args, 1, args.length);
        return switch (sub) {
            case "set" -> completeInheritSet(sender, tail);
            case "clear" -> completeClearInherit(sender, tail);
            default -> Collections.emptyList();
        };
    }

    private List<String> completeRelic(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return List.of("give", "remove");
        }
        if (args.length == 1) {
            return partial(List.of("give", "remove"), args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] tail = Arrays.copyOfRange(args, 1, args.length);
        if (sub.equals("give") || sub.equals("remove")) {
            return completeRelicAction(sender, tail);
        }
        return Collections.emptyList();
    }

    private record ResolvedRoute(AdminExecutor executor, String[] tail) {
    }

    @FunctionalInterface
    private interface AdminExecutor {
        void execute(CommandSender sender, String label, String[] args);
    }

}
