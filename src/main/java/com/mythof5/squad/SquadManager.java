package com.mythof5.squad;

import com.mythof5.MythPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class SquadManager {
    private final Map<String, Squad> squads = new HashMap<>();
    private final Map<UUID, Squad> membership = new HashMap<>();
    private final Map<UUID, String> invites = new HashMap<>();
    private final MythPlugin plugin;

    public SquadManager(MythPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean createSquad(String name, Player owner, int maxMembers) {
        if (squads.containsKey(name) || membership.containsKey(owner.getUniqueId())) return false;
        Squad squad = new Squad(name, owner.getUniqueId(), maxMembers);
        squad.members.add(owner.getUniqueId());
        squads.put(name, squad);
        membership.put(owner.getUniqueId(), squad);
        Bukkit.broadcastMessage(owner.getName() + "가 부대 " + name + "를 생성했습니다.");
        plugin.getLogger().info("SQUAD_CREATED name=" + name);
        return true;
    }

    public boolean invite(Player sender, Player target) {
        Squad squad = membership.get(sender.getUniqueId());
        if (squad == null || !squad.owner.equals(sender.getUniqueId())) return false;
        invites.put(target.getUniqueId(), squad.name);
        target.sendMessage(sender.getName() + "가 당신을 부대에 초대했습니다. /squad accept " + squad.name + " 로 수락");
        plugin.getLogger().info("SQUAD_INVITED from=" + sender.getName() + " to=" + target.getName());
        return true;
    }

    public boolean accept(Player player, String squadName) {
        String invite = invites.get(player.getUniqueId());
        Squad squad = squads.get(squadName);
        if (invite == null || !invite.equals(squadName) || squad == null) return false;
        if (squad.members.size() >= squad.maxMembers) return false;
        squad.members.add(player.getUniqueId());
        membership.put(player.getUniqueId(), squad);
        invites.remove(player.getUniqueId());
        Bukkit.broadcastMessage(player.getName() + "가 부대 " + squad.name + "에 합류했습니다.");
        plugin.getLogger().info("SQUAD_JOINED player=" + player.getName() + " squad=" + squad.name);
        return true;
    }

    public boolean leave(Player player) {
        Squad squad = membership.get(player.getUniqueId());
        if (squad == null) return false;
        if (squad.owner.equals(player.getUniqueId())) {
            disband(player);
            return true;
        }
        squad.members.remove(player.getUniqueId());
        membership.remove(player.getUniqueId());
        Bukkit.broadcastMessage(player.getName() + "가 부대 " + squad.name + "에서 탈퇴했습니다.");
        plugin.getLogger().info("SQUAD_LEFT player=" + player.getName() + " squad=" + squad.name);
        return true;
    }

    public boolean disband(Player player) {
        Squad squad = membership.get(player.getUniqueId());
        if (squad == null || !squad.owner.equals(player.getUniqueId())) return false;
        for (UUID uuid : squad.members) {
            membership.remove(uuid);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage("부대 " + squad.name + "가 해산되었습니다.");
            }
        }
        squads.remove(squad.name);
        Bukkit.broadcastMessage("부대 " + squad.name + "가 해산되었습니다.");
        plugin.getLogger().info("SQUAD_DISBANDED name=" + squad.name);
        return true;
    }

    public String status(Player player) {
        Squad squad = membership.get(player.getUniqueId());
        if (squad == null) return "당신은 어떤 부대에도 속해있지 않습니다.";
        StringBuilder sb = new StringBuilder();
        sb.append("부대 ").append(squad.name).append(" 멤버: ");
        for (UUID uuid : squad.members) {
            Player p = Bukkit.getPlayer(uuid);
            sb.append(p != null && p.isOnline() ? p.getName() : Bukkit.getOfflinePlayer(uuid).getName()).append(" ");
        }
        sb.append("(온라인 ").append(squad.onlineCount()).append("/").append(squad.members.size()).append(")");
        return sb.toString();
    }

    public boolean sameSquad(UUID a, UUID b) {
        Squad sa = membership.get(a);
        Squad sb = membership.get(b);
        return sa != null && sa == sb;
    }

    private static class Squad {
        final String name;
        final UUID owner;
        final Set<UUID> members = new HashSet<>();
        final int maxMembers;

        Squad(String name, UUID owner, int maxMembers) {
            this.name = name;
            this.owner = owner;
            this.maxMembers = maxMembers;
        }

        int onlineCount() {
            int c = 0;
            for (UUID uuid : members) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) c++;
            }
            return c;
        }
    }
}
