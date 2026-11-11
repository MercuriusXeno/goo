package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.quantum.Gate;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A player its own client carries out of a portal it backs out of, looking
 * into that portal, has its eye set clear of the portal's face by more than
 * the camera's near plane, so the first frame never cuts the face and its
 * wall away (decision portals-link-by-looking-through-portal-vision).
 */
class ClientGatePassageTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    /** A wall portal on a south face, the face the plane z 3. */
    private static final Gate EXIT = new Gate(OVERWORLD, new AABB(new BlockPos(0, 0, 2)).minmax(
            new AABB(new BlockPos(0, 1, 2))), Direction.SOUTH, Direction.UP.getUnitVec3());
    private static final double EYE = 1.62;
    /** Feet whose eye stands two hundredths before the face, where the probe found it backing out. */
    private static final Vec3 FEET_AT_THE_FACE = new Vec3(0.5, 0.0, 3.02);
    private static final Vec3 LOOKING_INTO_IT = new Vec3(0, 0, -1);
    private static final Vec3 LOOKING_AWAY = new Vec3(0, 0, 1);
    /** Twice the camera's near plane of five hundredths. */
    private static final double NEAR_CLEAR = 0.1;

    @Test
    void anEyeBackedOutOfAPortalLookingIntoItStandsClearOfItsFace() {
        Vec3 feet = ClientGatePassage.eyeClearOfTheFace(EXIT, FEET_AT_THE_FACE, EYE, LOOKING_INTO_IT);

        assertTrue(EXIT.out(feet.add(0, EYE, 0)) >= NEAR_CLEAR - 1.0E-9, "The eye should stand at least "
                + NEAR_CLEAR + " before the face it looks into, stands " + EXIT.out(feet.add(0, EYE, 0)));
    }

    @Test
    void anEyeWalkingOutOfAPortalLookingAwayStandsWhereItCameOut() {
        assertEquals(FEET_AT_THE_FACE, ClientGatePassage.eyeClearOfTheFace(EXIT, FEET_AT_THE_FACE, EYE,
                LOOKING_AWAY));
    }
}
