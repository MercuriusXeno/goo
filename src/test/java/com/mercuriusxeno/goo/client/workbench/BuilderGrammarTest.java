package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the expression builder offers only what the game's evaluator accepts where the value is read
 * (decision expression-builder-composes-every-form-without-typing): a prefab reads only the prefabs above it and no
 * item, a number prefab adds numbers and number prefabs, a goo value adds items and per-type prefabs and never a
 * bare number, a picker hides the goo types a value already holds, and a number shows no factor.
 */
class BuilderGrammarTest {
    private static final List<BuilderGrammar.Prefab> PREFABS = List.of(
            new BuilderGrammar.Prefab("base", false),
            new BuilderGrammar.Prefab("stick", true),
            new BuilderGrammar.Prefab("medium", false),
            new BuilderGrammar.Prefab("plank", true));

    @Nested
    class PrefabReadsAbove {
        @Test
        void prefabListsOnlyThePrefabsAboveIt() {
            BuilderGrammar grammar = BuilderGrammar.forPrefab(PREFABS, "medium");
            assertEquals(List.of("base"), grammar.scalarPrefabs());
            assertEquals(List.of("stick"), grammar.perTypePrefabs());
        }

        @Test
        void firstPrefabListsNone() {
            BuilderGrammar grammar = BuilderGrammar.forPrefab(PREFABS, "base");
            assertTrue(grammar.scalarPrefabs().isEmpty());
            assertTrue(grammar.perTypePrefabs().isEmpty());
            assertFalse(grammar.offersGooKind());
        }

        @Test
        void prefabReadsNoItem() {
            BuilderGrammar grammar = BuilderGrammar.forPrefab(PREFABS, "plank");
            assertFalse(grammar.readsItems());
            assertFalse(grammar.termTakesItems(BuilderGrammar.GOO_SORT));
        }

        @Test
        void itemValueReadsEveryPrefabAndItems() {
            BuilderGrammar grammar = BuilderGrammar.forItems(PREFABS);
            assertEquals(List.of("base", "medium"), grammar.scalarPrefabs());
            assertEquals(List.of("stick", "plank"), grammar.perTypePrefabs());
            assertTrue(grammar.readsItems());
        }
    }

    @Nested
    class Sorts {
        @Test
        void numberPrefabAddsNumbersAndNumberPrefabs() {
            BuilderGrammar grammar = BuilderGrammar.forPrefab(PREFABS, "plank");
            assertTrue(grammar.offersNumberKind());
            assertTrue(grammar.termTakesNumbers(BuilderGrammar.NUMBER_SORT));
            assertEquals(List.of("base", "medium"), grammar.termPrefabs(BuilderGrammar.NUMBER_SORT));
            assertFalse(grammar.memberOptional(BuilderGrammar.NUMBER_SORT));
        }

        @Test
        void gooValueAddsNoBareNumber() {
            BuilderGrammar grammar = BuilderGrammar.forItems(PREFABS);
            assertFalse(grammar.offersNumberKind());
            assertFalse(grammar.termTakesNumbers(BuilderGrammar.GOO_SORT));
            assertEquals(List.of("stick", "plank"), grammar.termPrefabs(BuilderGrammar.GOO_SORT));
            assertTrue(grammar.termTakesItems(BuilderGrammar.GOO_SORT));
        }

        @Test
        void prefabReadingAPerTypePrefabIsAGooValue() {
            BuilderGrammar grammar = BuilderGrammar.forPrefab(PREFABS, "plank");
            assertEquals(BuilderGrammar.GOO_SORT, grammar.sortOf(ExpressionBuilder.of("2 $stick")));
            assertEquals(BuilderGrammar.NUMBER_SORT, grammar.sortOf(ExpressionBuilder.of("3 $base + 4")));
            assertEquals(BuilderGrammar.GOO_SORT, BuilderGrammar.forItems(PREFABS)
                    .sortOf(ExpressionBuilder.of("anvil")));
        }
    }

    @Nested
    class Offers {
        @Test
        void pickerHidesTheTypesHeld() {
            assertEquals(Set.of("metal", "pulse"),
                    BuilderGrammar.heldTypes(ExpressionBuilder.of("{\"metal\": \"5\", \"pulse\": \"2\"}")));
        }

        @Test
        void numberShowsNoFactorAndAReferenceOffersOne() {
            assertEquals(BuilderGrammar.Factor.HIDDEN,
                    BuilderGrammar.factorOf(ExpressionBuilder.of("192").terms().getFirst()));
            assertEquals(BuilderGrammar.Factor.OFFERED,
                    BuilderGrammar.factorOf(ExpressionBuilder.of("$base").terms().getFirst()));
            assertEquals(BuilderGrammar.Factor.SHOWN,
                    BuilderGrammar.factorOf(ExpressionBuilder.of("3 $base").terms().getFirst()));
        }

        @Test
        void numberBoxTakesDigitsAndNoZeroFactor() {
            assertTrue(BuilderGrammar.acceptsTyped("192", false));
            assertTrue(BuilderGrammar.acceptsTyped("0", false));
            assertFalse(BuilderGrammar.acceptsTyped("1a", false));
            assertFalse(BuilderGrammar.acceptsTyped("-3", false));
            assertFalse(BuilderGrammar.acceptsTyped("0", true));
            assertTrue(BuilderGrammar.acceptsTyped("10", true));
            assertTrue(BuilderGrammar.acceptsTyped("", true));
        }
    }
}
