package me.j17e4eo.mythof5.inherit.skill;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Declarative metadata that accompanies a goblin skill. It is used to drive
 * combat resolution, status effect application and visual cues.
 */
public final class SkillSpec {

    private final double baseDamage;
    private final double range;
    private final int durationTicks;
    private final SkillHitbox hitbox;
    private final List<SkillStatusEffect> statuses;
    private final Set<SkillEnvironmentTag> environmentTags;

    private SkillSpec(Builder builder) {
        this.baseDamage = builder.baseDamage;
        this.range = builder.range;
        this.durationTicks = builder.durationTicks;
        this.hitbox = builder.hitbox;
        this.statuses = List.copyOf(builder.statuses);
        this.environmentTags = EnumSet.copyOf(builder.environmentTags);
    }

    public double getBaseDamage() {
        return baseDamage;
    }

    public double getRange() {
        return range;
    }

    public int getDurationTicks() {
        return durationTicks;
    }

    public SkillHitbox getHitbox() {
        return hitbox;
    }

    public List<SkillStatusEffect> getStatuses() {
        return statuses;
    }

    public Set<SkillEnvironmentTag> getEnvironmentTags() {
        return environmentTags;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private double baseDamage;
        private double range = 3.0D;
        private int durationTicks = 0;
        private SkillHitbox hitbox = SkillHitbox.SINGLE;
        private final List<SkillStatusEffect> statuses = new ArrayList<>();
        private final EnumSet<SkillEnvironmentTag> environmentTags = EnumSet.noneOf(SkillEnvironmentTag.class);

        private Builder() {
        }

        public Builder baseDamage(double value) {
            this.baseDamage = value;
            return this;
        }

        public Builder range(double value) {
            this.range = value;
            return this;
        }

        public Builder durationTicks(int ticks) {
            this.durationTicks = ticks;
            return this;
        }

        public Builder hitbox(SkillHitbox hitbox) {
            this.hitbox = hitbox;
            return this;
        }

        public Builder addStatus(SkillStatusEffect effect) {
            this.statuses.add(effect);
            return this;
        }

        public Builder addEnvironmentTag(SkillEnvironmentTag tag) {
            this.environmentTags.add(tag);
            return this;
        }

        public SkillSpec build() {
            return new SkillSpec(this);
        }
    }
}
