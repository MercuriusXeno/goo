package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.quantum.ExitTilt;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The first-person camera of a player come out of a pair tilted is its own
 * look turned by the tilt its body still holds, and once the tilt has eased
 * to nothing it is the look alone
 * (decision bodies-emerge-along-their-travel-and-ease-upright).
 */
class CameraTiltTest {

    private static final float YAW = 30F;
    private static final float PITCH = 20F;
    /** A quarter turn about the right of a wall portal facing north, east. */
    private static final ExitTilt QUARTER = new ExitTilt(new Vec3(1, 0, 0), 90.0);
    private static final ExitTilt NONE = new ExitTilt(new Vec3(1, 0, 0), 0.0);
    private static final float CLOSE = 1.0E-3F;

    @Test
    void theCameraAtTheFullTiltIsTheLookTurnedByIt() {
        CameraTilt.Angles turned = CameraTilt.turnedBy(YAW, PITCH, QUARTER);
        Quaternionf expected = new Quaternionf().rotationAxis((float) Math.toRadians(QUARTER.degrees()), 1, 0, 0)
                .mul(cameraOf(YAW, PITCH, 0F));
        Quaternionf drawn = cameraOf(turned.yaw(), turned.pitch(), turned.roll());
        assertTrue(Math.abs(Math.abs(expected.dot(drawn)) - 1F) < CLOSE, "The camera at the full tilt should be the "
                + "look turned a quarter turn, reads " + turned);
    }

    @Test
    void theCameraWithNoTiltLeftIsTheLookAlone() {
        CameraTilt.Angles turned = CameraTilt.turnedBy(YAW, PITCH, NONE);
        assertEquals(YAW, turned.yaw(), CLOSE);
        assertEquals(PITCH, turned.pitch(), CLOSE);
        assertEquals(0F, turned.roll(), CLOSE);
    }

    /** The camera's rotation for angles, as Minecraft's Camera builds it. */
    private static Quaternionf cameraOf(float yaw, float pitch, float roll) {
        return new Quaternionf().rotationYXZ((float) Math.PI - (float) Math.toRadians(yaw),
                -(float) Math.toRadians(pitch), -(float) Math.toRadians(roll));
    }
}
