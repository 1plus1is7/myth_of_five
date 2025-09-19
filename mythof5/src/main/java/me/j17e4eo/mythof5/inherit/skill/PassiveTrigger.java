package me.j17e4eo.mythof5.inherit.skill;

import java.util.ArrayList;
import java.util.List;

/**
 * Conditional trigger metadata for passive skills.
 */
public final class PassiveTrigger {

    private final double healthThreshold;
    private final int cooldownTicks;
    private final List<SkillStatusEffect> statuses;
    private final String messageKey;

    private PassiveTrigger(Builder builder) {
        this.healthThreshold = builder.healthThreshold;
        this.cooldownTicks = builder.cooldownTicks;
        this.statuses = List.copyOf(builder.statuses);
        this.messageKey = builder.messageKey;
    }

    public double getHealthThreshold() {
        return healthThreshold;
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public List<SkillStatusEffect> getStatuses() {
        return statuses;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private double healthThreshold = 0.0D;
        private int cooldownTicks = 200;
        private final List<SkillStatusEffect> statuses = new ArrayList<>();
        private String messageKey = "";

        private Builder() {
        }

        public Builder healthThreshold(double value) {
            this.healthThreshold = value;
            return this;
        }

        public Builder cooldownTicks(int ticks) {
            this.cooldownTicks = ticks;
            return this;
        }

        public Builder addStatus(SkillStatusEffect effect) {
            this.statuses.add(effect);
            return this;
        }

        public Builder messageKey(String key) {
            this.messageKey = key;
            return this;
        }

        public PassiveTrigger build() {
            return new PassiveTrigger(this);
        }
    }
}
