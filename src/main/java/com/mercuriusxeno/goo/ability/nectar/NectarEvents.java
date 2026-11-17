package com.mercuriusxeno.goo.ability.nectar;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.ability.NectarStick;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

/**
 * Holds down each jump pushing off Nectar goo by the jump share Nectar's
 * ability names, on either side, since the client jumps its own player.
 * nectar-is-the-sticky-goo-you-climb-that-nerfs-jumping-and-speed
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class NectarEvents {

    private NectarEvents() {
    }

    /**
     * @param event the living jump event, fired once the jump's rise is set
     */
    @SubscribeEvent
    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        LivingEntity jumper = event.getEntity();
        GooBlocks.JELLY_NECTAR.get().nectarIn(jumper.level())
                .ifPresent(nectar -> NectarStick.holdDown(jumper.level(), jumper, nectar));
    }
}
