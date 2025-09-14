package com.mythof5;

import com.mythof5.boss.BossManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MythCommand implements CommandExecutor {
    private final BossManager bossManager;

    public MythCommand(BossManager bossManager) {
        this.bossManager = bossManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1 || !args[0].equalsIgnoreCase("admin")) {
            return false;
        }
        if (!sender.hasPermission("myth.admin")) {
            sender.sendMessage("No permission");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /myth admin <spawnboss|bosslist|endboss>");
            return true;
        }
        String sub = args[1];
        switch (sub.toLowerCase()) {
            case "spawnboss":
                if (args.length < 9) {
                    sender.sendMessage("Usage: /myth admin spawnboss <name> <hp> <armor> <world> <x> <y> <z>");
                    return true;
                }
                String name = args[2];
                double hp = Double.parseDouble(args[3]);
                double armor = Double.parseDouble(args[4]);
                World world = Bukkit.getWorld(args[5]);
                double x = Double.parseDouble(args[6]);
                double y = Double.parseDouble(args[7]);
                double z = Double.parseDouble(args[8]);
                if (world == null) {
                    sender.sendMessage("World not found");
                    return true;
                }
                int id = bossManager.spawnBoss(name, hp, armor, world, x, y, z);
                sender.sendMessage("Spawned boss id " + id);
                return true;
            case "bosslist":
                bossManager.getBosses().forEach((i, info) -> sender.sendMessage(i + ": " + info.entity.getType() + " at " + info.entity.getLocation()));
                return true;
            case "endboss":
                if (args.length < 3) {
                    sender.sendMessage("Usage: /myth admin endboss <id>");
                    return true;
                }
                int bid = Integer.parseInt(args[2]);
                if (bossManager.endBoss(bid)) {
                    sender.sendMessage("Boss removed");
                } else {
                    sender.sendMessage("Boss not found");
                }
                return true;
        }
        return true;
    }
}
