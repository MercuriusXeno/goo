package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.item.GhostItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import it.unimi.dsi.fastutil.ints.IntList;

/**
 * The ghost item's model: the model of the item the ghost stands for, drawn translucent,
 * so the hand shows the item it stands for.
 * decision ghost-is-one-item-type-whose-entity-self-destroys
 */
public final class GhostItemModel implements ItemModel {

    /** The alpha a ghost draws its item at, out of 255. */
    static final int GHOST_ALPHA = 0x80;
    private static final int OPAQUE = 0xFF;
    private static final int ALPHA_SHIFT = 24;
    private static final int RGB_MASK = 0xFFFFFF;
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;

    private static final GhostItemModel INSTANCE = new GhostItemModel();

    /** Draws the represented item's faded layers in place of the ghost's one layer. */
    static final SpecialModelRenderer<ItemStackRenderState> FADED_LAYERS = new FadedLayersRenderer();

    /**
     * Resolves the represented item's model into a render state of its own, fades its quads, and
     * draws that state as the ghost's one layer.
     */
    @Override
    public void update(ItemStackRenderState output, ItemStack item, ItemModelResolver resolver,
                       ItemDisplayContext displayContext, @Nullable ClientLevel level, @Nullable ItemOwner owner,
                       int seed) {
        Identifier represented = GhostItem.represented(item);
        if (represented == null) {
            return;
        }
        // The GUI caches an icon by these elements, so each represented item caches apart.
        output.appendModelIdentityElement(this);
        output.appendModelIdentityElement(represented);
        LayerRecordingState layers = new LayerRecordingState();
        resolver.updateForTopItem(layers, GhostItem.representedStack(item), displayContext, level, owner, seed);
        layers.recorded.forEach(GhostItemModel::fadeLayer);
        if (layers.isAnimated()) {
            output.setAnimated();
        }
        ItemStackRenderState.LayerRenderState layer = output.newLayer();
        layer.setUsesBlockLight(layers.usesBlockLight());
        layer.setExtents(() -> extentsOf(layers));
        layer.setupSpecialModel(FADED_LAYERS, layers);
    }

    /**
     * Fades a tint to the ghost's alpha, keeping its color.
     *
     * @param argb the tint, alpha in the high byte
     * @return the tint at its alpha scaled by the ghost alpha
     */
    static int fade(int argb) {
        int alpha = (argb >>> ALPHA_SHIFT) * GHOST_ALPHA / OPAQUE;
        return alpha << ALPHA_SHIFT | argb & RGB_MASK;
    }

    /**
     * Fades every quad of a layer: each draws translucent, a tinted quad through its own tint faded,
     * an untinted one through one faded white tint appended after the layer's tints.
     *
     * @param layer the layer the represented item's model filled
     */
    private static void fadeLayer(ItemStackRenderState.LayerRenderState layer) {
        IntList tints = layer.tintLayers();
        for (int i = 0; i < tints.size(); i++) {
            tints.set(i, fade(tints.getInt(i)));
        }
        int untintedIndex = tints.size();
        tints.add(fade(OPAQUE_WHITE));
        List<BakedQuad> quads = layer.prepareQuadList();
        quads.replaceAll(quad -> faded(quad, untintedIndex));
    }

    private static BakedQuad faded(BakedQuad quad, int untintedIndex) {
        BakedQuad.MaterialInfo material = quad.materialInfo();
        RenderType drawn = material.itemRenderType();
        boolean blockSheet = drawn == Sheets.cutoutBlockItemSheet() || drawn == Sheets.translucentBlockItemSheet();
        RenderType translucent = blockSheet ? Sheets.translucentBlockItemSheet() : Sheets.translucentItemSheet();
        BakedQuad.MaterialInfo fadedMaterial = new BakedQuad.MaterialInfo(material.sprite(), material.layer(),
                translucent, material.isTinted() ? material.tintIndex() : untintedIndex, material.shade(),
                material.lightEmission(), material.ambientOcclusion());
        return new BakedQuad(quad.position0(), quad.position1(), quad.position2(), quad.position3(),
                quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(), quad.direction(),
                fadedMaterial, quad.bakedNormals(), quad.bakedColors());
    }

    private static Vector3fc[] extentsOf(ItemStackRenderState layers) {
        List<Vector3fc> extents = new ArrayList<>();
        layers.visitExtents(extent -> extents.add(new Vector3f(extent)));
        return extents.toArray(new Vector3fc[0]);
    }

    /** A render state remembering each layer a model adds, so the ghost can fade them. */
    private static final class LayerRecordingState extends ItemStackRenderState {
        private final List<LayerRenderState> recorded = new ArrayList<>();

        @Override
        public LayerRenderState newLayer() {
            LayerRenderState layer = super.newLayer();
            recorded.add(layer);
            return layer;
        }
    }

    /** Submits a recorded render state's layers as they stand. */
    private static final class FadedLayersRenderer implements SpecialModelRenderer<ItemStackRenderState> {
        @Override
        public void submit(@Nullable ItemStackRenderState layers, PoseStack poseStack,
                           SubmitNodeCollector submitNodeCollector, int lightCoords, int overlayCoords,
                           boolean hasFoil, int outlineColor) {
            if (layers != null) {
                layers.submit(poseStack, submitNodeCollector, lightCoords, overlayCoords, outlineColor);
            }
        }

        @Override
        public void getExtents(Consumer<Vector3fc> output) {
        }

        @Override
        public @Nullable ItemStackRenderState extractArgument(ItemStack stack) {
            return null;
        }
    }

    /**
     * Unbaked factory for the ghost item model, registered as "goo:ghost_item".
     */
    public record Unbaked() implements ItemModel.Unbaked {

        /** Codec for the item definition's {"type": "goo:ghost_item"}. */
        public static final MapCodec<GhostItemModel.Unbaked> MAP_CODEC = MapCodec.unit(new GhostItemModel.Unbaked());

        @Override
        public MapCodec<GhostItemModel.Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transformation) {
            return INSTANCE;
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
        }
    }
}
