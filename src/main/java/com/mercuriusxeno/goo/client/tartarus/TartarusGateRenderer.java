package com.mercuriusxeno.goo.client.tartarus;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.zone.ClientConjuredDoors;
import com.mercuriusxeno.goo.client.zone.GateDrawing;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import java.util.function.Function;

/**
 * Draws Tartarus' own portal, the rift the operator settled on, in place of
 * the door End and Zone draw, as the artifact draft drew it: cell by cell,
 * 64 cells a block, each cell showing what the rift's shape answers at its
 * middle, so its edge steps in quarter texels. The membrane is the nether
 * portal's own animated texture, a texel a sixteenth of a block, fixed in
 * the gate's plane; the rim and the embers are flat color over a white
 * texel; all of it fullbright, seen from either side. Each row's runs of
 * like cells draw as one quad, a membrane run broken where the texture
 * repeats.
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
    /** A white texel on the block atlas, which the rim and the embers tint. */
    static final Identifier FLAT_TEXTURE = Identifier.fromNamespaceAndPath(Goo.MODID, "block/flat_white");
    /** How far before the membrane an ember draws, so it shows over it from either side, in blocks. */
    private static final double EMBER_LIFT = 0.002;
    /** The texture repeats once a block across and up; a block's middle stands half its cells in. */
    private static final int HALF_BLOCK = RiftShape.CELLS / 2;
    private static final double HALF = 0.5;
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;

    private TartarusGateRenderer() {
    }

    @Override
    public void submit(SubmitNodeCollector collector, PoseStack poseStack, ClientConjuredDoors.Drawn door, Vec3 camera,
                       double time) {
        submitWith(collector, poseStack, door, camera, time,
                GooSubmitter.spriteUv(GooSubmitter.blockSprite(TEXTURE)),
                GooSubmitter.spriteUv(GooSubmitter.blockSprite(FLAT_TEXTURE)));
        TartarusCover.LOCAL.seeGateAt(door.at(), camera);
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
     * @param flatUv    the white texel sprite's UV rect
     */
    static void submitWith(SubmitNodeCollector collector, PoseStack poseStack, ClientConjuredDoors.Drawn door,
                           Vec3 camera, double time, GooRenderUtil.UvRect portalUv, GooRenderUtil.UvRect flatUv) {
        RiftShape.of(door, time).ifPresent(rift -> {
            double radians = Math.toRadians(door.yaw());
            Plane plane = new Plane(door.at().subtract(camera), new Vec3(Math.cos(radians), 0, Math.sin(radians)),
                    new Vec3(-Math.sin(radians), 0, Math.cos(radians)));
            collector.submitCustomGeometry(poseStack, GooSubmitter.renderType(), (pose, consumer) -> {
                Quads quads = new Quads(new RenderContext(pose, consumer, GooSubmitter.fullbrightLight()), plane,
                        portalUv, flatUv);
                emitCells(quads, rift);
                rift.embers(quads::ember);
            });
        });
    }

    /**
     * Emits every row of cells, each row's runs of like cells as one quad.
     *
     * @param quads the emitter
     * @param rift  the rift's shape
     */
    private static void emitCells(Quads quads, RiftShape rift) {
        int rows = (int) Math.ceil(RiftShape.TALLEST * RiftShape.CELLS);
        int side = (int) Math.ceil(RiftShape.WIDEST * RiftShape.CELLS);
        for (int row = 0; row < rows; row++) {
            double up = (row + HALF) / RiftShape.CELLS;
            int runStart = -side;
            RiftShape.Fill runFill = RiftShape.Fill.NONE;
            for (int cell = -side; cell <= side; cell++) {
                RiftShape.Fill fill = cell < side ? rift.fillAt((cell + HALF) / RiftShape.CELLS, up)
                        : RiftShape.Fill.NONE;
                if (fill != runFill || Math.floorMod(cell + HALF_BLOCK, RiftShape.CELLS) == 0) {
                    quads.run(runFill, runStart, cell, row, rift.rimColorAt(up));
                    runStart = cell;
                    runFill = fill;
                }
            }
        }
    }

    private static float lerp(float from, float to, double share) {
        return (float) (from + (to - from) * Math.clamp(share, 0, 1));
    }

    /**
     * The gate's plane relative to the camera.
     *
     * @param foot   the middle of its foot
     * @param across the unit way across it
     * @param facing the unit way it faces
     */
    private record Plane(Vec3 foot, Vec3 across, Vec3 facing) {

        Vec3 at(double side, double up, double lift) {
            return foot.add(across.scale(side)).add(0, up, 0).add(facing.scale(lift));
        }
    }

    /**
     * Emits the rift's quads into one frame's geometry.
     *
     * @param ctx      the render context
     * @param plane    the gate's plane
     * @param portalUv the nether portal sprite's UV rect
     * @param flatUv   the white texel sprite's UV rect
     */
    private record Quads(RenderContext ctx, Plane plane, GooRenderUtil.UvRect portalUv, GooRenderUtil.UvRect flatUv) {

        /**
         * Emits one run of like cells along a row.
         *
         * @param fill     what the run shows
         * @param from     its first cell across
         * @param to       the cell past its last
         * @param row      its row up
         * @param rimColor the row's rim color
         */
        void run(RiftShape.Fill fill, int from, int to, int row, int rimColor) {
            if (fill == RiftShape.Fill.NONE || from == to) {
                return;
            }
            double left = (double) from / RiftShape.CELLS;
            double right = (double) to / RiftShape.CELLS;
            double low = (double) row / RiftShape.CELLS;
            double high = (double) (row + 1) / RiftShape.CELLS;
            if (fill == RiftShape.Fill.RIM) {
                flat(left, right, low, high, rimColor, 0);
                return;
            }
            int column = Math.floorDiv(from + HALF_BLOCK, RiftShape.CELLS);
            int textureRow = Math.floorDiv(row, RiftShape.CELLS);
            bothWaysRound(corners(left, right, low, high), 0, corner -> new float[] {
                lerp(portalUv.u0(), portalUv.u1(), corner[0] + HALF - column),
                lerp(portalUv.v0(), portalUv.v1(), 1 - (corner[1] - textureRow))}, OPAQUE_WHITE);
        }

        /**
         * Emits one ember, a texel square on the texel grid, just before the membrane.
         *
         * @param ember the ember
         */
        void ember(RiftShape.Ember ember) {
            double left = Math.floor(ember.across() * RiftShape.TEXELS) / RiftShape.TEXELS;
            double low = Math.floor(ember.up() * RiftShape.TEXELS) / RiftShape.TEXELS;
            double size = 1.0 / RiftShape.TEXELS;
            flat(left, left + size, low, low + size, ember.color(), EMBER_LIFT);
        }

        private void flat(double left, double right, double low, double high, int color, double lift) {
            float u = lerp(flatUv.u0(), flatUv.u1(), HALF);
            float v = lerp(flatUv.v0(), flatUv.v1(), HALF);
            bothWaysRound(corners(left, right, low, high), lift, corner -> new float[] {u, v}, color);
        }

        private static double[][] corners(double left, double right, double low, double high) {
            return new double[][] {{left, low}, {right, low}, {right, high}, {left, high}};
        }

        private void bothWaysRound(double[][] corners, double lift, Function<double[], float[]> uv, int color) {
            for (double[] corner : corners) {
                vertex(corner, lift, uv.apply(corner), plane.facing(), color);
            }
            for (int index = corners.length - 1; index >= 0; index--) {
                vertex(corners[index], -lift, uv.apply(corners[index]), plane.facing().reverse(), color);
            }
        }

        private void vertex(double[] corner, double lift, float[] uv, Vec3 normal, int color) {
            Vec3 at = plane.at(corner[0], corner[1], lift);
            ctx.vertexColored(color, (float) at.x, (float) at.y, (float) at.z, uv[0], uv[1], (float) normal.x,
                    (float) normal.y, (float) normal.z);
        }
    }
}
