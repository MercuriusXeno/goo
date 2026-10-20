package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * Special item renderer for the goo label: a paper card with the symbol of the
 * label's goo type drawn over it, from the type icon sheet
 * (decision goo-label-is-paper-plus-one-mb-of-goo).
 */
public class GooLabelSpecialRenderer implements SpecialModelRenderer<ResourceKey<GooTypeDefinition>> {

    /** The paper card every label draws under its symbol. */
    static final Identifier PAPER_BASE = Identifier.fromNamespaceAndPath(Goo.MODID, "textures/item/goo_label.png");

    private static final String ICON_PREFIX = "textures/goo/type/";
    private static final String ICON_SUFFIX = ".png";
    private static final float PIXEL = 1f / 16f;
    private static final float CARD_FRONT = 8.5f * PIXEL;
    private static final float CARD_BACK = 7.5f * PIXEL;
    /** The symbol stands a hair off the card so the two never fight for depth. */
    private static final float SYMBOL_LIFT = 0.01f * PIXEL;
    /** The type icons are 11 px squares; the symbol sits centered on the card at that size. */
    private static final float SYMBOL_LOW = 2.5f * PIXEL;
    private static final float SYMBOL_HIGH = 13.5f * PIXEL;
    /** The north face's emitter lays u from +x to -x, so the texture reads unmirrored from the north. */
    private static final GooRenderUtil.UvRect FROM_NORTH = new GooRenderUtil.UvRect(0f, 0f, 1f, 1f);
    /** The south face's emitter lays u from -x to +x reversed, so its u runs backward to read unmirrored. */
    private static final GooRenderUtil.UvRect FROM_SOUTH = new GooRenderUtil.UvRect(1f, 0f, 0f, 1f);

    /**
     * The textures a label of the type draws, back to front: the paper base, then
     * the type's symbol.
     *
     * @param type the label's goo type
     * @return the paper base and the type's icon
     */
    static List<Identifier> layersOf(ResourceKey<GooTypeDefinition> type) {
        return List.of(PAPER_BASE, Identifier.fromNamespaceAndPath(Goo.MODID, ICON_PREFIX + GooTypes.id(type) + ICON_SUFFIX));
    }

    @Override
    public @Nullable ResourceKey<GooTypeDefinition> extractArgument(ItemStack stack) {
        return stack.get(GooDataComponents.GOO_TYPE.get());
    }

    @Override
    public void submit(@Nullable ResourceKey<GooTypeDefinition> type, PoseStack poseStack,
                       SubmitNodeCollector nodeCollector, int packedLight, int packedOverlay, boolean hasFoil,
                       int outlineColor) {
        if (type == null) {
            submitCard(poseStack, nodeCollector, packedLight, PAPER_BASE, cardBounds());
            return;
        }
        List<Identifier> layers = layersOf(type);
        submitCard(poseStack, nodeCollector, packedLight, layers.get(0), cardBounds());
        for (int i = 1; i < layers.size(); i++) {
            submitCard(poseStack, nodeCollector, packedLight, layers.get(i), symbolBounds());
        }
    }

    private static CuboidBounds cardBounds() {
        return new CuboidBounds(0f, 1f, CARD_BACK, CARD_FRONT, 0f, 1f);
    }

    private static CuboidBounds symbolBounds() {
        return new CuboidBounds(SYMBOL_LOW, SYMBOL_HIGH, CARD_BACK - SYMBOL_LIFT, CARD_FRONT + SYMBOL_LIFT,
                SYMBOL_LOW, SYMBOL_HIGH);
    }

    private static void submitCard(PoseStack poseStack, SubmitNodeCollector nodeCollector, int packedLight,
                                   Identifier texture, CuboidBounds bounds) {
        nodeCollector.submitCustomGeometry(poseStack, GooSubmitter.cutoutOn(texture), (pose, consumer) -> {
            RenderContext ctx = new RenderContext(pose, consumer, packedLight);
            ctx.emitFace(bounds, FROM_SOUTH, Direction.SOUTH);
            ctx.emitFace(bounds, FROM_NORTH, Direction.NORTH);
        });
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        output.accept(new Vector3f(0f, 0f, CARD_BACK));
        output.accept(new Vector3f(1f, 1f, CARD_FRONT));
    }

    /**
     * Unbaked factory for the label renderer, registered as "goo:goo_label".
     */
    public record Unbaked() implements SpecialModelRenderer.Unbaked<ResourceKey<GooTypeDefinition>> {

        /** Codec for the item definition's {"type": "goo:goo_label"}. */
        public static final MapCodec<GooLabelSpecialRenderer.Unbaked> MAP_CODEC =
                MapCodec.unit(new GooLabelSpecialRenderer.Unbaked());

        @Override
        public MapCodec<GooLabelSpecialRenderer.Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<ResourceKey<GooTypeDefinition>> bake(SpecialModelRenderer.BakingContext context) {
            return new GooLabelSpecialRenderer();
        }
    }
}
