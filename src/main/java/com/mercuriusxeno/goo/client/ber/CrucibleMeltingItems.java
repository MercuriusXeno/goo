package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.block.crucible.CrucibleBasin;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.crucible.CrucibleMeltQueue;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.data.GooValue;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Draws the items the crucible's pool holds on its fill: the oldest dissolving through
 * the dissolve shader, the rest waiting whole beside it (decisions dissolve-shader-on-item,
 * pool-keeps-stacks-in-order).
 */
final class CrucibleMeltingItems {

    /** A glow color for an item whose goo value the client has not received. */
    private static final int WHITE_GLOW = 0xFFFFFF;
    /** Lays an item's flat face up: its model's front faces +Z. */
    private static final float FACE_UP_DEGREES = -90f;
    /** The smallest extent a model is scaled by, so an empty model never divides by zero. */
    private static final double MIN_EXTENT = 1e-3;

    private final ItemModelResolver itemModelResolver;

    /**
     * @param itemModelResolver resolves an item stack to its model
     */
    CrucibleMeltingItems(ItemModelResolver itemModelResolver) {
        this.itemModelResolver = itemModelResolver;
    }

    /**
     * Resolves the dissolving item and the waiting ones into the render state.
     *
     * @param be    the crucible block entity
     * @param state the render state to populate
     */
    void extract(CrucibleBlockEntity be, CrucibleRenderState state) {
        CrucibleMeltQueue.Entry head = be.meltHead();
        state.hasHead = head != null && resolve(state.headItem, head.item());
        if (head != null) {
            state.headGlow = new DissolveGlow(head.dissolveFraction(), glowColorOf(head.item()));
        }
        List<CrucibleMeltQueue.Entry> waiting = be.meltWaiting();
        int shown = 0;
        for (int i = 0; i < waiting.size() && shown < state.waitingItems.length; i++) {
            if (resolve(state.waitingItems[shown], waiting.get(i).item())) {
                shown++;
            }
        }
        state.waitingShown = shown;
    }

    /**
     * Submits the dissolving item on the dissolve render type and the waiting items as they are.
     *
     * @param state         the crucible render state
     * @param surface       the drawn surface, or null while nothing has melted
     * @param poseStack     the pose stack at the block's origin
     * @param nodeCollector the render node collector
     */
    void submit(CrucibleRenderState state, CrucibleBasin.@Nullable DrawnSurface surface,
                PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        if (state.hasHead) {
            submitLyingFlat(state.headItem, CrucibleItemLayout.head(surface), poseStack,
                    new DissolvingItemCollector(nodeCollector, state.headGlow), state.lightCoords);
        }
        List<CrucibleItemLayout.ItemPlacement> placements = CrucibleItemLayout.waiting(surface, state.waitingShown);
        for (int i = 0; i < placements.size(); i++) {
            submitLyingFlat(state.waitingItems[i], placements.get(i), poseStack, nodeCollector, state.lightCoords);
        }
    }

    /**
     * Submits an item lying face up, centered on its placement and scaled to its width.
     *
     * @param item      the resolved item model
     * @param placement where the item lies and how wide
     * @param poseStack the pose stack at the block's origin
     * @param collector the collector the item submits into
     * @param light     the packed light coordinates
     */
    private static void submitLyingFlat(ItemStackRenderState item, CrucibleItemLayout.ItemPlacement placement,
                                        PoseStack poseStack, SubmitNodeCollector collector, int light) {
        AABB box = item.getModelBoundingBox();
        float scale = (float) (placement.size() / Math.max(Math.max(box.getXsize(), box.getYsize()), MIN_EXTENT));
        Vec3Center center = new Vec3Center(box);
        poseStack.pushPose();
        poseStack.translate(placement.x(), placement.y(), placement.z());
        poseStack.mulPose(Axis.XP.rotationDegrees(FACE_UP_DEGREES));
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-center.x(), -center.y(), -center.z());
        item.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    /**
     * An item model's bounding box center.
     *
     * @param x the center X
     * @param y the center Y
     * @param z the center Z
     */
    private record Vec3Center(double x, double y, double z) {
        Vec3Center(AABB box) {
            this(box.getCenter().x, box.getCenter().y, box.getCenter().z);
        }
    }

    /**
     * Resolves an item id to its model in the given render state.
     *
     * @param renderState the render state to populate
     * @param itemId      the item's registry id
     * @return true if the model resolved
     */
    private boolean resolve(ItemStackRenderState renderState, Identifier itemId) {
        ItemStack stack = BuiltInRegistries.ITEM.getValue(itemId).getDefaultInstance();
        itemModelResolver.updateForTopItem(renderState, stack, ItemDisplayContext.FIXED, null, null, 0);
        return !stack.isEmpty() && !renderState.isEmpty();
    }

    /**
     * Returns the color of the goo type the item yields most of, the glow this
     * commit paints before the mingled glow replaces it.
     *
     * @param itemId the item's registry id
     * @return the glow color as 0xRRGGBB
     */
    private static int glowColorOf(Identifier itemId) {
        GooValue value = Goo.GOO_VALUES.lookup(itemId);
        if (value == null || value.isEmpty()) {
            return WHITE_GLOW;
        }
        ResourceKey<GooTypeDefinition> largest = value.largestType();
        return largest == null ? WHITE_GLOW : ClientGooTypes.color(largest);
    }
}
