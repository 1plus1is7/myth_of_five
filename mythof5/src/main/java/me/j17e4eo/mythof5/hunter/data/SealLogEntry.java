package me.j17e4eo.mythof5.hunter.data;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Historical snapshot of a seal integrity change.
 */
public class SealLogEntry {

    private final Instant timestamp;
    private final double before;
    private final double after;
    private final String action;
    private final double roll;
    private final boolean release;

    public SealLogEntry(Instant timestamp, double before, double after, String action, double roll, boolean release) {
        this.timestamp = timestamp;
        this.before = before;
        this.after = after;
        this.action = action;
        this.roll = roll;
        this.release = release;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public double getBefore() {
        return before;
    }

    public double getAfter() {
        return after;
    }

    public String getAction() {
        return action;
    }

    public double getRoll() {
        return roll;
    }

    public boolean isRelease() {
        return release;
    }

    public Map<String, Object> serialize() {
        Map<String, Object> data = new HashMap<>();
        data.put("time", timestamp.toString());
        data.put("before", before);
        data.put("after", after);
        data.put("action", action);
        data.put("roll", roll);
        data.put("release", release);
        return data;
    }

    public static SealLogEntry deserialize(Map<?, ?> raw) {
        if (raw == null) {
            return null;
        }
        try {
            Instant time = Instant.parse(String.valueOf(raw.get("time")));
            double before = Double.parseDouble(String.valueOf(raw.get("before")));
            double after = Double.parseDouble(String.valueOf(raw.get("after")));
            String action = String.valueOf(raw.get("action"));
            double roll = raw.get("roll") != null ? Double.parseDouble(String.valueOf(raw.get("roll"))) : 0.0D;
            boolean release = Boolean.parseBoolean(String.valueOf(raw.get("release")));
            return new SealLogEntry(time, before, after, action, roll, release);
        } catch (Exception ex) {
            return null;
        }
    }
}
