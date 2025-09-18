package me.j17e4eo.mythof5.relic;

import java.util.List;
import java.util.Set;

/**
 * Represents a deterministic fusion between relics.
 */
public final class RelicFusion {

    private final RelicType result;
    private final List<RelicType> ingredients;
    private final String description;

    public RelicFusion(RelicType result, String description, RelicType... ingredients) {
        this.result = result;
        this.description = description;
        this.ingredients = List.of(ingredients);
    }

    public RelicType getResult() {
        return result;
    }

    public List<RelicType> getIngredients() {
        return ingredients;
    }

    public String getDescription() {
        return description;
    }

    public boolean matches(Set<RelicType> owned) {
        return owned.containsAll(ingredients);
    }
}
