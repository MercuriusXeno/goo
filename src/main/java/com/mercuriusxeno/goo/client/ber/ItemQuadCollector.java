package com.mercuriusxeno.goo.client.ber;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A proxy SubmitNodeCollector an item render state submits into, which re-emits the
 * item's quads as custom geometry on a render type of its choosing and drops every
 * other feature an item model can submit.
 */
abstract class ItemQuadCollector implements SubmitNodeCollector {

    /** Untinted sentinel - ARGB white means no tint modification. */
    static final int NO_TINT = -1;

    /** The real collector the re-emitted geometry goes to. */
    protected final SubmitNodeCollector delegate;

    /**
     * @param delegate the real collector to emit geometry into
     */
    ItemQuadCollector(SubmitNodeCollector delegate) {
        this.delegate = delegate;
    }

    /**
     * Sets up one quad's instance before it is emitted.
     */
    @FunctionalInterface
    interface QuadStyle {
        /**
         * @param instance the quad instance to set
         * @param tint     the quad's tint color, or {@link #NO_TINT}
         */
        void apply(QuadInstance instance, int tint);
    }

    /**
     * Emits one atlas's quads into the buffer its render type opened.
     */
    @FunctionalInterface
    interface GroupEmitter {
        /**
         * @param pose   the pose the geometry was submitted at
         * @param buffer the buffer to emit into
         * @param group  the quads on one atlas
         */
        void emit(PoseStack.Pose pose, VertexConsumer buffer, List<BakedQuad> group);
    }

    /**
     * Re-emits quads through putBakedQuad, grouped by their sprite's atlas.
     *
     * @param poseStack    the pose stack
     * @param quads        the baked quads
     * @param tintLayers   per-tint-index colors from the item color handler
     * @param renderTypeOf the render type each atlas draws on
     * @param style        sets each quad's color and coordinates
     */
    protected void resubmitByAtlas(PoseStack poseStack, List<BakedQuad> quads, int[] tintLayers,
                                   Function<Identifier, RenderType> renderTypeOf, QuadStyle style) {
        resubmitByAtlas(poseStack, quads, renderTypeOf, (pose, buffer, group) -> {
            QuadInstance instance = new QuadInstance();
            for (BakedQuad quad : group) {
                style.apply(instance, tintOf(quad, tintLayers));
                buffer.putBakedQuad(pose, quad, instance);
            }
        });
    }

    /**
     * Re-emits quads grouped by their sprite's atlas, so block-atlas and item-atlas
     * sprites each bind the right texture, one submission per atlas.
     *
     * @param poseStack    the pose stack
     * @param quads        the baked quads
     * @param renderTypeOf the render type each atlas draws on
     * @param emitter      emits one atlas's quads
     */
    protected void resubmitByAtlas(PoseStack poseStack, List<BakedQuad> quads,
                                   Function<Identifier, RenderType> renderTypeOf, GroupEmitter emitter) {
        for (Map.Entry<Identifier, List<BakedQuad>> entry : groupByAtlas(quads).entrySet()) {
            List<BakedQuad> group = entry.getValue();
            delegate.submitCustomGeometry(poseStack, renderTypeOf.apply(entry.getKey()),
                    (pose, buffer) -> emitter.emit(pose, buffer, group));
        }
    }

    /**
     * Returns a quad's tint color.
     *
     * @param quad       the baked quad
     * @param tintLayers per-tint-index colors from the item color handler
     * @return the tint color, or {@link #NO_TINT}
     */
    static int tintOf(BakedQuad quad, int[] tintLayers) {
        return resolveTint(quad.materialInfo().tintIndex(), tintLayers);
    }

    /**
     * Groups quads by their sprite's atlas location.
     *
     * @param quads the baked quads
     * @return quads grouped by atlas identifier
     */
    private static Map<Identifier, List<BakedQuad>> groupByAtlas(List<BakedQuad> quads) {
        Map<Identifier, List<BakedQuad>> map = new LinkedHashMap<>();
        for (BakedQuad quad : quads) {
            Identifier atlas = quad.materialInfo().sprite().atlasLocation();
            map.computeIfAbsent(atlas, k -> new ArrayList<>()).add(quad);
        }
        return map;
    }

    /**
     * Looks up the tint color for a quad's tint index.
     *
     * @param tintIndex  the quad's tint index, or -1 if untinted
     * @param tintLayers the color array from the item color handler
     * @return the tint color, or NO_TINT if untinted
     */
    private static int resolveTint(int tintIndex, int[] tintLayers) {
        if (tintIndex >= 0 && tintIndex < tintLayers.length) {
            return tintLayers[tintIndex];
        }
        return NO_TINT;
    }

    /**
     * Returns this collector since item rendering does not use ordering.
     */
    @Override
    public OrderedSubmitNodeCollector order(int order) {
        return this;
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
    public <S> void submitModel(Model<? super S> m, S s, PoseStack p, RenderType r,
                                int l, int o, int t, @Nullable TextureAtlasSprite sp, int oc,
                                ModelFeatureRenderer.@Nullable CrumblingOverlay c) {
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
