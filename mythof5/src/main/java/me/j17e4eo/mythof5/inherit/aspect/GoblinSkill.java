package me.j17e4eo.mythof5.inherit.aspect;

import me.j17e4eo.mythof5.inherit.skill.PassiveTrigger;
import me.j17e4eo.mythof5.inherit.skill.SkillSpec;

import java.util.Locale;
import java.util.Optional;

/**
 * Immutable description of a goblin skill. Behaviour is provided by the
 * {@link me.j17e4eo.mythof5.inherit.AspectManager} which interprets the skill
 * identifiers at runtime.
 */
public final class GoblinSkill {

    private final String key;
    private final String displayName;
    private final GoblinSkillCategory category;
    private final int baseCooldownSeconds;
    private final double sharedCooldownMultiplier;
    private final double sharedEffectiveness;
    private final String description;
    private final String sharedDescription;
    private final SkillSpec spec;
    private final PassiveTrigger passiveTrigger;

    public GoblinSkill(String key, String displayName, GoblinSkillCategory category,
                       int baseCooldownSeconds, double sharedCooldownMultiplier,
                       double sharedEffectiveness, String description,
                       String sharedDescription) {
        this(key, displayName, category, baseCooldownSeconds, sharedCooldownMultiplier,
                sharedEffectiveness, description, sharedDescription, null, null);
    }

    public GoblinSkill(String key, String displayName, GoblinSkillCategory category,
                       int baseCooldownSeconds, double sharedCooldownMultiplier,
                       double sharedEffectiveness, String description,
                       String sharedDescription, SkillSpec spec,
                       PassiveTrigger passiveTrigger) {
        this.key = key.toLowerCase(Locale.ROOT);
        this.displayName = displayName;
        this.category = category;
        this.baseCooldownSeconds = baseCooldownSeconds;
        this.sharedCooldownMultiplier = sharedCooldownMultiplier;
        this.sharedEffectiveness = sharedEffectiveness;
        this.description = description;
        this.sharedDescription = sharedDescription;
        this.spec = spec;
        this.passiveTrigger = passiveTrigger;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public GoblinSkillCategory getCategory() {
        return category;
    }

    public int getBaseCooldownSeconds() {
        return baseCooldownSeconds;
    }

    public double getSharedCooldownMultiplier() {
        return sharedCooldownMultiplier;
    }

    public double getSharedEffectiveness() {
        return sharedEffectiveness;
    }

    public String getDescription() {
        return description;
    }

    public String getSharedDescription() {
        return sharedDescription;
    }

    public int getCooldownSeconds(boolean inheritor) {
        if (inheritor) {
            return baseCooldownSeconds;
        }
        return (int) Math.ceil(baseCooldownSeconds * sharedCooldownMultiplier);
    }

    public Optional<SkillSpec> getSpec() {
        return Optional.ofNullable(spec);
    }

    public Optional<PassiveTrigger> getPassiveTrigger() {
        return Optional.ofNullable(passiveTrigger);
    }
}
