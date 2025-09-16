package me.j17e4eo.mythof5.squad;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.UUID;

public class Squad {

    private final String name;
    private final UUID owner;
    private final LinkedHashSet<UUID> members = new LinkedHashSet<>();

    public Squad(String name, UUID owner) {
        this.name = name;
        this.owner = owner;
        this.members.add(owner);
    }

    public String getName() {
        return name;
    }

    public UUID getOwner() {
        return owner;
    }

    public boolean isOwner(UUID uuid) {
        return owner.equals(uuid);
    }

    public boolean addMember(UUID uuid) {
        return members.add(uuid);
    }

    public boolean removeMember(UUID uuid) {
        if (owner.equals(uuid)) {
            return false;
        }
        return members.remove(uuid);
    }

    public boolean isMember(UUID uuid) {
        return members.contains(uuid);
    }

    public int size() {
        return members.size();
    }

    public Collection<UUID> getMembers() {
        return Collections.unmodifiableCollection(members);
    }

    public void clearMembers() {
        members.clear();
    }

    public void addAllMembers(Collection<UUID> uuids) {
        members.addAll(uuids);
    }
}
