package com.mercuriusxeno.goo.item;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ExoriteGear seam on plain values (decision zero-durability-stops-working):
 * where a piece reads broken, which hits reach the floor, and how a broken
 * piece mines. Gametests in ExoriteDurabilityTests prove the same on stacks.
 */
class ExoriteGearTest {

    private static final int MAX_DAMAGE = 2_500;
    private static final float NETHERITE_SPEED = 9.0F;

    @Test
    void brokenAtMaxDamage() {
        assertTrue(ExoriteGear.isBroken(MAX_DAMAGE, MAX_DAMAGE));
    }

    @Test
    void intactOneShortOfMaxDamage() {
        assertFalse(ExoriteGear.isBroken(MAX_DAMAGE - 1, MAX_DAMAGE));
    }

    @Test
    void hitToExactlyMaxReachesFloor() {
        assertTrue(ExoriteGear.hitReachesFloor(MAX_DAMAGE - 5, MAX_DAMAGE, 5));
    }

    @Test
    void hitPastMaxReachesFloor() {
        assertTrue(ExoriteGear.hitReachesFloor(0, MAX_DAMAGE, MAX_DAMAGE + 10));
    }

    @Test
    void hitShortOfMaxPassesThrough() {
        assertFalse(ExoriteGear.hitReachesFloor(MAX_DAMAGE - 5, MAX_DAMAGE, 4));
    }

    @Test
    void repairingHitOnBrokenPiecePassesThrough() {
        assertFalse(ExoriteGear.hitReachesFloor(MAX_DAMAGE, MAX_DAMAGE, -3));
    }

    @Test
    void brokenPieceMinesAtBareHandSpeed() {
        assertEquals(ExoriteGear.BARE_HAND_DESTROY_SPEED, ExoriteGear.destroySpeed(true, NETHERITE_SPEED));
    }

    @Test
    void intactPieceMinesAtItsOwnSpeed() {
        assertEquals(NETHERITE_SPEED, ExoriteGear.destroySpeed(false, NETHERITE_SPEED));
    }

    @Test
    void brokenPieceIsNeverCorrectTool() {
        assertFalse(ExoriteGear.correctToolForDrops(true, true));
    }

    @Test
    void intactPieceFollowsItsToolRules() {
        assertTrue(ExoriteGear.correctToolForDrops(false, true));
        assertFalse(ExoriteGear.correctToolForDrops(false, false));
    }
}
