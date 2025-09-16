package me.j17e4eo.mythof5;

import me.j17e4eo.mythof5.boss.BossManager;
import me.j17e4eo.mythof5.command.GuildCommand;
import me.j17e4eo.mythof5.command.MythAdminCommand;
import me.j17e4eo.mythof5.inherit.InheritManager;
import me.j17e4eo.mythof5.listener.BossListener;
import me.j17e4eo.mythof5.listener.PlayerListener;
import me.j17e4eo.mythof5.listener.SquadListener;
import me.j17e4eo.mythof5.squad.SquadManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class Mythof5 extends JavaPlugin {

    private BossManager bossManager;
    private InheritManager inheritManager;
    private SquadManager squadManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        inheritManager = new InheritManager(this);
        inheritManager.load();

        bossManager = new BossManager(this, inheritManager);
        squadManager = new SquadManager(this);
        squadManager.load();

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new BossListener(this, bossManager), this);
        pluginManager.registerEvents(new PlayerListener(bossManager, inheritManager), this);
        pluginManager.registerEvents(new SquadListener(squadManager, getConfig().getBoolean("squad.friendly_fire", false)), this);

        registerCommands();

        inheritManager.reapplyToOnlinePlayers();
        bossManager.initializeBossBars();
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
        }
    }

    private void registerCommands() {
        PluginCommand mythCommand = Objects.requireNonNull(getCommand("myth"), "Command myth not defined in plugin.yml");
        MythAdminCommand mythAdminCommand = new MythAdminCommand(this, bossManager);
        mythCommand.setExecutor(mythAdminCommand);
        mythCommand.setTabCompleter(mythAdminCommand);

        PluginCommand guildCommand = Objects.requireNonNull(getCommand("guild"), "Command guild not defined in plugin.yml");
        GuildCommand squadCommand = new GuildCommand(squadManager);
        guildCommand.setExecutor(squadCommand);
        guildCommand.setTabCompleter(squadCommand);
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

    public void broadcast(String message) {
        Component component = Component.text().append(Component.text("「", NamedTextColor.GOLD))
                .append(Component.text(message, NamedTextColor.GOLD))
                .append(Component.text("」", NamedTextColor.GOLD))
                .build();
        Bukkit.broadcast(component);
    }
}
