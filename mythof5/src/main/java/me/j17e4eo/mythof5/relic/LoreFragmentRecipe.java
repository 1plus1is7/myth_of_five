package me.j17e4eo.mythof5.relic;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Defines a required set of lore fragments and the resulting relic type.
 */
public final class LoreFragmentRecipe {

    private final Set<LoreFragmentType> ingredients;
    private final RelicType result;
    private final String description;

    public LoreFragmentRecipe(Set<LoreFragmentType> ingredients, RelicType result, String description) {
        this.ingredients = EnumSet.copyOf(ingredients);
        this.result = result;
        this.description = description;
    }

    public Set<LoreFragmentType> getIngredients() {
        return Collections.unmodifiableSet(ingredients);
    }

    public RelicType getResult() {
        return result;
    }

    public String getDescription() {
        return description;
    }
}
