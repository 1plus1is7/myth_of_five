package me.j17e4eo.mythof5.rift.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

public final class RiftRewardEntry {
    private final Material material;
    private final int minAmount;
    private final int maxAmount;
    private final double weight;
    private final String command;

    public RiftRewardEntry(Material material, int minAmount, int maxAmount, double weight, String command) {
        this.material = material;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.weight = weight;
        this.command = command;
    }

    public Material getMaterial() {
        return material;
    }

    public int getMinAmount() {
        return minAmount;
    }

    public int getMaxAmount() {
        return maxAmount;
    }

    public double getWeight() {
        return weight;
    }

    public String getCommand() {
        return command;
    }

    public static RiftRewardEntry fromSection(ConfigurationSection section) {
        String type = section.getString("type", "STONE");
        Material material = Material.matchMaterial(type);
        if (material == null && (section.getString("command") == null || section.getString("command").isBlank())) {
            throw new IllegalArgumentException("Unknown material in rewards entry: " + type);
        }
        int min = Math.max(1, section.getInt("min", 1));
        int max = Math.max(min, section.getInt("max", min));
        double weight = Math.max(0.0D, section.getDouble("weight", 1.0D));
        String command = section.getString("command");
        return new RiftRewardEntry(material, min, max, command == null ? weight : weight, command);
    }

    public boolean isCommand() {
        return command != null && !command.isBlank();
    }

    @Override
    public String toString() {
        return "RiftRewardEntry{" +
                "material=" + material +
                ", minAmount=" + minAmount +
                ", maxAmount=" + maxAmount +
                ", weight=" + weight +
                ", command='" + command + '\'' +
                '}';
    }
}
