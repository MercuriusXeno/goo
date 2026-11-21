package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.jspecify.annotations.Nullable;

/**
 * Prepares each render type goo draws the first time outside any open render pass, where its textures may load: a
 * type a stage draw first meets inside the stage's open pass prepares at the next frame's start and draws from then.
 * decision minecraft-26-3-update-is-a-port-to-parity
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class RenderTypePreparation {

    private static final FirstPreparedOutsideAPass<RenderType, PreparedRenderType> PREPARATION =
            new FirstPreparedOutsideAPass<>(RenderType::prepare);

    private RenderTypePreparation() {
    }

    /**
     * @param type     the render type a draw prepares
     * @param passOpen whether a render pass stands open where the draw runs
     * @return the prepared type, or null where its first prepare waits for the next frame's start
     */
    static @Nullable PreparedRenderType prepared(RenderType type, boolean passOpen) {
        return PREPARATION.prepared(type, passOpen);
    }

    /**
     * Prepares each render type a stage draw met inside an open pass, before any pass opens this frame.
     *
     * @param event the frame start event
     */
    @SubscribeEvent
    public static void onFrameStart(RenderFrameEvent.Pre event) {
        PREPARATION.prepareWaiting();
    }
}
