package me.j17e4eo.mythof5.command.gui;

/**
 * High level intent for admin GUI interactions. Determines how a selection
 * should be handled when the player clicks on contextual options.
 */
public enum AdminAction {
    NONE,
    INHERIT_SET,
    INHERIT_CLEAR,
    RELIC_GIVE,
    RELIC_REMOVE
}
