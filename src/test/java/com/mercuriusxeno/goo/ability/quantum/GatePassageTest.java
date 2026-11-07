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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The camera never reaches a gate's face: a body is carried while its eye
 * still stands a little before the face, nearer than the camera's near
 * plane would let it come without clipping into the wall, and it lands that
 * same margin before the partner's face
 * (decisions portals-link-by-looking-through-portal-vision, hole-closes-the-space-to-an-opening-ahead).
 */
class GatePassageTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    /** A 3x3 mouth on a wall's north face, the face the plane z 0. */
    private static final Gate NEAR = new Gate(OVERWORLD, new AABB(new BlockPos(-1, 0, 0)).minmax(
            new AABB(new BlockPos(1, 2, 0))), Direction.NORTH, Direction.UP.getUnitVec3());
    /** Its partner on the far face, the plane z 3, facing south. */
    private static final Gate FAR = new Gate(OVERWORLD, new AABB(new BlockPos(-1, 0, 2)).minmax(
            new AABB(new BlockPos(1, 2, 2))), Direction.SOUTH, Direction.UP.getUnitVec3());
    /** The camera's near plane, in blocks. */
    private static final double NEAR_PLANE = 0.05;

    @Test
    void anEyeNearerThanTheNearPlaneToTheFaceHasCrossed() {
        Vec3 before = new Vec3(0.5, 1.5, -0.2);
        Vec3 now = new Vec3(0.5, 1.5, -0.03);
        assertTrue(GatePassage.crossed(NEAR, before, now),
                "An eye three hundredths before the face, nearer than the near plane, should cross");
    }

    @Test
    void aCrossedEyeLandsBeyondTheNearPlaneBeforeThePartnersFace() {
        Vec3 eye = new Vec3(0.5, 1.5, -0.03);
        GatePassage.Landing landing = GatePassage.landingThrough(NEAR, FAR, new GatePassage.Held(eye, 1.62), 1.8,
                Vec3.ZERO, 0F, 0F);
        double out = FAR.out(landing.feet().add(0, 1.62, 0));
        assertTrue(out > NEAR_PLANE, "The eye should land past the near plane before the partner's face, stands "
                + out + " out of it");
    }
}
