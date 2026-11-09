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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A body coming out of a pair whose gates face different ways emerges along
 * the way it travelled, tilted from upright, and the tilt eases to nothing
 * over about half a second; a pair keeping up as up tilts nothing
 * (decision bodies-emerge-along-their-travel-and-ease-upright).
 */
class ExitTiltTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    /** A floor portal on the top of a block at y 64, running north. */
    private static final Gate FLOOR = new Gate(OVERWORLD, new AABB(new BlockPos(0, 64, 0)).minmax(
            new AABB(new BlockPos(0, 64, 1))), Direction.UP, Direction.NORTH.getUnitVec3());
    /** A second floor portal elsewhere. */
    private static final Gate OTHER_FLOOR = new Gate(OVERWORLD, new AABB(new BlockPos(8, 64, 0)).minmax(
            new AABB(new BlockPos(8, 64, 1))), Direction.UP, Direction.NORTH.getUnitVec3());
    /** A ceiling portal on the underside of a block at y 70. */
    private static final Gate CEILING = new Gate(OVERWORLD, new AABB(new BlockPos(0, 70, 0)).minmax(
            new AABB(new BlockPos(0, 70, 1))), Direction.DOWN, Direction.NORTH.getUnitVec3());
    /** A wall portal two tall on a north face. */
    private static final Gate NORTH_WALL = new Gate(OVERWORLD, new AABB(new BlockPos(0, 64, 10)).minmax(
            new AABB(new BlockPos(0, 65, 10))), Direction.NORTH, Direction.UP.getUnitVec3());
    /** A wall portal two tall on a south face, facing the north one. */
    private static final Gate SOUTH_WALL = new Gate(OVERWORLD, new AABB(new BlockPos(0, 64, 20)).minmax(
            new AABB(new BlockPos(0, 65, 20))), Direction.SOUTH, Direction.UP.getUnitVec3());
    private static final double QUARTER = 90.0;
    private static final double HALF_TURN = 180.0;
    /** About half a second. */
    private static final int EASE_TICKS = 10;
    private static final double CLOSE = 1.0E-6;

    @Test
    void aFloorGateIntoAWallGateEmergesAQuarterTurnAboutTheExitsRightAndEasesUpright() {
        ExitTilt tilt = ExitTilt.between(FLOOR, NORTH_WALL);
        Vec3 right = NORTH_WALL.up().cross(NORTH_WALL.face().getUnitVec3());
        assertEquals(QUARTER, tilt.degreesAt(0), CLOSE, "The body should emerge a quarter turn from upright");
        assertEquals(1.0, Math.abs(tilt.axis().dot(right)), CLOSE, "The body should be turned about the exit's right, "
                + "turned about " + tilt.axis());
        for (int tick = 1; tick <= EASE_TICKS; tick++) {
            assertTrue(tilt.degreesAt(tick) < tilt.degreesAt(tick - 1), "The tilt should ease each tick, at tick "
                    + tick + " reads " + tilt.degreesAt(tick));
        }
        assertEquals(0.0, tilt.degreesAt(EASE_TICKS), CLOSE, "The tilt should be gone after about half a second");
    }

    /**
     * The swing upright reads as a swing, not a flicker: three ticks in, most
     * of the turn is still to come, and halfway through the half second half
     * of it is, as the operator found the turn gone almost at once.
     */
    @Test
    void theSwingUprightIsSpreadOverTheWholeHalfSecond() {
        ExitTilt tilt = ExitTilt.between(FLOOR, NORTH_WALL);
        assertTrue(tilt.degreesAt(THREE_TICKS) >= QUARTER * MOST_STILL_TO_COME, "Three ticks in, most of the turn "
                + "should be still to come, reads " + tilt.degreesAt(THREE_TICKS));
        assertEquals(QUARTER / 2, tilt.degreesAt(EASE_TICKS / 2.0), 1.0, "Halfway through, half the turn should be "
                + "still to come");
    }

    private static final int THREE_TICKS = 3;
    private static final double MOST_STILL_TO_COME = 0.7;

    @Test
    void aWallGateIntoAWallGateFacingItTiltsNothing() {
        assertEquals(0.0, ExitTilt.between(NORTH_WALL, SOUTH_WALL).degreesAt(0), CLOSE);
    }

    @Test
    void aFloorGateIntoACeilingGateTiltsNothing() {
        assertEquals(0.0, ExitTilt.between(FLOOR, CEILING).degreesAt(0), CLOSE);
    }

    @Test
    void aFloorGateIntoAFloorGateTiltsAHalfTurn() {
        assertEquals(HALF_TURN, ExitTilt.between(FLOOR, OTHER_FLOOR).degreesAt(0), CLOSE);
    }
}
