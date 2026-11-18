package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Lightning striking by Nitro shocks it: every Nitro cell within a block of
 * the bolt's strike goes off.
 * nitro-explodes-when-touched-heated-shocked-pulsed-or-struck
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class NitroEvents {

    /** How far from the bolt's strike a Nitro cell takes the shock, in blocks each way. */
    private static final int SHOCK_REACH = 1;

    private NitroEvents() {
    }

    /**
     * Shocks the Nitro around a bolt as it strikes.
     *
     * @param event the entity joining a level
     */
    @SubscribeEvent
    public static void onLightningStrike(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof LightningBolt bolt && event.getLevel() instanceof ServerLevel level) {
            BlockPos strike = bolt.blockPosition();
            BlockPos.betweenClosedStream(strike.offset(-SHOCK_REACH, -SHOCK_REACH, -SHOCK_REACH),
                            strike.offset(SHOCK_REACH, SHOCK_REACH, SHOCK_REACH))
                    .map(BlockPos::immutable).toList()
                    .forEach(cell -> shock(level, cell));
        }
    }

    private static void shock(ServerLevel level, BlockPos cell) {
        if (level.getBlockState(cell).getBlock() instanceof NitroCellBlock nitro) {
            nitro.actOn(level, cell, NitroContact.SHOCK);
        }
    }
}
