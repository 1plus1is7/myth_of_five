package me.j17e4eo.mythof5.hunter.event;

import me.j17e4eo.mythof5.hunter.data.Artifact;
import me.j17e4eo.mythof5.hunter.data.SealLogEntry;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a hunter artifact breaks its seal and releases a dokkaebi.
 */
public class HunterReleaseEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player hunter;
    private final Artifact artifact;
    private final double chance;
    private final double roll;
    private final SealLogEntry logEntry;

    public HunterReleaseEvent(Player hunter, Artifact artifact, double chance, double roll, SealLogEntry logEntry) {
        this.hunter = hunter;
        this.artifact = artifact;
        this.chance = chance;
        this.roll = roll;
        this.logEntry = logEntry;
    }

    public Player getHunter() {
        return hunter;
    }

    public Artifact getArtifact() {
        return artifact;
    }

    public double getChance() {
        return chance;
    }

    public double getRoll() {
        return roll;
    }

    public SealLogEntry getLogEntry() {
        return logEntry;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
