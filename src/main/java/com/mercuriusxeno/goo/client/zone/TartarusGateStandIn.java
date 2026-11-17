package com.mercuriusxeno.goo.client.zone;

import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/**
 * The stand-in Tartarus' gate draws with until the operator settles its own
 * portal: the conjured door's outline, unzipping and widening as Zone's and
 * End's does, filled with the nether portal's texture, fullbright, seen from
 * either side.
 * decision tartarus-summons-a-gate-to-the-nether-like-end-and-zone
 */
final class TartarusGateStandIn {

    /** The nether portal's texture on the block atlas, animated as the block's own. */
    static final Identifier NETHER_PORTAL_SPRITE = Identifier.withDefaultNamespace("block/nether_portal");
    /** The doorway's half width and full height at full size, which the texture spans once. */
    private static final double HALF_WIDE = 0.5;
    private static final double FULL_WIDE = 2 * HALF_WIDE;
    private static final double FULL_TALL = 2.0;

    private TartarusGateStandIn() {
    }

    /**
     * Submits one gate's outline filled with the nether portal's texture.
     *
     * @param collector the collector the frame submits to
     * @param poseStack the frame's pose
     * @param door      the gate drawn
     * @param camera    the camera's world position
     */
    static void submit(SubmitNodeCollector collector, PoseStack poseStack, ClientConjuredDoors.Drawn door,
                       Vec3 camera) {
        GooRenderUtil.UvRect uv = GooSubmitter.spriteUv(GooSubmitter.blockSprite(NETHER_PORTAL_SPRITE));
        double radians = Math.toRadians(door.yaw());
        Vec3 across = new Vec3(Math.cos(radians), 0, Math.sin(radians));
        Vec3 facing = new Vec3(-Math.sin(radians), 0, Math.cos(radians));
        Vec3 foot = door.at().subtract(camera);
        List<DoorCrack.Corner> outline = DoorCrack.outline(door.unzipped(), door.widened());
        collector.submitCustomGeometry(poseStack, GooSubmitter.renderType(), (pose, consumer) -> {
            RenderContext ctx = new RenderContext(pose, consumer, GooSubmitter.fullbrightLight());
            for (DoorCrack.Corner corner : outline) {
                vertex(ctx, foot, across, corner, uv, facing);
            }
            for (int index = outline.size() - 1; index >= 0; index--) {
                vertex(ctx, foot, across, outline.get(index), uv, facing.reverse());
            }
        });
    }

    private static void vertex(RenderContext ctx, Vec3 foot, Vec3 across, DoorCrack.Corner corner,
                               GooRenderUtil.UvRect uv, Vec3 normal) {
        Vec3 at = foot.add(across.scale(corner.across())).add(0, corner.up(), 0);
        float u = (float) (uv.u0() + (uv.u1() - uv.u0()) * (corner.across() + HALF_WIDE) / FULL_WIDE);
        float v = (float) (uv.v1() - (uv.v1() - uv.v0()) * corner.up() / FULL_TALL);
        ctx.vertex((float) at.x, (float) at.y, (float) at.z, u, v, (float) normal.x, (float) normal.y,
                (float) normal.z);
    }
}
