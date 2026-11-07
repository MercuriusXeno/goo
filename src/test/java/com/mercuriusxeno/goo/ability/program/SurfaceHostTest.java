package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A surface host anchors its world actions at the center of the face it
 * names, the floor's top face for a spray's reach.
 */
class SurfaceHostTest {

    private static final BlockPos FLOOR = new BlockPos(4, 60, -9);

    @Test
    void faceCenterSitsHalfABlockOutOfTheBlockCenter() {
        assertEquals(new Vec3(4.5, 61.0, -8.5), SurfaceHost.faceCenter(FLOOR, Direction.UP));
        assertEquals(new Vec3(5.0, 60.5, -8.5), SurfaceHost.faceCenter(FLOOR, Direction.EAST));
    }
}
