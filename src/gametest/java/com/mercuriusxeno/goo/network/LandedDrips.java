package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.program.ChannelHost;
import com.mercuriusxeno.goo.ability.program.DripLanding;
import net.minecraft.core.BlockPos;
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
        flights.tick(helper.getLevel().getServer());
        helper.assertTrue(!flights.hasPending(), "Every landed drip should strike in its first flight tick");
    }
}
