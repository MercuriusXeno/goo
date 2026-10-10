package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityArea;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.LineContext;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.overlay.FaceBullseyeRenderer;
import com.mercuriusxeno.goo.client.overlay.RippleRings;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Map;

/**
 * Draws the selected ability's held ghost at the aim point while right click
 * is held, in two passes: the landing's dome through its own shader at a share
 * of its opacity, depth tested, with rings pulsing across the aimed face; then
 * the same dome fainter with depth ignored, so the part inside blocks shows
 * through them.
 * every-instant-aoe-shows-its-indicator-while-held
 * held-visual-ghosts-the-landing-in-two-passes
 */
public final class HeldDomeRenderer {

    /** The share of the landing's opacity the dome draws over blocks. */
    static final float HELD_OPACITY = 0.4f;
    /** The share of the landing's opacity the dome draws through blocks, fainter. */
    static final float THROUGH_BLOCKS_OPACITY = 0.15f;
    /** Ring line segments per block of ring radius, so a wide ring stays round. */
    private static final int RING_SEGMENTS_PER_BLOCK = 12;
    /** The fewest line segments a ring draws with. */
    private static final int MIN_RING_SEGMENTS = 32;
    /** Alpha of a ring at birth on the additive glow lines, before it fades. */
    private static final int RING_PEAK_ALPHA = 160;
    /** Offset to a block's center from its corner. */
    private static final double BLOCK_CENTER = 0.5;

    /** The ghost each goo type holds; a type with none draws no dome. */
    private static final Map<ResourceKey<GooTypeDefinition>, HeldGhostVisual> GHOSTS = Map.of(
            GooTypes.CRYSTAL, CrystalExplosionVisual.INSTANCE,
            GooTypes.FROST, FrostExplosionVisual.INSTANCE,
            GooTypes.METAL, MetalExplosionVisual.INSTANCE,
            GooTypes.NETHER, NetherHeldGhost.INSTANCE,
            GooTypes.SHROOM, MoteCloudGhost.SHROOM,
            GooTypes.ROCK, RockExplosionVisual.INSTANCE,
            GooTypes.UNSTABLE, UnstableExplosionVisual.INSTANCE);

    /**
     * Where a ghost draws: its dome about the center of the cell the throw lands
     * in, and its rings on the face the throw strikes.
     *
     * @param domeCorner the lower corner of the block the dome centers in
     * @param ringCenter the point the rings center on
     * @param face       the face the rings lie on
     */
    record DomeAnchor(Vec3 domeCorner, Vec3 ringCenter, Direction face) {
    }

    private HeldDomeRenderer() {
    }

    /**
     * Whether the held dome draws: right click arms a press, and the selected
     * ability is an arc throw at the world or the crosshair whose area is a sphere.
     *
     * @param delivery the selected ability's delivery
     * @param badge    the selected ability's badge
     * @param area     the selected ability's area
     * @param armed    whether right click holds an armed press
     * @return true when the dome draws
     */
    public static boolean showsDome(Delivery delivery, AbilityBadge badge, AbilityArea area, boolean armed) {
        return armed && isArcAtTheWorld(delivery, badge) && isSphere(area);
    }

    private static boolean isArcAtTheWorld(Delivery delivery, AbilityBadge badge) {
        return delivery.kind() == DeliveryKind.ARC && (badge == AbilityBadge.WORLD || badge == AbilityBadge.FREE);
    }

    private static boolean isSphere(AbilityArea area) {
        return area.shape() == AbilityArea.Shape.SPHERE && area.size() > 0;
    }

    /**
     * The ghost a goo type holds, or null where its type has none yet.
     *
     * @param type the goo type
     * @return the ghost visual
     */
    static @Nullable HeldGhostVisual ghostOf(ResourceKey<GooTypeDefinition> type) {
        return GHOSTS.get(type);
    }

    /**
     * Where the ghost draws for a target: the cell beside the struck face for a
     * block, the tile under an aimed point that met one, or about a point in open air.
     *
     * @param target the aim target
     * @return the anchor, or null when nothing is aimed at
     */
    static @Nullable DomeAnchor anchorOf(TargetResult target) {
        return switch (target) {
            case TargetResult.BlockTarget bt -> besideFace(bt);
            case TargetResult.PointTarget pt when pt.onBlock() -> besideFace(pt.tile());
            case TargetResult.PointTarget pt -> new DomeAnchor(
                    pt.point().subtract(BLOCK_CENTER, BLOCK_CENTER, BLOCK_CENTER), pt.point(), Direction.UP);
            default -> null;
        };
    }

    private static DomeAnchor besideFace(TargetResult.BlockTarget bt) {
        return new DomeAnchor(Vec3.atLowerCornerOf(bt.pos().relative(bt.face())), bt.resolveEndpoint(), bt.face());
    }

    /**
     * Draws the selected ability's ghost where the target anchors it.
     *
     * @param poseStack  the pose stack, camera relative
     * @param buffers    the buffer source
     * @param camera     the camera's world position
     * @param target     the aim target
     * @param ability    the selected ability
     * @param type       the selected goo type
     * @param ringRgb    the goo type's highlight color
     * @param nowSeconds seconds on the real-time clock the rings run on
     */
    public static void render(PoseStack poseStack, MultiBufferSource.BufferSource buffers, Vec3 camera,
                              TargetResult target, ClientAbility ability, ResourceKey<GooTypeDefinition> type,
                              int ringRgb, double nowSeconds) {
        HeldGhostVisual visual = ghostOf(type);
        DomeAnchor anchor = anchorOf(target);
        if (visual == null || anchor == null) {
            return;
        }
        HeldGhost ghost = visual.ghost(ability.area(), ability.behaviors());
        poseStack.pushPose();
        Vec3 corner = anchor.domeCorner().subtract(camera);
        poseStack.translate(corner.x, corner.y, corner.z);
        HeldPass pass = new HeldPass(ghost, anchor.face(), nowSeconds);
        for (HeldLayer layer : visual.heldLayers()) {
            pass.draw(poseStack, buffers, layer, layer.throughBlocks(), THROUGH_BLOCKS_OPACITY);
        }
        for (HeldLayer layer : visual.heldLayers()) {
            pass.draw(poseStack, buffers, layer, layer.overBlocks(), HELD_OPACITY);
        }
        poseStack.popPose();
        drawRings(poseStack, buffers, camera, anchor, ghost, ringRgb, nowSeconds);
    }

    /**
     * One frame's ghost, drawn once per pass.
     *
     * @param ghost      the ghost
     * @param face       the face the throw strikes
     * @param nowSeconds seconds on the real-time clock
     */
    private record HeldPass(HeldGhost ghost, Direction face, double nowSeconds) {
        /**
         * Draws one layer through one render type at a share of the landing's opacity.
         *
         * @param poseStack the pose stack, at the dome's block corner
         * @param buffers   the buffer source
         * @param layer     the layer
         * @param type      the pass's render type for the layer
         * @param opacity   the share of the landing's opacity
         */
        void draw(PoseStack poseStack, MultiBufferSource.BufferSource buffers, HeldLayer layer, RenderType type,
                  float opacity) {
            layer.emitter().emit(poseStack.last(), buffers.getBuffer(type), ghost, face, opacity, nowSeconds);
            buffers.endBatch(type);
        }
    }

    /**
     * Draws the rings rippling across the aimed face to the ghost's ring radius,
     * depth tested so they float over gaps in the surface.
     *
     * @param poseStack  the pose stack, camera relative
     * @param buffers    the buffer source
     * @param camera     the camera's world position
     * @param anchor     where the rings center and the face they lie on
     * @param ghost      the ghost whose ring radius they span
     * @param ringRgb    the goo type's highlight color
     * @param nowSeconds seconds on the real-time clock the rings run on
     */
    private static void drawRings(PoseStack poseStack, MultiBufferSource.BufferSource buffers, Vec3 camera,
                                  DomeAnchor anchor, HeldGhost ghost, int ringRgb, double nowSeconds) {
        float width = Minecraft.getInstance().getWindow().getAppropriateLineWidth();
        int segments = Math.max(MIN_RING_SEGMENTS, (int) Math.ceil(ghost.ringRadius() * RING_SEGMENTS_PER_BLOCK));
        LineContext ctx = new LineContext(poseStack.last(), buffers.getBuffer(GooRenderTypes.LINES_GLOW));
        for (double phase : RippleRings.ringPhases(nowSeconds)) {
            int alpha = (int) (RING_PEAK_ALPHA * ghost.rings().opacity(phase));
            if (RippleRings.isAlive(phase) && alpha > 0) {
                int color = ARGB.color(alpha, ARGB.red(ringRgb), ARGB.green(ringRgb), ARGB.blue(ringRgb));
                Vec3[] ring = FaceBullseyeRenderer.ringPoints(anchor.ringCenter(), anchor.face(),
                        ghost.rings().radius(phase, ghost.ringRadius()), segments);
                ctx.emitPolyline(camera, ring, color, width);
            }
        }
        buffers.endBatch(GooRenderTypes.LINES_GLOW);
    }
}
