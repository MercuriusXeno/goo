package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.Goo;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Carries Scry's rings on each server tick, and lets go every sweep's landed
 * pulses, so a hold let go with a front still out leaves none standing.
 * decision diagnose-and-correct-scry-lag-building-over-a-long-cast
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class HeldSweepPulseEvents {

    private HeldSweepPulseEvents() {
    }

    /**
     * Carries each instant Scry ring's front on, then drops the landed
     * pulses, after each server tick.
     * decision scry-is-instant-and-no-longer-channeled
     *
     * @param event the server tick event
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ScryRings.SERVER.tick();
        HeldSweepPulses.SERVER.dropLanded(event.getServer().overworld().getGameTime());
    }
}
