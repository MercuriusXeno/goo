package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import java.util.List;

/**
 * Draws every live ghost trail: each ghost submits the entity's frozen
 * render state through its own renderer at the ghost's point, the body
 * alone redrawn translucent in the goo type's color at the ghost's fade.
 * Decision ghost-trail-spans-the-blink.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GhostTrailRenderer {

    private GhostTrailRenderer() {
    }

    /**
     * Submits the live trails' ghosts, dropping each trail that has faded.
     *
     * @param event the custom geometry submit event
     */
    @SubscribeEvent
    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        List<GhostTrails.GhostTrail<EntityRenderState>> trails = GhostTrails.CLIENT.live(mc.level.getGameTime());
        if (trails.isEmpty()) {
            return;
        }
        float gameTime = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for (GhostTrails.GhostTrail<EntityRenderState> trail : trails) {
            submitTrail(mc.getEntityRenderDispatcher(), event, trail, gameTime);
        }
    }

    /**
     * The ghost render type over the skin the entity's renderer draws its
     * frozen state with.
     *
     * @param living   the entity's renderer
     * @param snapshot the frozen render state, the renderer's own kind
     * @return the ghost render type
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static RenderType ghostTypeOf(LivingEntityRenderer living, EntityRenderState snapshot) {
        return GooRenderTypes.gooGhost(living.getTextureLocation((LivingEntityRenderState) snapshot));
    }

    /**
     * Submits one trail's showing ghosts.
     *
     * @param dispatcher the entity render dispatcher
     * @param event      the custom geometry submit event
     * @param trail      the trail
     * @param gameTime   the game time including the partial tick
     */
    private static void submitTrail(EntityRenderDispatcher dispatcher, SubmitCustomGeometryEvent event,
            GhostTrails.GhostTrail<EntityRenderState> trail, float gameTime) {
        EntityRenderState snapshot = trail.snapshot();
        EntityRenderer<?, ?> renderer = dispatcher.getRenderer(snapshot);
        if (!(renderer instanceof LivingEntityRenderer<?, ?, ?> living)) {
            return;
        }
        RenderType ghostType = ghostTypeOf(living, snapshot);
        CameraRenderState camera = event.getLevelRenderState().cameraRenderState;
        for (GhostTrails.Ghost ghost : trail.ghosts(gameTime)) {
            Vec3 fromCamera = ghost.position().subtract(camera.pos);
            dispatcher.submit(snapshot, camera, fromCamera.x, fromCamera.y, fromCamera.z, event.getPoseStack(),
                    new GhostCollector(event.getSubmitNodeCollector(), living.getModel(), ghostType,
                            ARGB.color(ghost.alpha(), trail.rgb())));
        }
    }
}
