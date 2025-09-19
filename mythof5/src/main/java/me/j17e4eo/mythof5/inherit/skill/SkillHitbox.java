package me.j17e4eo.mythof5.inherit.skill;

/**
 * Describes the general shape of a goblin skill hitbox.
 */
public enum SkillHitbox {
    /** A spherical area around the caster. */
    SPHERE,
    /** A conical area projected from the caster's facing direction. */
    CONE,
    /** A line projected forward from the caster. */
    LINE,
    /** Applies only to the primary target. */
    SINGLE
}
