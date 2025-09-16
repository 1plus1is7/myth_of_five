package me.j17e4eo.mythof5.squad;

import me.j17e4eo.mythof5.Mythof5;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class SquadManager {

    private final Mythof5 plugin;
    private final Map<String, Squad> squads = new HashMap<>();
    private final Map<UUID, String> membership = new HashMap<>();
    private final Map<UUID, Set<String>> invites = new HashMap<>();
    private final int maxMembers;
    private File dataFile;
    private YamlConfiguration dataConfig;

    public SquadManager(Mythof5 plugin) {
        this.plugin = plugin;
        this.maxMembers = plugin.getConfig().getInt("squad.max_members", 5);
    }

    public void load() {
        plugin.getDataFolder().mkdirs();
        dataFile = new File(plugin.getDataFolder(), "squads.yml");
        if (!dataFile.exists()) {
            dataConfig = new YamlConfiguration();
            return;
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection squadsSection = dataConfig.getConfigurationSection("squads");
        if (squadsSection == null) {
            return;
        }
        for (String key : squadsSection.getKeys(false)) {
            ConfigurationSection section = squadsSection.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            String ownerId = section.getString("owner");
            if (ownerId == null) {
                continue;
            }
            UUID owner;
            try {
                owner = UUID.fromString(ownerId);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Invalid owner UUID for squad " + key);
                continue;
            }
            Squad squad = new Squad(key, owner);
            List<String> memberIds = section.getStringList("members");
            for (String memberId : memberIds) {
                try {
                    UUID uuid = UUID.fromString(memberId);
                    squad.addMember(uuid);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Invalid member UUID in squad " + key + ": " + memberId);
                }
            }
            registerSquad(squad);
        }
    }

    public void save() {
        if (dataConfig == null) {
            dataConfig = new YamlConfiguration();
        }
        dataConfig.set("squads", null);
        ConfigurationSection squadsSection = dataConfig.createSection("squads");
        for (Squad squad : squads.values()) {
            ConfigurationSection section = squadsSection.createSection(squad.getName());
            section.set("owner", squad.getOwner().toString());
            List<String> members = new ArrayList<>();
            for (UUID uuid : squad.getMembers()) {
                members.add(uuid.toString());
            }
            section.set("members", members);
        }
        if (dataFile == null) {
            dataFile = new File(plugin.getDataFolder(), "squads.yml");
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save squad data: " + e.getMessage());
        }
    }

    public boolean createSquad(Player owner, String name) {
        if (membership.containsKey(owner.getUniqueId())) {
            owner.sendMessage(Component.text("이미 다른 부대에 소속되어 있습니다.", NamedTextColor.RED));
            return false;
        }
        String key = normalize(name);
        if (squads.containsKey(key)) {
            owner.sendMessage(Component.text("이미 존재하는 부대 이름입니다.", NamedTextColor.RED));
            return false;
        }
        Squad squad = new Squad(name, owner.getUniqueId());
        registerSquad(squad);
        save();
        plugin.broadcast("[방송] " + owner.getName() + "가 부대 " + name + "을(를) 창설했습니다.");
        plugin.getLogger().info(String.format("[Event:SQUAD_CREATED] %s created squad %s", owner.getName(), name));
        return true;
    }

    public boolean invite(Player owner, Player target) {
        Optional<Squad> optional = getSquad(owner.getUniqueId());
        if (optional.isEmpty()) {
            owner.sendMessage(Component.text("부대를 먼저 생성해야 합니다.", NamedTextColor.RED));
            return false;
        }
        Squad squad = optional.get();
        if (!squad.isOwner(owner.getUniqueId())) {
            owner.sendMessage(Component.text("부대장만 초대할 수 있습니다.", NamedTextColor.RED));
            return false;
        }
        if (membership.containsKey(target.getUniqueId())) {
            owner.sendMessage(Component.text("해당 플레이어는 이미 다른 부대에 소속되어 있습니다.", NamedTextColor.RED));
            return false;
        }
        if (squad.size() >= maxMembers) {
            owner.sendMessage(Component.text("부대 인원이 가득 찼습니다.", NamedTextColor.RED));
            return false;
        }
        invites.computeIfAbsent(target.getUniqueId(), uuid -> new HashSet<>()).add(normalize(squad.getName()));
        target.sendMessage(Component.text(owner.getName() + "가 " + target.getName() + "를 부대에 초대했습니다. /guild accept " + squad.getName() + " 로 수락", NamedTextColor.GOLD));
        owner.sendMessage(Component.text(target.getName() + "에게 초대장을 보냈습니다.", NamedTextColor.GREEN));
        plugin.getLogger().info(String.format("[Event:SQUAD_INVITED] %s invited %s to squad %s", owner.getName(), target.getName(), squad.getName()));
        return true;
    }

    public boolean accept(Player player, String squadName) {
        String key = normalize(squadName);
        Set<String> pending = invites.get(player.getUniqueId());
        if (pending == null || !pending.contains(key)) {
            player.sendMessage(Component.text("해당 부대의 초대가 없습니다.", NamedTextColor.RED));
            return false;
        }
        pending.remove(key);
        if (pending.isEmpty()) {
            invites.remove(player.getUniqueId());
        }
        Squad squad = squads.get(key);
        if (squad == null) {
            player.sendMessage(Component.text("해당 부대는 존재하지 않습니다.", NamedTextColor.RED));
            return false;
        }
        if (squad.size() >= maxMembers) {
            player.sendMessage(Component.text("부대 인원이 가득 찼습니다.", NamedTextColor.RED));
            return false;
        }
        squad.addMember(player.getUniqueId());
        membership.put(player.getUniqueId(), key);
        save();
        plugin.broadcast("[방송] " + player.getName() + "가 부대 " + squad.getName() + "에 합류했습니다.");
        plugin.getLogger().info(String.format("[Event:SQUAD_JOINED] %s joined squad %s", player.getName(), squad.getName()));
        return true;
    }

    public boolean leave(Player player) {
        UUID uuid = player.getUniqueId();
        Optional<Squad> optional = getSquad(uuid);
        if (optional.isEmpty()) {
            player.sendMessage(Component.text("소속된 부대가 없습니다.", NamedTextColor.RED));
            return false;
        }
        Squad squad = optional.get();
        if (squad.isOwner(uuid)) {
            player.sendMessage(Component.text("부대장은 /guild disband 로 해산해야 합니다.", NamedTextColor.RED));
            return false;
        }
        if (!squad.removeMember(uuid)) {
            return false;
        }
        membership.remove(uuid);
        save();
        plugin.broadcast("[방송] " + player.getName() + "가 부대 " + squad.getName() + "에서 탈퇴했습니다.");
        plugin.getLogger().info(String.format("[Event:SQUAD_LEFT] %s left squad %s", player.getName(), squad.getName()));
        return true;
    }

    public boolean disband(Player player) {
        UUID uuid = player.getUniqueId();
        Optional<Squad> optional = getSquad(uuid);
        if (optional.isEmpty()) {
            player.sendMessage(Component.text("소속된 부대가 없습니다.", NamedTextColor.RED));
            return false;
        }
        Squad squad = optional.get();
        if (!squad.isOwner(uuid)) {
            player.sendMessage(Component.text("부대장만 해산할 수 있습니다.", NamedTextColor.RED));
            return false;
        }
        unregisterSquad(squad);
        save();
        plugin.broadcast("[방송] 부대 " + squad.getName() + "이(가) 해산되었습니다.");
        plugin.getLogger().info(String.format("[Event:SQUAD_DISBANDED] %s disbanded squad %s", player.getName(), squad.getName()));
        return true;
    }

    public void sendStatus(Player player) {
        Optional<Squad> optional = getSquad(player.getUniqueId());
        if (optional.isEmpty()) {
            player.sendMessage(Component.text("소속된 부대가 없습니다.", NamedTextColor.GRAY));
            return;
        }
        Squad squad = optional.get();
        long onlineCount = squad.getMembers().stream().map(Bukkit::getPlayer).filter(Objects::nonNull).count();
        String ownerName = resolveName(squad.getOwner());
        List<String> memberNames = squad.getMembers().stream().map(this::resolveName).collect(Collectors.toList());
        player.sendMessage(Component.text("=== 부대 상태 ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("부대명: " + squad.getName(), NamedTextColor.GREEN));
        player.sendMessage(Component.text("부대장: " + ownerName, NamedTextColor.GREEN));
        player.sendMessage(Component.text(String.format("인원: %d/%d (온라인 %d)", squad.size(), maxMembers, onlineCount), NamedTextColor.GREEN));
        player.sendMessage(Component.text("부대원: " + String.join(", ", memberNames), NamedTextColor.GREEN));
    }

    public Optional<Squad> getSquad(UUID uuid) {
        String key = membership.get(uuid);
        if (key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(squads.get(key));
    }

    public boolean isSameSquad(UUID left, UUID right) {
        if (left == null || right == null) {
            return false;
        }
        String a = membership.get(left);
        if (a == null) {
            return false;
        }
        return a.equals(membership.get(right));
    }

    public Set<String> getInvites(UUID uuid) {
        return invites.getOrDefault(uuid, Set.of());
    }

    private void registerSquad(Squad squad) {
        String key = normalize(squad.getName());
        squads.put(key, squad);
        for (UUID member : squad.getMembers()) {
            membership.put(member, key);
        }
    }

    private void unregisterSquad(Squad squad) {
        String key = normalize(squad.getName());
        squads.remove(key);
        for (UUID member : new ArrayList<>(squad.getMembers())) {
            membership.remove(member);
        }
        invites.values().forEach(pending -> pending.remove(key));
    }

    private String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private String resolveName(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            return player.getName();
        }
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        if (offlinePlayer.getName() != null) {
            return offlinePlayer.getName();
        }
        return uuid.toString();
    }
}
