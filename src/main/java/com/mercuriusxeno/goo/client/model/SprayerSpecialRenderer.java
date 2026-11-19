package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.block.canister.CanisterGeometry;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.item.SprayerItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import java.util.function.Consumer;

/**
 * Draws the canister a sprayer carries, the gun's gold frame being the item's base model: the canister lies on its
 * side inside the frame, its goo visible and settled as a canister turned sideways settles, every goo type it holds
 * mingled on one surface; an empty sprayer draws its frame alone.
 * decision sprayer-holds-a-canister-sideways-like-a-caulk-gun
 * decision sideways-canister-goo-settles-as-if-turned-sideways
 */
public class SprayerSpecialRenderer implements SpecialModelRenderer<CanisterSpecialRenderer.GooData> {

    /** The canister's center across its width, in its own frame, as the canister item draws it. */
    private static final float CANISTER_CENTER = 8f / 16f;
    /** The middle of the canister's length, gasket to gasket, in its own frame. */
    private static final float LENGTH_MIDDLE = (CanisterGeometry.STANDING.gasketBottom()
            + CanisterGeometry.STANDING.gasketTop()) / 2f;
    /** How high in the frame the lying canister's axis runs, in block units. */
    private static final float CRADLE_HEIGHT = 9f / 16f;
    /** The quarter turn that lays the canister's length along X, its own low X side then facing down. */
    private static final float ON_ITS_SIDE_DEGREES = 90f;

    /**
     * What the renderer submits past the frame: the canister's body and caps, and its goo in a box.
     */
    interface FittedParts {

        /**
         * Submits the canister's body and gasket caps.
         *
         * @param data the canister's render data
         */
        void body(CanisterSpecialRenderer.GooData data);

        /**
         * Submits the canister's goo in a box of its own frame.
         *
         * @param data   the canister's render data
         * @param bounds the box the goo fills
         */
        void fluid(CanisterSpecialRenderer.GooData data, CuboidBounds bounds);
    }

    /**
     * Submits the fitted canister: nothing for an empty sprayer; else its body, and its goo, where it holds any,
     * settled sideways.
     *
     * @param data  the fitted canister's render data, or null where the sprayer holds none
     * @param parts what the parts are submitted to
     */
    static void submitFitted(CanisterSpecialRenderer.@Nullable GooData data, FittedParts parts) {
        if (data == null) {
            return;
        }
        parts.body(data);
        if (data.fill() > 0f) {
            parts.fluid(data, sidewaysFluidBounds(data.fill()));
        }
    }

    /**
     * The box a sideways canister's goo fills at a fill, in the canister's own frame.
     *
     * @param fill the fill fraction in [0, 1]
     * @return the goo's box
     */
    static CuboidBounds sidewaysFluidBounds(float fill) {
        return CanisterSpecialRenderer.fluidBounds(CanisterGeometry.Settling.SIDEWAYS, fill);
    }

    @Override
    public CanisterSpecialRenderer.@Nullable GooData extractArgument(ItemStack sprayer) {
        ItemStack canister = SprayerItem.fittedCanister(sprayer);
        if (canister.isEmpty()) {
            return null;
        }
        CanisterMetadata meta = CanisterItem.getMetadata(canister);
        var content = CanisterItem.getFluidContent(canister);
        return CanisterSpecialRenderer.dataOf(content, content.capacityOver(ContainerCapacity.CANISTER_BASE),
                meta.topGasketId() != null, meta.bottomGasketId() != null);
    }

    @Override
    public void submit(CanisterSpecialRenderer.@Nullable GooData data, PoseStack poseStack,
                       SubmitNodeCollector nodeCollector, int packedLight, int packedOverlay, boolean hasFoil,
                       int outlineColor) {
        poseStack.pushPose();
        poseStack.translate(CANISTER_CENTER, CRADLE_HEIGHT, CANISTER_CENTER);
        poseStack.mulPose(Axis.ZP.rotationDegrees(ON_ITS_SIDE_DEGREES));
        poseStack.translate(-CANISTER_CENTER, -LENGTH_MIDDLE, -CANISTER_CENTER);
        submitFitted(data, new FittedParts() {
            @Override
            public void body(CanisterSpecialRenderer.GooData fitted) {
                GooSubmitter.submitCanisterBody(poseStack, nodeCollector, packedLight);
                CanisterSpecialRenderer.submitGaskets(poseStack, nodeCollector, packedLight, fitted);
            }

            @Override
            public void fluid(CanisterSpecialRenderer.GooData fitted, CuboidBounds bounds) {
                CanisterSpecialRenderer.submitFluidIn(poseStack, nodeCollector, fitted, bounds);
            }
        });
        poseStack.popPose();
    }

    /**
     * Reports the frame's extents, the whole block the gun's model fills.
     *
     * @param output consumer for extent corner vertices
     */
    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        output.accept(new Vector3f(0, 0, 0));
        output.accept(new Vector3f(1, 1, 1));
    }

    /**
     * Unbaked factory for the sprayer's special renderer, registered as "goo:sprayer_canister".
     */
    public record Unbaked() implements SpecialModelRenderer.Unbaked<CanisterSpecialRenderer.GooData> {

        /** Codec for data-driven item model deserialization. */
        public static final MapCodec<SprayerSpecialRenderer.Unbaked> MAP_CODEC =
                MapCodec.unit(new SprayerSpecialRenderer.Unbaked());

        @Override
        public MapCodec<SprayerSpecialRenderer.Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<CanisterSpecialRenderer.GooData> bake(SpecialModelRenderer.BakingContext context) {
            return new SprayerSpecialRenderer();
        }
    }
}
