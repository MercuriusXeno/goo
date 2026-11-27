package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.ThrowRange;
import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import com.mercuriusxeno.goo.item.ReagentScanner;
import com.mercuriusxeno.goo.throwing.AimReach;
import com.mercuriusxeno.goo.throwing.GateArc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A point an ability aims beyond the throw range is capped onto the range
 * along the throw's line rather than refused, so aiming at the sky throws to
 * the range's end (decision aim-point-follows-the-cursor).
 */
class GooThrowHandlerTest {

    private static final double TOLERANCE = 1e-9;
    private static final double RANGE = 16;
    private static final Vec3 EYE = new Vec3(10, 65.62, -4);
    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    /** A wall portal on a north face, the plane z 0. */
    private static final Gate NEAR = new Gate(OVERWORLD, new AABB(new BlockPos(0, 64, 0)).minmax(
            new AABB(new BlockPos(0, 65, 0))), Direction.NORTH, Direction.UP.getUnitVec3());
    /** Its partner on a south face 480 blocks east, the plane z 1. */
    private static final Gate FAR = new Gate(OVERWORLD, new AABB(new BlockPos(480, 64, 0)).minmax(
            new AABB(new BlockPos(480, 65, 0))), Direction.SOUTH, Direction.UP.getUnitVec3());

    /**
     * A throw through a linked gate measures its two legs, not the straight
     * line between its ends (decision aim-and-shots-carry-through-linked-gates).
     */
    @Test
    void aThrowThroughAWallPairMeasuresHandToFacePlusPartnerToTarget() {
        Vec3 hand = new Vec3(0.5, 65.0, -4.0);
        Vec3 face = NEAR.center();
        Vec3 target = new Vec3(480.5, 64.5, 5.0);
        GateArc.Hop hop = GooThrowHandler.admittedHop(List.of(new GatePassage.Pair(NEAR, FAR)), NEAR, face, hand, RANGE)
                .orElseThrow(() -> new AssertionError("A throw through a linked gate should be admitted"));
        assertEquals(hand.distanceTo(face) + hop.partnerPoint().distanceTo(target),
                GooThrowHandler.throughDistance(hand, hop, target), TOLERANCE,
                "The throw should measure the hand to the face plus the partner to the target");
    }

    @Test
    void aThrowNamingAGateInNoPairIsRefused() {
        Vec3 hand = new Vec3(0.5, 65.0, -4.0);
        assertTrue(GooThrowHandler.admittedHop(List.of(), NEAR, NEAR.center(), hand, RANGE).isEmpty(),
                "A throw naming a gate that stands in no pair should be refused");
    }

    @Test
    void aPointBeyondRangeIsCappedOntoTheRangeAlongItsLine() {
        Vec3 far = EYE.add(30, 90, -60);

        Vec3 capped = GooThrowHandler.capToRange(EYE, far, RANGE);

        assertEquals(RANGE, capped.distanceTo(EYE), TOLERANCE);
        Vec3 along = far.subtract(EYE).normalize();
        assertEquals(1, capped.subtract(EYE).normalize().dot(along), TOLERANCE);
    }

    @Test
    void aPointWithinRangePassesUnchanged() {
        Vec3 near = EYE.add(12, -3, 10);

        assertSame(near, GooThrowHandler.capToRange(EYE, near, RANGE));
    }

    @Test
    void aPointOnTheRangePassesUnchanged() {
        Vec3 edge = EYE.add(0, 0, RANGE);

        assertSame(edge, GooThrowHandler.capToRange(EYE, edge, RANGE));
    }

    /**
     * A Mitosis throw aimed past its 16 blocks is thrown at the point 16
     * blocks out along the same line.
     * decision each-ability-names-its-range-and-the-shared-throw-range-retires
     */
    @Test
    void aMitosisThrowAimedFortyBlocksOutIsThrownSixteenBlocksOutAlongItsLine() {
        Vec3 far = out(40);

        Vec3 thrown = GooThrowHandler.pointThrownAt(EYE, far, rangeOf("weird_mitosis"));

        assertEquals(16, thrown.distanceTo(EYE), TOLERANCE);
        assertEquals(1, thrown.subtract(EYE).normalize().dot(far.subtract(EYE).normalize()), TOLERANCE);
    }

    @Test
    void aMitosisThrowAimedTenBlocksOutIsThrownAtTheAimedPoint() {
        Vec3 near = out(10);

        assertSame(near, GooThrowHandler.pointThrownAt(EYE, near, rangeOf("weird_mitosis")));
    }

    /**
     * Each server range check on a thrown or cast ability reads that
     * ability's own range.
     * decision each-ability-names-its-range-and-the-shared-throw-range-retires
     * decision abilities-leaving-the-shared-range-start-at-sixteen-blocks
     * decision zap-starts-at-thirty-two-blocks
     */
    @Nested
    class EachCheckReadsTheAbilitysRange {

        @Test
        void anIgnitePointSeventeenBlocksOutCapsAtSixteen() {
            assertEquals(16, GooThrowHandler.pointThrownAt(EYE, out(17), rangeOf("blaze_ignite")).distanceTo(EYE),
                    TOLERANCE);
        }

        @Test
        void anIgnitePointFifteenBlocksOutStands() {
            Vec3 near = out(15);

            assertSame(near, GooThrowHandler.pointThrownAt(EYE, near, rangeOf("blaze_ignite")));
        }

        @Test
        void aZapPointFortyBlocksOutCapsAtThirtyTwo() {
            assertEquals(32, GooThrowHandler.pointThrownAt(EYE, out(40), rangeOf("pulse_zap")).distanceTo(EYE),
                    TOLERANCE);
        }

        /**
         * decision meteo-range-is-thirty-two-blocks
         */
        @Test
        void aMeteoReleaseThirtyOneBlocksOutStandsAndThirtyFourIsRefused() {
            double meteoRange = rangeOf("astral_meteo");

            assertEquals(32, meteoRange);
            assertTrue(takenAt(31, "astral_meteo"), "Meteo should stand at 31 blocks");
            assertFalse(takenAt(34, "astral_meteo"), "Meteo should refuse 34 blocks");
        }

        @Test
        void aBioFlightAtAMobEighteenBlocksOutIsRefusedAndFifteenStands() {
            assertReleasedWithinSixteen("leaf_bio");
        }

        @Test
        void aHoleCastEighteenBlocksOutIsRefusedAndFifteenStands() {
            assertReleasedWithinSixteen("void_hole");
        }

        @Test
        void aShulkSpewEighteenBlocksOutIsRefusedAndFifteenStands() {
            assertReleasedWithinSixteen("typhoon_shulk");
        }

        private void assertReleasedWithinSixteen(String name) {
            assertFalse(takenAt(18, name), name + " should refuse 18 blocks");
            assertTrue(takenAt(15, name), name + " should stand at 15 blocks");
        }

        private boolean takenAt(double blocks, String name) {
            Vec3 aimed = out(blocks);
            return GooThrowHandler.takesAim(EYE, new AABB(BlockPos.containing(aimed)), aimed, rangeOf(name));
        }
    }

    /**
     * Every release the server range-gates measures the caster's eye to the
     * aimed point, so a face or a mob lit within range is taken where the
     * block's center or the mob's feet stand past that range of the caster's feet.
     * decision diagnose-and-correct-the-indicator-disagreeing-with-the-cast
     */
    @Nested
    class EachReleaseMeasuresTheEyeToTheAimedPoint {

        private static final double EYE_HEIGHT = 1.62;

        @Test
        void aMeteoCastAtTheNearEdgeOfATopFaceWithinRangeOfTheEyeIsTaken() {
            double range = rangeOf("astral_meteo");
            BlockPos ground = new BlockPos(40, 64, 0);
            Vec3 struck = new Vec3(40, 65, 0.5);
            double across = Math.sqrt(square(range - 0.25) - square(EYE_HEIGHT));
            Vec3 eye = struck.add(-across, EYE_HEIGHT, 0);

            assertTrue(feetOf(eye).distanceTo(Vec3.atCenterOf(ground)) > range,
                    "The ground's center should stand past range of the caster's feet");
            assertTrue(GooThrowHandler.takesAim(eye, new AABB(ground), struck, range),
                    "A Meteo cast at a face within range of the eye should be taken");
        }

        @Test
        void aBloomFlightAtAWallFaceWithinRangeOfTheEyeIsTaken() {
            double range = rangeOf("leaf_bloom");
            BlockPos wall = new BlockPos(40, 66, 0);
            Vec3 struck = new Vec3(40, 66.62, 0.5);
            Vec3 eye = struck.add(-(range - 0.25), 0, 0);

            assertTrue(feetOf(eye).distanceTo(Vec3.atCenterOf(wall)) > range,
                    "The wall's center should stand past range of the caster's feet");
            assertTrue(GooThrowHandler.takesAim(eye, new AABB(wall), struck, range),
                    "A Bloom flight at a face within range of the eye should be taken");
        }

        @Test
        void aBioFlightAtAMobsNearSideWithinRangeOfTheEyeIsTaken() {
            double range = rangeOf("leaf_bio");
            AABB zombie = zombieStandingAt(40, 65);
            Vec3 struck = new Vec3(zombie.minX, 66.62, 0.5);
            Vec3 eye = struck.add(-(range - 0.1), 0, 0);

            assertTrue(feetOf(eye).distanceTo(zombie.getBottomCenter()) > range,
                    "The mob's feet should stand past range of the caster's feet");
            assertTrue(GooThrowHandler.takesAim(eye, zombie, struck, range),
                    "A Bio flight at a mob within range of the eye should be taken");
        }

        private static Vec3 feetOf(Vec3 eye) {
            return eye.subtract(0, EYE_HEIGHT, 0);
        }
    }

    /**
     * The client's indicator and the server's range gate read one table of
     * eye, aimed point and range alike, for a block face and for a mob.
     * decision diagnose-and-correct-the-indicator-disagreeing-with-the-cast
     */
    @Nested
    class TheIndicatorAndTheGateReadOneMeasure {

        private static final double FACE_X = 40;

        @ParameterizedTest
        @CsvSource({"-0.5, true", "0, true", "2, false"})
        void aWallFaceIsLitAndTakenAlike(double pastRange, boolean expected) {
            Vec3 struck = new Vec3(FACE_X, 66.62, 0.5);
            Vec3 eye = struck.add(-(RANGE + pastRange), 0, 0);

            assertEquals(expected, AimReach.lights(eye, struck, RANGE), "the indicator");
            assertEquals(expected, GooThrowHandler.takesAim(eye, new AABB(BlockPos.containing(FACE_X, 66, 0)), struck,
                    RANGE), "the server's range gate");
        }

        @ParameterizedTest
        @CsvSource({"-0.5, true", "0, true", "2, false"})
        void aMobIsLitAndTakenAlike(double pastRange, boolean expected) {
            AABB zombie = zombieStandingAt(FACE_X + 0.3, 65);
            Vec3 struck = new Vec3(zombie.minX, 66.62, 0.5);
            Vec3 eye = struck.add(-(RANGE + pastRange), 0, 0);

            assertEquals(expected, AimReach.lights(eye, struck, RANGE), "the indicator");
            assertEquals(expected, GooThrowHandler.takesAim(eye, zombie, struck, RANGE), "the server's range gate");
        }
    }

    private static AABB zombieStandingAt(double x, double feetY) {
        return new AABB(x - 0.3, feetY, 0.2, x + 0.3, feetY + 1.95, 0.8);
    }

    private static double square(double value) {
        return value * value;
    }

    private static Vec3 out(double blocks) {
        return EYE.add(new Vec3(1, 2, -3).normalize().scale(blocks));
    }

    private static double rangeOf(String name) {
        return ThrowRange.of(AbilityJson.decode(name));
    }

    @Test
    void aMobAbilityNamingNoEntityIsRefusedAndOneNamingAnEntityIsNot() {
        assertTrue(GooThrowHandler.aimsNoMob(AbilityBadge.MOB, false));
        assertFalse(GooThrowHandler.aimsNoMob(AbilityBadge.MOB, true));
        assertFalse(GooThrowHandler.aimsNoMob(AbilityBadge.WORLD, false));
    }

    @Test
    void charmThrownByAPlayerHoldingNothingPassesTheReagentCheck() {
        // ability-json-names-its-reagent
        List<Identifier> charmReagents = AbilityJson.decode("hex_charm").consumes();

        assertTrue(ReagentScanner.holdsEvery(charmReagents, reagent -> false));
    }

    @Test
    void enchantThrownByAPlayerHoldingNoBookFailsTheReagentCheck() {
        List<Identifier> enchantReagents = AbilityJson.decode("hex_enchant").consumes();

        assertFalse(ReagentScanner.holdsEvery(enchantReagents, reagent -> false));
    }
}
