package com.mercuriusxeno.goo.block.alientech;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The metronome's shape holds its switch torch and its button where its model draws them, turned to its back, so a
 * look aimed at the torch lands on the torch (operator ruling 2026-11-28).
 */
class MetronomeShapeTest {

    private static final double PIXEL = 1.0 / 16;

    private static boolean holds(VoxelShape shape, double x, double y, double z) {
        Vec3 point = new Vec3(x * PIXEL, y * PIXEL, z * PIXEL);
        return shape.toAabbs().stream().anyMatch(box -> box.contains(point));
    }

    @Test
    void aSouthBackedMetronomeHoldsItsTorchAtTheFrontAndItsButtonAtTheBack() {
        VoxelShape shape = MetronomeShape.of(Direction.SOUTH, 1, false);
        assertTrue(holds(shape, 8, 5, 7), "the torch at its first step");
        assertTrue(holds(shape, 8, 3, 12), "the button");
        assertFalse(holds(shape, 8, 5, 12), "nothing above the button");
    }

    @Test
    void anEastBackedMetronomeTurnsItsPartsToTheEast() {
        VoxelShape shape = MetronomeShape.of(Direction.EAST, 1, false);
        assertTrue(holds(shape, 12, 3, 8), "the button toward the east");
        assertTrue(holds(shape, 7, 5, 8), "the torch toward the west");
    }

    @Test
    void aPressedButtonStandsLower() {
        assertFalse(holds(MetronomeShape.of(Direction.SOUTH, 1, true), 8, 3.5, 12));
    }
}
