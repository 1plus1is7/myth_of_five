package me.j17e4eo.mythof5.inherit.skilltree;

/**
 * Immutable descriptor of a selectable skill upgrade node.
 */
public final class SkillUpgrade {

    private final String id;
    private final String name;
    private final String description;
    private final int cost;

    public SkillUpgrade(String id, String name, String description, int cost) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.cost = cost;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getCost() {
        return cost;
    }
}
