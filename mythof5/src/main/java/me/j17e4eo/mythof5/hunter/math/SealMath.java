package me.j17e4eo.mythof5.hunter.math;

import me.j17e4eo.mythof5.hunter.data.ArtifactState;

/**
 * Encapsulates seal integrity math and release probability.
 */
public class SealMath {

    private final double lowChance;
    private final double criticalBase;
    private final double slope;
    private final double cap;
    private final long fatigueWindowMillis;
    private final double fatigueMin;
    private final double fatigueMax;

    public SealMath(double lowChance, double criticalBase, double slope, double cap,
                    long fatigueWindowMillis, double fatigueMin, double fatigueMax) {
        this.lowChance = lowChance;
        this.criticalBase = criticalBase;
        this.slope = slope;
        this.cap = cap;
        this.fatigueWindowMillis = fatigueWindowMillis;
        this.fatigueMin = fatigueMin;
        this.fatigueMax = fatigueMax;
    }

    public double computeChance(double integrity, double fatigueModifier) {
        double base;
        if (integrity >= 90.0) {
            base = criticalBase + slope * (integrity - 90.0);
            base = Math.min(base, cap);
        } else {
            base = lowChance;
        }
        double result = base * fatigueModifier;
        if (result < 0.0) {
            return 0.0;
        }
        if (result > 1.0) {
            return 1.0;
        }
        return result;
    }

    public ArtifactState evaluateState(double integrity, ArtifactState current) {
        if (current == ArtifactState.BROKEN || current == ArtifactState.DESTROYED) {
            return current;
        }
        if (integrity >= 90.0) {
            return ArtifactState.CRITICAL;
        }
        return ArtifactState.STABLE;
    }

    public long getFatigueWindowMillis() {
        return fatigueWindowMillis;
    }

    public double clampFatigue(double modifier) {
        if (modifier < fatigueMin) {
            return fatigueMin;
        }
        if (modifier > fatigueMax) {
            return fatigueMax;
        }
        return modifier;
    }
}
