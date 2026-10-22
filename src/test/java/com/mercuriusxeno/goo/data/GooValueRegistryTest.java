package com.mercuriusxeno.goo.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GooValueRegistry derivation, conflict detection, and effective value resolution.
 * All tests use the pure-logic layer  - no Minecraft server required.
 */
class GooValueRegistryTest {

    private GooValueRegistry registry;

    /**
     * Creates a fresh registry before each test.
     */
    @BeforeEach
    void setUp() {
        registry = new GooValueRegistry();
    }

    // ── Test helpers (replace removed registry methods) ────────────────

    /**
     * Sets base values on the registry, copying to effective.
     */
    private void setBaseValues(Map<Identifier, GooValue> values) {
        registry.seedBaseValues(values);
    }

    /**
     * Sets denied items on the registry.
     */
    private void setDeniedItems(Set<Identifier> items) {
        registry.seedDeniedItems(items);
    }

    /**
     * Returns derived values snapshot.
     */
    private Map<Identifier, GooValue> getDerivedValues() {
        return registry.lastDerivedValues();
    }

    /**
     * Parses base values from a JSON stream into the registry.
     */
    private void parseBaseValuesFromStream(java.io.InputStream is) throws IOException {
        registry.parseBaseValues(is);
    }

    /**
     * Copies base values to effective, applying post-conversions.
     */
    private void copyBaseToEffective() {
        registry.publishBaseAsEffective();
    }

    /**
     * Validates a JSON string for expression mistakes.
     */
    private List<String> validateJsonString(String jsonString) {
        List<String> warnings = new java.util.ArrayList<>();
        JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();
        GooValueValidator.validateJson(json, warnings);
        return warnings;
    }

    // ── LCD Derivation ──────────────────────────────────────────────────

    @Nested
    class LcdDerivation {

        /**
         * A single recipe with all ingredients valued produces a derived value.
         */
        @Test
        void singleRecipeDerivesValue() {
            setBaseValues(Map.of(
                    id("minecraft:iron_ingot"), goo(GooTypes.METAL, 10)
            ));
            List<RecipeInput> recipes = List.of(
                    recipe("minecraft:iron_block", 1,
                            slot("minecraft:iron_ingot"), slot("minecraft:iron_ingot"),
                            slot("minecraft:iron_ingot"), slot("minecraft:iron_ingot"),
                            slot("minecraft:iron_ingot"), slot("minecraft:iron_ingot"),
                            slot("minecraft:iron_ingot"), slot("minecraft:iron_ingot"),
                            slot("minecraft:iron_ingot"))
            );

            int derived = registry.deriveFromRecipeInputs(recipes, false);
            assertEquals(1, derived);
            GooValue val = registry.table().lookup(id("minecraft:iron_block"));
            assertNotNull(val);
            assertEquals(GooFraction.of(90), val.exact(GooTypes.METAL)); // 9 * 10
        }

        /**
         * Multiple recipes for the same item: cheapest wins (LCD rule).
         */
        @Test
        void multipleRecipesCheapestWins() {
            setBaseValues(Map.of(
                    id("a"), goo(GooTypes.METAL, 10),
                    id("b"), goo(GooTypes.METAL, 3)
            ));
            List<RecipeInput> recipes = List.of(
                    recipe("x", 1, slot("a")),          // cost 10
                    recipe("x", 1, slot("b"), slot("b")) // cost 6
            );

            registry.deriveFromRecipeInputs(recipes, false);
            GooValue val = registry.table().lookup(id("x"));
            assertNotNull(val);
            assertEquals(GooFraction.of(6), val.exact(GooTypes.METAL)); // cheaper recipe wins
        }

        /**
         * A base value tying a recipe on total holds, though it carries more
         * goo types than the recipe (idea explicit-value-wins-a-tie).
         */
        @Test
        void baseHoldsAgainstAnEqualRecipeWithFewerTypes() {
            setBaseValues(Map.of(
                    id("shroom"), goo(GooTypes.SHROOM, 10),
                    id("stew"), goo(GooTypes.SHROOM, 4, GooTypes.LEAF, 3, GooTypes.JELLY, 3)
            ));
            List<RecipeInput> recipes = List.of(recipe("stew", 1, slot("shroom")));

            registry.deriveFromRecipeInputs(recipes, false);
            assertEquals(GooFraction.of(3), registry.table().lookup(id("stew")).exact(GooTypes.JELLY));
        }

        /**
         * Multi-pass: recipe B needs A's value, A derives on pass 1, B on pass 2.
         */
        @Test
        void multiPassDerivation() {
            setBaseValues(Map.of(
                    id("raw"), goo(GooTypes.ROCK, 5)
            ));
            List<RecipeInput> recipes = List.of(
                    recipe("intermediate", 1, slot("raw"), slot("raw")),  // pass 1: 10
                    recipe("final", 1, slot("intermediate"))               // pass 2: 10
            );

            int derived = registry.deriveFromRecipeInputs(recipes, false);
            assertEquals(2, derived);
            assertEquals(GooFraction.of(10), registry.table().lookup(id("final")).exact(GooTypes.ROCK));
        }

        /**
         * Ingredient with alternatives: cheapest alternative is chosen.
         */
        @Test
        void ingredientAlternativesCheapestChosen() {
            setBaseValues(Map.of(
                    id("cheap"), goo(GooTypes.LEAF, 2),
                    id("expensive"), goo(GooTypes.LEAF, 20)
            ));
            List<RecipeInput> recipes = List.of(
                    recipe("output", 1, slot("cheap", "expensive"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertEquals(GooFraction.of(2), registry.table().lookup(id("output")).exact(GooTypes.LEAF));
        }

        /**
         * Result count > 1 divides the total value.
         */
        @Test
        void resultCountDividesValue() {
            setBaseValues(Map.of(
                    id("planks"), goo(GooTypes.LEAF, 10)
            ));
            List<RecipeInput> recipes = List.of(
                    recipe("sticks", 4, slot("planks"), slot("planks")) // 20 / 4 = 5
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertEquals(GooFraction.of(5), registry.table().lookup(id("sticks")).exact(GooTypes.LEAF));
        }

        /**
         * Missing ingredient value causes recipe to be skipped.
         */
        @Test
        void missingIngredientSkipsRecipe() {
            setBaseValues(Map.of(
                    id("known"), goo(GooTypes.METAL, 5)
            ));
            List<RecipeInput> recipes = List.of(
                    recipe("output", 1, slot("known"), slot("unknown"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertNull(registry.table().lookup(id("output")));
        }

        /**
         * Pass limit terminates derivation even with circular dependencies.
         */
        @Test
        void passLimitTerminates() {
            // No base values, recipes form a cycle  - should terminate without error
            List<RecipeInput> recipes = List.of(
                    recipe("a", 1, slot("b")),
                    recipe("b", 1, slot("a"))
            );

            assertDoesNotThrow(() -> registry.deriveFromRecipeInputs(recipes, false));
            assertEquals(0, registry.diagnostics().derivedSize());
        }

        /**
         * When total goo differ, cheaper still wins regardless of type count.
         */
        @Test
        void cheaperStillWinsOverFewerTypes() {
            setBaseValues(Map.of(
                    id("a"), goo(GooTypes.METAL, 10, GooTypes.ROCK, 10, GooTypes.CRYSTAL, 10),
                    id("b"), goo(GooTypes.METAL, 20, GooTypes.ROCK, 20)
            ));
            // a costs 30 (3 types), b costs 40 (2 types) -- a wins on total
            List<RecipeInput> recipes = List.of(
                    recipe("x", 1, slot("a")),
                    recipe("x", 1, slot("b"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            GooValue val = registry.table().lookup(id("x"));
            assertNotNull(val);
            assertEquals(GooFraction.of(30), val.totalExact(), "Cheaper recipe wins even with more types");
            assertEquals(3, val.exactAll().size());
        }

        /**
         * Recipe with no ingredients produces no derived value.
         */
        @Test
        void noIngredientsProducesNothing() {
            List<RecipeInput> recipes = List.of(
                    recipeInput(id("output"), 1, List.of())
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertNull(registry.table().lookup(id("output")));
        }
    }

    // ── Conflict Detection ──────────────────────────────────────────────

    @Nested
    class ConflictDetection {

        /**
         * No conflict when derived equals base.
         */
        @Test
        void noConflictWhenEqual() {
            setBaseValues(Map.of(
                    id("a"), goo(GooTypes.METAL, 10),
                    id("b"), goo(GooTypes.METAL, 5)
            ));
            List<RecipeInput> recipes = List.of(
                    recipe("a", 1, slot("b"), slot("b")) // cost 10 == base 10
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertTrue(registry.diagnostics().conflicts().isEmpty());
        }

        /**
         * Recipe cheaper than base is flagged as conflict.
         */
        @Test
        void recipeCheaperThanBaseIsConflict() {
            setBaseValues(Map.of(
                    id("a"), goo(GooTypes.METAL, 20),
                    id("b"), goo(GooTypes.METAL, 3)
            ));
            List<RecipeInput> recipes = List.of(
                    recipe("a", 1, slot("b")) // cost 3 < base 20
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertEquals(1, registry.diagnostics().conflicts().size());
            assertTrue(registry.diagnostics().conflicts().get(0).isRecipeCheaper());
        }

        /**
         * Base cheaper than recipe is also flagged as conflict (value mismatch).
         */
        @Test
        void baseCheaperThanRecipeIsConflict() {
            setBaseValues(Map.of(
                    id("a"), goo(GooTypes.METAL, 5),
                    id("b"), goo(GooTypes.METAL, 10)
            ));
            List<RecipeInput> recipes = List.of(
                    recipe("a", 1, slot("b")) // cost 10 > base 5
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertEquals(1, registry.diagnostics().conflicts().size());
            assertFalse(registry.diagnostics().conflicts().get(0).isRecipeCheaper());
        }
    }

    // ── Effective Values ────────────────────────────────────────────────

    @Nested
    class EffectiveValues {

        /**
         * Derived-only item: effective equals derived.
         */
        @Test
        void derivedOnlyItemUsesDerivation() {
            setBaseValues(Map.of(
                    id("base_item"), goo(GooTypes.METAL, 10)
            ));
            List<RecipeInput> recipes = List.of(
                    recipe("derived_only", 1, slot("base_item"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            GooValue eff = registry.table().lookup(id("derived_only"));
            assertNotNull(eff);
            assertEquals(GooFraction.of(10), eff.exact(GooTypes.METAL));
        }

        /**
         * Base-only item: effective equals base.
         */
        @Test
        void baseOnlyItemUsesBase() {
            setBaseValues(Map.of(
                    id("base_only"), goo(GooTypes.LEAF, 7)
            ));
            registry.deriveFromRecipeInputs(List.of(), false);

            GooValue eff = registry.table().lookup(id("base_only"));
            assertNotNull(eff);
            assertEquals(GooFraction.of(7), eff.exact(GooTypes.LEAF));
        }

        /**
         * Both exist, recipe cheaper, override off: effective = derived.
         */
        @Test
        void cheaperRecipeWinsWithoutOverride() {
            setBaseValues(Map.of(
                    id("item"), goo(GooTypes.METAL, 20),
                    id("cheap"), goo(GooTypes.METAL, 3)
            ));
            List<RecipeInput> recipes = List.of(
                    recipe("item", 1, slot("cheap"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertEquals(GooFraction.of(3), registry.table().lookup(id("item")).exact(GooTypes.METAL));
        }

        /**
         * Both exist, recipe cheaper, override on: effective = base.
         */
        @Test
        void baseWinsWithOverride() {
            setBaseValues(Map.of(
                    id("item"), goo(GooTypes.METAL, 20),
                    id("cheap"), goo(GooTypes.METAL, 3)
            ));
            List<RecipeInput> recipes = List.of(
                    recipe("item", 1, slot("cheap"))
            );

            registry.deriveFromRecipeInputs(recipes, true);
            assertEquals(GooFraction.of(20), registry.table().lookup(id("item")).exact(GooTypes.METAL));
        }
    }

    @Nested
    class NegativeValueRejection {

        /**
         * A derived value with negatives is excluded from effective values.
         */
        @Test
        void negativeDerivationExcludedFromEffective() {
            Map<ResourceKey<GooTypeDefinition>, Integer> negMap = new HashMap<>();
            negMap.put(GooTypes.METAL, -10);
            negMap.put(GooTypes.YORE, 5);
            GooValue negValue = new GooValue(negMap);

            Map<Identifier, GooValue> base = Map.of();
            Map<Identifier, GooValue> derived = Map.of(id("bad_item"), negValue);

            Map<Identifier, GooValue> effective = GooValueDerivation.buildEffectiveValues(
                    base, derived, false);
            assertNull(effective.get(id("bad_item")),
                    "Items with negative goo types must not appear in effective values");
        }

        /**
         * A base value with negatives is excluded from effective values.
         */
        @Test
        void negativeBaseExcludedFromEffective() {
            Map<ResourceKey<GooTypeDefinition>, Integer> negMap = new HashMap<>();
            negMap.put(GooTypes.METAL, -10);
            GooValue negValue = new GooValue(negMap);

            Map<Identifier, GooValue> base = Map.of(id("bad_base"), negValue);
            Map<Identifier, GooValue> derived = Map.of();

            Map<Identifier, GooValue> effective = GooValueDerivation.buildEffectiveValues(
                    base, derived, false);
            assertNull(effective.get(id("bad_base")),
                    "Base items with negative goo types must not appear in effective values");
        }

        /**
         * A valid derived value next to a rejected one still appears.
         */
        @Test
        void validItemSurvivesAlongsideRejected() {
            Map<ResourceKey<GooTypeDefinition>, Integer> negMap = new HashMap<>();
            negMap.put(GooTypes.METAL, -10);
            GooValue negValue = new GooValue(negMap);
            GooValue goodValue = goo(GooTypes.ROCK, 50);

            Map<Identifier, GooValue> base = Map.of();
            Map<Identifier, GooValue> derived = Map.of(
                    id("bad_item"), negValue,
                    id("good_item"), goodValue);

            Map<Identifier, GooValue> effective = GooValueDerivation.buildEffectiveValues(
                    base, derived, false);
            assertNull(effective.get(id("bad_item")));
            assertEquals(GooFraction.of(50), effective.get(id("good_item")).exact(GooTypes.ROCK));
        }
    }

    // ── Deny List ────────────────────────────────────────────────────────

    @Nested
    class DenyList {

        /**
         * Denied items never receive a derived value, even with valid recipes.
         */
        @Test
        void deniedItemNeverDerives() {
            setBaseValues(Map.of(
                    id("raw"), goo(GooTypes.METAL, 10)
            ));
            setDeniedItems(Set.of(id("ore_block")));
            List<RecipeInput> recipes = List.of(
                    recipe("ore_block", 1, slot("raw"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertNull(registry.table().lookup(id("ore_block")));
        }

        /**
         * Denied items can still be used as recipe inputs for other items.
         */
        @Test
        void deniedItemWorksAsInput() {
            setBaseValues(Map.of(
                    id("ore_block"), goo(GooTypes.METAL, 20)
            ));
            setDeniedItems(Set.of(id("ore_block")));
            List<RecipeInput> recipes = List.of(
                    recipe("ingot", 1, slot("ore_block"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            // ore_block is denied as output, but its base value still works as input
            GooValue val = registry.table().lookup(id("ingot"));
            assertNotNull(val);
            assertEquals(GooFraction.of(20), val.exact(GooTypes.METAL));
        }

        /**
         * Denied items don't appear in effective values even if they have a base value.
         */
        @Test
        void deniedItemBaseValueStillInEffective() {
            // Deny list only blocks derivation output, not base values
            setBaseValues(Map.of(
                    id("ore"), goo(GooTypes.METAL, 10)
            ));
            setDeniedItems(Set.of(id("ore")));
            registry.deriveFromRecipeInputs(List.of(), false);

            // Base value should still be present  - deny blocks derivation, not base
            assertNotNull(registry.table().lookup(id("ore")));
        }
    }

    // ── Container Subtraction ────────────────────────────────────────────

    @Nested
    class ContainerSubtraction {

        /**
         * Bucket returned: net cost = ingredient value minus bucket value.
         */
        @Test
        void bucketReturnedSubtractsContainerValue() {
            setBaseValues(Map.of(
                    id("minecraft:milk_bucket"), goo(GooTypes.JELLY, 30),
                    id("minecraft:bucket"), goo(GooTypes.METAL, 10)
            ));
            List<RecipeInput> recipes = List.of(
                    recipe("output", 1,
                            Map.of("minecraft:milk_bucket", "minecraft:bucket"),
                            slot("minecraft:milk_bucket"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            GooValue val = registry.table().lookup(id("output"));
            assertNotNull(val);
            // 30 jelly - 0 jelly from bucket = 30 jelly; 0 metal - 10 metal = no subtraction on missing type
            assertEquals(GooFraction.of(30), val.exact(GooTypes.JELLY));
            assertEquals(GooFraction.of(0), val.exact(GooTypes.METAL)); // bucket's metal not in milk_bucket
        }

        /**
         * Container has no known value: full ingredient cost is used.
         */
        @Test
        void containerNoValueUsesFullCost() {
            setBaseValues(Map.of(
                    id("minecraft:milk_bucket"), goo(GooTypes.JELLY, 30)
                    // no value for bucket
            ));
            List<RecipeInput> recipes = List.of(
                    recipe("output", 1,
                            Map.of("minecraft:milk_bucket", "minecraft:bucket"),
                            slot("minecraft:milk_bucket"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            GooValue val = registry.table().lookup(id("output"));
            assertNotNull(val);
            assertEquals(GooFraction.of(30), val.exact(GooTypes.JELLY));
        }

        /**
         * Multiple container ingredients: each slot subtracts independently.
         */
        @Test
        void multipleContainerIngredients() {
            setBaseValues(Map.of(
                    id("milk_bucket"), goo(GooTypes.JELLY, 20, GooTypes.METAL, 10),
                    id("bucket"), goo(GooTypes.METAL, 10)
            ));
            List<RecipeInput> recipes = List.of(
                    recipe("cake", 1,
                            Map.of("milk_bucket", "bucket"),
                            slot("milk_bucket"), slot("milk_bucket"), slot("milk_bucket"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            GooValue val = registry.table().lookup(id("cake"));
            assertNotNull(val);
            // Each slot: 20 jelly + 10 metal - 10 metal = 20 jelly + 0 metal
            // 3 slots: 60 jelly
            assertEquals(GooFraction.of(60), val.exact(GooTypes.JELLY));
            assertEquals(GooFraction.of(0), val.exact(GooTypes.METAL)); // bucket metal cancels ingredient metal
        }

        /**
         * Container worth more than ingredient: gross cost is used (guard case).
         */
        @Test
        void containerWorthMoreThanIngredientUsesGross() {
            setBaseValues(Map.of(
                    id("cheap_item"), goo(GooTypes.LEAF, 5),
                    id("expensive_container"), goo(GooTypes.LEAF, 50)
            ));
            List<RecipeInput> recipes = List.of(
                    recipe("output", 1,
                            Map.of("cheap_item", "expensive_container"),
                            slot("cheap_item"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            GooValue val = registry.table().lookup(id("output"));
            assertNotNull(val);
            // 5 - 50 = -45 → empty → guard returns gross (5)
            assertEquals(GooFraction.of(5), val.exact(GooTypes.LEAF));
        }

        /**
         * Existing tests still pass with the 3-arg constructor (empty container map).
         */
        @Test
        void backwardCompatibleNoContainers() {
            setBaseValues(Map.of(
                    id("iron"), goo(GooTypes.METAL, 10)
            ));
            // Uses the 3-arg RecipeInput constructor (no container map)
            List<RecipeInput> recipes = List.of(
                    recipeInput(id("block"), 1,
                            List.of(Set.of(id("iron")), Set.of(id("iron"))))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertEquals(GooFraction.of(20), registry.table().lookup(id("block")).exact(GooTypes.METAL));
        }
    }

    // ── Cycle Detection ─────────────────────────────────────────────────

    @Nested
    class CycleDetection {

        /**
         * Two-item cycle is detected as a single SCC.
         */
        @Test
        void twoItemCycleDetected() {
            setBaseValues(Map.of());
            List<RecipeInput> recipes = List.of(
                    recipe("a", 1, slot("b")),
                    recipe("b", 1, slot("a"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            List<GooValueRegistry.RecipeCycle> cycles = registry.diagnostics().cycles();
            assertEquals(1, cycles.size());
            assertTrue(cycles.get(0).items().containsAll(List.of(id("a"), id("b"))));
        }

        /**
         * Anchored cycle: one member has a base value.
         */
        @Test
        void anchoredCycleDetected() {
            setBaseValues(Map.of(
                    id("a"), goo(GooTypes.METAL, 10)
            ));
            List<RecipeInput> recipes = List.of(
                    recipe("a", 1, slot("b")),
                    recipe("b", 1, slot("a"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            List<GooValueRegistry.RecipeCycle> cycles = registry.diagnostics().cycles();
            assertEquals(1, cycles.size());
            assertTrue(cycles.get(0).hasAnchor());
            assertEquals(id("a"), cycles.get(0).anchor());
        }

        /**
         * Non-cyclic chain produces no cycles.
         */
        @Test
        void noCyclesInLinearChain() {
            setBaseValues(Map.of(
                    id("raw"), goo(GooTypes.ROCK, 5)
            ));
            List<RecipeInput> recipes = List.of(
                    recipe("mid", 1, slot("raw")),
                    recipe("final", 1, slot("mid"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            assertTrue(registry.diagnostics().cycles().isEmpty());
        }

        /**
         * Unanchored cycle: no member has a base value or is reachable from one.
         */
        @Test
        void unanchoredCycleDetected() {
            setBaseValues(Map.of());
            List<RecipeInput> recipes = List.of(
                    recipe("x", 1, slot("y")),
                    recipe("y", 1, slot("x"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            List<GooValueRegistry.RecipeCycle> cycles = registry.diagnostics().cycles();
            assertEquals(1, cycles.size());
            assertFalse(cycles.get(0).hasAnchor());
            assertNull(cycles.get(0).anchor());
        }
    }

    // ── Group By Output ─────────────────────────────────────────────────

    @Nested
    class GroupByOutput {

        /**
         * Recipes with the same output are grouped together.
         */
        @Test
        void recipesGroupedByOutput() {
            List<RecipeInput> recipes = List.of(
                    recipe("a", 1, slot("x")),
                    recipe("a", 1, slot("y")),
                    recipe("b", 1, slot("z"))
            );

            Map<Identifier, List<RecipeInput>> grouped = GooValueDerivation.groupByOutput(recipes);
            assertEquals(2, grouped.size());
            assertEquals(2, grouped.get(id("a")).size());
            assertEquals(1, grouped.get(id("b")).size());
        }

        /**
         * Empty recipe list produces empty map.
         */
        @Test
        void emptyRecipesProduceEmptyMap() {
            Map<Identifier, List<RecipeInput>> grouped = GooValueDerivation.groupByOutput(List.of());
            assertTrue(grouped.isEmpty());
        }
    }

    // ── Lookup For Derivation ───────────────────────────────────────────

    @Nested
    class LookupForDerivation {

        /**
         * Base-only value is returned when no derived value exists.
         */
        @Test
        void baseOnlyReturnsBase() {
            Map<Identifier, GooValue> base = Map.of(id("item"), goo(GooTypes.METAL, 10));
            GooValue result = GooValueDerivation.lookupForDerivation(id("item"), base, Map.of());
            assertNotNull(result);
            assertEquals(GooFraction.of(10), result.exact(GooTypes.METAL));
        }

        /**
         * Unknown item returns null.
         */
        @Test
        void unknownItemReturnsNull() {
            assertNull(GooValueDerivation.lookupForDerivation(id("unknown"), Map.of(), Map.of()));
        }

        /**
         * When both exist, cheaper value wins.
         */
        @Test
        void cheaperOfBaseAndDerivedWins() {
            Map<Identifier, GooValue> base = Map.of(id("item"), goo(GooTypes.METAL, 20));
            Map<Identifier, GooValue> derived = Map.of(id("item"), goo(GooTypes.METAL, 3));
            GooValue result = GooValueDerivation.lookupForDerivation(id("item"), base, derived);
            assertNotNull(result);
            assertEquals(GooFraction.of(3), result.exact(GooTypes.METAL));
        }
    }

    // ── Multi-type value handling ────────────────────────────────────────

    @Nested
    class MultiTypeValues {

        /**
         * Multi-type ingredients sum correctly.
         */
        @Test
        void multiTypeIngredientsSumCorrectly() {
            setBaseValues(Map.of(
                    id("a"), goo(GooTypes.METAL, 5, GooTypes.CRYSTAL, 3),
                    id("b"), goo(GooTypes.METAL, 2, GooTypes.LEAF, 4)
            ));
            List<RecipeInput> recipes = List.of(
                    recipe("combo", 1, slot("a"), slot("b"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            GooValue val = registry.table().lookup(id("combo"));
            assertNotNull(val);
            assertEquals(GooFraction.of(7), val.exact(GooTypes.METAL));   // 5 + 2
            assertEquals(GooFraction.of(3), val.exact(GooTypes.CRYSTAL));  // 3 + 0
            assertEquals(GooFraction.of(4), val.exact(GooTypes.LEAF));     // 0 + 4
        }

        /**
         * Multi-type value divided correctly.
         */
        @Test
        void multiTypeDivision() {
            setBaseValues(Map.of(
                    id("block"), goo(GooTypes.METAL, 9, GooTypes.CRYSTAL, 6)
            ));
            List<RecipeInput> recipes = List.of(
                    recipe("nugget", 3, slot("block"))
            );

            registry.deriveFromRecipeInputs(recipes, false);
            GooValue val = registry.table().lookup(id("nugget"));
            assertNotNull(val);
            assertEquals(GooFraction.of(3), val.exact(GooTypes.METAL));   // 9 / 3
            assertEquals(GooFraction.of(2), val.exact(GooTypes.CRYSTAL)); // 6 / 3
        }
    }

    // ── findCheapestAmong ────────────────────────────────────────────────

    @Nested
    class FindCheapestAmong {

        /**
         * Returns the candidate with fewest total goo.
         */
        @Test
        void returnsLowestTotalGoo() {
            Map<Identifier, GooValue> values = Map.of(
                    id("a"), goo(GooTypes.METAL, 10),
                    id("b"), goo(GooTypes.METAL, 3),
                    id("c"), goo(GooTypes.METAL, 7)
            );
            Identifier result = IGooValueLookup.findCheapestAmong(
                    Set.of(id("a"), id("b"), id("c")), values::get);
            assertEquals(id("b"), result);
        }

        /**
         * Null lookup results are skipped.
         */
        @Test
        void nullLookupSkipped() {
            Map<Identifier, GooValue> values = Map.of(
                    id("known"), goo(GooTypes.METAL, 5)
            );
            Identifier result = IGooValueLookup.findCheapestAmong(
                    Set.of(id("known"), id("missing")), values::get);
            assertEquals(id("known"), result);
        }

        /**
         * Empty GooValue is skipped even if lookup returns non-null.
         */
        @Test
        void emptyValueSkipped() {
            Map<Identifier, GooValue> values = Map.of(
                    id("empty"), GooValue.EMPTY,
                    id("real"), goo(GooTypes.METAL, 5)
            );
            Identifier result = IGooValueLookup.findCheapestAmong(
                    Set.of(id("empty"), id("real")), values::get);
            assertEquals(id("real"), result);
        }

        /**
         * Returns null if no candidate has a non-empty value.
         */
        @Test
        void allNullReturnsNull() {
            Identifier result = IGooValueLookup.findCheapestAmong(
                    Set.of(id("a"), id("b")), id -> null);
            assertNull(result);
        }
    }

    // ── Group parsing ────────────────────────────────────────────────────

    @Nested
    class GroupParsing {

        /**
         * Helper: parse a JSON string through the registry's base value loader.
         */
        private void loadJson(String json) throws IOException {
            parseBaseValuesFromStream(
                    new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
            copyBaseToEffective();
        }

        /**
         * A _groups entry creates a pseudo-tag, #name assigns values.
         */
        @Test
        void groupCreatesPseudoTagForAssignment() throws IOException {
            loadJson("""
                    {
                        "_groups": {
                            "logs": ["minecraft:oak_log", "minecraft:spruce_log", "minecraft:birch_log"]
                        },
                        "#logs": { "leaf": 384 }
                    }
                    """);
            assertEquals(GooFraction.of(384), registry.table().lookup(id("minecraft:oak_log")).exact(GooTypes.LEAF));
            assertEquals(GooFraction.of(384), registry.table().lookup(id("minecraft:spruce_log")).exact(GooTypes.LEAF));
            assertEquals(GooFraction.of(384), registry.table().lookup(id("minecraft:birch_log")).exact(GooTypes.LEAF));
        }

        /**
         * A denied pseudo-tag marks all items as denied.
         */
        @Test
        void pseudoTagDeniedMarksAllItems() throws IOException {
            loadJson("""
                    {
                        "_groups": {
                            "ores": ["minecraft:coal_ore", "minecraft:iron_ore"]
                        },
                        "#ores": "denied"
                    }
                    """);
            assertTrue(registry.table().isDenied(id("minecraft:coal_ore")));
            assertTrue(registry.table().isDenied(id("minecraft:iron_ore")));
        }

        /**
         * Pseudo-tag values support $constant expressions.
         */
        @Test
        void pseudoTagValuesResolveConstants() throws IOException {
            loadJson("""
                    {
                        "_constants": { "log": 384 },
                        "_groups": {
                            "logs": ["minecraft:oak_log", "minecraft:spruce_log"]
                        },
                        "#logs": { "leaf": "$log" }
                    }
                    """);
            assertEquals(GooFraction.of(384), registry.table().lookup(id("minecraft:oak_log")).exact(GooTypes.LEAF));
            assertEquals(GooFraction.of(384), registry.table().lookup(id("minecraft:spruce_log")).exact(GooTypes.LEAF));
        }

        /**
         * Multiple groups all create pseudo-tags.
         */
        @Test
        void multipleGroupsAllParse() throws IOException {
            loadJson("""
                    {
                        "_groups": {
                            "logs": ["minecraft:oak_log"],
                            "stones": ["minecraft:stone", "minecraft:cobblestone"]
                        },
                        "#logs": { "leaf": 384 },
                        "#stones": { "rock": 240 }
                    }
                    """);
            assertEquals(GooFraction.of(384), registry.table().lookup(id("minecraft:oak_log")).exact(GooTypes.LEAF));
            assertEquals(GooFraction.of(240), registry.table().lookup(id("minecraft:stone")).exact(GooTypes.ROCK));
            assertEquals(GooFraction.of(240), registry.table().lookup(id("minecraft:cobblestone")).exact(GooTypes.ROCK));
        }

        /**
         * Groups coexist with individual entries.
         */
        @Test
        void groupsAndIndividualEntriesCoexist() throws IOException {
            loadJson("""
                    {
                        "minecraft:diamond": { "crystal": 12000 },
                        "_groups": {
                            "logs": ["minecraft:oak_log"]
                        },
                        "#logs": { "leaf": 384 }
                    }
                    """);
            assertEquals(GooFraction.of(12000), registry.table().lookup(id("minecraft:diamond")).exact(GooTypes.CRYSTAL));
            assertEquals(GooFraction.of(384), registry.table().lookup(id("minecraft:oak_log")).exact(GooTypes.LEAF));
        }

        /**
         * Parallel copy with scale: #wood = #logs * 3 / 4.
         */
        @Test
        void parallelCopyWithScaleInBaseValues() throws IOException {
            loadJson("""
                    {
                        "_groups": {
                            "logs": ["minecraft:oak_log", "minecraft:birch_log"],
                            "wood": ["minecraft:oak_wood", "minecraft:birch_wood"]
                        },
                        "#logs": { "leaf": 480 },
                        "#wood": "#logs * 3 / 4"
                    }
                    """);
            assertEquals(GooFraction.of(360), registry.table().lookup(id("minecraft:oak_wood")).exact(GooTypes.LEAF));
            assertEquals(GooFraction.of(360), registry.table().lookup(id("minecraft:birch_wood")).exact(GooTypes.LEAF));
        }

        /**
         * Parallel copy without scale: #stripped_logs = #logs (identity copy).
         */
        @Test
        void parallelCopyWithoutScale() throws IOException {
            loadJson("""
                    {
                        "_groups": {
                            "logs": ["minecraft:oak_log", "minecraft:birch_log"],
                            "stripped": ["minecraft:stripped_oak_log", "minecraft:stripped_birch_log"]
                        },
                        "#logs": { "leaf": 480 },
                        "#stripped": "#logs"
                    }
                    """);
            assertEquals(GooFraction.of(480), registry.table().lookup(id("minecraft:stripped_oak_log")).exact(GooTypes.LEAF));
            assertEquals(GooFraction.of(480), registry.table().lookup(id("minecraft:stripped_birch_log")).exact(GooTypes.LEAF));
        }

        /**
         * Parallel copy preserves multi-type values with scale.
         */
        @Test
        void parallelCopyMultiTypeWithScale() throws IOException {
            loadJson("""
                    {
                        "_groups": {
                            "ingots": ["minecraft:iron_ingot"],
                            "nuggets": ["minecraft:iron_nugget"]
                        },
                        "#ingots": { "metal": 1728, "rock": 432 },
                        "#nuggets": "#ingots * 1 / 9"
                    }
                    """);
            GooValue nugget = registry.table().lookup(id("minecraft:iron_nugget"));
            assertEquals(GooFraction.of(192), nugget.exact(GooTypes.METAL));  // 1728 / 9
            assertEquals(GooFraction.of(48), nugget.exact(GooTypes.ROCK));     // 432 / 9
        }

        /**
         * Unresolved pseudo-tag (no _groups entry, no MC tag) logs warning.
         */
        @Test
        void unresolvedPseudoTagSkipped() throws IOException {
            loadJson("""
                    {
                        "#nonexistent": { "rock": 100 }
                    }
                    """);
            // No items assigned, no crash
            assertNull(registry.table().lookup(id("minecraft:nonexistent")));
        }
    }

    // ── Conversions ────────────────────────────────────────────────────

    @Nested
    class Conversions {

        private void loadJson(String json) throws IOException {
            parseBaseValuesFromStream(
                    new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
            copyBaseToEffective();
        }

        /**
         * Single conversion applied to an individual item.
         */
        @Test
        void singleConversionOnItem() throws IOException {
            loadJson("""
                    {
                        "minecraft:copper_ingot": { "metal": 160, "rock": 40 },
                        "_conversions": {
                            "oxidation": "metal / 4 -> yore / 2",
                            "exposed": "@oxidation",
                            "minecraft:copper_ingot": "@exposed"
                        }
                    }
                    """);
            GooValue val = registry.table().lookup(id("minecraft:copper_ingot"));
            assertNotNull(val);
            assertEquals(GooFraction.of(120), val.exact(GooTypes.METAL)); // 160 - 40
            assertEquals(GooFraction.of(20), val.exact(GooTypes.YORE));    // 40 / 2
            assertEquals(GooFraction.of(40), val.exact(GooTypes.ROCK));    // untouched
        }

        /**
         * Stacked conversion: 2x oxidation.
         */
        @Test
        void stackedConversionOnItem() throws IOException {
            loadJson("""
                    {
                        "minecraft:copper_ingot": { "metal": 160 },
                        "_conversions": {
                            "oxidation": "metal / 4 -> yore / 2",
                            "weathered": "2 @oxidation",
                            "minecraft:copper_ingot": "@weathered"
                        }
                    }
                    """);
            GooValue val = registry.table().lookup(id("minecraft:copper_ingot"));
            assertNotNull(val);
            assertEquals(GooFraction.of(80), val.exact(GooTypes.METAL));  // 160 - 2*40
            assertEquals(GooFraction.of(40), val.exact(GooTypes.YORE));    // 2 * 20
        }

        /**
         * Conversion applied to pseudo-tag group.
         */
        @Test
        void conversionOnPseudoTag() throws IOException {
            loadJson("""
                    {
                        "_groups": {
                            "weathered_copper": [
                                "minecraft:weathered_copper",
                                "minecraft:weathered_cut_copper"
                            ]
                        },
                        "minecraft:weathered_copper": { "metal": 160 },
                        "minecraft:weathered_cut_copper": { "metal": 160 },
                        "_conversions": {
                            "oxidation": "metal / 4 -> yore / 2",
                            "weathered": "2 @oxidation",
                            "#weathered_copper": "@weathered"
                        }
                    }
                    """);
            GooValue copper = registry.table().lookup(id("minecraft:weathered_copper"));
            GooValue cut = registry.table().lookup(id("minecraft:weathered_cut_copper"));
            assertNotNull(copper);
            assertNotNull(cut);
            assertEquals(GooFraction.of(80), copper.exact(GooTypes.METAL));
            assertEquals(GooFraction.of(40), copper.exact(GooTypes.YORE));
            assertEquals(GooFraction.of(80), cut.exact(GooTypes.METAL));
            assertEquals(GooFraction.of(40), cut.exact(GooTypes.YORE));
        }

        /**
         * 3x oxidation (oxidized).
         */
        @Test
        void tripleStackConversion() throws IOException {
            loadJson("""
                    {
                        "minecraft:copper_ingot": { "metal": 160 },
                        "_conversions": {
                            "oxidation": "metal / 4 -> yore / 2",
                            "oxidized": "3 @oxidation",
                            "minecraft:copper_ingot": "@oxidized"
                        }
                    }
                    """);
            GooValue val = registry.table().lookup(id("minecraft:copper_ingot"));
            assertNotNull(val);
            assertEquals(GooFraction.of(40), val.exact(GooTypes.METAL));   // 160 - 3*40
            assertEquals(GooFraction.of(60), val.exact(GooTypes.YORE));     // 3 * 20
        }

        /**
         * Pre-derivation conversions propagate through recipes.
         */
        @Test
        void preConversionPropagatesThroughRecipes() throws IOException {
            parseBaseValuesFromStream(
                    new ByteArrayInputStream("""
                            {
                                "minecraft:exposed_copper": { "metal": 160 },
                                "_conversions": {
                                    "oxidation": "metal / 4 -> yore / 2",
                                    "minecraft:exposed_copper": "@oxidation"
                                }
                            }
                            """.getBytes(StandardCharsets.UTF_8)));
            // Pre-conversion modifies base: exposed_copper = {metal: 120, yore: 20}
            // Recipe derives a slab from it
            List<RecipeInput> recipes = List.of(
                    recipe("minecraft:exposed_copper_slab", 2, slot("minecraft:exposed_copper"))
            );
            registry.deriveFromRecipeInputs(recipes, false);
            GooValue slab = registry.table().lookup(id("minecraft:exposed_copper_slab"));
            assertNotNull(slab);
            assertEquals(GooFraction.of(60), slab.exact(GooTypes.METAL));  // 120 / 2
            assertEquals(GooFraction.of(10), slab.exact(GooTypes.YORE));    // 20 / 2
        }

        /**
         * Post-derivation conversions modify effective values after recipes.
         */
        @Test
        void postConversionAppliesAfterDerivation() throws IOException {
            parseBaseValuesFromStream(
                    new ByteArrayInputStream("""
                            {
                                "minecraft:copper_ingot": { "metal": 160 },
                                "_post_conversions": {
                                    "oxidation": "metal / 4 -> yore / 2",
                                    "minecraft:copper_ingot": "@oxidation"
                                }
                            }
                            """.getBytes(StandardCharsets.UTF_8)));
            // Post-conversion: base value is unchanged (metal: 160)
            // Recipes derive from unmodified base
            List<RecipeInput> recipes = List.of(
                    recipe("minecraft:copper_block", 1,
                            slot("minecraft:copper_ingot"), slot("minecraft:copper_ingot"),
                            slot("minecraft:copper_ingot"), slot("minecraft:copper_ingot"),
                            slot("minecraft:copper_ingot"), slot("minecraft:copper_ingot"),
                            slot("minecraft:copper_ingot"), slot("minecraft:copper_ingot"),
                            slot("minecraft:copper_ingot"))
            );
            registry.deriveFromRecipeInputs(recipes, false);
            // copper_block derived from unmodified ingot: 9 * 160 = 1440 metal
            GooValue block = registry.table().lookup(id("minecraft:copper_block"));
            assertNotNull(block);
            assertEquals(GooFraction.of(1440), block.exact(GooTypes.METAL));
            // But copper_ingot itself got post-converted
            GooValue ingot = registry.table().lookup(id("minecraft:copper_ingot"));
            assertNotNull(ingot);
            assertEquals(GooFraction.of(120), ingot.exact(GooTypes.METAL)); // 160 - 40
            assertEquals(GooFraction.of(20), ingot.exact(GooTypes.YORE));    // 40 / 2
        }

        /**
         * Parallel copy from source tag + conversion chain in post_conversions.
         */
        @Test
        void parallelCopyWithConversionChain() throws IOException {
            loadJson("""
                    {
                        "_groups": {
                            "originals": ["minecraft:copper_block", "minecraft:cut_copper"],
                            "exposed": ["minecraft:exposed_copper", "minecraft:exposed_cut_copper"]
                        },
                        "minecraft:copper_block": { "metal": 160 },
                        "minecraft:cut_copper": { "metal": 80 },
                        "_post_conversions": {
                            "oxidation": "metal / 4 -> yore / 2",
                            "exposed": "@oxidation",
                            "#exposed": "#originals @exposed"
                        }
                    }
                    """);
            // exposed_copper gets copper_block's value (160 metal), then 1x oxidation
            GooValue exposed = registry.table().lookup(id("minecraft:exposed_copper"));
            assertNotNull(exposed);
            assertEquals(GooFraction.of(120), exposed.exact(GooTypes.METAL)); // 160 - 40
            assertEquals(GooFraction.of(20), exposed.exact(GooTypes.YORE));    // 40 / 2

            // exposed_cut_copper gets cut_copper's value (80 metal), then 1x oxidation
            GooValue exposedCut = registry.table().lookup(id("minecraft:exposed_cut_copper"));
            assertNotNull(exposedCut);
            assertEquals(GooFraction.of(60), exposedCut.exact(GooTypes.METAL)); // 80 - 20
            assertEquals(GooFraction.of(10), exposedCut.exact(GooTypes.YORE));   // 20 / 2
        }

        /**
         * Chained parallel copy: weathered copies from exposed (already converted).
         */
        @Test
        void chainedParallelCopy() throws IOException {
            loadJson("""
                    {
                        "_groups": {
                            "originals": ["minecraft:copper_block"],
                            "exposed": ["minecraft:exposed_copper"],
                            "weathered": ["minecraft:weathered_copper"]
                        },
                        "minecraft:copper_block": { "metal": 160 },
                        "_post_conversions": {
                            "oxidation": "metal / 4 -> yore / 2",
                            "exposed_conv": "@oxidation",
                            "weathered_conv": "2 @oxidation",
                            "#exposed": "#originals @exposed_conv",
                            "#weathered": "#exposed @weathered_conv"
                        }
                    }
                    """);
            // exposed_copper: copy 160 metal, 1x oxidation -> 120 metal, 20 yore
            GooValue exposed = registry.table().lookup(id("minecraft:exposed_copper"));
            assertNotNull(exposed);
            assertEquals(GooFraction.of(120), exposed.exact(GooTypes.METAL));
            assertEquals(GooFraction.of(20), exposed.exact(GooTypes.YORE));

            // weathered_copper: copy from exposed (120 metal, 20 yore), 2x oxidation
            // 2 * (120/4) = 60 removed, 2 * (120/4/2) = 30 added
            GooValue weathered = registry.table().lookup(id("minecraft:weathered_copper"));
            assertNotNull(weathered);
            assertEquals(GooFraction.of(60), weathered.exact(GooTypes.METAL));  // 120 - 60
            assertEquals(GooFraction.of(50), weathered.exact(GooTypes.YORE));    // 20 + 30
        }

        /**
         * Additive conversion: +$waxed adds flat jelly to copied copper items.
         */
        @Test
        void additiveConversionAddsFlatValue() throws IOException {
            loadJson("""
                    {
                        "_constants": { "waxed": { "jelly": 48 } },
                        "_groups": {
                            "copper": ["minecraft:copper_block", "minecraft:cut_copper"],
                            "waxed": ["minecraft:waxed_copper_block", "minecraft:waxed_cut_copper"]
                        },
                        "minecraft:copper_block": { "metal": 160 },
                        "minecraft:cut_copper": { "metal": 80 },
                        "_post_conversions": {
                            "waxed": "+$waxed",
                            "#waxed": "#copper @waxed"
                        }
                    }
                    """);
            GooValue waxedBlock = registry.table().lookup(id("minecraft:waxed_copper_block"));
            assertNotNull(waxedBlock);
            assertEquals(GooFraction.of(160), waxedBlock.exact(GooTypes.METAL)); // copied from copper_block
            assertEquals(GooFraction.of(48), waxedBlock.exact(GooTypes.JELLY));   // added by +$waxed

            GooValue waxedCut = registry.table().lookup(id("minecraft:waxed_cut_copper"));
            assertNotNull(waxedCut);
            assertEquals(GooFraction.of(80), waxedCut.exact(GooTypes.METAL));
            assertEquals(GooFraction.of(48), waxedCut.exact(GooTypes.JELLY));
        }
    }

    // ── Item reference expressions ───────────────────────────────────────

    @Nested
    class ItemReferenceExpressions {

        private void loadJson(String json) throws IOException {
            parseBaseValuesFromStream(
                    new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
            copyBaseToEffective();
        }

        /**
         * String constant referencing a tree constant promotes to tree.
         */
        @Test
        void stringConstantReferencingTreePromotes() throws IOException {
            loadJson("""
                    {
                        "_constants": {
                            "base": 48,
                            "nugget": { "metal": "$base" },
                            "ingot": "9 $nugget",
                            "block": "9 $ingot"
                        },
                        "minecraft:iron_block": "$block"
                    }
                    """);
            GooValue val = registry.table().lookup(id("minecraft:iron_block"));
            assertNotNull(val, "iron_block should have a value");
            // nugget = {metal: 48}, ingot = 9 * {metal: 48} = {metal: 432}
            // block = 9 * {metal: 432} = {metal: 3888}
            assertEquals(GooFraction.of(3888), val.exact(GooTypes.METAL));
        }

        /**
         * Adding two items merges their goo types.
         */
        @Test
        void addTwoItems() throws IOException {
            loadJson("""
                    {
                        "minecraft:copper_block": { "metal": 1440, "pulse": 1440 },
                        "minecraft:carved_pumpkin": { "leaf": 480, "jelly": 120 },
                        "minecraft:copper_golem": "minecraft:copper_block + minecraft:carved_pumpkin"
                    }
                    """);
            GooValue golem = registry.table().lookup(id("minecraft:copper_golem"));
            assertEquals(GooFraction.of(1440), golem.exact(GooTypes.METAL));
            assertEquals(GooFraction.of(1440), golem.exact(GooTypes.PULSE));
            assertEquals(GooFraction.of(480), golem.exact(GooTypes.LEAF));
            assertEquals(GooFraction.of(120), golem.exact(GooTypes.JELLY));
        }

        /**
         * Subtracting items does per-type subtraction.
         */
        @Test
        void subtractItems() throws IOException {
            loadJson("""
                    {
                        "minecraft:iron_block": { "metal": 90 },
                        "minecraft:iron_ingot": { "metal": 10 },
                        "minecraft:leftover": "minecraft:iron_block - minecraft:iron_ingot"
                    }
                    """);
            assertEquals(GooFraction.of(80), registry.table().lookup(id("minecraft:leftover")).exact(GooTypes.METAL));
        }

        /**
         * Multiplying an item by a scalar scales all types.
         */
        @Test
        void multiplyItemByScalar() throws IOException {
            loadJson("""
                    {
                        "minecraft:iron_ingot": { "metal": 10 },
                        "minecraft:iron_block": "minecraft:iron_ingot * 9"
                    }
                    """);
            assertEquals(GooFraction.of(90), registry.table().lookup(id("minecraft:iron_block")).exact(GooTypes.METAL));
        }

        /**
         * Dividing an item by a scalar divides all types.
         */
        @Test
        void divideItemByScalar() throws IOException {
            loadJson("""
                    {
                        "minecraft:iron_block": { "metal": 90 },
                        "minecraft:iron_ingot": "minecraft:iron_block / 9"
                    }
                    """);
            assertEquals(GooFraction.of(10), registry.table().lookup(id("minecraft:iron_ingot")).exact(GooTypes.METAL));
        }

        /**
         * Scalar on the left side of multiplication works.
         */
        @Test
        void scalarTimesItem() throws IOException {
            loadJson("""
                    {
                        "minecraft:iron_ingot": { "metal": 10 },
                        "minecraft:iron_block": "9 * minecraft:iron_ingot"
                    }
                    """);
            assertEquals(GooFraction.of(90), registry.table().lookup(id("minecraft:iron_block")).exact(GooTypes.METAL));
        }

        /**
         * Parenthesized sub-expressions work.
         */
        @Test
        void parenthesizedExpression() throws IOException {
            loadJson("""
                    {
                        "minecraft:copper_block": { "metal": 100, "pulse": 50 },
                        "minecraft:pumpkin": { "leaf": 200 },
                        "minecraft:thing": "(minecraft:copper_block + minecraft:pumpkin) * 2"
                    }
                    """);
            GooValue thing = registry.table().lookup(id("minecraft:thing"));
            assertEquals(GooFraction.of(200), thing.exact(GooTypes.METAL));
            assertEquals(GooFraction.of(100), thing.exact(GooTypes.PULSE));
            assertEquals(GooFraction.of(400), thing.exact(GooTypes.LEAF));
        }

        /**
         * $constants can be used as scalars in item expressions.
         */
        @Test
        void constantsAsScalars() throws IOException {
            loadJson("""
                    {
                        "_constants": { "count": 9 },
                        "minecraft:iron_ingot": { "metal": 10 },
                        "minecraft:iron_block": "minecraft:iron_ingot * $count"
                    }
                    """);
            assertEquals(GooFraction.of(90), registry.table().lookup(id("minecraft:iron_block")).exact(GooTypes.METAL));
        }

        /**
         * Operator precedence: multiply before add.
         */
        @Test
        void precedenceMultiplyBeforeAdd() throws IOException {
            loadJson("""
                    {
                        "minecraft:a": { "metal": 10 },
                        "minecraft:b": { "rock": 5 },
                        "minecraft:c": "minecraft:a + minecraft:b * 3"
                    }
                    """);
            GooValue c = registry.table().lookup(id("minecraft:c"));
            assertEquals(GooFraction.of(10), c.exact(GooTypes.METAL));
            assertEquals(GooFraction.of(15), c.exact(GooTypes.ROCK));
        }

        /**
         * Dot notation pulls a single type as a scalar, usable in expressions.
         */
        @Test
        void dotNotationExtractsSingleType() throws IOException {
            loadJson("""
                    {
                        "minecraft:coal": { "rock": 48, "blaze": 336 },
                        "minecraft:thing": { "blaze": "minecraft:coal.blaze" }
                    }
                    """);
            assertEquals(GooFraction.of(336), registry.table().lookup(id("minecraft:thing")).exact(GooTypes.BLAZE));
            assertEquals(GooFraction.of(0), registry.table().lookup(id("minecraft:thing")).exact(GooTypes.ROCK));
        }

        /**
         * Dot notation in arithmetic: minecraft:coal.blaze * 2.
         */
        @Test
        void dotNotationInArithmetic() throws IOException {
            loadJson("""
                    {
                        "minecraft:coal": { "rock": 48, "blaze": 336 },
                        "minecraft:thing": { "blaze": "minecraft:coal.blaze * 2" }
                    }
                    """);
            assertEquals(GooFraction.of(672), registry.table().lookup(id("minecraft:thing")).exact(GooTypes.BLAZE));
        }

        /**
         * Dot notation as a scalar in item-level expression returns single-type GooValue.
         */
        @Test
        void dotNotationInItemExpression() throws IOException {
            loadJson("""
                    {
                        "minecraft:coal": { "rock": 48, "blaze": 336 },
                        "minecraft:iron_ingot": { "metal": 10 },
                        "minecraft:thing": "minecraft:iron_ingot + minecraft:coal.blaze"
                    }
                    """);
            GooValue thing = registry.table().lookup(id("minecraft:thing"));
            assertEquals(GooFraction.of(10), thing.exact(GooTypes.METAL));
            assertEquals(GooFraction.of(336), thing.exact(GooTypes.BLAZE));
            assertEquals(GooFraction.of(0), thing.exact(GooTypes.ROCK));
        }

        /**
         * Bare word item ref (no namespace) defaults to minecraft: and resolves.
         */
        @Test
        void bareWordItemRef() throws IOException {
            loadJson("""
                    {
                        "minecraft:iron_ingot": { "metal": 10 },
                        "minecraft:iron_block": "iron_ingot * 9"
                    }
                    """);
            assertEquals(GooFraction.of(90), registry.table().lookup(id("minecraft:iron_block")).exact(GooTypes.METAL));
        }

        /**
         * Tree constant added to item produces merged GooValue.
         */
        @Test
        void treeConstantAddedToItem() throws IOException {
            loadJson("""
                    {
                        "_constants": { "stripped": { "nether": 50 } },
                        "minecraft:dark_oak_log": { "leaf": 384 },
                        "minecraft:stripped_dark_oak_log": "dark_oak_log + $stripped"
                    }
                    """);
            GooValue val = registry.table().lookup(id("minecraft:stripped_dark_oak_log"));
            assertEquals(GooFraction.of(384), val.exact(GooTypes.LEAF));
            assertEquals(GooFraction.of(50), val.exact(GooTypes.NETHER));
        }

        /**
         * Tree constant multiplied then added to item.
         */
        @Test
        void treeConstantMultipliedThenAdded() throws IOException {
            loadJson("""
                    {
                        "_constants": { "bonus": { "crystal": 100 } },
                        "minecraft:obsidian": { "rock": 500, "nether": 200 },
                        "minecraft:fancy_obsidian": "obsidian + $bonus * 2"
                    }
                    """);
            GooValue val = registry.table().lookup(id("minecraft:fancy_obsidian"));
            assertEquals(GooFraction.of(500), val.exact(GooTypes.ROCK));
            assertEquals(GooFraction.of(200), val.exact(GooTypes.NETHER));
            assertEquals(GooFraction.of(200), val.exact(GooTypes.CRYSTAL));
        }

        /**
         * Tree constant in parenthesized expression.
         */
        @Test
        void treeConstantInParens() throws IOException {
            loadJson("""
                    {
                        "_constants": { "bonus": { "crystal": 10 } },
                        "minecraft:iron_ingot": { "metal": 10 },
                        "minecraft:thing": "(iron_ingot + $bonus) * 3"
                    }
                    """);
            GooValue val = registry.table().lookup(id("minecraft:thing"));
            assertEquals(GooFraction.of(30), val.exact(GooTypes.METAL));
            assertEquals(GooFraction.of(30), val.exact(GooTypes.CRYSTAL));
        }

        /**
         * Implicit multiplication: "4 iron_ingot" == "iron_ingot * 4".
         */
        @Test
        void implicitMultiplication() throws IOException {
            loadJson("""
                    {
                        "minecraft:iron_ingot": { "metal": 10 },
                        "minecraft:iron_block": "9 iron_ingot"
                    }
                    """);
            assertEquals(GooFraction.of(90), registry.table().lookup(id("minecraft:iron_block")).exact(GooTypes.METAL));
        }

        /**
         * Implicit multiplication with tree constant: "2 $bonus".
         */
        @Test
        void implicitMultiplicationWithTreeConstant() throws IOException {
            loadJson("""
                    {
                        "_constants": { "bonus": { "nether": 50 } },
                        "minecraft:iron_ingot": { "metal": 10 },
                        "minecraft:thing": "iron_ingot + 2 $bonus"
                    }
                    """);
            GooValue val = registry.table().lookup(id("minecraft:thing"));
            assertEquals(GooFraction.of(10), val.exact(GooTypes.METAL));
            assertEquals(GooFraction.of(100), val.exact(GooTypes.NETHER));
        }

        /**
         * Implicit multiplication in complex expression: "obsidian + 4 lapis_lazuli".
         */
        @Test
        void implicitMultiplicationInAddition() throws IOException {
            loadJson("""
                    {
                        "minecraft:lapis_lazuli": { "crystal": 20, "yore": 10 },
                        "minecraft:obsidian": { "rock": 100 },
                        "minecraft:crying_obsidian": "obsidian + 4 lapis_lazuli"
                    }
                    """);
            GooValue val = registry.table().lookup(id("minecraft:crying_obsidian"));
            assertEquals(GooFraction.of(100), val.exact(GooTypes.ROCK));
            assertEquals(GooFraction.of(80), val.exact(GooTypes.CRYSTAL));
            assertEquals(GooFraction.of(40), val.exact(GooTypes.YORE));
        }

        /**
         * Unary minus on scalar: "-2 $base" negates the multiplier.
         */
        @Test
        void unaryMinusScalarTimesTreeConstant() throws IOException {
            loadJson("""
                    {
                        "_constants": { "base": { "metal": 100 } },
                        "minecraft:copper_ingot": { "metal": 160 },
                        "minecraft:exposed_copper": "copper_ingot + -2 $base"
                    }
                    """);
            GooValue val = registry.table().lookup(id("minecraft:exposed_copper"));
            // 160 + (-2 * 100) = -40 metal; negative -> excluded by effective value guard
            // But the interstitial expression itself should produce -40
            // Since effective values reject negatives, exposed_copper won't appear
            assertNull(registry.table().lookup(id("minecraft:exposed_copper")),
                    "Negative final value should be rejected from effective values");
        }

        /**
         * Unary minus on a tree constant: "-$bonus" negates all types.
         */
        @Test
        void unaryMinusOnTreeConstant() throws IOException {
            loadJson("""
                    {
                        "_constants": { "drain": { "metal": 64 } },
                        "minecraft:copper_ingot": { "metal": 200, "yore": 30 },
                        "minecraft:exposed_copper": "copper_ingot + -$drain"
                    }
                    """);
            GooValue val = registry.table().lookup(id("minecraft:exposed_copper"));
            assertNotNull(val);
            assertEquals(GooFraction.of(136), val.exact(GooTypes.METAL)); // 200 - 64
            assertEquals(GooFraction.of(30), val.exact(GooTypes.YORE));   // unchanged
        }

        /**
         * Unary minus combined with addition: tree constant with mixed signs.
         */
        @Test
        void treeConstantWithNegativeType() throws IOException {
            loadJson("""
                    {
                        "_constants": { "exposed": { "yore": 32, "metal": -64 } },
                        "minecraft:copper_ingot": { "metal": 200, "rock": 50 },
                        "minecraft:exposed_copper": "copper_ingot + $exposed"
                    }
                    """);
            GooValue val = registry.table().lookup(id("minecraft:exposed_copper"));
            assertNotNull(val);
            assertEquals(GooFraction.of(136), val.exact(GooTypes.METAL)); // 200 - 64
            assertEquals(GooFraction.of(32), val.exact(GooTypes.YORE));   // 0 + 32
            assertEquals(GooFraction.of(50), val.exact(GooTypes.ROCK));   // unchanged
        }

        /**
         * Tree constant dot notation in item-level expression: produces single-type GooValue.
         */
        @Test
        void treeConstantDotInItemExpression() throws IOException {
            loadJson("""
                    {
                        "_constants": { "log": { "leaf": 480 } },
                        "minecraft:oak_wood": "$log.leaf * 3 / 4"
                    }
                    """);
            GooValue val = registry.table().lookup(id("minecraft:oak_wood"));
            assertNotNull(val);
            assertEquals(GooFraction.of(360), val.exact(GooTypes.LEAF)); // 480 * 3 / 4
            assertEquals(1, val.typeCount()); // only leaf, not the full tree
        }

        /**
         * Tree constant dot notation in per-type expression: resolves to scalar int.
         */
        @Test
        void treeConstantDotInPerTypeExpression() throws IOException {
            loadJson("""
                    {
                        "_constants": { "log": { "leaf": 480 } },
                        "minecraft:stem": { "leaf": "$log.leaf" }
                    }
                    """);
            GooValue val = registry.table().lookup(id("minecraft:stem"));
            assertNotNull(val);
            assertEquals(GooFraction.of(480), val.exact(GooTypes.LEAF));
        }

        /**
         * Tree constant dot notation with arithmetic in per-type context.
         */
        @Test
        void treeConstantDotWithArithmeticPerType() throws IOException {
            loadJson("""
                    {
                        "_constants": { "log": { "leaf": 480 } },
                        "minecraft:oak_wood": { "leaf": "$log.leaf * 3 / 4" }
                    }
                    """);
            GooValue val = registry.table().lookup(id("minecraft:oak_wood"));
            assertNotNull(val);
            assertEquals(GooFraction.of(360), val.exact(GooTypes.LEAF));
        }

        /**
         * Binary subtraction still works after unary minus support.
         */
        @Test
        void binarySubtractionStillWorks() throws IOException {
            loadJson("""
                    {
                        "minecraft:iron_block": { "metal": 90 },
                        "minecraft:iron_ingot": { "metal": 10 },
                        "minecraft:scrap": "iron_block - 2 iron_ingot"
                    }
                    """);
            assertEquals(GooFraction.of(70), registry.table().lookup(id("minecraft:scrap")).exact(GooTypes.METAL));
        }
    }

    // ── Effective Cache ───────────────────────────────────────────────────

    @Nested
    class EffectiveCache {

        @TempDir
        Path tempDir;

        /**
         * Loading a flat cache populates effective values without base values.
         */
        @Test
        void loadEffectiveCache_populatesWithoutBaseValues() throws IOException {
            Path cacheFile = tempDir.resolve("cache.json");
            Files.writeString(cacheFile,
                    "{\"minecraft:iron_ingot\": {\"metal\": 10}}", StandardCharsets.UTF_8);
            registry.setEffectiveCachePath(cacheFile);

            registry.loadEffectiveCache();

            GooValue val = registry.table().lookup(id("minecraft:iron_ingot"));
            assertNotNull(val);
            assertEquals(GooFraction.of(10), val.exact(GooTypes.METAL));
            assertEquals(0, registry.diagnostics().baseSize());
        }

        /**
         * Missing cache file leaves effective values empty.
         */
        @Test
        void loadEffectiveCache_missingFileIsEmpty() {
            registry.setEffectiveCachePath(tempDir.resolve("nonexistent.json"));

            registry.loadEffectiveCache();

            assertEquals(0, registry.table().size());
        }

        /**
         * Round-trip: save effective values, clear, reload, and verify match.
         */
        @Test
        void saveAndLoadRoundTrip() {
            Path cacheFile = tempDir.resolve("roundtrip.json");
            registry.setEffectiveCachePath(cacheFile);

            setBaseValues(Map.of(
                    id("minecraft:iron_ingot"), goo(GooTypes.METAL, 10)
            ));
            List<RecipeInput> recipes = List.of(
                    recipe("minecraft:iron_block", 1,
                            slot("minecraft:iron_ingot"), slot("minecraft:iron_ingot"),
                            slot("minecraft:iron_ingot"), slot("minecraft:iron_ingot"),
                            slot("minecraft:iron_ingot"), slot("minecraft:iron_ingot"),
                            slot("minecraft:iron_ingot"), slot("minecraft:iron_ingot"),
                            slot("minecraft:iron_ingot"))
            );
            registry.deriveFromRecipeInputs(recipes, false);

            Map<Identifier, GooValue> before = Map.copyOf(registry.table().getEffectiveValues());
            registry.saveEffectiveValues();

            // A fresh registry, as the next server start stands, reloads from the cache
            registry = new GooValueRegistry();
            assertEquals(0, registry.table().size());

            registry.setEffectiveCachePath(cacheFile);
            registry.loadEffectiveCache();

            assertEquals(before.size(), registry.table().size());
            for (Map.Entry<Identifier, GooValue> entry : before.entrySet()) {
                GooValue reloaded = registry.table().lookup(entry.getKey());
                assertNotNull(reloaded, "Missing after reload: " + entry.getKey());
                assertEquals(entry.getValue().totalExact(), reloaded.totalExact(),
                        "Mismatch for " + entry.getKey());
            }
        }
    }

    // ── Expression validation ────────────────────────────────────────────

    @Nested
    class ExpressionValidation {

        /**
         * Forgotten $ prefix on a known constant is flagged.
         */
        @Test
        void forgottenDollarPrefix() {
            List<String> warnings = validateJsonString("""
                    {
                        "_constants": { "iron": 100 },
                        "minecraft:foo": { "metal": "iron * 3" }
                    }
                    """);
            assertTrue(warnings.stream().anyMatch(w -> w.contains("iron") && w.contains("$")),
                    "Should warn about missing $ prefix: " + warnings);
        }

        /**
         * Unknown constant reference is flagged.
         */
        @Test
        void unknownConstant() {
            List<String> warnings = validateJsonString("""
                    {
                        "minecraft:foo": { "metal": "$nonexistent" }
                    }
                    """);
            assertTrue(warnings.stream().anyMatch(w -> w.contains("nonexistent")),
                    "Should warn about unknown constant: " + warnings);
        }

        /**
         * Out-of-order item reference is flagged.
         */
        @Test
        void outOfOrderReference() {
            List<String> warnings = validateJsonString("""
                    {
                        "minecraft:thing": "minecraft:iron_ingot * 2",
                        "minecraft:iron_ingot": { "metal": 10 }
                    }
                    """);
            assertTrue(warnings.stream().anyMatch(w -> w.contains("iron_ingot") && w.contains("not defined above")),
                    "Should warn about out-of-order reference: " + warnings);
        }

        /**
         * Valid expressions produce no warnings.
         */
        @Test
        void validExpressionsNoWarnings() {
            List<String> warnings = validateJsonString("""
                    {
                        "_constants": { "iron": 100 },
                        "minecraft:iron_ingot": { "metal": "$iron" },
                        "minecraft:iron_block": "minecraft:iron_ingot * 9"
                    }
                    """);
            assertTrue(warnings.isEmpty(), "Should have no warnings: " + warnings);
        }

        /**
         * Tree constant ref in expression doesn't produce a warning.
         */
        @Test
        void treeConstantRefNoWarning() {
            List<String> warnings = validateJsonString("""
                    {
                        "_constants": { "stripped": { "nether": 50 } },
                        "minecraft:dark_oak_log": { "leaf": 384 },
                        "minecraft:stripped_dark_oak_log": "minecraft:dark_oak_log + $stripped"
                    }
                    """);
            assertTrue(warnings.isEmpty(), "Should have no warnings: " + warnings);
        }

        /**
         * Bare word item ref in expression doesn't produce a warning.
         */
        @Test
        void bareWordItemRefNoWarning() {
            List<String> warnings = validateJsonString("""
                    {
                        "minecraft:iron_ingot": { "metal": 10 },
                        "minecraft:iron_block": "iron_ingot * 9"
                    }
                    """);
            assertTrue(warnings.isEmpty(), "Should have no warnings: " + warnings);
        }

        /**
         * Dot notation referencing an item above is valid.
         */
        @Test
        void validDotNotation() {
            List<String> warnings = validateJsonString("""
                    {
                        "minecraft:coal": { "rock": 48, "blaze": 336 },
                        "minecraft:thing": { "blaze": "minecraft:coal.blaze * 2" }
                    }
                    """);
            assertTrue(warnings.isEmpty(), "Should have no warnings: " + warnings);
        }

        /**
         * Dot notation referencing an item below is flagged.
         */
        @Test
        void outOfOrderDotNotation() {
            List<String> warnings = validateJsonString("""
                    {
                        "minecraft:thing": { "blaze": "minecraft:coal.blaze * 2" },
                        "minecraft:coal": { "rock": 48, "blaze": 336 }
                    }
                    """);
            assertTrue(warnings.stream().anyMatch(w -> w.contains("coal") && w.contains("not defined above")),
                    "Should warn about out-of-order dot-notation reference: " + warnings);
        }
    }

    // ── Datapack Merging ─────────────────────────────────────────────────

    @Nested
    class DatapackMerging {

        /**
         * Parses a JSON string into a JsonObject for layer construction.
         */
        private JsonObject json(String raw) {
            return JsonParser.parseString(raw).getAsJsonObject();
        }

        /**
         * A later pack's item definition overwrites an earlier one.
         */
        @Test
        void laterPackOverridesItem() {
            JsonObject base = json("""
                    { "minecraft:stick": { "jelly": 5 } }
                    """);
            JsonObject overlay = json("""
                    { "minecraft:stick": { "jelly": 10 } }
                    """);
            JsonObject merged = GooValueMerger.mergeBaseValueJsonLayers(List.of(base, overlay));

            assertEquals(10, merged.getAsJsonObject("minecraft:stick").get("jelly").getAsInt());
        }

        /**
         * A later pack can add items not present in the earlier pack.
         */
        @Test
        void laterPackAddsNewItem() {
            JsonObject base = json("""
                    { "minecraft:stick": { "jelly": 5 } }
                    """);
            JsonObject overlay = json("""
                    { "minecraft:coal": { "blaze": 20 } }
                    """);
            JsonObject merged = GooValueMerger.mergeBaseValueJsonLayers(List.of(base, overlay));

            assertTrue(merged.has("minecraft:stick"), "Base item should survive");
            assertTrue(merged.has("minecraft:coal"), "Overlay item should appear");
        }

        /**
         * Constants merge at inner key level: new keys add, existing keys overwrite.
         */
        @Test
        void constantsMergeAtKeyLevel() {
            JsonObject base = json("""
                    { "_constants": { "base": 1000, "stone": 240 } }
                    """);
            JsonObject overlay = json("""
                    { "_constants": { "base": 2000 } }
                    """);
            JsonObject merged = GooValueMerger.mergeBaseValueJsonLayers(List.of(base, overlay));

            JsonObject constants = merged.getAsJsonObject("_constants");
            assertEquals(2000, constants.get("base").getAsInt(), "Overridden constant");
            assertEquals(240, constants.get("stone").getAsInt(), "Preserved constant");
        }

        /**
         * Groups merge at inner key level: new groups add, existing groups overwrite.
         */
        @Test
        void groupsMergeAtKeyLevel() {
            JsonObject base = json("""
                    {
                        "_groups": {
                            "logs": { "value": { "jelly": 10 }, "items": ["minecraft:oak_log"] },
                            "ores": { "value": "denied", "items": ["minecraft:iron_ore"] }
                        }
                    }
                    """);
            JsonObject overlay = json("""
                    {
                        "_groups": {
                            "logs": { "value": { "jelly": 20 }, "items": ["minecraft:birch_log"] }
                        }
                    }
                    """);
            JsonObject merged = GooValueMerger.mergeBaseValueJsonLayers(List.of(base, overlay));

            JsonObject groups = merged.getAsJsonObject("_groups");
            assertTrue(groups.has("ores"), "Preserved group from base");
            assertEquals(20, groups.getAsJsonObject("logs")
                    .getAsJsonObject("value").get("jelly").getAsInt(), "Overridden group");
        }

        /**
         * A single layer passes through unchanged.
         */
        @Test
        void singleLayerIdenticalToInput() {
            JsonObject layer = json("""
                    {
                        "_constants": { "base": 1000 },
                        "minecraft:stick": { "jelly": 5 }
                    }
                    """);
            JsonObject merged = GooValueMerger.mergeBaseValueJsonLayers(List.of(layer));

            assertEquals(1000, merged.getAsJsonObject("_constants").get("base").getAsInt());
            assertEquals(5, merged.getAsJsonObject("minecraft:stick").get("jelly").getAsInt());
        }

        /**
         * An empty layer list produces an empty JsonObject.
         */
        @Test
        void emptyLayerListProducesEmptyObject() {
            JsonObject merged = GooValueMerger.mergeBaseValueJsonLayers(List.of());

            assertEquals(0, merged.size());
        }

        /**
         * A later pack can deny a previously valued item.
         */
        @Test
        void laterPackCanDenyPreviouslyValuedItem() {
            JsonObject base = json("""
                    { "minecraft:iron_ore": { "metal": 100 } }
                    """);
            JsonObject overlay = json("""
                    { "minecraft:iron_ore": "denied" }
                    """);
            JsonObject merged = GooValueMerger.mergeBaseValueJsonLayers(List.of(base, overlay));

            assertEquals("denied", merged.get("minecraft:iron_ore").getAsString());
        }
    }

    // ── Tag Expansion ────────────────────────────────────────────────────

    @Nested
    class TagExpansion {

        /**
         * Parses a JSON string into a JsonObject.
         */
        private JsonObject json(String raw) {
            return JsonParser.parseString(raw).getAsJsonObject();
        }

        /**
         * Builds a tag resolver backed by a map of tag ID to member set.
         */
        private Function<Identifier, Set<Identifier>> resolver(Map<Identifier, Set<Identifier>> tags) {
            return tagId -> tags.getOrDefault(tagId, Collections.emptySet());
        }

        /**
         * A tag key expands to all members with the tag's value.
         */
        @Test
        void tagExpandsToAllMembers() {
            JsonObject input = json("""
                    { "#test:planks": { "leaf": 100 } }
                    """);
            Map<Identifier, Set<Identifier>> tags = Map.of(
                    id("test:planks"), Set.of(id("minecraft:oak_planks"), id("minecraft:birch_planks"))
            );

            JsonObject result = GooValueMerger.expandTagEntries(input, resolver(tags));

            assertTrue(result.has("minecraft:oak_planks"));
            assertTrue(result.has("minecraft:birch_planks"));
            assertFalse(result.has("#test:planks"));
            assertEquals(100, result.getAsJsonObject("minecraft:oak_planks").get("leaf").getAsInt());
            assertEquals(100, result.getAsJsonObject("minecraft:birch_planks").get("leaf").getAsInt());
        }

        /**
         * An explicit entry after a tag overwrites that member (last-in-wins).
         */
        @Test
        void explicitEntryAfterTagOverrides() {
            JsonObject input = json("""
                    { "#test:planks": { "leaf": 100 }, "minecraft:oak_planks": { "leaf": 200 } }
                    """);
            Map<Identifier, Set<Identifier>> tags = Map.of(
                    id("test:planks"), Set.of(id("minecraft:oak_planks"), id("minecraft:birch_planks"))
            );

            JsonObject result = GooValueMerger.expandTagEntries(input, resolver(tags));

            assertEquals(200, result.getAsJsonObject("minecraft:oak_planks").get("leaf").getAsInt());
            assertEquals(100, result.getAsJsonObject("minecraft:birch_planks").get("leaf").getAsInt());
        }

        /**
         * A tag after an explicit entry overwrites it (last-in-wins).
         */
        @Test
        void explicitEntryBeforeTagIsOverridden() {
            JsonObject input = json("""
                    { "minecraft:oak_planks": { "leaf": 200 }, "#test:planks": { "leaf": 100 } }
                    """);
            Map<Identifier, Set<Identifier>> tags = Map.of(
                    id("test:planks"), Set.of(id("minecraft:oak_planks"), id("minecraft:birch_planks"))
            );

            JsonObject result = GooValueMerger.expandTagEntries(input, resolver(tags));

            assertEquals(100, result.getAsJsonObject("minecraft:oak_planks").get("leaf").getAsInt());
            assertEquals(100, result.getAsJsonObject("minecraft:birch_planks").get("leaf").getAsInt());
        }

        /**
         * Multiple tags expand independently.
         */
        @Test
        void multipleTagsExpanded() {
            JsonObject input = json("""
                    { "#test:planks": { "leaf": 100 }, "#test:ores": { "rock": 50 } }
                    """);
            Map<Identifier, Set<Identifier>> tags = new HashMap<>();
            tags.put(id("test:planks"), Set.of(id("minecraft:oak_planks")));
            tags.put(id("test:ores"), Set.of(id("minecraft:iron_ore")));

            JsonObject result = GooValueMerger.expandTagEntries(input, resolver(tags));

            assertEquals(100, result.getAsJsonObject("minecraft:oak_planks").get("leaf").getAsInt());
            assertEquals(50, result.getAsJsonObject("minecraft:iron_ore").get("rock").getAsInt());
        }

        /**
         * An unknown tag (empty resolver result) adds no entries and does not crash.
         */
        @Test
        void unknownTagPreservedForPseudoTagResolution() {
            JsonObject input = json("""
                    { "#test:nonexistent": { "leaf": 100 } }
                    """);

            JsonObject result = GooValueMerger.expandTagEntries(input, resolver(Map.of()));

            // Unresolved MC tags are preserved so pseudo-tag resolution can try them
            assertEquals(1, result.size());
            assertTrue(result.has("#test:nonexistent"));
        }

        /**
         * A tag value can be a string expression; expansion preserves it as-is for later evaluation.
         */
        @Test
        void tagValueCanBeExpression() {
            JsonObject input = json("""
                    { "#test:planks": "stick * 2" }
                    """);
            Map<Identifier, Set<Identifier>> tags = Map.of(
                    id("test:planks"), Set.of(id("minecraft:oak_planks"), id("minecraft:birch_planks"))
            );

            JsonObject result = GooValueMerger.expandTagEntries(input, resolver(tags));

            assertEquals("stick * 2", result.get("minecraft:oak_planks").getAsString());
            assertEquals("stick * 2", result.get("minecraft:birch_planks").getAsString());
        }

        /**
         * _constants and _groups pass through unchanged; only # keys are expanded.
         */
        @Test
        void constantsAndGroupsUntouchedByTagExpansion() {
            JsonObject input = json("""
                    {
                        "_constants": { "iron": 10 },
                        "_groups": { "ores": { "value": { "rock": 5 }, "items": ["minecraft:gold_ore"] } },
                        "#test:planks": { "leaf": 100 }
                    }
                    """);
            Map<Identifier, Set<Identifier>> tags = Map.of(
                    id("test:planks"), Set.of(id("minecraft:oak_planks"))
            );

            JsonObject result = GooValueMerger.expandTagEntries(input, resolver(tags));

            assertTrue(result.has("_constants"));
            assertTrue(result.has("_groups"));
            assertEquals(10, result.getAsJsonObject("_constants").get("iron").getAsInt());
            assertTrue(result.has("minecraft:oak_planks"));
            assertFalse(result.has("#test:planks"));
        }
    }

    // ── Restricted items ───────────────────────────────────────────────

    @Nested
    class RestrictedItems {

        private void loadJson(String json) throws IOException {
            parseBaseValuesFromStream(
                    new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
            copyBaseToEffective();
        }

        /**
         * Items listed in _restricted are flagged but still have values.
         */
        @Test
        void restrictedItemKeepsValueButIsFlagged() throws IOException {
            loadJson("""
                    {
                        "minecraft:coal_ore": { "rock": 100 },
                        "_restricted": ["minecraft:coal_ore"]
                    }
                    """);
            assertTrue(registry.table().isRestricted(id("minecraft:coal_ore")));
            assertNotNull(registry.table().lookup(id("minecraft:coal_ore")));
            assertEquals(GooFraction.of(100), registry.table().lookup(id("minecraft:coal_ore")).exact(GooTypes.ROCK));
        }

        /**
         * Group references in _restricted expand to all members.
         */
        @Test
        void restrictedGroupExpandsToMembers() throws IOException {
            loadJson("""
                    {
                        "_groups": {
                            "ores": ["minecraft:coal_ore", "minecraft:iron_ore"]
                        },
                        "#ores": { "rock": 100 },
                        "_restricted": ["#ores"]
                    }
                    """);
            assertTrue(registry.table().isRestricted(id("minecraft:coal_ore")));
            assertTrue(registry.table().isRestricted(id("minecraft:iron_ore")));
            assertNotNull(registry.table().lookup(id("minecraft:coal_ore")));
        }

        /**
         * Items not in _restricted are not flagged.
         */
        @Test
        void nonRestrictedItemIsNotFlagged() throws IOException {
            loadJson("""
                    {
                        "minecraft:coal_ore": { "rock": 100 },
                        "minecraft:stick": { "leaf": 50 },
                        "_restricted": ["minecraft:coal_ore"]
                    }
                    """);
            assertFalse(registry.table().isRestricted(id("minecraft:stick")));
        }

        /**
         * Absent _restricted section means nothing is restricted.
         */
        @Test
        void noRestrictedSectionMeansNothingRestricted() throws IOException {
            loadJson("""
                    {
                        "minecraft:coal_ore": { "rock": 100 }
                    }
                    """);
            assertFalse(registry.table().isRestricted(id("minecraft:coal_ore")));
        }
    }

    // ── Bundled yore terms ──────────────────────────────────────────────

    /**
     * The shipped base values give yore to old, hard-to-come-by things and
     * nothing mundane (decision old-ships-from-scutes-diamonds-and-scrap).
     */
    @Nested
    class BundledYoreTerms {

        private static final String BASE_VALUES = "/data/goo/goo_values/base_values.json";

        /**
         * Loads the base_values.json the mod ships.
         */
        @BeforeEach
        void loadBundledBaseValues() throws IOException {
            try (java.io.InputStream bundled = GooValueRegistryTest.class.getResourceAsStream(BASE_VALUES)) {
                assertNotNull(bundled, BASE_VALUES);
                parseBaseValuesFromStream(bundled);
            }
            copyBaseToEffective();
        }

        /**
         * Diamond, netherite scrap, turtle scute and a spawn egg each carry yore.
         */
        @Test
        void oldThingsResolveYore() {
            for (String item : List.of("minecraft:diamond", "minecraft:netherite_scrap",
                    "minecraft:turtle_scute", "minecraft:armadillo_scute", "minecraft:allay_spawn_egg")) {
                GooValue value = registry.table().lookup(id(item));
                assertNotNull(value, item);
                assertTrue(value.exact(GooTypes.YORE).isPositive(), item);
            }
        }

        /**
         * Dirt carries no yore.
         */
        @Test
        void dirtResolvesNoYore() {
            GooValue dirt = registry.table().lookup(id("minecraft:dirt"));
            assertNotNull(dirt);
            assertEquals(GooFraction.of(0), dirt.exact(GooTypes.YORE));
        }

        /**
         * The old, hard-to-come-by items whose retired type's term moved carry yore.
         */
        @Test
        void retiredTermsMoveToYore() {
            for (String item : List.of("minecraft:heart_of_the_sea", "minecraft:echo_shard", "minecraft:elytra",
                    "minecraft:dragon_egg", "minecraft:nautilus_shell")) {
                GooValue value = registry.table().lookup(id(item));
                assertNotNull(value, item);
                assertTrue(value.exact(GooTypes.YORE).isPositive(), item + " carries no yore: " + value);
            }
        }

        /**
         * Every type a shipped item resolves is a bundled type, so no item
         * resolves a retired type.
         */
        @Test
        void noItemResolvesARetiredType() {
            List<String> retired = registry.table().getEffectiveValues().entrySet().stream()
                    .filter(entry -> !GooTypes.BUNDLED.containsAll(entry.getValue().exactAll().keySet()))
                    .map(entry -> entry.getKey() + " " + entry.getValue())
                    .toList();
            assertEquals(List.of(), retired);
        }
    }

    // ── Shipped deep values ─────────────────────────────────────────────

    /**
     * The shipped base values give deepslate, its variants and ores, and the
     * sculk family a deep term (decision deep-ships-from-deepslate-and-sculk).
     */
    @Nested
    class ShippedDeepValues {

        private static final String SHIPPED_BASE_VALUES = "/data/goo/goo_values/base_values.json";

        @Test
        void deepslateAndSculkResolveDeep() throws IOException {
            try (java.io.InputStream shipped = GooValueRegistryTest.class.getResourceAsStream(SHIPPED_BASE_VALUES)) {
                assertNotNull(shipped, SHIPPED_BASE_VALUES);
                parseBaseValuesFromStream(shipped);
            }
            copyBaseToEffective();
            for (String item : List.of("deepslate", "cobbled_deepslate", "polished_deepslate", "chiseled_deepslate",
                    "deepslate_bricks", "cracked_deepslate_bricks", "deepslate_tiles", "cracked_deepslate_tiles",
                    "infested_deepslate", "deepslate_iron_ore", "deepslate_diamond_ore",
                    "sculk", "sculk_vein", "sculk_shrieker", "sculk_catalyst", "sculk_sensor")) {
                GooValue value = registry.table().lookup(id("minecraft:" + item));
                assertNotNull(value, item);
                assertTrue(value.exact(GooTypes.DEEP).isPositive(), item + " carries no deep: " + value);
            }
            GooValue ironOre = registry.table().lookup(id("minecraft:iron_ore"));
            GooValue deepslateIronOre = registry.table().lookup(id("minecraft:deepslate_iron_ore"));
            assertEquals(ironOre.exact(GooTypes.METAL), deepslateIronOre.exact(GooTypes.METAL));
            assertEquals(GooFraction.of(0), ironOre.exact(GooTypes.DEEP));
        }
    }

    // ── Shipped base values ────────────────────────────────────────────

    @Nested
    class ShippedBaseValues {

        private static final String SHIPPED_BASE_VALUES = "/data/goo/goo_values/base_values.json";
        private static final List<String> SCULK_FAMILY = List.of("minecraft:sculk", "minecraft:sculk_vein",
                "minecraft:sculk_sensor", "minecraft:sculk_shrieker", "minecraft:sculk_catalyst");

        /**
         * The shipped slime ball and gunpowder each resolve a kinetic share.
         * decision kinetic-ships-from-slime-and-gunpowder
         */
        @Test
        void slimeBallAndGunpowderResolveKinetic() throws IOException {
            try (java.io.InputStream in = GooValueRegistryTest.class.getResourceAsStream(SHIPPED_BASE_VALUES)) {
                assertNotNull(in, SHIPPED_BASE_VALUES);
                parseBaseValuesFromStream(in);
            }
            copyBaseToEffective();
            for (String item : List.of("minecraft:slime_ball", "minecraft:gunpowder")) {
                GooValue value = registry.table().lookup(id(item));
                assertNotNull(value, item);
                assertTrue(value.exact(GooTypes.KINETIC).isPositive(), item + " resolves no kinetic");
            }
        }

        /**
         * End stone and its bricks resolve astral from the base values the mod
         * ships, beside the ender end stone always carried.
         * decision astral-ships-from-endstone
         */
        @Test
        void endStoneResolvesAstral() throws IOException {
            try (java.io.InputStream shipped = GooValueRegistryTest.class.getResourceAsStream(SHIPPED_BASE_VALUES)) {
                assertNotNull(shipped, SHIPPED_BASE_VALUES + " is off the test classpath");
                parseBaseValuesFromStream(shipped);
            }
            copyBaseToEffective();
            for (String item : List.of("minecraft:end_stone", "minecraft:end_stone_bricks")) {
                GooValue value = registry.table().lookup(id(item));
                assertNotNull(value, item + " holds no value");
                assertTrue(value.exact(GooTypes.ASTRAL).isPositive(), item + " resolves no astral: " + value);
                assertTrue(value.exact(GooTypes.ENDER).isPositive(), item + " lost its ender: " + value);
            }
        }

        /**
         * The shipped base_values.json values gold, emeralds, diamonds and
         * honey in opulent.
         * decision opulent-ships-from-things-of-worth
         */
        @Test
        void goldEmeraldDiamondAndHoneyResolveOpulent() throws IOException {
            try (java.io.InputStream in = GooValueRegistryTest.class.getResourceAsStream(SHIPPED_BASE_VALUES)) {
                assertNotNull(in, SHIPPED_BASE_VALUES);
                parseBaseValuesFromStream(in);
            }
            copyBaseToEffective();
            for (String item : List.of("minecraft:gold_ingot", "minecraft:emerald", "minecraft:diamond",
                    "minecraft:honey_bottle")) {
                GooValue value = registry.table().lookup(id(item));
                assertNotNull(value, item);
                assertTrue(value.exact(GooTypes.OPULENT).isPositive(), item + " carries no opulent: " + value);
            }
        }

        /**
         * The shipped base_values.json values slime balls, frogspawn, froglights,
         * honeycomb and wet sponges in weird, and the sculk family carries none.
         * decision weird-ships-from-slime-and-sculk
         */
        @Test
        void slimeAndItsKinResolveWeird() throws IOException {
            try (java.io.InputStream in = GooValueRegistryTest.class.getResourceAsStream(SHIPPED_BASE_VALUES)) {
                assertNotNull(in, SHIPPED_BASE_VALUES);
                parseBaseValuesFromStream(in);
            }
            copyBaseToEffective();
            for (String item : List.of("minecraft:slime_ball", "minecraft:frogspawn", "minecraft:ochre_froglight",
                    "minecraft:honeycomb", "minecraft:wet_sponge")) {
                GooValue value = registry.table().lookup(id(item));
                assertNotNull(value, item);
                assertTrue(value.exact(GooTypes.WEIRD).isPositive(), item + " carries no weird: " + value);
            }
            for (String item : SCULK_FAMILY) {
                assertEquals(GooFraction.of(0), registry.table().lookup(id(item)).exact(GooTypes.WEIRD), item);
            }
        }

        /**
         * The shipped base_values.json values the sculk family, echo shards and
         * phantom membranes in xeno.
         * decision xeno-ships-as-a-type-with-no-source-yet
         */
        @Test
        void sculkEchoAndPhantomResolveXeno() throws IOException {
            try (java.io.InputStream in = GooValueRegistryTest.class.getResourceAsStream(SHIPPED_BASE_VALUES)) {
                assertNotNull(in, SHIPPED_BASE_VALUES);
                parseBaseValuesFromStream(in);
            }
            copyBaseToEffective();
            List<String> items = new java.util.ArrayList<>(SCULK_FAMILY);
            items.addAll(List.of("minecraft:echo_shard", "minecraft:phantom_membrane"));
            for (String item : items) {
                GooValue value = registry.table().lookup(id(item));
                assertNotNull(value, item);
                assertTrue(value.exact(GooTypes.XENO).isPositive(), item + " carries no xeno: " + value);
            }
        }

        /**
         * The shipped base_values.json values chorus fruit, chorus plants and
         * shulker shells in void.
         * decision void-ships-from-astral-and-deep
         */
        @Test
        void chorusAndShulkerResolveVoid() throws IOException {
            try (java.io.InputStream in = GooValueRegistryTest.class.getResourceAsStream(SHIPPED_BASE_VALUES)) {
                assertNotNull(in, SHIPPED_BASE_VALUES);
                parseBaseValuesFromStream(in);
            }
            copyBaseToEffective();
            for (String item : List.of("minecraft:chorus_fruit", "minecraft:chorus_plant", "minecraft:shulker_shell")) {
                GooValue value = registry.table().lookup(id(item));
                assertNotNull(value, item);
                assertTrue(value.exact(GooTypes.VOID).isPositive(), item + " carries no void: " + value);
            }
        }
    }

    // ── Shipped values ─────────────────────────────────────────────────

    @Nested
    class ShippedValues {

        private static final String SHIPPED_BASE_VALUES = "/data/goo/goo_values/base_values.json";

        /**
         * No shipped item value resolves to quantum, after the post-conversions
         * run; recipes only sum their inputs, so no derived item carries it either.
         * decision quantum-ships-from-goo-ratios
         */
        @Test
        void noShippedItemResolvesQuantum() throws IOException {
            try (var stream = GooValueRegistryTest.class.getResourceAsStream(SHIPPED_BASE_VALUES)) {
                assertNotNull(stream, SHIPPED_BASE_VALUES);
                parseBaseValuesFromStream(stream);
            }
            copyBaseToEffective();

            Map<Identifier, GooValue> effective = registry.table().getEffectiveValues();
            assertTrue(effective.values().stream().anyMatch(value -> value.exact(GooTypes.ENDER).isPositive()),
                    "shipped values carry ender, so the scan reads real data");
            List<Identifier> quantumItems = effective.entrySet().stream()
                    .filter(entry -> entry.getValue().exact(GooTypes.QUANTUM).isPositive())
                    .map(Map.Entry::getKey)
                    .toList();
            assertEquals(List.of(), quantumItems);
        }

        /**
         * No shipped item resolves to infinity, so no item can be broken down into it.
         * decision infinity-sources-whatever-shares-its-vessel
         */
        @Test
        void noShippedItemResolvesInfinity() throws IOException {
            try (java.io.InputStream in = GooValueRegistryTest.class.getResourceAsStream(SHIPPED_BASE_VALUES)) {
                assertNotNull(in, SHIPPED_BASE_VALUES);
                parseBaseValuesFromStream(in);
            }
            copyBaseToEffective();
            Map<Identifier, GooValue> effective = registry.table().getEffectiveValues();

            assertFalse(effective.isEmpty(), SHIPPED_BASE_VALUES);
            List<Identifier> yieldingInfinity = effective.entrySet().stream()
                    .filter(entry -> entry.getValue().exact(GooTypes.INFINITY).isPositive())
                    .map(Map.Entry::getKey)
                    .toList();
            assertEquals(List.of(), yieldingInfinity);
        }
    }

    /**
     * The shipped base values give Zoo the animal items, Jelly the food and
     * Yore the eggs, and no item resolves a type outside the bundled set.
     * decision zoo-ships-from-animal-items
     */
    @Nested
    class ShippedAnimalValues {

        private static final String SHIPPED_BASE_VALUES = "/data/goo/goo_values/base_values.json";

        private Map<Identifier, GooValue> loadShipped() throws IOException {
            try (java.io.InputStream shipped = GooValueRegistryTest.class.getResourceAsStream(SHIPPED_BASE_VALUES)) {
                assertNotNull(shipped, SHIPPED_BASE_VALUES);
                parseBaseValuesFromStream(shipped);
            }
            copyBaseToEffective();
            return registry.table().getEffectiveValues();
        }

        /**
         * A spawn egg, leather, bone and string each resolve zoo; ink and slime,
         * which an animal produces rather than is made of, carry none.
         */
        @Test
        void animalItemsResolveZoo() throws IOException {
            loadShipped();
            for (String item : List.of("minecraft:ink_sac", "minecraft:slime_ball", "minecraft:honeycomb")) {
                assertEquals(GooFraction.of(0), registry.table().lookup(id(item)).exact(GooTypes.ZOO), item);
            }
            for (String item : List.of("minecraft:cow_spawn_egg", "minecraft:leather", "minecraft:bone",
                    "minecraft:string")) {
                GooValue value = registry.table().lookup(id(item));
                assertNotNull(value, item);
                assertTrue(value.exact(GooTypes.ZOO).isPositive(), item + " resolves no zoo");
            }
        }

        /**
         * Turtle eggs, sniffer eggs and frogspawn each carry an eighth of base in yore, as the scutes do.
         */
        @Test
        void eggsResolveYoreLikeScutes() throws IOException {
            loadShipped();
            GooFraction scuteYore = registry.table().lookup(id("minecraft:turtle_scute")).exact(GooTypes.YORE);
            assertTrue(scuteYore.isPositive(), "turtle_scute resolves no yore");
            for (String egg : List.of("minecraft:turtle_egg", "minecraft:sniffer_egg", "minecraft:frogspawn")) {
                GooValue value = registry.table().lookup(id(egg));
                assertNotNull(value, egg);
                assertEquals(scuteYore, value.exact(GooTypes.YORE), egg);
            }
        }

        /**
         * Every type a shipped item resolves is a bundled type, so the
         * deleted type, which GooTypes.BUNDLED no longer holds, values nothing.
         */
        @Test
        void noShippedItemResolvesAnUnbundledType() throws IOException {
            Map<Identifier, GooValue> effective = loadShipped();
            assertTrue(effective.values().stream().anyMatch(value -> value.exact(GooTypes.JELLY).isPositive()),
                    "the shipped load valued no item in jelly");
            List<String> unbundled = effective.entrySet().stream()
                    .flatMap(entry -> entry.getValue().exactAll().entrySet().stream()
                            .filter(term -> !term.getValue().isZero() && !GooTypes.BUNDLED.contains(term.getKey()))
                            .map(term -> entry.getKey() + " " + term.getKey().identifier()))
                    .toList();
            assertEquals(List.of(), unbundled);
        }
    }

    /**
     * Ties on total goo: the cheapest wins, and the simplest, the value holding fewer goo types,
     * breaks a tie.
     */
    @Nested
    class SimplestBreaksATie {

        @Test
        void slotTakesTheSimplerOfTwoEqualAlternatives() {
            setBaseValues(Map.of(id("minecraft:oak_log"), goo(GooTypes.LEAF, 4),
                    id("minecraft:stripped_oak_log"), goo(GooTypes.LEAF, 3, GooTypes.NETHER, 1)));
            registry.deriveFromRecipeInputs(List.of(recipe("minecraft:oak_planks", 4,
                    slot("minecraft:stripped_oak_log", "minecraft:oak_log"))), false);

            assertEquals(Map.of(GooTypes.LEAF, 1), getDerivedValues().get(id("minecraft:oak_planks")).meltFloorAll());
        }

        @Test
        void outputTakesTheSimplerOfTwoEqualRecipes() {
            setBaseValues(Map.of(id("minecraft:oak_log"), goo(GooTypes.LEAF, 16),
                    id("minecraft:stripped_oak_log"), goo(GooTypes.LEAF, 12, GooTypes.NETHER, 4)));
            registry.deriveFromRecipeInputs(List.of(
                    recipe("minecraft:oak_planks", 4, slot("minecraft:stripped_oak_log")),
                    recipe("minecraft:oak_planks", 4, slot("minecraft:oak_log"))), false);

            assertEquals(Map.of(GooTypes.LEAF, 4), getDerivedValues().get(id("minecraft:oak_planks")).meltFloorAll());
        }
    }

    /**
     * The recipes that set a value: where two paths exist the smaller value wins, so only the
     * winning recipe and its cheapest alternatives per slot carry a bound.
     */
    @Nested
    class ValueSettingRecipes {

        @Test
        void costlierRecipeForTheSameOutputSetsNothing() {
            setBaseValues(Map.of(id("minecraft:dandelion"), goo(GooTypes.LEAF, 2),
                    id("minecraft:golden_dandelion"), goo(GooTypes.METAL, 100)));
            registry.deriveFromRecipeInputs(List.of(
                    recipe("minecraft:yellow_dye", 1, slot("minecraft:dandelion")),
                    recipe("minecraft:yellow_dye", 1, slot("minecraft:golden_dandelion"))), false);

            List<RecipeInput> setting = registry.valueSettingRecipes();

            assertEquals(1, setting.size());
            assertEquals(List.of(Set.of(id("minecraft:dandelion"))), setting.getFirst().ingredientAlternatives());
        }

        @Test
        void fewerTypedOfTwoEqualRecipesSetsTheValue() {
            setBaseValues(Map.of(id("minecraft:oak_log"), goo(GooTypes.LEAF, 16),
                    id("minecraft:stripped_oak_log"), goo(GooTypes.LEAF, 12, GooTypes.NETHER, 4)));
            registry.deriveFromRecipeInputs(List.of(
                    recipe("minecraft:oak_planks", 4, slot("minecraft:stripped_oak_log")),
                    recipe("minecraft:oak_planks", 4, slot("minecraft:oak_log"))), false);

            assertEquals(List.of(List.of(Set.of(id("minecraft:oak_log")))),
                    registry.valueSettingRecipes().stream().map(RecipeInput::ingredientAlternatives).toList());
        }

        @Test
        void handKeyedOutputStillCarriesItsWinningRecipe() {
            setBaseValues(Map.of(id("minecraft:gold_ingot"), goo(GooTypes.METAL, 9),
                    id("minecraft:gold_block"), goo(GooTypes.METAL, 81)));
            registry.deriveFromRecipeInputs(List.of(
                    recipe("minecraft:gold_block", 1, slot("minecraft:gold_ingot"), slot("minecraft:gold_ingot"),
                            slot("minecraft:gold_ingot"), slot("minecraft:gold_ingot"), slot("minecraft:gold_ingot"),
                            slot("minecraft:gold_ingot"), slot("minecraft:gold_ingot"), slot("minecraft:gold_ingot"),
                            slot("minecraft:gold_ingot"))), false);

            assertEquals(List.of(id("minecraft:gold_block")),
                    registry.valueSettingRecipes().stream().map(RecipeInput::output).toList());
        }

        @Test
        void slotKeepsEveryAlternativeTiedForCheapestAndSimplest() {
            setBaseValues(Map.of(id("minecraft:oak_planks"), goo(GooTypes.LEAF, 2),
                    id("minecraft:acacia_planks"), goo(GooTypes.LEAF, 2),
                    id("minecraft:crimson_planks"), goo(GooTypes.LEAF, 1, GooTypes.NETHER, 1)));
            registry.deriveFromRecipeInputs(List.of(recipe("minecraft:stick", 4,
                    slot("minecraft:oak_planks", "minecraft:acacia_planks", "minecraft:crimson_planks"),
                    slot("minecraft:oak_planks", "minecraft:acacia_planks", "minecraft:crimson_planks"))), false);

            assertEquals(Set.of(id("minecraft:oak_planks"), id("minecraft:acacia_planks")),
                    registry.valueSettingRecipes().getFirst().ingredientAlternatives().getFirst());
        }

        @Test
        void slotKeepsOnlyItsCheapestAlternative() {
            setBaseValues(Map.of(id("minecraft:stone"), goo(GooTypes.ROCK, 2),
                    id("minecraft:granite"), goo(GooTypes.ROCK, 6)));
            registry.deriveFromRecipeInputs(List.of(
                    recipe("minecraft:gravel", 2, slot("minecraft:stone", "minecraft:granite"))), false);

            assertEquals(List.of(Set.of(id("minecraft:stone"))),
                    registry.valueSettingRecipes().getFirst().ingredientAlternatives());
        }
    }

    /**
     * A recipe whose output holds a value and whose slots are all valued but one solves that slot's
     * ingredient (decision derivation-solves-whichever-side-of-a-recipe-is-unknown).
     */
    @Nested
    class SolvingInputs {

        @Test
        void singleInputRecipeSolvesItsInputFromTheOutput() {
            setBaseValues(Map.of(id("minecraft:stick"), goo(GooTypes.LEAF, 4)));
            RecipeInput sticks = recipe("minecraft:stick", 4, slot("minecraft:oak_planks"), slot("minecraft:oak_planks"));
            registry.deriveFromRecipeInputs(List.of(sticks), false);

            assertEquals(goo(GooTypes.LEAF, 8), registry.table().lookup(id("minecraft:oak_planks")));
            assertEquals(sticks, registry.diagnostics().derivationSources().get(id("minecraft:oak_planks")));
        }

        @Test
        void mixedRecipeSolvesItsOneUnknownSlot() {
            setBaseValues(Map.of(id("minecraft:torch"), goo(GooTypes.LEAF, 2, GooTypes.ROCK, 3),
                    id("minecraft:stick"), goo(GooTypes.LEAF, 8)));
            registry.deriveFromRecipeInputs(List.of(recipe("minecraft:torch", 4,
                    slot("minecraft:coal"), slot("minecraft:coal"), slot("minecraft:stick"))), false);

            assertEquals(goo(GooTypes.ROCK, 6), registry.table().lookup(id("minecraft:coal")));
        }

        @Test
        void twoUnknownSlotsSolveNothing() {
            setBaseValues(Map.of(id("minecraft:torch"), goo(GooTypes.LEAF, 2, GooTypes.ROCK, 3)));
            registry.deriveFromRecipeInputs(List.of(recipe("minecraft:torch", 4,
                    slot("minecraft:coal"), slot("minecraft:stick"))), false);

            assertNull(registry.table().lookup(id("minecraft:coal")));
            assertNull(registry.table().lookup(id("minecraft:stick")));
        }

        @Test
        void bambooSolvesSticksPlanksAndLogs() {
            setBaseValues(Map.of(id("minecraft:bamboo"), goo(GooTypes.LEAF, 1)));
            RecipeInput sticksFromBamboo = recipe("minecraft:stick", 1, slot("minecraft:bamboo"), slot("minecraft:bamboo"));
            RecipeInput sticksFromPlanks = recipe("minecraft:stick", 4,
                    slot("minecraft:oak_planks"), slot("minecraft:oak_planks"));
            RecipeInput planksFromLog = recipe("minecraft:oak_planks", 4, slot("minecraft:oak_log"));
            registry.deriveFromRecipeInputs(List.of(sticksFromBamboo, sticksFromPlanks, planksFromLog), false);

            assertEquals(goo(GooTypes.LEAF, 2), registry.table().lookup(id("minecraft:stick")));
            assertEquals(goo(GooTypes.LEAF, 4), registry.table().lookup(id("minecraft:oak_planks")));
            assertEquals(goo(GooTypes.LEAF, 16), registry.table().lookup(id("minecraft:oak_log")));
            Map<Identifier, RecipeInput> sources = registry.diagnostics().derivationSources();
            assertEquals(sticksFromBamboo, sources.get(id("minecraft:stick")));
            assertEquals(sticksFromPlanks, sources.get(id("minecraft:oak_planks")));
            assertEquals(planksFromLog, sources.get(id("minecraft:oak_log")));
            assertEquals(Set.of(id("minecraft:stick"), id("minecraft:oak_planks")),
                    registry.valueSettingRecipes().stream().map(RecipeInput::output).collect(java.util.stream.Collectors.toSet()));
            assertEquals(3, registry.valueSettingRecipes().size());
        }

        @Test
        void solvedInputNeverOverwritesAHandKeyedValue() {
            setBaseValues(Map.of(id("minecraft:stick"), goo(GooTypes.LEAF, 4),
                    id("minecraft:oak_planks"), goo(GooTypes.LEAF, 20)));
            registry.deriveFromRecipeInputs(List.of(recipe("minecraft:stick", 4,
                    slot("minecraft:oak_planks"), slot("minecraft:oak_planks"))), false);

            assertFalse(getDerivedValues().containsKey(id("minecraft:oak_planks")));
            assertEquals(goo(GooTypes.LEAF, 20), registry.table().lookup(id("minecraft:oak_planks")));
        }

        @Test
        void tagSlotSolvesEachUnvaluedAlternative() {
            setBaseValues(Map.of(id("minecraft:stick"), goo(GooTypes.LEAF, 4),
                    id("minecraft:oak_planks"), goo(GooTypes.LEAF, 8)));
            String[] planks = slot("minecraft:oak_planks", "minecraft:birch_planks", "minecraft:spruce_planks");
            registry.deriveFromRecipeInputs(List.of(tagRecipe("minecraft:stick", 4,
                    Arrays.asList("minecraft:planks", "minecraft:planks"), planks, planks)), false);

            assertEquals(goo(GooTypes.LEAF, 8), registry.table().lookup(id("minecraft:birch_planks")));
            assertEquals(goo(GooTypes.LEAF, 8), registry.table().lookup(id("minecraft:spruce_planks")));
            assertFalse(getDerivedValues().containsKey(id("minecraft:oak_planks")));
        }
    }
}
