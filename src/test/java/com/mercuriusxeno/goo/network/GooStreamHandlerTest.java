package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.program.CalcifyStep;
import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.PetrifyStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mercuriusxeno.goo.throwing.StreamCone;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A stream's program splits by pass: the steps needing the channel run on
 * the player over the cone's blocks and the rest on each entity in the cone
 * (decisions bore-vortex-with-a-worldspace-shake,
 * petrify-stone-encasement-and-calcify-map); a mob-first stream's swarm
 * goes for the mob lying nearest its aim, not the one nearest the player,
 * and seeks the living as wide and as far as its delivery names
 * (decision decay-gnats-degrade-each-block-once).
 */
class GooStreamHandlerTest {

    private static final Step PETRIFY = new PetrifyStep(Expr.literal(2));
    private static final Step CALCIFY = new CalcifyStep(Identifier.fromNamespaceAndPath("goo", "calcify"), 30);

    @Test
    void channelStepsRunInTheBlockPassAndTheRestInTheEntityPass() {
        List<Step> program = List.of(PETRIFY, CALCIFY);
        assertEquals(List.of(CALCIFY), GooStreamHandler.channelSteps(program, true));
        assertEquals(List.of(PETRIFY), GooStreamHandler.channelSteps(program, false));
    }

    @Nested
    class MoteLaunch {

        private static final Vec3 HEADING = new Vec3(0.6, 0.0, 0.8);
        private static final double SPEED = 0.5;
        private static final double DELTA = 1e-12;

        @Test
        void aMoteLaunchesAlongItsHeadingPlusThePlayersTravel() {
            Vec3 travel = new Vec3(0.2, -0.1, 0.3);

            Vec3 launch = GooStreamHandler.moteLaunch(HEADING, SPEED, travel);

            assertEquals(0.6 * SPEED + 0.2, launch.x, DELTA);
            assertEquals(-0.1, launch.y, DELTA);
            assertEquals(0.8 * SPEED + 0.3, launch.z, DELTA);
        }

        @Test
        void aMoteFromAStandingPlayerLaunchesAlongItsHeadingAlone() {
            Vec3 launch = GooStreamHandler.moteLaunch(HEADING, SPEED, Vec3.ZERO);

            assertEquals(HEADING.scale(SPEED), launch);
        }
    }

    /**
     * A Goop or Slick spray drip launches toward the end of the coat ray its
     * shares name, a tenth of the gap each tick, plus its caster's travel; any
     * other stream's mote keeps the crosshair-distance launch.
     * goop-and-slick-spray-follows-the-coat
     * glove-stream-motes-carry-the-players-travel-velocity
     */
    @Nested
    class SprayLaunch {

        private static final Identifier DRIP = Identifier.fromNamespaceAndPath("goo", "goop_spray_drip");
        private static final Identifier SPORE = Identifier.fromNamespaceAndPath("goo", "spore");
        private static final Vec3 GLOVE = new Vec3(0.3, 1.3, 0.2);
        private static final Vec3 EYE = new Vec3(0, 1.62, 0);
        private static final double CONE = 16;
        private static final double RANGE = 8;
        private static final CoatRay.ConeShare SHARE = new CoatRay.ConeShare(0.5, 0.25);
        /** A launch at a tenth of the gap per tick aims ten launches off. */
        private static final double COAST = 10;
        private static final double TOLERANCE = 1e-9;
        /** The floor's top face at y 0, the only block a ray meets. */
        private static final CoatRay.BlockClip FLOOR = (from, to) -> from.y > 0 && to.y <= 0
                ? Optional.of(from.add(to.subtract(from).scale(from.y / (from.y - to.y))))
                : Optional.empty();
        private static final CoatRay.BlockClip OPEN_AIR = (from, to) -> Optional.empty();

        private static final Vec3 TRAVEL = new Vec3(0.2, -0.1, 0.3);

        private StreamMotesPayload coatMotes(Vec3 look, Vec3 travel) {
            return new StreamMotesPayload(1, GLOVE, look, DRIP, CONE, 0, travel,
                    Optional.of(new CoatSpray(EYE, look, RANGE)));
        }

        private void assertNear(Vec3 expected, Vec3 actual) {
            assertEquals(expected.x, actual.x, TOLERANCE, "x");
            assertEquals(expected.y, actual.y, TOLERANCE, "y");
            assertEquals(expected.z, actual.z, TOLERANCE, "z");
        }

        @Test
        void aCoatMoteRestsWhereItsRayMeetsTheFloorShortOfTheRange() {
            Vec3 look = new Vec3(0, -0.5, 1).normalize();
            Vec3 direction = StreamCone.launchDirection(look, CONE, SHARE.tilt(), SHARE.turn());
            Vec3 hit = FLOOR.firstHit(EYE, EYE.add(direction.scale(RANGE))).orElseThrow();
            Vec3 end = hit.add(direction.scale(0.01));
            assertTrue(EYE.distanceTo(end) < RANGE, "the floor should stand short of the range");

            Vec3 launch = GooStreamHandler.moteLaunch(coatMotes(look, TRAVEL), SHARE, FLOOR);

            assertNear(end, GLOVE.add(launch.subtract(TRAVEL).scale(COAST)));
        }

        @Test
        void aCoatMoteMeetingNoBlockRestsAtTheRange() {
            Vec3 look = new Vec3(0, 0.3, 1).normalize();
            Vec3 direction = StreamCone.launchDirection(look, CONE, SHARE.tilt(), SHARE.turn());

            Vec3 launch = GooStreamHandler.moteLaunch(coatMotes(look, TRAVEL), SHARE, FLOOR);

            assertNear(EYE.add(direction.scale(RANGE)), GLOVE.add(launch.subtract(TRAVEL).scale(COAST)));
        }

        @Test
        void aCoatDripsLaunchAddsItsCastersTravel() {
            Vec3 look = new Vec3(0, -0.5, 1).normalize();

            Vec3 standing = GooStreamHandler.moteLaunch(coatMotes(look, Vec3.ZERO), SHARE, FLOOR);
            Vec3 travelling = GooStreamHandler.moteLaunch(coatMotes(look, TRAVEL), SHARE, FLOOR);

            assertNear(TRAVEL, travelling.subtract(standing));
        }

        @Test
        void aMoteOffTheCoatLaunchesAtTheCrosshairShortOfTheRange() {
            assertCrosshairLaunch(3.5, 3.5);
        }

        @Test
        void aMoteOffTheCoatLaunchesAtTheRangeWhenTheCrosshairLiesPastIt() {
            assertCrosshairLaunch(12, RANGE);
        }

        private void assertCrosshairLaunch(double crosshairDistance, double restsAt) {
            Vec3 axis = new Vec3(0.6, 0, 0.8);
            Vec3 travel = new Vec3(0.2, -0.1, 0.3);
            StreamMotesPayload spores = new StreamMotesPayload(1, GLOVE, axis, SPORE, 0,
                    GooStreamHandler.crosshairSpeed(RANGE, crosshairDistance), travel, Optional.empty());

            Vec3 launch = GooStreamHandler.moteLaunch(spores, SHARE, OPEN_AIR);

            assertNear(axis.scale(restsAt * 0.1).add(travel), launch);
        }
    }

    @Nested
    class SwarmPick {

        private static final Vec3 EYE = Vec3.ZERO;
        private static final Vec3 LOOK = new Vec3(1, 0, 0);
        private static final double HALF_A_MOB = 0.3;

        private Mob mobAt(Vec3 middle) {
            Mob mob = mock(Mob.class);
            when(mob.getBoundingBox()).thenReturn(new AABB(middle, middle).inflate(HALF_A_MOB));
            return mob;
        }

        @Test
        void theSwarmGoesForTheMobNearestItsAimOverTheMobNearestThePlayer() {
            // two blocks out and 40 degrees off the aim
            Mob near = mobAt(new Vec3(2 * Math.cos(Math.toRadians(40)), 0, 2 * Math.sin(Math.toRadians(40))));
            // six blocks out and 5 degrees off the aim
            Mob aligned = mobAt(new Vec3(6 * Math.cos(Math.toRadians(5)), 0, 6 * Math.sin(Math.toRadians(5))));
            List<LivingEntity> inTheCone = List.of(near, aligned);

            assertEquals(List.of(aligned), GooStreamHandler.mostAlignedMob(inTheCone, EYE, LOOK));
        }
    }

    /**
     * Decay's swarm seeks a mob within its delivery's 45 degrees either side of the aim and 8 blocks out,
     * past the 6 it paints blocks within (decision decay-gnats-degrade-each-block-once).
     */
    @Nested
    class DecaysSeek {

        private static final Vec3 EYE = Vec3.ZERO;
        private static final Vec3 LOOK = new Vec3(1, 0, 0);
        private static final double HALF_A_MOB = 0.3;
        private final Delivery decay = AbilityJson.decode("nether_decay").delivery();

        private Mob mobAt(Vec3 middle) {
            Mob mob = mock(Mob.class);
            when(mob.getBoundingBox()).thenReturn(new AABB(middle, middle).inflate(HALF_A_MOB));
            return mob;
        }

        @Test
        void theSwarmReachesAMobAboutThirtyFourDegreesOffTheAim() {
            assertTrue(GooStreamHandler.inCone(EYE, LOOK, decay, mobAt(new Vec3(3, 0, 2))));
        }

        @Test
        void theSwarmReachesAMobSevenBlocksOutPastWhereItPaints() {
            assertTrue(GooStreamHandler.inCone(EYE, LOOK, decay, mobAt(new Vec3(7, 0, 0))));
        }
    }
}
