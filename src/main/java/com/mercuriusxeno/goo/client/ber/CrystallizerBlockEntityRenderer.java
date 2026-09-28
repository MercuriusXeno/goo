package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooColors;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.block.crystallizer.CrystalCluster;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerBlock;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerBlockEntity;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.GooTypeSprites;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.registry.GooEnchantments;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Renders the two canisters standing on the crystallizer's top, back left and
 * back right, and the quartz cluster growing from its purple spot in the growing
 * type's own fluid texture (decision crystallizer-emits-chrysm), all turned with
 * its facing.
 */
public class CrystallizerBlockEntityRenderer
        implements BlockEntityRenderer<CrystallizerBlockEntity, CrystallizerRenderState> {

    private static final float BLOCK_CENTER = 0.5f;

    /** The slots' centers in model space, the dial on the south face: back left then back right. */
    private static final float[][] SLOT_CENTERS = {{5f / 16f, 5f / 16f}, {11f / 16f, 5f / 16f}};

    /** The cluster's alpha: a little see-through, as quartz is. */
    private static final int CRYSTAL_ALPHA = 0xE0;
    private static final int SIDES = 6;
    private static final double SIDE_ANGLE = Math.PI * 2 / SIDES;
    private static final double PIXEL = 1.0 / 16.0;


    /**
     * @param context the renderer provider context
     */
    public CrystallizerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public CrystallizerRenderState createRenderState() {
        return new CrystallizerRenderState();
    }

    @Override
    public void extractRenderState(CrystallizerBlockEntity be, CrystallizerRenderState state,
            float partialTick, Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(be, state, breakProgress);
        state.facing = be.getBlockState().getValue(CrystallizerBlock.FACING);
        if (be.getLevel() != null) {
            state.lightCoords = LevelRenderer.getLightCoords(be.getLevel(), be.getBlockPos().above());
        }
        for (int slot = 0; slot < state.slots.length; slot++) {
            extractSlot(be.getCanister(slot), state.slots[slot]);
        }
        state.crystallized = be.crystallized();
        extractCrystal(be, state);
    }

    /**
     * Reads the growing type's fluid sprite and tint: the sprite the type names,
     * drawn untinted, or the grey base tinted by the type's color, as its fluid draws.
     *
     * @param be    the block entity
     * @param state the render state
     */
    private static void extractCrystal(CrystallizerBlockEntity be, CrystallizerRenderState state) {
        ResourceKey<GooTypeDefinition> type = be.formingType();
        if (type == null || be.getLevel() == null) {
            state.crystalUv = null;
            return;
        }
        GooTypeSprites.FluidSprites sprites = GooSubmitter.fluidSprites(type);
        state.crystalUv = GooSubmitter.spriteUv(GooSubmitter.blockSprite(sprites.still()));
        int rgb = sprites.tinted() ? GooColors.get(be.getLevel().registryAccess(), type) : GooRenderUtil.OPAQUE_WHITE;
        state.crystalColor = ARGB.color(CRYSTAL_ALPHA, rgb);
    }

    /**
     * Reads presence, compression, gasket caps and goo fill from one canister.
     *
     * @param canister the canister stack, or EMPTY
     * @param slot     the slot snapshot to fill
     */
    private static void extractSlot(ItemStack canister, SlotState slot) {
        slot.present = !canister.isEmpty();
        slot.type = null;
        slot.fill = 0f;
        slot.topGasketPresent = false;
        slot.bottomGasketPresent = false;
        if (!slot.present) {
            return;
        }
        slot.compression = GooEnchantments.getCompressionLevel(canister);
        CanisterMetadata meta = CanisterItem.getMetadata(canister);
        slot.topGasketPresent = meta.topGasketId() != null;
        slot.bottomGasketPresent = meta.bottomGasketId() != null;
        CanisterFluidContent content = CanisterItem.getFluidContent(canister);
        if (!content.isEmpty()) {
            slot.type = content.getGooType();
            slot.fill = Math.min(1f, (float) content.amount() / ContainerCapacity.canisterCapacity(slot.compression));
        }
    }

    @Override
    public void submit(CrystallizerRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodeCollector, CameraRenderState cameraState) {
        poseStack.pushPose();
        rotateToFacing(poseStack, state.facing);
        CanisterSlotRenderer.submitBodies(poseStack, nodeCollector, state.lightCoords, state.canisterGeometry(),
                state.slots, SLOT_CENTERS);
        CanisterSlotRenderer.submitCaps(poseStack, nodeCollector, state.lightCoords, state.canisterGeometry(),
                state.slots, SLOT_CENTERS);
        CanisterSlotRenderer.submitFluids(poseStack, nodeCollector, state.canisterGeometry(), state.slots,
                SLOT_CENTERS, false);
        submitCrystal(state, poseStack, nodeCollector);
        poseStack.popPose();
    }

    /**
     * Submits the quartz cluster: each prism a hexagonal column capped by a pointed tip.
     *
     * @param state         the render state
     * @param poseStack     the pose stack, turned to the facing
     * @param nodeCollector the node collector
     */
    private static void submitCrystal(CrystallizerRenderState state, PoseStack poseStack,
                                      SubmitNodeCollector nodeCollector) {
        List<CrystalCluster.Prism> prisms = CrystalCluster.prisms(state.crystallized);
        GooRenderUtil.UvRect uv = state.crystalUv;
        if (prisms.isEmpty() || uv == null) {
            return;
        }
        int light = state.lightCoords;
        int color = state.crystalColor;
        float[][] corners = quadUv(uv);
        nodeCollector.submitCustomGeometry(poseStack, GooSubmitter.renderType(), (pose, c) -> {
            RenderContext ctx = new RenderContext(pose, c, light);
            for (CrystalCluster.Prism prism : prisms) {
                emitPrism(ctx, prism, color, corners);
            }
        });
    }

    /**
     * Emits one prism's six sides and its six tip faces, in model pixels scaled to the block.
     *
     * @param ctx   the render context
     * @param prism the prism
     * @param color the tint
     * @param uv    the sprite UV at each quad corner, from {@link #quadUv}
     */
    private static void emitPrism(RenderContext ctx, CrystalCluster.Prism prism, int color, float[][] uv) {
        double tilt = Math.toRadians(prism.tilt());
        double yaw = Math.toRadians(prism.yaw());
        Vec3 axis = new Vec3(Math.sin(tilt) * Math.sin(yaw), Math.cos(tilt), Math.sin(tilt) * Math.cos(yaw));
        Vec3 across = tilt == 0 ? new Vec3(1, 0, 0) : axis.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 along = axis.cross(across);
        Vec3 base = new Vec3(CrystalCluster.BASE_X, CrystalCluster.BASE_Y, CrystalCluster.BASE_Z);
        Vec3 tip = base.add(axis.scale(prism.length()));
        Vec3 shaft = axis.scale(prism.length() - prism.tipLength());
        Vec3[] bottom = new Vec3[SIDES];
        Vec3[] top = new Vec3[SIDES];
        for (int k = 0; k < SIDES; k++) {
            Vec3 rim = across.scale(Math.cos(k * SIDE_ANGLE) * prism.radius())
                    .add(along.scale(Math.sin(k * SIDE_ANGLE) * prism.radius()));
            bottom[k] = base.add(rim);
            top[k] = bottom[k].add(shaft);
        }
        for (int k = 0; k < SIDES; k++) {
            int next = (k + 1) % SIDES;
            emitQuad(ctx, color, uv, new Vec3[] {bottom[k], bottom[next], top[next], top[k]});
            emitQuad(ctx, color, uv, new Vec3[] {top[k], top[next], tip, tip});
        }
    }

    /**
     * Emits one quad, its corners in model pixels, lit by its own normal.
     *
     * @param ctx     the render context
     * @param color   the tint
     * @param uv      the sprite UV at each corner, from {@link #quadUv}
     * @param corners the four corners in winding order
     */
    private static void emitQuad(RenderContext ctx, int color, float[][] uv, Vec3[] corners) {
        Vec3 normal = corners[1].subtract(corners[0]).cross(corners[corners.length - 1].subtract(corners[0])).normalize();
        for (int i = 0; i < corners.length; i++) {
            Vec3 corner = corners[i].scale(PIXEL);
            ctx.vertexColored(color, (float) corner.x, (float) corner.y, (float) corner.z, uv[i][0], uv[i][1],
                    (float) normal.x, (float) normal.y, (float) normal.z);
        }
    }

    /**
     * Turns the south-facing model space to the block's facing, as the blockstate's y rotation does.
     *
     * @param poseStack the pose stack
     * @param facing    the face the dial sits on
     */
    private static void rotateToFacing(PoseStack poseStack, Direction facing) {
        poseStack.translate(BLOCK_CENTER, 0, BLOCK_CENTER);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.translate(-BLOCK_CENTER, 0, -BLOCK_CENTER);
    }

    /**
     * Maps a sprite's rectangle onto a quad's corners in winding order: bottom left,
     * bottom right, top right, top left, so every face shows the whole sprite upright.
     *
     * @param uv the sprite's rectangle on the atlas
     * @return {u, v} for each of the four corners
     */
    static float[][] quadUv(GooRenderUtil.UvRect uv) {
        return new float[][] {{uv.u0(), uv.v1()}, {uv.u1(), uv.v1()}, {uv.u1(), uv.v0()}, {uv.u0(), uv.v0()}};
    }
}
