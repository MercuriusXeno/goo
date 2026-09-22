package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.client.model.CanisterBodyModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import java.util.function.Consumer;

/**
 * The one entry point every canister body and goo-fluid surface submits
 * through (decision shared-submission-entry-point). It alone holds the
 * block atlas, the render type, the lightmap rule and the sprite lookup:
 * bodies and fluids share entityTranslucent on the block atlas so one
 * buffer holds both and sortOnUpload orders them; body vertices carry the
 * block entity's world light while fluid vertices carry fullbright, which
 * makes the lightmap multiply a no-op without a shader or pipeline change.
 */
public final class GooSubmitter {

    /** The block atlas every body and fluid sprite is stitched into. */
    private static final Identifier BLOCK_ATLAS =
        Identifier.withDefaultNamespace("textures/atlas/blocks.png");

    /** Mod namespace for goo sprite identifiers. */
    private static final String NAMESPACE = "goo";

    /** Vanilla water still sprite in the block atlas. */
    private static final Identifier WATER_STILL = Identifier.withDefaultNamespace("block/water_still");
    /** Vanilla lava still sprite in the block atlas. */
    private static final Identifier LAVA_STILL = Identifier.withDefaultNamespace("block/lava_still");
    /** Water tint: the plains biome blue. */
    private static final int WATER_TINT = 0xFF3F76E4;

    /** Canister body side sprite on the block atlas. */
    private static final Identifier CANISTER_SIDE =
        Identifier.fromNamespaceAndPath(NAMESPACE, "block/canister_side");
    /** Body side U extent of canister_side.png: 4px of 16. */
    private static final float BODY_SIDE_U1 = 0.25f;
    /** Body side V extent of canister_side.png: 10px of 16. */
    private static final float BODY_SIDE_V1 = 0.625f;

    private GooSubmitter() {
    }

    /**
     * Returns the render type bodies and fluids share, for a draw call that
     * must land in the same buffer as them.
     *
     * @return entityTranslucent on the block atlas
     */
    public static RenderType renderType() {
        return translucentOn(BLOCK_ATLAS);
    }

    /**
     * Returns the translucent render type on a standalone texture, for a
     * draw that is neither a body nor a fluid but must stay translucent:
     * the reactor wheel, or a batched translucent item texture. The choice
     * still lives here so no renderer names a render type of its own.
     *
     * @param texture the texture the draw samples
     * @return entityTranslucent on that texture
     */
    public static RenderType translucentOn(Identifier texture) {
        return RenderTypes.entityTranslucent(texture);
    }

    /**
     * Submits body geometry at the block entity's world light.
     *
     * @param poseStack     the pose stack
     * @param nodeCollector the render node collector
     * @param worldLight    the packed light the block entity extracted
     * @param emitter       emits the body vertices through the context
     */
    public static void submitBody(PoseStack poseStack, SubmitNodeCollector nodeCollector,
                                  int worldLight, Consumer<RenderContext> emitter) {
        nodeCollector.submitCustomGeometry(poseStack, renderType(),
            (pose, c) -> emitter.accept(new RenderContext(pose, c, worldLight)));
    }

    /**
     * Submits the baked canister body model at the current pose position.
     *
     * @param poseStack     the pose stack
     * @param nodeCollector the render node collector
     * @param worldLight    the packed light the block entity extracted
     */
    public static void submitCanisterBody(PoseStack poseStack, SubmitNodeCollector nodeCollector,
                                          int worldLight) {
        submitBakedBody(poseStack, nodeCollector, worldLight, CanisterBodyModels.getModel());
    }

    /**
     * Submits a baked body model at the current pose position, every quad
     * at the caller's world light in opaque white.
     *
     * @param poseStack     the pose stack
     * @param nodeCollector the render node collector
     * @param worldLight    the packed light the caller extracted
     * @param model         the baked quad collection
     */
    public static void submitBakedBody(PoseStack poseStack, SubmitNodeCollector nodeCollector,
                                       int worldLight, QuadCollection model) {
        submitBody(poseStack, nodeCollector, worldLight, ctx -> emitBakedQuads(ctx, model));
    }

    /**
     * Submits the four side faces of each canister body box at the block
     * entity's world light, on the body sub-rect of the canister_side sprite.
     * Sides alone, because the gasket caps close each box on their own
     * texture.
     *
     * @param poseStack     the pose stack
     * @param nodeCollector the render node collector
     * @param worldLight    the packed light the block entity extracted
     * @param bodies        the body boxes to emit
     */
    public static void submitSidedBodies(PoseStack poseStack, SubmitNodeCollector nodeCollector,
                                         int worldLight, Iterable<CuboidBounds> bodies) {
        submitBody(poseStack, nodeCollector, worldLight, ctx -> {
            GooRenderUtil.UvRect uv = GooRenderUtil.spriteSubRect(
                blockSprite(CANISTER_SIDE), 0f, 0f, BODY_SIDE_U1, BODY_SIDE_V1);
            for (CuboidBounds body : bodies) {
                ctx.emitSides(body, uv);
            }
        });
    }

    /**
     * Emits every quad of a baked model at the context light in opaque white.
     *
     * @param ctx   the render context
     * @param model the baked quad collection
     */
    private static void emitBakedQuads(RenderContext ctx, QuadCollection model) {
        QuadInstance qi = new QuadInstance();
        qi.setColor(GooRenderUtil.OPAQUE_WHITE);
        qi.setLightCoords(ctx.light());
        qi.setOverlayCoords(OverlayTexture.NO_OVERLAY);
        for (BakedQuad quad : model.getAll()) {
            ctx.c().putBakedQuad(ctx.pose(), quad, qi);
        }
    }

    /**
     * Submits fluid geometry fullbright in opaque white.
     *
     * @param poseStack     the pose stack
     * @param nodeCollector the render node collector
     * @param emitter       emits the fluid vertices through the context
     */
    public static void submitFluid(PoseStack poseStack, SubmitNodeCollector nodeCollector,
                                   Consumer<RenderContext> emitter) {
        submitFluid(poseStack, nodeCollector, GooRenderUtil.OPAQUE_WHITE, emitter);
    }

    /**
     * Submits fluid geometry fullbright in the given color, so a crossfade
     * can carry its alpha into the vertex color.
     *
     * @param poseStack     the pose stack
     * @param nodeCollector the render node collector
     * @param color         the ARGB color the context's uncolored emitters use
     * @param emitter       emits the fluid vertices through the context
     */
    public static void submitFluid(PoseStack poseStack, SubmitNodeCollector nodeCollector,
                                   int color, Consumer<RenderContext> emitter) {
        nodeCollector.submitCustomGeometry(poseStack, renderType(),
            (pose, c) -> emitter.accept(
                new RenderContext(pose, c, LightCoordsUtil.FULL_BRIGHT, color)));
    }

    /**
     * Resolves the fluid sprite ids of a goo type: the still and flowing
     * sprites its JSON names, or the grey base (decision type-named-textures).
     *
     * @param type the goo type
     * @return the sprite ids and whether the grey base stands in for them
     */
    public static GooTypeSprites.FluidSprites fluidSprites(ResourceKey<GooTypeDefinition> type) {
        GooTypeDefinition definition = ClientGooTypes.definition(type);
        return GooTypeSprites.fluid(definition == null ? null : definition.textures(),
            GooSubmitter::isBlockSpriteStitched);
    }

    /**
     * Resolves the still fluid sprite of a goo type from the block atlas.
     *
     * @param type the goo type
     * @return the still sprite its JSON names, or the grey base
     */
    public static TextureAtlasSprite fluidSprite(ResourceKey<GooTypeDefinition> type) {
        return blockSprite(fluidSprites(type).still());
    }

    /**
     * Resolves the vertex tint a goo type's fluid sprite renders under.
     *
     * @param type the goo type
     * @return opaque white over a named sprite, the opaque highlight color over the grey base
     */
    public static int fluidTint(ResourceKey<GooTypeDefinition> type) {
        return fluidSprites(type).tinted() ? ARGB.opaque(ClientGooTypes.color(type)) : GooRenderUtil.OPAQUE_WHITE;
    }

    /**
     * Builds the in-world fluid model of a goo type on the sprites its JSON
     * names, or on the grey base under the type's tint.
     *
     * @param type     the goo type
     * @param greyTint the tint source the grey base renders under
     * @return a translucent fluid model on the type's sprites
     */
    public static FluidModel fluidModel(ResourceKey<GooTypeDefinition> type, FluidTintSource greyTint) {
        GooTypeSprites.FluidSprites sprites = fluidSprites(type);
        return new FluidModel(ChunkSectionLayer.TRANSLUCENT,
            new Material.Baked(blockSprite(sprites.still()), true),
            new Material.Baked(blockSprite(sprites.flowing()), true),
            null, sprites.tinted() ? greyTint : null);
    }

    /**
     * Whether a sprite id is stitched into the block atlas.
     *
     * @param spriteId the sprite identifier
     * @return false when the lookup answers the atlas's missing sprite
     */
    private static boolean isBlockSpriteStitched(Identifier spriteId) {
        TextureAtlas atlas = blockAtlas();
        return atlas.getSprite(spriteId) != atlas.missingSprite();
    }

    /**
     * Resolves the still sprite of a vanilla fluid from the block atlas:
     * water takes the water still sprite, any other fluid the lava one.
     *
     * @param fluid the vanilla fluid
     * @return the still sprite
     */
    public static TextureAtlasSprite fluidSprite(Fluid fluid) {
        return blockSprite(isWater(fluid) ? WATER_STILL : LAVA_STILL);
    }

    /**
     * Resolves the tint of a vanilla fluid: water takes the plains blue,
     * any other fluid stays white.
     *
     * @param fluid the vanilla fluid
     * @return the ARGB tint
     */
    public static int fluidTint(Fluid fluid) {
        return isWater(fluid) ? WATER_TINT : GooRenderUtil.OPAQUE_WHITE;
    }

    /**
     * Returns true for still or flowing water.
     *
     * @param fluid the fluid to test
     * @return true when the fluid is water
     */
    private static boolean isWater(Fluid fluid) {
        return fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER;
    }

    /**
     * Looks a sprite up on the block atlas.
     *
     * @param spriteId the sprite identifier
     * @return the stitched sprite
     */
    private static TextureAtlasSprite blockSprite(Identifier spriteId) {
        return blockAtlas().getSprite(spriteId);
    }

    /**
     * @return the block atlas every body and fluid sprite is stitched into
     */
    private static TextureAtlas blockAtlas() {
        return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
    }
}
