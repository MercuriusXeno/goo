package com.mercuriusxeno.goo.client.ability;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ScryReveal: which faces open onto air, which blocks the front crosses
 * between two radii, and how the sweep flashes and fades.
 * decision scry-sphere-reveals-faces-and-glistens-mobs
 */
class ScryRevealTest {

    private static final BlockPos STONE = new BlockPos(4, 60, -2);

    @Nested
    class ExposedFaces {

        @Test
        void aBlockShowsOnlyTheSidesOpenToAir() {
            Set<BlockPos> air = Set.of(STONE.above(), STONE.east());
            List<Direction> faces = ScryReveal.exposedFaces(air::contains, STONE);
            assertEquals(EnumSet.of(Direction.UP, Direction.EAST), EnumSet.copyOf(faces));
        }

        @Test
        void aBuriedBlockShowsNoFace() {
            assertTrue(ScryReveal.exposedFaces(pos -> false, STONE).isEmpty());
        }

        @Test
        void airItselfShowsNoFace() {
            Predicate<BlockPos> allAir = pos -> true;
            assertTrue(ScryReveal.exposedFaces(allAir, STONE).isEmpty());
        }
    }

    @Nested
    class Shell {

        private static final Vec3 CENTER = new Vec3(0.3, 64.9, -7.6);

        @Test
        void holdsExactlyTheBlocksPastTheInnerRadiusAndWithinTheOuter() {
            assertEquals(bruteForce(CENTER, 5.5, 6.5), new HashSet<>(ScryReveal.shell(CENTER, 5.5, 6.5)));
        }

        @Test
        void theFirstTickHoldsTheWholeBall() {
            assertEquals(bruteForce(CENTER, 0, 3), new HashSet<>(ScryReveal.shell(CENTER, 0, 3)));
        }

        @Test
        void listsNoBlockTwice() {
            List<BlockPos> shell = ScryReveal.shell(CENTER, 9, 10);
            assertEquals(new HashSet<>(shell).size(), shell.size());
        }

        @Test
        void aFrontThatStoppedCrossesNothing() {
            assertTrue(ScryReveal.shell(CENTER, 48, 48).isEmpty());
        }

        private Set<BlockPos> bruteForce(Vec3 center, double inner, double outer) {
            Set<BlockPos> kept = new HashSet<>();
            int reach = (int) Math.ceil(outer) + 1;
            BlockPos middle = BlockPos.containing(center);
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dy = -reach; dy <= reach; dy++) {
                    for (int dz = -reach; dz <= reach; dz++) {
                        BlockPos pos = middle.offset(dx, dy, dz);
                        double d = Math.sqrt(center.distanceToSqr(Vec3.atCenterOf(pos)));
                        if (d > inner && d <= outer) {
                            kept.add(pos);
                        }
                    }
                }
            }
            return kept;
        }
    }

    @Nested
    class Timing {

        @Test
        void showsInFullWhileHeld() {
            assertEquals(1f, ScryReveal.fade(ScryReveal.RELEASE_GRACE_TICKS), 0f);
        }

        @Test
        void fadesOverASecondOnceLetGo() {
            assertEquals(0.5f, ScryReveal.fade(ScryReveal.RELEASE_GRACE_TICKS + ScryReveal.FADE_TICKS / 2), 1e-6f);
            assertEquals(0f, ScryReveal.fade(ScryReveal.RELEASE_GRACE_TICKS + ScryReveal.FADE_TICKS), 0f);
        }

        @Test
        void aPingShowsFullyWithinItsReachThenFadesPastIt() {
            assertEquals(1f, ScryReveal.pingFade(96, 96, 20), 0f);
            assertEquals(0.5f, ScryReveal.pingFade(106, 96, 20), 1e-6f);
            assertEquals(0f, ScryReveal.pingFade(116, 96, 20), 0f);
        }

        @Test
        void aNewFaceFlashesThenSettles() {
            assertEquals(1f, ScryReveal.flash(0), 0f);
            assertEquals(0.5f, ScryReveal.flash(ScryReveal.FLASH_TICKS / 2), 1e-6f);
            assertEquals(0f, ScryReveal.flash(ScryReveal.FLASH_TICKS), 0f);
        }
    }
}
