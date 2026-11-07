package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the expression builder offers only what the game's evaluator accepts where the value is read
 * (decision expression-builder-composes-every-form-without-typing): a prefab reads every other prefab wherever it
 * sits and no item (decision prefabs-resolve-by-dependency-graph-conversions-alone-stay-ordered), a number prefab
 * adds numbers and number prefabs, a goo value adds items and per-type prefabs and never a bare number, a picker
 * hides the goo types a value already holds, and a number shows no factor.
 */
class BuilderGrammarTest {
    private static final List<BuilderGrammar.Prefab> PREFABS = List.of(
            new BuilderGrammar.Prefab("base", false),
            new BuilderGrammar.Prefab("stick", true),
            new BuilderGrammar.Prefab("medium", false),
            new BuilderGrammar.Prefab("plank", true));

    @Nested
    class PrefabReadsEveryOther {
        @Test
        void firstPrefabListsEveryOtherPrefabAndNeverItself() {
            BuilderGrammar grammar = BuilderGrammar.forPrefab(PREFABS, "base");
            assertEquals(List.of("medium"), grammar.scalarPrefabs());
            assertEquals(List.of("stick", "plank"), grammar.perTypePrefabs());
            assertTrue(grammar.offersGooKind());
        }

        @Test
        void prefabOffersNoGooKindWhereNoOtherPerTypePrefabStands() {
            BuilderGrammar grammar = BuilderGrammar.forPrefab(List.of(new BuilderGrammar.Prefab("base", false),
                    new BuilderGrammar.Prefab("stick", true)), "stick");
            assertEquals(List.of("base"), grammar.scalarPrefabs());
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

        // Decision one-x-is-implied-so-an-unscaled-term-draws-no-ratio: an unscaled number, reference or
        // parenthetical shows no factor; a scaled one shows it.
        @Test
        void unscaledTermsShowNoFactorAndAScaledOneShowsIt() {
            for (String unscaled : List.of("192", "$base", "($base)")) {
                assertEquals(BuilderGrammar.Factor.HIDDEN,
                        BuilderGrammar.factorOf(ExpressionBuilder.of(unscaled).terms().getFirst()), unscaled);
            }
            assertEquals(BuilderGrammar.Factor.SHOWN,
                    BuilderGrammar.factorOf(ExpressionBuilder.of("3 $base").terms().getFirst()));
        }

        @Test
        void numberBoxTakesDigitsAndAZero() {
            assertTrue(BuilderGrammar.acceptsTyped("192", false));
            assertFalse(BuilderGrammar.acceptsTyped("1a", false));
            assertFalse(BuilderGrammar.acceptsTyped("-3", false));
            // Decision factor-left-at-one-or-set-to-one-or-zero-erases-its-segment: a term's factor box takes a 0,
            // which erases its side; a conversion formula's ratio box still refuses one.
            assertTrue(BuilderGrammar.acceptsTyped("0", false));
            assertFalse(BuilderGrammar.acceptsTyped("0", true));
            assertTrue(BuilderGrammar.acceptsTyped("10", true));
            assertTrue(BuilderGrammar.acceptsTyped("", true));
        }

        // Decision number-box-takes-a-mixed-number: a term's number box takes each step of typing 2 1/2.
        @Test
        void termNumberBoxTakesAMixedNumberAsItIsTyped() {
            for (String typed : List.of("", "2", "2 ", "2 1", "2 1/", "2 1/2", "1/2")) {
                assertTrue(BuilderGrammar.acceptsTypedMixed(typed), typed);
            }
            for (String typed : List.of("2.5", "a", "-1", "1/2/3", "2  1")) {
                assertFalse(BuilderGrammar.acceptsTypedMixed(typed), typed);
            }
        }
    }

    /**
     * An ability's cost builds like a by-type value that can only accept one type, the ability's own (decision
     * ability-cost-builds-as-a-one-type-by-type-value).
     */
    @Nested
    class OneType {
        private final BuilderGrammar cost = BuilderGrammar.forOneType(PREFABS, "rock");
        private final Set<String> everyOther = GooTypes.order().stream().map(GooTypes::id)
                .filter(type -> !type.equals("rock")).collect(Collectors.toSet());

        @Test
        void offersTheAbilitysTypeAlone() {
            assertEquals(Set.of("rock"), cost.offeredTypes());
        }

        @Test
        void pickersHideEveryOtherType() {
            assertEquals(everyOther, cost.memberHidden());
            Set<String> hidden = cost.hiddenTypes(ExpressionBuilder.of("{\"rock\": \"3 minecraft:cobblestone.rock\"}"));
            assertTrue(hidden.containsAll(everyOther), hidden.toString());
        }

        @Test
        void readsItemsAndEveryPrefabByTypeAlone() {
            assertTrue(cost.readsItems());
            assertEquals(List.of("base", "medium"), cost.scalarPrefabs());
            assertEquals(List.of("stick", "plank"), cost.perTypePrefabs());
            assertFalse(cost.offersGooKind());
            assertFalse(cost.offersNumberKind());
        }

        @Test
        void aValueOfAnyTypeHidesNothingButWhatItHolds() {
            assertEquals(Set.of(), BuilderGrammar.forItems(PREFABS).memberHidden());
            assertEquals(Set.of("metal"),
                    BuilderGrammar.forItems(PREFABS).hiddenTypes(ExpressionBuilder.of("{\"metal\": \"5\"}")));
        }
    }
}
