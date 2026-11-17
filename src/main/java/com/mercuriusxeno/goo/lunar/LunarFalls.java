package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.Goo;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

/**
 * A landing in lunar is charged for one sixth of the blocks fallen: vanilla counts fall damage from blocks fallen, not
 * from the speed of the landing, so under one-sixth gravity a jump six times as high would hurt; scaled by the same
 * sixth, a jump lands unhurt and a long drop deals what a drop one sixth as far deals in the overworld.
 * decision lunar-gravity-is-one-sixth-of-the-overworlds
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class LunarFalls {

    private LunarFalls() {
    }

    /**
     * Scales the fall a landing is charged for by its dimension's gravity.
     *
     * @param event the living fall event
     */
    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        event.setDistance(event.getDistance() * LunarGravity.scaleIn(event.getEntity().level().dimension()));
    }
}
