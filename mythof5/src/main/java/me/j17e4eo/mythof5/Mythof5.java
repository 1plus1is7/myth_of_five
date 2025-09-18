package me.j17e4eo.mythof5;

import me.j17e4eo.mythof5.balance.BalanceTable;
import me.j17e4eo.mythof5.boss.BossManager;
import me.j17e4eo.mythof5.chronicle.ChronicleManager;
import me.j17e4eo.mythof5.command.GoblinCommand;
import me.j17e4eo.mythof5.command.MythAdminCommand;
import me.j17e4eo.mythof5.command.RelicCommand;
import me.j17e4eo.mythof5.command.SquadCommand;
import me.j17e4eo.mythof5.config.Messages;
import me.j17e4eo.mythof5.inherit.AspectManager;
import me.j17e4eo.mythof5.inherit.InheritManager;
import me.j17e4eo.mythof5.listener.BossListener;
import me.j17e4eo.mythof5.listener.PlayerListener;
import me.j17e4eo.mythof5.listener.SquadListener;
import me.j17e4eo.mythof5.omens.OmenManager;
import me.j17e4eo.mythof5.relic.RelicManager;
import me.j17e4eo.mythof5.squad.SquadManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Objects;

public final class Mythof5 extends JavaPlugin {

    private BossManager bossManager;
    private InheritManager inheritManager;
    private SquadManager squadManager;
    private Messages messages;
    private ChronicleManager chronicleManager;
    private RelicManager relicManager;
    private AspectManager aspectManager;
    private OmenManager omenManager;
    private BalanceTable balanceTable;
    private boolean doubleJumpEnabled;
    private double doubleJumpVerticalVelocity;
    private double doubleJumpForwardMultiplier;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        registerConfigDefaults();
        loadDoubleJumpSettings();

        messages = new Messages(this);
        chronicleManager = new ChronicleManager(this, messages);
        chronicleManager.load();
        relicManager = new RelicManager(this, messages, chronicleManager);
        relicManager.load();
        omenManager = new OmenManager(this, messages, chronicleManager);
        balanceTable = new BalanceTable();

        aspectManager = new AspectManager(this, messages, chronicleManager, relicManager, omenManager);
        aspectManager.load();

        inheritManager = new InheritManager(this, messages, aspectManager);
        inheritManager.load();

        bossManager = new BossManager(this, inheritManager, aspectManager, messages);
        squadManager = new SquadManager(this, messages);
        squadManager.load();

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new BossListener(this, bossManager), this);
        PlayerListener playerListener = new PlayerListener(bossManager, inheritManager, aspectManager,
                doubleJumpEnabled, doubleJumpVerticalVelocity, doubleJumpForwardMultiplier);
        pluginManager.registerEvents(playerListener, this);
        pluginManager.registerEvents(inheritManager, this);
        pluginManager.registerEvents(new SquadListener(squadManager, getConfig().getBoolean("squad.friendly_fire", false), messages), this);

        registerCommands();

        inheritManager.reapplyToOnlinePlayers();
        for (Player player : Bukkit.getOnlinePlayers()) {
            aspectManager.handlePlayerJoin(player);
        }
        bossManager.initializeBossBars();
        playerListener.initializeExistingPlayers();
    }

    @Override
    public void onDisable() {
        if (bossManager != null) {
            bossManager.shutdown();
        }
        if (inheritManager != null) {
            inheritManager.save();
        }
        if (aspectManager != null) {
            aspectManager.save();
        }
        if (relicManager != null) {
            relicManager.save();
        }
        if (chronicleManager != null) {
            chronicleManager.save();
        }
        if (squadManager != null) {
            squadManager.save();
            squadManager.saveInvites();
        }
        if (omenManager != null) {
            omenManager.shutdown();
        }

        if (doubleJumpEnabled) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
                    player.setAllowFlight(false);
                    player.setFlying(false);
                }
            }
        }
    }

    private void registerCommands() {
        PluginCommand mythCommand = Objects.requireNonNull(getCommand("myth"), "Command myth not defined in plugin.yml");
        MythAdminCommand mythAdminCommand = new MythAdminCommand(this, bossManager, inheritManager, aspectManager,
                relicManager, chronicleManager, omenManager, balanceTable, messages);
        mythCommand.setExecutor(mythAdminCommand);
        mythCommand.setTabCompleter(mythAdminCommand);

        PluginCommand squadCommand = Objects.requireNonNull(getCommand("squad"), "Command squad not defined in plugin.yml");
        SquadCommand squadExecutor = new SquadCommand(squadManager, messages);
        squadCommand.setExecutor(squadExecutor);
        squadCommand.setTabCompleter(squadExecutor);

        PluginCommand goblinCommand = Objects.requireNonNull(getCommand("goblin"), "Command goblin not defined in plugin.yml");
        GoblinCommand goblinExecutor = new GoblinCommand(aspectManager, messages);
        goblinCommand.setExecutor(goblinExecutor);
        goblinCommand.setTabCompleter(goblinExecutor);

        PluginCommand relicCommand = Objects.requireNonNull(getCommand("relic"), "Command relic not defined in plugin.yml");
        RelicCommand relicExecutor = new RelicCommand(relicManager, messages);
        relicCommand.setExecutor(relicExecutor);
        relicCommand.setTabCompleter(relicExecutor);
    }

    public BossManager getBossManager() {
        return bossManager;
    }

    public InheritManager getInheritManager() {
        return inheritManager;
    }

    public SquadManager getSquadManager() {
        return squadManager;
    }

    public Messages getMessages() {
        return messages;
    }

    public ChronicleManager getChronicleManager() {
        return chronicleManager;
    }

    public RelicManager getRelicManager() {
        return relicManager;
    }

    public AspectManager getAspectManager() {
        return aspectManager;
    }

    public OmenManager getOmenManager() {
        return omenManager;
    }

    public BalanceTable getBalanceTable() {
        return balanceTable;
    }

    public void broadcast(String message) {
        Component component = Component.text().append(Component.text("「", NamedTextColor.GOLD))
                .append(Component.text(message, NamedTextColor.GOLD))
                .append(Component.text("」", NamedTextColor.GOLD))
                .build();
        Bukkit.broadcast(component);
    }

    private void registerConfigDefaults() {
        FileConfiguration config = getConfig();
        config.addDefault("boss.hp_default", 10000D);
        config.addDefault("boss.armor_default", 50D);
        config.addDefault("boss.name", "태초의 도깨비");
        config.addDefault("inherit.power_key", "dokkaebi.core");
        config.addDefault("inherit.buffs", List.of(
                "generic.max_health:set:40",
                "generic.movement_speed:add:0.03",
                "generic.attack_damage:add:2"
        ));
        config.addDefault("inherit.announce", true);
        config.addDefault("inherit.transformation.default.scale_multiplier", 2.0D);
        config.addDefault("inherit.transformation.default.attack_bonus", 6.0D);
        config.addDefault("inherit.transformation.default.speed_bonus", 0.0D);
        config.addDefault("inherit.transformation.aspects.power.scale_multiplier", 2.4D);
        config.addDefault("inherit.transformation.aspects.power.attack_bonus", 9.0D);
        config.addDefault("inherit.transformation.aspects.power.speed_bonus", 0.02D);
        config.addDefault("inherit.transformation.aspects.speed.scale_multiplier", 1.8D);
        config.addDefault("inherit.transformation.aspects.speed.attack_bonus", 4.0D);
        config.addDefault("inherit.transformation.aspects.speed.speed_bonus", 0.08D);
        config.addDefault("inherit.transformation.aspects.mischief.scale_multiplier", 1.9D);
        config.addDefault("inherit.transformation.aspects.mischief.attack_bonus", 5.0D);
        config.addDefault("inherit.transformation.aspects.mischief.speed_bonus", 0.05D);
        config.addDefault("inherit.transformation.aspects.flame.scale_multiplier", 2.1D);
        config.addDefault("inherit.transformation.aspects.flame.attack_bonus", 5.5D);
        config.addDefault("inherit.transformation.aspects.flame.speed_bonus", 0.04D);
        config.addDefault("inherit.transformation.aspects.forge.scale_multiplier", 2.35D);
        config.addDefault("inherit.transformation.aspects.forge.attack_bonus", 8.0D);
        config.addDefault("inherit.transformation.aspects.forge.speed_bonus", -0.01D);
        config.addDefault("goblin.triggers.power.boss_keywords", List.of("태초의 도깨비"));
        config.addDefault("goblin.triggers.speed.trace_items", List.of("SNOWBALL", "GOAT_HORN"));
        config.addDefault("goblin.triggers.mischief.contract_items", List.of("WRITTEN_BOOK", "WRITABLE_BOOK"));
        config.addDefault("goblin.triggers.mischief.contract_keywords", List.of("계약"));
        config.addDefault("goblin.triggers.flame.ritual_blocks", List.of("CAMPFIRE", "SOUL_CAMPFIRE"));
        config.addDefault("goblin.triggers.flame.catalyst", "BLAZE_POWDER");
        config.addDefault("goblin.triggers.forge.catalyst", "NETHERITE_INGOT");
        config.addDefault("goblin.triggers.forge.fuel", "BLAZE_ROD");
        config.addDefault("goblin.triggers.forge.station_blocks", List.of("SMITHING_TABLE", "ANVIL"));
        config.addDefault("goblin.triggers.forge.radius", 4);
        config.addDefault("squad.max_members", 5);
        config.addDefault("squad.friendly_fire", false);
        config.addDefault("movement.double_jump.enabled", true);
        config.addDefault("movement.double_jump.vertical_velocity", 0.9D);
        config.addDefault("movement.double_jump.forward_multiplier", 0.6D);
        config.options().copyDefaults(true);
        saveConfig();
    }

    private void loadDoubleJumpSettings() {
        FileConfiguration config = getConfig();
        doubleJumpEnabled = config.getBoolean("movement.double_jump.enabled", true);
        doubleJumpVerticalVelocity = config.getDouble("movement.double_jump.vertical_velocity", 0.9D);
        if (doubleJumpVerticalVelocity <= 0) {
            getLogger().warning("[Config] movement.double_jump.vertical_velocity 값은 0보다 커야 합니다. 기본값 0.9를 사용합니다.");
            doubleJumpVerticalVelocity = 0.9D;
        }

        doubleJumpForwardMultiplier = config.getDouble("movement.double_jump.forward_multiplier", 0.6D);
        if (doubleJumpForwardMultiplier < 0) {
            getLogger().warning("[Config] movement.double_jump.forward_multiplier 값은 음수가 될 수 없습니다. 기본값 0.6을 사용합니다.");
            doubleJumpForwardMultiplier = 0.6D;
        }
    }
}
