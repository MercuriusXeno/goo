package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.canister.CanisterGeometry;
import com.mercuriusxeno.goo.block.tap.TapBlock;
import com.mercuriusxeno.goo.block.tap.TapBlockEntity;
import com.mercuriusxeno.goo.block.tap.TapStream;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.particle.DripQuadPlacement;
import com.mercuriusxeno.goo.client.particle.TapDripLook;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.registry.GooEnchantments;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Renders the canister sitting on a tap's body slot through the shared
 * {@link CanisterSlotRenderer}, positioned at the per-facing canister slot
 * center, and the stream the tap pours at 1:1 and 1:4.
 */
public class TapBlockEntityRenderer
        implements BlockEntityRenderer<TapBlockEntity, TapRenderState> {

    /**
     * Divisor for computing AABB center from min+max.
     */
    private static final double CENTER_DIVISOR = 2.0;

    // -- Stream geometry (block coords) --

    /**
     * Stream half-width at 1:1: a trickle half a pixel wide. The half-width
     * grows with the square root of the mB poured a tick, so 1:4 is twice as
     * wide both ways (decision one-to-one-draws-a-stream).
     */
    private static final float TRICKLE_HW = 0.25f / 16f;

    /**
     * Stream center X and Z: the spigot sits on the block's vertical axis.
     */
    private static final float STREAM_CENTER = 0.5f;

    /**
     * Top of the stream: the spigot's underside.
     */
    private static final float STREAM_TOP = (float) TapStream.SPIGOT_UNDERSIDE_LOCAL_Y;

    // -- Splat geometry (decision stream-leaves-a-live-splat) --

    /**
     * How many stream half-widths the splat spreads to either side of the
     * landing point, so it widens with the stream from 1:1 to 1:4.
     */
    private static final float SPLAT_SPREAD = 6f;

    /** Radians the splat's pulse advances a tick. */
    private static final float SPLAT_PULSE_FREQUENCY = 0.4f;

    /** Fraction the splat's half-width swells and shrinks by as it pulses. */
    private static final float SPLAT_PULSE_AMPLITUDE = 0.15f;

    /** Splat width in sprite-local space per block of splat half-width: the sprite at native scale, both ways. */
    private static final float SPLAT_SPRITE_PER_HALF_WIDTH = 2f;

    /** Blocks the splat's texture drifts a tick, so the sprite patch keeps moving under the stream. */
    private static final float SPLAT_DRIFT_PER_TICK = 0.02f;

    /**
     * Creates a tap BER.
     *
     * @param context the renderer provider context
     */
    public TapBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    /**
     * Reads compression level and goo fill from the canister stack.
     *
     * @param canister the canister item stack
     * @param state    the render state to populate
     */
    private static void extractCanisterContents(ItemStack canister, TapRenderState state) {
        state.slot.compression = GooEnchantments.getCompressionLevel(canister);
        CanisterFluidContent content = CanisterItem.getFluidContent(canister);
        if (content.isEmpty()) {
            clearContents(state);
        } else {
            extractNonEmptyContents(content, state);
        }
    }

    /**
     * Populates goo type and fill ratio from non-empty canister contents.
     *
     * @param content the non-empty canister fluid content
     * @param state   the render state to populate
     */
    private static void extractNonEmptyContents(CanisterFluidContent content, TapRenderState state) {
        int capacity = ContainerCapacity.canisterCapacity(state.slot.compression);
        state.slot.type = content.dominantGooType();
        state.slot.fill = Math.min(1f, (float) content.totalVolume() / capacity);
    }

    /**
     * Resets goo type and fill to empty defaults.
     *
     * @param state the render state to clear
     */
    private static void clearContents(TapRenderState state) {
        state.slot.type = null;
        state.slot.fill = 0f;
    }

    /**
     * Emits the four sides of the thin goo column the tap pours at 1:1 and
     * 1:4, from the spigot underside down to the landing surface, its sprite
     * tiled at native scale and flowing downward (decision
     * diagnose-then-fix-stream-tiling); a tap pouring no stream emits nothing
     * (decision one-to-one-draws-a-stream).
     *
     * @param ctx    the render context
     * @param state  the tap render state
     * @param sprite the goo type's fluid sprite
     * @param tint   the goo type's fluid tint
     */
    static void emitStream(RenderContext ctx, TapRenderState state, TextureAtlasSprite sprite, int tint) {
        if (state.streamType == null) {
            return;
        }
        GooStreamRenderer.emitTiledColumn(ctx,
                new GooStreamRenderer.StreamColumn(STREAM_CENTER, STREAM_CENTER, STREAM_TOP, state.streamBottomY),
                streamHalfWidth(state.streamMbPerTick), sprite, tint,
                GooStreamRenderer.flowPhase(state.animationTime));
    }

    /**
     * Emits the splat a pouring stream keeps where it lands: one flat,
     * upward-facing quad centered on the landing point, lifted the drip's
     * surface margin off the surface, widening with the stream, pulsing with
     * the animation time and drifting across the fluid sprite so it reads as
     * continuously refreshing; a tap pouring no stream emits nothing
     * (decision stream-leaves-a-live-splat).
     *
     * @param ctx    the render context
     * @param state  the tap render state
     * @param sprite the goo type's fluid sprite
     * @param color  the muted color the tap's drip particles draw under
     */
    static void emitSplat(RenderContext ctx, TapRenderState state, TextureAtlasSprite sprite, int color) {
        if (state.streamType == null) {
            return;
        }
        float halfWidth = splatHalfWidth(state.streamMbPerTick, state.animationTime);
        float y = state.streamBottomY + (float) DripQuadPlacement.SURFACE_MARGIN;
        float span = Math.min(1f, halfWidth * SPLAT_SPRITE_PER_HALF_WIDTH);
        float drift = state.animationTime * SPLAT_DRIFT_PER_TICK;
        float u0 = (drift - (float) Math.floor(drift)) * (1f - span);
        ctx.liquidSurface(color,
                new CuboidBounds(STREAM_CENTER - halfWidth, STREAM_CENTER + halfWidth,
                        STREAM_CENTER - halfWidth, STREAM_CENTER + halfWidth, y, y),
                GooSubmitter.spriteSubRect(sprite, u0, u0, u0 + span, u0 + span));
    }

    /**
     * @param mbPerTick     the mB the tap pours a tick
     * @param animationTime game time plus partial tick
     * @return the splat's half-width, a spread of the stream's pulsing over time
     */
    static float splatHalfWidth(int mbPerTick, float animationTime) {
        float pulse = 1f + SPLAT_PULSE_AMPLITUDE * (float) Math.sin(animationTime * SPLAT_PULSE_FREQUENCY);
        return streamHalfWidth(mbPerTick) * SPLAT_SPREAD * pulse;
    }

    /**
     * @param mbPerTick the mB the tap pours a tick
     * @return the stream's half-width, a trickle at 1 mB and twice as wide at 4
     */
    static float streamHalfWidth(int mbPerTick) {
        return TRICKLE_HW * (float) Math.sqrt(Math.max(1, mbPerTick));
    }

    /**
     * Submits the stream and its splat through the shared submitter,
     * fullbright like every goo fluid (decision submitter-owns-render-choices).
     *
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param state         the tap render state
     */
    private static void submitStream(PoseStack poseStack, SubmitNodeCollector nodeCollector, TapRenderState state) {
        ResourceKey<GooTypeDefinition> type = state.streamType;
        if (type == null) {
            return;
        }
        TextureAtlasSprite sprite = GooSubmitter.fluidSprite(type);
        int tint = GooSubmitter.fluidTint(type);
        int splatColor = TapDripLook.mute(tint, ClientGooTypes.color(type));
        GooSubmitter.submitFluid(poseStack, nodeCollector, ctx -> {
            emitStream(ctx, state, sprite, tint);
            emitSplat(ctx, state, sprite, splatColor);
        });
    }

    @Override
    public TapRenderState createRenderState() {
        return new TapRenderState();
    }

    /**
     * Snapshots canister presence and fluid data from the block entity. The
     * tap draws its canister with copper caps on both ends whatever gaskets the
     * canister carries: the tap's own gasket is the block-level one, so the
     * slot never reports a cap gasket.
     *
     * @param be            the block entity instance
     * @param state         the block state
     * @param partialTick   the partial tick for interpolation
     * @param cameraPos     the camera world position
     * @param breakProgress the crumbling overlay, or null
     */
    @Override
    public void extractRenderState(TapBlockEntity be, TapRenderState state, float partialTick,
                                   Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(be, state, breakProgress);
        state.facing = be.getBlockState().getValue(TapBlock.FACING);
        state.slot.present = !be.getCanister().isEmpty();
        state.slot.topGasketPresent = false;
        state.slot.bottomGasketPresent = false;
        if (state.slot.present) {
            extractCanisterContents(be.getCanister(), state);
        } else {
            clearContents(state);
        }
        TapStream stream = be.pourStream();
        state.streamType = stream == null ? null : stream.type();
        state.streamBottomY = stream == null ? 0f : (float) (stream.surfaceY() - be.getBlockPos().getY());
        state.streamMbPerTick = stream == null ? 0 : stream.mbPerTick();
        state.animationTime = be.getLevel() == null ? 0f : be.getLevel().getGameTime() + partialTick;
    }

    /**
     * Stretches the tap's culling box down to its stream's landing, so the
     * stream stays drawn while the tap itself is out of view.
     *
     * @param be the tap block entity
     * @return the box the tap and its stream fill
     */
    @Override
    public AABB getRenderBoundingBox(TapBlockEntity be) {
        AABB block = new AABB(be.getBlockPos());
        TapStream stream = be.pourStream();
        return stream == null ? block : block.minmax(new AABB(block.minX, stream.surfaceY(), block.minZ,
                block.maxX, block.minY, block.maxZ));
    }

    /**
     * Submits the canister's body, caps and fluid if a canister is present.
     *
     * @param state         the block state
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param cameraState   the camera render state
     */
    @Override
    public void submit(TapRenderState state, PoseStack poseStack,
                       SubmitNodeCollector nodeCollector, CameraRenderState cameraState) {
        submitStream(poseStack, nodeCollector, state);
        if (!state.slot.present) {
            return;
        }
        AABB sb = TapBlock.canisterSlotShape(state.facing).bounds();
        float[][] center = {{
            (float) ((sb.minX + sb.maxX) / CENTER_DIVISOR),
            (float) ((sb.minZ + sb.maxZ) / CENTER_DIVISOR)}};
        SlotState[] slots = {state.slot};
        CanisterGeometry geometry = state.canisterGeometry();
        CanisterSlotRenderer.submitBodies(poseStack, nodeCollector, state.lightCoords, geometry, slots, center);
        CanisterSlotRenderer.submitCaps(poseStack, nodeCollector, state.lightCoords, geometry, slots, center);
        CanisterSlotRenderer.submitFluids(poseStack, nodeCollector, geometry, slots, center, false);
    }
}
