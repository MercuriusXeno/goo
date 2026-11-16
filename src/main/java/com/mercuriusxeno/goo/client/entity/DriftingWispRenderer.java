package com.mercuriusxeno.goo.client.entity;

import com.mercuriusxeno.goo.client.ability.WispGlow;
import com.mercuriusxeno.goo.client.ber.WispRenderer;
import com.mercuriusxeno.goo.entity.DriftingWisp;
import com.mercuriusxeno.goo.registry.GooBlocks;
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
 * settled wisp does. The drifting wisp lights nothing; only the mote shows,
 * and once its wisp block stands on its spot the block's mote draws instead.
 * wisps-travel-as-entities-then-settle-as-glowy-air-blocks
 */
public final class DriftingWispRenderer extends EntityRenderer<DriftingWisp, DriftingWispRenderer.DriftRenderState> {

    /** Degrees a drifting wisp turns each tick. */
    private static final float TURN_DEGREES_PER_TICK = 1.5f;
    /** A drifting wisp shows its whole mote. */
    private static final float WHOLE = 1f;
    /**
     * How near a block's center a drifting wisp stands, squared, to have
     * settled there, so one passing through another wisp's spot still draws.
     */
    private static final double SETTLED_SLACK_SQR = 0.01;

    /**
     * @param context the renderer context
     */
    public DriftingWispRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    /** A drifting wisp's render state: the entity's own, and whether its wisp block has taken over. */
    public static final class DriftRenderState extends EntityRenderState {
        /** True once the drifting wisp rests on its spot and the wisp block there draws the mote in its place. */
        boolean settled;
    }

    @Override
    public DriftRenderState createRenderState() {
        return new DriftRenderState();
    }

    @Override
    public void extractRenderState(DriftingWisp wisp, DriftRenderState state, float partialTick) {
        super.extractRenderState(wisp, state, partialTick);
        state.settled = wisp.position().distanceToSqr(Vec3.atCenterOf(wisp.blockPosition())) < SETTLED_SLACK_SQR
                && wisp.level().getBlockState(wisp.blockPosition()).is(GooBlocks.WISP.get());
    }

    @Override
    public void submit(DriftRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        if (state.settled) {
            // the settled wisp's own mote draws here now, so two never draw over each other
            return;
        }
        WispGlow.queue(new WispGlow.Sprite(new Vec3(state.x, state.y, state.z),
                state.ageInTicks * TURN_DEGREES_PER_TICK,
                (pose, consumer) -> WispRenderer.drawMote(pose, consumer, WHOLE)));
    }
}
