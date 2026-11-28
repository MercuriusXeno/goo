package com.mercuriusxeno.goo.lunar;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A body crossing lunar's edge comes out the same distance in from the other edge, at its own height, facing as it
 * faced and moving as it moved; a body inside the moon's span stays where it is.
 * decision moon-wraps-at-its-border-like-the-zone
 */
class LunarCrossingTest {

    private static final Vec3 MOTION = new Vec3(0.3, -0.08, 0.1);
    private static final float YAW = 87.5F;
    private static final float PITCH = -12.25F;

    @Test
    void aBodyPastTheEastEdgeComesOutPastTheWestEdgeAtItsOwnHeight() {
        assertEquals(new Vec3(-7_499_999.5, 64.25, 12.5),
                carried(new Vec3(7_500_000.5, 64.25, 12.5)).position());
    }

    @Test
    void aBodyPastTheNorthEdgeComesOutPastTheSouthEdgeAtItsOwnHeight() {
        assertEquals(new Vec3(-40.0, 71.0, 7_499_999.75),
                carried(new Vec3(-40.0, 71.0, -7_500_000.25)).position());
    }

    @Test
    void aBodyCrossingKeepsItsMotion() {
        assertEquals(MOTION, carried(new Vec3(7_500_000.5, 64.25, 12.5)).motion());
    }

    @Test
    void aBodyCrossingKeepsItsFacing() {
        LunarCrossing.Carry carry = carried(new Vec3(7_500_000.5, 64.25, 12.5));
        assertEquals(YAW, carry.yaw());
        assertEquals(PITCH, carry.pitch());
    }

    @Test
    void aBodyInsideTheMoonStaysWhereItIs() {
        assertTrue(LunarCrossing.across(new Vec3(7_499_999.5, 64.0, 0.0), MOTION, YAW, PITCH).isEmpty());
    }

    private static LunarCrossing.Carry carried(Vec3 position) {
        Optional<LunarCrossing.Carry> carry = LunarCrossing.across(position, MOTION, YAW, PITCH);
        assertTrue(carry.isPresent(), "a body at " + position + " should cross the seam");
        return carry.get();
    }
}
