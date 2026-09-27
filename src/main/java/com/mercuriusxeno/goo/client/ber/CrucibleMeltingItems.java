package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.crucible.CrucibleBasin;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.crucible.CrucibleMeltQueue;
import com.mercuriusxeno.goo.block.crucible.CrucibleShape;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.SurfaceRipple;
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
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

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
    /** The cut faces kept, enough for every item melting in the crucibles in view. */
    private static final int KEPT_FACES = 32;
    private static final float LOAD_FACTOR = 0.75f;
    /** A unit face, for a head drawn whole before its face is probed. */
    private static final QuadRectClipper.Rect WHOLE_FACE = new QuadRectClipper.Rect(0f, 0f, 1f, 1f);

    private final ItemModelResolver itemModelResolver;
    /** Each crucible's entity-to-head handoff, held client-side and dropped with the crucible. */
    private final Map<CrucibleBlockEntity, CrucibleHeadHandoff> handoffs = new WeakHashMap<>();
    /** Each melting item's cut face, the last {@link #KEPT_FACES} items kept. */
    private final Map<Identifier, ShardFace> faces = new LinkedHashMap<>(KEPT_FACES, LOAD_FACTOR, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Identifier, ShardFace> eldest) {
            return size() > KEPT_FACES;
        }
    };

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
     * Returns the head's face and shard map, cut once per item and kept while it melts,
     * since the item's seed fixes the cut (decision tiles-of-the-items-image).
     *
     * @param item   the resolved head model
     * @param itemId the head's item id
     * @return the face and its shard map
     */
    private ShardFace faceOf(ItemStackRenderState item, Identifier itemId) {
        return faces.computeIfAbsent(itemId, id -> ItemFaceProbe.probe(item, ItemShardCutter.seedOf(id.toString())));
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
        state.headEasing = state.headPose != rest;
        state.headFace = state.hasHead && head != null ? faceOf(state.headItem, head.item()) : null;
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
            submitHead(state, surface, poseStack, nodeCollector);
        }
        List<CrucibleItemLayout.ItemPlacement> placements = CrucibleItemLayout.waiting(surface, state.rippleAmplitude,
                state.waitingShown);
        for (int i = 0; i < placements.size(); i++) {
            submitLyingFlat(state.waitingItems[i], placements.get(i), poseStack, nodeCollector, state.lightCoords);
        }
    }

    /**
     * Submits the dissolving item: whole at its handoff pose while it eases in from the item
     * entity it came from (decision consume-at-rest-in-place), then as its shards once it
     * lies at rest (decision tiles-of-the-items-image).
     *
     * @param state         the crucible render state
     * @param surface       the drawn surface, or null while nothing has melted
     * @param poseStack     the pose stack at the block's origin
     * @param nodeCollector the render node collector
     */
    private static void submitHead(CrucibleRenderState state, CrucibleBasin.@Nullable DrawnSurface surface,
                                   PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        ShardFace face = state.headFace;
        if (state.headEasing || face == null) {
            ShardFace whole = new ShardFace(face != null ? face.bounds() : WHOLE_FACE,
                    ItemShardCutter.ShardMap.whole(1, 1));
            submitPosed(state.headItem, state.headPose, poseStack,
                    pose -> new DissolvingItemCollector(nodeCollector, state.headGlow, whole, 0, pose),
                    state.lightCoords);
            return;
        }
        submitShards(state.headItem, face, CrucibleItemLayout.shardHomes(surface, state.rippleAmplitude,
                        new SurfaceRipple.Field(state.blockPos.getX(), state.blockPos.getZ(), state.dayFraction),
                        homeOffsets(face)),
                state.headGlow, poseStack, nodeCollector, state.lightCoords);
    }

    /**
     * Returns each shard's centroid as an offset from the face's center, in widths of the
     * face's larger side, rows running along the model's Y.
     *
     * @param face the item's face and shard map
     * @return one X and Y offset per shard
     */
    static List<float[]> homeOffsets(ShardFace face) {
        List<float[]> offsets = new ArrayList<>(face.map().count());
        for (int shard = 0; shard < face.map().count(); shard++) {
            float[] centroid = face.centroid(shard);
            offsets.add(new float[] {(centroid[0] - face.centerX()) / face.span(),
                (centroid[1] - face.centerY()) / face.span()});
        }
        return offsets;
    }

    /**
     * Submits the dissolving item once per shard of its image, each shard lying face up on
     * its own placement and dissolving on its own (decision tiles-of-the-items-image).
     *
     * @param item          the resolved item model
     * @param face          the item's face and shard map
     * @param shards        where each shard's centroid lies, in shard order
     * @param glow          how far the item has dissolved and the layers its edge glows in
     * @param poseStack     the pose stack at the block's origin
     * @param nodeCollector the render node collector
     * @param light         the packed light coordinates
     */
    static void submitShards(ItemStackRenderState item, ShardFace face, List<CrucibleItemLayout.ItemPlacement> shards,
                             DissolveGlow glow, PoseStack poseStack, SubmitNodeCollector nodeCollector, int light) {
        double centerZ = item.getModelBoundingBox().getCenter().z;
        for (int shard = 0; shard < shards.size(); shard++) {
            CrucibleItemLayout.ItemPlacement placement = shards.get(shard);
            float scale = (float) (placement.size() / Math.max(face.span(), MIN_EXTENT));
            float[] centroid = face.centroid(shard);
            poseStack.pushPose();
            poseStack.translate(placement.x(), placement.y(), placement.z());
            poseStack.mulPose(Axis.XP.rotationDegrees(CrucibleHeadHandoff.FLAT_TILT_DEGREES));
            poseStack.scale(scale, scale, scale);
            poseStack.translate(-centroid[0], -centroid[1], -centerZ);
            DissolvingItemCollector shardCollector =
                    new DissolvingItemCollector(nodeCollector, glow, face, shard, poseStack.last().pose());
            item.submit(poseStack, shardCollector, light, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
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
            poseStack, itemPose -> collector, light);
    }

    /**
     * Submits an item model at a pose: centered on it, turned, tilted and scaled.
     *
     * @param item        the resolved item model
     * @param pose        the pose the model's center takes
     * @param poseStack   the pose stack at the block's origin
     * @param collectorAt the collector the item submits into, given the pose it is submitted at
     * @param light       the packed light coordinates
     */
    private static void submitPosed(ItemStackRenderState item, CrucibleHeadHandoff.ItemPose pose,
                                    PoseStack poseStack, Function<Matrix4fc, SubmitNodeCollector> collectorAt,
                                    int light) {
        Vec3Center center = new Vec3Center(item.getModelBoundingBox());
        poseStack.pushPose();
        poseStack.translate(pose.x(), pose.y(), pose.z());
        poseStack.mulPose(Axis.YP.rotation(pose.spin()));
        poseStack.mulPose(Axis.XP.rotationDegrees(pose.tilt()));
        poseStack.scale(pose.scale(), pose.scale(), pose.scale());
        poseStack.translate(-center.x(), -center.y(), -center.z());
        item.submit(poseStack, collectorAt.apply(poseStack.last().pose()), light, OverlayTexture.NO_OVERLAY, 0);
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
