package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The soup drinks the square about the aimed block on the face under the
 * cursor, one deep, the outer ring first and the aimed block last
 * (decision unmake-waves-dissolve-by-crucible-cost).
 */
class SiphonFaceTest {

    private static final BlockPos AIMED = new BlockPos(4, 2, 3);

    @Test
    void aWestFaceGivesANineBlockSquareInItsPlane() {
        List<BlockPos> square = SiphonFace.square(AIMED, Direction.Axis.X, 1);

        assertEquals(9, new HashSet<>(square).size());
        assertTrue(square.stream().allMatch(pos -> pos.getX() == AIMED.getX()));
        assertEquals(AIMED, square.getLast());
    }

    @Test
    void theCornersComeFirstThenTheSides() {
        List<BlockPos> square = SiphonFace.square(AIMED, Direction.Axis.Y, 1);

        for (int corner = 0; corner < 4; corner++) {
            BlockPos pos = square.get(corner);
            assertEquals(2, Math.abs(pos.getX() - AIMED.getX()) + Math.abs(pos.getZ() - AIMED.getZ()));
        }
        for (int side = 4; side < 8; side++) {
            BlockPos pos = square.get(side);
            assertEquals(1, Math.abs(pos.getX() - AIMED.getX()) + Math.abs(pos.getZ() - AIMED.getZ()));
        }
    }

    @Test
    void aChargedSquareDrinksItsOuterRingBeforeItsInner() {
        List<BlockPos> square = SiphonFace.square(AIMED, Direction.Axis.Y, 2);

        assertEquals(2, Math.max(Math.abs(square.get(15).getX() - AIMED.getX()),
                Math.abs(square.get(15).getZ() - AIMED.getZ())));
        assertEquals(1, Math.max(Math.abs(square.get(16).getX() - AIMED.getX()),
                Math.abs(square.get(16).getZ() - AIMED.getZ())));
    }

    @Test
    void aChargedRadiusGivesAFiveByFive() {
        assertEquals(25, new HashSet<>(SiphonFace.square(AIMED, Direction.Axis.Z, 2)).size());
    }

    @Test
    void theFaceIsTheSideTheCursorRestsOn() {
        assertEquals(Direction.WEST, SiphonFace.faceOf(new Vec3(4, 2.3, 3.8), AIMED));
        assertEquals(Direction.UP, SiphonFace.faceOf(new Vec3(4.2, 3, 3.6), AIMED));
    }
}
