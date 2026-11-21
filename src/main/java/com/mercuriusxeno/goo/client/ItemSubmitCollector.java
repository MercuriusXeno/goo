package com.mercuriusxeno.goo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * A SubmitNodeCollector an item render state submits into, which keeps the features an
 * item model submits for a subclass to draw its own way and drops every other feature.
 * A subclass answers submitItem and submitCustomGeometry; submitModel drops unless it answers it too.
 */
public abstract class ItemSubmitCollector implements SubmitNodeCollector {

    /**
     * Returns this collector, since an item's features draw in one order.
     */
    @Override
    public OrderedSubmitNodeCollector order(int order) {
        return this;
    }

    @Override
    public <S> void submitModel(Model<? super S> m, S s, PoseStack p, RenderType r,
                                int l, int o, int t, @Nullable UvMapping sp, int oc) {
    }

    @Override
    public void submitShadow(PoseStack p, float r, List<EntityRenderState.ShadowPiece> s) {
    }

    @Override
    public void submitNameTag(PoseStack p, @Nullable Vec3 a, int o, Component n,
                              boolean s, int l, CameraRenderState c) {
    }

    @Override
    public void submitText(PoseStack p, float x, float y, FormattedCharSequence s,
                           boolean d, Font.DisplayMode m, int l, int c, int bg, int o) {
    }

    @Override
    public void submitTextBackground(PoseStack p, float x0, float y0, float x1, float y1, int c,
                                     Font.DisplayMode m, int l) {
    }

    @Override
    public <S> void submitCrumblingOverlay(Model<? super S> m, S s, PoseStack p, RenderType r, int l, int o, int t,
                                           ModelFeatureRenderer.CrumblingOverlay c) {
    }

    @Override
    public void submitFlame(PoseStack p, EntityRenderState s, Quaternionf r) {
    }

    @Override
    public void submitLeash(PoseStack p, EntityRenderState.LeashState s) {
    }

    @Override
    public void submitModelPart(ModelPart m, PoseStack p, RenderType r, int l, int o,
                                @Nullable UvMapping sp, int t,
                                int oc) {
    }

    @Override
    public void submitMovingBlock(PoseStack p, MovingBlockRenderState s, int oc) {
    }

    @Override
    public void submitBlockModel(PoseStack p, RenderType r,
                                 List<BlockStateModelPart> parts, int[] t, int l, int o, int oc) {
    }

    @Override
    public void submitBreakingBlockModel(PoseStack p, List<BlockStateModelPart> parts, int pr,
                                         boolean isBlockTranslucent) {
    }

    @Override
    public void submitShapeOutline(PoseStack p, VoxelShape s, RenderType r, int c, float w, boolean a) {
    }

    @Override
    public void submitQuadParticleGroup(QuadParticleRenderState particles) {
    }

    @Override
    public void submitGizmoPrimitives(DrawableGizmoPrimitives.Group g, CameraRenderState c, boolean top) {
    }
}
