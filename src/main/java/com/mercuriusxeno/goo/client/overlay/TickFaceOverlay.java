package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.TickBlockStep;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;
import java.util.Optional;

/**
 * Tick's channel overlay: while right click holds a stream that ticks the
 * block it ends on, the aimed block is highlighted and the aimed face wears
 * golden squares marching out from its middle like a wave emitter, faster
 * the more extra ticks the machine takes, drawn by {@code goo_tick_face.fsh}.
 * tick-channel-marches-squares-on-the-face
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class TickFaceOverlay {

    /** The march rate the shader's blue channel at one stands for, in rings a tick. */
    static final float MAX_RINGS_PER_TICK = 0.5f;
    /** Rings a tick the squares march at for each tick the machine takes, its own and the extra. */
    static final float RINGS_PER_MACHINE_TICK = 0.02f;
    /** How far the overlay sits off the face along its normal, clear of the highlight's fill. */
    private static final double FACE_NUDGE = 0.004;
    private static final double HALF = 0.5;
    private static final int OPAQUE = 255;

    private TickFaceOverlay() {
    }

    /**
     * How fast the squares march for a number of extra ticks: in step with the
     * machine's ticks a game tick, its own one and the extra, capped at the
     * rate the shader reads.
     *
     * @param extraTicks the extra ticks the machine takes each game tick
     * @return rings a tick
     */
    static float marchRate(int extraTicks) {
        return Math.min(MAX_RINGS_PER_TICK, RINGS_PER_MACHINE_TICK * (1 + Math.max(0, extraTicks)));
    }

    /**
     * Highlights the aimed block and draws the marching squares on its aimed
     * face while a tick stream runs and a block stands within its reach.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientAbility ability = player == null || mc.level == null ? null : runningTick(player);
        Optional<TickBlockStep> tick = ability == null ? Optional.empty() : tickStepOf(ability);
        BlockHitResult block = tick.isEmpty() ? null : aimedBlock(mc, player, ability);
        if (block == null) {
            return;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VoxelHighlightRenderer.renderBlockShape(poseStack, buffers, camera, block.getBlockPos(), GooTypes.AEON);
        FlatQuadContext quads = new FlatQuadContext(poseStack.last(), buffers.getBuffer(GooRenderTypes.TICK_FACE_TYPE));
        emitFace(quads, block.getBlockPos(), block.getDirection(), camera.position(),
                marchRate(tick.get().extraTicks()));
        buffers.endBatch(GooRenderTypes.TICK_FACE_TYPE);
    }

    /**
     * Emits the face quad, each corner's color carrying its place on the face
     * and the march rate for the shader.
     *
     * @param quads  the quad context
     * @param pos    the aimed block
     * @param face   the aimed face
     * @param camera the camera's position
     * @param rate   the march rate, rings a tick
     */
    private static void emitFace(FlatQuadContext quads, BlockPos pos, Direction face, Vec3 camera, float rate) {
        Vec3 normal = face.getUnitVec3();
        Vec3 center = Vec3.atCenterOf(pos).add(normal.scale(HALF + FACE_NUDGE)).subtract(camera);
        Direction.Axis axis = face.getAxis();
        Vec3 across = axis == Direction.Axis.X ? new Vec3(0, 0, 1) : new Vec3(1, 0, 0);
        Vec3 along = axis == Direction.Axis.Y ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
        int rateChannel = Math.round(rate / MAX_RINGS_PER_TICK * OPAQUE);
        int[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int[] corner : corners) {
            Vec3 point = center.add(across.scale(corner[0] - HALF)).add(along.scale(corner[1] - HALF));
            int color = ARGB.color(OPAQUE, corner[0] * OPAQUE, corner[1] * OPAQUE, rateChannel);
            quads.vertex((float) point.x, (float) point.y, (float) point.z, color,
                    (float) normal.x, (float) normal.y, (float) normal.z);
        }
    }

    /**
     * The block the tick stream ends on.
     *
     * @param mc      the client
     * @param player  the local player
     * @param ability the tick stream
     * @return the hit on the aimed block, or null where the stream reaches none
     */
    private static @Nullable BlockHitResult aimedBlock(Minecraft mc, LocalPlayer player, ClientAbility ability) {
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        HitResult hit = player.pick(ability.delivery().range(), partialTick, false);
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK ? block : null;
    }

    /**
     * The tick stream the local player's glove runs now.
     *
     * @param player the local player
     * @return the selected ability while right click holds it, otherwise null
     */
    private static @Nullable ClientAbility runningTick(LocalPlayer player) {
        if (!GloveUseTracker.showsArea()) {
            return null;
        }
        String abilityId = GloveAim.selectedAbilityId(player);
        return abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
    }

    private static Optional<TickBlockStep> tickStepOf(ClientAbility ability) {
        return ability.behaviors().stream().filter(TickBlockStep.class::isInstance).map(TickBlockStep.class::cast)
                .findFirst();
    }
}
