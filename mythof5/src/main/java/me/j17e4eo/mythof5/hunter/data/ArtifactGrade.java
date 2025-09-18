package me.j17e4eo.mythof5.hunter.data;

import java.util.Locale;

/**
 * Grade influences baseline gauge gain.
 */
public enum ArtifactGrade {
    C(7.0),
    B(9.0),
    A(11.0),
    S(12.0);

    private final double defaultGaugeGain;

    ArtifactGrade(double defaultGaugeGain) {
        this.defaultGaugeGain = defaultGaugeGain;
    }

    public double getDefaultGaugeGain() {
        return defaultGaugeGain;
    }

    public static ArtifactGrade fromKey(String key) {
        if (key == null) {
            return C;
        }
        try {
            return ArtifactGrade.valueOf(key.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return C;
        }
    }
}
