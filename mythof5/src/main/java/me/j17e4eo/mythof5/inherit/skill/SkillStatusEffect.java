package me.j17e4eo.mythof5.inherit.skill;

/**
 * A simple descriptor of a status effect applied by a goblin skill.
 */
public final class SkillStatusEffect {

    private final SkillStatusType type;
    private final int durationTicks;
    private final int amplifier;

    public SkillStatusEffect(SkillStatusType type, int durationTicks, int amplifier) {
        this.type = type;
        this.durationTicks = durationTicks;
        this.amplifier = amplifier;
    }

    public SkillStatusType getType() {
        return type;
    }

    public int getDurationTicks() {
        return durationTicks;
    }

    public int getAmplifier() {
        return amplifier;
    }
}
