package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.ability.LiftedPalmBlob;
import com.mercuriusxeno.goo.client.ability.PortalView;
import com.mercuriusxeno.goo.client.throwing.BlobShape;
import com.mercuriusxeno.goo.client.throwing.GooFlightRenderer;
import com.mercuriusxeno.goo.client.zone.RoomBodyCopyRenderer;
import com.mercuriusxeno.goo.item.GooCasters;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import java.util.function.Consumer;

/**
 * Special item renderer for the goo item in the level: dropped, held in a
 * hand or set in an item frame, it draws as the palm cuboid, the core inside
 * the translucent shell the glove's palm blob draws, while GUI slots keep the
 * sprite tiers. Held as the player's caster with no glove, the cuboid is
 * the palm blob: it marks the hand the aim line leaves from, and while the
 * blob stands lifted it draws nothing, the local first-person lift drawing in
 * its place as the glove's does.
 * decision goo-item-in-world-draws-as-the-palm-cuboid
 * decision raw-goo-items-work-bare-handed-for-the-held-type
 */
public class GooCuboidSpecialRenderer implements SpecialModelRenderer<GooCuboidSpecialRenderer.GooCuboid> {

    /**
     * The cuboid's center in the item model, its middle: the base model's
     * display transforms place that point where the dropped, framed or held
     * item stands.
     */
    private static final float MODEL_CENTER = 0.5f;

    /**
     * Creates a goo cuboid special renderer.
     */
    public GooCuboidSpecialRenderer() {
    }

    /**
     * Extracts the goo type the stack carries and the size it draws at: a
     * running join's eased size while the stack is that join's anchor's, its
     * volume's size otherwise.
     * decision combining-flecks-join-into-one-cuboid
     *
     * @param stack the goo item stack
     * @return the cuboid to draw, or null for a stack with no goo type
     */
    @Override
    public @Nullable GooCuboid extractArgument(ItemStack stack) {
        ResourceKey<GooTypeDefinition> type = GooStacks.keyOf(stack);
        if (type == null) {
            return null;
        }
        Float joining = GooJoins.anchorSizeOf(stack);
        float size = joining != null ? joining : GooCuboidSize.sizeOf(GooStacks.volumeOf(stack));
        Minecraft mc = Minecraft.getInstance();
        boolean localCaster = mc.player != null && stack == GooCasters.casterOf(mc.player);
        boolean lifted = LiftedPalmBlob.liftedFrom(stack);
        return new GooCuboid(type, size, localCaster, lifted, lifted && LiftedPalmBlob.heldInView(stack));
    }

    /**
     * Submits the local first-person lift in the hand's pass in place of the
     * cuboid, as the glove's palm does; any other lifted cuboid draws nothing,
     * the level's pass drawing its blob.
     *
     * @param data          the lifted cuboid
     * @param poseStack     the current pose stack
     * @param nodeCollector the render node collector
     */
    private static void submitLift(GooCuboid data, PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        if (data.liftedInView() && !PortalView.drawing() && !RoomBodyCopyRenderer.drawingCopies()) {
            LiftedPalmBlob.liftHeldInView().ifPresent(lift -> GloveSpecialRenderer.submitLiftedGoo(poseStack,
                    nodeCollector, data.type(), lift, standingOffTheCuboid(lift)));
        }
    }

    /**
     * Where the lifted blob stands off the cuboid as it stands up: from the
     * cuboid's center, up the model.
     *
     * @param lift the local player's lift
     * @return the point in the item's model, in block coords
     */
    private static Vector3f standingOffTheCuboid(LiftedPalmBlob.HandLift lift) {
        return new Vector3f(MODEL_CENTER, MODEL_CENTER + lift.stand() * GooFlightRenderer.SHELL_HW, MODEL_CENTER);
    }

    /**
     * Submits the palm cuboid of the stack's goo type about the model's
     * center, sized by its volume: the core still inside the shell, both on
     * the translucent goo type and the core first, the pair the glove's palm
     * blob submits. A stack with no goo type draws nothing.
     * decision fleck-size-follows-volume-on-a-log-scale
     * goo-cuboid-core-is-three-quarters-of-its-shell
     * slime-core-draws-translucent-at-the-shells-opacity
     *
     * @param data          the cuboid to draw, or null
     * @param poseStack     the current pose stack
     * @param nodeCollector the render node collector
     * @param packedLight   packed light value
     * @param packedOverlay packed overlay value
     * @param hasFoil       whether the item has enchantment foil
     * @param outlineColor  outline color for selected items
     */
    @Override
    public void submit(@Nullable GooCuboid data, PoseStack poseStack, SubmitNodeCollector nodeCollector,
                       int packedLight, int packedOverlay, boolean hasFoil, int outlineColor) {
        if (data == null) {
            return;
        }
        ResourceKey<GooTypeDefinition> type = data.type();
        if (data.localCaster()) {
            GloveSpecialRenderer.captureGooCenter(poseStack, MODEL_CENTER, MODEL_CENTER, MODEL_CENTER);
        }
        if (data.lifted()) {
            submitLift(data, poseStack, nodeCollector);
            return;
        }
        BlobShape shape = BlobShape.CUBE.scaled(data.size());
        poseStack.pushPose();
        poseStack.translate(MODEL_CENTER, MODEL_CENTER, MODEL_CENTER);
        nodeCollector.submitCustomGeometry(poseStack, GooSubmitter.renderType(),
                (pose, c) -> GooFlightRenderer.emitStillCore(pose, c, type, shape));
        nodeCollector.submitCustomGeometry(poseStack, GooSubmitter.renderType(),
                (pose, c) -> GooFlightRenderer.emitShell(pose, c, type, shape));
        poseStack.popPose();
    }

    /**
     * Reports the shell's corners at the capped size, the cuboid's outer bound.
     *
     * @param output consumer for extent corner vertices
     */
    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        float halfWidth = GooFlightRenderer.SHELL_HW * GooCuboidSize.CAPPED_SIZE;
        output.accept(new Vector3f(MODEL_CENTER - halfWidth));
        output.accept(new Vector3f(MODEL_CENTER + halfWidth));
    }

    /**
     * The goo item's render data.
     *
     * @param type         the goo type the stack carries
     * @param size         the cuboid's size against a thrown blob's
     * @param localCaster  whether the stack is the local player's caster, held bare handed
     * @param lifted       whether a caster's blob stands lifted off this stack
     * @param liftedInView whether the lift is the local player's, drawn in the first-person hand's pass
     */
    public record GooCuboid(ResourceKey<GooTypeDefinition> type, float size, boolean localCaster, boolean lifted,
                            boolean liftedInView) {

        /**
         * The cuboid a stack draws as at rest, sized by its volume.
         * decision fleck-size-follows-volume-on-a-log-scale
         *
         * @param type   the goo type the stack carries
         * @param volume the goo the stack holds, in mB
         * @return the cuboid
         */
        public static GooCuboid ofVolume(ResourceKey<GooTypeDefinition> type, double volume) {
            return new GooCuboid(type, GooCuboidSize.sizeOf(volume), false, false, false);
        }
    }

    /**
     * Unbaked factory for the goo cuboid renderer. Registered as
     * "goo:goo_cuboid" in the special model renderer registry.
     */
    public record Unbaked() implements SpecialModelRenderer.Unbaked<GooCuboid> {

        /**
         * Codec for data-driven item model deserialization.
         */
        public static final MapCodec<GooCuboidSpecialRenderer.Unbaked> MAP_CODEC =
                MapCodec.unit(new GooCuboidSpecialRenderer.Unbaked());

        @Override
        public MapCodec<GooCuboidSpecialRenderer.Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<GooCuboid> bake(SpecialModelRenderer.BakingContext context) {
            return new GooCuboidSpecialRenderer();
        }
    }
}
