package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.program.ChannelHost;
import com.mercuriusxeno.goo.ability.program.DripLanding;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Drips a test lands straight down onto a cell's top, all striking in one
 * flight tick through the real landing a stream's drips take, its caster
 * paying, so a test reads what a counted number of strikes did. The flights
 * fly in a set of their own, so no other test's drips tick with them.
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 */
final class LandedDrips {

    /** Where each drip starts over the cell's floor: just inside the cell above, over any top the cell can hold. */
    private static final double START_OVER_THE_FLOOR = 1.05;
    /** Straight down, fast enough that one tick carries the drip from its start through the cell's floor. */
    private static final Vec3 FALLING = new Vec3(0, -1.1, 0);
    /** Where a side drip starts from the struck block's center: inside the open cell beside the face. */
    private static final double START_OFF_THE_FACE = 1.05;
    /** A side drip's speed into the face, enough that one tick carries it from its start through the face. */
    private static final double INTO_THE_FACE = 1.1;

    private LandedDrips() {
    }

    /**
     * Lands drips on a cell in one flight tick, each striking the top of
     * whatever stands in the cell, or the floor under it.
     *
     * @param helper  the gametest helper
     * @param caster  the player who threw them and pays for what they lay
     * @param ability the stream ability whose landing step lays what they strike
     * @param cell    the cell, relative to the test
     * @param drips   how many drips strike in the tick
     */
    static void landOn(GameTestHelper helper, ServerPlayer caster, AbilityDefinition ability, BlockPos cell,
                       int drips) {
        DripFlights flights = new DripFlights();
        DripFlights.Thrower thrower = new DripFlights.Thrower(helper.getLevel().dimension(), caster.getUUID(),
                caster.getId(), ability, DripLanding.in(ChannelHost.passSteps(ability.behaviors(), true)), false);
        Vec3 start = Vec3.atBottomCenterOf(helper.absolutePos(cell)).add(0, START_OVER_THE_FLOOR, 0);
        for (int drip = 0; drip < drips; drip++) {
            flights.launch(new DripFlights.Flight(thrower, start, FALLING));
        }
        fly(helper, flights);
    }

    /**
     * Lands drips on a block's side in one flight tick, each flying level
     * into the face from just inside the open cell beside it.
     * drip-striking-a-side-sticks-or-runs-by-adhesion
     *
     * @param helper  the gametest helper
     * @param caster  the player who threw them and pays for what they lay
     * @param ability the stream ability whose landing step lays what they strike
     * @param wall    the struck block, relative to the test
     * @param face    the side of it they strike
     * @param drips   how many drips strike in the tick
     */
    static void landOnSide(GameTestHelper helper, ServerPlayer caster, AbilityDefinition ability, BlockPos wall,
                           Direction face, int drips) {
        DripFlights flights = new DripFlights();
        DripFlights.Thrower thrower = new DripFlights.Thrower(helper.getLevel().dimension(), caster.getUUID(),
                caster.getId(), ability, DripLanding.in(ChannelHost.passSteps(ability.behaviors(), true)), false);
        Vec3 normal = Vec3.atLowerCornerOf(face.getUnitVec3i());
        Vec3 start = Vec3.atCenterOf(helper.absolutePos(wall)).add(normal.scale(START_OFF_THE_FACE));
        for (int drip = 0; drip < drips; drip++) {
            flights.launch(new DripFlights.Flight(thrower, start, normal.scale(-INTO_THE_FACE)));
        }
        fly(helper, flights);
    }

    private static void fly(GameTestHelper helper, DripFlights flights) {
        flights.tick(helper.getLevel().getServer());
        helper.assertTrue(!flights.hasPending(), "Every landed drip should strike in its first flight tick");
    }
}
