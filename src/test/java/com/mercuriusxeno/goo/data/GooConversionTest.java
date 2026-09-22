package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.goo;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.id;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the GooConversion system: formula parsing, stacking, and application.
 */
class GooConversionTest {

    // ── Formula parsing ──────────────────────────────────────────────────

    @Nested
    class FormulaParsing {

        /**
         * Basic formula: "metal / 4 -> aeon / 2".
         */
        @Test
        void parseBasicFormula() {
            GooConversion.Formula f = GooConversion.parseFormula("metal / 4 -> aeon / 2");
            assertEquals(GooTypes.METAL, f.sourceType());
            assertEquals(4, f.sourceDivisor());
            assertEquals(GooTypes.AEON, f.targetType());
            assertEquals(2, f.targetDivisor());
        }

        /**
         * Formula with divisor of 1: "vital / 1 -> nether / 2".
         */
        @Test
        void parseDivisorOfOne() {
            GooConversion.Formula f = GooConversion.parseFormula("vital / 1 -> nether / 2");
            assertEquals(GooTypes.VITAL, f.sourceType());
            assertEquals(1, f.sourceDivisor());
            assertEquals(GooTypes.NETHER, f.targetType());
            assertEquals(2, f.targetDivisor());
        }

        /**
         * Invalid formula throws.
         */
        @Test
        void invalidFormulaThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> GooConversion.parseFormula("not a formula"));
        }
    }

    // ── Additive parsing ────────────────────────────────────────────────

    @Nested
    class AdditiveParsing {

        /**
         * Additive modifier: "+$waxed" adds a flat GooValue.
         */
        @Test
        void parseAdditiveFromTreeConstant() {
            Map<String, GooValue> treeConstants = Map.of(
                    "waxed", goo(GooTypes.VITAL, 48));
            Map<String, String> entries = new LinkedHashMap<>();
            entries.put("waxed", "+$waxed");
            entries.put("#waxed_copper", "#copper @waxed");

            GooConversion.ParsedConversions parsed = GooConversion.parseBlock(entries, Map.of(), treeConstants);
            assertNotNull(parsed.additives().get("waxed"));
            assertEquals(48, parsed.additives().get("waxed").get(GooTypes.VITAL));
        }

        /**
         * Additive applied via @ref adds the value to the item.
         */
        @Test
        void additiveAppliedToItem() {
            GooValue copper = goo(GooTypes.METAL, 160);
            GooValue waxBonus = goo(GooTypes.VITAL, 48);

            GooValue result = copper.add(waxBonus, 1);
            assertEquals(160, result.get(GooTypes.METAL));
            assertEquals(48, result.get(GooTypes.VITAL));
        }

        /**
         * Additive with multiplier: "2 @waxed" adds 2x the value.
         */
        @Test
        void additiveWithMultiplier() {
            GooValue copper = goo(GooTypes.METAL, 160);
            GooValue waxBonus = goo(GooTypes.VITAL, 48);

            GooValue result = copper.add(waxBonus, 2);
            assertEquals(160, result.get(GooTypes.METAL));
            assertEquals(96, result.get(GooTypes.VITAL));
        }
    }

    // ── Application math ─────────────────────────────────────────────────

    @Nested
    class ApplicationMath {

        /**
         * 1x oxidation on metal=160: removes 40, adds 20 aeon.
         */
        @Test
        void singleApplication() {
            GooConversion.Formula oxidation = GooConversion.parseFormula("metal / 4 -> aeon / 2");
            GooValue copper = goo(GooTypes.METAL, 160);

            GooValue result = GooConversion.apply(copper, oxidation, 1);
            assertEquals(120, result.get(GooTypes.METAL)); // 160 - 40
            assertEquals(20, result.get(GooTypes.AEON));    // 40 / 2
        }

        /**
         * 2x oxidation on metal=160: removes 80, adds 40 aeon (simultaneous).
         */
        @Test
        void doubleApplicationSimultaneous() {
            GooConversion.Formula oxidation = GooConversion.parseFormula("metal / 4 -> aeon / 2");
            GooValue copper = goo(GooTypes.METAL, 160);

            GooValue result = GooConversion.apply(copper, oxidation, 2);
            assertEquals(80, result.get(GooTypes.METAL));  // 160 - 2*40
            assertEquals(40, result.get(GooTypes.AEON));    // 2 * (40/2)
        }

        /**
         * 3x oxidation on metal=160: removes 120, adds 60 aeon.
         */
        @Test
        void tripleApplicationSimultaneous() {
            GooConversion.Formula oxidation = GooConversion.parseFormula("metal / 4 -> aeon / 2");
            GooValue copper = goo(GooTypes.METAL, 160);

            GooValue result = GooConversion.apply(copper, oxidation, 3);
            assertEquals(40, result.get(GooTypes.METAL));  // 160 - 3*40
            assertEquals(60, result.get(GooTypes.AEON));    // 3 * (40/2)
        }

        /**
         * Conversion preserves other goo types untouched.
         */
        @Test
        void otherTypesPreserved() {
            GooConversion.Formula oxidation = GooConversion.parseFormula("metal / 4 -> aeon / 2");
            GooValue copper = goo(GooTypes.METAL, 160, GooTypes.ROCK, 50);

            GooValue result = GooConversion.apply(copper, oxidation, 1);
            assertEquals(120, result.get(GooTypes.METAL));
            assertEquals(20, result.get(GooTypes.AEON));
            assertEquals(50, result.get(GooTypes.ROCK)); // untouched
        }

        /**
         * Conversion on an item missing the source type is a no-op.
         */
        @Test
        void missingSourceTypeNoOp() {
            GooConversion.Formula oxidation = GooConversion.parseFormula("metal / 4 -> aeon / 2");
            GooValue noMetal = goo(GooTypes.ROCK, 100);

            GooValue result = GooConversion.apply(noMetal, oxidation, 1);
            assertEquals(100, result.get(GooTypes.ROCK));
            assertEquals(0, result.get(GooTypes.METAL));
            assertEquals(0, result.get(GooTypes.AEON));
        }

        /**
         * Lossy source division throws.
         */
        @Test
        void lossySourceDivisionThrows() {
            GooConversion.Formula f = GooConversion.parseFormula("metal / 3 -> aeon / 1");
            GooValue copper = goo(GooTypes.METAL, 100); // 100/3 = 33.3

            assertThrows(ArithmeticException.class,
                    () -> GooConversion.apply(copper, f, 1));
        }

        /**
         * Lossy target division throws.
         */
        @Test
        void lossyTargetDivisionThrows() {
            GooConversion.Formula f = GooConversion.parseFormula("metal / 4 -> aeon / 3");
            GooValue copper = goo(GooTypes.METAL, 160); // 40/3 = 13.3

            assertThrows(ArithmeticException.class,
                    () -> GooConversion.apply(copper, f, 1));
        }
    }

    // ── Block parsing ────────────────────────────────────────────────────

    @Nested
    class BlockParsing {

        /**
         * Full _conversions block: formulas, stacks, and assignments.
         */
        @Test
        void parseFullBlock() {
            Map<String, String> entries = new LinkedHashMap<>();
            entries.put("oxidation", "metal / 4 -> aeon / 2");
            entries.put("exposed", "@oxidation");
            entries.put("weathered", "2 @oxidation");
            entries.put("oxidized", "3 @oxidation");

            GooConversion.ParsedConversions parsed = GooConversion.parseBlock(entries);
            assertEquals(1, parsed.formulas().size());
            assertEquals(3, parsed.stacks().size());
            assertEquals(2, parsed.stacks().get("weathered").multiplier());
            assertEquals("oxidation", parsed.stacks().get("weathered").formulaName());
        }

        /**
         * "denied" on an assignment skips it.
         */
        @Test
        void deniedAssignmentSkipped() {
            Map<String, String> entries = new LinkedHashMap<>();
            entries.put("oxidation", "metal / 4 -> aeon / 2");
            entries.put("exposed", "@oxidation");
            entries.put("#copper_stuff", "@exposed");
            entries.put("#iron_stuff", "denied");

            GooConversion.ParsedConversions parsed = GooConversion.parseBlock(entries);
            assertEquals(1, parsed.assignments().size());
            assertEquals("#copper_stuff", parsed.assignments().get(0).target());
        }

        /**
         * Assignment with * N / M scalar is parsed correctly.
         */
        @Test
        void parseScaleAssignment() {
            Map<String, String> entries = new LinkedHashMap<>();
            entries.put("#wood", "#logs * 3 / 4");

            GooConversion.ParsedConversions parsed = GooConversion.parseBlock(entries);
            assertEquals(1, parsed.assignments().size());
            var assignment = parsed.assignments().get(0);
            assertEquals("#wood", assignment.target());
            assertEquals("#logs", assignment.parallelSource());
            assertEquals(3, assignment.scaleMultiplier());
            assertEquals(4, assignment.scaleDivisor());
            assertTrue(assignment.chain().isEmpty());
        }

        /**
         * Scale + chain: "#wood": "#logs * 3 / 4 @decay".
         */
        @Test
        void parseScaleWithChain() {
            Map<String, String> entries = new LinkedHashMap<>();
            entries.put("decay", "leaf / 2 -> aeon / 4");
            entries.put("#wood", "#logs * 3 / 4 @decay");

            GooConversion.ParsedConversions parsed = GooConversion.parseBlock(entries);
            var assignment = parsed.assignments().get(0);
            assertEquals(3, assignment.scaleMultiplier());
            assertEquals(4, assignment.scaleDivisor());
            assertEquals(1, assignment.chain().size());
            assertEquals("decay", assignment.chain().get(0).formulaName());
        }

        /**
         * "denied" on a formula removes it.
         */
        @Test
        void deniedFormulaRemoved() {
            Map<String, String> entries = new LinkedHashMap<>();
            entries.put("oxidation", "metal / 4 -> aeon / 2");
            entries.put("oxidation", "denied"); // override removes it

            GooConversion.ParsedConversions parsed = GooConversion.parseBlock(entries);
            assertTrue(parsed.formulas().isEmpty());
        }
    }

    // ── End-to-end with effective values ──────────────────────────────────

    @Nested
    class EndToEnd {

        /**
         * Apply conversion chain to effective values.
         */
        @Test
        void chainApplies() {
            Map<Identifier, GooValue> effective = new HashMap<>();
            effective.put(id("minecraft:weathered_copper"), goo(GooTypes.METAL, 160, GooTypes.ROCK, 50));
            effective.put(id("minecraft:weathered_cut_copper"), goo(GooTypes.METAL, 160));

            GooConversion.Formula oxidation = GooConversion.parseFormula("metal / 4 -> aeon / 2");
            GooConversion.Stack weatheredStack = new GooConversion.Stack("oxidation", 2);
            Map<String, GooConversion.Formula> formulas = Map.of("oxidation", oxidation);

            java.util.List<Identifier> targets = java.util.List.of(
                    id("minecraft:weathered_copper"),
                    id("minecraft:weathered_cut_copper"));

            GooConversion.Assignment assignment = new GooConversion.Assignment(
                    "#weathered", null, 1, 1, java.util.List.of(weatheredStack));
            GooConversion.applyAssignment(effective, targets, null, assignment, formulas, Map.of());

            GooValue copper = effective.get(id("minecraft:weathered_copper"));
            assertEquals(80, copper.get(GooTypes.METAL));
            assertEquals(40, copper.get(GooTypes.AEON));
            assertEquals(50, copper.get(GooTypes.ROCK));
        }

        /**
         * Parallel copy + conversion chain.
         */
        @Test
        void parallelCopyThenConvert() {
            Map<Identifier, GooValue> effective = new HashMap<>();
            effective.put(id("minecraft:copper_block"), goo(GooTypes.METAL, 160));
            effective.put(id("minecraft:cut_copper"), goo(GooTypes.METAL, 80));
            // Targets start empty
            effective.put(id("minecraft:exposed_copper"), GooValue.EMPTY);
            effective.put(id("minecraft:exposed_cut_copper"), GooValue.EMPTY);

            GooConversion.Formula oxidation = GooConversion.parseFormula("metal / 4 -> aeon / 2");
            Map<String, GooConversion.Formula> formulas = Map.of("oxidation", oxidation);

            java.util.List<Identifier> sources = java.util.List.of(
                    id("minecraft:copper_block"), id("minecraft:cut_copper"));
            java.util.List<Identifier> targets = java.util.List.of(
                    id("minecraft:exposed_copper"), id("minecraft:exposed_cut_copper"));

            GooConversion.Assignment assignment = new GooConversion.Assignment(
                    "#exposed", "#originals", 1, 1,
                    java.util.List.of(new GooConversion.Stack("oxidation", 1)));
            GooConversion.applyAssignment(effective, targets, sources, assignment, formulas, Map.of());

            // copper_block (160 metal) copied to exposed_copper, then 1x oxidation
            GooValue exposed = effective.get(id("minecraft:exposed_copper"));
            assertEquals(120, exposed.get(GooTypes.METAL)); // 160 - 40
            assertEquals(20, exposed.get(GooTypes.AEON));    // 40 / 2

            // cut_copper (80 metal) copied to exposed_cut_copper, then 1x oxidation
            GooValue exposedCut = effective.get(id("minecraft:exposed_cut_copper"));
            assertEquals(60, exposedCut.get(GooTypes.METAL)); // 80 - 20
            assertEquals(10, exposedCut.get(GooTypes.AEON));   // 20 / 2
        }

        /**
         * Parallel copy with scale: #wood = #logs * 3 / 4 (4 logs -> 3 wood).
         */
        @Test
        void parallelCopyWithScale() {
            Map<Identifier, GooValue> effective = new HashMap<>();
            effective.put(id("oak_log"), goo(GooTypes.LEAF, 120));
            effective.put(id("birch_log"), goo(GooTypes.LEAF, 80));

            java.util.List<Identifier> sources = java.util.List.of(
                    id("oak_log"), id("birch_log"));
            java.util.List<Identifier> targets = java.util.List.of(
                    id("oak_wood"), id("birch_wood"));

            GooConversion.Assignment assignment = new GooConversion.Assignment(
                    "#wood", "#logs", 3, 4, java.util.List.of());
            GooConversion.applyAssignment(effective, targets, sources, assignment, Map.of(), Map.of());

            assertEquals(90, effective.get(id("oak_wood")).get(GooTypes.LEAF));   // 120 * 3 / 4
            assertEquals(60, effective.get(id("birch_wood")).get(GooTypes.LEAF)); // 80 * 3 / 4
        }

        /**
         * Scale with lossy division throws.
         */
        @Test
        void parallelCopyScaleLossyThrows() {
            Map<Identifier, GooValue> effective = new HashMap<>();
            effective.put(id("a"), goo(GooTypes.METAL, 10));

            java.util.List<Identifier> sources = java.util.List.of(id("a"));
            java.util.List<Identifier> targets = java.util.List.of(id("b"));

            GooConversion.Assignment assignment = new GooConversion.Assignment(
                    "b", "#src", 3, 4, java.util.List.of());
            GooConversion.applyAssignment(effective, targets, sources, assignment, Map.of(), Map.of());

            // 10 * 3 = 30, 30 / 4 has remainder -- logged as error, value unchanged
            assertTrue(effective.get(id("b")).isEmpty() || effective.get(id("b")).get(GooTypes.METAL) == 10);
        }

        /**
         * Size mismatch between source and target logs error, skips assignment.
         */
        @Test
        void parallelSizeMismatchSkips() {
            Map<Identifier, GooValue> effective = new HashMap<>();
            effective.put(id("a"), goo(GooTypes.METAL, 100));
            effective.put(id("b"), GooValue.EMPTY);
            effective.put(id("c"), GooValue.EMPTY);

            GooConversion.Assignment assignment = new GooConversion.Assignment(
                    "#target", "#source", 1, 1, java.util.List.of());
            GooConversion.applyAssignment(effective,
                    java.util.List.of(id("b"), id("c")),
                    java.util.List.of(id("a")),
                    assignment, Map.of(), Map.of());

            // Mismatch: 1 source, 2 targets. No copy happens.
            assertTrue(effective.get(id("b")).isEmpty());
            assertTrue(effective.get(id("c")).isEmpty());
        }
    }
}
