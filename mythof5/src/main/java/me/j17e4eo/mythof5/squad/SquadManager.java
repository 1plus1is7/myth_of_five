package me.j17e4eo.mythof5.squad;

import me.j17e4eo.mythof5.Mythof5;
import me.j17e4eo.mythof5.config.Messages;
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
import java.util.LinkedHashSet;
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
    private final Messages messages;
    private final Map<String, Squad> squads = new HashMap<>();
    private final Map<UUID, String> membership = new HashMap<>();
    private final Map<UUID, Set<String>> invites = new HashMap<>();
    private final int maxMembers;
    private File dataFile;
    private YamlConfiguration dataConfig;
    private File invitesFile;
    private YamlConfiguration invitesConfig;

    public SquadManager(Mythof5 plugin, Messages messages) {
        this.plugin = plugin;
        this.messages = messages;
        int configured = plugin.getConfig().getInt("squad.max_members", 5);
        if (configured < 1) {
            plugin.getLogger().warning("squad.max_members must be at least 1. Using default 5.");
            configured = 5;
        }
        this.maxMembers = configured;
    }

    public void load() {
        plugin.getDataFolder().mkdirs();
        squads.clear();
        membership.clear();
        invites.clear();
        dataFile = new File(plugin.getDataFolder(), "squads.yml");
        if (!dataFile.exists()) {
            dataConfig = new YamlConfiguration();
        } else {
            dataConfig = YamlConfiguration.loadConfiguration(dataFile);
            ConfigurationSection squadsSection = dataConfig.getConfigurationSection("squads");
            if (squadsSection != null) {
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
                    Squad squad = new Squad(section.getString("name", key), owner);
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
        }
        loadInvites();
    }

    public void save() {
        if (dataConfig == null) {
            dataConfig = new YamlConfiguration();
        }
        dataConfig.set("squads", null);
        ConfigurationSection squadsSection = dataConfig.createSection("squads");
        for (Squad squad : squads.values()) {
            String key = normalize(squad.getName());
            ConfigurationSection section = squadsSection.createSection(key);
            section.set("name", squad.getName());
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
            owner.sendMessage(Component.text(messages.format("squad.already_in_squad"), NamedTextColor.RED));
            return false;
        }
        String key = normalize(name);
        if (squads.containsKey(key)) {
            owner.sendMessage(Component.text(messages.format("squad.name_taken"), NamedTextColor.RED));
            return false;
        }
        Squad squad = new Squad(name, owner.getUniqueId());
        registerSquad(squad);
        save();
        saveInvites();
        owner.sendMessage(Component.text(messages.format("squad.create_success", Map.of("squad", squad.getName())), NamedTextColor.GREEN));
        plugin.broadcast(messages.format("broadcast.squad_created", Map.of("player", owner.getName(), "squad", squad.getName())));
        plugin.getLogger().info(String.format("[Event:SQUAD_CREATED] %s created squad %s", owner.getName(), name));
        return true;
    }

    public boolean invite(Player owner, Player target) {
        Optional<Squad> optional = getSquad(owner.getUniqueId());
        if (optional.isEmpty()) {
            owner.sendMessage(Component.text(messages.format("squad.create_first"), NamedTextColor.RED));
            return false;
        }
        Squad squad = optional.get();
        if (!squad.isOwner(owner.getUniqueId())) {
            owner.sendMessage(Component.text(messages.format("squad.only_owner_invite"), NamedTextColor.RED));
            return false;
        }
        if (membership.containsKey(target.getUniqueId())) {
            owner.sendMessage(Component.text(messages.format("squad.target_in_squad"), NamedTextColor.RED));
            return false;
        }
        if (squad.size() >= maxMembers) {
            owner.sendMessage(Component.text(messages.format("squad.full"), NamedTextColor.RED));
            return false;
        }
        invites.computeIfAbsent(target.getUniqueId(), uuid -> new HashSet<>()).add(normalize(squad.getName()));
        saveInvites();
        target.sendMessage(Component.text(messages.format("squad.invite_sent_target", Map.of(
                "owner", owner.getName(),
                "squad", squad.getName()
        )), NamedTextColor.GOLD));
        owner.sendMessage(Component.text(messages.format("squad.invite_sent_owner", Map.of(
                "player", target.getName(),
                "squad", squad.getName()
        )), NamedTextColor.GREEN));
        plugin.getLogger().info(String.format("[Event:SQUAD_INVITED] %s invited %s to squad %s", owner.getName(), target.getName(), squad.getName()));
        return true;
    }

    public boolean accept(Player player, String squadName) {
        String key = normalize(squadName);
        Set<String> pending = invites.get(player.getUniqueId());
        if (pending == null || !pending.contains(key)) {
            player.sendMessage(Component.text(messages.format("squad.no_invite"), NamedTextColor.RED));
            return false;
        }
        pending.remove(key);
        if (pending.isEmpty()) {
            invites.remove(player.getUniqueId());
        }
        Squad squad = squads.get(key);
        if (squad == null) {
            player.sendMessage(Component.text(messages.format("squad.not_found"), NamedTextColor.RED));
            return false;
        }
        if (squad.size() >= maxMembers) {
            player.sendMessage(Component.text(messages.format("squad.full"), NamedTextColor.RED));
            return false;
        }
        squad.addMember(player.getUniqueId());
        membership.put(player.getUniqueId(), key);
        save();
        saveInvites();
        player.sendMessage(Component.text(messages.format("squad.join_success", Map.of("squad", squad.getName())), NamedTextColor.GREEN));
        plugin.broadcast(messages.format("broadcast.squad_joined", Map.of("player", player.getName(), "squad", squad.getName())));
        plugin.getLogger().info(String.format("[Event:SQUAD_JOINED] %s joined squad %s", player.getName(), squad.getName()));
        return true;
    }

    public boolean leave(Player player) {
        UUID uuid = player.getUniqueId();
        Optional<Squad> optional = getSquad(uuid);
        if (optional.isEmpty()) {
            player.sendMessage(Component.text(messages.format("squad.no_membership"), NamedTextColor.RED));
            return false;
        }
        Squad squad = optional.get();
        if (squad.isOwner(uuid)) {
            player.sendMessage(Component.text(messages.format("squad.leave_owner_forbidden"), NamedTextColor.RED));
            return false;
        }
        if (!squad.removeMember(uuid)) {
            return false;
        }
        membership.remove(uuid);
        save();
        saveInvites();
        player.sendMessage(Component.text(messages.format("squad.leave_success", Map.of("squad", squad.getName())), NamedTextColor.GREEN));
        plugin.broadcast(messages.format("broadcast.squad_left", Map.of("player", player.getName(), "squad", squad.getName())));
        plugin.getLogger().info(String.format("[Event:SQUAD_LEFT] %s left squad %s", player.getName(), squad.getName()));
        return true;
    }

    public boolean disband(Player player) {
        UUID uuid = player.getUniqueId();
        Optional<Squad> optional = getSquad(uuid);
        if (optional.isEmpty()) {
            player.sendMessage(Component.text(messages.format("squad.no_membership"), NamedTextColor.RED));
            return false;
        }
        Squad squad = optional.get();
        if (!squad.isOwner(uuid)) {
            player.sendMessage(Component.text(messages.format("squad.only_owner_disband"), NamedTextColor.RED));
            return false;
        }
        unregisterSquad(squad);
        save();
        saveInvites();
        player.sendMessage(Component.text(messages.format("squad.disband_success", Map.of("squad", squad.getName())), NamedTextColor.GREEN));
        plugin.broadcast(messages.format("broadcast.squad_disbanded", Map.of("squad", squad.getName())));
        plugin.getLogger().info(String.format("[Event:SQUAD_DISBANDED] %s disbanded squad %s", player.getName(), squad.getName()));
        return true;
    }

    public void sendStatus(Player player) {
        Optional<Squad> optional = getSquad(player.getUniqueId());
        if (optional.isEmpty()) {
            player.sendMessage(Component.text(messages.format("squad.no_membership"), NamedTextColor.GRAY));
            return;
        }
        Squad squad = optional.get();
        long onlineCount = squad.getMembers().stream().map(Bukkit::getPlayer).filter(Objects::nonNull).count();
        String ownerName = resolveName(squad.getOwner());
        List<String> memberNames = squad.getMembers().stream().map(this::resolveName).collect(Collectors.toList());
        player.sendMessage(Component.text(messages.format("squad.status_header"), NamedTextColor.GOLD));
        player.sendMessage(Component.text(messages.format("squad.status_name", Map.of("squad", squad.getName())), NamedTextColor.GREEN));
        player.sendMessage(Component.text(messages.format("squad.status_owner", Map.of("owner", ownerName)), NamedTextColor.GREEN));
        player.sendMessage(Component.text(messages.format("squad.status_count", Map.of(
                "count", String.valueOf(squad.size()),
                "max", String.valueOf(maxMembers),
                "online", String.valueOf(onlineCount)
        )), NamedTextColor.GREEN));
        player.sendMessage(Component.text(messages.format("squad.status_members", Map.of("members", String.join(", ", memberNames))), NamedTextColor.GREEN));
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
        Set<String> pending = invites.get(uuid);
        if (pending == null || pending.isEmpty()) {
            return Set.of();
        }
        return pending.stream()
                .map(squads::get)
                .filter(Objects::nonNull)
                .map(Squad::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
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

    private void loadInvites() {
        invitesFile = new File(plugin.getDataFolder(), "invites.yml");
        if (!invitesFile.exists()) {
            invitesConfig = new YamlConfiguration();
            return;
        }
        invitesConfig = YamlConfiguration.loadConfiguration(invitesFile);
        ConfigurationSection section = invitesConfig.getConfigurationSection("invites");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(key);
                List<String> squadNames = section.getStringList(key);
                Set<String> normalized = new HashSet<>();
                for (String squadName : squadNames) {
                    String normalizedKey = normalize(squadName);
                    if (squads.containsKey(normalizedKey)) {
                        normalized.add(normalizedKey);
                    }
                }
                if (!normalized.isEmpty()) {
                    invites.put(playerId, normalized);
                }
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Invalid player UUID in invites.yml: " + key);
            }
        }
    }

    public void saveInvites() {
        if (invitesConfig == null) {
            invitesConfig = new YamlConfiguration();
        }
        invites.entrySet().removeIf(entry -> {
            Set<String> pending = entry.getValue();
            pending.removeIf(name -> !squads.containsKey(name));
            return pending.isEmpty();
        });
        invitesConfig.set("invites", null);
        ConfigurationSection section = invitesConfig.createSection("invites");
        for (Map.Entry<UUID, Set<String>> entry : invites.entrySet()) {
            Set<String> pending = entry.getValue();
            if (pending.isEmpty()) {
                continue;
            }
            List<String> squadsList = pending.stream()
                    .map(squads::get)
                    .filter(Objects::nonNull)
                    .map(Squad::getName)
                    .collect(Collectors.toList());
            if (squadsList.isEmpty()) {
                continue;
            }
            section.set(entry.getKey().toString(), squadsList);
        }
        if (invitesFile == null) {
            invitesFile = new File(plugin.getDataFolder(), "invites.yml");
        }
        try {
            invitesConfig.save(invitesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save squad invites: " + e.getMessage());
        }
    }
}
