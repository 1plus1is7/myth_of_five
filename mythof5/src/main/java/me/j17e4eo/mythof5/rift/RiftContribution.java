package me.j17e4eo.mythof5.rift;

public final class RiftContribution {
    private double damage;
    private double support;
    private int mechanics;

    public void addDamage(double amount) {
        if (amount > 0) {
            damage += amount;
        }
    }

    public void addSupport(double amount) {
        if (amount > 0) {
            support += amount;
        }
    }

    public void addMechanic() {
        mechanics++;
    }

    public double getDamage() {
        return damage;
    }

    public double getSupport() {
        return support;
    }

    public int getMechanics() {
        return mechanics;
    }

    public double getScore() {
        double mechanicScore = mechanics * 25.0D;
        double supportScore = support * 0.75D;
        return damage + mechanicScore + supportScore;
    }
}
