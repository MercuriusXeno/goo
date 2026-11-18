package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.NectarGooStep;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers Nectar's nerf: an entity touching the goo jumps and moves at the
 * jump and speed factors jelly_nectar.json ships, and at full strength once
 * clear of it.
 * nectar-is-the-sticky-goo-you-climb-that-nerfs-jumping-and-speed
 */
class NectarStickTest {

    private static final NectarGooStep SHIPPED = NectarGooStep.in(AbilityJson.decode("jelly_nectar").behaviors())
            .orElseThrow();
    private static final double FULL_STRENGTH = 1.0;
    private static final double SHIPPED_SPEED_SHARE = 0.6;
    /** A ladder's climb, blocks per tick. */
    private static final double LADDER_CLIMB = 0.2;
    /** A walking player's speed, blocks per tick. */
    private static final double WALK = 0.2158;
    private static final double SAME = 1e-9;
    private static final double FEET = 64.0;
    /** A player's step height. */
    private static final double STEP = 0.6;

    @Test
    void anEntityTouchingNectarJumpsAndMovesAtTheShippedFactors() {
        assertEquals(new NectarStick.Multipliers(SHIPPED.jumpFactor(), SHIPPED.speedFactor()),
                NectarStick.multipliersFor(true, SHIPPED));
    }

    /**
     * operator UAT 2026-11-17: on Nectar you are temporarily Spider-Man, gravity off while stuck; jumping ascends,
     * sneaking descends
     */
    @Test
    void anEntityStuckOnAWallRisesWhileJumpingSinksWhileSneakingAndHangsOtherwise() {
        double climb = SHIPPED.climbSpeed();

        assertEquals(List.of(climb, -climb, 0.0, 0.0), List.of(NectarStick.climbVelocity(true, false, climb),
                NectarStick.climbVelocity(false, true, climb), NectarStick.climbVelocity(false, false, climb),
                NectarStick.climbVelocity(true, true, climb)));
    }

    /**
     * operator ruling 2026-11-17: the Nectar speed nerf is reduced to 0.6, and climbing is nerfed to match it
     */
    @Test
    void nectarKeepsSixTenthsOfYourSpeedAndClimbsAtTheSameShareOfALadder() {
        assertEquals(SHIPPED_SPEED_SHARE, SHIPPED.speedFactor(), SAME);
        assertEquals(LADDER_CLIMB * SHIPPED_SPEED_SHARE, NectarStick.wallClimbSpeed(SHIPPED), SAME);
    }

    /**
     * operator ruling 2026-11-17: on the wall you move at walking speed, nerfed by the speed factor alone
     */
    @Test
    void anEntityStuckOnAWallMovesAlongItAtItsWalkingSpeedTimesTheSpeedShare() {
        Vec3 ahead = NectarStick.wallMotion(0, 1, 0f, WALK, SHIPPED.speedFactor());
        Vec3 beside = NectarStick.wallMotion(1, 0, 0f, WALK, SHIPPED.speedFactor());
        Vec3 diagonal = NectarStick.wallMotion(1, 1, 0f, WALK, SHIPPED.speedFactor());
        Vec3 still = NectarStick.wallMotion(0, 0, 0f, WALK, SHIPPED.speedFactor());

        double moved = WALK * SHIPPED.speedFactor();
        assertEquals(0, ahead.distanceTo(new Vec3(0, 0, moved)), SAME);
        assertEquals(0, beside.distanceTo(new Vec3(moved, 0, 0)), SAME);
        assertEquals(moved, diagonal.length(), SAME);
        assertEquals(Vec3.ZERO, still);
    }

    /**
     * operator UAT 2026-11-17: touching the side of Nectar goo taller than a step counts as Nectar on a wall
     */
    @Test
    void gooRisingPastAStepOverTheFeetCountsAsAWallAndThinGooUnderfootDoesNot() {
        assertEquals(List.of(true, false), List.of(NectarStick.towersOverStep(FEET + 1.0, FEET, STEP),
                NectarStick.towersOverStep(FEET + 0.5, FEET, STEP)));
    }

    @Test
    void anEntityClearOfNectarJumpsAndMovesAtFullStrength() {
        assertEquals(new NectarStick.Multipliers(FULL_STRENGTH, FULL_STRENGTH),
                NectarStick.multipliersFor(false, SHIPPED));
    }
}
