package me.j17e4eo.mythof5;

import me.j17e4eo.mythof5.boss.BossManager;
import me.j17e4eo.mythof5.command.SquadCommand;
import me.j17e4eo.mythof5.command.MythAdminCommand;
import me.j17e4eo.mythof5.config.Messages;
import me.j17e4eo.mythof5.inherit.InheritManager;
import me.j17e4eo.mythof5.listener.BossListener;
import me.j17e4eo.mythof5.listener.PlayerListener;
import me.j17e4eo.mythof5.listener.SquadListener;
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
    private boolean doubleJumpEnabled;
    private double doubleJumpVerticalVelocity;
    private double doubleJumpForwardMultiplier;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        registerConfigDefaults();
        loadDoubleJumpSettings();

        messages = new Messages(this);

        inheritManager = new InheritManager(this, messages);
        inheritManager.load();

        bossManager = new BossManager(this, inheritManager, messages);
        squadManager = new SquadManager(this, messages);
        squadManager.load();

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new BossListener(this, bossManager), this);
        PlayerListener playerListener = new PlayerListener(bossManager, inheritManager,
                doubleJumpEnabled, doubleJumpVerticalVelocity, doubleJumpForwardMultiplier);
        pluginManager.registerEvents(playerListener, this);
        pluginManager.registerEvents(new SquadListener(squadManager, getConfig().getBoolean("squad.friendly_fire", false), messages), this);

        registerCommands();

        inheritManager.reapplyToOnlinePlayers();
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
        if (squadManager != null) {
            squadManager.save();
            squadManager.saveInvites();
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
        MythAdminCommand mythAdminCommand = new MythAdminCommand(this, bossManager, messages);
        mythCommand.setExecutor(mythAdminCommand);
        mythCommand.setTabCompleter(mythAdminCommand);

        PluginCommand squadCommand = Objects.requireNonNull(getCommand("squad"), "Command squad not defined in plugin.yml");
        SquadCommand squadExecutor = new SquadCommand(squadManager, messages);
        squadCommand.setExecutor(squadExecutor);
        squadCommand.setTabCompleter(squadExecutor);
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
        config.addDefault("inherit.buffs", List.of("speed:1", "strength:1"));
        config.addDefault("inherit.announce", true);
        config.addDefault("inherit.transfer_on_pvp_death", false);
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
