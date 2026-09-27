package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.crucible.CrucibleBasin;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.crucible.CrucibleMeltQueue;
import com.mercuriusxeno.goo.block.crucible.CrucibleShape;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.data.GooValue;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws the items the crucible's pool holds on its fill: the oldest dissolving through
 * the dissolve shader, the rest waiting whole beside it (decisions dissolve-shader-on-item,
 * pool-keeps-stacks-in-order).
 */
final class CrucibleMeltingItems {

    /** A glow color for an item whose goo value the client has not received. */
    private static final int WHITE_GLOW = 0xFFFFFF;
    /** The smallest extent a model is scaled by, so an empty model never divides by zero. */
    private static final double MIN_EXTENT = 1e-3;
    /** The basin center in block-relative X and Z. */
    private static final double BASIN_CENTER = (CrucibleBasin.FOOTPRINT_MIN + CrucibleBasin.FOOTPRINT_MAX) / 2.0;

    private final ItemModelResolver itemModelResolver;
    /** Each crucible's entity-to-head handoff, held client-side and dropped with the crucible. */
    private final Map<CrucibleBlockEntity, CrucibleHeadHandoff> handoffs = new WeakHashMap<>();

    /**
     * @param itemModelResolver resolves an item stack to its model
     */
    CrucibleMeltingItems(ItemModelResolver itemModelResolver) {
        this.itemModelResolver = itemModelResolver;
    }

    /**
     * Resolves the dissolving item and the waiting ones into the render state, the
     * head in the ground display an item entity draws in, posed where the entity
     * it came from was drawn as it eases flat (decision consume-at-rest-in-place).
     *
     * @param be          the crucible block entity
     * @param state       the render state to populate, volumes and ripple already extracted
     * @param partialTick the partial tick
     */
    void extract(CrucibleBlockEntity be, CrucibleRenderState state, float partialTick) {
        CrucibleMeltQueue.Entry head = be.meltHead();
        state.hasHead = head != null && resolve(state.headItem, head.item(), ItemDisplayContext.GROUND);
        if (head != null) {
            state.headGlow = glowOf(head);
        }
        extractHeadPose(be, state, head, partialTick);
        List<CrucibleMeltQueue.Entry> waiting = be.meltWaiting();
        int shown = 0;
        for (int i = 0; i < waiting.size() && shown < state.waitingItems.length; i++) {
            if (resolve(state.waitingItems[shown], waiting.get(i).item(), ItemDisplayContext.FIXED)) {
                shown++;
            }
        }
        state.waitingShown = shown;
    }

    /**
     * Sees the item entity resting in the basin and poses the head from it.
     *
     * @param be          the crucible block entity
     * @param state       the render state, head resolved
     * @param head        the dissolving entry, or null
     * @param partialTick the partial tick
     */
    private void extractHeadPose(CrucibleBlockEntity be, CrucibleRenderState state,
                                 CrucibleMeltQueue.@Nullable Entry head, float partialTick) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        double now = level.getGameTime() + (double) partialTick;
        CrucibleHeadHandoff handoff = handoffs.computeIfAbsent(be, key -> new CrucibleHeadHandoff());
        seeBasinEntity(handoff, level, be.getBlockPos(), partialTick, now);
        AABB box = state.headItem.getModelBoundingBox();
        CrucibleItemLayout.ItemPlacement placement = CrucibleItemLayout.head(
            CrucibleBasin.drawnSurface(state.volumes), state.rippleAmplitude);
        CrucibleHeadHandoff.ItemPose rest = CrucibleHeadHandoff.restingPose(placement, box, MIN_EXTENT);
        state.headPose = handoff.headPose(state.hasHead && head != null ? head.item() : null, box, rest, now);
    }

    /**
     * Hands the handoff the item entity in the basin nearest its center, if one is there.
     *
     * @param handoff     the crucible's handoff
     * @param level       the client level
     * @param pos         the crucible's position
     * @param partialTick the partial tick
     * @param now         the game time with the partial tick
     */
    private static void seeBasinEntity(CrucibleHeadHandoff handoff, Level level, BlockPos pos,
                                       float partialTick, double now) {
        ItemEntity nearest = null;
        Vec3 nearestAt = Vec3.ZERO;
        double nearestDistance = Double.MAX_VALUE;
        Vec3 origin = Vec3.atLowerCornerOf(pos);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, CrucibleShape.CAVITY.bounds().move(pos))) {
            Vec3 at = item.getPosition(partialTick).subtract(origin);
            double distance = at.distanceToSqr(BASIN_CENTER, at.y, BASIN_CENTER);
            if (CrucibleBasin.holdsPoint(at.x, at.y, at.z) && distance < nearestDistance) {
                nearest = item;
                nearestAt = at;
                nearestDistance = distance;
            }
        }
        if (nearest != null) {
            handoff.seeEntity(BuiltInRegistries.ITEM.getKey(nearest.getItem().getItem()),
                nearestAt.x, nearestAt.y, nearestAt.z, nearest.tickCount + partialTick, nearest.bobOffs, now);
        }
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
            submitPosed(state.headItem, state.headPose, poseStack,
                    new DissolvingItemCollector(nodeCollector, state.headGlow), state.lightCoords);
        }
        List<CrucibleItemLayout.ItemPlacement> placements = CrucibleItemLayout.waiting(surface, state.rippleAmplitude,
                state.waitingShown);
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
        submitPosed(item, CrucibleHeadHandoff.restingPose(placement, item.getModelBoundingBox(), MIN_EXTENT),
            poseStack, collector, light);
    }

    /**
     * Submits an item model at a pose: centered on it, turned, tilted and scaled.
     *
     * @param item      the resolved item model
     * @param pose      the pose the model's center takes
     * @param poseStack the pose stack at the block's origin
     * @param collector the collector the item submits into
     * @param light     the packed light coordinates
     */
    private static void submitPosed(ItemStackRenderState item, CrucibleHeadHandoff.ItemPose pose,
                                    PoseStack poseStack, SubmitNodeCollector collector, int light) {
        Vec3Center center = new Vec3Center(item.getModelBoundingBox());
        poseStack.pushPose();
        poseStack.translate(pose.x(), pose.y(), pose.z());
        poseStack.mulPose(Axis.YP.rotation(pose.spin()));
        poseStack.mulPose(Axis.XP.rotationDegrees(pose.tilt()));
        poseStack.scale(pose.scale(), pose.scale(), pose.scale());
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
     * @param display     the display context the model is resolved in
     * @return true if the model resolved
     */
    private boolean resolve(ItemStackRenderState renderState, Identifier itemId, ItemDisplayContext display) {
        ItemStack stack = BuiltInRegistries.ITEM.getValue(itemId).getDefaultInstance();
        itemModelResolver.updateForTopItem(renderState, stack, display, null, null, 0);
        return !stack.isEmpty() && !renderState.isEmpty();
    }

    /**
     * Returns the dissolving item's glow: one layer per goo type it yields, each in its
     * type's color, or white when the client holds no goo value for it.
     *
     * @param head the stack dissolving
     * @return the glow
     */
    private static DissolveGlow glowOf(CrucibleMeltQueue.Entry head) {
        GooValue value = Goo.GOO_VALUES.lookup(head.item());
        if (value == null || value.isEmpty()) {
            return DissolveGlow.single(head.dissolveFraction(), WHITE_GLOW);
        }
        return DissolveGlow.of(head.dissolveFraction(), value, ClientGooTypes::color);
    }
}
