package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.ability.MeltMeshNoise;
import com.mercuriusxeno.goo.client.ability.MingledGoo;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A block melting like wax under Unmake: its own model, every face split into
 * a fine grid and warped smoothly as it melts, its top sinking, its foot
 * bulging and its top edges drooping, with a ripple running through it; and
 * over it the goo it is made of, patches of its own goo types mingled by their
 * shares, spreading across its surface as it melts. A block that cannot sag
 * shows the goo spreading over it without the warp.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class MeltMesh {

    /** Cells along each side of a face; enough that the warp reads as one smooth shape. */
    static final int GRID = 8;
    /** The share of its height a fully melted block loses. */
    static final float SINK = 0.62f;
    /** How far a fully melted block's top edges droop below its middle, in blocks. */
    static final float DROOP = 0.22f;
    /** How far a fully melted block's foot bulges past its footprint, as a share of its half width. */
    static final float BULGE = 0.38f;
    /** How far a fully melted block's top pinches in, as a share of its half width. */
    static final float PINCH = 0.18f;
    /** How far the ripple through a melting block swings it, as a share of its half width. */
    static final float RIPPLE = 0.035f;
    /** Ripple cycles a tick. */
    static final float RIPPLE_RATE = 0.05f;
    /** How far the goo stands off the surface, so it never fights the block's faces. */
    private static final float GOO_LIFT = 0.003f;
    /** The goo's alpha where a patch has fully formed. */
    private static final int GOO_ALPHA = 0xEE;
    /** How wide the edge of a forming patch is, as a share of the melt. */
    private static final float PATCH_EDGE = 0.18f;
    private static final float HALF = 0.5f;
    private static final float RIPPLE_HEIGHT_FREQ = 7f;
    private static final float RIPPLE_AROUND_FREQ = 3f;
    private static final double TWO_PI = 2 * Math.PI;
    /** The most goo types layered over one block; past three the patches read as noise. */
    private static final int MAX_LAYERS = 3;
    /** How fine the goo's patches are: field cells per block. */
    private static final float PATCH_SCALE = 2.5f;
    /** How far past its share a lesser type's patches cover, so a small share still shows. */
    private static final float PATCH_COVERAGE = 1.6f;
    /** How soft a patch's edge is, in field share either side of it. */
    private static final float PATCH_SOFTNESS = 0.08f;
    private static final long LAYER_SALT = 0x9E37_79B9L;
    private static final long PATCH_SALT = 0x7F4A_7C15L;
    /** The cubic smoothstep's constant term. */
    private static final float SMOOTH_BASE = 3f;
    /** The cubic smoothstep's slope term. */
    private static final float SMOOTH_SLOPE = 2f;
    private static final long MODEL_SEED = 42L;
    /** How much further the goo stands off a block that does not sag, whose faces it lies flat on. */
    private static final float UNSAGGED_LIFT = 2f;
    private static final int FIRST = 0;
    private static final int SECOND = 1;
    private static final int THIRD = 2;
    private static final int FOURTH = 3;

    private MeltMesh() {
    }

    /**
     * One melt to draw.
     *
     * @param state  the block melting
     * @param level  the level, for its tint
     * @param pos    where it stands
     * @param goo    the goo it melts into
     * @param melt   how far it has melted, 0 whole to 1 slumped
     * @param ticks  the game time including the partial tick
     * @param sags   whether the block itself is drawn and warps, or only the goo spreads over it
     */
    public record Melt(BlockState state, BlockAndTintGetter level, BlockPos pos, MingledGoo goo, float melt,
                       float ticks, boolean sags) {
    }

    /**
     * Emits a melt's block and goo through a body context in block-local coordinates.
     *
     * @param ctx  the render context, its pose at the block's corner
     * @param melt the melt
     */
    public static void emit(RenderContext ctx, Melt melt) {
        int quadIndex = 0;
        for (BakedQuad quad : quadsOf(melt.state(), melt.level(), melt.pos())) {
            emitQuad(ctx, melt, quad, quadIndex++);
        }
    }

    /**
     * Every quad of a block's model where it stands, each face's then the free ones.
     *
     * @param state the block
     * @param level the level it stands in
     * @param pos   where it stands
     * @return its quads
     */
    public static List<BakedQuad> quadsOf(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        List<BlockStateModelPart> parts = new ArrayList<>();
        Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state)
                .collectParts(level, pos, state, RandomSource.create(MODEL_SEED), parts);
        List<BakedQuad> quads = new ArrayList<>();
        for (BlockStateModelPart part : parts) {
            for (Direction side : Direction.values()) {
                quads.addAll(part.getQuads(side));
            }
            quads.addAll(part.getQuads(null));
        }
        return quads;
    }

    /**
     * One point of a block's face, with the texture and the baked colour its quad gives it there.
     *
     * @param x     the point's x, block-local
     * @param y     the point's y, block-local
     * @param z     the point's z, block-local
     * @param u     its texture u on the atlas
     * @param v     its texture v on the atlas
     * @param color its baked ARGB colour
     */
    public record FacePoint(float x, float y, float z, float u, float v, int color) {
    }

    /**
     * A quad split into its fine grid: the four corners of every cell, in
     * the quad's winding, so a mesh bending the block keeps its textures.
     *
     * @param quad the quad
     * @return each cell's corners
     */
    public static List<FacePoint[]> cellsOf(BakedQuad quad) {
        List<QuadRectClipper.ClipVertex> corners = QuadRectClipper.verticesOf(quad);
        List<FacePoint[]> cells = new ArrayList<>(GRID * GRID);
        for (int index = 0; index < GRID * GRID; index++) {
            Cell cell = new Cell(corners, index / GRID, index % GRID);
            FacePoint[] points = new FacePoint[CELL_CORNERS.length];
            for (int corner = 0; corner < CELL_CORNERS.length; corner++) {
                QuadRectClipper.ClipVertex point = cell.at(CELL_CORNERS[corner][0], CELL_CORNERS[corner][1]);
                points[corner] = new FacePoint(point.x(), point.y(), point.z(), point.u(), point.v(), point.color());
            }
            cells.add(points);
        }
        return cells;
    }

    /**
     * Emits one quad of the block, split into its grid, then its goo.
     *
     * @param ctx       the render context
     * @param melt      the melt
     * @param quad      the quad
     * @param quadIndex the quad's index, seeding its patches
     */
    private static void emitQuad(RenderContext ctx, Melt melt, BakedQuad quad, int quadIndex) {
        List<QuadRectClipper.ClipVertex> corners = QuadRectClipper.verticesOf(quad);
        Vec3 normal = Vec3.atLowerCornerOf(quad.direction().getUnitVec3i());
        int tint = tintOf(melt, quad);
        if (melt.sags()) {
            for (int cell = 0; cell < GRID * GRID; cell++) {
                emitBlockCell(ctx, melt, new Cell(corners, cell / GRID, cell % GRID), normal, tint);
            }
        }
        int layers = Math.min(melt.goo().types().size(), MAX_LAYERS);
        for (int layer = 0; layer < layers; layer++) {
            emitGooLayer(ctx, melt, corners, normal,
                    new GooLayer(melt.goo().types().get(layer), layer, melt.goo().share(layer)));
        }
    }

    /**
     * Emits one goo layer over a quad, cell by cell.
     *
     * @param ctx     the render context
     * @param melt    the melt
     * @param corners the quad's corners
     * @param normal  the face's normal
     * @param goo     the layer
     */
    private static void emitGooLayer(RenderContext ctx, Melt melt, List<QuadRectClipper.ClipVertex> corners,
                                     Vec3 normal, GooLayer goo) {
        for (int cell = 0; cell < GRID * GRID; cell++) {
            emitGooCell(ctx, melt, new Cell(corners, cell / GRID, cell % GRID), normal, goo);
        }
    }

    /**
     * One goo type's layer over melting matter: the largest type the base,
     * laid everywhere the melt has reached; each other type in soft patches
     * over it, covering about its share. A drink's stream wears the same
     * layers, the melt there being how far along the stream a point is.
     *
     * @param type  the goo type
     * @param index its index, largest first
     * @param share its share of the whole
     */
    public record GooLayer(ResourceKey<GooTypeDefinition> type, int index, float share) {

        /**
         * How opaque this layer is at a point: formed once the melt reaches
         * the point's own share of a smooth field, and, past the base, only
         * inside its soft patches.
         *
         * @param x    the point's x, in blocks
         * @param y    the point's y, in blocks
         * @param z    the point's z, in blocks
         * @param melt how far the matter there has melted, 0 to 1
         * @return the layer's opacity there, 0 to 1
         */
        public float opacityAt(float x, float y, float z, float melt) {
            long seed = index * LAYER_SALT;
            float formed = patchFormed(melt, MeltMeshNoise.smooth(x * PATCH_SCALE, y * PATCH_SCALE, z * PATCH_SCALE,
                    seed));
            if (index == 0) {
                return formed;
            }
            double field = MeltMeshNoise.smooth(x * PATCH_SCALE, y * PATCH_SCALE, z * PATCH_SCALE, seed + PATCH_SALT);
            float edge = 1f - share * PATCH_COVERAGE;
            return formed * smoothRamp(edge - PATCH_SOFTNESS, edge + PATCH_SOFTNESS, (float) field);
        }
    }

    /**
     * @param edge0 where the ramp starts
     * @param edge1 where it ends
     * @param x     the input
     * @return the smooth ramp from 0 at edge0 to 1 at edge1
     */
    static float smoothRamp(float edge0, float edge1, float x) {
        float t = Math.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (SMOOTH_BASE - SMOOTH_SLOPE * t);
    }

    /**
     * One cell of a quad's grid: its four corners, in the quad's winding.
     *
     * @param corners the quad's corners
     * @param i       the cell's column
     * @param j       the cell's row
     */
    private record Cell(List<QuadRectClipper.ClipVertex> corners, int i, int j) {

        /**
         * @param di 0 or 1, which column edge
         * @param dj 0 or 1, which row edge
         * @return the point of the quad at that corner of the cell
         */
        QuadRectClipper.ClipVertex at(int di, int dj) {
            float s = (float) (i + di) / GRID;
            float t = (float) (j + dj) / GRID;
            QuadRectClipper.ClipVertex top = corners.get(FIRST).toward(corners.get(SECOND), s);
            QuadRectClipper.ClipVertex bottom = corners.get(FOURTH).toward(corners.get(THIRD), s);
            return top.toward(bottom, t);
        }
    }

    private static final int[][] CELL_CORNERS = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

    /**
     * Emits one cell of the block's own face, warped.
     *
     * @param ctx    the render context
     * @param melt   the melt
     * @param cell   the cell
     * @param normal the face's normal
     * @param tint   the face's tint
     */
    private static void emitBlockCell(RenderContext ctx, Melt melt, Cell cell, Vec3 normal, int tint) {
        for (int[] corner : CELL_CORNERS) {
            QuadRectClipper.ClipVertex point = cell.at(corner[0], corner[1]);
            Vec3 warped = warp(point.x(), point.y(), point.z(), melt.melt(), melt.ticks());
            ctx.vertexColored(ARGB.multiply(tint, point.color()), (float) warped.x, (float) warped.y,
                    (float) warped.z, point.u(), point.v(), (float) normal.x, (float) normal.y, (float) normal.z);
        }
    }

    /**
     * Emits one cell of a goo layer, each corner as opaque as the layer is
     * there, so its patches fade in softly with no seams; its sprite is laid
     * once across the face, continuous from cell to cell.
     *
     * @param ctx    the render context
     * @param melt   the melt
     * @param cell   the cell
     * @param normal the face's normal
     * @param goo    the layer
     */
    private static void emitGooCell(RenderContext ctx, Melt melt, Cell cell, Vec3 normal, GooLayer goo) {
        float sagging = melt.sags() ? melt.melt() : 0f;
        float lift = (melt.sags() ? GOO_LIFT : GOO_LIFT * UNSAGGED_LIFT) * (goo.index() + 1);
        int[] alphas = new int[CELL_CORNERS.length];
        boolean shows = false;
        for (int corner = 0; corner < CELL_CORNERS.length; corner++) {
            QuadRectClipper.ClipVertex point = cell.at(CELL_CORNERS[corner][0], CELL_CORNERS[corner][1]);
            alphas[corner] = Math.round(GOO_ALPHA * goo.opacityAt(point.x(), point.y(), point.z(), melt.melt()));
            shows |= alphas[corner] > 0;
        }
        if (!shows) {
            return;
        }
        GooRenderUtil.UvRect sprite = GooSubmitter.spriteUv(GooRenderUtil.lookupFluidSprite(goo.type()));
        for (int corner = 0; corner < CELL_CORNERS.length; corner++) {
            QuadRectClipper.ClipVertex point = cell.at(CELL_CORNERS[corner][0], CELL_CORNERS[corner][1]);
            Vec3 warped = warp(point.x(), point.y(), point.z(), sagging, melt.ticks()).add(normal.scale(lift));
            float s = (float) (cell.i() + CELL_CORNERS[corner][0]) / GRID;
            float t = (float) (cell.j() + CELL_CORNERS[corner][1]) / GRID;
            ctx.vertexColored(ARGB.color(alphas[corner], GooRenderUtil.OPAQUE_WHITE), (float) warped.x,
                    (float) warped.y, (float) warped.z, sprite.u0() + (sprite.u1() - sprite.u0()) * s,
                    sprite.v0() + (sprite.v1() - sprite.v0()) * t, (float) normal.x, (float) normal.y,
                    (float) normal.z);
        }
    }

    /**
     * How formed a patch is: none until the melt reaches the patch's own
     * share, then thickening over a short edge, so patches spread across the
     * surface in a scatter as the block melts, all formed by the end.
     *
     * @param melt  how far the block has melted
     * @param share the patch's random share, 0 to 1
     * @return how formed it is, 0 to 1
     */
    static float patchFormed(float melt, double share) {
        float start = (float) share * (1f - PATCH_EDGE);
        return Math.clamp((melt - start) / PATCH_EDGE, 0f, 1f);
    }

    /**
     * Where a point of a block stands as it melts like wax: its height sinks,
     * the top edges droop, the foot bulges and the top pinches, and a ripple
     * runs through it. A whole block stands as it was.
     *
     * @param x     the point's x, block-local
     * @param y     the point's y, block-local
     * @param z     the point's z, block-local
     * @param melt  how far the block has melted, 0 to 1
     * @param ticks the game time including the partial tick
     * @return where the point stands
     */
    static Vec3 warp(float x, float y, float z, float melt, float ticks) {
        float dx = x - HALF;
        float dz = z - HALF;
        float edge = Math.min(1f, Math.max(Math.abs(dx), Math.abs(dz)) / HALF);
        float height = y * (1f - SINK * melt) - DROOP * melt * y * edge * edge;
        double around = Math.atan2(dz, dx);
        float ripple = RIPPLE * melt * (float) Math.sin(ticks * RIPPLE_RATE * TWO_PI + y * RIPPLE_HEIGHT_FREQ
                + around * RIPPLE_AROUND_FREQ);
        float spread = 1f + BULGE * melt * (1f - y) * (1f - y) - PINCH * melt * y * y + ripple;
        return new Vec3(HALF + dx * spread, Math.max(0f, height), HALF + dz * spread);
    }

    /**
     * The sprite a block's quad is textured with, as the rectangle of the atlas it covers.
     *
     * @param quad the quad
     * @return its sprite's atlas rectangle
     */
    public static GooRenderUtil.UvRect spriteOf(BakedQuad quad) {
        float u0 = Float.MAX_VALUE;
        float v0 = Float.MAX_VALUE;
        float u1 = -Float.MAX_VALUE;
        float v1 = -Float.MAX_VALUE;
        for (QuadRectClipper.ClipVertex vertex : QuadRectClipper.verticesOf(quad)) {
            u0 = Math.min(u0, vertex.u());
            v0 = Math.min(v0, vertex.v());
            u1 = Math.max(u1, vertex.u());
            v1 = Math.max(v1, vertex.v());
        }
        return new GooRenderUtil.UvRect(u0, v0, u1, v1);
    }

    private static int tintOf(Melt melt, BakedQuad quad) {
        return tintOf(melt.state(), melt.level(), melt.pos(), quad);
    }

    /**
     * The colour a block's quad is tinted where it stands, as grass and leaves are, or white for an untinted quad.
     *
     * @param state the block
     * @param level the level it stands in
     * @param pos   where it stands
     * @param quad  the quad
     * @return the tint
     */
    public static int tintOf(BlockState state, BlockAndTintGetter level, BlockPos pos, BakedQuad quad) {
        int index = quad.materialInfo().tintIndex();
        if (index < 0) {
            return GooRenderUtil.OPAQUE_WHITE;
        }
        @Nullable BlockTintSource source = Minecraft.getInstance().getBlockColors().getTintSource(state, index);
        return source == null ? GooRenderUtil.OPAQUE_WHITE : ARGB.opaque(source.colorInWorld(state, level, pos));
    }

}
