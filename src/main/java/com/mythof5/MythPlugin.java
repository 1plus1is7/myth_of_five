package com.mythof5;

import com.mythof5.boss.BossManager;
import com.mythof5.inherit.InheritanceListener;
import com.mythof5.squad.SquadCommand;
import com.mythof5.squad.SquadManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class MythPlugin extends JavaPlugin {
    private static MythPlugin instance;
    private BossManager bossManager;
    private SquadManager squadManager;

    public static MythPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        bossManager = new BossManager(this);
        squadManager = new SquadManager(this);

        getCommand("myth").setExecutor(new MythCommand(bossManager));
        SquadCommand squadCommand = new SquadCommand(squadManager);
        getCommand("squad").setExecutor(squadCommand);
        getCommand("squad").setTabCompleter(squadCommand);

        Bukkit.getPluginManager().registerEvents(new InheritanceListener(bossManager, squadManager), this);
    }

    @Override
    public void onDisable() {
        bossManager.clearBosses();
    }

    public FileConfiguration getConfiguration() {
        return getConfig();
    }

    public SquadManager getSquadManager() {
        return squadManager;
    }
}
