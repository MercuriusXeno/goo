package com.mercuriusxeno.goo.ability.quantum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The camera transform through a linked pair: a point in front of portal A
 * maps to the matching point behind portal B, so a camera looking into A sees
 * out of B as though B's side stood right there; what is on the viewer's
 * right stays on their right, and height above a wall portal holds
 * (decision portals-link-by-looking-through-portal-vision).
 */
class PortalTransformTest {

    private static final double TOLERANCE = 1e-9;
    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    /** A, two tall on the south faces of a block at the origin and the one above: its face's middle is (0.5, 65, 1). */
    private static final Portal SOUTH_WALL = portal(new BlockPos(0, 64, 0), Direction.SOUTH, Direction.UP);
    /** B, two tall on the east faces of a block ten east and the one above: its middle is (11, 65, 0.5). */
    private static final Portal EAST_WALL = portal(new BlockPos(10, 64, 0), Direction.EAST, Direction.UP);
    /** C, flat on the top faces of a block and the one north of it: its face's middle is (20.5, 65, 0). */
    private static final Portal FLOOR = portal(new BlockPos(20, 64, 0), Direction.UP, Direction.NORTH);

    private static Portal portal(BlockPos block, Direction face, Direction along) {
        return new Portal(GlobalPos.of(OVERWORLD, block), face, along);
    }

    private static void assertVec(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x(), actual.x(), TOLERANCE, "x of " + actual);
        assertEquals(expected.y(), actual.y(), TOLERANCE, "y of " + actual);
        assertEquals(expected.z(), actual.z(), TOLERANCE, "z of " + actual);
    }

    @Test
    void pointInFrontOfTheEntryMapsBehindTheExit() {
        PortalTransform through = PortalTransform.between(SOUTH_WALL, EAST_WALL);
        assertVec(new Vec3(9.0, 64.5, 0.5), through.point(new Vec3(0.5, 64.5, 3.0)));
    }

    @Test
    void theViewersRightStaysOnTheirRight() {
        PortalTransform through = PortalTransform.between(SOUTH_WALL, EAST_WALL);
        assertVec(new Vec3(9.0, 64.5, 1.5), through.point(new Vec3(1.5, 64.5, 3.0)));
    }

    @Test
    void heightAboveAWallPortalHolds() {
        PortalTransform through = PortalTransform.between(SOUTH_WALL, EAST_WALL);
        assertVec(new Vec3(9.0, 66.0, 0.5), through.point(new Vec3(0.5, 66.0, 3.0)));
    }

    @Test
    void lookingIntoTheEntryLooksOutOfTheExit() {
        PortalTransform through = PortalTransform.between(SOUTH_WALL, EAST_WALL);
        assertVec(new Vec3(1, 0, 0), through.direction(new Vec3(0, 0, -1)));
    }

    @Test
    void pointInFrontOfAWallMapsBelowAFloor() {
        PortalTransform through = PortalTransform.between(SOUTH_WALL, FLOOR);
        assertVec(new Vec3(20.5, 63.0, 0.5), through.point(new Vec3(0.5, 64.5, 3.0)));
    }

    @Test
    void theFaceMiddleMapsOntoTheExitFaceMiddle() {
        PortalTransform through = PortalTransform.between(SOUTH_WALL, EAST_WALL);
        assertVec(EAST_WALL.faceCenter(), through.point(SOUTH_WALL.faceCenter()));
    }
}
