package com.mercuriusxeno.goo.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.goo;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.id;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.recipe;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.slot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for LeastBaseSearch: the smallest base per goo type at which no expression division and no
 * recipe division leaves a remainder, built from small base_values JSON and TestRecipeBuilder recipes.
 */
class LeastBaseSearchTest {

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    private static LeastBaseSearch.TypeLeastBase onlyRow(List<LeastBaseSearch.TypeLeastBase> rows) {
        assertEquals(1, rows.size());
        return rows.getFirst();
    }

    @Test
    void leastBaseClearsBothTheExpressionAndTheRecipeDivision() {
        JsonObject merged = json("""
                {"_constants": {"base": 1, "eighth": "$base / 8"},
                 "minecraft:cobblestone": {"rock": "$eighth"}}""");
        List<RecipeInput> recipes = List.of(recipe("minecraft:gravel", 3, slot("minecraft:cobblestone")));

        LeastBaseSearch.TypeLeastBase rock = onlyRow(
                LeastBaseSearch.search(merged, recipes, List.of(GooTypes.ROCK), false, 64));

        assertEquals(LeastBaseSearch.Outcome.FOUND, rock.outcome());
        assertEquals(24, rock.leastBase());
        assertEquals(new TreeSet<>(Set.of("$eighth / 8", "minecraft:gravel -> 3")), rock.divisions());
        assertEquals("  rock: 24 (forced by $eighth / 8, minecraft:gravel -> 3)", rock.lines().getFirst());
    }

    @Test
    void typeNoDivisionTouchesHasLeastBaseOne() {
        LeastBaseSearch.TypeLeastBase leaf = onlyRow(LeastBaseSearch.search(
                json("{\"_constants\": {\"base\": 1}}"), List.of(), List.of(GooTypes.LEAF), false, 8));

        assertEquals(LeastBaseSearch.Outcome.FOUND, leaf.outcome());
        assertEquals(1, leaf.leastBase());
        assertEquals(List.of("  leaf: 1"), leaf.lines());
    }

    @Test
    void literalAmountIsAnAnchorAndItsLossyDivisionIsUnfixable() {
        JsonObject merged = json("""
                {"_constants": {"base": 1},
                 "minecraft:cake": {"jelly": 36}}""");
        List<RecipeInput> recipes = List.of(recipe("minecraft:cookie", 8, slot("minecraft:cake")));

        LeastBaseSearch.TypeLeastBase jelly = onlyRow(
                LeastBaseSearch.search(merged, recipes, List.of(GooTypes.JELLY), false, 16));

        assertEquals(LeastBaseSearch.Outcome.UNFIXABLE, jelly.outcome());
        assertEquals(List.of("minecraft:cake = 36"), jelly.anchors());
        assertEquals(List.of(
                "  jelly: unfixable by the base (minecraft:cookie -> 8; fixed anchors: minecraft:cake = 36)",
                "    anchor: minecraft:cake = 36 (does not scale with the base)"), jelly.lines());
    }

    @Test
    void typeNoCandidateClearsWithoutALiteralReadsNoneUnderTheCap() {
        JsonObject merged = json("""
                {"_constants": {"base": 1, "ninth": "$base / 9"},
                 "minecraft:cobblestone": {"rock": "$ninth"}}""");

        LeastBaseSearch.TypeLeastBase rock = onlyRow(
                LeastBaseSearch.search(merged, List.of(), List.of(GooTypes.ROCK), false, 4));

        assertEquals(LeastBaseSearch.Outcome.NONE_UNDER_CAP, rock.outcome());
        assertEquals(List.of("  rock: none under 4 (remaining: $ninth / 9)"), rock.lines());
    }

    @Test
    void recipeTheDivisibilitySectionReportsLossyForcesItsType() {
        JsonObject merged = json("""
                {"_constants": {"base": 4},
                 "minecraft:stone": {"rock": "$base"}}""");
        List<RecipeInput> recipes = List.of(recipe("minecraft:stone_slab", 3, slot("minecraft:stone")));
        Map<Identifier, GooValue> baseValues = Map.of(id("minecraft:stone"), goo(GooTypes.ROCK, 4));

        List<GooValueRegistry.DivisibilityLoss> losses = GooDerivationDiagnostics.detectDivisibilityLoss(
                GooValueDerivation.groupByOutput(recipes), baseValues, Map.of());
        LeastBaseSearch.TypeLeastBase rock = onlyRow(
                LeastBaseSearch.search(merged, recipes, List.of(GooTypes.ROCK), false, 4));

        assertEquals(1, losses.size());
        assertTrue(rock.divisions().contains(losses.getFirst().output() + " -> " + losses.getFirst().outputCount()));
    }
}
