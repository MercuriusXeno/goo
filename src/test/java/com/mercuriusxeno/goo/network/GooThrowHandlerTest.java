package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.ThrowRange;
import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import com.mercuriusxeno.goo.client.ability.BlobRest;
import com.mercuriusxeno.goo.item.ReagentScanner;
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

        @Test
        void aBioFlightAtAMobSeventeenBlocksOutIsRefusedAndFifteenStands() {
            assertReleasedWithinSixteen("leaf_bio");
        }

        @Test
        void aHoleCastSeventeenBlocksOutIsRefusedAndFifteenStands() {
            assertReleasedWithinSixteen("void_hole");
        }

        @Test
        void aShulkSpewSeventeenBlocksOutIsRefusedAndFifteenStands() {
            assertReleasedWithinSixteen("typhoon_shulk");
        }

        private void assertReleasedWithinSixteen(String name) {
            assertFalse(GooThrowHandler.withinRange(17 * 17, rangeOf(name)), name + " should refuse 17 blocks");
            assertTrue(GooThrowHandler.withinRange(15 * 15, rangeOf(name)), name + " should stand at 15 blocks");
        }
    }

    /**
     * An orb leaving the lifted blob off the eye rolls at what the look
     * intersects (decision ability-goes-where-the-look-intersects).
     */
    @Nested
    class OrbHeading {

        private static final double ON_THE_LINE = 0.05;
        private static final Vec3 LOOK_NORTH = new Vec3(0, 0, -1);
        private static final Vec3 HALF_A_BLOCK_RIGHT = EYE.add(0.5, 0, 0);
        private static final double WALL_OUT = 10;
        private static final double REST_WALL_OUT = 8;
        private static final float NORTH_YAW = 180f;

        @Test
        void anOrbStartedBesideTheEyeHeadsAtTheBlockTheLookHits() {
            Vec3 hit = EYE.add(LOOK_NORTH.scale(WALL_OUT));
            Vec3 heading = GooThrowHandler.rollHeading(HALF_A_BLOCK_RIGHT, EYE, LOOK_NORTH, rangeOf("frost_orb"),
                    OrbHeading::wallTenBlocksNorth);
            assertTrue(offLine(hit, HALF_A_BLOCK_RIGHT, heading) < ON_THE_LINE,
                    "The orb's path should pass within a twentieth of a block of the look's hit point, passed "
                            + offLine(hit, HALF_A_BLOCK_RIGHT, heading) + " off it");
        }

        @Test
        void anOrbStartedBesideTheEyeHeadsAtTheLooksPointAtItsRangeInOpenAir() {
            double range = rangeOf("frost_orb");
            Vec3 far = EYE.add(LOOK_NORTH.scale(range));
            Vec3 heading = GooThrowHandler.rollHeading(HALF_A_BLOCK_RIGHT, EYE, LOOK_NORTH, range,
                    (from, to) -> to);
            assertTrue(offLine(far, HALF_A_BLOCK_RIGHT, heading) < ON_THE_LINE,
                    "The orb's path should pass within a twentieth of a block of the look's point at its range, "
                            + "passed " + offLine(far, HALF_A_BLOCK_RIGHT, heading) + " off it");
        }

        /**
         * From the blob's rest above the hand the orb still heads at the block the look hits
         * (decision no-ability-floats-its-blob-to-the-center-line).
         */
        @Test
        void anOrbStartedAtTheBlobsRestAboveTheHandHeadsAtTheBlockTheLookHits() {
            Vec3 look = Vec3.directionFromRotation(0f, NORTH_YAW);
            Vec3 rest = BlobRest.inView(EYE, 0f, NORTH_YAW, true, BlobRest.Rest.ABOVE_THE_HAND);
            double range = rangeOf("frost_orb");
            Vec3 hit = wallEightBlocksNorth(EYE, EYE.add(look.scale(range)));
            Vec3 heading = GooThrowHandler.rollHeading(rest, EYE, look, range, OrbHeading::wallEightBlocksNorth);
            assertTrue(offLine(hit, rest, heading) < ON_THE_LINE,
                    "The orb's path from the blob's rest should pass within a twentieth of a block of the look's "
                            + "hit point, passed " + offLine(hit, rest, heading) + " off it");
        }

        private static Vec3 wallEightBlocksNorth(Vec3 from, Vec3 to) {
            double wallZ = EYE.z - REST_WALL_OUT;
            if (to.z > wallZ || from.z <= wallZ) {
                return to;
            }
            return from.lerp(to, (from.z - wallZ) / (from.z - to.z));
        }

        private static Vec3 wallTenBlocksNorth(Vec3 from, Vec3 to) {
            double wallZ = EYE.z - WALL_OUT;
            if (to.z > wallZ || from.z <= wallZ) {
                return to;
            }
            return from.lerp(to, (from.z - wallZ) / (from.z - to.z));
        }

        private static double offLine(Vec3 point, Vec3 origin, Vec3 heading) {
            Vec3 offset = point.subtract(origin);
            return offset.subtract(heading.scale(offset.dot(heading))).length();
        }
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
    void spawnThrownByAPlayerHoldingNothingPassesTheReagentCheck() {
        // ability-json-names-its-reagent
        List<Identifier> spawnReagents = AbilityJson.decode("zoo_spawn").consumes();

        assertTrue(ReagentScanner.holdsEvery(spawnReagents, reagent -> false));
    }

    @Test
    void enchantThrownByAPlayerHoldingNoBookFailsTheReagentCheck() {
        List<Identifier> enchantReagents = AbilityJson.decode("hex_enchant").consumes();

        assertFalse(ReagentScanner.holdsEvery(enchantReagents, reagent -> false));
    }
}
