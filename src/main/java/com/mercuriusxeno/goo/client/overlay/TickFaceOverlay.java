package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.TickBlockStep;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

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
        if (mc.level == null) {
            return;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        drawChannel(mc, poseStack, buffers, camera);
        drawSplashes(mc, poseStack, buffers, camera);
    }

    /**
     * Highlights the block a running tick stream ends on and draws the
     * squares over its aimed face.
     *
     * @param mc        the client
     * @param poseStack the pose stack
     * @param buffers   the buffer source
     * @param camera    the render camera
     */
    private static void drawChannel(Minecraft mc, PoseStack poseStack, MultiBufferSource.BufferSource buffers,
            Camera camera) {
        LocalPlayer player = mc.player;
        ClientAbility ability = player == null ? null : runningTick(player);
        Optional<TickBlockStep> tick = ability == null ? Optional.empty() : tickStepOf(ability.behaviors());
        BlockHitResult block = tick.isEmpty() ? null : aimedBlock(mc, player, ability);
        if (block == null) {
            return;
        }
        VoxelHighlightRenderer.renderBlockShape(poseStack, buffers, camera, block.getBlockPos(), GooTypes.AEON);
        Direction face = block.getDirection();
        Vec3 faceCenter = Vec3.atCenterOf(block.getBlockPos()).add(face.getUnitVec3().scale(HALF));
        FlatQuadContext quads = new FlatQuadContext(poseStack.last(), buffers.getBuffer(GooRenderTypes.TICK_FACE_TYPE));
        emitFace(quads, new FaceQuad(faceCenter, face, 1.0, 1f), camera.position(), marchRate(tick.get().extraTicks()));
        buffers.endBatch(GooRenderTypes.TICK_FACE_TYPE);
    }

    /**
     * Draws each drip's splash: the squares small on the face the drip
     * struck, fading out (decision tick-drip-splashes-a-small-tick-effect).
     *
     * @param mc        the client
     * @param poseStack the pose stack
     * @param buffers   the buffer source
     * @param camera    the render camera
     */
    private static void drawSplashes(Minecraft mc, PoseStack poseStack, MultiBufferSource.BufferSource buffers,
            Camera camera) {
        List<TickSplashes.Splash> live = TickSplashes.CLIENT.live(mc.level.getGameTime());
        if (live.isEmpty()) {
            return;
        }
        float gameTime = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        FlatQuadContext quads = new FlatQuadContext(poseStack.last(), buffers.getBuffer(GooRenderTypes.TICK_FACE_TYPE));
        for (TickSplashes.Splash splash : live) {
            emitFace(quads, new FaceQuad(splash.at(), Direction.UP, TickSplashes.SPLASH_SIZE,
                    splash.strength(gameTime)), camera.position(), marchRate(splash.extraTicks()));
        }
        buffers.endBatch(GooRenderTypes.TICK_FACE_TYPE);
    }

    /**
     * Starts a drip's splash where it lands, when its goo type's tap ability
     * ticks the block below; the drip's landing particle calls it.
     * tick-drip-splashes-a-small-tick-effect
     *
     * @param gooType the drip's goo type
     * @param at      the world point the drip lands on
     */
    public static void splashOnLanding(ResourceKey<GooTypeDefinition> gooType, Vec3 at) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        AbilitySyncHandler.getAbilitiesForType(gooType).stream()
                .filter(ability -> ability.tags().contains(AbilityTags.TAP))
                .map(ability -> tickStepOf(ability.behaviors()))
                .flatMap(Optional::stream)
                .findFirst()
                .ifPresent(tick -> TickSplashes.CLIENT.splash(at, tick.extraTicks(), now));
    }

    /**
     * Emits the marching squares on one face of a shape in the pose's own
     * space, as the timekeeper prism wears them on its shell
     * (decision timekeeper-prism-banks-ticks-forward-only).
     *
     * @param quads      the quad context
     * @param center     the face's center in the pose's space
     * @param face       the face
     * @param size       the face's width
     * @param extraTicks the extra ticks whose pace the squares march at
     * @param strength   how strongly the squares glow, 0 to 1
     */
    public static void emitFaceQuad(FlatQuadContext quads, Vec3 center, Direction face, double size, int extraTicks,
                                    float strength) {
        emitFace(quads, new FaceQuad(center, face, size, strength), Vec3.ZERO, marchRate(extraTicks));
    }

    /**
     * One quad of the overlay on a face.
     *
     * @param center   the face's center in the world
     * @param face     the face
     * @param size     the quad's width in blocks
     * @param strength how strongly it draws, 0 to 1
     */
    private record FaceQuad(Vec3 center, Direction face, double size, float strength) {
    }

    /**
     * Emits a face quad, each corner's color carrying its place on the face,
     * the march rate and the quad's strength for the shader.
     *
     * @param quads  the quad context
     * @param quad   the quad
     * @param camera the camera's position
     * @param rate   the march rate, rings a tick
     */
    private static void emitFace(FlatQuadContext quads, FaceQuad quad, Vec3 camera, float rate) {
        Vec3 normal = quad.face().getUnitVec3();
        Vec3 center = quad.center().add(normal.scale(FACE_NUDGE)).subtract(camera);
        Direction.Axis axis = quad.face().getAxis();
        Vec3 across = axis == Direction.Axis.X ? new Vec3(0, 0, quad.size()) : new Vec3(quad.size(), 0, 0);
        Vec3 along = axis == Direction.Axis.Y ? new Vec3(0, 0, quad.size()) : new Vec3(0, quad.size(), 0);
        int rateChannel = Math.round(rate / MAX_RINGS_PER_TICK * OPAQUE);
        int alpha = Math.round(quad.strength() * OPAQUE);
        int[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int[] corner : corners) {
            Vec3 point = center.add(across.scale(corner[0] - HALF)).add(along.scale(corner[1] - HALF));
            int color = ARGB.color(alpha, corner[0] * OPAQUE, corner[1] * OPAQUE, rateChannel);
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
        // tick-channel-marches-squares-on-the-face: only a block entity can tick, so a plain block gets no overlay
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                && mc.level.getBlockEntity(block.getBlockPos()) != null ? block : null;
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

    /**
     * The tick_block step in a program, at its top or under another step,
     * as Tick's tap holds it under its drip count.
     *
     * @param behaviors the program
     * @return the first tick_block step, empty where the program ticks no block
     */
    static Optional<TickBlockStep> tickStepOf(List<Step> behaviors) {
        return behaviors.stream().flatMap(TickFaceOverlay::withDescendants).filter(TickBlockStep.class::isInstance)
                .map(TickBlockStep.class::cast).findFirst();
    }

    private static Stream<Step> withDescendants(Step step) {
        return Stream.concat(Stream.of(step), step.children().flatMap(TickFaceOverlay::withDescendants));
    }
}
