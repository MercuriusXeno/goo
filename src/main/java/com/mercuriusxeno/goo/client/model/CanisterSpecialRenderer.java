package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.block.canister.CanisterGeometry;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import java.util.function.Consumer;

/**
 * Special item renderer for canister items. Submits the canister body and
 * the fluid fill through {@link GooSubmitter}, which owns the render type,
 * the lightmap rule and the sprite (decision shared-submission-entry-point),
 * and draws the gasket caps on their own texture. Reads goo contents from
 * item data components.
 */
public class CanisterSpecialRenderer implements SpecialModelRenderer<CanisterSpecialRenderer.GooData> {

    /**
     * Copper endcap texture (default canister caps).
     */
    private static final Identifier COPPER_GASKET =
            Identifier.fromNamespaceAndPath("goo", "textures/block/gasket.png");

    /**
     * Choral gasket texture (upgraded canister caps).
     */
    private static final Identifier CHORAL_GASKET =
            Identifier.fromNamespaceAndPath("goo", "textures/block/choral_gasket.png");

    // -- Item-form geometry (single canister centered in block) --

    /** Center of the canister in block coordinates. */
    private static final float CENTER = 8f / 16f;

    /** Min X/Z boundary of the canister body. */
    private static final float BODY_MIN_XZ = CENTER - CanisterGeometry.HW;

    /** Max X/Z boundary of the canister body. */
    private static final float BODY_MAX_XZ = CENTER + CanisterGeometry.HW;

    /**
     * Creates a canister special renderer.
     */
    public CanisterSpecialRenderer() {
    }

    /**
     * Computes the fill fraction [0,1] for the canister's current contents.
     *
     * @param stack   the canister item stack
     * @param content the fluid content to compute fill from
     * @return fill fraction clamped to [0,1]
     */
    private static float computeFillFraction(ItemStack stack, CanisterFluidContent content) {
        int compression = com.mercuriusxeno.goo.registry.GooEnchantments.getCompressionLevel(stack);
        int capacity = ContainerCapacity.canisterCapacity(compression);
        return Math.min(1f, (float) content.amount() / capacity);
    }

    /**
     * Submits fluid geometry only when the data contains a non-empty fill.
     * The fluid takes the submitter's lightmap rule rather than the item's
     * packed light, so it reads bright in the dark as the placed canister does.
     *
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param data          the goo data, or null
     */
    private static void submitFluidIfPresent(PoseStack poseStack,
                                             SubmitNodeCollector nodeCollector,
                                             @Nullable GooData data) {
        if (data == null || data.fill() <= 0f) {
            return;
        }
        if (data.gooType() != null) {
            submitFluid(poseStack, nodeCollector, data.gooType(), data.fill());
        } else if (data.vanillaFluid() != null) {
            submitVanillaFluid(poseStack, nodeCollector, data.vanillaFluid(), data.fill());
        }
    }

    /**
     * Submits endcap geometry. Every canister always gets top and bottom caps -
     * copper by default, choral when upgraded. Two draw calls batch each texture.
     *
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param packedLight   the packed light value
     * @param data          the goo data, or null
     */
    private static void submitGaskets(PoseStack poseStack,
                                      SubmitNodeCollector nodeCollector, int packedLight,
                                      @Nullable GooData data) {
        boolean hasTopChoral = data != null && data.hasTopGasket();
        boolean hasBottomChoral = data != null && data.hasBottomGasket();
        submitCopperEndcaps(poseStack, nodeCollector, packedLight, hasTopChoral, hasBottomChoral);
        submitChoralEndcaps(poseStack, nodeCollector, packedLight, hasTopChoral, hasBottomChoral);
    }

    /**
     * Submits copper (default) endcap quads for caps without choral upgrades.
     *
     * @param poseStack       the pose stack for rendering
     * @param nodeCollector   the render node collector
     * @param packedLight     the packed light value
     * @param hasTopChoral    true if top has choral upgrade
     * @param hasBottomChoral true if bottom has choral upgrade
     */
    private static void submitCopperEndcaps(PoseStack poseStack,
                                            SubmitNodeCollector nodeCollector, int packedLight,
                                            boolean hasTopChoral, boolean hasBottomChoral) {
        boolean copperTop = !hasTopChoral;
        boolean copperBottom = !hasBottomChoral;
        if (copperTop || copperBottom) {
            submitEndcapBatch(poseStack, nodeCollector, packedLight,
                    COPPER_GASKET, copperTop, copperBottom);
        }
    }

    /**
     * Submits choral (upgraded) endcap quads for caps with choral gaskets.
     *
     * @param poseStack       the pose stack for rendering
     * @param nodeCollector   the render node collector
     * @param packedLight     the packed light value
     * @param hasTopChoral    true if top has choral upgrade
     * @param hasBottomChoral true if bottom has choral upgrade
     */
    private static void submitChoralEndcaps(PoseStack poseStack,
                                            SubmitNodeCollector nodeCollector, int packedLight,
                                            boolean hasTopChoral, boolean hasBottomChoral) {
        if (hasTopChoral || hasBottomChoral) {
            submitEndcapBatch(poseStack, nodeCollector, packedLight,
                    CHORAL_GASKET, hasTopChoral, hasBottomChoral);
        }
    }

    /**
     * Submits a single endcap draw call for the given texture and cap flags,
     * solid on the gasket texture as the placed canister's caps are.
     *
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param packedLight   the packed light value
     * @param texture       the endcap texture identifier
     * @param top           true to render top endcap
     * @param bottom        true to render bottom endcap
     */
    private static void submitEndcapBatch(PoseStack poseStack,
                                          SubmitNodeCollector nodeCollector, int packedLight,
                                          Identifier texture, boolean top, boolean bottom) {
        nodeCollector.submitCustomGeometry(poseStack,
                RenderTypes.entitySolid(texture),
                (pose, c) -> emitEndcapQuads(new RenderContext(pose, c, packedLight), top, bottom));
    }

    /**
     * Emits gasket box quads for the requested top and/or bottom endcaps.
     *
     * @param ctx    the render context
     * @param top    true to render the top endcap
     * @param bottom true to render the bottom endcap
     */
    private static void emitEndcapQuads(RenderContext ctx, boolean top, boolean bottom) {
        if (top) {
            ctx.gasketBox(new CuboidBounds(BODY_MIN_XZ, BODY_MAX_XZ, BODY_MIN_XZ, BODY_MAX_XZ,
                    CanisterGeometry.BODY_TOP, CanisterGeometry.GASKET_TOP),
                    CanisterGeometry.GS_U0, CanisterGeometry.GS_U1, CanisterGeometry.GS_V1);
        }
        if (bottom) {
            ctx.gasketBox(new CuboidBounds(BODY_MIN_XZ, BODY_MAX_XZ, BODY_MIN_XZ, BODY_MAX_XZ,
                    CanisterGeometry.GASKET_BOT, CanisterGeometry.BODY_BOT),
                    CanisterGeometry.GS_U0, CanisterGeometry.GS_U1, CanisterGeometry.GS_V1);
        }
    }

    /**
     * Submits goo fluid surface geometry inside the canister body through the
     * shared submitter: the top face and four side faces from the body bottom
     * up to the fill level, on the sprite the submitter resolves for the type.
     *
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param type          the goo type
     * @param fill          the fill fraction in [0, 1]
     */
    private static void submitFluid(PoseStack poseStack, SubmitNodeCollector nodeCollector,
                                    ResourceKey<GooTypeDefinition> type, float fill) {
        CuboidBounds b = fluidBounds(fill);
        TextureAtlasSprite sprite = GooSubmitter.fluidSprite(type);
        GooSubmitter.submitFluid(poseStack, nodeCollector,
                ctx -> FluidFaceEmitter.emitFluidFaces(ctx, b, sprite, ctx.color()));
    }

    /**
     * Submits vanilla fluid (water/lava) surface geometry inside the canister
     * body through the shared submitter, on the still sprite and tint the
     * submitter resolves for the fluid.
     *
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param fluid         the vanilla fluid
     * @param fill          the fill fraction in [0, 1]
     */
    private static void submitVanillaFluid(PoseStack poseStack, SubmitNodeCollector nodeCollector,
                                           Fluid fluid, float fill) {
        CuboidBounds b = fluidBounds(fill);
        TextureAtlasSprite sprite = GooSubmitter.fluidSprite(fluid);
        int tint = GooSubmitter.fluidTint(fluid);
        GooSubmitter.submitFluid(poseStack, nodeCollector,
                ctx -> FluidFaceEmitter.emitFluidFaces(ctx, b, sprite, tint));
    }

    /**
     * Computes the fluid cuboid inside the item-form canister body: inset from
     * the body walls, rising from the body bottom to the fill level.
     *
     * @param fill the fill fraction in [0, 1]
     * @return the fluid cuboid whose Y span encodes the fill
     */
    private static CuboidBounds fluidBounds(float fill) {
        float min = CENTER - CanisterGeometry.HW + CanisterGeometry.FLUID_INSET;
        float max = CENTER + CanisterGeometry.HW - CanisterGeometry.FLUID_INSET;
        return new CuboidBounds(min, max, min, max,
                CanisterGeometry.BODY_BOT,
                CanisterGeometry.BODY_BOT + fill * (CanisterGeometry.BODY_TOP - CanisterGeometry.BODY_BOT));
    }

    /**
     * Extracts goo render data from the canister item stack.
     *
     * @param stack the canister item stack
     * @return render data, or null if the canister is empty (no visual change)
     */
    @Override
    public @Nullable GooData extractArgument(ItemStack stack) {
        CanisterMetadata meta = CanisterItem.getMetadata(stack);
        boolean hasTop = meta.topGasketId() != null;
        boolean hasBottom = meta.bottomGasketId() != null;
        CanisterFluidContent content = CanisterItem.getFluidContent(stack);
        if (content.isEmpty()) {
            return new GooData(null, null, 0f, hasTop, hasBottom);
        }
        float fill = computeFillFraction(stack, content);
        ResourceKey<GooTypeDefinition> gooType = content.getGooType();
        Fluid vanillaFluid =
                gooType == null ? content.fluid() : null;
        return new GooData(gooType, vanillaFluid, fill, hasTop, hasBottom);
    }

    /**
     * Renders the canister body, gaskets, and fluid fill for the item. The
     * body and gaskets take the item's packed light as their world light; the
     * fluid takes the submitter's lightmap rule.
     *
     * @param data          extracted goo data (may be null for empty canisters)
     * @param poseStack     the current pose stack
     * @param nodeCollector the render node collector
     * @param packedLight   packed light value
     * @param packedOverlay packed overlay value
     * @param hasFoil       whether the item has enchantment foil
     * @param outlineColor  outline color for selected items
     */
    @Override
    public void submit(@Nullable GooData data,
                       PoseStack poseStack, SubmitNodeCollector nodeCollector,
                       int packedLight, int packedOverlay, boolean hasFoil, int outlineColor) {
        poseStack.pushPose();

        GooSubmitter.submitCanisterBody(poseStack, nodeCollector, packedLight);
        submitGaskets(poseStack, nodeCollector, packedLight, data);
        submitFluidIfPresent(poseStack, nodeCollector, data);

        poseStack.popPose();
    }

    /**
     * Reports the geometric extents of the canister for GUI rendering.
     * Covers the full canister volume: 4px wide, 12px tall, 4px deep,
     * centered at (8, 6, 8) in block coordinates.
     *
     * @param output consumer for extent corner vertices
     */
    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        float x0 = CENTER - CanisterGeometry.HW;
        float x1 = CENTER + CanisterGeometry.HW;
        float z0 = CENTER - CanisterGeometry.HW;
        float z1 = CENTER + CanisterGeometry.HW;
        output.accept(new Vector3f(x0, CanisterGeometry.GASKET_BOT, z0));
        output.accept(new Vector3f(x1, CanisterGeometry.GASKET_TOP, z1));
    }

    /**
     * Extracted render data from the canister item stack. For goo fluids,
     * gooType is set. For vanilla fluids (water/lava), vanillaFluid is set.
     *
     * @param gooType         the goo type, or null for vanilla/empty
     * @param vanillaFluid    the vanilla fluid, or null for goo/empty
     * @param fill            the fill fraction [0, 1]
     * @param hasTopGasket    whether a choral gasket is installed on top
     * @param hasBottomGasket whether a choral gasket is installed on bottom
     */
    public record GooData(@Nullable ResourceKey<GooTypeDefinition> gooType,
                          @Nullable Fluid vanillaFluid,
                          float fill, boolean hasTopGasket, boolean hasBottomGasket) {
    }

    /**
     * Unbaked factory for the canister special renderer. Registered as
     * "goo:canister_goo" in the special model renderer registry.
     */
    public record Unbaked() implements SpecialModelRenderer.Unbaked<GooData> {

        /**
         * Codec for data-driven item model deserialization.
         */
        public static final MapCodec<CanisterSpecialRenderer.Unbaked> MAP_CODEC =
                MapCodec.unit(new CanisterSpecialRenderer.Unbaked());

        @Override
        public MapCodec<CanisterSpecialRenderer.Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<GooData> bake(SpecialModelRenderer.BakingContext context) {
            return new CanisterSpecialRenderer();
        }
    }
}
