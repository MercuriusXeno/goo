package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mojang.math.Quadrant;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.cuboid.FaceBakery;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.ExtraFaceData;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the quads a slime core draws as: a body at 50% opacity on the
 * cell's goo type's fluid sprite, in its own colors as the palm blob's core
 * is, following the sloped top from inside. At every point its top stands
 * 0.4 px under the shell's top, its floor 0.4 px over the block's floor, and
 * it stays 2 px in from each side no goo of its type continues past; where
 * the slope leaves less room than that the core ends. A block on a full
 * block of its goo keeps no floor to its core and the full block no top, so
 * a column keeps one unbroken core, and a core reaching a side its goo
 * continues past draws no face there, meeting the core across at one height.
 * Each face is textured where it stands in its block, so the texture runs on
 * across a join as across tiled blocks. It reads no client state, so a test
 * hands it any sprite.
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
     * The margin in from each side no goo continues past, 2 px.
     * residual-always-draws-a-core-inside-a-one-eighth-margin
     */
    private static final float SIDE_MARGIN = 2f;
    /**
     * The margin under the shell's top and over the floor, 0.4 px.
     * residual-always-draws-a-core-inside-a-one-eighth-margin
     */
    private static final float VERTICAL_MARGIN = 0.4f;
    /** Model pixels in one layer. */
    private static final float PIXELS_PER_LAYER = WHOLE / VolumeCellBlock.MAX_LAYERS;
    /** The offset to the cell below. */
    private static final int BELOW = -1;
    /** How far short of the margin a corner still counts as holding room, against rounding. */
    private static final float ROOM_SLACK = 1e-4f;

    private SlimeCoreQuads() {
    }

    /**
     * The quads a cell's core draws as.
     *
     * @param around   the layers the cells of the goo hold around the cell
     * @param material the core's material: the goo type's fluid sprite, on the translucent layer
     * @return the core's faces, none for a cell of no layers
     */
    public static List<BakedQuad> quadsOf(LayersAround around, BakedQuad.MaterialInfo material) {
        if (around.layersAt(0, 0, 0) == 0) {
            return List.of();
        }
        boolean column = around.layersAt(0, BELOW, 0) == VolumeCellBlock.MAX_LAYERS;
        return new CoreShape(SlimeTop.of(around), column ? 0 : VERTICAL_MARGIN).quadsOf(material);
    }

    /**
     * The core's plan cut into patches at the shell's splits, each patch
     * held where the slope leaves room over all four of its corners.
     */
    private static final class CoreShape {

        private final SlimeTop top;
        private final float floor;
        private final float[] xs;
        private final float[] zs;
        private final boolean[][] held;

        /**
         * @param top   the shell's top over the core
         * @param floor the core's floor, in model pixels, 0 for a column's upper block
         */
        CoreShape(SlimeTop top, float floor) {
            this.top = top;
            this.floor = floor;
            this.xs = cutsWithin(inset(Direction.WEST), WHOLE - inset(Direction.EAST));
            this.zs = cutsWithin(inset(Direction.NORTH), WHOLE - inset(Direction.SOUTH));
            this.held = new boolean[xs.length - 1][zs.length - 1];
            for (int x = 0; x < held.length; x++) {
                for (int z = 0; z < held[x].length; z++) {
                    held[x][z] = roomAt(xs[x], zs[z]) && roomAt(xs[x + 1], zs[z]) && roomAt(xs[x], zs[z + 1])
                            && roomAt(xs[x + 1], zs[z + 1]);
                }
            }
        }

        /**
         * @param material the core's material
         * @return each held patch's top and floor, and a side wherever the core ends
         */
        List<BakedQuad> quadsOf(BakedQuad.MaterialInfo material) {
            List<BakedQuad> quads = new ArrayList<>();
            for (int x = 0; x < held.length; x++) {
                for (int z = 0; z < held[x].length; z++) {
                    if (held[x][z]) {
                        quads.addAll(patchOf(x, z, material));
                    }
                }
            }
            return quads;
        }

        /**
         * One held patch's faces: its top unless goo of its type covers the
         * block, its floor unless it stands on a full block of its goo, and its
         * sides where the core ends.
         *
         * @param x        the patch's index west to east
         * @param z        its index north to south
         * @param material the core's material
         * @return the patch's faces
         */
        private List<BakedQuad> patchOf(int x, int z, BakedQuad.MaterialInfo material) {
            List<BakedQuad> quads = new ArrayList<>();
            if (!top.covered()) {
                quads.add(SlimeTopQuads.raised(faceOf(new Vector3f(xs[x], 0, zs[z]), new Vector3f(xs[x + 1], 0,
                        zs[z + 1]), Direction.UP, material), this::topAt, false));
            }
            if (floor > 0) {
                quads.add(faceOf(new Vector3f(xs[x], floor, zs[z]), new Vector3f(xs[x + 1], floor, zs[z + 1]),
                        Direction.DOWN, material));
            }
            quads.addAll(wallsOf(x, z, material));
            return quads;
        }

        /**
         * A held patch's sides on each edge where the core ends, each standing
         * from the core's floor up to its top, none where the top meets the floor.
         *
         * @param x        the patch's index west to east
         * @param z        its index north to south
         * @param material the core's material
         * @return the patch's sides
         */
        private List<BakedQuad> wallsOf(int x, int z, BakedQuad.MaterialInfo material) {
            List<BakedQuad> walls = new ArrayList<>();
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BakedQuad wall = endsAcross(x, z, side)
                        ? SlimeTopQuads.raised(sideOf(x, z, side, material), this::topAt, true) : null;
                if (wall != null && SlimeTopQuads.tallestOf(wall) > floor / WHOLE + ROOM_SLACK) {
                    walls.add(wall);
                }
            }
            return walls;
        }

        /**
         * Whether the core ends across one edge of a patch: the patch beside
         * it holds no core, or the edge is the core's own bound on a side no
         * goo continues past.
         *
         * @param x    the patch's index west to east
         * @param z    its index north to south
         * @param side the edge
         * @return true where the patch draws a side there
         */
        private boolean endsAcross(int x, int z, Direction side) {
            int besideX = x + side.getStepX();
            int besideZ = z + side.getStepZ();
            if (besideX < 0 || besideZ < 0 || besideX >= held.length || besideZ >= held[0].length) {
                return top.rims().contains(side);
            }
            return !held[besideX][besideZ];
        }

        /**
         * A patch's side on one edge, baked flat from the core's floor to the block's top.
         *
         * @param x        the patch's index west to east
         * @param z        its index north to south
         * @param side     the edge
         * @param material the core's material
         * @return the side, its upper vertices to raise to the core's top
         */
        private BakedQuad sideOf(int x, int z, Direction side, BakedQuad.MaterialInfo material) {
            float west = side == Direction.EAST ? xs[x + 1] : xs[x];
            float east = side == Direction.WEST ? xs[x] : xs[x + 1];
            float north = side == Direction.SOUTH ? zs[z + 1] : zs[z];
            float south = side == Direction.NORTH ? zs[z] : zs[z + 1];
            return faceOf(new Vector3f(west, floor, north), new Vector3f(east, WHOLE, south), side, material);
        }

        /**
         * The core's top at a point, in blocks: 0.4 px under the shell's top,
         * the whole block where goo of its type covers it, never under its floor.
         *
         * @param x model pixels east of the block's west side
         * @param z model pixels south of its north side
         * @return the top, in blocks above the block's floor
         */
        private float topAt(float x, float z) {
            return Math.max(floor, roofAt(x, z)) / WHOLE;
        }

        /**
         * @param x model pixels east of the block's west side
         * @param z model pixels south of its north side
         * @return the highest the core may stand there, in model pixels
         */
        private float roofAt(float x, float z) {
            return top.covered() ? WHOLE : (float) top.heightAtPixel(x, z) * PIXELS_PER_LAYER - VERTICAL_MARGIN;
        }

        private boolean roomAt(float x, float z) {
            return roofAt(x, z) >= floor - ROOM_SLACK;
        }

        /**
         * @param side a side of the block
         * @return how far in the core stands from it: its margin where no goo continues past, else none
         */
        private float inset(Direction side) {
            return top.rims().contains(side) ? SIDE_MARGIN : 0;
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
