package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.LeechGooStep;
import com.mercuriusxeno.goo.block.ability.LeechStanding;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.Optional;

/**
 * Hex wisps drifting up off every living thing standing in Leech goo, for
 * as long as it stands there: the client holds the cells and the mobs, so
 * each tick it reads who stands in the goo through the seam the server's
 * drain reads, and sheds a wisp off each one whose shed interval, the leech
 * goo step's, comes due.
 * hex-wisps-drift-off-mobs-standing-in-leech-goo
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class LeechWispShed {

    private LeechWispShed() {
    }

    /**
     * Sheds the wisps due this tick off the living things standing in Leech goo.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) {
            return;
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living) {
                shedIfDue(mc, level, living);
            }
        }
    }

    private static void shedIfDue(Minecraft mc, ClientLevel level, LivingEntity living) {
        if (!living.isAlive() || living.isSpectator()) {
            return;
        }
        Optional<BlockPos> cell = LeechStanding.leechCellUnder(living);
        Optional<LeechGooStep> leech = cell.flatMap(pos -> LeechStanding.stepAt(level, pos));
        if (leech.isPresent() && LeechWisps.sheds(true, living.tickCount, leech.get().wispShedTicks())) {
            LeechWisps.shed(mc, level.getRandom(), living);
        }
    }
}
