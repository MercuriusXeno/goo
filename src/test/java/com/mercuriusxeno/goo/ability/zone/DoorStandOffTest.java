package com.mercuriusxeno.goo.ability.zone;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers how far before its caster Tartarus' doorway stands, read over a block lookup the test builds
 * (decision tartarus-draws-its-own-portal-with-the-nether-portal-texture).
 */
class DoorStandOffTest {

    private static final double EXACT = 1e-9;
    /** Feet in the middle of block (0, 64, 0), facing south, toward +Z. */
    private static final Vec3 FEET = new Vec3(0.5, 64, 0.5);
    private static final float FACING_SOUTH = 0f;

    @Test
    void openGroundStandsTheDoorwayThreeQuartersOfABlockBeforeYou() {
        assertEquals(0.75, GateLook.NETHER_PORTAL.standsOff(), EXACT);
        assertEquals(0.75, DoorStandOff.clearAhead(pos -> false, FEET, FACING_SOUTH, 0.75), EXACT);
    }

    @Test
    void aWallAtHeadHeightHalfABlockAheadPullsTheDoorwayShortOfIt() {
        BlockPos wall = new BlockPos(0, 65, 1);

        double standsOff = DoorStandOff.clearAhead(wall::equals, FEET, FACING_SOUTH, 0.75);

        assertEquals(0.5 - DoorStandOff.STEP, standsOff, EXACT);
    }

    @Test
    void zoneAndEndKeepTheirBlockAndAHalf() {
        assertEquals(ConjuredDoor.STANDS_OFF, GateLook.STARFIELD.standsOff(), EXACT);
    }
}
