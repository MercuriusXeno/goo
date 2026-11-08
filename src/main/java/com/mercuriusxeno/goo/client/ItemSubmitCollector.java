package com.mercuriusxeno.goo.client;

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
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
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
                                int l, int o, int t, @Nullable TextureAtlasSprite sp, int oc,
                                ModelFeatureRenderer.@Nullable CrumblingOverlay c) {
    }

    @Override
    public void submitShadow(PoseStack p, float r, List<EntityRenderState.ShadowPiece> s) {
    }

    @Override
    public void submitNameTag(PoseStack p, @Nullable Vec3 a, int o, Component n,
                              boolean s, int l, double d, CameraRenderState c) {
    }

    @Override
    public void submitText(PoseStack p, float x, float y, FormattedCharSequence s,
                           boolean d, Font.DisplayMode m, int l, int c, int bg, int o) {
    }

    @Override
    public void submitFlame(PoseStack p, EntityRenderState s, Quaternionf r) {
    }

    @Override
    public void submitLeash(PoseStack p, EntityRenderState.LeashState s) {
    }

    @Override
    public void submitModelPart(ModelPart m, PoseStack p, RenderType r, int l, int o,
                                @Nullable TextureAtlasSprite sp, boolean sh, boolean f, int t,
                                ModelFeatureRenderer.@Nullable CrumblingOverlay c, int oc) {
    }

    @Override
    public void submitMovingBlock(PoseStack p, MovingBlockRenderState s) {
    }

    @Override
    public void submitBlockModel(PoseStack p, RenderType r,
                                 List<BlockStateModelPart> parts, int[] t, int l, int o, int oc) {
    }

    @Override
    public void submitBreakingBlockModel(PoseStack p, BlockStateModel m, long s, int pr) {
    }

    @Override
    public void submitParticleGroup(ParticleGroupRenderer r) {
    }
}
