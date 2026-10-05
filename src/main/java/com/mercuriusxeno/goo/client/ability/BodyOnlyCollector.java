package com.mercuriusxeno.goo.client.ability;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * A collector an entity renderer submits a frozen render state through when
 * an effect wants the entity's body alone: the body model's submission
 * reaches onBody, posed as the renderer posed it, and every other
 * submission, equipment, held items, shadow, name, flames and layers'
 * geometry, is dropped. The ripple reads the body's pose through it and the
 * ghost trail redraws the body through it.
 * Decisions afterimage-is-one-shared-effect, ghost-trail-spans-the-blink.
 */
abstract class BodyOnlyCollector implements SubmitNodeCollector {

    private final Model<?> body;

    /**
     * @param body the entity renderer's body model
     */
    BodyOnlyCollector(Model<?> body) {
        this.body = body;
    }

    /** @return the entity renderer's body model */
    final Model<?> body() {
        return body;
    }

    /**
     * Takes the body model's submission.
     *
     * @param model       the body model
     * @param state       the entity's render state
     * @param poseStack   the pose the renderer gives the body
     * @param lightCoords the entity's light
     * @param <S>         the render state type
     */
    abstract <S> void onBody(Model<? super S> model, S state, PoseStack poseStack, int lightCoords);

    @Override
    public final OrderedSubmitNodeCollector order(int order) {
        return this;
    }

    @Override
    public final <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType,
            int lightCoords, int overlayCoords, int tintedColor, @Nullable TextureAtlasSprite sprite, int outlineColor,
            ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
        if (model == body) {
            onBody(model, state, poseStack, lightCoords);
        }
    }

    @Override
    public final void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
        // The body alone casts no shadow.
    }

    @Override
    public final void submitNameTag(PoseStack poseStack, @Nullable Vec3 nameTagAttachment, int offset, Component name,
            boolean seeThrough, int lightCoords, double distanceToCameraSq, CameraRenderState camera) {
        // The body alone carries no name.
    }

    @Override
    public final void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence string,
            boolean dropShadow, Font.DisplayMode displayMode, int lightCoords, int color, int backgroundColor,
            int outlineColor) {
        // The body alone carries no text.
    }

    @Override
    public final void submitFlame(PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation) {
        // The body alone does not burn.
    }

    @Override
    public final void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {
        // The body alone holds no leash.
    }

    @Override
    public final void submitModelPart(ModelPart modelPart, PoseStack poseStack, RenderType renderType,
            int lightCoords, int overlayCoords, @Nullable TextureAtlasSprite sprite, boolean sheeted, boolean hasFoil,
            int tintedColor, ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay, int outlineColor) {
        // A loose part is equipment, which the body alone drops.
    }

    @Override
    public final void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState) {
        // The body alone carries no block.
    }

    @Override
    public final void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts,
            int[] tintLayers, int lightCoords, int overlayCoords, int outlineColor) {
        // The body alone carries no block.
    }

    @Override
    public final void submitBreakingBlockModel(PoseStack poseStack, BlockStateModel model, long seed, int progress) {
        // The body alone breaks no block.
    }

    @Override
    public final void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords,
            int overlayCoords, int outlineColor, int[] tintLayers, List<BakedQuad> quads,
            ItemStackRenderState.FoilType foilType) {
        // The body alone holds no item.
    }

    @Override
    public final void submitCustomGeometry(PoseStack poseStack, RenderType renderType,
            SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer) {
        // A layer's own geometry, a goo splat among them, stays off the body alone.
    }

    @Override
    public final void submitParticleGroup(SubmitNodeCollector.ParticleGroupRenderer particleGroupRenderer) {
        // The body alone spawns no particles.
    }
}
