package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.Goo;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Lets go every held sweep's landed pulses each server tick, so a hold let
 * go with a front still out leaves none standing.
 * decision diagnose-and-correct-scry-lag-building-over-a-long-cast
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class HeldSweepPulseEvents {

    private HeldSweepPulseEvents() {
    }

    /**
     * Drops the landed pulses after each server tick.
     *
     * @param event the server tick event
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        HeldSweepPulses.SERVER.dropLanded(event.getServer().overworld().getGameTime());
    }
}
