package com.mercuriusxeno.goo.ability.root;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The vines on a held thing: the cell it crosses latching them, a second
 * cell adding to them, the cell that last gave vines giving none again, and
 * the share they cover as they spread and fall away.
 * vines-is-sprayed-slime-that-entangles-what-tries-to-cross
 * vines-roots-last-three-seconds-plus-one-per-stack-and-each-vine-refreshes
 */
class RootedTest {

    private static final long NOW = 1_000L;
    private static final BlockPos CELL = new BlockPos(3, 64, 3);
    private static final BlockPos NEXT_CELL = CELL.east();
    private static final Vec3 ON_THE_CELL = new Vec3(3.5, 65, 3.5);
    private static final Vec3 ON_THE_NEXT_CELL = new Vec3(4.5, 65, 3.5);
    private static final int FULL = 15;
    private static final float EPSILON = 1e-6f;

    private static Rooted.Touch touch(BlockPos cell, int layers) {
        return new Rooted.Touch(cell, layers, 1.5f);
    }

    private static Rooted latched(int layers) {
        return Rooted.NONE.touched(touch(CELL, layers), ON_THE_CELL, NOW);
    }

    @Nested
    class Touch {

        @Test
        void aFreeThingCrossingACellIsHeldByItsVinesForTheirTime() {
            Rooted rooted = latched(FULL);

            assertEquals(5, rooted.vines());
            assertTrue(rooted.holds(NOW + VineHold.ticksOf(5) - 1));
            assertFalse(rooted.holds(NOW + VineHold.ticksOf(5)));
        }

        @Test
        void theCellThatLastGaveVinesGivesNoneAgain() {
            Rooted rooted = latched(1);

            assertSame(rooted, rooted.touched(touch(CELL, 1), ON_THE_CELL, NOW + 5));
        }

        @Test
        void aSecondCellAddsItsVinesAndKeepsTheLatchTime() {
            Rooted second = latched(3).touched(touch(NEXT_CELL, 3), ON_THE_NEXT_CELL, NOW + 5);

            assertEquals(4, second.vines());
            assertEquals(NOW, second.rootedAt());
        }

        @Test
        void aCellCrossedAfterTheReleaseLatchesFreshVines() {
            long later = NOW + 200;
            Rooted fresh = latched(FULL).releasedAt(NOW + 160).touched(touch(NEXT_CELL, 1), ON_THE_NEXT_CELL, later);

            assertEquals(1, fresh.vines());
            assertEquals(later, fresh.rootedAt());
        }

        @Test
        void heldVinesSlowByTheirCountAndReleasedVinesNone() {
            Rooted rooted = latched(6);

            assertEquals(VineHold.speedOf(3), rooted.speedAt(NOW), EPSILON);
            assertEquals(1.0, rooted.releasedAt(NOW).speedAt(NOW), EPSILON);
        }
    }

    @Nested
    class Cover {

        @Test
        void theVinesSpreadOverTheThingAcrossTheUnpack() {
            Rooted rooted = latched(1);
            assertEquals(0f, rooted.coverAt(NOW), EPSILON);
            assertEquals(0.5f, rooted.coverAt(NOW + Rooted.UNPACK_TICKS / 2f), EPSILON);
            assertEquals(1f, rooted.coverAt(NOW + Rooted.UNPACK_TICKS), EPSILON);
        }

        @Test
        void releasedVinesFallAwayAcrossTheFade() {
            long released = NOW + 20;
            Rooted rooted = latched(1).releasedAt(released);
            assertEquals(1f, rooted.coverAt(released), EPSILON);
            assertEquals(0.5f, rooted.coverAt(released + Rooted.FADE_TICKS / 2f), EPSILON);
            assertEquals(0f, rooted.coverAt(released + Rooted.FADE_TICKS), EPSILON);
        }
    }
}
