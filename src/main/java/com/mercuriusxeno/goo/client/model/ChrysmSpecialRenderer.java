package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.block.crystallizer.CrystalCluster;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.CrystalClusterSubmitter;
import com.mercuriusxeno.goo.item.ChrysmTier;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Special item renderer for the chrysm tiers (decision chrysm-tiers-fixed-and-stackable):
 * each draws as the quartz crystal the crystallizer grows, at its tier's volume, in
 * its goo type's own texture. Operator rulings: each tier fills the slot, then
 * steps past it, so each tier reads larger than the one below, and each stands
 * centered in the slot, and materia draws as a marble-like orb half the slot wide
 * (decision chrysm-tiers-in-32x-steps). The operator's drawn sizes can replace it later.
 */
public class ChrysmSpecialRenderer implements SpecialModelRenderer<ResourceKey<GooTypeDefinition>> {

    /** The item box a cluster fills: 7 px either side of its base and 14 px tall. */
    private static final double BOX_HALF_WIDTH = 7;
    private static final double BOX_HEIGHT = 14;
    /**
     * Operator rulings: each cluster tier past its fit, a chrysm slightly (1.1), a budding
     * chrysm 20% past the fit twice over (1.44) and a flowering chrysm three times (1.728).
     */
    private static final double[] TIER_SCALES = {1.1, 1.44, 1.728};
    /** The materia orb's drawn height: half the 16 pixel slot (operator ruling). */
    private static final double ORB_HEIGHT = 8;
    /** The item box's middle height, in pixels. */
    private static final double BOX_MIDDLE = 8;
    private static final double HALF = 0.5;
    private static final float CENTER = 0.5f;
    private static final float PIXEL = 1f / 16f;
    private static final float EXTENT_LOW = 0.1f;
    private static final float EXTENT_HIGH = 0.9f;
    private static final float EXTENT_TOP = 0.7f;

    private final ChrysmTier tier;

    /**
     * @param tier the tier whose crystal this renderer draws
     */
    public ChrysmSpecialRenderer(ChrysmTier tier) {
        this.tier = tier;
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
            return;
        }
        poseStack.pushPose();
        float scale = tierScale(tier);
        poseStack.translate(CENTER, baseHeight(tier, scale) * PIXEL, CENTER);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-CrystalCluster.BASE_X * PIXEL, -CrystalCluster.BASE_Y * PIXEL,
                -CrystalCluster.BASE_Z * PIXEL);
        CrystalClusterSubmitter.submit(poseStack, nodeCollector, CrystalCluster.prisms(tier.volume()),
                CrystalClusterSubmitter.lookOf(type, ClientGooTypes.color(type)), packedLight);
        poseStack.popPose();
    }

    /**
     * Operator ruling: materia draws as a marble-like orb rather than a cluster, the
     * marble a materia's crystal compresses into.
     *
     * @param tier the tier
     * @return true when the tier draws as the orb
     */
    static boolean drawsAsOrb(ChrysmTier tier) {
        return tier == ChrysmTier.MATERIA;
    }

    /**
     * The tier's drawn scale: a cluster's fit to the item box, stepped past it by its
     * ruled share, so each cluster tier reads larger than the one below; the orb draws
     * at its own size.
     *
     * @param tier the tier
     * @return the scale
     */
    static float tierScale(ChrysmTier tier) {
        if (drawsAsOrb(tier)) {
            return (float) (ORB_HEIGHT / drawnHeight(tier));
        }
        return (float) (fitScale(CrystalCluster.reach(tier.volume())) * TIER_SCALES[tier.ordinal()]);
    }

    /**
     * @param tier the tier
     * @return the unscaled height of what the tier draws, in pixels
     */
    static double drawnHeight(ChrysmTier tier) {
        return CrystalCluster.reach(tier.volume())[1];
    }

    /**
     * Where the crystal's base stands so its scaled height is centered in the item box
     * (operator ruling: the larger tiers sat high, the largest touching the top).
     *
     * @param tier  the tier
     * @param scale the tier's drawn scale
     * @return the base's height, in pixels
     */
    static float baseHeight(ChrysmTier tier, float scale) {
        return (float) (BOX_MIDDLE - drawnHeight(tier) * scale * HALF);
    }

    /**
     * The scale that brings a cluster to fill the item box, its widest reach or its
     * height touching the box's edge.
     *
     * @param reach the cluster's {half width, height}, in pixels
     * @return the scale
     */
    static float fitScale(double[] reach) {
        return (float) Math.min(BOX_HALF_WIDTH / reach[0], BOX_HEIGHT / reach[1]);
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        output.accept(new Vector3f(EXTENT_LOW, 0, EXTENT_LOW));
        output.accept(new Vector3f(EXTENT_HIGH, EXTENT_TOP, EXTENT_HIGH));
    }

    /**
     * Unbaked factory for the chrysm renderer, registered as "goo:chrysm_crystal"
     * and naming its tier in the item definition.
     *
     * @param tier the tier the item draws
     */
    public record Unbaked(ChrysmTier tier) implements SpecialModelRenderer.Unbaked<ResourceKey<GooTypeDefinition>> {

        private static final Codec<ChrysmTier> TIER_CODEC = Codec.STRING.xmap(
                name -> ChrysmTier.valueOf(name.toUpperCase(Locale.ROOT)),
                tier -> tier.name().toLowerCase(Locale.ROOT));

        /** Codec for the item definition's {"type": "goo:chrysm_crystal", "tier": ...}. */
        public static final MapCodec<ChrysmSpecialRenderer.Unbaked> MAP_CODEC =
                TIER_CODEC.fieldOf("tier").xmap(ChrysmSpecialRenderer.Unbaked::new, ChrysmSpecialRenderer.Unbaked::tier);

        @Override
        public MapCodec<ChrysmSpecialRenderer.Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<ResourceKey<GooTypeDefinition>> bake(SpecialModelRenderer.BakingContext context) {
            return new ChrysmSpecialRenderer(tier);
        }
    }
}
