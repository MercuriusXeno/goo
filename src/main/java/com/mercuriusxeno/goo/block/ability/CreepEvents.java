package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooServerState;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Ticks the sculk charges Creep goo started in each server level, as a
 * catalyst's block entity ticks its own spreader.
 * mob-dying-on-or-near-host-goo-spreads-sculk-from-the-goo
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class CreepEvents {

    private CreepEvents() {
    }

    /**
     * Moves the level's Creep charges one tick along.
     *
     * @param event the level tick event
     */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            GooServerState.of(level.getServer()).creepSpreaders().tick(level);
        }
    }
}
