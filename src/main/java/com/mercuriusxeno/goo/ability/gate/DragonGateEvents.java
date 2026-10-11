package com.mercuriusxeno.goo.ability.gate;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Closes each Dragon Gate pair on its clock: once a second the server puts
 * back the blocks every expired pair covered, loading their chunks as it
 * writes them.
 * Decision end-clears-blocks-and-opens-a-portal.
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class DragonGateEvents {

    /** Ticks between one look for expired pairs and the next. */
    static final int CHECK_INTERVAL_TICKS = 20;

    private DragonGateEvents() {
    }

    /**
     * Closes the expired pairs once a second.
     *
     * @param event the server tick event
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now % CHECK_INTERVAL_TICKS == 0) {
            DragonGateOpening.closeExpired(server, now);
        }
    }
}
