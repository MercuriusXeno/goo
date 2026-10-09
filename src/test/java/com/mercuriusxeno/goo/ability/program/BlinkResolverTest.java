package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers where a blink lands over a world of solid unit cubes: free aim a
 * fixed range along the look, through a thin wall into free room, shorted in
 * front of a thick wall, never into the ground, sliding on a pinned face,
 * and refused when nothing fits (decision blink-lands-safely-costed-by-distance).
 */
class BlinkResolverTest {

    private static final BlinkBody PLAYER = new BlinkBody(0.6, 1.8, 1.62);
    private static final double RANGE = 8;
    private static final Vec3 FEET = new Vec3(0.5, 64, 0.5);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final double NEAR = 0.01;

    /** A world of full solid cubes at the positions it holds. */
    private static final class CubeSpace implements BlinkSpace {

        private static final double MARCH_STEP = 0.001;
        private final Set<BlockPos> solids = new HashSet<>();

        CubeSpace fill(int x0, int y0, int z0, int x1, int y1, int z1) {
            for (BlockPos pos : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
                solids.add(pos.immutable());
            }
            return this;
        }

        CubeSpace clear(int x, int y, int z) {
            solids.remove(new BlockPos(x, y, z));
            return this;
        }

        @Override
        public boolean fits(AABB box) {
            return solids.stream().noneMatch(pos -> new AABB(pos).intersects(box));
        }

        @Override
        public Optional<FaceHit> firstFace(Vec3 from, Vec3 to) {
            Vec3 line = to.subtract(from);
            double length = line.length();
            BlockPos previous = BlockPos.containing(from);
            for (double along = 0; along <= length; along += MARCH_STEP) {
                Vec3 point = from.add(line.scale(along / length));
                BlockPos at = BlockPos.containing(point);
                if (solids.contains(at)) {
                    BlockPos back = previous.subtract(at);
                    Direction face = Arrays.stream(Direction.values())
                            .filter(side -> side.getUnitVec3i().equals(back)).findFirst().orElseThrow();
                    return Optional.of(new FaceHit(point, at, face));
                }
                previous = at;
            }
            return Optional.empty();
        }
    }

    private static Optional<BlinkLanding> freeAim(CubeSpace space, Vec3 look) {
        return BlinkResolver.resolve(space, FEET, look, RANGE, PLAYER, Optional.empty());
    }

    @Nested
    class FreeAim {

        @Test
        void openAirLandsTheRangeAlongTheLook() {
            BlinkLanding landing = freeAim(new CubeSpace(), EAST).orElseThrow();

            assertEquals(FEET.add(RANGE, 0, 0), landing.feet());
            assertEquals(RANGE, landing.distance(), NEAR);
            assertFalse(landing.throughWall());
        }

        @Test
        void aOneBlockWallIsPassedIntoTheFreeRoomBehind() {
            CubeSpace space = new CubeSpace().fill(3, 64, -2, 3, 67, 2);

            BlinkLanding landing = freeAim(space, EAST).orElseThrow();

            assertEquals(FEET.add(RANGE, 0, 0), landing.feet());
            assertTrue(landing.throughWall());
        }

        @Test
        void aSpotInsideAThickWallShortsInFrontOfIt() {
            CubeSpace space = new CubeSpace().fill(3, 64, -2, 12, 67, 2);

            BlinkLanding landing = freeAim(space, EAST).orElseThrow();

            assertTrue(landing.feet().x() < 3 && landing.feet().x() > 2, "landed at " + landing.feet());
            assertEquals(FEET.y(), landing.feet().y(), NEAR);
            assertTrue(space.fits(PLAYER.boxAt(landing.feet())));
            assertFalse(landing.throughWall());
        }

        @Test
        void aLookIntoTheGroundStandsOnTheGround() {
            CubeSpace space = new CubeSpace().fill(-2, 55, -2, 12, 63, 2);

            BlinkLanding landing = freeAim(space, new Vec3(0.8, -0.6, 0)).orElseThrow();

            assertEquals(64, landing.feet().y(), NEAR);
            assertTrue(landing.feet().x() > FEET.x() + 1, "landed at " + landing.feet());
            assertTrue(space.fits(PLAYER.boxAt(landing.feet())));
        }

        @Test
        void aBlinkerWalledInOnEverySideIsRefused() {
            CubeSpace space = new CubeSpace().fill(-10, 54, -10, 10, 75, 10).clear(0, 64, 0).clear(0, 65, 0);

            assertTrue(freeAim(space, EAST).isEmpty());
        }
    }

    @Nested
    class PinnedFace {

        @Test
        void aPinnedTopFaceLandsOnItWhereTheLookCrossesIt() {
            CubeSpace space = new CubeSpace().fill(5, 64, 0, 5, 66, 0);
            Vec3 eye = FEET.add(0, PLAYER.eyeHeight(), 0);
            Vec3 look = new Vec3(5.5, 67, 0.5).subtract(eye).normalize();

            BlinkLanding landing = BlinkResolver.resolve(space, FEET, look, RANGE, PLAYER,
                    Optional.of(new ChannelAim.FacePlane(new BlockPos(5, 66, 0), Direction.UP))).orElseThrow();

            assertEquals(5.5, landing.feet().x(), NEAR);
            assertEquals(67, landing.feet().y(), NEAR);
            assertEquals(0.5, landing.feet().z(), NEAR);
        }

        @Test
        void aPinnedSideFaceStandsTheBlinkerAgainstIt() {
            CubeSpace space = new CubeSpace().fill(6, 64, -5, 6, 70, 5);

            BlinkLanding landing = BlinkResolver.resolve(space, FEET, EAST, RANGE, PLAYER,
                    Optional.of(new ChannelAim.FacePlane(new BlockPos(6, 65, 0), Direction.WEST))).orElseThrow();

            assertEquals(6 - PLAYER.width() / 2, landing.feet().x(), NEAR);
            assertEquals(FEET.y(), landing.feet().y(), NEAR);
            assertTrue(space.fits(PLAYER.boxAt(landing.feet())));
        }

        @Test
        void aPressPinsTheFirstFaceTheLookCrossesInRange() {
            CubeSpace space = new CubeSpace().fill(4, 60, -2, 4, 70, 2);

            Optional<ChannelAim.FacePlane> pin = BlinkResolver.pinAt(space, FEET.add(0, PLAYER.eyeHeight(), 0),
                    EAST, RANGE);

            assertEquals(Optional.of(new ChannelAim.FacePlane(new BlockPos(4, 65, 0), Direction.WEST)), pin);
        }
    }
}
