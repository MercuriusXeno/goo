package com.mercuriusxeno.goo.ability.nectar;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.ability.NectarStick;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

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
     * Sticks a living thing touching Nectar goo taller than a step, and gives
     * a living thing its gravity back the tick after it leaves Nectar on a wall.
     * operator UAT 2026-11-17: on Nectar you are temporarily Spider-Man; gravity is off while you are stuck
     *
     * @param event the entity tick event, after the entity's tick
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity living) {
            // operator UAT 2026-11-17: touching the side of Nectar goo taller than a step counts as Nectar on a wall
            NectarStick.stickToTallGoo(living.level(), living);
            NectarStick.unstickIfClear(living.level(), living);
        }
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
