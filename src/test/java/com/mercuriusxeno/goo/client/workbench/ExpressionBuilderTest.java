package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
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
        void rowMemberChanges() {
            ExpressionBuilder builder = ExpressionBuilder.of("{\"metal\": \"bucket.metal / 2\"}");
            builder.setRowMember(0, "pulse");
            assertEquals("{\"metal\": \"bucket.pulse / 2\"}", builder.text());
        }
    }

    /**
     * A sum of terms stands as one term, its own terms edited in place, and the whole value scales as one sum, the
     * two ways the game's files use parentheses.
     */
    @Nested
    class Sums {
        private static final List<Integer> FIRST_SUM = List.of(1);

        private static final List<Integer> LEAF_ROW = List.of(0);

        // Each step's string is one the file grammar reads.
        private static void assertWrites(String expected, ExpressionBuilder builder) {
            assertEquals(expected, builder.text());
            assertTrue(ExpressionTerms.parseShown(builder.text()).isPresent(), builder.text());
        }

        // Decision builder-is-kind-plus-then-rows-of-terms-with-term-operators: the joined add, () and ^ v build
        // beetroot's leaf from an empty row.
        @Test
        void wrapMoveAndTheJoinedAddBuildBeetrootsLeafFromAnEmptyRow() {
            ExpressionBuilder builder = ExpressionBuilder.of("");
            builder.switchToPerType();
            builder.addType("leaf");
            assertWrites("{\"leaf\": \"1\"}", builder);
            builder.setOperand(List.of(0, 0), new ExpressionBuilder.Ref(SearchDropdown.Kind.PREFAB, "dye", "leaf"));
            assertWrites("{\"leaf\": \"$dye.leaf\"}", builder);
            assertTrue(builder.add(LEAF_ROW, new ExpressionBuilder.Ref(SearchDropdown.Kind.PREFAB, "food", "jelly"),
                    ExpressionTerms.Join.MINUS));
            assertWrites("{\"leaf\": \"$dye.leaf - $food.jelly\"}", builder);
            builder.wrap(List.of(0, 1));
            assertWrites("{\"leaf\": \"$dye.leaf - ($food.jelly)\"}", builder);
            assertTrue(builder.add(List.of(0, 1), new ExpressionBuilder.Ref(SearchDropdown.Kind.PREFAB, "saturation",
                    "jelly"), ExpressionTerms.Join.PLUS));
            builder.setScale(List.of(0, 1, 1), ExpressionBuilder.Op.MULTIPLY, "12");
            assertWrites("{\"leaf\": \"$dye.leaf - ($food.jelly + 12 $saturation.jelly)\"}", builder);
            builder.move(List.of(0, 1, 1), -1);
            assertWrites("{\"leaf\": \"$dye.leaf - (12 $saturation.jelly + $food.jelly)\"}", builder);
            builder.move(List.of(0, 1, 0), 1);
            assertWrites("{\"leaf\": \"$dye.leaf - ($food.jelly + 12 $saturation.jelly)\"}", builder);
        }

        // Decision parenthetical-handle-drags-onto-a-term: the handle dropped on a term inside a parenthetical nests a
        // group there, and dropped on the parenthetical itself wraps it in a second group.
        @Test
        void wrapNestsOnAnInnerTermAndOnAParentheticalItself() {
            ExpressionBuilder inner = ExpressionBuilder.of("$waxed + ($edited - $base)");
            inner.wrap(List.of(1, 1));
            assertWrites("$waxed + ($edited - ($base))", inner);
            ExpressionBuilder outer = ExpressionBuilder.of("$waxed + ($edited - $base)");
            outer.wrap(List.of(1));
            assertWrites("$waxed + (($edited - $base))", outer);
        }

        @Test
        void firstTermMovesNoHigherAndLastNoLower() {
            ExpressionBuilder builder = ExpressionBuilder.of("$waxed + $edited");
            builder.move(List.of(0), -1);
            builder.move(List.of(1), 1);
            assertEquals("$waxed + $edited", builder.text());
            builder.move(List.of(1), -1);
            assertEquals("$edited + $waxed", builder.text());
        }

        // A first term takes no sign, so a move landing a subtracted term first would drop its minus and change the
        // value; the move is refused and the value stands, as removing the last term is refused.
        @Test
        void subtractedTermMovesNoFirstUpOrDown() {
            ExpressionBuilder up = ExpressionBuilder.of("$waxed - $edited");
            assertFalse(up.canMove(List.of(1), -1));
            up.move(List.of(1), -1);
            assertEquals("$waxed - $edited", up.text());
            assertTrue(up.refusal().isPresent());
            ExpressionBuilder down = ExpressionBuilder.of("$waxed - $edited");
            assertFalse(down.canMove(List.of(0), 1));
            down.move(List.of(0), 1);
            assertEquals("$waxed - $edited", down.text());
            assertTrue(down.refusal().isPresent());
        }

        // Decision terms-drag-by-rails-with-a-preview-and-a-blue-drop-cursor: a dragged term lands in any slot, a
        // later one in its own sum, inside a parenthetical or out of one.
        @Test
        void dropMovesATermToALaterSlotIntoAndOutOfAParenthetical() {
            ExpressionBuilder later = ExpressionBuilder.of("$waxed + $edited + $food");
            assertTrue(later.moveTo(List.of(0), List.of(), 3));
            assertWrites("$edited + $food + $waxed", later);
            ExpressionBuilder into = ExpressionBuilder.of("$waxed + ($edited + $food)");
            assertTrue(into.moveTo(List.of(0), List.of(1), 1));
            assertWrites("($edited + $waxed + $food)", into);
            ExpressionBuilder out = ExpressionBuilder.of("$waxed + ($edited + $food)");
            assertTrue(out.moveTo(List.of(1, 1), List.of(), 1));
            assertWrites("$waxed + $food + ($edited)", out);
        }

        // A drop that would empty a sum, land a subtracted term first or drop a parenthetical into itself is refused
        // and the value stands.
        @Test
        void dropThatBreaksTheValueIsRefused() {
            ExpressionBuilder emptying = ExpressionBuilder.of("$waxed + ($edited)");
            assertFalse(emptying.moveTo(List.of(1, 0), List.of(), 0));
            assertEquals("$waxed + ($edited)", emptying.text());
            ExpressionBuilder subtracted = ExpressionBuilder.of("$waxed - $edited");
            assertFalse(subtracted.moveTo(List.of(1), List.of(), 0));
            assertEquals("$waxed - $edited", subtracted.text());
            ExpressionBuilder itself = ExpressionBuilder.of("$waxed + ($edited + $food)");
            assertFalse(itself.moveTo(List.of(1), List.of(1), 0));
            assertEquals("$waxed + ($edited + $food)", itself.text());
        }

        // A move that lands no subtracted term first keeps every term's sign, so the value stands.
        @Test
        void moveKeepsTheSumsValue() {
            ExpressionBuilder builder = ExpressionBuilder.of("$waxed + $edited - $food");
            assertTrue(builder.canMove(List.of(2), -1));
            builder.move(List.of(2), -1);
            assertEquals("$waxed - $food + $edited", builder.text());
        }

        @Test
        void joinedAddWritesTheChosenSignToTheWholeValue() {
            ExpressionBuilder builder = ExpressionBuilder.of("$waxed");
            builder.add(List.of(), new ExpressionBuilder.Ref(SearchDropdown.Kind.PREFAB, "edited", null),
                    ExpressionTerms.Join.MINUS);
            assertEquals("$waxed - $edited", builder.text());
        }

        // Decision sum-of-terms-leaves-the-builder: a parenthetical the file holds still edits in place.
        @Test
        void beetrootsParentheticalWritesBackAfterAnInnerEdit() {
            ExpressionBuilder builder = ExpressionBuilder.of("$dye.leaf - ($food.jelly + $saturation.jelly * 12)");
            builder.setJoin(List.of(1, 1), ExpressionTerms.Join.MINUS);
            assertEquals("$dye.leaf - ($food.jelly - $saturation.jelly * 12)", builder.text());
        }

        @Test
        void loadedSumEditsInPlace() {
            ExpressionBuilder builder = ExpressionBuilder.of("$dye.leaf - ($food.jelly + 1)");
            builder.setNumber(List.of(1, 1), "4");
            assertEquals("$dye.leaf - ($food.jelly + 4)", builder.text());
            builder.remove(List.of(1, 0));
            assertEquals("$dye.leaf - (4)", builder.text());
        }

        @Test
        void wholeValueScalesAsOneSum() {
            ExpressionBuilder builder = ExpressionBuilder.of("$medium - 4 $food.jelly");
            builder.scaleWhole(ExpressionBuilder.Op.DIVIDE, "2");
            assertEquals("($medium - 4 $food.jelly) / 2", builder.text());
        }

        @Test
        void wholeSumScaledOneToOneComesApart() {
            ExpressionBuilder builder = ExpressionBuilder.of("($medium - $food.jelly) / 2");
            builder.setScale(List.of(0), ExpressionBuilder.Op.DIVIDE, "1");
            assertEquals("$medium - $food.jelly", builder.text());
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
        void eachWholeKindKeepsItsOwnTerms() {
            ExpressionBuilder builder = ExpressionBuilder.of("192");
            builder.nameWholeSort(BuilderGrammar.NUMBER_SORT);
            builder.switchToWhole(BuilderGrammar.GOO_SORT, List.of(ExpressionBuilder.firstTerm(
                    new ExpressionBuilder.Ref(SearchDropdown.Kind.PREFAB, "stick", null))));
            assertEquals("$stick", builder.text());
            builder.switchToWhole(BuilderGrammar.NUMBER_SORT, List.of());
            assertEquals("192", builder.text());
            assertTrue(builder.holdsKept(BuilderGrammar.GOO_SORT));
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
     * A ratio holds a multiplier and a divisor together, each set leaving the other side standing (decision
     * ratio-holds-multiplier-and-divisor-together).
     */
    @Nested
    class RatioHoldsBothSides {
        @Test
        void termKeepsItsMultiplierWhenItsDivisorIsSet() {
            ExpressionBuilder builder = ExpressionBuilder.of("$x");
            builder.setScale(0, ExpressionBuilder.Op.MULTIPLY, "2");
            builder.setScale(0, ExpressionBuilder.Op.DIVIDE, "3");
            assertEquals("2 $x / 3", builder.text());
        }

        @Test
        void sumKeepsItsMultiplierWhenItsDivisorIsSet() {
            ExpressionBuilder builder = ExpressionBuilder.of("$medium - 4 $food.jelly");
            builder.scaleWhole(ExpressionBuilder.Op.MULTIPLY, "2");
            builder.setScale(List.of(0), ExpressionBuilder.Op.DIVIDE, "3");
            assertEquals("2 ($medium - 4 $food.jelly) / 3", builder.text());
        }

        @Test
        void gooTypeRowKeepsItsMultiplierWhenItsDivisorIsSet() {
            ExpressionBuilder builder = ExpressionBuilder.of("");
            builder.addType("metal");
            builder.setRowRelative(0, BUCKET, "metal");
            builder.setRowScale(0, ExpressionBuilder.Op.MULTIPLY, "2");
            builder.setRowScale(0, ExpressionBuilder.Op.DIVIDE, "3");
            assertEquals("{\"metal\": \"2 bucket.metal / 3\"}", builder.text());
        }

        @Test
        void numberOperandWritesBothSidesApart() {
            ExpressionBuilder builder = ExpressionBuilder.of("5");
            builder.setScale(0, ExpressionBuilder.Op.MULTIPLY, "2");
            builder.setScale(0, ExpressionBuilder.Op.DIVIDE, "3");
            assertEquals("5 * 2 / 3", builder.text());
            ExpressionBuilder.Piece read = ExpressionBuilder.of("5 * 2 / 3").terms().getFirst();
            assertEquals(List.of("2", "3"), List.of(read.multiplier(), read.divisor()));
        }

        @Test
        void twoSidedTermOpensAsOnePieceAndWritesBackUnchanged() {
            ExpressionBuilder builder = ExpressionBuilder.of("2 $iron_ingot / 3");
            ExpressionBuilder.Piece piece = builder.terms().getFirst();
            assertEquals(new ExpressionBuilder.Ref(SearchDropdown.Kind.PREFAB, "iron_ingot", null), piece.operand());
            assertEquals(List.of("2", "3"), List.of(piece.multiplier(), piece.divisor()));
            assertEquals("2 $iron_ingot / 3", builder.text());
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
