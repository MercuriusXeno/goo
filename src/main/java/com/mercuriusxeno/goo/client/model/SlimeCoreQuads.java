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
    /** The offset to the cell below. */
    private static final int BELOW = -1;
    /** The least room a corner holds a core in, in blocks, against rounding. */
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
        return new CoreShape(SlimeTop.of(around), column).quadsOf(material);
    }

    /**
     * The core's plan cut into patches at the shell's splits, each patch
     * held where the shell over all four of its corners holds goo.
     */
    private static final class CoreShape {

        private final SlimeTop top;
        private final boolean column;
        private final float[] xs;
        private final float[] zs;
        private final boolean[][] held;

        /**
         * @param top    the shell's top over the core
         * @param column whether the block stands on a full block of its goo, its core running on down
         */
        CoreShape(SlimeTop top, boolean column) {
            this.top = top;
            this.column = column;
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
                quads.add(spanned(faceOf(new Vector3f(xs[x], WHOLE, zs[z]), new Vector3f(xs[x + 1], WHOLE,
                        zs[z + 1]), Direction.UP, material)));
            }
            if (!column) {
                quads.add(spanned(faceOf(new Vector3f(xs[x], 0, zs[z]), new Vector3f(xs[x + 1], 0, zs[z + 1]),
                        Direction.DOWN, material)));
            }
            quads.addAll(wallsOf(x, z, material));
            return quads;
        }

        /**
         * A held patch's sides on each edge where the core ends.
         *
         * @param x        the patch's index west to east
         * @param z        its index north to south
         * @param material the core's material
         * @return the patch's sides
         */
        private List<BakedQuad> wallsOf(int x, int z, BakedQuad.MaterialInfo material) {
            List<BakedQuad> walls = new ArrayList<>();
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (endsAcross(x, z, side)) {
                    walls.add(spanned(sideOf(x, z, side, material)));
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
         * A patch's side on one edge, baked flat over the block's height.
         *
         * @param x        the patch's index west to east
         * @param z        its index north to south
         * @param side     the edge
         * @param material the core's material
         * @return the side, its lower vertices to stand on the core's floor and its upper on its top
         */
        private BakedQuad sideOf(int x, int z, Direction side, BakedQuad.MaterialInfo material) {
            float west = side == Direction.EAST ? xs[x + 1] : xs[x];
            float east = side == Direction.WEST ? xs[x] : xs[x + 1];
            float north = side == Direction.SOUTH ? zs[z + 1] : zs[z];
            float south = side == Direction.NORTH ? zs[z] : zs[z + 1];
            return faceOf(new Vector3f(west, 0, north), new Vector3f(east, WHOLE, south), side, material);
        }

        /**
         * A face baked flat between the block's floor and top, its vertices
         * moved onto the core: those at the floor to its floor, those at the
         * top to its top.
         *
         * @param flat the face baked flat
         * @return the face standing on the core
         */
        private BakedQuad spanned(BakedQuad flat) {
            return SlimeTopQuads.spanned(flat, this::floorAt, this::topAt);
        }

        /**
         * The core's top at a point: 7/8 of the shell's height there, the
         * whole block where goo of its type covers it and the column runs on up.
         *
         * @param x model pixels east of the block's west side
         * @param z model pixels south of its north side
         * @return the top, in blocks above the block's floor
         */
        private float topAt(float x, float z) {
            return top.covered() ? 1 : shellAt(x, z) * CORE_TOP;
        }

        /**
         * The core's floor at a point: 1/8 of the shell's height there, the
         * block's own floor where the column runs on down.
         *
         * @param x model pixels east of the block's west side
         * @param z model pixels south of its north side
         * @return the floor, in blocks above the block's floor
         */
        private float floorAt(float x, float z) {
            return column ? 0 : shellAt(x, z) * CORE_FLOOR;
        }

        /**
         * @param x model pixels east of the block's west side
         * @param z model pixels south of its north side
         * @return the shell's height there, in blocks, a whole block where goo of its type covers it
         */
        private float shellAt(float x, float z) {
            return top.covered() ? 1 : (float) top.heightAtPixel(x, z) / VolumeCellBlock.MAX_LAYERS;
        }

        private boolean roomAt(float x, float z) {
            return topAt(x, z) - floorAt(x, z) > ROOM_SLACK;
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
