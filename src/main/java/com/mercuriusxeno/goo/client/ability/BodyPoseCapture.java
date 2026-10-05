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
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * The collector an afterimage's entity renderer submits through so the
 * ripple learns where the body stands: it keeps the pose the renderer
 * gives the body model, the scale, turn and flip it applies, and draws
 * nothing; every other submission, equipment, items, shadow, name and
 * flames, is dropped, so the ripple is the body's alone.
 * Decision afterimage-is-one-shared-effect.
 */
final class BodyPoseCapture implements SubmitNodeCollector {

    private final Model<?> body;
    private @Nullable Matrix4f rootPose;
    private @Nullable Runnable poseBody;

    /**
     * @param body the entity renderer's body model
     */
    BodyPoseCapture(Model<?> body) {
        this.body = body;
    }

    /** @return the entity renderer's body model */
    Model<?> body() {
        return body;
    }

    /**
     * The body model root's pose in camera space, as the renderer placed it.
     *
     * @return the pose, or null where the renderer submitted no body
     */
    @Nullable Matrix4f rootPose() {
        return rootPose;
    }

    /**
     * Poses the shared body model for the echoed entity's state, as the
     * renderer would when it draws.
     */
    void poseBody() {
        if (poseBody != null) {
            poseBody.run();
        }
    }

    @Override
    public OrderedSubmitNodeCollector order(int order) {
        return this;
    }

    @Override
    public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType,
            int lightCoords, int overlayCoords, int tintedColor, @Nullable TextureAtlasSprite sprite, int outlineColor,
            ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
        if (model == body) {
            rootPose = new Matrix4f(poseStack.last().pose());
            poseBody = () -> model.setupAnim(state);
        }
    }

    @Override
    public void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
        // The ripple casts no shadow.
    }

    @Override
    public void submitNameTag(PoseStack poseStack, @Nullable Vec3 nameTagAttachment, int offset, Component name,
            boolean seeThrough, int lightCoords, double distanceToCameraSq, CameraRenderState camera) {
        // The ripple carries no name.
    }

    @Override
    public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence string, boolean dropShadow,
            Font.DisplayMode displayMode, int lightCoords, int color, int backgroundColor, int outlineColor) {
        // The ripple carries no text.
    }

    @Override
    public void submitFlame(PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation) {
        // The ripple does not burn.
    }

    @Override
    public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {
        // The ripple holds no leash.
    }

    @Override
    public void submitModelPart(ModelPart modelPart, PoseStack poseStack, RenderType renderType, int lightCoords,
            int overlayCoords, @Nullable TextureAtlasSprite sprite, boolean sheeted, boolean hasFoil, int tintedColor,
            ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay, int outlineColor) {
        // A loose part is equipment, which the ripple drops.
    }

    @Override
    public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState) {
        // The ripple carries no block.
    }

    @Override
    public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts,
            int[] tintLayers, int lightCoords, int overlayCoords, int outlineColor) {
        // The ripple carries no block.
    }

    @Override
    public void submitBreakingBlockModel(PoseStack poseStack, BlockStateModel model, long seed, int progress) {
        // The ripple breaks no block.
    }

    @Override
    public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords, int overlayCoords,
            int outlineColor, int[] tintLayers, List<BakedQuad> quads, ItemStackRenderState.FoilType foilType) {
        // The ripple holds no item.
    }

    @Override
    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType,
            SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer) {
        // A layer's own geometry, a goo splat among them, stays off the ripple.
    }

    @Override
    public void submitParticleGroup(SubmitNodeCollector.ParticleGroupRenderer particleGroupRenderer) {
        // The ripple spawns no particles.
    }
}
