package com.mercuriusxeno.goo.command;

import com.mercuriusxeno.goo.data.RecipeInput;
import net.minecraft.resources.Identifier;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;

/**
 * How an audit section writes an item and a recipe: a vanilla id reads bare, and a recipe reads as its
 * ingredient counts, each slot by its first alternative in id order, then its output count.
 */
final class GooAuditRecipeText {
    private static final String SPACE = " ";
    private static final String RECIPE_JOIN = " + ";
    private static final String RECIPE_ARROW = " -> ";
    private static final String DEFAULT_NAMESPACE = "minecraft";

    private GooAuditRecipeText() {
    }

    /**
     * @param recipe the recipe
     * @return the recipe like "6 glass -> 16 glass_pane"
     */
    static String recipeText(RecipeInput recipe) {
        Map<Identifier, Integer> counts = new TreeMap<>();
        for (Set<Identifier> slot : recipe.ingredientAlternatives()) {
            slot.stream().sorted().findFirst().ifPresent(pick -> counts.merge(pick, 1, Integer::sum));
        }
        StringJoiner inputs = new StringJoiner(RECIPE_JOIN);
        counts.forEach((id, count) -> inputs.add(count + SPACE + itemName(id)));
        return inputs + RECIPE_ARROW + recipe.resultCount() + SPACE + itemName(recipe.output());
    }

    /**
     * @param id an item id
     * @return the id's path for a vanilla item, the whole id otherwise
     */
    static String itemName(Identifier id) {
        return DEFAULT_NAMESPACE.equals(id.getNamespace()) ? id.getPath() : id.toString();
    }
}
