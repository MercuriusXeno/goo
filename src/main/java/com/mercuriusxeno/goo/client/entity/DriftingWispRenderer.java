package com.mercuriusxeno.goo.client.entity;

import com.mercuriusxeno.goo.client.ability.WispGlow;
import com.mercuriusxeno.goo.client.ber.WispRenderer;
import com.mercuriusxeno.goo.entity.DriftingWisp;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a drifting wisp as the mote a settled wisp draws, turning as it
 * goes, queued to {@link WispGlow} so it shows in front of water as a
 * settled wisp does. The drifting wisp lights nothing; only the mote shows.
 * wisps-travel-as-entities-then-settle-as-glowy-air-blocks
 */
public final class DriftingWispRenderer extends EntityRenderer<DriftingWisp, EntityRenderState> {

    /** Degrees a drifting wisp turns each tick. */
    private static final float TURN_DEGREES_PER_TICK = 1.5f;
    /** A drifting wisp shows its whole mote. */
    private static final float WHOLE = 1f;

    /**
     * @param context the renderer context
     */
    public DriftingWispRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    @Override
    public void submit(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        WispGlow.queue(new WispGlow.Sprite(new Vec3(state.x, state.y, state.z),
                state.ageInTicks * TURN_DEGREES_PER_TICK,
                (pose, consumer) -> WispRenderer.drawMote(pose, consumer, WHOLE)));
    }
}
