package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.block.vat.VatBlockEntity;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.SurfaceAgitation;
import com.mercuriusxeno.goo.client.machine.VatStack;
import com.mercuriusxeno.goo.item.GooContents;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Renders the fluid fill level inside a vat. When vats are vertically
 * stacked, the renderer treats the stack as one contiguous tank: fluid
 * fills from the bottom-most interior floor upward, and each vat renders
 * only the slice of the unified fluid column that falls within its block.
 */
public class VatBlockEntityRenderer
        implements BlockEntityRenderer<VatBlockEntity, VatRenderState> {

    /**
     * Interior floor for a bottom/solo vat (above the base cap, 2/16).
     */
    static final float BASE_FLOOR = 2f / 16f;
    /**
     * Interior ceiling for a top/solo vat (below the top cap, 14/16).
     */
    static final float CAP_CEILING = 14f / 16f;
    /**
     * Vat center X/Z for stream rendering.
     */
    private static final float VAT_CENTER_X = 0.5f;
    private static final float VAT_CENTER_Z = 0.5f;
    /**
     * Epsilon threshold for full-submersion check.
     */
    private static final float SUBMERSION_EPSILON = 0.0001f;

    /** Each vat's surface agitation, held client-side and dropped with the vat. */
    private final Map<VatBlockEntity, SurfaceAgitation> agitations = new WeakHashMap<>();

    /**
     * Creates a vat BER. Context is unused.
     *
     * @param context the renderer provider context
     */
    public VatBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    /**
     * Reads this vat's place in its column, the column's unified fill and the
     * top-most stream from the column VatStack walks once per tick for the HUD
     * and this renderer alike (decision one-panel-painter-takes-rows).
     *
     * @param be       the vat block entity
     * @param state    the render state snapshot to populate
     * @param gameTick the current game tick
     */
    private static void extractColumn(VatBlockEntity be, VatRenderState state, long gameTick) {
        Level level = be.getLevel();
        VatStack stack = level == null ? null : VatStack.at(level, be.getBlockPos());
        if (stack == null) {
            applyLoneVat(be, state, gameTick);
            return;
        }
        int index = stack.column().indexFromBottom(be.getBlockPos().getY());
        state.stackSize = stack.column().size();
        state.indexFromBottom = index;
        state.vatBelow = index > 0;
        state.vatAbove = index < state.stackSize - 1;
        applyFill(state, stack.contents(), stack.capacity());
        applyTopStream(state, stack.members(), gameTick);
    }

    /**
     * Reads a vat outside any walked column as a column of one.
     *
     * @param be       the vat block entity
     * @param state    the render state snapshot to populate
     * @param gameTick the current game tick
     */
    private static void applyLoneVat(VatBlockEntity be, VatRenderState state, long gameTick) {
        state.stackSize = 1;
        state.indexFromBottom = 0;
        state.vatBelow = false;
        state.vatAbove = false;
        applyFill(state, be.getContents(), be.getCapacity());
        applyTopStream(state, List.of(be), gameTick);
    }

    /**
     * Sets the dominant type and fill fraction from a column's contents and capacity.
     *
     * @param state    the render state snapshot to populate
     * @param contents the column's summed contents
     * @param capacity the column's summed capacity
     */
    private static void applyFill(VatRenderState state, GooContents contents, int capacity) {
        if (contents.isEmpty() || capacity <= 0) {
            state.dominantType = null;
            state.fillFraction = 0f;
        } else {
            state.dominantType = contents.largestType();
            state.fillFraction = Math.min(1f, (float) contents.totalVolume() / capacity);
        }
    }

    /**
     * Sets the stream from the highest member receiving one.
     *
     * @param state    the render state snapshot to populate
     * @param members  the column's block entities bottom to top, null where one is missing
     * @param gameTick the current game tick
     */
    private static void applyTopStream(VatRenderState state, List<@Nullable VatBlockEntity> members,
                                       long gameTick) {
        state.streamType = null;
        state.streamRate = 0;
        for (VatBlockEntity vat : members) {
            ResourceKey<GooTypeDefinition> type = vat == null ? null : vat.getVatStreamType(gameTick);
            if (type != null) {
                state.streamType = type;
                state.streamRate = vat.getVatStreamRate(gameTick);
            }
        }
    }

    /**
     * Submits the fluid quad(s) for this vat's slice of the unified column.
     *
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param state         the block state
     */
    private static void submitFluid(PoseStack poseStack,
                                    SubmitNodeCollector nodeCollector, VatRenderState state) {
        TextureAtlasSprite sprite = GooSubmitter.fluidSprite(state.dominantType);
        GooSubmitter.submitUndulatingFluid(poseStack, nodeCollector, GooSubmitter.fluidTint(state.dominantType),
                ctx -> VatFluidRenderer.renderFluid(ctx, sprite, state));
    }

    /**
     * Submits a fluid stream segment for this vat's slice of the stack.
     *
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param state         the block state
     */
    private static void submitStream(PoseStack poseStack,
                                     SubmitNodeCollector nodeCollector, VatRenderState state) {
        float[] sb = computeStreamBounds(state);
        if (sb.length == 0) {
            return;
        }
        int light = state.lightCoords;
        float anim = state.animationTime;
        ResourceKey<GooTypeDefinition> type = state.streamType;
        float rate = state.streamRate;
        emitStreamGeometry(poseStack, nodeCollector, light, anim, type, rate, sb[0], sb[1]);
    }

    /**
     * Submits the stream geometry draw call for the given Y range.
     *
     * @param poseStack     the current pose transformation stack
     * @param nodeCollector the render node collector for geometry submission
     * @param light         the packed light level for shading
     * @param anim          the animation tick fraction
     * @param type          the goo type for color lookup
     * @param rate          the stream flow rate for animation speed
     * @param yTop          the stream top Y coordinate
     * @param yBottom       the stream bottom Y coordinate
     */
    private static void emitStreamGeometry(PoseStack poseStack,
                                           SubmitNodeCollector nodeCollector, int light, float anim,
                                           ResourceKey<GooTypeDefinition> type, float rate, float yTop, float yBottom) {
        nodeCollector.submitCustomGeometry(poseStack, GooSubmitter.renderType(),
                (pose, c) -> GooStreamRenderer.renderStream(new RenderContext(pose, c, light),
                        VAT_CENTER_X, VAT_CENTER_Z, yTop, yBottom,
                        type, rate, anim));
    }

    /**
     * Computes stream Y bounds, or null if the vat is fully submerged or inverted.
     *
     * @param state the render state snapshot to populate
     * @return the stream Y bounds {yBot, yTop}, or empty if not visible
     */
    private static float[] computeStreamBounds(VatRenderState state) {
        float localFloor = state.vatBelow ? 0f : BASE_FLOOR;
        float localCeiling = state.vatAbove ? 1.0f : CAP_CEILING;
        float localHeight = localCeiling - localFloor;
        float localFill = VatFluidRenderer.computeLocalFill(state, localFloor, localCeiling);

        if (localFill >= localHeight - SUBMERSION_EPSILON) {
            return new float[0];
        }
        float yTop = localCeiling;
        float yBottom = localFloor + Math.max(localFill, 0f);
        if (yBottom >= yTop) {
            return new float[0];
        }
        return new float[]{yTop, yBottom};
    }

    // --- Geometry submission ---

    @Override
    public VatRenderState createRenderState() {
        return new VatRenderState();
    }

    /**
     * Snapshots dominant type, fill fraction, and stack geometry by walking
     * the connected vat column. All vats in a stack share the same dominant
     * type and fill fraction so they render one unified fluid body.
     *
     * @param be            the block entity instance
     * @param state         the block state
     * @param partialTick   the partial tick for interpolation
     * @param cameraPos     the camera world position
     * @param breakProgress the crumbling overlay, or null
     */
    @Override
    public void extractRenderState(VatBlockEntity be, VatRenderState state,
                                   float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(be, state, breakProgress);
        long gameTick = be.getLevel() != null ? be.getLevel().getGameTime() : 0L;
        state.animationTime = gameTick + partialTick;
        extractColumn(be, state, gameTick);
        extractRipple(be, state, gameTick);
    }

    /**
     * Ticks this vat's surface agitation on the stack-wide fill and the top
     * vat's stream, which every vat in the column extracted alike, so the
     * whole column agitates together (decision undulating-fluid-surface).
     *
     * @param be       the vat block entity
     * @param state    the render state, fill and stream already extracted
     * @param gameTick the current game tick
     */
    private void extractRipple(VatBlockEntity be, VatRenderState state, long gameTick) {
        state.rippleAmplitude = agitations.computeIfAbsent(be, key -> new SurfaceAgitation())
            .tick(state.fillFraction, state.streamRate, gameTick);
    }

    // --- Stream rendering ---

    /**
     * Submits fluid geometry and stream if the vat contains goo or is receiving.
     *
     * @param state         the block state
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param cameraState   the camera render state
     */
    @Override
    public void submit(VatRenderState state, PoseStack poseStack,
                       SubmitNodeCollector nodeCollector, CameraRenderState cameraState) {
        if (state.dominantType != null && state.fillFraction > 0f) {
            submitFluid(poseStack, nodeCollector, state);
        }
        if (state.streamType != null) {
            submitStream(poseStack, nodeCollector, state);
        }
    }
}
