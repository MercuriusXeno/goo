package com.mercuriusxeno.goo.client.tartarus;

import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.zone.ClientConjuredDoors;
import com.mercuriusxeno.goo.client.zone.GateDrawing;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws Tartarus' own portal, the rift the operator settled on, in place of
 * the door End and Zone draw: a tongue of the nether portal's own animated
 * texture standing on its foot, cut in slices up its height, each slice's
 * membrane edged by a hot rim of lava, fullbright, seen from either side.
 * decision tartarus-draws-its-own-portal-with-the-nether-portal-texture
 * Where it stands at the camera, a first-person caster's own gate reaching
 * them, the nether portal's texture covers the view.
 * decision tartarus-draws-its-own-portal-with-the-nether-portal-texture
 */
public final class TartarusGateRenderer implements GateDrawing {

    /** The drawing. */
    public static final TartarusGateRenderer INSTANCE = new TartarusGateRenderer();
    /** The nether portal's texture on the block atlas, animated as the block's own. */
    public static final Identifier TEXTURE = Identifier.withDefaultNamespace("block/nether_portal");
    /** The hot rim's texture on the block atlas. */
    static final Identifier RIM_TEXTURE = Identifier.withDefaultNamespace("block/lava_still");
    /** Slices up a full rift's height; each block row's boundary splits a slice so the texture repeats per block. */
    private static final int SLICES = 24;
    /** The texture spans one block across and one block up, as the nether portal block's does. */
    private static final double TEXTURE_SPAN = 1;
    private static final double HALF_SPAN = TEXTURE_SPAN / 2;
    /** How far into a slice its block row is read, past a boundary's rounding. */
    private static final double INTO_ROW = 1.0E-6;

    private TartarusGateRenderer() {
    }

    @Override
    public void submit(SubmitNodeCollector collector, PoseStack poseStack, ClientConjuredDoors.Drawn door, Vec3 camera,
                       double time) {
        submitWith(collector, poseStack, door, camera, time,
                GooSubmitter.spriteUv(GooSubmitter.blockSprite(TEXTURE)),
                GooSubmitter.spriteUv(GooSubmitter.blockSprite(RIM_TEXTURE)));
        TartarusCover.LOCAL.seeGateAt(door.at(), camera);
        double radians = Math.toRadians(door.yaw());
        RiftShape.of(door, time).ifPresent(rift -> TartarusEmbers.see(time, door.at(),
                new Vec3(Math.cos(radians), 0, Math.sin(radians)), rift));
    }

    /**
     * Submits one rift with the sprites given.
     *
     * @param collector the collector the frame submits to
     * @param poseStack the frame's pose
     * @param door      the gate drawn
     * @param camera    the camera's world position
     * @param time      the client game tick and the frame's partial tick
     * @param portalUv  the nether portal sprite's UV rect
     * @param rimUv     the hot rim sprite's UV rect
     */
    static void submitWith(SubmitNodeCollector collector, PoseStack poseStack, ClientConjuredDoors.Drawn door,
                           Vec3 camera, double time, GooRenderUtil.UvRect portalUv, GooRenderUtil.UvRect rimUv) {
        RiftShape.of(door, time).ifPresent(rift -> {
            double radians = Math.toRadians(door.yaw());
            Plane plane = new Plane(door.at().subtract(camera), new Vec3(Math.cos(radians), 0, Math.sin(radians)),
                    new Vec3(-Math.sin(radians), 0, Math.cos(radians)));
            collector.submitCustomGeometry(poseStack, GooSubmitter.renderType(), (pose, consumer) -> {
                RenderContext ctx = new RenderContext(pose, consumer, GooSubmitter.fullbrightLight());
                List<Double> heights = sliceHeights(rift.height());
                for (int index = 0; index + 1 < heights.size(); index++) {
                    emitSlice(ctx, plane, rift, heights.get(index), heights.get(index + 1), portalUv, rimUv);
                }
            });
        });
    }

    /**
     * The gate's plane relative to the camera.
     *
     * @param foot   the middle of its foot
     * @param across the unit way across it
     * @param facing the unit way it faces
     */
    private record Plane(Vec3 foot, Vec3 across, Vec3 facing) {

        Vec3 at(double side, double up) {
            return foot.add(across.scale(side)).add(0, up, 0);
        }
    }

    /**
     * The heights a rift is cut at: evenly up its height, with each block
     * row's boundary added, so no slice spans two rows of texture.
     *
     * @param height the rift's height
     * @return the heights, lowest first
     */
    private static List<Double> sliceHeights(double height) {
        List<Double> heights = new ArrayList<>();
        for (int index = 0; index <= SLICES; index++) {
            heights.add(height * index / SLICES);
        }
        for (int row = 1; row * TEXTURE_SPAN < height; row++) {
            heights.add(row * TEXTURE_SPAN);
        }
        heights.sort(Double::compare);
        return heights;
    }

    /**
     * Emits one slice: its membrane between the rims, and a rim on each side.
     *
     * @param ctx      the render context
     * @param plane    the gate's plane
     * @param rift     the rift's shape
     * @param low      the slice's lower height
     * @param high     the slice's upper height
     * @param portalUv the nether portal sprite's UV rect
     * @param rimUv    the hot rim sprite's UV rect
     */
    private static void emitSlice(RenderContext ctx, Plane plane, RiftShape rift, double low, double high,
                                  GooRenderUtil.UvRect portalUv, GooRenderUtil.UvRect rimUv) {
        double row = Math.floor((low + INTO_ROW) / TEXTURE_SPAN) * TEXTURE_SPAN;
        double halfLow = rift.halfWidthAt(low);
        double halfHigh = rift.halfWidthAt(high);
        double innerLow = Math.max(0, halfLow - RiftShape.RIM);
        double innerHigh = Math.max(0, halfHigh - RiftShape.RIM);
        Slice slice = new Slice(plane, low, high, row);
        slice.emit(ctx, -innerLow, innerLow, -innerHigh, innerHigh, portalUv);
        slice.emit(ctx, innerLow, halfLow, innerHigh, halfHigh, rimUv);
        slice.emit(ctx, -halfLow, -innerLow, -halfHigh, -innerHigh, rimUv);
    }

    /**
     * One slice of the rift between two heights, within one block row of texture.
     *
     * @param plane the gate's plane
     * @param low   the slice's lower height
     * @param high  the slice's upper height
     * @param row   the height its block row of texture starts at
     */
    private record Slice(Plane plane, double low, double high, double row) {

        /**
         * Emits a trapezoid from its lower edge's two sides to its upper edge's, both ways round.
         *
         * @param ctx       the render context
         * @param lowLeft   the lower edge's left side
         * @param lowRight  the lower edge's right side
         * @param highLeft  the upper edge's left side
         * @param highRight the upper edge's right side
         * @param uv        the sprite's UV rect
         */
        void emit(RenderContext ctx, double lowLeft, double lowRight, double highLeft, double highRight,
                  GooRenderUtil.UvRect uv) {
            double[][] corners = {{lowLeft, low}, {lowRight, low}, {highRight, high}, {highLeft, high}};
            for (double[] corner : corners) {
                vertex(ctx, corner[0], corner[1], uv, plane.facing());
            }
            for (int index = corners.length - 1; index >= 0; index--) {
                vertex(ctx, corners[index][0], corners[index][1], uv, plane.facing().reverse());
            }
        }

        private void vertex(RenderContext ctx, double side, double up, GooRenderUtil.UvRect uv, Vec3 normal) {
            Vec3 at = plane.at(side, up);
            double across = Math.clamp((side + HALF_SPAN) / TEXTURE_SPAN, 0, 1);
            double down = Math.clamp(1 - (up - row) / TEXTURE_SPAN, 0, 1);
            ctx.vertex((float) at.x, (float) at.y, (float) at.z, (float) (uv.u0() + (uv.u1() - uv.u0()) * across),
                    (float) (uv.v0() + (uv.v1() - uv.v0()) * down), (float) normal.x, (float) normal.y,
                    (float) normal.z);
        }
    }
}
