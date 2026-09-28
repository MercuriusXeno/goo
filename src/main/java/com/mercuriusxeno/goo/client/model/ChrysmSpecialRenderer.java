package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.block.crystallizer.CrystalCluster;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.CrystalClusterSubmitter;
import com.mercuriusxeno.goo.item.ChrysmTier;
import com.mercuriusxeno.goo.registry.GooDataComponents;
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
 * its goo type's own texture (operator ruling), so the three tiers show at their
 * own sizes. The operator's drawn sizes can replace it later.
 */
public class ChrysmSpecialRenderer implements SpecialModelRenderer<ResourceKey<GooTypeDefinition>> {

    /** One scale for every tier, so a megachrysm fits the item box and the smaller tiers read smaller. */
    private static final float SCALE = 0.7f;
    private static final float CENTER = 0.5f;
    /** The crystal's base sits one pixel above the item box floor. */
    private static final float FLOOR = 1f / 16f;
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
        poseStack.translate(CENTER, FLOOR, CENTER);
        poseStack.scale(SCALE, SCALE, SCALE);
        poseStack.translate(-CrystalCluster.BASE_X * PIXEL, -CrystalCluster.BASE_Y * PIXEL,
                -CrystalCluster.BASE_Z * PIXEL);
        CrystalClusterSubmitter.submit(poseStack, nodeCollector, CrystalCluster.prisms(tier.volume()),
                CrystalClusterSubmitter.lookOf(type, ClientGooTypes.color(type)), packedLight);
        poseStack.popPose();
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
