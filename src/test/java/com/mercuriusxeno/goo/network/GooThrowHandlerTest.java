package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.ability.quantum.GatePassage;
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
        GateArc.Hop hop = GooThrowHandler.admittedHop(List.of(new GatePassage.Pair(NEAR, FAR)), NEAR, face, hand)
                .orElseThrow(() -> new AssertionError("A throw through a linked gate should be admitted"));
        assertEquals(hand.distanceTo(face) + hop.partnerPoint().distanceTo(target),
                GooThrowHandler.throughDistance(hand, hop, target), TOLERANCE,
                "The throw should measure the hand to the face plus the partner to the target");
    }

    @Test
    void aThrowNamingAGateInNoPairIsRefused() {
        Vec3 hand = new Vec3(0.5, 65.0, -4.0);
        assertTrue(GooThrowHandler.admittedHop(List.of(), NEAR, NEAR.center(), hand).isEmpty(),
                "A throw naming a gate that stands in no pair should be refused");
    }

    @Test
    void aPointBeyondRangeIsCappedOntoTheRangeAlongItsLine() {
        Vec3 far = EYE.add(30, 90, -60);

        Vec3 capped = GooThrowHandler.capToRange(EYE, far, GooThrowHandler.MAX_RANGE);

        assertEquals(GooThrowHandler.MAX_RANGE, capped.distanceTo(EYE), TOLERANCE);
        Vec3 along = far.subtract(EYE).normalize();
        assertEquals(1, capped.subtract(EYE).normalize().dot(along), TOLERANCE);
    }

    @Test
    void aPointWithinRangePassesUnchanged() {
        Vec3 near = EYE.add(12, -3, 40);

        assertSame(near, GooThrowHandler.capToRange(EYE, near, GooThrowHandler.MAX_RANGE));
    }

    @Test
    void aPointOnTheRangePassesUnchanged() {
        Vec3 edge = EYE.add(0, 0, GooThrowHandler.MAX_RANGE);

        assertSame(edge, GooThrowHandler.capToRange(EYE, edge, GooThrowHandler.MAX_RANGE));
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
