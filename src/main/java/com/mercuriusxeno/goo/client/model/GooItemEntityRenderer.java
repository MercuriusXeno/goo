package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.throwing.GooFlightRenderer;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The item entity renderer, drawing a dropped goo item as its cuboid resting
 * on the entity's feet, its faces square to the world's axes and still at
 * every age, where vanilla bobs, hovers and spins an item. Every other item
 * draws as vanilla draws it.
 * operator ruling: "Bob, hover, and spin, and also they should stay aligned to the world grid"
 * decision goo-item-in-world-draws-as-the-palm-cuboid
 * decision flecks-move-by-flexible-goo-physics-and-stay-items
 */
public class GooItemEntityRenderer extends ItemEntityRenderer {

    /**
     * Creates the item entity renderer.
     *
     * @param context the renderer context
     */
    public GooItemEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public GooItemRenderState createRenderState() {
        return new GooItemRenderState();
    }

    /**
     * Extracts vanilla's state, and for a goo stack its type and the size it
     * draws at: a running join's eased size while it anchors one, its
     * volume's size otherwise.
     * decision combining-flecks-join-into-one-cuboid
     *
     * @param entity       the item entity
     * @param state        the state to fill
     * @param partialTicks the frame's partial tick
     */
    @Override
    public void extractRenderState(ItemEntity entity, ItemEntityRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        if (!(state instanceof GooItemRenderState goo)) {
            return;
        }
        ItemStack stack = entity.getItem();
        goo.gooType = GooStacks.keyOf(stack);
        if (goo.gooType != null) {
            Float joining = GooJoins.anchorSizeOf(stack);
            goo.gooSize = joining != null ? joining : GooCuboidSize.sizeOf(GooStacks.volumeOf(stack));
        }
    }

    /**
     * Draws a goo item's cuboid on its feet, or any other item as vanilla does.
     *
     * @param state         the extracted state
     * @param poseStack     the pose stack, at the entity's feet
     * @param nodeCollector the render node collector
     * @param camera        the camera's state
     */
    @Override
    public void submit(ItemEntityRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector,
                       CameraRenderState camera) {
        if (!(state instanceof GooItemRenderState goo) || goo.gooType == null) {
            super.submit(state, poseStack, nodeCollector, camera);
            return;
        }
        submitCuboid(goo.gooType, goo.gooSize, poseStack, nodeCollector);
    }

    /**
     * Submits the palm cuboid resting on the pose's origin, unturned: the
     * core inside the translucent shell.
     *
     * @param type          the goo type
     * @param size          the size against a thrown blob's in the item model
     * @param poseStack     the pose stack, at the entity's feet
     * @param nodeCollector the render node collector
     */
    private static void submitCuboid(ResourceKey<GooTypeDefinition> type, float size, PoseStack poseStack,
                                     SubmitNodeCollector nodeCollector) {
        float scale = GooCuboidSize.sizeInLevel(size);
        float gameTime = GloveSpecialRenderer.gameTime();
        poseStack.pushPose();
        poseStack.translate(0, GooCuboidSize.halfWidthInLevel(size), 0);
        nodeCollector.submitCustomGeometry(poseStack, GooSubmitter.solidOnBlockAtlas(),
                (pose, c) -> GooFlightRenderer.emitCore(pose, c, type, gameTime, scale));
        nodeCollector.submitCustomGeometry(poseStack, GooSubmitter.renderType(),
                (pose, c) -> GooFlightRenderer.emitShell(pose, c, type, scale));
        poseStack.popPose();
    }

    /**
     * Vanilla's item entity state with the goo cuboid a goo stack draws as.
     */
    public static class GooItemRenderState extends ItemEntityRenderState {
        /** The stack's goo type, or null for any other item. */
        public @Nullable ResourceKey<GooTypeDefinition> gooType;
        /** The cuboid's size against a thrown blob's in the item model. */
        public float gooSize;
    }
}
