package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.goo;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.recipe;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.slot;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A recipe's per-item value is its input value divided exactly by its result count
 * (decision values-are-fractions-melt-floors-fabricate-ceils).
 */
class GooRecipeEvaluatorTest {

    private static final String GLASS = "minecraft:glass";

    @Test
    void sixGlassIntoSixteenPanesCarriesThreeEighthsPerType() {
        RecipeInput panes = recipe("minecraft:glass_pane", 16,
                slot(GLASS), slot(GLASS), slot(GLASS), slot(GLASS), slot(GLASS), slot(GLASS));
        Map<Identifier, GooValue> base = Map.of(Identifier.parse(GLASS),
                goo(GooTypes.CRYSTAL, 1, GooTypes.ROCK, 1));

        GooRecipeEvaluator.RecipeResult pane = GooRecipeEvaluator.evaluateRecipe(panes, base, Map.of());

        assertEquals(GooFraction.of(3, 8), pane.value().exact(GooTypes.CRYSTAL));
        assertEquals(GooFraction.of(3, 8), pane.value().exact(GooTypes.ROCK));
    }
}
