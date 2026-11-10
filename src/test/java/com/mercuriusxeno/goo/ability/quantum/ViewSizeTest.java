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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A gate's size on screen reads from its span over its distance at the
 * camera's vertical field of view and the window's height; a nested view
 * whose gate shows under a few pixels is skipped, and a gate the main frame
 * sees directly is drawn whatever its size
 * (decision nested-views-skip-when-tiny-or-outside-the-parent-opening).
 */
class ViewSizeTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    /** A two-tall wall gate on a north face, its middle at (0.5, 1, 0). */
    private static final Gate TWO_TALL = new Gate(OVERWORLD, new AABB(new BlockPos(0, 0, 0)).minmax(
            new AABB(new BlockPos(0, 1, 0))), Direction.NORTH, Direction.UP.getUnitVec3());
    private static final double FOV = 70.0;
    private static final int HEIGHT = 1080;

    @Test
    void aTwoTallGateReadsItsPixelsFromItsSpanOverItsDistance() {
        assertEquals(1543.0, ViewSize.pixelsAcross(new Vec3(0.5, 1.0, -1.0), TWO_TALL, FOV, HEIGHT), 2.0,
                "A two-tall gate one block away should span about 1543 pixels");
        assertEquals(15.4, ViewSize.pixelsAcross(new Vec3(0.5, 1.0, -100.0), TWO_TALL, FOV, HEIGHT), 0.5,
                "A two-tall gate a hundred blocks away should span about 15 pixels");
    }

    @Test
    void aNestedViewUnderTheThresholdIsSkippedAndOneAboveItIsDrawn() {
        assertTrue(ViewSize.skips(2, ViewSize.TOO_SMALL_PIXELS - 1), "A depth-two view under the threshold should skip");
        assertFalse(ViewSize.skips(2, ViewSize.TOO_SMALL_PIXELS + 1), "A depth-two view above it should draw");
    }

    @Test
    void aViewTheMainFrameSeesDirectlyIsDrawnWhateverItsSize() {
        assertFalse(ViewSize.skips(1, ViewSize.TOO_SMALL_PIXELS - 1), "A depth-one view should draw however small");
    }
}
