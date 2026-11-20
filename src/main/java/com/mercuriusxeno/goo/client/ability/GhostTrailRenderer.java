package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.ImmediateBuffers;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
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
import java.util.List;
import java.util.function.Consumer;

/**
 * Draws every live ghost trail: each ghost submits the entity's frozen
 * render state through its own renderer at the ghost's point, the body
 * alone redrawn translucent in the goo type's color at the ghost's fade.
 * A ghost writes no depth, so it draws on the shared path after the clouds
 * rather than in vanilla's main pass before them (operator ruling 2026-11-18).
 * Decision ghost-trail-spans-the-blink.
 */
public final class GhostTrailRenderer {

    private GhostTrailRenderer() {
    }

    /**
     * Hands the shared path one draw per showing ghost of every live trail,
     * each sorted by the camera's distance to the ghost's point, dropping
     * each trail that has faded.
     * decision diagnose-and-correct-translucent-goo-visuals-that-depth-sort-wrong
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     * decision ghost-trail-spans-the-blink
     *
     * @param frame the frame being drawn
     * @param draws where each ghost's draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
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
            collectTrail(mc.getEntityRenderDispatcher(), frame, trail, gameTime, draws);
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
     * Hands the path one trail's showing ghosts.
     *
     * @param dispatcher the entity render dispatcher
     * @param frame      the frame being drawn
     * @param trail      the trail
     * @param gameTime   the game time including the partial tick
     * @param draws      where each ghost's draw goes
     */
    private static void collectTrail(EntityRenderDispatcher dispatcher, LevelFrame frame,
            GhostTrails.GhostTrail<EntityRenderState> trail, float gameTime, Consumer<SortedDraw> draws) {
        EntityRenderState snapshot = trail.snapshot();
        EntityRenderer<?, ?> renderer = dispatcher.getRenderer(snapshot);
        if (!(renderer instanceof LivingEntityRenderer<?, ?, ?> living)) {
            return;
        }
        RenderType ghostType = ghostTypeOf(living, snapshot);
        CameraRenderState camera = frame.levelRenderState().cameraRenderState;
        for (GhostTrails.Ghost ghost : trail.ghosts(gameTime)) {
            int color = ARGB.color(ghost.alpha(), trail.rgb());
            draws.accept(new SortedDraw(ghost.position().distanceToSqr(camera.pos), () -> {
                ImmediateBuffers buffers = ImmediateBuffers.shared();
                Vec3 fromCamera = ghost.position().subtract(camera.pos);
                dispatcher.submit(snapshot, camera, fromCamera.x, fromCamera.y, fromCamera.z, frame.poseStack(),
                        new GhostCollector(buffers, living.getModel(), ghostType, color));
                buffers.endBatch(ghostType);
            }));
        }
    }
}
