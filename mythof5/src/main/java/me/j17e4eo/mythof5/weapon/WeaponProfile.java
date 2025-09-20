package me.j17e4eo.mythof5.weapon;

/**
 * Immutable snapshot of a weapon classification consisting of the
 * detected weapon type and its tier.
 */
public record WeaponProfile(WeaponType type, WeaponTier tier) {
}

