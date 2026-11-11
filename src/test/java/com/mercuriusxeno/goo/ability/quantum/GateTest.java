package com.mercuriusxeno.goo.ability.quantum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers a gate's opening by its mask: an oval gate opens the oval inside its
 * face, a rectangular one the whole face, corners and all
 * (decision outsider-room-wraps-and-repeats-at-the-platform-edge).
 */
class GateTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    /** A 3x3 face on a wall's north side, the plane z 0, from x -1 to 2 and y 0 to 3. */
    private static final AABB WALL = new AABB(new BlockPos(-1, 0, 0)).minmax(new AABB(new BlockPos(1, 2, 0)));
    private static final Gate OVAL = new Gate(OVERWORLD, WALL, Direction.NORTH, Direction.UP.getUnitVec3());
    private static final Gate RECTANGLE = new Gate(OVERWORLD, WALL, Direction.NORTH, Direction.UP.getUnitVec3(),
            Gate.Mask.RECTANGLE);
    /** Just inside the face's top east corner. */
    private static final Vec3 CORNER = new Vec3(1.9, 2.9, 0);
    /** Just outside the face, past its east side. */
    private static final Vec3 PAST_THE_SIDE = new Vec3(2.1, 1.5, 0);

    @Test
    void aRectangularGateOpensAtItsFacesCorner() {
        assertTrue(RECTANGLE.within(CORNER), "a rectangular gate should open at its face's corner");
    }

    @Test
    void anOvalGateRefusesItsFacesCorner() {
        assertFalse(OVAL.within(CORNER), "an oval gate should refuse its face's corner");
    }

    @Test
    void aRectangularGateRefusesAPointPastItsSide() {
        assertFalse(RECTANGLE.within(PAST_THE_SIDE), "a rectangular gate should refuse a point past its face");
    }

    @Test
    void aMarginGrowsTheRectangle() {
        assertTrue(RECTANGLE.within(PAST_THE_SIDE, 0.2), "a margin should grow the rectangle past its side");
    }
}
