package com.mercuriusxeno.goo.client.hud;

import net.minecraft.core.Direction;
import org.joml.Vector3f;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers which way a side-face panel's nudge moves it: off the face, never into the block. */
class PanelPlacementTest {

    private static final float EPSILON = 1e-6f;

    /**
     * PanelPainter.orient turns the panel by InWorldHud's face rotation and then translates it
     * by the side-face nudge along its own z, so the nudge lands in the world as the rotation
     * applied to (0, 0, nudge); on every horizontal face that vector points along the face's
     * outward normal.
     */
    @ParameterizedTest
    @EnumSource(value = Direction.class, names = {"NORTH", "SOUTH", "EAST", "WEST"})
    void sideFaceNudgeMovesThePanelOutOfTheBlock(Direction face) {
        float nudge = PanelPlacement.Facing.SIDE_FACE.zNudge();
        Vector3f world = InWorldHud.faceRotation(face).transform(new Vector3f(0f, 0f, nudge));
        float outward = world.x * face.getStepX() + world.z * face.getStepZ();

        assertTrue(outward > 0, "The nudge moves the panel " + outward + " blocks along " + face + "'s outward normal");
        assertEquals(Math.abs(nudge), outward, EPSILON);
        assertEquals(0f, world.y, EPSILON);
    }
}
