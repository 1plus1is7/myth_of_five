package me.j17e4eo.mythof5.rift.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RiftRewardTable {
    private final List<RiftRewardEntry> personal;
    private final List<RiftRewardEntry> party;
    private final List<RiftRewardEntry> weekly;

    public RiftRewardTable(List<RiftRewardEntry> personal, List<RiftRewardEntry> party, List<RiftRewardEntry> weekly) {
        this.personal = List.copyOf(personal);
        this.party = List.copyOf(party);
        this.weekly = List.copyOf(weekly);
    }

    public List<RiftRewardEntry> getPersonal() {
        return personal;
    }

    public List<RiftRewardEntry> getParty() {
        return party;
    }

    public List<RiftRewardEntry> getWeekly() {
        return weekly;
    }

    public static RiftRewardTable empty() {
        return new RiftRewardTable(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    public static List<RiftRewardEntry> copyList(List<RiftRewardEntry> entries) {
        return new ArrayList<>(entries);
    }
}
