package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.client.throwing.GooFlightRenderer;
import com.mojang.math.Quadrant;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.cuboid.FaceBakery;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.ExtraFaceData;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the quads a slime core draws as: a body at 50% opacity on the
 * cell's goo type's fluid sprite, in its own colors as the palm blob's core
 * is, the shell's sloped body at the slime's 3/4, centred in it: at every
 * point it runs from 1/8 to 7/8 of the shell's height there, and it stands
 * 1/8 of a block in from each side no goo of its type continues past. Since
 * neighbouring shells meet at one height, so do their cores, and they join
 * smoothly. A block on a full block of its goo keeps no floor to its core
 * and the full block no top, so a column keeps one unbroken core, and a core
 * reaching a side its goo continues past draws no face there. Each face is
 * textured where it stands in its block, so the texture runs on across a
 * join as across tiled blocks. It reads no client state, so a test hands it
 * any sprite.
 * slime-core-slopes-with-the-pile-inside-its-contour
 * residual-always-draws-a-core-inside-a-one-eighth-margin
 * stacked-goo-cells-share-one-slime-core
 * palm-blob-draws-as-the-charge-blob
 */
public final class SlimeCoreQuads {

    /** Bakes each quad as it comes, sharing nothing between them. */
    static final ModelBaker.Interner UNSHARED = new ModelBaker.Interner() {
        @Override
        public Vector3fc vector(Vector3fc vector) {
            return vector;
        }

        @Override
        public BakedQuad.MaterialInfo materialInfo(BakedQuad.MaterialInfo material) {
            return material;
        }
    };
    /** Model pixels across a whole block. */
    private static final float WHOLE = 16f;
    /**
     * The core's share of its shell along every axis: the slime's own, the
     * flying blob's 3/4, centred in the shell.
     * slime-core-slopes-with-the-pile-inside-its-contour
     */
    private static final float CORE_SHARE = GooFlightRenderer.CORE_SHARE_OF_SHELL;
    /** The core's top, as a share of the shell's height over the same point. */
    private static final float CORE_TOP = (1 + CORE_SHARE) / 2;
    /** The core's floor, as a share of the shell's height over the same point. */
    private static final float CORE_FLOOR = (1 - CORE_SHARE) / 2;
    /** The core's margin in from each side no goo continues past, in model pixels: 2 px of a block's 16. */
    private static final float SIDE_MARGIN = WHOLE * CORE_FLOOR;
    /** Model pixels in one layer. */
    private static final float PIXELS_PER_LAYER = WHOLE / VolumeCellBlock.MAX_LAYERS;
    /** The least room a corner holds a core in, in blocks, against rounding. */
    private static final float ROOM_SLACK = 1e-4f;

    private SlimeCoreQuads() {
    }

    /**
     * The quads a cell's floor slab's core draws as.
     *
     * @param around   the layers the cells of the goo hold around the cell
     * @param material the core's material: the goo type's fluid sprite, on the translucent layer
     * @return the core's faces, none for a cell of no layers
     */
    public static List<BakedQuad> quadsOf(LayersAround around, BakedQuad.MaterialInfo material) {
        if (around.layersAt(0, 0, 0) == 0) {
            return List.of();
        }
        return quadsOf(SlimeTop.of(around), material);
    }

    /**
     * The quads one slab's core draws as, the floor's, a wall's or the
     * ceiling's alike: the slab's body at the slime's 3/4, centred in it.
     *
     * @param slab     the slab's heights
     * @param material the core's material
     * @return the core's faces
     */
    public static List<BakedQuad> quadsOf(SlimeTop slab, BakedQuad.MaterialInfo material) {
        return new CoreShape(slab).quadsOf(material);
    }

    /**
     * A slab's core's plan cut into patches at the shell's splits, each
     * patch held where the slab over any of its corners holds room for the
     * core; where the slab leaves no room the core's top closes onto its
     * floor, so the core ends with no side inside its plan. Distances are in model pixels: along the slab's plane by its
     * two axes, and out from its surface.
     */
    private static final class CoreShape {

        private final SlimeTop slab;
        private final SlimeTop.Plane plane;
        private final float[] firsts;
        private final float[] seconds;
        private final boolean[][] held;
        private final boolean[][] notched;

        /**
         * @param slab the slab the core stands in
         */
        CoreShape(SlimeTop slab) {
            this.slab = slab;
            this.plane = slab.plane();
            this.firsts = cutsWithin(inset(plane.first().getOpposite()), WHOLE - inset(plane.first()));
            this.seconds = cutsWithin(inset(plane.second().getOpposite()), WHOLE - inset(plane.second()));
            this.held = new boolean[firsts.length - 1][seconds.length - 1];
            this.notched = new boolean[firsts.length - 1][seconds.length - 1];
            for (int a = 0; a < held.length; a++) {
                for (int b = 0; b < held[a].length; b++) {
                    notched[a][b] = inAnInnerCorner(a, b);
                    held[a][b] = !notched[a][b] && roomAtACorner(a, b);
                }
            }
        }

        /**
         * Whether the slab over any of a patch's corners holds room for the core.
         *
         * @param a the patch's index along the first axis
         * @param b its index along the second
         * @return true where the core has room at a corner of the patch
         */
        private boolean roomAtACorner(int a, int b) {
            return roomAt(firsts[a], seconds[b]) || roomAt(firsts[a + 1], seconds[b])
                    || roomAt(firsts[a], seconds[b + 1]) || roomAt(firsts[a + 1], seconds[b + 1]);
        }

        /**
         * Whether a patch stands in the margin of a corner the goo's edge
         * turns in round, where the neighbours' cores stand in from the
         * block past the corner, so the core is notched there to meet them.
         * slime-core-slopes-with-the-pile-inside-its-contour
         *
         * @param a the patch's index along the first axis
         * @param b its index along the second
         * @return true where the core leaves the patch out
         */
        private boolean inAnInnerCorner(int a, int b) {
            Direction alongFirst = marginEdge(firsts, a, plane.first());
            Direction alongSecond = marginEdge(seconds, b, plane.second());
            return alongFirst != null && alongSecond != null && slab.turnsInAt(alongFirst, alongSecond);
        }

        /**
         * The edge whose margin a patch's span stands in along one axis.
         *
         * @param cuts     the cuts along the axis
         * @param index    the patch's index along it
         * @param positive the axis's positive edge
         * @return that edge, or null where the span stands in neither end's margin
         */
        private static @Nullable Direction marginEdge(float[] cuts, int index, Direction positive) {
            if (cuts[index + 1] <= SIDE_MARGIN + ROOM_SLACK) {
                return positive.getOpposite();
            }
            if (cuts[index] >= WHOLE - SIDE_MARGIN - ROOM_SLACK) {
                return positive;
            }
            return null;
        }

        /**
         * @param material the core's material
         * @return each held patch's faces, and a side wherever the core ends
         */
        List<BakedQuad> quadsOf(BakedQuad.MaterialInfo material) {
            List<BakedQuad> quads = new ArrayList<>();
            for (int a = 0; a < held.length; a++) {
                for (int b = 0; b < held[a].length; b++) {
                    if (held[a][b]) {
                        quads.addAll(patchOf(a, b, material));
                    }
                }
            }
            return quads;
        }

        /**
         * One held patch's faces: its outer face, save where the column runs
         * on up through it, its face toward the surface, save in a block on a
         * full block of its goo, whose core runs on down, and its sides where
         * the core ends.
         *
         * @param a        the patch's index along the plane's first axis
         * @param b        its index along the second
         * @param material the core's material
         * @return the patch's faces
         */
        private List<BakedQuad> patchOf(int a, int b, BakedQuad.MaterialInfo material) {
            List<BakedQuad> quads = new ArrayList<>();
            if (!slab.covered() || underTheTop(a, b)) {
                quads.add(spanned(face(a, b, WHOLE, plane.surface().getOpposite(), material)));
            }
            if (slab.base() == 0) {
                quads.add(spanned(face(a, b, 0, plane.surface(), material)));
            }
            quads.addAll(wallsOf(a, b, material));
            return quads;
        }

        /**
         * A held patch's sides on the core's own bound, at open sides, where the core has height.
         *
         * @param a        the patch's index along the first axis
         * @param b        its index along the second
         * @param material the core's material
         * @return the patch's sides
         */
        private List<BakedQuad> wallsOf(int a, int b, BakedQuad.MaterialInfo material) {
            List<BakedQuad> walls = new ArrayList<>();
            for (Direction edge : edges()) {
                if (endsAcross(a, b, edge) && roomAlong(a, b, edge)) {
                    walls.add(spanned(sideOf(a, b, edge, material)));
                }
            }
            return walls;
        }

        /**
         * A patch's face parallel to the slab's surface, baked flat at a distance out from it.
         *
         * @param a        the patch's index along the first axis
         * @param b        its index along the second
         * @param out      the distance out, in model pixels
         * @param facing   the side the face draws as
         * @param material the core's material
         * @return the face
         */
        private BakedQuad face(int a, int b, float out, Direction facing, BakedQuad.MaterialInfo material) {
            Vector3f from = SlimeTopQuads.pointOn(plane, firsts[a], seconds[b], out);
            Vector3f to = SlimeTopQuads.pointOn(plane, firsts[a + 1], seconds[b + 1], out);
            return SlimeTopQuads.faceOf(SlimeTopQuads.lowCorner(from, to), SlimeTopQuads.highCorner(from, to),
                    facing, material);
        }

        /**
         * A patch's side on one edge, baked flat from the surface to a block out from it.
         *
         * @param a        the patch's index along the first axis
         * @param b        its index along the second
         * @param edge     the edge, in the slab's plane
         * @param material the core's material
         * @return the side
         */
        private BakedQuad sideOf(int a, int b, Direction edge, BakedQuad.MaterialInfo material) {
            boolean acrossFirst = edge.getAxis() == plane.first().getAxis();
            float[] alongFirst = spanOf(firsts, a, acrossFirst ? edge : null);
            float[] alongSecond = spanOf(seconds, b, acrossFirst ? null : edge);
            Vector3f from = SlimeTopQuads.pointOn(plane, alongFirst[0], alongSecond[0], 0);
            Vector3f to = SlimeTopQuads.pointOn(plane, alongFirst[1], alongSecond[1], WHOLE);
            return SlimeTopQuads.faceOf(SlimeTopQuads.lowCorner(from, to), SlimeTopQuads.highCorner(from, to), edge,
                    material);
        }

        /**
         * A patch's span along one axis, or the one cut its edge stands at.
         *
         * @param cuts  the cuts along the axis
         * @param index the patch's index along it
         * @param edge  the edge across this axis, or null for the whole span
         * @return the span's low and high bounds, one cut twice for an edge
         */
        private static float[] spanOf(float[] cuts, int index, @Nullable Direction edge) {
            if (edge == null) {
                return new float[] {cuts[index], cuts[index + 1]};
            }
            float at = cuts[edge.getAxisDirection() == Direction.AxisDirection.POSITIVE ? index + 1 : index];
            return new float[] {at, at};
        }

        private BakedQuad spanned(BakedQuad flat) {
            return SlimeTopQuads.spannedOn(flat, plane, this::floorAt, this::topAt);
        }

        private List<Direction> edges() {
            return List.of(plane.first(), plane.first().getOpposite(), plane.second(), plane.second().getOpposite());
        }

        /**
         * Whether the core draws a side across one edge of a patch: only on
         * the core's own bound, at a side no slab of its goo continues past.
         *
         * @param a    the patch's index along the first axis
         * @param b    its index along the second
         * @param edge the edge
         * @return true where the patch draws a side there
         */
        private boolean endsAcross(int a, int b, Direction edge) {
            boolean acrossFirst = edge.getAxis() == plane.first().getAxis();
            int step = edge.getAxisDirection().getStep();
            int besideA = acrossFirst ? a + step : a;
            int besideB = acrossFirst ? b : b + step;
            // slime-core-slopes-with-the-pile-inside-its-contour: inside its plan the core closes onto its floor
            // where it has no room, so it ends there with no side; a side stands on its open bound and round
            // the notch an inner corner cuts
            if (inPlan(besideA, besideB)) {
                return notched[besideA][besideB];
            }
            return opensAt(edge);
        }

        /**
         * Whether the core holds room at either end of a patch's edge, so a side there stands any height.
         *
         * @param a    the patch's index along the first axis
         * @param b    its index along the second
         * @param edge the edge
         * @return true where the side has height
         */
        private boolean roomAlong(int a, int b, Direction edge) {
            boolean acrossFirst = edge.getAxis() == plane.first().getAxis();
            float[] alongFirst = spanOf(firsts, a, acrossFirst ? edge : null);
            float[] alongSecond = spanOf(seconds, b, acrossFirst ? null : edge);
            return roomAt(alongFirst[0], alongSecond[0]) || roomAt(alongFirst[1], alongSecond[1]);
        }

        private boolean inPlan(int a, int b) {
            return a >= 0 && b >= 0 && a < held.length && b < held[0].length;
        }

        /**
         * Whether the column's core top stands under this block's top at all
         * four corners of a patch: only there does a full block under goo
         * draw it, and where any corner rises past, the block on top draws
         * the patch, so the two never draw one patch twice.
         * stacked-goo-cells-share-one-slime-core
         *
         * @param a the patch's index along the first axis
         * @param b its index along the second
         * @return true where this block draws the patch's core top
         */
        private boolean underTheTop(int a, int b) {
            return rawTopAt(firsts[a], seconds[b]) < WHOLE - ROOM_SLACK
                    && rawTopAt(firsts[a + 1], seconds[b]) < WHOLE - ROOM_SLACK
                    && rawTopAt(firsts[a], seconds[b + 1]) < WHOLE - ROOM_SLACK
                    && rawTopAt(firsts[a + 1], seconds[b + 1]) < WHOLE - ROOM_SLACK;
        }

        /**
         * The core's outer face at a point: 7/8 of the pile's surface over
         * it, measured from the column's base, a whole block at the most, and
         * down on its floor where that leaves no room.
         *
         * @param first  model pixels along the first axis
         * @param second model pixels along the second
         * @return the distance out from the slab's surface, in model pixels
         */
        private float topAt(float first, float second) {
            return Math.max(floorAt(first, second), Math.min(WHOLE, rawTopAt(first, second)));
        }

        /**
         * @param first  model pixels along the first axis
         * @param second model pixels along the second
         * @return 7/8 of the pile's surface over the point from the column's base, out from the slab's surface
         */
        private float rawTopAt(float first, float second) {
            return ((float) slab.surfaceAtPixel(first, second) * CORE_TOP - slab.base()) * PIXELS_PER_LAYER;
        }

        /**
         * The core's face toward the surface at a point: 1/8 of the pile's
         * surface over it; in a block on a full block of its goo the block's
         * own floor, where the column's core runs on down, or the core's top
         * where that dips under the floor, so the core closes there inside the
         * column's.
         *
         * @param first  model pixels along the first axis
         * @param second model pixels along the second
         * @return the distance out from the slab's surface, in model pixels
         */
        private float floorAt(float first, float second) {
            if (slab.base() > 0) {
                // stacked-goo-cells-share-one-slime-core: a column's core runs on down, closing where its top dips
                return Math.min(0, rawTopAt(first, second));
            }
            float surface = (float) slab.surfaceAtPixel(first, second);
            return Math.max(0, surface * CORE_FLOOR * PIXELS_PER_LAYER);
        }

        private boolean roomAt(float first, float second) {
            return topAt(first, second) - floorAt(first, second) > ROOM_SLACK;
        }

        /**
         * @param edge an edge of the slab
         * @return true where no slab of its goo continues past it on its surface
         */
        private boolean opensAt(Direction edge) {
            return slab.rims().contains(edge) || slab.joins().contains(edge);
        }

        /**
         * @param edge an edge of the slab
         * @return how far in the core stands from it: its margin where it opens, else none
         */
        private float inset(Direction edge) {
            return opensAt(edge) ? SIDE_MARGIN : 0;
        }
    }

    /**
     * A span's bounds with each of the shell's splits inside it.
     *
     * @param min the span's low bound, in model pixels
     * @param max its high bound
     * @return the bounds and each split between them, in order
     */
    private static float[] cutsWithin(float min, float max) {
        List<Float> cuts = new ArrayList<>();
        cuts.add(min);
        for (float cut : SlimeTopQuads.CUTS) {
            if (cut > min && cut < max) {
                cuts.add(cut);
            }
        }
        cuts.add(max);
        float[] bounds = new float[cuts.size()];
        for (int i = 0; i < bounds.length; i++) {
            bounds[i] = cuts.get(i);
        }
        return bounds;
    }

    /**
     * One face of a box as a block model bakes it, textured where it stands in its block.
     *
     * @param from     the box's low corner, in model pixels
     * @param to       its high corner
     * @param face     the side
     * @param material the core's material
     * @return the face's quad
     */
    static BakedQuad faceOf(Vector3fc from, Vector3fc to, Direction face, BakedQuad.MaterialInfo material) {
        return FaceBakery.bakeQuad(UNSHARED, from, to, FaceBakery.defaultFaceUV(from, to, face), Quadrant.R0,
                material, face, BlockModelRotation.IDENTITY, null, ExtraFaceData.DEFAULT);
    }
}
