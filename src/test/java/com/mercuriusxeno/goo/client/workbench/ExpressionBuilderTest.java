package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests each expression builder control's edit and the string it writes, with no Screen
 * (decision expression-builder-composes-every-form-without-typing).
 */
class ExpressionBuilderTest {
    private static final SearchDropdown.Choice ANVIL =
            new SearchDropdown.Choice(SearchDropdown.Kind.ITEM, "minecraft:anvil", "Anvil");
    private static final SearchDropdown.Choice BUCKET =
            new SearchDropdown.Choice(SearchDropdown.Kind.ITEM, "minecraft:bucket", "Bucket");
    private static final SearchDropdown.Choice IRON_INGOT =
            new SearchDropdown.Choice(SearchDropdown.Kind.PREFAB, "iron_ingot", "iron_ingot");
    private static final SearchDropdown.Choice IRON_BLOCK =
            new SearchDropdown.Choice(SearchDropdown.Kind.PREFAB, "iron_block", "iron_block");
    private static final SearchDropdown.Choice STICK =
            new SearchDropdown.Choice(SearchDropdown.Kind.PREFAB, "stick", "stick");
    private static final SearchDropdown.Choice FUEL =
            new SearchDropdown.Choice(SearchDropdown.Kind.PREFAB, "fuel", "fuel");

    @Nested
    class AddType {
        @Test
        void numberAmountWritesItsRow() {
            ExpressionBuilder builder = ExpressionBuilder.of("");
            assertTrue(builder.addType("metal"));
            builder.setRowNumber(0, "5");
            assertEquals("{\"metal\": \"5\"}", builder.text());
        }

        @Test
        void scalarPrefabAmountMultipliesByItsFactor() {
            ExpressionBuilder builder = ExpressionBuilder.of("");
            builder.addType("metal");
            builder.setRowVar(0, "base");
            builder.setRowScale(0, ExpressionBuilder.Op.MULTIPLY, "3");
            assertEquals("{\"metal\": \"3 $base\"}", builder.text());
        }

        @Test
        void relativePrefabMemberMultipliesByItsFactor() {
            ExpressionBuilder builder = ExpressionBuilder.of("");
            builder.addType("blaze");
            builder.setRowRelative(0, FUEL, "blaze");
            builder.setRowScale(0, ExpressionBuilder.Op.MULTIPLY, "20");
            assertEquals("{\"blaze\": \"20 $fuel.blaze\"}", builder.text());
        }

        @Test
        void relativeItemMemberDividesByItsFactor() {
            ExpressionBuilder builder = ExpressionBuilder.of("");
            builder.addType("metal");
            builder.setRowRelative(0, BUCKET, "metal");
            builder.setRowScale(0, ExpressionBuilder.Op.DIVIDE, "2");
            assertEquals("{\"metal\": \"bucket.metal / 2\"}", builder.text());
        }

        @Test
        void secondTypeAddsASecondRow() {
            ExpressionBuilder builder = ExpressionBuilder.of("{\"metal\": \"5\"}");
            builder.addType("pulse");
            builder.setRowNumber(1, "7");
            assertEquals("{\"metal\": \"5\", \"pulse\": \"7\"}", builder.text());
        }
    }

    @Nested
    class RelateToItem {
        @Test
        void countMultipliesTheRelatedPrefab() {
            ExpressionBuilder builder = ExpressionBuilder.of("");
            assertTrue(builder.relate(IRON_INGOT));
            builder.setCount(0, "3");
            assertEquals("3 $iron_ingot", builder.text());
        }

        @Test
        void minusJoinsTheSecondTerm() {
            ExpressionBuilder builder = ExpressionBuilder.of("");
            builder.relate(ANVIL);
            builder.relate(IRON_BLOCK);
            builder.setJoin(1, ExpressionTerms.Join.MINUS);
            assertEquals("anvil - $iron_block", builder.text());
        }

        @Test
        void parenthesesWrapTermsTheCountMultiplies() {
            ExpressionBuilder builder = ExpressionBuilder.of("");
            builder.relate(BUCKET);
            builder.relate(STICK);
            builder.wrap(0, 1);
            builder.setCount(0, "2");
            assertEquals("2 (bucket + $stick)", builder.text());
        }

        @Test
        void memberAndFactorDivideTheRelatedItem() {
            ExpressionBuilder builder = ExpressionBuilder.of("");
            builder.relate(BUCKET);
            builder.setMember(0, "metal");
            builder.setScale(0, ExpressionBuilder.Op.DIVIDE, "2");
            assertEquals("bucket.metal / 2", builder.text());
        }
    }

    @Nested
    class Mixing {
        @Test
        void wholeTermBesideTypeRowsIsRefusedWithAReason() {
            ExpressionBuilder builder = ExpressionBuilder.of("{\"metal\": \"5\"}");
            assertFalse(builder.relate(ANVIL));
            assertTrue(builder.refusal().orElseThrow().contains("per-type"));
            assertEquals("{\"metal\": \"5\"}", builder.text());
        }

        @Test
        void typeRowBesideWholeTermsIsRefusedWithAReason() {
            ExpressionBuilder builder = ExpressionBuilder.of("anvil - $iron_block");
            assertFalse(builder.addType("metal"));
            assertTrue(builder.refusal().orElseThrow().contains("per-type"));
            assertEquals("anvil - $iron_block", builder.text());
        }
    }

    /**
     * A value never empties: the last term or row is kept, and a builder holding nothing writes nothing, since the
     * resolver throws on an empty expression.
     */
    @Nested
    class NeverEmpty {
        @Test
        void removingTheLastTermIsRefused() {
            ExpressionBuilder builder = ExpressionBuilder.of("192");
            builder.remove(0);
            assertTrue(builder.refusal().isPresent());
            assertEquals("192", builder.text());
        }

        @Test
        void removingTheLastRowIsRefused() {
            ExpressionBuilder builder = ExpressionBuilder.of("{\"metal\": \"5\"}");
            builder.removeRow(0);
            assertTrue(builder.refusal().isPresent());
            assertEquals("{\"metal\": \"5\"}", builder.text());
        }

        @Test
        void emptyBuilderWritesNothing() {
            assertTrue(ExpressionBuilder.of("").written().isEmpty());
            ExpressionBuilder perType = ExpressionBuilder.of("192");
            perType.switchToPerType();
            assertTrue(perType.written().isEmpty());
        }
    }

    /**
     * Every piece the builder shows can be changed in place.
     */
    @Nested
    class ChangeInPlace {
        @Test
        void scalarNumberChanges() {
            ExpressionBuilder builder = ExpressionBuilder.of("192");
            builder.setNumber(0, "200");
            assertEquals("200", builder.text());
        }

        @Test
        void relatedItemIsReplaced() {
            ExpressionBuilder builder = ExpressionBuilder.of("3 bucket");
            builder.setRef(0, STICK);
            assertEquals("3 $stick", builder.text());
        }

        @Test
        void rowTypeChanges() {
            ExpressionBuilder builder = ExpressionBuilder.of("{\"metal\": \"5\", \"pulse\": \"2\"}");
            builder.setRowType(0, "blaze");
            assertEquals("{\"blaze\": \"5\", \"pulse\": \"2\"}", builder.text());
            builder.setRowType(0, "pulse");
            assertTrue(builder.refusal().isPresent());
        }

        @Test
        void termWrapsWithTheNextAndUnwraps() {
            ExpressionBuilder builder = ExpressionBuilder.of("bucket + $stick");
            builder.wrapWithNext(0);
            assertEquals("(bucket + $stick)", builder.text());
            builder.unwrap(0);
            assertEquals("bucket + $stick", builder.text());
        }
    }

    /**
     * The value's kind is chosen: scalar, one expression, or per goo type, a row per type; switching back restores
     * what the other kind held.
     */
    @Nested
    class Kind {
        @Test
        void scalarSwitchesToPerTypeAndBack() {
            ExpressionBuilder builder = ExpressionBuilder.of("192");
            assertFalse(builder.isPerType());
            builder.switchToPerType();
            assertTrue(builder.isPerType());
            builder.addType("metal");
            assertEquals("{\"metal\": \"1\"}", builder.text());
            builder.switchToWhole();
            assertEquals("192", builder.text());
        }

        @Test
        void perTypeSwitchesToAScalarOfOne() {
            ExpressionBuilder builder = ExpressionBuilder.of("{\"metal\": \"5\"}");
            builder.switchToWhole();
            assertFalse(builder.isPerType());
            assertEquals("1", builder.text());
        }
    }

    /**
     * A value loaded into the builder writes back unchanged, a form no control builds kept whole.
     */
    @Test
    void loadedValueWritesBackUnchanged() {
        assertEquals("anvil - $iron_block", ExpressionBuilder.of("anvil - $iron_block").text());
        String perType = "{\"jelly\": \"3 $food.jelly / 2\", \"leaf\": \"$dye.leaf - ($food.jelly + 1)\"}";
        assertEquals(perType, ExpressionBuilder.of(perType).text());
        assertEquals("denied", ExpressionBuilder.of("denied").text());
    }
}
