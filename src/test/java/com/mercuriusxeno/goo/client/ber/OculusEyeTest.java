package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.alientech.OculusBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The oculus's eye hovers off the face its base stands on, starts still, and its lids close in whole pixel rows
 * (decision oculus-prism-becomes-a-hovering-eye).
 */
class OculusEyeTest {

    private static final float EPSILON = 1e-4f;

    @Test
    void aFloorOculusEyeHoversOverItsBase() {
        assertEquals(new Vec3(0.5, OculusBlock.EYE_LIFT / 16, 0.5), OculusEye.eyeInCell(Direction.UP, OculusBlock.EYE_LIFT));
    }

    @Test
    void aWallOculusEyeHoversOffTheWall() {
        assertEquals(OculusBlock.EYE_LIFT / 16, OculusEye.eyeInCell(Direction.SOUTH, OculusBlock.EYE_LIFT).z, EPSILON);
    }

    @Test
    void theEyeStartsStill() {
        assertEquals(0f, OculusEye.hover(0f), EPSILON);
    }

    /** operator ruling 2026-11-28: an open eye keeps the last row of each lid */
    @Test
    void theLidsCloseInWholePixelRowsAndKeepARowOpen() {
        assertEquals(OculusEye.OPEN_LID_ROWS, OculusEye.lidRows(0f, OculusEye.TOP_LID_ROWS));
        assertEquals(OculusEye.TOP_LID_ROWS + OculusEye.BOTTOM_LID_ROWS,
                OculusEye.lidRows(1f, OculusEye.TOP_LID_ROWS) + OculusEye.lidRows(1f, OculusEye.BOTTOM_LID_ROWS));
        assertEquals(2, OculusEye.lidRows(0.6f, OculusEye.BOTTOM_LID_ROWS));
    }

    /** decision oculus-eyelid-is-shaded-hide-with-a-seam */
    @Test
    void eachLidsLeadingRowIsTheSeamAtEveryClosure() {
        for (int shown = 1; shown <= OculusEye.TOP_LID_ROWS; shown++) {
            assertEquals(OculusEye.TOP_LID_ROWS - 1, OculusEye.upperLidRow(shown, shown - 1));
            assertEquals(0, OculusEye.upperLidRow(OculusEye.TOP_LID_ROWS, 0));
        }
        for (int shown = 1; shown <= OculusEye.BOTTOM_LID_ROWS; shown++) {
            assertEquals(OculusEye.TOP_LID_ROWS, OculusEye.lowerLidRow(shown, shown - 1));
        }
        assertEquals(OculusEye.TOP_LID_ROWS + OculusEye.BOTTOM_LID_ROWS - 1,
                OculusEye.lowerLidRow(OculusEye.BOTTOM_LID_ROWS, 0));
    }
}
