package me.j17e4eo.mythof5.rift.config;

import java.util.Collections;
import java.util.List;

/**
 * Container for theme specific flavour text that decorates the rift lifecycle.
 */
public final class RiftFlavor {
    private final List<String> activation;
    private final List<String> awakening;
    private final List<String> phaseOneCallouts;
    private final List<String> phaseTwoCallouts;
    private final List<String> boss;
    private final List<String> collapse;
    private final List<String> mechanic;

    public RiftFlavor(List<String> activation,
                      List<String> awakening,
                      List<String> phaseOneCallouts,
                      List<String> phaseTwoCallouts,
                      List<String> boss,
                      List<String> collapse,
                      List<String> mechanic) {
        this.activation = activation == null ? List.of() : List.copyOf(activation);
        this.awakening = awakening == null ? List.of() : List.copyOf(awakening);
        this.phaseOneCallouts = phaseOneCallouts == null ? List.of() : List.copyOf(phaseOneCallouts);
        this.phaseTwoCallouts = phaseTwoCallouts == null ? List.of() : List.copyOf(phaseTwoCallouts);
        this.boss = boss == null ? List.of() : List.copyOf(boss);
        this.collapse = collapse == null ? List.of() : List.copyOf(collapse);
        this.mechanic = mechanic == null ? List.of() : List.copyOf(mechanic);
    }

    public List<String> activation() {
        return Collections.unmodifiableList(activation);
    }

    public List<String> awakening() {
        return Collections.unmodifiableList(awakening);
    }

    public List<String> phaseOneCallouts() {
        return Collections.unmodifiableList(phaseOneCallouts);
    }

    public List<String> phaseTwoCallouts() {
        return Collections.unmodifiableList(phaseTwoCallouts);
    }

    public List<String> boss() {
        return Collections.unmodifiableList(boss);
    }

    public List<String> collapse() {
        return Collections.unmodifiableList(collapse);
    }

    public List<String> mechanic() {
        return Collections.unmodifiableList(mechanic);
    }

    public static RiftFlavor empty() {
        return new RiftFlavor(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
