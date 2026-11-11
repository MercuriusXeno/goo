package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.quantum.ExitTilt;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    private static final int PLAYER = 4107;
    private static final long EMERGED_AT = 98_210L;

    /**
     * A player holding an exit tilt has the frame's camera angles turned by
     * it, written to the camera event alone: the player's own yaw and pitch
     * are never set, so they stay as the landing set them.
     */
    @Test
    void theTiltTurnsTheCameraEventsAnglesAndNeverThePlayers() {
        ClientExitTilts.record(PLAYER, QUARTER, EMERGED_AT);
        ViewportEvent.ComputeCameraAngles event = mock(ViewportEvent.ComputeCameraAngles.class);
        when(event.getYaw()).thenReturn(YAW);
        when(event.getPitch()).thenReturn(PITCH);
        Entity player = mock(Entity.class);
        when(player.getId()).thenReturn(PLAYER);

        CameraTilt.turnCamera(event, player, EMERGED_AT);

        CameraTilt.Angles turned = CameraTilt.turnedBy(YAW, PITCH, QUARTER);
        verify(event).setYaw(turned.yaw());
        verify(event).setPitch(turned.pitch());
        verify(event).setRoll(turned.roll());
        verify(player, never()).setYRot(anyFloat());
        verify(player, never()).setXRot(anyFloat());
    }

    /** The camera's rotation for angles, as Minecraft's Camera builds it. */
    private static Quaternionf cameraOf(float yaw, float pitch, float roll) {
        return new Quaternionf().rotationYXZ((float) Math.PI - (float) Math.toRadians(yaw),
                -(float) Math.toRadians(pitch), -(float) Math.toRadians(roll));
    }
}
