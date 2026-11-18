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

/**
 * Draws Tartarus' own portal, the doorway the operator settled on, in place
 * of the door End and Zone draw, as the artifact draft drew it: one block
 * wide and two tall, texel by texel, each texel of it that shows drawn with
 * its own texel of the nether portal's animated texture, fixed in the
 * doorway's plane as the vanilla portal block's; each row's run of showing
 * texels draws as one quad. Its motes are flat color over a white texel, a
 * texel square each; all of it fullbright, seen from either side.
 * decision tartarus-draws-its-own-portal-with-the-nether-portal-texture
 * Where it stands at the camera, a first-person caster's own doorway reaching
 * them, the nether portal's texture covers the view.
 * decision tartarus-draws-its-own-portal-with-the-nether-portal-texture
 */
public final class TartarusGateRenderer implements GateDrawing {

    /** The drawing. */
    public static final TartarusGateRenderer INSTANCE = new TartarusGateRenderer();
    /** The nether portal's texture on the block atlas, animated as the block's own. */
    public static final Identifier TEXTURE = Identifier.withDefaultNamespace("block/nether_portal");
    /** A white texel on the block atlas, which the motes tint. */
    static final Identifier FLAT_TEXTURE = Identifier.fromNamespaceAndPath(Goo.MODID, "block/flat_white");
    /** How far before the doorway a mote draws, so it shows over it from either side, in blocks. */
    private static final double MOTE_LIFT = 0.002;
    private static final double TEXEL = 1.0 / TexelDoorway.TEXELS;
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
     * Submits one doorway with the sprites given.
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
        TexelDoorway.of(door, time).ifPresent(doorway -> {
            double radians = Math.toRadians(door.yaw());
            Plane plane = new Plane(door.at().subtract(camera), new Vec3(Math.cos(radians), 0, Math.sin(radians)),
                    new Vec3(-Math.sin(radians), 0, Math.cos(radians)));
            collector.submitCustomGeometry(poseStack, GooSubmitter.renderType(), (pose, consumer) -> {
                Quads quads = new Quads(new RenderContext(pose, consumer, GooSubmitter.fullbrightLight()), plane,
                        portalUv, flatUv);
                emitTexels(quads, doorway);
                doorway.motes(quads::mote);
            });
        });
    }

    /**
     * Emits every row of the doorway, each run of showing texels as one quad.
     *
     * @param quads   the emitter
     * @param doorway the doorway
     */
    private static void emitTexels(Quads quads, TexelDoorway doorway) {
        for (int up = 0; up < TexelDoorway.UP; up++) {
            int across = 0;
            while (across < TexelDoorway.ACROSS) {
                int end = runEnd(doorway, across, up);
                if (end > across) {
                    quads.run(across, end, up);
                }
                across = Math.max(end, across + 1);
            }
        }
    }

    /**
     * Where a run of showing texels starting at a texel ends.
     *
     * @param doorway the doorway
     * @param from    the texel across the run starts at
     * @param up      the row up
     * @return the texel past the run's last, the start itself where it shows not
     */
    private static int runEnd(TexelDoorway doorway, int from, int up) {
        int end = from;
        while (end < TexelDoorway.ACROSS && doorway.shows(end, up)) {
            end++;
        }
        return end;
    }

    private static float lerp(float from, float to, double share) {
        return (float) (from + (to - from) * Math.clamp(share, 0, 1));
    }

    /**
     * The doorway's plane relative to the camera.
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
     * Emits the doorway's quads into one frame's geometry.
     *
     * @param ctx      the render context
     * @param plane    the doorway's plane
     * @param portalUv the nether portal sprite's UV rect
     * @param flatUv   the white texel sprite's UV rect
     */
    private record Quads(RenderContext ctx, Plane plane, GooRenderUtil.UvRect portalUv, GooRenderUtil.UvRect flatUv) {

        /**
         * Emits one run of showing texels along a row, each on its own texel of the texture.
         *
         * @param from the run's first texel across
         * @param to   the texel past its last
         * @param up   its row up
         */
        void run(int from, int to, int up) {
            double left = from * TEXEL - HALF;
            double right = to * TEXEL - HALF;
            double low = up * TEXEL;
            double high = (up + 1) * TEXEL;
            int row = up % TexelDoorway.TEXELS;
            float v0 = lerp(portalUv.v0(), portalUv.v1(), 1 - (row + 1) * TEXEL);
            float v1 = lerp(portalUv.v0(), portalUv.v1(), 1 - row * TEXEL);
            float u0 = lerp(portalUv.u0(), portalUv.u1(), from * TEXEL);
            float u1 = lerp(portalUv.u0(), portalUv.u1(), to * TEXEL);
            quad(left, right, low, high, 0, new float[][] {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}}, OPAQUE_WHITE);
        }

        /**
         * Emits one mote, a texel square on the texel grid, just before the doorway.
         *
         * @param mote the mote
         */
        void mote(TexelDoorway.Mote mote) {
            double left = Math.floor(mote.across() / TEXEL) * TEXEL;
            double low = Math.floor(mote.up() / TEXEL) * TEXEL;
            float u = lerp(flatUv.u0(), flatUv.u1(), HALF);
            float v = lerp(flatUv.v0(), flatUv.v1(), HALF);
            quad(left, left + TEXEL, low, low + TEXEL, MOTE_LIFT, new float[][] {{u, v}, {u, v}, {u, v}, {u, v}},
                    mote.color());
        }

        /**
         * Emits a quad from its lower edge to its upper, both ways round; the UVs pair with the corners in order.
         *
         * @param left  its left edge across, in blocks
         * @param right its right edge across, in blocks
         * @param low   its lower edge up, in blocks
         * @param high  its upper edge up, in blocks
         * @param lift  how far before the plane it draws, in blocks
         * @param uvs   each corner's UV, lower left first and counterclockwise
         * @param color its ARGB color
         */
        private void quad(double left, double right, double low, double high, double lift, float[][] uvs,
                          int color) {
            double[][] corners = {{left, low}, {right, low}, {right, high}, {left, high}};
            for (int index = 0; index < corners.length; index++) {
                vertex(corners[index], lift, uvs[index], plane.facing(), color);
            }
            for (int index = corners.length - 1; index >= 0; index--) {
                vertex(corners[index], -lift, uvs[index], plane.facing().reverse(), color);
            }
        }

        private void vertex(double[] corner, double lift, float[] uv, Vec3 normal, int color) {
            Vec3 at = plane.at(corner[0], corner[1], lift);
            ctx.vertexColored(color, (float) at.x, (float) at.y, (float) at.z, uv[0], uv[1], (float) normal.x,
                    (float) normal.y, (float) normal.z);
        }
    }
}
