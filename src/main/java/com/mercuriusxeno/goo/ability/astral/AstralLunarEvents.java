package com.mercuriusxeno.goo.ability.astral;

import com.mercuriusxeno.goo.Goo;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Runs Lunar each server tick: every fixed caster stands held, and a caster whose fixation ran out is
 * carried. The server's tick reaches every player on its list, a player its connection does not tick among them.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class AstralLunarEvents {

    private AstralLunarEvents() {
    }

    /**
     * Runs one tick of every standing fixation.
     *
     * @param event the server tick event
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        AstralLunar.tickAll(event.getServer());
    }
}
