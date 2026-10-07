package com.mercuriusxeno.goo.ability.nourish;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Feeds a nourished player a food point every interval while Nourish stands.
 * nourish-restores-hunger-over-time
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class NourishEvents {

    /** The food points one landing adds. */
    private static final int FOOD_PER_POINT = 1;
    /** A landing adds hunger, not saturation. */
    private static final float NO_SATURATION = 0f;

    private NourishEvents() {
    }

    /**
     * Advances a standing nourishment one tick on the server, feeding the
     * player when a point lands.
     *
     * @param event the player tick event
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.hasData(GooAttachments.NOURISH)) {
            return;
        }
        Nourish nourish = player.getData(GooAttachments.NOURISH);
        long now = player.level().getGameTime();
        if (nourish.feeds(now)) {
            player.getFoodData().eat(FOOD_PER_POINT, NO_SATURATION);
        }
        Nourish after = nourish.tick(now);
        if (after != nourish) {
            player.setData(GooAttachments.NOURISH, after);
        }
    }
}
