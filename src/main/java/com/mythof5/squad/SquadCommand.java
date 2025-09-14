package com.mythof5.squad;

import com.mythof5.MythPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class SquadCommand implements CommandExecutor, TabCompleter {
    private final SquadManager manager;

    public SquadCommand(SquadManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only");
            return true;
        }
        Player player = (Player) sender;
        int maxMembers = MythPlugin.getInstance().getConfiguration().getInt("squad.max_members");
        if (args.length == 0) {
            sender.sendMessage("Usage: /squad <create|invite|accept|leave|disband|status>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "create":
                if (args.length < 2) {
                    sender.sendMessage("/squad create <name>");
                    return true;
                }
                if (!manager.createSquad(args[1], player, maxMembers)) {
                    sender.sendMessage("부대를 만들 수 없습니다.");
                }
                return true;
            case "invite":
                if (args.length < 2) {
                    sender.sendMessage("/squad invite <player>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("플레이어를 찾을 수 없습니다.");
                    return true;
                }
                if (!manager.invite(player, target)) {
                    sender.sendMessage("초대할 수 없습니다.");
                }
                return true;
            case "accept":
                if (args.length < 2) {
                    sender.sendMessage("/squad accept <squad>");
                    return true;
                }
                if (!manager.accept(player, args[1])) {
                    sender.sendMessage("초대를 찾을 수 없거나 인원이 가득 찼습니다.");
                }
                return true;
            case "leave":
                if (!manager.leave(player)) {
                    sender.sendMessage("부대에 속해있지 않습니다.");
                }
                return true;
            case "disband":
                if (!manager.disband(player)) {
                    sender.sendMessage("부대를 해산할 수 없습니다.");
                }
                return true;
            case "status":
                sender.sendMessage(manager.status(player));
                return true;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            list.add("create");
            list.add("invite");
            list.add("accept");
            list.add("leave");
            list.add("disband");
            list.add("status");
        }
        return list;
    }
}
